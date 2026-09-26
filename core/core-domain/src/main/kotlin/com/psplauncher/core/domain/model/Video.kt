package com.psplauncher.core.domain.model

data class Video(
    val id: String,
    val libraryId: String,
    val uri: String,
    val displayName: String,

    val title: String? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val codec: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val dateAdded: Long? = null,
    val lastModified: Long? = null,
    val relativePath: String? = null,

    val thumbnailUri: String? = null,

    val customThumbnailUri: String? = null,

    val posterUri: String? = null,

    val resumePositionMs: Long = 0,
    val lastWatchedAt: Long? = null,
    val isFavorite: Boolean = false,
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: MovieFileName.titleOf(displayName)

    val effectiveThumbnailUri: String? get() = customThumbnailUri?.takeIf { it.isNotBlank() }
        ?: posterUri?.takeIf { it.isNotBlank() }
        ?: thumbnailUri

    val resolutionLabel: String? get() =
        if (width != null && height != null && width > 0 && height > 0) "${width}×${height}" else null
}
