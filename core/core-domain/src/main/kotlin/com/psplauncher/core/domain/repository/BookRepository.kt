package com.psplauncher.core.domain.repository

import com.psplauncher.core.domain.model.Book
import com.psplauncher.core.domain.model.BookLibrary
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeLibraries(): Flow<List<BookLibrary>>
    suspend fun getLibraries(): List<BookLibrary>
    suspend fun getLibrary(id: String): BookLibrary?

    suspend fun addLibrary(displayName: String, treeUri: String, scanRecursively: Boolean = true): BookLibrary
    suspend fun renameLibrary(id: String, displayName: String)
    suspend fun setLibraryScanRecursively(id: String, scanRecursively: Boolean)

    suspend fun setLibraryTreeUri(id: String, treeUri: String)

    suspend fun removeLibrary(id: String)

    fun observeAllBooks(): Flow<List<Book>>
    fun observeBooksByLibrary(libraryId: String): Flow<List<Book>>

    suspend fun markBookOpened(bookId: String, openedAt: Long)

    suspend fun clearBookLastOpened(bookId: String)
    fun observeRecentlyOpenedBooks(limit: Int): Flow<List<Book>>
    suspend fun getBook(id: String): Book?
    suspend fun getBooksForLibrary(libraryId: String): List<Book>

    suspend fun replaceBooksForLibrary(libraryId: String, books: List<Book>, scannedAt: Long)

    suspend fun removeBook(id: String)

    fun observeDefaultReader(): Flow<String?>
    suspend fun getDefaultReader(): String?
    suspend fun setDefaultReader(packageName: String?)

    fun observeNewestArtUris(limit: Int): Flow<List<String>>
}
