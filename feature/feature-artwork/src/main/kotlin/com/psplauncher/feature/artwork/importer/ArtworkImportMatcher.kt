package com.psplauncher.feature.artwork.importer

import com.psplauncher.feature.artwork.portable.ArtworkNaming

object ArtworkImportMatcher {
    data class GameRef(
        val id: Long,
        val romStem: String?,
        val displayTitle: String,
        val scrapedTitle: String?,
    )

    sealed interface Result {
        data class Matched(val gameIds: List<Long>, val confidence: MatchConfidence) : Result {
            constructor(gameId: Long, confidence: MatchConfidence) : this(listOf(gameId), confidence)
        }
        data class Ambiguous(val gameIds: List<Long>) : Result
        data object Unmatched : Result
    }

    private val INDEX_PREFIX = Regex("""^\d{2,6}\s*-\s*""")

    class PlatformIndex(games: List<GameRef>) {
        private val byRomStem = games.filter { it.romStem != null }
            .groupBy({ ArtworkNaming.normalizeForMatch(it.romStem!!) }, { it.id })
        private val byTitle = buildMap<String, MutableList<Long>> {
            games.forEach { g ->
                val titles = listOfNotNull(g.displayTitle, g.scrapedTitle)
                    .map { ArtworkNaming.normalizeForMatch(it) }.distinct()
                titles.forEach { getOrPut(it) { mutableListOf() }.add(g.id) }
            }
        }
        private val bySimplified = buildMap<String, MutableList<Long>> {
            games.forEach { g ->
                val keys = listOfNotNull(g.romStem, g.displayTitle, g.scrapedTitle)
                    .map { ArtworkNaming.simplifyTitle(it) }.filter { it.isNotBlank() }.distinct()
                keys.forEach { getOrPut(it) { mutableListOf() }.add(g.id) }
            }
        }

        fun match(artworkFileName: String): Result {
            val stem = ArtworkNaming.fileStem(artworkFileName)
            matchStem(stem)?.let { return it }

            val stripped = stem.replace(INDEX_PREFIX, "")
            if (stripped != stem && stripped.isNotBlank()) {
                matchStem(stripped)?.let { result ->
                    return if (result is Result.Matched) {
                        result.copy(confidence = MatchConfidence.INDEXED_FILENAME)
                    } else result
                }
            }
            return Result.Unmatched
        }

        private fun matchStem(stem: String): Result? {
            byRomStem[ArtworkNaming.normalizeForMatch(stem)]?.let {
                return Result.Matched(it.distinct(), MatchConfidence.EXACT_FILENAME)
            }
            byTitle[ArtworkNaming.normalizeForMatch(stem)]?.let {
                return singleOrAmbiguous(it, MatchConfidence.DISPLAY_TITLE)
            }
            val simplified = ArtworkNaming.simplifyTitle(stem)
            if (simplified.isNotBlank()) {
                bySimplified[simplified]?.let { ids ->

                    return if (ids.distinct().size == 1) {
                        Result.Matched(ids.distinct(), MatchConfidence.SIMPLIFIED_TITLE)
                    } else {
                        Result.Ambiguous(ids.distinct())
                    }
                }
            }
            return null
        }

        private fun singleOrAmbiguous(ids: List<Long>, confidence: MatchConfidence): Result {
            val distinct = ids.distinct()
            return if (distinct.size == 1) Result.Matched(distinct, confidence)
            else Result.Ambiguous(distinct)
        }
    }
}
