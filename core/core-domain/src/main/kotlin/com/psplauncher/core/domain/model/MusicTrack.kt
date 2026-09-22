package com.psplauncher.core.domain.model

/**
 * A single scanned audio file. [uri] is a SAF document URI string (never a raw file path) so the
 * track can be launched via an external player with a grantable read permission.
 */
data class MusicTrack(
    val id: String,
    val folderId: String,
    val uri: String,
    val displayName: String,
    val title: String? = null,
    val artist: String? = null,
    /**
     * Who the RECORD is by, as distinct from who is on this track.
     *
     * `artist` is the whole credit line the file was tagged with -- "Ab-Soul, Anderson .Paak,
     * James Blake" is one real value from this library -- so grouping by it lists credit
     * combinations, not artists. The album artist is the one tag that names a single act, and
     * it is what the Artists view groups on. Null on a file that does not carry one, which is
     * why [primaryArtist] exists rather than a bare field read.
     */
    val albumArtist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val lastModified: Long? = null,
    val trackNumber: Int? = null,
    val relativePath: String? = null,
    /** file:// uri of cached album art extracted during scan, or null when none was embedded. */
    val artUri: String? = null,
    /** When this track was last played here, or null if never. Not the file's mtime — see the entity. */
    val lastPlayedAt: Long? = null,
) {
    /** Best label for display: real title when scanned, else the file name. */
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: displayName
}

/**
 * The one act to file this track under.
 *
 * The album artist when the file carries one, else the credit line. The fallback is what keeps a
 * library that has not been rescanned since album_artist arrived looking exactly as it did --
 * wrong in the old way rather than empty in a new one -- and it is also right for a single by one
 * artist, which is most files that carry no album-artist tag at all.
 */
val MusicTrack.primaryArtist: String?
    get() = albumArtist?.trim()?.ifBlank { null } ?: artist
