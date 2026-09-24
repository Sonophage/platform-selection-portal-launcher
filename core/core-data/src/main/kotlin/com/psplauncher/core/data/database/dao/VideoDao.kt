package com.psplauncher.core.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.psplauncher.core.data.database.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

/** One row's watch state, read back before a scan replaces it. See [VideoDao.replaceForLibrary]. */
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

    // Most-recently-watched first; only videos that have actually been played (have a timestamp).
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

    // How many rows still reference a cached thumbnail (generated or custom) — 0 means its file
    // can be deleted.
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

    // Replaces a single library's videos atomically; other libraries are never touched.
    @Transaction
    @Query(
        "SELECT id, last_watched_at, resume_position_ms, poster_uri FROM videos " +
            "WHERE library_id = :libraryId AND (last_watched_at IS NOT NULL " +
            "OR resume_position_ms > 0 OR poster_uri IS NOT NULL)"
    )
    suspend fun watchStampsForLibrary(libraryId: String): List<VideoWatchStamp>

    /**
     * Replaces a single library's videos atomically; other libraries are never touched.
     *
     * Watch state survives the replace. This deletes and re-inserts from a scan that reads only
     * the filesystem, so before this guard a rescan silently cleared both last_watched_at and
     * resume_position_ms — Recently Watched emptied itself and every part-watched film went back
     * to the start, with nothing on screen to say why. Found while giving music and books the
     * same column; the fault was already here.
     *
     * Restored by id, which VideoScanner keeps stable by carrying `prior?.id` forward. A row the
     * scan supplies its own values for keeps them.
     */
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
                    // The scanner never supplies one, so this is always the stored value coming
                    // back. Without it a rescan silently unmatches every film.
                    posterUri = v.posterUri ?: prior.posterUri,
                )
            },
        )
    }

    /** Every video, for the poster matcher to walk. A one-shot read, not a flow. */
    @Query("SELECT * FROM videos")
    suspend fun getAllOnce(): List<VideoEntity>

    @Query("UPDATE videos SET poster_uri = :posterUri WHERE id = :id")
    suspend fun setPosterUri(id: String, posterUri: String?)

    /** Drops the video off the recents shelf. Resume position is left alone — see clearLastWatched. */
    @Query("UPDATE videos SET last_watched_at = NULL WHERE id = :id")
    suspend fun clearLastWatched(id: String)

    /**
     * The newest thumbnails in this library, newest first — for the XMB's card art grids.
     *
     * A LIMIT query returning only the URIs, not the rows. The grids need four per card and the
     * media columns slice one pool across their rows, so this is tens of strings; streaming every
     * track or photo to read one column off each would be thousands of rows for a handful of
     * thumbnails.
     *
     * Newest is highest id, the same proxy the games grid uses: these tables have no added-at
     * column either, and rows are inserted in scan order.
     */
    @Query(
        """
        SELECT COALESCE(custom_thumbnail_uri, poster_uri, thumbnail_uri) FROM videos
        WHERE COALESCE(custom_thumbnail_uri, poster_uri, thumbnail_uri) IS NOT NULL AND COALESCE(custom_thumbnail_uri, poster_uri, thumbnail_uri) != ''
        ORDER BY id DESC LIMIT :limit
        """
    )
    fun observeNewestArtUris(limit: Int): Flow<List<String>>
}
