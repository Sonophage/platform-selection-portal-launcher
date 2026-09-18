package com.psplauncher.feature.library.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.psplauncher.core.data.book.BookFileFilter
import com.psplauncher.core.data.saf.SafChild
import com.psplauncher.core.data.saf.hasNoMediaMarker
import com.psplauncher.core.data.saf.isIgnoredDir
import com.psplauncher.core.data.saf.querySafChildren
import com.psplauncher.core.data.saf.safScanStartDocId
import com.psplauncher.core.domain.model.Book
import com.psplauncher.core.domain.model.BookLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BookScanResult {
    data class Progress(val libraryName: String, val filesSeen: Int, val booksFound: Int) : BookScanResult
    data class Complete(val libraryId: String, val books: List<Book>) : BookScanResult
    data class Error(val libraryId: String, val message: String) : BookScanResult
}

/** One found file: the SAF row, and its path relative to the library root. */
data class FoundBookFile(val child: SafChild, val relativePath: String)

/**
 * Walks one library's folder and returns the book files in it.
 *
 * The walk is a plain function taking [listChildren] rather than a class reading a
 * `ContentResolver`, which is the whole reason the policy below is testable without a device. It
 * holds everything that can be got wrong: honouring `.nomedia`, pruning hidden directories,
 * refusing to recurse when the library says not to, and not looping when a provider surfaces a
 * directory under itself.
 *
 * Iterative rather than recursive so a deep tree cannot blow the stack, and both directories and
 * files are de-duplicated, because a provider that surfaces one document under two parents would
 * otherwise produce two rows for one book.
 */
internal fun collectBookFiles(
    startDocId: String,
    scanRecursively: Boolean,
    listChildren: (String) -> List<SafChild>,
): List<FoundBookFile> {
    val found = mutableListOf<FoundBookFile>()
    val visitedDirs = HashSet<String>().apply { add(startDocId) }
    val seenFiles = HashSet<String>()
    val stack = ArrayDeque<Pair<String, String>>()
    stack.addLast(startDocId to "")

    while (stack.isNotEmpty()) {
        val (dirDocId, relPath) = stack.removeLast()
        val children = listChildren(dirDocId)
        // A .nomedia marker skips this folder's files AND its whole subtree.
        if (children.hasNoMediaMarker()) continue
        for (child in children) {
            if (child.isDirectory) {
                if (!scanRecursively) continue
                if (child.isIgnoredDir()) continue
                if (!visitedDirs.add(child.documentId)) continue
                stack.addLast(
                    child.documentId to if (relPath.isEmpty()) child.name else "$relPath/${child.name}"
                )
            } else {
                if (!BookFileFilter.isBook(child.name, child.mime)) continue
                // Keyed on the document id, not the uri: the id IS the provider's identity for a
                // file, so this catches the same document surfaced under two parents even when the
                // uris it builds differ.
                if (seenFiles.add(child.documentId)) found.add(FoundBookFile(child, relPath))
            }
        }
    }
    return found
}

/**
 * Finds the books in one [BookLibrary].
 *
 * Much less than [PhotoScanner], on purpose. A photo scan decodes bounds, reads EXIF and writes a
 * thumbnail per file, so it needs bounded parallelism and a quick/deep distinction. A book is
 * handed to a reader app untouched, so there is nothing to probe: the directory cursor already
 * carries every field a row needs, and the scan is the walk plus a filter.
 */
@Singleton
class BookScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scan(library: BookLibrary): Flow<BookScanResult> = channelFlow {
        val treeUri = runCatching { Uri.parse(library.treeUri) }.getOrNull()
        val root = treeUri?.let { DocumentFile.fromTreeUri(context, it) }
        if (treeUri == null || root == null || !root.canRead()) {
            send(BookScanResult.Error(library.id, "Permission lost, re-select folder."))
            return@channelFlow
        }

        val startMs = System.currentTimeMillis()
        Timber.i("Book scan started: \"${library.displayName}\"")

        val files = collectBookFiles(
            startDocId = safScanStartDocId(context, treeUri),
            scanRecursively = library.scanRecursively,
        ) { docId -> context.contentResolver.querySafChildren(treeUri, docId) }

        send(BookScanResult.Progress(library.displayName, files.size, files.size))

        val now = System.currentTimeMillis()
        val books = files.map { (child, relPath) ->
            Book(
                id = UUID.randomUUID().toString(),
                libraryId = library.id,
                uri = child.uri.toString(),
                displayName = child.name,
                lastModified = child.lastModified,
                sizeBytes = child.sizeBytes,
                mimeType = child.mime,
                relativePath = relPath.takeIf { it.isNotEmpty() },
                dateAdded = now,
            )
        }

        Timber.i(
            "Book scan complete: \"${library.displayName}\" — ${books.size} books in " +
                "${System.currentTimeMillis() - startMs}ms"
        )
        send(BookScanResult.Complete(library.id, books))
    }.flowOn(Dispatchers.IO)
}
