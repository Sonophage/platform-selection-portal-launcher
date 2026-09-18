package com.psplauncher.feature.library.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.psplauncher.core.data.book.BookFileFilter
import com.psplauncher.core.data.book.EpubMetadata
import com.psplauncher.core.data.book.EpubMetadataReader
import com.psplauncher.core.data.saf.SafChild
import com.psplauncher.core.data.saf.hasNoMediaMarker
import com.psplauncher.core.data.saf.isIgnoredDir
import com.psplauncher.core.data.saf.querySafChildren
import com.psplauncher.core.data.saf.safScanStartDocId
import com.psplauncher.core.domain.model.Book
import com.psplauncher.core.domain.model.BookLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

// Longest edge of a cached cover, in px. Covers are portrait and drawn in a 40x56 list tile, so
// this is generous enough for the flyout without keeping a full-size jacket per book on disk.
private const val COVER_MAX_DIM = 400

// Concurrent per-file EPUB reads. Each one opens the archive up to three times and decodes one
// image, so this is I/O bound; four in flight keeps a folder of large books from monopolising the
// device without leaving the disk idle between files.
private const val SCAN_PARALLELISM = 4

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
 * True when [prior] can be carried forward without reopening the book.
 *
 * Top-level and pure so the quick-scan rule is unit-testable without a device, because it is the
 * one rule here whose failure is silent in both directions: too strict and every rescan reopens
 * the whole library, too loose and a book's metadata never updates.
 *
 * The rule has three parts:
 *  - the file has not changed on disk, by `lastModified`;
 *  - the row has actually been through the metadata pass. A row scanned before covers existed has
 *    every metadata field null and must be reparsed once, which is what [hasParsedMetadata]
 *    detects. A book that genuinely declares nothing is reparsed on every quick scan as a result:
 *    that is the safe direction of the error, and it costs one archive read for a file that has
 *    no metadata to find;
 *  - its cached cover is still on disk. Clearing the cover cache must make the next rescan
 *    regenerate covers rather than reporting "nothing changed".
 */
internal fun canReuse(prior: Book?, lastModified: Long?, coverExists: (String) -> Boolean): Boolean {
    if (prior == null) return false
    if (prior.lastModified != lastModified) return false
    if (!prior.hasParsedMetadata()) return false
    val cover = prior.coverUri
    return cover.isNullOrBlank() || coverExists(cover)
}

/**
 * Whether this row has been through the EPUB metadata pass.
 *
 * There is no "parsed" flag, so this infers it from the fields the pass fills. A book that
 * declares none of them is indistinguishable from one that was never parsed, which is why the
 * quick scan errs towards reparsing rather than towards leaving a book blank forever.
 */
internal fun Book.hasParsedMetadata(): Boolean =
    title != null || author != null || series != null || coverUri != null

/**
 * Finds the books in one [BookLibrary] and reads what each one says about itself.
 *
 * Two modes, mirroring [PhotoScanner] and [MusicScanner]:
 *  - **Quick** ([deep] = false): a file whose `lastModified` is unchanged, whose row has been
 *    parsed before and whose cover is still cached is carried forward untouched, so a rescan of a
 *    settled library opens no archives at all.
 *  - **Deep** ([deep] = true): every book is reopened and its cover regenerated. This is what the
 *    Library settings screen's Deep Rescan row runs, and it is the escape hatch for a library
 *    whose metadata was edited in place without the file's timestamp moving, which is exactly what
 *    a Calibre "polish books" pass does.
 *
 * The metadata read is the expensive half and the reason the quick/deep split exists at all: the
 * first pass of this section did a cursor-only walk with no per-file I/O, and adding covers and
 * series turns every new book into three archive passes and an image decode.
 */
