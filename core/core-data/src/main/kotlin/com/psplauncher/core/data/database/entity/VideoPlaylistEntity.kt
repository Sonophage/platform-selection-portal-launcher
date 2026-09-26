package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.psplauncher.core.domain.model.VideoPlaylist

@Serializable
@Entity(tableName = "video_playlists")
data class VideoPlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
)

@Serializable
@Entity(
    tableName = "video_playlist_items",
    primaryKeys = ["playlist_id", "video_id"],
    foreignKeys = [
        ForeignKey(
            entity = VideoPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlist_id"), Index("video_id")],
)
data class VideoPlaylistItemEntity(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    @ColumnInfo(name = "video_id")
    val videoId: String,

    val position: Int,

    @ColumnInfo(name = "added_at")
    val addedAt: Long,
)

fun VideoPlaylistEntity.toDomain(videoCount: Int = 0) = VideoPlaylist(
    id = id,
    name = name,
    videoCount = videoCount,
)
