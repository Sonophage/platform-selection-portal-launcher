package com.psplauncher.feature.artwork.match

import com.psplauncher.core.data.database.entity.GameEntity
import com.psplauncher.feature.artwork.MetadataCandidates

enum class MetadataField(val label: String) {
    TITLE("Title"),
    DESCRIPTION("Description"),
    DEVELOPER("Developer"),
    PUBLISHER("Publisher"),
    RELEASE_YEAR("Year"),
    RELEASE_DATE("Release Date"),
    GENRE("Genre"),
    AGE_RATING("Age Rating"),
    FRANCHISE("Franchise"),
    COMMUNITY_RATING("Rating"),
}

enum class MetadataApplyPolicy(val label: String) {
    REPLACE_ALL("Replace All"),

    FILL_MISSING_ONLY("Fill Missing Only"),

    CHOOSE_FIELDS("Choose Fields"),

    KEEP_CURRENT("Keep Current"),
}

data class MetadataFieldRow(
    val field: MetadataField,
    val current: Any?,
    val incoming: Any,
) {
    val differs: Boolean get() = incoming != current
}

data class MetadataPreview(
    val current: Map<MetadataField, Any?>,
    val presets: List<MetadataPreset>,
)

object MetadataApply {
    fun presetsFrom(candidates: MetadataCandidates): List<MetadataPreset> = listOfNotNull(
        candidates.ssInfo?.let { ss ->
            MetadataPreset(
                provider = MatchProvider.SCREENSCRAPER,
                title = ss.title,
                description = ss.description,
                developer = ss.developer,
                publisher = ss.publisher,
                releaseYear = ss.releaseYear,
                releaseDate = ss.releaseDate,
                genre = ss.genre,
                ageRating = ss.ageRating,
                franchise = ss.franchise,
                communityRating = ss.communityRating,
            )
        },
        candidates.steamDetails?.let { steam ->
            MetadataPreset(
                provider = MatchProvider.STEAM_STORE,
                title = steam.title,
                description = steam.description,
                developer = steam.developer,
                publisher = steam.publisher,
                releaseYear = steam.releaseYear,
                genre = steam.genre,

            )
        },
    ).filterNot { it.isEmpty }

    fun currentOf(game: GameEntity): Map<MetadataField, Any?> = mapOf(
        MetadataField.TITLE to game.scrapedTitle,
        MetadataField.DESCRIPTION to game.description,
        MetadataField.DEVELOPER to game.developer,
        MetadataField.PUBLISHER to game.publisher,
        MetadataField.RELEASE_YEAR to game.releaseYear,
        MetadataField.RELEASE_DATE to game.releaseDate,
        MetadataField.GENRE to game.genre,
        MetadataField.AGE_RATING to game.ageRating,
        MetadataField.FRANCHISE to game.franchise,
        MetadataField.COMMUNITY_RATING to game.communityRating,
    )

    fun incomingOf(preset: MetadataPreset): Map<MetadataField, Any> = buildMap {
        fun offer(field: MetadataField, value: Any?) {
            if (value == null || (value is String && value.isBlank())) return
            put(field, value)
        }
        offer(MetadataField.TITLE, preset.title)
        offer(MetadataField.DESCRIPTION, preset.description)
        offer(MetadataField.DEVELOPER, preset.developer)
        offer(MetadataField.PUBLISHER, preset.publisher)
        offer(MetadataField.RELEASE_YEAR, preset.releaseYear)
        offer(MetadataField.RELEASE_DATE, preset.releaseDate)
        offer(MetadataField.GENRE, preset.genre)
        offer(MetadataField.AGE_RATING, preset.ageRating)
        offer(MetadataField.FRANCHISE, preset.franchise)
        offer(MetadataField.COMMUNITY_RATING, preset.communityRating)
    }

    fun rows(current: Map<MetadataField, Any?>, incoming: MetadataPreset): List<MetadataFieldRow> {
        val values = incomingOf(incoming)
        return MetadataField.entries.mapNotNull { field ->
            values[field]?.let { MetadataFieldRow(field, current[field], it) }
        }
    }

    fun changedFields(current: Map<MetadataField, Any?>, incoming: MetadataPreset): Set<MetadataField> =
        rows(current, incoming).filter { it.differs }.mapTo(mutableSetOf()) { it.field }

    fun plan(
        current: Map<MetadataField, Any?>,
        incoming: MetadataPreset,
        policy: MetadataApplyPolicy,
        chosen: Set<MetadataField>,
    ): Map<MetadataField, Any> {
        val changes = rows(current, incoming).filter { it.differs }
        return when (policy) {
            MetadataApplyPolicy.REPLACE_ALL -> changes
            MetadataApplyPolicy.FILL_MISSING_ONLY -> changes.filter { current[it.field] == null }
            MetadataApplyPolicy.CHOOSE_FIELDS -> changes.filter { it.field in chosen }
            MetadataApplyPolicy.KEEP_CURRENT -> emptyList()
        }.associate { it.field to it.incoming }
    }
}
