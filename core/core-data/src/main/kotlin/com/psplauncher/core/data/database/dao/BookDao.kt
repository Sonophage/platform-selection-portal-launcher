package com.psplauncher.core.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.psplauncher.core.data.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

data class BookOpenStamp(
    val id: String,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
)

@Dao
interface BookDao {
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

    @Transaction
    suspend fun replaceForLibrary(libraryId: String, books: List<BookEntity>) {
        val stamps = openStampsForLibrary(libraryId).associate { it.id to it.lastOpenedAt }
        deleteForLibrary(libraryId)
        if (books.isNotEmpty()) insertAll(
            books.map { if (it.lastOpenedAt == null) it.copy(lastOpenedAt = stamps[it.id]) else it },
        )
    }

    @Query("UPDATE books SET last_opened_at = :openedAt WHERE id = :id")
    suspend fun markOpened(id: String, openedAt: Long)

    @Query("UPDATE books SET last_opened_at = NULL WHERE id = :id")
    suspend fun clearLastOpened(id: String)

    @Query(
        "SELECT * FROM books WHERE last_opened_at IS NOT NULL " +
            "ORDER BY last_opened_at DESC LIMIT :limit"
    )
    fun observeRecentlyOpened(limit: Int): Flow<List<BookEntity>>

    @Query(
        """
        SELECT cover_uri FROM books
        WHERE cover_uri IS NOT NULL AND cover_uri != ''
        ORDER BY id DESC LIMIT :limit
        """
    )
    fun observeNewestArtUris(limit: Int): Flow<List<String>>
}
