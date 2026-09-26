package com.psplauncher.feature.settings.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.data.video.VideoIntentResolver
import com.psplauncher.core.data.video.VideoPlayerApp
import com.psplauncher.core.data.repository.FolderLinkStatus
import com.psplauncher.core.data.repository.MediaRootKind
import com.psplauncher.core.data.repository.MediaRootRepository
import com.psplauncher.core.data.repository.SafGrants
import com.psplauncher.core.domain.model.VideoLibrary
import com.psplauncher.core.domain.repository.VideoRepository
import com.psplauncher.core.ui.notification.BackgroundTaskNotifier
import com.psplauncher.feature.library.scanner.VideoScanResult
import com.psplauncher.feature.library.scanner.VideoScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PLAYER_BUILTIN = "builtin"
private const val PLAYER_ASK = "ask"

data class VideoSettingsUiState(

    val roots: List<RootFolderRow> = emptyList(),
    val scanning: Boolean = false,
    val scanMessage: String? = null,

    val defaultPlayer: String? = null,
    val availablePlayers: List<VideoPlayerApp> = emptyList(),
    val showPlayerPicker: Boolean = false,

    val hasTmdbKey: Boolean = false,
    val tmdbKeyDraft: String = "",
    val matchingPosters: Boolean = false,
    val posterMessage: String? = null,
) {
    val hasRoots: Boolean get() = roots.isNotEmpty()

    val defaultPlayerLabel: String
        get() = when (defaultPlayer) {
            null, PLAYER_BUILTIN -> "PSPLauncher"
            PLAYER_ASK           -> "System Default"
            else -> availablePlayers.firstOrNull { it.packageName == defaultPlayer }?.label ?: defaultPlayer
        }
}

@HiltViewModel
class VideoSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,
    private val videoScanner: VideoScanner,
    private val intentResolver: VideoIntentResolver,
    private val mediaRootRepository: MediaRootRepository,
    private val tmdbKeyProvider: com.psplauncher.feature.artwork.api.TmdbApiKeyProvider,
    private val posterFetcher: com.psplauncher.feature.artwork.api.VideoPosterFetcher,
) : ViewModel() {
    private val notifier = BackgroundTaskNotifier(context)
    private val _ui = MutableStateFlow(VideoSettingsUiState())
    val uiState: StateFlow<VideoSettingsUiState> = _ui

    init {
        viewModelScope.launch {
            tmdbKeyProvider.keyFlow.collect { key ->
                _ui.update { it.copy(hasTmdbKey = !key.isNullOrBlank()) }
            }
        }

        viewModelScope.launch {
            mediaRootRepository.roots(MediaRootKind.VIDEO).distinctUntilChanged().collect { roots ->
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
        viewModelScope.launch {
            videoRepository.observeDefaultVideoPlayer().collect { pref ->
                _ui.value = _ui.value.copy(defaultPlayer = pref)
            }
        }
    }

    fun addRoot(treeUri: Uri) {
        viewModelScope.launch {
            mediaRootRepository.persist(treeUri)
            mediaRootRepository.add(MediaRootKind.VIDEO, treeUri.toString())
            rescan()
        }
    }

    fun removeRoot(treeUri: String) {
        viewModelScope.launch {
            mediaRootRepository.remove(MediaRootKind.VIDEO, treeUri)
            rescan()
        }
    }

    fun relinkRoot(oldTreeUri: String, newUri: Uri) {
        viewModelScope.launch {
            mediaRootRepository.persist(newUri)
            mediaRootRepository.replace(MediaRootKind.VIDEO, oldTreeUri, newUri.toString())
            rescan()
        }
    }

    fun rescan() {
        viewModelScope.launch {
            val roots = mediaRootRepository.getAll(MediaRootKind.VIDEO)
            if (roots.isEmpty()) {
                _ui.value = _ui.value.copy(scanMessage = "Add a root folder first.")
                return@launch
            }
            _ui.value = _ui.value.copy(scanning = true, scanMessage = "Scanning…")

            videoRepository.getLibraries()
                .filter { it.treeUri !in roots }
                .forEach { videoRepository.removeLibrary(it.id) }

            var total = 0
            var error: String? = null
            for (root in roots) {
                val library = syncLibraryForRoot(root)
                val existing = videoRepository.getVideosForLibrary(library.id)
                val taskId = "video_scan_${library.id}"
                notifier.running(taskId, "Scanning ${library.displayName}", null)
                videoScanner.scan(library, deep = false, existing = existing).collect { result ->
                    when (result) {
                        is VideoScanResult.Progress ->
                            _ui.value = _ui.value.copy(scanMessage = "${result.videosFound} videos")
                        is VideoScanResult.Complete -> {
                            videoRepository.replaceVideosForLibrary(result.libraryId, result.videos, System.currentTimeMillis())
                            total += result.videos.size
                            notifier.complete(taskId, "Scanned ${library.displayName}", "${result.videos.size} videos")
                        }
                        is VideoScanResult.Error -> {
                            error = result.message
                            notifier.failed(taskId, "Scan failed", result.message)
                        }
                    }
                }
            }
            _ui.value = _ui.value.copy(scanning = false, scanMessage = error ?: "Found $total videos across ${roots.size} root(s).")
        }
    }

    fun openPlayerPicker() {
        _ui.value = _ui.value.copy(showPlayerPicker = true, availablePlayers = intentResolver.availablePlayers())
    }

    fun dismissPlayerPicker() { _ui.value = _ui.value.copy(showPlayerPicker = false) }

    fun chooseDefaultPlayer(value: String?) {
        _ui.value = _ui.value.copy(showPlayerPicker = false)
        viewModelScope.launch { videoRepository.setDefaultVideoPlayer(value) }
    }

    fun dismissMessage() { _ui.value = _ui.value.copy(scanMessage = null) }

    private suspend fun syncLibraryForRoot(root: String): VideoLibrary {
        val existing = videoRepository.getLibraries().firstOrNull { it.treeUri == root }
        val library = existing ?: videoRepository.addLibrary(displayName(root), root, scanRecursively = true)
        return videoRepository.getLibrary(library.id) ?: library
    }

    private fun displayName(treeUri: String): String =
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.name }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Uri.parse(treeUri).lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?: "Videos"

    fun setTmdbKeyDraft(v: String) = _ui.update { it.copy(tmdbKeyDraft = v) }

    fun saveTmdbKey() {
        val key = _ui.value.tmdbKeyDraft.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            val protection = tmdbKeyProvider.saveKey(key)
            _ui.update {
                it.copy(
                    tmdbKeyDraft = "",

                    posterMessage = if (protection == com.psplauncher.core.common.security.SecretProtection.PROTECTED) "Key saved"
                    else "Key saved, but it could not be encrypted on this device",
                )
            }
        }
    }

    fun clearTmdbKey() {
        viewModelScope.launch {
            tmdbKeyProvider.clearKey()
            _ui.update { it.copy(posterMessage = "Key removed") }
        }
    }

    fun fetchPosters(refresh: Boolean = false) {
        if (_ui.value.matchingPosters) return
        viewModelScope.launch {
            _ui.update { it.copy(matchingPosters = true, posterMessage = null) }
            val result = runCatching { posterFetcher.run(refreshExisting = refresh) }.getOrNull()
            _ui.update {
                it.copy(
                    matchingPosters = false,
                    posterMessage = result?.message() ?: "Could not reach TMDB",
                )
            }
        }
    }

    fun clearPosters() {
        viewModelScope.launch {
            posterFetcher.clearAll()
            _ui.update { it.copy(posterMessage = "Posters cleared") }
        }
    }
}
