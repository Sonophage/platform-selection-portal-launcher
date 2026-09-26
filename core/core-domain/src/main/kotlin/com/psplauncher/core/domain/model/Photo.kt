package com.psplauncher.core.domain.model

data class Photo(
    val id: String,
    val libraryId: String,
    val uri: String,
    val displayName: String,
    val width: Int? = null,
    val height: Int? = null,

    val dateTaken: Long? = null,
    val lastModified: Long? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    val relativePath: String? = null,

    val thumbnailUri: String? = null,
    val dateAdded: Long? = null,
) {
    val resolutionLabel: String? get() =
        if (width != null && height != null && width > 0 && height > 0) "${width}×${height}" else null

    val displayDateMs: Long? get() = dateTaken ?: lastModified
}
