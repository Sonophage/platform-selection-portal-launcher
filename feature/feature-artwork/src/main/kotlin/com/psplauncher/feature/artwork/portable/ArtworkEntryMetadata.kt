package com.psplauncher.feature.artwork.portable

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ArtworkEntryMetadata(
    @SerialName("format_version") val formatVersion: Int = FORMAT_VERSION,
    val key: String,
    @SerialName("platform_id") val platformId: String,
    val title: String = "",
    @SerialName("scraped_title") val scrapedTitle: String? = null,

    @SerialName("rom_file_name") val romFileName: String? = null,
    @SerialName("rom_size") val romSize: Long? = null,
    @SerialName("rom_crc32") val romCrc32: String? = null,

    @SerialName("ss_id") val ssId: Long? = null,
    @SerialName("sgdb_id") val sgdbId: Long? = null,
    @SerialName("igdb_id") val igdbId: Long? = null,
    val assets: List<AssetRecord> = emptyList(),
    @SerialName("updated_at") val updatedAt: Long = 0,
) {
    @Serializable
    data class AssetRecord(

        val kind: String,
        @SerialName("file_name") val fileName: String,

        val source: String,
        @SerialName("original_file_name") val originalFileName: String? = null,
        @SerialName("size_bytes") val sizeBytes: Long = 0,
        @SerialName("saved_at") val savedAt: Long = 0,

        val locked: Boolean = false,
    )

    fun withAsset(record: AssetRecord, nowMillis: Long = System.currentTimeMillis()): ArtworkEntryMetadata =
        copy(assets = assets.filterNot { it.kind == record.kind } + record, updatedAt = nowMillis)

    companion object {
        const val FORMAT_VERSION = 1
        const val FILE_NAME = "metadata.json"
        const val MAX_BYTES = 256 * 1024

        const val SOURCE_IMPORT_ESDE = "import-esde"

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun parse(text: String): ArtworkEntryMetadata? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()

        fun encode(metadata: ArtworkEntryMetadata): String =
            json.encodeToString(serializer(), metadata)
    }
}
