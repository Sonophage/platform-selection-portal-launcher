package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.psplauncher.core.domain.model.BookLibrary

// One row per user-added SAF book library. id is a generated UUID; tree_uri is the persisted
// ACTION_OPEN_DOCUMENT_TREE uri. book_count / last_scanned_at mirror the photo-library pattern so
// the library list stays reactive with counts without an extra join.
@Serializable
@Entity(tableName = "book_libraries")
data class BookLibraryEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "tree_uri")
    val treeUri: String,

    val enabled: Boolean = true,

    @ColumnInfo(name = "scan_recursively")
    val scanRecursively: Boolean = true,

    @ColumnInfo(name = "book_count")
    val bookCount: Int = 0,

    @ColumnInfo(name = "last_scanned_at")
    val lastScannedAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

fun BookLibraryEntity.toDomain() = BookLibrary(
    id              = id,
    displayName     = displayName,
    treeUri         = treeUri,
    enabled         = enabled,
    scanRecursively = scanRecursively,
    bookCount       = bookCount,
    lastScannedAt   = lastScannedAt,
    createdAt       = createdAt,
    updatedAt       = updatedAt,
)

fun BookLibrary.toEntity() = BookLibraryEntity(
    id              = id,
    displayName     = displayName,
    treeUri         = treeUri,
    enabled         = enabled,
    scanRecursively = scanRecursively,
    bookCount       = bookCount,
    lastScannedAt   = lastScannedAt,
    createdAt       = createdAt,
    updatedAt       = updatedAt,
)
