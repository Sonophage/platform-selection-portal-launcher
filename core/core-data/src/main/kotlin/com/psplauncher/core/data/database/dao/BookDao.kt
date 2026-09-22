package com.psplauncher.core.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.psplauncher.core.data.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

/** One row's recency, read back before a scan replaces it. See [BookDao.replaceForLibrary]. */
data class BookOpenStamp(
    val id: String,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
)

@Dao
interface BookDao {

    // Sorted by the parsed title when a later pass fills one in, else the file name, so the list
    // order does not jump around once metadata lands.
    @Query("SELECT * FROM books ORDER BY COALESCE(title, display_name) COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query(
        """
        SELECT * FROM books
        WHERE library_id = :libraryId
        ORDER BY COALESCE(title, display_name) COLLATE NOCASE ASC
        """
    )
    fun observeByLibrary(libraryId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE library_id = :libraryId")
    suspend fun getForLibrary(libraryId: String): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookEntity>)

    @Query("DELETE FROM books WHERE library_id = :libraryId")
    suspend fun deleteForLibrary(libraryId: String)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "SELECT id, last_opened_at FROM books " +
            "WHERE library_id = :libraryId AND last_opened_at IS NOT NULL"
    )
    suspend fun openStampsForLibrary(libraryId: String): List<BookOpenStamp>

    /**
     * Replaces a single library's books atomically; other libraries are never touched.
     *
     * last_opened_at survives the replace, for the same reason music_tracks.last_played_at does:
     * this deletes and re-inserts from a scan that knows only the filesystem, so without it a
     * rescan would silently clear the recents shelf. Restored by id, which BookScanner keeps
     * stable by carrying `prior?.id` forward.
     */
    @Transaction
    suspend fun replaceForLibrary(libraryId: String, books: List<BookEntity>) {
        val stamps = openStampsForLibrary(libraryId).associate { it.id to it.lastOpenedAt }
        deleteForLibrary(libraryId)
        if (books.isNotEmpty()) insertAll(
            books.map { if (it.lastOpenedAt == null) it.copy(lastOpenedAt = stamps[it.id]) else it },
        )
    }

    /** Stamps a book as opened now. */
    @Query("UPDATE books SET last_opened_at = :openedAt WHERE id = :id")
    suspend fun markOpened(id: String, openedAt: Long)

    /** Drops the book off the recents shelf without touching anything else about it. */
    @Query("UPDATE books SET last_opened_at = NULL WHERE id = :id")
    suspend fun clearLastOpened(id: String)

    @Query(
        "SELECT * FROM books WHERE last_opened_at IS NOT NULL " +
            "ORDER BY last_opened_at DESC LIMIT :limit"
    )
    fun observeRecentlyOpened(limit: Int): Flow<List<BookEntity>>
}
