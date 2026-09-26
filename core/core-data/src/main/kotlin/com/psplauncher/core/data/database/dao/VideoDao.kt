package com.psplauncher.core.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.psplauncher.core.data.database.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

data class VideoWatchStamp(
    val id: String,
    @ColumnInfo(name = "last_watched_at") val lastWatchedAt: Long?,
    @ColumnInfo(name = "resume_position_ms") val resumePositionMs: Long,
    @ColumnInfo(name = "poster_uri") val posterUri: String?,
)

@Dao
interface VideoDao {
    @Query(
        """
        SELECT * FROM videos
        ORDER BY COALESCE(title, display_name) COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT * FROM videos
        WHERE library_id = :libraryId
        ORDER BY COALESCE(title, display_name) COLLATE NOCASE ASC
        """
    )
    fun observeByLibrary(libraryId: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE library_id = :libraryId")
    suspend fun getForLibrary(libraryId: String): List<VideoEntity>

    @Query(
        """
        SELECT * FROM videos
        WHERE is_favorite = 1
        ORDER BY COALESCE(title, display_name) COLLATE NOCASE ASC
        """
    )
    fun observeFavorites(): Flow<List<VideoEntity>>

    @Query(
        """
        SELECT * FROM videos
        WHERE last_watched_at IS NOT NULL
        ORDER BY last_watched_at DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyWatched(limit: Int): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getById(id: String): VideoEntity?

    @Query("SELECT COUNT(*) FROM videos WHERE library_id = :libraryId")
    suspend fun countForLibrary(libraryId: String): Int

    @Query("SELECT COUNT(*) FROM videos WHERE thumbnail_uri = :uri OR custom_thumbnail_uri = :uri")
    suspend fun countReferencingThumbnail(uri: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<VideoEntity>)

    @Query("DELETE FROM videos WHERE library_id = :libraryId")
    suspend fun deleteForLibrary(libraryId: String)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE videos SET resume_position_ms = :positionMs, last_watched_at = :watchedAt WHERE id = :id")
    suspend fun updateResumePosition(id: String, positionMs: Long, watchedAt: Long)

    @Query("UPDATE videos SET resume_position_ms = 0 WHERE id = :id")
    suspend fun clearResumePosition(id: String)

    @Query("UPDATE videos SET title = :title WHERE id = :id")
    suspend fun setTitle(id: String, title: String?)

    @Query("UPDATE videos SET custom_thumbnail_uri = :uri WHERE id = :id")
    suspend fun setCustomThumbnail(id: String, uri: String?)

    @Query("UPDATE videos SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Transaction
    @Query(
        "SELECT id, last_watched_at, resume_position_ms, poster_uri FROM videos " +
            "WHERE library_id = :libraryId AND (last_watched_at IS NOT NULL " +
            "OR resume_position_ms > 0 OR poster_uri IS NOT NULL)"
    )
    suspend fun watchStampsForLibrary(libraryId: String): List<VideoWatchStamp>

    @Transaction
    suspend fun replaceForLibrary(libraryId: String, videos: List<VideoEntity>) {
        val stamps = watchStampsForLibrary(libraryId).associateBy { it.id }
        deleteForLibrary(libraryId)
        if (videos.isNotEmpty()) insertAll(
            videos.map { v ->
                val prior = stamps[v.id] ?: return@map v
                v.copy(
                    lastWatchedAt = v.lastWatchedAt ?: prior.lastWatchedAt,
                    resumePositionMs = if (v.resumePositionMs > 0) v.resumePositionMs else prior.resumePositionMs,

                    posterUri = v.posterUri ?: prior.posterUri,
                )
            },
        )
    }

    @Query("SELECT * FROM videos")
    suspend fun getAllOnce(): List<VideoEntity>

    @Query("UPDATE videos SET poster_uri = :posterUri WHERE id = :id")
    suspend fun setPosterUri(id: String, posterUri: String?)

    @Query("UPDATE videos SET last_watched_at = NULL WHERE id = :id")
    suspend fun clearLastWatched(id: String)

    @Query(
        """
        SELECT COALESCE(custom_thumbnail_uri, poster_uri, thumbnail_uri) FROM videos
        WHERE COALESCE(custom_thumbnail_uri, poster_uri, thumbnail_uri) IS NOT NULL AND COALESCE(custom_thumbnail_uri, poster_uri, thumbnail_uri) != ''
        ORDER BY id DESC LIMIT :limit
        """
    )
    fun observeNewestArtUris(limit: Int): Flow<List<String>>
}
