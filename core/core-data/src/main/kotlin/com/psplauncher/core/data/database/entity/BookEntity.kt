package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.psplauncher.core.domain.model.Book

@Serializable
@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = BookLibraryEntity::class,
            parentColumns = ["id"],
            childColumns = ["library_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],

    indices = [Index("library_id"), Index("uri"), Index("last_opened_at")],
)
data class BookEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "library_id")
    val libraryId: String,

    val uri: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    val title: String? = null,
    val author: String? = null,

    val series: String? = null,

    @ColumnInfo(name = "series_index")
    val seriesIndex: Double? = null,

    @ColumnInfo(name = "cover_uri")
    val coverUri: String? = null,

    @ColumnInfo(name = "last_modified")
    val lastModified: Long? = null,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long? = null,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "relative_path")
    val relativePath: String? = null,

    @ColumnInfo(name = "date_added")
    val dateAdded: Long? = null,

    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAt: Long? = null,
)

fun BookEntity.toDomain() = Book(
    id           = id,
    libraryId    = libraryId,
    uri          = uri,
    displayName  = displayName,
    title        = title,
    author       = author,
    series       = series,
    seriesIndex  = seriesIndex,
    coverUri     = coverUri,
    lastModified = lastModified,
    sizeBytes    = sizeBytes,
    mimeType     = mimeType,
    relativePath = relativePath,
    dateAdded    = dateAdded,
    lastOpenedAt = lastOpenedAt,
)

fun Book.toEntity() = BookEntity(
    id           = id,
    libraryId    = libraryId,
    uri          = uri,
    displayName  = displayName,
    title        = title,
    author       = author,
    series       = series,
    seriesIndex  = seriesIndex,
    coverUri     = coverUri,
    lastModified = lastModified,
    sizeBytes    = sizeBytes,
    mimeType     = mimeType,
    relativePath = relativePath,
    dateAdded    = dateAdded,
    lastOpenedAt = lastOpenedAt,
)
