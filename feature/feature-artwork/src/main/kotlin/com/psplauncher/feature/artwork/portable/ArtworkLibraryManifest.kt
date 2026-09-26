package com.psplauncher.feature.artwork.portable

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ArtworkLibraryManifest(
    @SerialName("format_version") val formatVersion: Int = FORMAT_VERSION,
    @SerialName("library_uuid") val libraryUuid: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("app_version") val appVersion: String = "",
    @SerialName("normalization_version") val normalizationVersion: Int = ArtworkNaming.NORMALIZATION_VERSION,
    @SerialName("entry_count_hint") val entryCountHint: Int = 0,
) {
    companion object {
        const val FORMAT_VERSION = 3
        const val FILE_NAME = "pfp-artwork-library.json"

        const val DIR_ARTWORK = "Artwork"

        const val DIR_GAMES = "games"

        const val DIR_IMPORT = "Import"

        const val MAX_BYTES = 64 * 1024

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun parse(text: String): ArtworkLibraryManifest? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()

        fun encode(manifest: ArtworkLibraryManifest): String =
            json.encodeToString(serializer(), manifest)
    }
}
