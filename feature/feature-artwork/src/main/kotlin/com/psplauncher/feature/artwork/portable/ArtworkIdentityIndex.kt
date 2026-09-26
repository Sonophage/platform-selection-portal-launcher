package com.psplauncher.feature.artwork.portable

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import timber.log.Timber
import java.util.Locale

@Serializable
data class ArtworkIdentityIndex(
    @SerialName("format_version") val formatVersion: Int = FORMAT_VERSION,
    @SerialName("updated_at") val updatedAt: Long = 0,
    val entries: List<Entry> = emptyList(),
) {
    @Serializable
    data class Entry(
        @SerialName("platform_id") val platformId: String,

        val kind: String,

        @SerialName("portable_name") val portableName: String,
        @SerialName("rom_crc32") val romCrc32: String? = null,
        @SerialName("ss_id") val ssId: Long? = null,
        @SerialName("igdb_id") val igdbId: Long? = null,
        @SerialName("sgdb_id") val sgdbId: Long? = null,

        @SerialName("artwork_key") val artworkKey: String? = null,
    ) {
        fun tokens(): List<String> = tokensOf(romCrc32, ssId, igdbId, sgdbId, artworkKey)
    }

    private val byFile: Map<Triple<String, String, String>, Entry> by lazy {
        entries.associateBy { keyOf(it.platformId, it.kind, it.portableName) }
    }

    fun find(platformId: String, kind: String, portableName: String): Entry? =
        byFile[keyOf(platformId, kind, portableName)]

    fun upsert(entry: Entry): ArtworkIdentityIndex {
        val key = keyOf(entry.platformId, entry.kind, entry.portableName)
        return copy(entries = entries.filterNot { keyOf(it.platformId, it.kind, it.portableName) == key } + entry)
    }

    fun upsertAll(rows: List<Entry>): ArtworkIdentityIndex {
        if (rows.isEmpty()) return this
        val merged = LinkedHashMap<Triple<String, String, String>, Entry>(entries.size + rows.size)
        entries.forEach { merged[keyOf(it.platformId, it.kind, it.portableName)] = it }
        rows.forEach { merged[keyOf(it.platformId, it.kind, it.portableName)] = it }
        return copy(entries = merged.values.toList())
    }

    companion object {
        const val FORMAT_VERSION = 1
        const val FILE_NAME = "pfp-artwork-identity.json"

        const val MAX_BYTES = 4 * 1024 * 1024

        fun tokensOf(
            romCrc32: String?,
            ssId: Long?,
            igdbId: Long?,
            sgdbId: Long?,
            artworkKey: String?,
        ): List<String> = buildList {
            romCrc32?.takeIf { it.isNotBlank() }?.let { add("crc:${it.uppercase(Locale.US)}") }
            ssId?.let { add("ss:$it") }
            igdbId?.let { add("igdb:$it") }
            sgdbId?.let { add("sgdb:$it") }
            artworkKey?.takeIf { it.isNotBlank() }?.let { add("key:$it") }
        }

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        private fun keyOf(platformId: String, kind: String, portableName: String) =
            Triple(
                platformId.lowercase(Locale.US),
                kind.uppercase(Locale.US),
                portableName.lowercase(Locale.US),
            )

        fun parse(text: String): ArtworkIdentityIndex? = runCatching {
            val root = json.parseToJsonElement(text) as? JsonObject ?: return@runCatching null
            val formatVersion = root["format_version"]?.jsonPrimitive?.intOrNull ?: FORMAT_VERSION
            if (formatVersion > FORMAT_VERSION) return@runCatching null
            val updatedAt = root["updated_at"]?.jsonPrimitive?.longOrNull ?: 0L

            val rawEntries = root["entries"] as? JsonArray ?: return@runCatching null
            var dropped = 0
            val entries = rawEntries.mapNotNull { element ->
                runCatching { json.decodeFromJsonElement(Entry.serializer(), element) }
                    .getOrElse { dropped++; null }
            }
            if (dropped > 0) Timber.w("Artwork identity index: dropped $dropped malformed row(s)")
            ArtworkIdentityIndex(formatVersion = formatVersion, updatedAt = updatedAt, entries = entries)
        }.getOrNull()

        fun encode(index: ArtworkIdentityIndex): String =
            json.encodeToString(serializer(), index)
    }
}
