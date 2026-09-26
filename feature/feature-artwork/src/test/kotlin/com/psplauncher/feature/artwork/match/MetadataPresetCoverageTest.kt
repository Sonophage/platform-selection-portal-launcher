package com.psplauncher.feature.artwork.match

import com.psplauncher.feature.artwork.MetadataCandidates
import com.psplauncher.feature.artwork.api.SsGameInfo
import com.psplauncher.feature.artwork.api.SteamAppDetails
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataPresetCoverageTest {
    private fun candidatesAnsweredBy(provider: MatchProvider): MetadataCandidates {
        val base = MetadataCandidates(
            gameEntity = null,
            bestTitle = "The Elder Scrolls V: Skyrim Special Edition",
            ssInfo = null,
            romIdentity = null,
            usedSsCache = false,
            cachedSsId = null,
            igdbInfo = null,
            sgdbGameId = null,
            sgdbGridUrl = null,
            sgdbHeroUrl = null,
            sgdbLogoUrl = null,
            steamDetails = null,
            steamArt = null,
        )
        return when (provider) {
            MatchProvider.SCREENSCRAPER -> base.copy(
                ssInfo = SsGameInfo(
                    ssId = 1L, title = "Skyrim", description = "Dragons.", developer = "Bethesda",
                    publisher = "Bethesda", releaseYear = 2011, genre = "RPG", players = "1",
                    ageRating = null, franchise = null, communityRating = null, releaseDate = null,
                    artworkUrl = null, boxArtUrl = null, box3dUrl = null, physicalMediaUrl = null,
                    screenshotUrl = null, heroUrl = null, logoUrl = null, manualUrl = null,
                    videoUrl = null, videoRawUrl = null,
                ),
            )
            MatchProvider.STEAM_STORE -> base.copy(
                steamDetails = SteamAppDetails(
                    appId = "489830",
                    title = "The Elder Scrolls V: Skyrim Special Edition",
                    developer = "Bethesda Game Studios",
                    publisher = "Bethesda Softworks",
                    genre = "RPG",
                    releaseYear = 2016,
                    description = "Winter is coming.",
                ),
            )

            else -> base
        }
    }

    @Test
    fun `every provider that claims to supply metadata can actually produce a preset`() {
        val missing = ProviderCapabilities.metadataProviders.filter { provider ->
            MetadataApply.presetsFrom(candidatesAnsweredBy(provider))
                .none { it.provider == provider }
        }

        assertEquals(
            "these providers declare suppliesMetadata = true but presetsFrom cannot offer them, " +
                "so Update Metadata reports 'No source recognised this game' after they answer: $missing",
            emptyList<MatchProvider>(),
            missing,
        )
    }

    @Test
    fun `a provider that supplies no text is never offered a preset`() {
        val artworkOnly = MatchProvider.entries - ProviderCapabilities.metadataProviders.toSet()
        val offered = artworkOnly.filter { provider ->
            MetadataApply.presetsFrom(candidatesAnsweredBy(provider))
                .any { it.provider == provider }
        }

        assertEquals(emptyList<MatchProvider>(), offered)
    }

    @Test
    fun `Steam's preset carries the fields its store record actually has`() {
        val preset = MetadataApply.presetsFrom(candidatesAnsweredBy(MatchProvider.STEAM_STORE))
            .single { it.provider == MatchProvider.STEAM_STORE }

        assertEquals("Winter is coming.", preset.description)
        assertEquals("Bethesda Game Studios", preset.developer)
        assertEquals(2016, preset.releaseYear)
        assertEquals("RPG", preset.genre)
    }
}
