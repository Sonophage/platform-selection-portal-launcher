package com.psplauncher.feature.settings.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.data.book.BookIntentResolver
import com.psplauncher.core.data.book.ReaderApp
import com.psplauncher.core.data.repository.FolderLinkStatus
import com.psplauncher.core.data.repository.MediaRootKind
import com.psplauncher.core.data.repository.MediaRootRepository
import com.psplauncher.core.data.repository.SafGrants
import com.psplauncher.core.domain.model.BookLibrary
import com.psplauncher.core.domain.repository.BookRepository
import com.psplauncher.core.ui.notification.BackgroundTaskNotifier
import com.psplauncher.feature.library.scanner.BookScanResult
import com.psplauncher.feature.library.scanner.BookScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BooksSettingsUiState(
    val roots: List<RootFolderRow> = emptyList(),
    val scanning: Boolean = false,
    val scanMessage: String? = null,
    /** Package name of the chosen reader, or null for "ask every time". */
    val defaultReader: String? = null,
    val availableReaders: List<ReaderApp> = emptyList(),
    val showReaderPicker: Boolean = false,
) {
    val hasRoots: Boolean get() = roots.isNotEmpty()

    val defaultReaderLabel: String
        get() = defaultReader
            ?.let { pkg -> availableReaders.firstOrNull { it.packageName == pkg }?.label ?: pkg }
            ?: "Ask Every Time"
}

/**
 * Multi-root Library settings, mirroring the Photo section: several root folders, each a persisted
 * SAF grant whose subfolders become libraries on scan, plus a rescan that reconciles the rows with
 * the configured roots.
 *
 * The one thing photos do not have is the reader. There is no in-app reader, so unlike Music and
 * Video the picker has no built-in choice: it is an installed app, or the system chooser.
 */
@HiltViewModel
class BooksSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val bookScanner: BookScanner,
    private val bookIntentResolver: BookIntentResolver,
    private val mediaRootRepository: MediaRootRepository,
) : ViewModel() {

    private val notifier = BackgroundTaskNotifier(context)
    private val _ui = MutableStateFlow(BooksSettingsUiState())
    val uiState: StateFlow<BooksSettingsUiState> = _ui

    init {
        viewModelScope.launch {
            // distinctUntilChanged: the backing DataStore is app-wide, so without it every
            // unrelated preference write would re-run the persisted-grant snapshot below.
            mediaRootRepository.roots(MediaRootKind.BOOK).distinctUntilChanged().collect { roots ->
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
            bookRepository.observeDefaultReader().distinctUntilChanged().collect { reader ->
                _ui.value = _ui.value.copy(defaultReader = reader)
            }
        }
    }

    fun addRoot(treeUri: Uri) {
        viewModelScope.launch {
            mediaRootRepository.persist(treeUri)
            mediaRootRepository.add(MediaRootKind.BOOK, treeUri.toString())
            rescan()
        }
    }

    fun removeRoot(treeUri: String) {
        viewModelScope.launch {
            mediaRootRepository.remove(MediaRootKind.BOOK, treeUri)
            rescan()
        }
    }

    /** Replaces one root's URI (re-link after a lost grant, or picking a different folder). */
    fun relinkRoot(oldTreeUri: String, newUri: Uri) {
        viewModelScope.launch {
            mediaRootRepository.persist(newUri)
            mediaRootRepository.replace(MediaRootKind.BOOK, oldTreeUri, newUri.toString())
            rescan()
        }
    }

    /**
     * Reconciles the library rows with the configured roots (dropping rows whose root is gone)
     * and scans every root.
     *
     * [deep] reopens every book and regenerates every cover. A normal rescan reads only files that
     * are new or whose timestamp moved, because reading a book's series and cover means opening
     * the archive, which a cursor-only walk never had to do.
     */
    fun rescan(deep: Boolean = false) {
        viewModelScope.launch {
            val roots = mediaRootRepository.getAll(MediaRootKind.BOOK)
            if (roots.isEmpty()) {
                _ui.value = _ui.value.copy(scanMessage = "Add a root folder first.")
                return@launch
            }
            _ui.value = _ui.value.copy(
                scanning = true,
                scanMessage = if (deep) "Reading every book…" else "Scanning…",
            )

            // Roots removed here take their library rows, and their books, with them.
            bookRepository.getLibraries()
                .filter { it.treeUri !in roots }
                .forEach { bookRepository.removeLibrary(it.id) }

            var total = 0
            var error: String? = null
            for (root in roots) {
                val library = syncLibraryForRoot(root)
                val taskId = "book_scan_${library.id}"
                notifier.running(taskId, "Scanning ${library.displayName}", null)
                // The existing rows are what makes a quick scan quick: an unchanged book is
                // carried forward from here rather than reopened.
                val existing = bookRepository.getBooksForLibrary(library.id)
                bookScanner.scan(library, deep = deep, existing = existing).collect { result ->
                    when (result) {
                        is BookScanResult.Progress ->
                            _ui.value = _ui.value.copy(
                                scanMessage = "${result.booksFound} of ${result.filesSeen} books",
                            )
                        is BookScanResult.Complete -> {
                            bookRepository.replaceBooksForLibrary(
                                result.libraryId, result.books, System.currentTimeMillis(),
                            )
                            total += result.books.size
                            notifier.complete(taskId, "Scanned ${library.displayName}", "${result.books.size} books")
                        }
                        is BookScanResult.Error -> {
                            error = result.message
                            notifier.failed(taskId, "Scan failed", result.message)
                        }
                    }
                }
            }
            _ui.value = _ui.value.copy(
                scanning = false,
                scanMessage = error ?: "Found $total books across ${roots.size} root(s).",
            )
        }
    }

    fun openReaderPicker() {
        _ui.value = _ui.value.copy(
            showReaderPicker = true,
            availableReaders = bookIntentResolver.availableReaders(),
        )
    }

    fun dismissReaderPicker() { _ui.value = _ui.value.copy(showReaderPicker = false) }

    /** [packageName] null means "ask every time": the chooser runs on every book. */
    fun chooseReader(packageName: String?) {
        viewModelScope.launch {
            bookRepository.setDefaultReader(packageName)
            _ui.value = _ui.value.copy(showReaderPicker = false)
        }
    }

    fun dismissMessage() { _ui.value = _ui.value.copy(scanMessage = null) }

    /**
     * Deletes every cached cover. The rows keep pointing at files that are now gone, which the
     * next rescan notices and regenerates, so this is safe to run at any time.
     */
    fun clearCoverCache() {
        viewModelScope.launch {
            val removed = bookScanner.clearCoverCache()
            _ui.value = _ui.value.copy(scanMessage = "Cleared $removed cover(s). Rescan to rebuild.")
        }
    }

    // Ensures one BookLibrary exists for [root] — other roots keep their own rows.
    private suspend fun syncLibraryForRoot(root: String): BookLibrary {
        val existing = bookRepository.getLibraries().firstOrNull { it.treeUri == root }
        val library = existing ?: bookRepository.addLibrary(displayName(root), root, scanRecursively = true)
        return bookRepository.getLibrary(library.id) ?: library
    }

    private fun displayName(treeUri: String): String =
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.name }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Uri.parse(treeUri).lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?: "Books"
}
