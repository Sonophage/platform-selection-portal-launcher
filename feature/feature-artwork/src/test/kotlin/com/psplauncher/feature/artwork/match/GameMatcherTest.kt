package com.psplauncher.feature.artwork.match

import com.psplauncher.core.domain.model.Game
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameMatcherTest {
    private fun game(
        title: String = "Final Fantasy VI Advance",
        platformId: String = "gba",
        ssId: Long? = null,
        igdbId: Long? = null,
        steamGridDbId: Long? = null,
        romCrc32: String? = null,
        storefront: String? = null,
        storefrontGameId: String? = null,
    ) = Game(
        id = 1L,
        title = title,
        platformId = platformId,
        ssId = ssId,
        igdbId = igdbId,
        steamGridDbId = steamGridDbId,
        romCrc32 = romCrc32,
        storefront = storefront,
        storefrontGameId = storefrontGameId,
    )

    private class FakeEvidence(
        val byRomHash: Map<String, GameCandidate> = emptyMap(),
        val byStorefront: Map<Pair<String, String>, GameCandidate> = emptyMap(),
        val byTitle: List<GameCandidate> = emptyList(),
    ) : MatchEvidenceSource {
        val asked = mutableListOf<String>()

        override suspend fun candidateByRomHash(
            provider: MatchProvider,
            crc32: String,
            platformId: String,
        ): GameCandidate? {
            asked += "rom:$crc32"
            return byRomHash[crc32.uppercase()]
        }

        override suspend fun candidateByStorefront(
            provider: MatchProvider,
            storefront: String,
            storefrontGameId: String,
        ): GameCandidate? {
            asked += "store:$storefront/$storefrontGameId"
            return byStorefront[storefront to storefrontGameId]
        }

        override suspend fun searchByTitle(
            provider: MatchProvider,
            query: String,
            platformId: String,
        ): List<GameCandidate> {
            asked += "title:$query"
            return byTitle
        }
    }

    private fun candidate(
        provider: MatchProvider = MatchProvider.STEAMGRIDDB,
        id: String = "9001",
        title: String = "Final Fantasy VI Advance",
    ) = GameCandidate(provider = provider, providerGameId = id, title = title)

    @Test
    fun `a saved provider id wins outright and consults no evidence source`() = runTest {
        val evidence = FakeEvidence(byRomHash = mapOf("ABCD1234" to candidate()))
        val matcher = GameMatcher(evidence)

        val match = matcher.resolve(
            game(ssId = 4242L, romCrc32 = "abcd1234"),
            MatchProvider.SCREENSCRAPER,
        )

        assertEquals(MatchTier.SAVED_PROVIDER_ID, match?.tier)
        assertEquals("4242", match?.candidate?.providerGameId)
        assertEquals(MatchProvider.SCREENSCRAPER, match?.candidate?.provider)
        assertTrue("stronger tier must short-circuit", evidence.asked.isEmpty())
    }

    @Test
    fun `a saved id is never read across providers`() = runTest {
        val matcher = GameMatcher(FakeEvidence())

        assertNull(matcher.resolve(game(steamGridDbId = 77L), MatchProvider.IGDB))
        assertEquals(
            "77",
            matcher.resolve(game(steamGridDbId = 77L), MatchProvider.STEAMGRIDDB)?.candidate?.providerGameId,
        )
    }

    @Test
    fun `a ROM checksum resolves ScreenScraper when no id is saved`() = runTest {
        val hit = candidate(provider = MatchProvider.SCREENSCRAPER, id = "551")
        val matcher = GameMatcher(FakeEvidence(byRomHash = mapOf("ABCD1234" to hit)))

        val match = matcher.resolve(game(romCrc32 = "abcd1234"), MatchProvider.SCREENSCRAPER)

        assertEquals(MatchTier.CONTENT_ID, match?.tier)
        assertEquals("551", match?.candidate?.providerGameId)
    }

    @Test
    fun `skipping the ROM checksum tier goes straight to the title search`() = runTest {
        val evidence = FakeEvidence(
            byRomHash = mapOf("ABCD1234" to candidate(provider = MatchProvider.SCREENSCRAPER, id = "551")),
            byTitle = listOf(candidate(provider = MatchProvider.SCREENSCRAPER, id = "552")),
        )
        val matcher = GameMatcher(evidence)

        val match = matcher.resolve(game(romCrc32 = "abcd1234"), MatchProvider.SCREENSCRAPER, skipRomHash = true)

        assertEquals(MatchTier.EXACT_TITLE, match?.tier)
        assertEquals("552", match?.candidate?.providerGameId)
        assertEquals(listOf("title:Final Fantasy VI Advance"), evidence.asked)
    }

    @Test
    fun `a ROM checksum is not offered to a provider that cannot take one`() = runTest {
        val evidence = FakeEvidence(byRomHash = mapOf("ABCD1234" to candidate()))
        val matcher = GameMatcher(evidence)

        matcher.resolve(game(romCrc32 = "abcd1234"), MatchProvider.IGDB)

        assertTrue(evidence.asked.none { it.startsWith("rom:") })
    }

    @Test
    fun `a storefront match requires the whole pair`() = runTest {
        val hit = candidate(id = "620")
        val evidence = FakeEvidence(byStorefront = mapOf(("STEAM" to "620") to hit))
        val matcher = GameMatcher(evidence)

        val steam = matcher.resolve(
            game(platformId = "windows", storefront = "STEAM", storefrontGameId = "620"),
            MatchProvider.STEAMGRIDDB,
        )
        assertEquals(MatchTier.CONTENT_ID, steam?.tier)

        val gog = matcher.resolve(
            game(platformId = "windows", storefront = "GOG", storefrontGameId = "620"),
            MatchProvider.STEAMGRIDDB,
        )
        assertNull(gog)
    }

    @Test
    fun `a half-populated storefront pair is never consulted`() = runTest {
        val evidence = FakeEvidence()
        val matcher = GameMatcher(evidence)

        matcher.resolve(game(storefront = "STEAM", storefrontGameId = null), MatchProvider.STEAMGRIDDB)

        assertTrue(evidence.asked.none { it.startsWith("store:") })
    }

    @Test
    fun `one exact normalized title hit resolves`() = runTest {
        val matcher = GameMatcher(
            FakeEvidence(byTitle = listOf(candidate(title = "  final   fantasy VI advance (USA) "))),
        )

        val match = matcher.resolve(game(), MatchProvider.STEAMGRIDDB)

        assertEquals(MatchTier.EXACT_TITLE, match?.tier)
    }

    @Test
    fun `two exact hits are ambiguous and resolve to nothing`() = runTest {
        val matcher = GameMatcher(
            FakeEvidence(
                byTitle = listOf(
                    candidate(id = "1", title = "Final Fantasy VI Advance"),
                    candidate(id = "2", title = "Final Fantasy VI Advance"),
                ),
            ),
        )

        assertNull(matcher.resolve(game(), MatchProvider.STEAMGRIDDB))
    }

    @Test
    fun `near titles are not exact and do not resolve`() = runTest {
        val matcher = GameMatcher(FakeEvidence(byTitle = listOf(candidate(title = "Final Fantasy VI"))))

        assertNull(matcher.resolve(game(), MatchProvider.STEAMGRIDDB))
    }

    @Test
    fun `searchable is the providers that actually have a title endpoint, not all of them`() {
        assertEquals(
            MatchProvider.entries.filter { ProviderCapabilities[it].supportsTitleSearch },
            ProviderCapabilities.searchable,
        )
    }

    @Test
    fun `a provider with no title endpoint cannot reach the Change Match picker`() {
        assertFalse(MatchProvider.STEAM_STORE in ProviderCapabilities.searchable)
        assertFalse(ProviderCapabilities[MatchProvider.STEAM_STORE].supportsTitleSearch)
    }

    @Test
    fun `every provider is addressable somehow`() {
        MatchProvider.entries.forEach { provider ->
            val c = ProviderCapabilities[provider]
            assertTrue(
                "$provider cannot be addressed by anything",
                c.addressableBySavedId || c.addressableByRomHash ||
                    c.addressableByStorefrontId || c.supportsTitleSearch,
            )
        }
    }

    @Test
    fun `every provider supplies something`() {
        MatchProvider.entries.forEach { provider ->
            val c = ProviderCapabilities[provider]
            assertTrue("$provider supplies nothing", c.suppliesMetadata || c.suppliesArtwork)
        }
    }

    @Test
    fun `ScreenScraper resolves a game with no ROM by its exact title`() = runTest {
        val evidence = FakeEvidence(
            byTitle = listOf(
                candidate(provider = MatchProvider.SCREENSCRAPER, id = "88", title = "Hollow Knight"),
                candidate(provider = MatchProvider.SCREENSCRAPER, id = "89", title = "Hollow Knight: Silksong"),
            ),
        )

        val match = GameMatcher(evidence).resolve(
            game(title = "Hollow Knight", platformId = "windows"),
            MatchProvider.SCREENSCRAPER,
        )

        assertEquals(MatchTier.EXACT_TITLE, match?.tier)
        assertEquals("SCREENSCRAPER:88", match?.matchKey)
        assertTrue("no ROM, so no checksum lookup", evidence.asked.none { it.startsWith("rom:") })
    }

    @Test
    fun `IGDB resolves the unique exact title among near misses`() = runTest {
        val evidence = FakeEvidence(
            byTitle = listOf(
                candidate(provider = MatchProvider.IGDB, id = "421", title = "Final Fantasy VI"),
                candidate(provider = MatchProvider.IGDB, id = "3506", title = "Final Fantasy VI Advance"),
            ),
        )
        val matcher = GameMatcher(evidence)

        val match = matcher.resolve(game(), MatchProvider.IGDB)

        assertEquals(MatchTier.EXACT_TITLE, match?.tier)
        assertEquals("IGDB:3506", match?.matchKey)
        assertTrue(evidence.asked.contains("title:Final Fantasy VI Advance"))
    }

    @Test
    fun `an explicit query overrides the game title`() = runTest {
        val evidence = FakeEvidence(byTitle = listOf(candidate(title = "Chrono Trigger")))
        val matcher = GameMatcher(evidence)

        val match = matcher.resolve(game(), MatchProvider.STEAMGRIDDB, query = "chrono trigger")

        assertEquals(MatchTier.EXACT_TITLE, match?.tier)
        assertTrue(evidence.asked.contains("title:chrono trigger"))
    }

    @Test
    fun `match keys are provider-qualified so two providers ids never collide`() = runTest {
        val a = GameMatch(candidate(provider = MatchProvider.IGDB, id = "12"), MatchTier.SAVED_PROVIDER_ID)
        val b = GameMatch(candidate(provider = MatchProvider.SCREENSCRAPER, id = "12"), MatchTier.SAVED_PROVIDER_ID)

        assertEquals("IGDB:12", a.matchKey)
        assertTrue(a.matchKey != b.matchKey)
    }

    @Test
    fun `title keying drops release tags punctuation and case`() {
        assertEquals(TitleKey.of("Final Fantasy VI Advance"), TitleKey.of("final fantasy vi advance"))
        assertEquals(TitleKey.of("Final Fantasy VI Advance"), TitleKey.of("Final Fantasy VI Advance (USA)"))
        assertEquals(TitleKey.of("Marvel vs. Capcom"), TitleKey.of("Marvel vs Capcom"))
        assertTrue(TitleKey.of("Final Fantasy VI") != TitleKey.of("Final Fantasy VII"))
    }

    @Test
    fun `a title that is only a tag keeps an identity of its own`() {
        assertEquals("[bios]", TitleKey.of("[BIOS]"))
        assertTrue(TitleKey.of("[BIOS]") != TitleKey.of(""))
    }
}
