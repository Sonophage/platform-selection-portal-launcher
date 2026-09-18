package com.psplauncher.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.psplauncher.core.data.database.entity.BookLibraryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookLibraryDao {

    @Query("SELECT * FROM book_libraries ORDER BY display_name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BookLibraryEntity>>

    @Query("SELECT * FROM book_libraries ORDER BY display_name COLLATE NOCASE ASC")
    suspend fun getAll(): List<BookLibraryEntity>

    @Query("SELECT * FROM book_libraries WHERE id = :id")
    suspend fun getById(id: String): BookLibraryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(library: BookLibraryEntity)

    @Query("DELETE FROM book_libraries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE book_libraries SET display_name = :name, updated_at = :now WHERE id = :id")
    suspend fun setDisplayName(id: String, name: String, now: Long)

    @Query("UPDATE book_libraries SET tree_uri = :treeUri, updated_at = :now WHERE id = :id")
    suspend fun setTreeUri(id: String, treeUri: String, now: Long)

    @Query("UPDATE book_libraries SET scan_recursively = :recursive, updated_at = :now WHERE id = :id")
    suspend fun setScanRecursively(id: String, recursive: Boolean, now: Long)

    @Query("UPDATE book_libraries SET book_count = :count, last_scanned_at = :scannedAt, updated_at = :scannedAt WHERE id = :id")
    suspend fun updateScanResult(id: String, count: Int, scannedAt: Long)
}
