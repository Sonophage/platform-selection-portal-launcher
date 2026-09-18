package com.psplauncher.core.domain.model

/**
 * One scanned book file. [uri] is a SAF document uri; the launcher never opens the file itself, it
 * hands that uri to the reader app the user chose.
 *
 * [title] and [author] are null for now. The first pass reads no EPUB metadata, so the list shows
 * [displayName]; the fields exist so a later metadata pass is a scanner change rather than a
 * migration.
 */
data class Book(
    val id: String,
    val libraryId: String,
    val uri: String,
    val displayName: String,
    val title: String? = null,
    val author: String? = null,
    val lastModified: Long? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    val relativePath: String? = null,
    val dateAdded: Long? = null,
) {
    /** What the list shows: the parsed title once there is one, else the file name. */
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: displayName
}
