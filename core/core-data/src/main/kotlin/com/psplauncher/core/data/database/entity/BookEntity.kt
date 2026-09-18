package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.psplauncher.core.domain.model.Book

// One row per scanned book file. uri is a SAF document uri string. Cascade-deletes with its
// library so removing a library removes its books; indexed by library_id for per-library queries.
// title, author, series and cover_uri are all filled by the scanner's EPUB metadata pass and stay
// null for a book whose package document does not declare them.
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
    indices = [Index("library_id"), Index("uri")],
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

    /** Series name from the EPUB's package document; null when it declares none. */
    val series: String? = null,

    /**
     * Position within [series]. REAL rather than INTEGER because a novella between books 2 and 3
     * is conventionally numbered 2.5, and Calibre stores it that way.
     */
    @ColumnInfo(name = "series_index")
    val seriesIndex: Double? = null,

    /** file:// uri of the cached cover thumbnail, or null when the book has no usable cover. */
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
)
