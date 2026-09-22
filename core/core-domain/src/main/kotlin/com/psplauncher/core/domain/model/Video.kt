package com.psplauncher.core.domain.model

/**
 * A single scanned video file. [uri] is a SAF document URI string (never a raw file path) so the
 * file can be opened by the built-in player (or, later, an external player) with a grantable read
 * permission. Mirrors [MusicTrack], extended with video metadata and resume-position state.
 */
data class Video(
    val id: String,
    val libraryId: String,
    val uri: String,
    val displayName: String,
    /** User-set display title override; falls back to [displayName]. */
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
    /** file:// uri of the generated thumbnail (a frame grab cached during scan), or null. */
    val thumbnailUri: String? = null,
    /** file:// or content:// uri of a user-chosen custom thumbnail; takes priority when set. */
    val customThumbnailUri: String? = null,
    /** A poster matched from TMDB, or null. See effectiveThumbnailUri for how it ranks. */
    val posterUri: String? = null,
    /** Last playback position in ms; 0 = start / fully watched-and-reset. */
    val resumePositionMs: Long = 0,
    val lastWatchedAt: Long? = null,
    val isFavorite: Boolean = false,
) {
    /** Best label for display: user title when set, else the file name. */
    /**
     * What the UI shows: the user's own title when they have set one, otherwise the filename
     * cleaned up — see MovieFileName. It used to be the raw filename, so a library of scene
     * releases listed itself as "Dune.2021.1080p.BluRay.1600MB.DD2.0.x264-GalaxyRG.mkv".
     */
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: MovieFileName.titleOf(displayName)

    /** The thumbnail to show: user custom art first, then the generated frame grab. */
    /**
     * The art to draw, best first: the user's own choice, then a matched poster, then the
     * scanner's frame grab.
     *
     * The poster sits in the middle deliberately. A frame grab from a film is usually a dark
     * still of nothing in particular — the owner's library showed Dune as an unlit face — and a
     * poster is what a film looks like. But a picture the user chose outranks anything matched
     * for them.
     */
    val effectiveThumbnailUri: String? get() = customThumbnailUri?.takeIf { it.isNotBlank() }
        ?: posterUri?.takeIf { it.isNotBlank() }
        ?: thumbnailUri

    /** "1920×1080"-style label, or null when resolution is unknown. */
    val resolutionLabel: String? get() =
        if (width != null && height != null && width > 0 && height > 0) "${width}×${height}" else null
}
