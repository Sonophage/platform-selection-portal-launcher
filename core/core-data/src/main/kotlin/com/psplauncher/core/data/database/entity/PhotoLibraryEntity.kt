package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.psplauncher.core.domain.model.PhotoLibrary

@Serializable
@Entity(tableName = "photo_libraries")
data class PhotoLibraryEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "tree_uri")
    val treeUri: String,

    val enabled: Boolean = true,

    @ColumnInfo(name = "scan_recursively")
    val scanRecursively: Boolean = true,

    @ColumnInfo(name = "photo_count")
    val photoCount: Int = 0,

    @ColumnInfo(name = "last_scanned_at")
    val lastScannedAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

fun PhotoLibraryEntity.toDomain() = PhotoLibrary(
    id              = id,
    displayName     = displayName,
    treeUri         = treeUri,
    enabled         = enabled,
    scanRecursively = scanRecursively,
    photoCount      = photoCount,
    lastScannedAt   = lastScannedAt,
    createdAt       = createdAt,
    updatedAt       = updatedAt,
)

fun PhotoLibrary.toEntity() = PhotoLibraryEntity(
    id              = id,
    displayName     = displayName,
    treeUri         = treeUri,
    enabled         = enabled,
    scanRecursively = scanRecursively,
    photoCount      = photoCount,
    lastScannedAt   = lastScannedAt,
    createdAt       = createdAt,
    updatedAt       = updatedAt,
)
