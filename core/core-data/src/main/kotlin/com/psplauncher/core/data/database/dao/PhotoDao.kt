package com.psplauncher.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.psplauncher.core.data.database.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY display_name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query(
        """
        SELECT * FROM photos
        WHERE library_id = :libraryId
        ORDER BY display_name COLLATE NOCASE ASC
        """
    )
    fun observeByLibrary(libraryId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE library_id = :libraryId")
    suspend fun getForLibrary(libraryId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: String): PhotoEntity?

    @Query("SELECT COUNT(*) FROM photos WHERE thumbnail_uri = :uri")
    suspend fun countReferencingThumbnail(uri: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE library_id = :libraryId")
    suspend fun deleteForLibrary(libraryId: String)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    suspend fun replaceForLibrary(libraryId: String, photos: List<PhotoEntity>) {
        deleteForLibrary(libraryId)
        if (photos.isNotEmpty()) insertAll(photos)
    }

    @Query(
        """
        SELECT thumbnail_uri FROM photos
        WHERE thumbnail_uri IS NOT NULL AND thumbnail_uri != ''
        ORDER BY id DESC LIMIT :limit
        """
    )
    fun observeNewestArtUris(limit: Int): Flow<List<String>>
}
