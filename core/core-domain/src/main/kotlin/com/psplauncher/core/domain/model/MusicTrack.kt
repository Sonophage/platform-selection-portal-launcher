package com.psplauncher.core.domain.model

data class MusicTrack(
    val id: String,
    val folderId: String,
    val uri: String,
    val displayName: String,
    val title: String? = null,
    val artist: String? = null,

    val albumArtist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val lastModified: Long? = null,
    val trackNumber: Int? = null,
    val relativePath: String? = null,

    val artUri: String? = null,

    val lastPlayedAt: Long? = null,
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: displayName
}

val MusicTrack.primaryArtist: String?
    get() = albumArtist?.trim()?.ifBlank { null } ?: artist
