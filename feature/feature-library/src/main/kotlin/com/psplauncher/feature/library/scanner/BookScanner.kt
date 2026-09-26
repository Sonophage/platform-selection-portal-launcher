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

private const val COVER_MAX_DIM = 1200

private const val SCAN_PARALLELISM = 4

sealed interface BookScanResult {
    data class Progress(val libraryName: String, val filesSeen: Int, val booksFound: Int) : BookScanResult
    data class Complete(val libraryId: String, val books: List<Book>) : BookScanResult
    data class Error(val libraryId: String, val message: String) : BookScanResult
}

data class FoundBookFile(val child: SafChild, val relativePath: String)

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

                if (seenFiles.add(child.documentId)) found.add(FoundBookFile(child, relPath))
            }
        }
    }
    return found
}

internal fun canReuse(prior: Book?, lastModified: Long?, coverExists: (String) -> Boolean): Boolean {
    if (prior == null) return false
    if (prior.lastModified != lastModified) return false
    if (!prior.hasParsedMetadata()) return false
    val cover = prior.coverUri
    return cover.isNullOrBlank() || coverExists(cover)
}

internal fun Book.hasParsedMetadata(): Boolean =
    title != null || author != null || series != null || coverUri != null

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

    private fun bareBook(child: SafChild, libraryId: String, relPath: String, prior: Book?) = Book(

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

    val coverCacheDir: File by lazy { File(context.cacheDir, "book_covers").apply { mkdirs() } }

    fun clearCoverCache(): Int = runCatching {
        coverCacheDir.listFiles()?.count { it.delete() } ?: 0
    }.getOrDefault(0)

    private fun cacheCover(bookUri: Uri, meta: EpubMetadata?): String? {
        val entry = meta?.coverEntry ?: return null
        val file = File(coverCacheDir, "${sha1(bookUri.toString())}.jpg")
        return runCatching {
            val bytes = EpubMetadataReader.readEntry({ openStream(bookUri) }, entry)
                ?: return@runCatching null

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

    private fun openStream(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open $uri")

    private fun fileExistsForUri(uriString: String): Boolean = runCatching {
        Uri.parse(uriString).path?.let { File(it).exists() } == true
    }.getOrDefault(false)

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
