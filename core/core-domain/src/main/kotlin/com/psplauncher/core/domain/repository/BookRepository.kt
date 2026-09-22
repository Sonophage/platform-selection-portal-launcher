package com.psplauncher.core.domain.repository

import com.psplauncher.core.domain.model.Book
import com.psplauncher.core.domain.model.BookLibrary
import kotlinx.coroutines.flow.Flow

/**
 * Source of truth for the Library section: user-added SAF folders and the books found in them.
 * Mirrors [PhotoRepository], deliberately minimal — no favorites, no collections, no reading
 * progress. Progress belongs to the reader app, which owns the file once we hand it over.
 */
interface BookRepository {

    // ── Libraries ─────────────────────────────────────────────────────────────
    fun observeLibraries(): Flow<List<BookLibrary>>
    suspend fun getLibraries(): List<BookLibrary>
    suspend fun getLibrary(id: String): BookLibrary?
    /** Adds a library. Recursive by default; "Include Subfolders" can limit it per library. */
    suspend fun addLibrary(displayName: String, treeUri: String, scanRecursively: Boolean = true): BookLibrary
    suspend fun renameLibrary(id: String, displayName: String)
    suspend fun setLibraryScanRecursively(id: String, scanRecursively: Boolean)
    /** Points the library at a different folder; the next scan replaces its books. */
    suspend fun setLibraryTreeUri(id: String, treeUri: String)
    /** Removes the library and all of its book rows. Never touches the files on disk. */
    suspend fun removeLibrary(id: String)

    // ── Books ─────────────────────────────────────────────────────────────────
    fun observeAllBooks(): Flow<List<Book>>
    fun observeBooksByLibrary(libraryId: String): Flow<List<Book>>

    // ── Recency ───────────────────────────────────────────────────────────────
    /** Stamps a book as opened now. */
    suspend fun markBookOpened(bookId: String, openedAt: Long)
    /** Takes the book off the recents shelf. */
    suspend fun clearBookLastOpened(bookId: String)
    fun observeRecentlyOpenedBooks(limit: Int): Flow<List<Book>>
    suspend fun getBook(id: String): Book?
    suspend fun getBooksForLibrary(libraryId: String): List<Book>
    /** Atomically replaces the books of one library only; other libraries are untouched. */
    suspend fun replaceBooksForLibrary(libraryId: String, books: List<Book>, scannedAt: Long)
    /** Removes one book row from its library. Never deletes the file on disk. */
    suspend fun removeBook(id: String)

    // ── The reader ────────────────────────────────────────────────────────────
    /**
     * Package name of the app a book opens in, or null for "ask every time". There is no in-app
     * reader, so unlike music and video this has no sentinel for one.
     */
    fun observeDefaultReader(): Flow<String?>
    suspend fun getDefaultReader(): String?
    suspend fun setDefaultReader(packageName: String?)
}
