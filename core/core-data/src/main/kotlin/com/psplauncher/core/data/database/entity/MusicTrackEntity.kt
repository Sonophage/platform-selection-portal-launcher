package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.psplauncher.core.domain.model.MusicTrack

// One row per scanned audio file. uri is a SAF document uri string. Cascade-deletes with its
// folder so removing a folder removes its tracks; indexed by folder_id for per-folder queries.
@Serializable
@Entity(
    tableName = "music_tracks",
    foreignKeys = [
        ForeignKey(
            entity = MusicFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // last_played_at is indexed for the same reason games.last_played_at is: the recents
    // query is ORDER BY it DESC over a table that is mostly nulls.
    indices = [Index("folder_id"), Index("last_played_at")],
)
data class MusicTrackEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "folder_id")
    val folderId: String,

    val uri: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,

    /** The album's own artist, when the file carries one. See MusicTrack.albumArtist. */
    @ColumnInfo(name = "album_artist")
    val albumArtist: String? = null,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long? = null,

    @ColumnInfo(name = "last_modified")
    val lastModified: Long? = null,

    @ColumnInfo(name = "track_number")
    val trackNumber: Int? = null,

    @ColumnInfo(name = "relative_path")
    val relativePath: String? = null,

    @ColumnInfo(name = "art_uri")
    val artUri: String? = null,

    /**
     * When this track was last played through the launcher, or null if it never has been.
     *
     * NOT [lastModified], which is the file's mtime — when the bytes were written, not when they
     * were listened to. A library copied in one go would have every track claim the same recency,
     * which is why the recents shelf needed a column of its own rather than a proxy.
     */
    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: Long? = null,
)

fun MusicTrackEntity.toDomain() = MusicTrack(
    id           = id,
    folderId     = folderId,
    uri          = uri,
    displayName  = displayName,
    title        = title,
    artist       = artist,
    album        = album,
    albumArtist  = albumArtist,
    durationMs   = durationMs,
    mimeType     = mimeType,
    sizeBytes    = sizeBytes,
    lastModified = lastModified,
    trackNumber  = trackNumber,
    relativePath = relativePath,
    artUri       = artUri,
    lastPlayedAt = lastPlayedAt,
)

fun MusicTrack.toEntity() = MusicTrackEntity(
    id           = id,
    folderId     = folderId,
    uri          = uri,
    displayName  = displayName,
    title        = title,
    artist       = artist,
    album        = album,
    albumArtist  = albumArtist,
    durationMs   = durationMs,
    mimeType     = mimeType,
    sizeBytes    = sizeBytes,
    lastModified = lastModified,
    trackNumber  = trackNumber,
    relativePath = relativePath,
    artUri       = artUri,
    lastPlayedAt = lastPlayedAt,
)
