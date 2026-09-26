package com.psplauncher.core.domain.model

data class Book(
    val id: String,
    val libraryId: String,
    val uri: String,
    val displayName: String,
    val title: String? = null,
    val author: String? = null,
    val series: String? = null,

    val seriesIndex: Double? = null,

    val coverUri: String? = null,
    val lastModified: Long? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    val relativePath: String? = null,
    val dateAdded: Long? = null,

    val lastOpenedAt: Long? = null,
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: displayName

    val seriesName: String? get() = series?.takeIf { it.isNotBlank() }
}
