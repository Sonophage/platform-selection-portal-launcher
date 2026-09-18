package com.psplauncher.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.psplauncher.core.data.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

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

    // Replaces a single library's books atomically; other libraries are never touched.
    @Transaction
    suspend fun replaceForLibrary(libraryId: String, books: List<BookEntity>) {
        deleteForLibrary(libraryId)
        if (books.isNotEmpty()) insertAll(books)
    }
}
