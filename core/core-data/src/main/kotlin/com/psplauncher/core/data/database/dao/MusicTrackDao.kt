package com.psplauncher.core.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.psplauncher.core.data.database.entity.MusicTrackEntity
import kotlinx.coroutines.flow.Flow

/** One row's recency, read back before a scan replaces it. See [MusicTrackDao.replaceForFolder]. */
data class TrackPlayStamp(
    val id: String,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long?,
)

@Dao
interface MusicTrackDao {

    // Library-wide ordering: artist, then album, then title/displayName for a stable, musical sort.
    @Query(
        """
        SELECT * FROM music_tracks
        ORDER BY artist COLLATE NOCASE ASC,
                 album COLLATE NOCASE ASC,
                 track_number ASC,
                 COALESCE(title, display_name) COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<MusicTrackEntity>>

    @Query(
        """
        SELECT * FROM music_tracks
        WHERE folder_id = :folderId
        ORDER BY artist COLLATE NOCASE ASC,
                 album COLLATE NOCASE ASC,
                 track_number ASC,
                 COALESCE(title, display_name) COLLATE NOCASE ASC
        """
    )
    fun observeByFolder(folderId: String): Flow<List<MusicTrackEntity>>

    @Query("SELECT * FROM music_tracks WHERE id = :id")
    suspend fun getById(id: String): MusicTrackEntity?

    @Query("SELECT COUNT(*) FROM music_tracks WHERE folder_id = :folderId")
    suspend fun countForFolder(folderId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<MusicTrackEntity>)

    @Query("DELETE FROM music_tracks WHERE folder_id = :folderId")
    suspend fun deleteForFolder(folderId: String)

    @Query(
        "SELECT id, last_played_at FROM music_tracks " +
            "WHERE folder_id = :folderId AND last_played_at IS NOT NULL"
    )
    suspend fun playStampsForFolder(folderId: String): List<TrackPlayStamp>

    /**
     * Replaces a single folder's tracks atomically; other folders are never touched.
     *
     * last_played_at survives the replace. This method deletes and re-inserts, and the scanner
     * builds its rows from the filesystem, which knows nothing about what has been played — so
     * without this the recents shelf would quietly empty itself every time a music folder was
     * rescanned, with nothing on screen to say why. The stamp is the user's, not the scan's.
     *
     * Restored by id, which both scanners keep stable across scans by carrying `prior?.id`
     * forward; a row the scan supplies a stamp for keeps its own.
     */
    @Transaction
    suspend fun replaceForFolder(folderId: String, tracks: List<MusicTrackEntity>) {
        val stamps = playStampsForFolder(folderId).associate { it.id to it.lastPlayedAt }
        deleteForFolder(folderId)
        if (tracks.isNotEmpty()) insertAll(
            tracks.map { if (it.lastPlayedAt == null) it.copy(lastPlayedAt = stamps[it.id]) else it },
        )
    }

    /** Stamps a track as played now. Called when playback actually starts, not when it is queued. */
    @Query("UPDATE music_tracks SET last_played_at = :playedAt WHERE id = :id")
    suspend fun markPlayed(id: String, playedAt: Long)

    /** Drops the track off the recents shelf without touching anything else about it. */
    @Query("UPDATE music_tracks SET last_played_at = NULL WHERE id = :id")
    suspend fun clearLastPlayed(id: String)

    @Query(
        "SELECT * FROM music_tracks WHERE last_played_at IS NOT NULL " +
            "ORDER BY last_played_at DESC LIMIT :limit"
    )
    fun observeRecentlyPlayed(limit: Int): Flow<List<MusicTrackEntity>>
}
