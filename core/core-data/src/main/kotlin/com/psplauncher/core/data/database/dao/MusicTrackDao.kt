package com.psplauncher.core.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.psplauncher.core.data.database.entity.MusicTrackEntity
import kotlinx.coroutines.flow.Flow

data class TrackPlayStamp(
    val id: String,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long?,
)

@Dao
interface MusicTrackDao {
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

    @Transaction
    suspend fun replaceForFolder(folderId: String, tracks: List<MusicTrackEntity>) {
        val stamps = playStampsForFolder(folderId).associate { it.id to it.lastPlayedAt }
        deleteForFolder(folderId)
        if (tracks.isNotEmpty()) insertAll(
            tracks.map { if (it.lastPlayedAt == null) it.copy(lastPlayedAt = stamps[it.id]) else it },
        )
    }

    @Query("UPDATE music_tracks SET last_played_at = :playedAt WHERE id = :id")
    suspend fun markPlayed(id: String, playedAt: Long)

    @Query("UPDATE music_tracks SET last_played_at = NULL WHERE id = :id")
    suspend fun clearLastPlayed(id: String)

    @Query(
        "SELECT * FROM music_tracks WHERE last_played_at IS NOT NULL " +
            "ORDER BY last_played_at DESC LIMIT :limit"
    )
    fun observeRecentlyPlayed(limit: Int): Flow<List<MusicTrackEntity>>

    @Query(
        """
        SELECT art_uri FROM music_tracks
        WHERE art_uri IS NOT NULL AND art_uri != ''
        ORDER BY id DESC LIMIT :limit
        """
    )
    fun observeNewestArtUris(limit: Int): Flow<List<String>>
}
