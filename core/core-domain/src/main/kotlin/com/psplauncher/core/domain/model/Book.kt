package com.psplauncher.core.domain.model

/**
 * One scanned book file. [uri] is a SAF document uri; the launcher never opens the file itself, it
 * hands that uri to the reader app the user chose.
 *
 * [title], [author], [series], [seriesIndex] and [coverUri] all come from one read of the EPUB's
 * package document during the scan. Any of them can be null: an EPUB is only obliged to carry a
 * title, and a book with no series metadata is a book with no series, never a guess made from its
 * file name.
 */
data class Book(
    val id: String,
    val libraryId: String,
    val uri: String,
    val displayName: String,
    val title: String? = null,
    val author: String? = null,
    val series: String? = null,
    /** Position within [series]. Fractional because a novella between books 2 and 3 is "2.5". */
    val seriesIndex: Double? = null,
    /** file:// uri of the cached cover thumbnail, or null when the book has no usable cover. */
    val coverUri: String? = null,
    val lastModified: Long? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    val relativePath: String? = null,
    val dateAdded: Long? = null,
) {
    /** What the list shows: the parsed title once there is one, else the file name. */
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: displayName

    /**
     * The series name, or null when the book declares none.
     *
     * Separate from [series] only so callers cannot accidentally group every metadata-less book
     * under an empty-string series, which is what a blank `<meta content="">` produces.
     */
    val seriesName: String? get() = series?.takeIf { it.isNotBlank() }
}
