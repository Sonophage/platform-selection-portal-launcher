package com.psplauncher.feature.settings.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.data.repository.FolderLinkStatus
import com.psplauncher.core.data.repository.MediaRootKind
import com.psplauncher.core.data.repository.MediaRootRepository
import com.psplauncher.core.data.repository.SafGrants
import com.psplauncher.core.domain.model.PhotoLibrary
import com.psplauncher.core.domain.repository.PhotoRepository
import com.psplauncher.core.ui.notification.BackgroundTaskNotifier
import com.psplauncher.feature.library.scanner.PhotoScanResult
import com.psplauncher.feature.library.scanner.PhotoScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhotoSettingsUiState(

    val roots: List<RootFolderRow> = emptyList(),
    val scanning: Boolean = false,
    val scanMessage: String? = null,
) {
    val hasRoots: Boolean get() = roots.isNotEmpty()
}

@HiltViewModel
class PhotoSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoRepository: PhotoRepository,
    private val photoScanner: PhotoScanner,
    private val mediaRootRepository: MediaRootRepository,
) : ViewModel() {
    private val notifier = BackgroundTaskNotifier(context)
    private val _ui = MutableStateFlow(PhotoSettingsUiState())
    val uiState: StateFlow<PhotoSettingsUiState> = _ui

    init {
        viewModelScope.launch {
            mediaRootRepository.roots(MediaRootKind.PHOTO).distinctUntilChanged().collect { roots ->
                val persisted = SafGrants.persistedReadUris(context.contentResolver)
                _ui.value = _ui.value.copy(roots = roots.map { uri ->
                    RootFolderRow(
                        treeUri = uri,
                        name = displayName(uri),
                        linked = SafGrants.linkStatus(uri, persisted) == FolderLinkStatus.LINKED,
                    )
                })
            }
        }
    }

    fun addRoot(treeUri: Uri) {
        viewModelScope.launch {
            mediaRootRepository.persist(treeUri)
            mediaRootRepository.add(MediaRootKind.PHOTO, treeUri.toString())
            rescan()
        }
    }

    fun removeRoot(treeUri: String) {
        viewModelScope.launch {
            mediaRootRepository.remove(MediaRootKind.PHOTO, treeUri)
            rescan()
        }
    }

    fun relinkRoot(oldTreeUri: String, newUri: Uri) {
        viewModelScope.launch {
            mediaRootRepository.persist(newUri)
            mediaRootRepository.replace(MediaRootKind.PHOTO, oldTreeUri, newUri.toString())
            rescan()
        }
    }

    fun rescan() {
        viewModelScope.launch {
            val roots = mediaRootRepository.getAll(MediaRootKind.PHOTO)
            if (roots.isEmpty()) {
                _ui.value = _ui.value.copy(scanMessage = "Add a root folder first.")
                return@launch
            }
            _ui.value = _ui.value.copy(scanning = true, scanMessage = "Scanning…")

            photoRepository.getLibraries()
                .filter { it.treeUri !in roots }
                .forEach { photoRepository.removeLibrary(it.id) }

            var total = 0
            var error: String? = null
            for (root in roots) {
                val library = syncLibraryForRoot(root)
                val existing = photoRepository.getPhotosForLibrary(library.id)
                val taskId = "photo_scan_${library.id}"
                notifier.running(taskId, "Scanning ${library.displayName}", null)
                photoScanner.scan(library, deep = false, existing = existing).collect { result ->
                    when (result) {
                        is PhotoScanResult.Progress ->
                            _ui.value = _ui.value.copy(scanMessage = "${result.photosFound} photos")
                        is PhotoScanResult.Complete -> {
                            photoRepository.replacePhotosForLibrary(result.libraryId, result.photos, System.currentTimeMillis())
                            total += result.photos.size
                            notifier.complete(taskId, "Scanned ${library.displayName}", "${result.photos.size} photos")
                        }
                        is PhotoScanResult.Error -> {
                            error = result.message
                            notifier.failed(taskId, "Scan failed", result.message)
                        }
                    }
                }
            }
            _ui.value = _ui.value.copy(scanning = false, scanMessage = error ?: "Found $total photos across ${roots.size} root(s).")
        }
    }

    fun clearThumbnailCache() {
        viewModelScope.launch {
            val removed = photoScanner.clearThumbnailCache()
            _ui.value = _ui.value.copy(scanMessage = "Cleared $removed cached thumbnail(s). Rescan to regenerate.")
        }
    }

    fun dismissMessage() { _ui.value = _ui.value.copy(scanMessage = null) }

    private suspend fun syncLibraryForRoot(root: String): PhotoLibrary {
        val existing = photoRepository.getLibraries().firstOrNull { it.treeUri == root }
        val library = existing ?: photoRepository.addLibrary(displayName(root), root, scanRecursively = true)
        return photoRepository.getLibrary(library.id) ?: library
    }

    private fun displayName(treeUri: String): String =
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.name }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Uri.parse(treeUri).lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?: "Photos"
}
