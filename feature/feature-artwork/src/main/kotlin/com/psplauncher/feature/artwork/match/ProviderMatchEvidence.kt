package com.psplauncher.feature.artwork.match

import com.psplauncher.feature.artwork.api.steamAppIdOf
import com.psplauncher.feature.artwork.api.IgdbApi
import com.psplauncher.feature.artwork.api.ScreenScraperApi
import com.psplauncher.feature.artwork.api.SsSearchHit
import com.psplauncher.feature.artwork.api.SteamGridDbApi
import com.psplauncher.feature.artwork.rom.RomIdentity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderMatchEvidence @Inject constructor(
    private val steamGridDb: SteamGridDbApi,
    private val screenScraper: ScreenScraperApi,
    private val igdbApi: IgdbApi,
) : MatchEvidenceSource {
    override suspend fun candidateByRomHash(
        provider: MatchProvider,
        crc32: String,
        platformId: String,
    ): GameCandidate? {
        if (provider != MatchProvider.SCREENSCRAPER) return null
        val info = runCatching {
            screenScraper.fetchGameInfo(
                platformId = platformId,
                rom = RomIdentity(crc32 = crc32, sizeBytes = null, fileName = null),
            ).info
        }.onFailure { Timber.d(it, "SS crc lookup failed for %s", crc32) }.getOrNull() ?: return null

        val id = info.ssId ?: return null
        return GameCandidate(
            provider = MatchProvider.SCREENSCRAPER,
            providerGameId = id.toString(),
            title = info.title.orEmpty().ifBlank { return null },
            releaseYear = info.releaseYear,
        )
    }

    override suspend fun candidateByStorefront(
        provider: MatchProvider,
        storefront: String,
        storefrontGameId: String,
    ): GameCandidate? {
        if (provider != MatchProvider.STEAMGRIDDB) return null

        val appId = steamAppIdOf(storefront, storefrontGameId) ?: return null

        val game = steamGridDb.getGameBySteamAppId(appId) ?: return null
        return GameCandidate(
            provider = MatchProvider.STEAMGRIDDB,
            providerGameId = game.id.toString(),
            title = game.name,
        )
    }

    override suspend fun searchByTitle(
        provider: MatchProvider,
        query: String,
        platformId: String,
    ): List<GameCandidate> = when (provider) {
        MatchProvider.STEAM_STORE -> emptyList()
        MatchProvider.STEAMGRIDDB -> steamGridDb.searchGame(query)
            .onFailure { Timber.d(it, "SGDB search failed for '%s'", query) }
            .getOrDefault(emptyList())
            .map {
                GameCandidate(
                    provider = MatchProvider.STEAMGRIDDB,
                    providerGameId = it.id.toString(),
                    title = it.name,
                    releaseYear = it.releaseDate?.let(::yearOf),
                )
            }

        MatchProvider.IGDB -> igdbApi.searchGames(query).mapNotNull { game ->
            val name = game.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GameCandidate(
                provider = MatchProvider.IGDB,
                providerGameId = game.id.toString(),
                title = name,
                releaseYear = game.firstReleaseDate?.let(::yearOf),
                thumbUrl = game.cover?.imageId?.let { IgdbApi.coverThumbUrl(it) },
            )
        }

        MatchProvider.SCREENSCRAPER ->
            if (platformId in PLATFORMS_WITHOUT_ROMS) emptyList()
            else screenScraper.searchGames(platformId, query).map(::ssCandidate)
    }

    suspend fun searchScreenScraperOnAnyPlatform(query: String, preferredPlatformId: String): List<GameCandidate> {
        val preferredSystem = ScreenScraperApi.PLATFORM_IDS[preferredPlatformId]
        return screenScraper.searchGames(platformId = null, title = query)
            .sortedByDescending { preferredSystem != null && it.systemId == preferredSystem }
            .map(::ssCandidate)
    }

    fun searchesEveryPlatformFirst(provider: MatchProvider, platformId: String): Boolean =
        provider == MatchProvider.SCREENSCRAPER && platformId in PLATFORMS_WITHOUT_ROMS

    private fun ssCandidate(hit: SsSearchHit) = GameCandidate(
        provider = MatchProvider.SCREENSCRAPER,
        providerGameId = hit.ssId.toString(),
        title = hit.title,
        platformName = hit.systemName,
        releaseYear = hit.releaseYear,
        gameArtCount = hit.gameArtCount,
    )

    private fun yearOf(epochSeconds: Long): Int =
        java.time.Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneOffset.UTC).year

    private companion object {
        val PLATFORMS_WITHOUT_ROMS = setOf("windows")
    }
}
