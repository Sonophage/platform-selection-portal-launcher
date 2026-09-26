package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.psplauncher.core.domain.model.MusicFolder

@Serializable
@Entity(tableName = "music_folders")
data class MusicFolderEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "tree_uri")
    val treeUri: String,

    val enabled: Boolean = true,

    @ColumnInfo(name = "track_count")
    val trackCount: Int = 0,

    @ColumnInfo(name = "last_scanned_at")
    val lastScannedAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

fun MusicFolderEntity.toDomain() = MusicFolder(
    id            = id,
    displayName   = displayName,
    treeUri       = treeUri,
    enabled       = enabled,
    trackCount    = trackCount,
    lastScannedAt = lastScannedAt,
    createdAt     = createdAt,
    updatedAt     = updatedAt,
)

fun MusicFolder.toEntity() = MusicFolderEntity(
    id            = id,
    displayName   = displayName,
    treeUri       = treeUri,
    enabled       = enabled,
    trackCount    = trackCount,
    lastScannedAt = lastScannedAt,
    createdAt     = createdAt,
    updatedAt     = updatedAt,
)
