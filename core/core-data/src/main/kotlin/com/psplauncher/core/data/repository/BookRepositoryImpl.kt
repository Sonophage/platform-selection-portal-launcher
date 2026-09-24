package com.psplauncher.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psplauncher.core.data.database.dao.BookDao
import com.psplauncher.core.data.database.dao.BookLibraryDao
import com.psplauncher.core.data.database.entity.toDomain
import com.psplauncher.core.data.database.entity.toEntity
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.domain.model.Book
import com.psplauncher.core.domain.model.BookLibrary
import com.psplauncher.core.domain.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backed up by name in `BackupManager.BACKED_UP_STRING_KEYS`. A preference that misses that list
 * reverts to its default on a restored device without anything failing.
 */
private val KEY_BOOK_DEFAULT_READER = stringPreferencesKey("books_default_reader")

@Singleton
class BookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryDao: BookLibraryDao,
    private val bookDao: BookDao,
) : BookRepository {

    override fun observeLibraries(): Flow<List<BookLibrary>> =
        libraryDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getLibraries(): List<BookLibrary> =
        libraryDao.getAll().map { it.toDomain() }

    override suspend fun getLibrary(id: String): BookLibrary? =
        libraryDao.getById(id)?.toDomain()

    override suspend fun addLibrary(
        displayName: String,
        treeUri: String,
        scanRecursively: Boolean,
    ): BookLibrary {
        val now = System.currentTimeMillis()
        val library = BookLibrary(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            treeUri = treeUri,
            scanRecursively = scanRecursively,
            createdAt = now,
            updatedAt = now,
        )
        libraryDao.upsert(library.toEntity())
        Timber.i("Added book library \"$displayName\"")
        return library
    }

    override suspend fun renameLibrary(id: String, displayName: String) =
        libraryDao.setDisplayName(id, displayName, System.currentTimeMillis())

    override suspend fun setLibraryScanRecursively(id: String, scanRecursively: Boolean) =
        libraryDao.setScanRecursively(id, scanRecursively, System.currentTimeMillis())

    override suspend fun setLibraryTreeUri(id: String, treeUri: String) =
        libraryDao.setTreeUri(id, treeUri, System.currentTimeMillis())

    /** The books go with it through the foreign key, not through a second delete here. */
    override suspend fun removeLibrary(id: String) = libraryDao.delete(id)

    override fun observeAllBooks(): Flow<List<Book>> =
        bookDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeBooksByLibrary(libraryId: String): Flow<List<Book>> =
        bookDao.observeByLibrary(libraryId).map { list -> list.map { it.toDomain() } }

    override suspend fun markBookOpened(bookId: String, openedAt: Long) =
        bookDao.markOpened(bookId, openedAt)

    override suspend fun clearBookLastOpened(bookId: String) = bookDao.clearLastOpened(bookId)

    override fun observeRecentlyOpenedBooks(limit: Int): Flow<List<Book>> =
        bookDao.observeRecentlyOpened(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getBook(id: String): Book? = bookDao.getById(id)?.toDomain()

    override suspend fun getBooksForLibrary(libraryId: String): List<Book> =
        bookDao.getForLibrary(libraryId).map { it.toDomain() }

    override suspend fun replaceBooksForLibrary(
        libraryId: String,
        books: List<Book>,
        scannedAt: Long,
    ) {
        bookDao.replaceForLibrary(libraryId, books.map { it.toEntity() })
        libraryDao.updateScanResult(libraryId, books.size, scannedAt)
    }

    override suspend fun removeBook(id: String) = bookDao.deleteById(id)

    override fun observeDefaultReader(): Flow<String?> =
        context.pfpDataStore.data.map { it[KEY_BOOK_DEFAULT_READER] }

    override suspend fun getDefaultReader(): String? =
        context.pfpDataStore.data.first()[KEY_BOOK_DEFAULT_READER]

    override suspend fun setDefaultReader(packageName: String?) {
        context.pfpDataStore.edit { prefs ->
            if (packageName.isNullOrBlank()) prefs.remove(KEY_BOOK_DEFAULT_READER)
            else prefs[KEY_BOOK_DEFAULT_READER] = packageName
        }
    }

    override fun observeNewestArtUris(limit: Int): Flow<List<String>> =
        bookDao.observeNewestArtUris(limit)
}
