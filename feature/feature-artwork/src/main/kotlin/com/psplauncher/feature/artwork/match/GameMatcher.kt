package com.psplauncher.feature.artwork.match

import com.psplauncher.core.domain.model.Game
import java.util.Locale

object TitleKey {
    private val WHITESPACE = Regex("\\s+")

    private val TAGS = Regex("[\\(\\[][^\\)\\]]*[\\)\\]]")

    private val NOISE = Regex("[\\p{Punct}&&[^&]]")

    fun of(raw: String): String {
        val trimmed = raw.trim()
        val stripped = trimmed
            .replace(TAGS, " ")
            .replace(NOISE, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .lowercase(Locale.US)
        return stripped.ifBlank { trimmed.lowercase(Locale.US) }
    }

    fun same(a: String, b: String): Boolean = of(a) == of(b)
}

interface MatchEvidenceSource {
    suspend fun candidateByRomHash(
        provider: MatchProvider,
        crc32: String,
        platformId: String,
    ): GameCandidate?

    suspend fun candidateByStorefront(
        provider: MatchProvider,
        storefront: String,
        storefrontGameId: String,
    ): GameCandidate?

    suspend fun searchByTitle(
        provider: MatchProvider,
        query: String,
        platformId: String,
    ): List<GameCandidate>

    suspend fun titleForSavedId(provider: MatchProvider, providerGameId: String): String? = null
}

class GameMatcher(private val evidence: MatchEvidenceSource) {
    suspend fun resolve(
        game: Game,
        provider: MatchProvider,
        query: String? = null,
        skipRomHash: Boolean = false,
    ): GameMatch? {
        val capability = ProviderCapabilities[provider]

        if (capability.addressableBySavedId) {
            savedIdFor(game, provider)?.let { id ->
                return GameMatch(
                    candidate = GameCandidate(
                        provider = provider,
                        providerGameId = id,
                        title = evidence.titleForSavedId(provider, id) ?: game.displayTitle,
                    ),
                    tier = MatchTier.SAVED_PROVIDER_ID,
                )
            }
        }

        val crc = game.romCrc32?.trim()?.takeIf { it.isNotEmpty() }
        if (capability.addressableByRomHash && crc != null && !skipRomHash) {
            evidence.candidateByRomHash(provider, crc, game.platformId)?.let {
                return GameMatch(it.copy(provider = provider), MatchTier.CONTENT_ID)
            }
        }

        val store = game.storefront?.trim()?.takeIf { it.isNotEmpty() }
        val storeId = game.storefrontGameId?.trim()?.takeIf { it.isNotEmpty() }
        if (capability.addressableByStorefrontId && store != null && storeId != null) {
            evidence.candidateByStorefront(provider, store, storeId)?.let {
                return GameMatch(it.copy(provider = provider), MatchTier.CONTENT_ID)
            }
        }

        return tierThree(game, provider, capability, query)
    }

    private suspend fun tierThree(
        game: Game,
        provider: MatchProvider,
        capability: ProviderCapability,
        query: String?,
    ): GameMatch? {
        if (!capability.supportsTitleSearch) return null

        val raw = query?.takeIf { it.isNotBlank() } ?: game.displayTitle
        val wanted = TitleKey.of(raw)
        if (wanted.isBlank()) return null

        val exact = evidence.searchByTitle(provider, raw.trim(), game.platformId)
            .filter { TitleKey.of(it.title) == wanted }

        val only = exact.singleOrNull() ?: return null
        return GameMatch(only.copy(provider = provider), MatchTier.EXACT_TITLE)
    }

    private fun savedIdFor(game: Game, provider: MatchProvider): String? = when (provider) {
        MatchProvider.SCREENSCRAPER -> game.ssId
        MatchProvider.IGDB -> game.igdbId
        MatchProvider.STEAMGRIDDB -> game.steamGridDbId

        MatchProvider.STEAM_STORE -> null
    }?.takeIf { it > 0 }?.toString()
}
