package com.psplauncher.feature.artwork.importer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ImportCandidate(
    @SerialName("platform_id") val platformId: String,

    val kind: String,
    @SerialName("document_id") val documentId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("size_bytes") val sizeBytes: Long,
)

data class DetectedImportSource(
    val sourceId: String,
    val label: String,
    val folderDocId: String,
    val systemsRootDocId: String,
    val systems: List<SystemFolder>,

    val gamelistDocIds: Map<String, String> = emptyMap(),
) {
    data class SystemFolder(val platformId: String, val docId: String, val folderName: String)
}

enum class MatchConfidence { EXACT_FILENAME, DISPLAY_TITLE, SIMPLIFIED_TITLE, INDEXED_FILENAME }

@Serializable
data class PlannedItem(
    val kind: String,
    @SerialName("document_id") val documentId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    val confidence: MatchConfidence,

    @SerialName("replaces_stale") val replacesStale: Boolean = false,
)

@Serializable
data class PlannedGame(
    @SerialName("game_id") val gameId: Long,
    @SerialName("platform_id") val platformId: String,
    @SerialName("artwork_key") val artworkKey: String,

    @SerialName("portable_name") val portableName: String = "",
    val title: String,
    @SerialName("rom_file_name") val romFileName: String? = null,
    val items: List<PlannedItem>,
)

@Serializable
data class AmbiguousCandidate(
    val candidate: ImportCandidate,
    @SerialName("game_ids") val gameIds: List<Long>,
    @SerialName("game_titles") val gameTitles: List<String>,
)

@Serializable
data class ImportPlan(
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_label") val sourceLabel: String,
    @SerialName("tree_uri") val treeUri: String,
    val games: List<PlannedGame>,
    val ambiguous: List<AmbiguousCandidate>,
    @SerialName("unmatched_count") val unmatchedCount: Int,
    @SerialName("skipped_existing_count") val skippedExistingCount: Int,
    @SerialName("unknown_system_folders") val unknownSystemFolders: List<String> = emptyList(),

    @SerialName("platforms_without_games") val platformsWithoutGames: List<String> = emptyList(),

    @SerialName("metadata_updates") val metadataUpdates: List<MetadataUpdate> = emptyList(),
) {
    val itemCount: Int get() = games.sumOf { it.items.size }
    val totalBytes: Long get() = games.sumOf { g -> g.items.sumOf { it.sizeBytes } }
    fun countsByKind(): Map<String, Int> =
        games.flatMap { it.items }.groupingBy { it.kind }.eachCount()

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun parse(text: String): ImportPlan? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
        fun encode(plan: ImportPlan): String = json.encodeToString(serializer(), plan)
    }
}

@Serializable
data class MetadataUpdate(
    @SerialName("game_id") val gameId: Long,
    val name: String? = null,
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    @SerialName("release_year") val releaseYear: Int? = null,
    val genre: String? = null,
)

@Serializable
data class ImportSummary(
    @SerialName("source_label") val sourceLabel: String,
    val transfer: String,
    val imported: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val ambiguous: Int = 0,
    val unmatched: Int = 0,
    @SerialName("bytes_written") val bytesWritten: Long = 0,
    @SerialName("metadata_applied") val metadataApplied: Int = 0,
    @SerialName("counts_by_kind") val countsByKind: Map<String, Int> = emptyMap(),
    @SerialName("unknown_system_folders") val unknownSystemFolders: List<String> = emptyList(),

    val errors: List<String> = emptyList(),
    val cancelled: Boolean = false,
) {
    companion object {
        const val MAX_ERRORS = 50
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun parse(text: String): ImportSummary? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
        fun encode(summary: ImportSummary): String = json.encodeToString(serializer(), summary)
    }
}
