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
    // last_opened_at is indexed because the recents query orders by it over a column that
    // is null for every book not yet read.
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

    /**
     * When this book was last opened in the reader, or null if it never has been.
     *
     * NOT [lastModified] (the file's mtime) and NOT [dateAdded] (when the scan first saw it):
     * both answer "when did this file appear", and a shelf built on either would rank a library
     * copied in one go by nothing more useful than copy order.
     */
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