@Singleton
class BookScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scan(
        library: BookLibrary,
        deep: Boolean = false,
        existing: List<Book> = emptyList(),
    ): Flow<BookScanResult> = channelFlow {
        val treeUri = runCatching { Uri.parse(library.treeUri) }.getOrNull()
        val root = treeUri?.let { DocumentFile.fromTreeUri(context, it) }
        if (treeUri == null || root == null || !root.canRead()) {
            send(BookScanResult.Error(library.id, "Permission lost, re-select folder."))
            return@channelFlow
        }

        val startMs = System.currentTimeMillis()
        Timber.i("Book scan started (${if (deep) "deep" else "quick"}): \"${library.displayName}\"")
        val byUri = existing.associateBy { it.uri }

        val files = collectBookFiles(
            startDocId = safScanStartDocId(context, treeUri),
            scanRecursively = library.scanRecursively,
        ) { docId -> context.contentResolver.querySafChildren(treeUri, docId) }

        send(BookScanResult.Progress(library.displayName, files.size, 0))

        val processed = AtomicInteger(0)
        val semaphore = Semaphore(SCAN_PARALLELISM)
        val books = coroutineScope {
            files.map { (child, relPath) ->
                async {
                    semaphore.withPermit {
                        val book = runCatching { toBook(child, library.id, relPath, deep, byUri) }
                            .getOrElse { e ->
                                if (e is CancellationException) throw e
                                // One unreadable book is a row without metadata, never a failed
                                // scan: a single corrupt file must not cost the user the library.
                                Timber.w(e, "Reading metadata failed for ${child.uri}")
                                bareBook(child, library.id, relPath, byUri[child.uri.toString()])
                            }
                        val done = processed.incrementAndGet()
                        if (done % 10 == 0) {
                            trySend(BookScanResult.Progress(library.displayName, files.size, done))
                        }
                        book
                    }
                }
            }.awaitAll()
        }

        Timber.i(
            "Book scan complete: \"${library.displayName}\" — ${books.size} books in " +
                "${System.currentTimeMillis() - startMs}ms"
        )
        send(BookScanResult.Complete(library.id, books))
    }.flowOn(Dispatchers.IO)

    private fun toBook(
        child: SafChild,
        libraryId: String,
        relPath: String,
        deep: Boolean,
        byUri: Map<String, Book>,
    ): Book {
        val uriStr = child.uri.toString()
        val prior = byUri[uriStr]

        if (prior != null && !deep && canReuse(prior, child.lastModified) { fileExistsForUri(it) }) {
            return prior.copy(libraryId = libraryId, relativePath = relPath.takeIf { it.isNotEmpty() })
        }

        val meta = EpubMetadataReader.read { openStream(child.uri) }
        // A deep scan regenerates the cover; a quick scan reuses one that is still on disk, so a
        // changed file does not pay for an image decode it does not need.
        val cover = prior?.coverUri
            ?.takeIf { !deep && it.isNotBlank() && fileExistsForUri(it) }
            ?: cacheCover(child.uri, meta)

        return bareBook(child, libraryId, relPath, prior).copy(
            title = meta?.title,
            author = meta?.author,
            series = meta?.series,
            seriesIndex = meta?.seriesIndex,
            coverUri = cover,
        )
    }

    /** The row every book gets, metadata or not: what the directory cursor already knew. */
    private fun bareBook(child: SafChild, libraryId: String, relPath: String, prior: Book?) = Book(
        // The id is kept across rescans so anything holding one keeps pointing at the same book.
        id = prior?.id ?: UUID.randomUUID().toString(),
        libraryId = libraryId,
        uri = child.uri.toString(),
        displayName = child.name,
        lastModified = child.lastModified,
        sizeBytes = child.sizeBytes,
        mimeType = child.mime,
        relativePath = relPath.takeIf { it.isNotEmpty() },
        dateAdded = prior?.dateAdded ?: System.currentTimeMillis(),
    )

    // ── Cover cache ───────────────────────────────────────────────────────────

    /**
     * Cover cache, next to the photo thumbnails: internal app cache, private to the app, never
     * indexed by MediaStore and evictable by the OS. A separate directory so Clear Cover Cache and
     * Clear Thumbnail Cache do not take each other's files.
     */
    val coverCacheDir: File by lazy { File(context.cacheDir, "book_covers").apply { mkdirs() } }

    /** Deletes every cached cover. Returns how many files went. */
    fun clearCoverCache(): Int = runCatching {
        coverCacheDir.listFiles()?.count { it.delete() } ?: 0
    }.getOrDefault(0)

    /**
     * Extracts, downsamples and caches [meta]'s cover. Returns a file:// uri, or null when the
     * book names no cover or the image cannot be decoded.
     */
    private fun cacheCover(bookUri: Uri, meta: EpubMetadata?): String? {
        val entry = meta?.coverEntry ?: return null
        val file = File(coverCacheDir, "${sha1(bookUri.toString())}.jpg")
        return runCatching {
            val bytes = EpubMetadataReader.readEntry({ openStream(bookUri) }, entry)
                ?: return@runCatching null

            // Bounds first, so a large jacket is subsampled during decode rather than allocated
            // full size and scaled afterwards.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, COVER_MAX_DIM)
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: return@runCatching null
            FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            bmp.recycle()
            Uri.fromFile(file).toString().takeIf { file.length() > 0 }
        }.getOrElse { Timber.w(it, "Cover extraction failed for $bookUri"); null }
    }

    // Throws rather than returning an empty stream on a revoked grant, so the caller logs a lost
    // permission instead of silently recording a book with no metadata.
    private fun openStream(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open $uri")

    private fun fileExistsForUri(uriString: String): Boolean = runCatching {
        Uri.parse(uriString).path?.let { File(it).exists() } == true
    }.getOrDefault(false)

    // Power-of-two subsample factor that brings the longest edge at or under [maxDim].
    private fun sampleSize(w: Int, h: Int, maxDim: Int): Int {
        var sample = 1
        var longest = maxOf(w, h)
        while (longest / 2 >= maxDim) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
