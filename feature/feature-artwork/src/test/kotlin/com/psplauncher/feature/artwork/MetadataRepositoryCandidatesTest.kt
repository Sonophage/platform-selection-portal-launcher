package com.psplauncher.feature.artwork

import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.core.data.database.entity.GameEntity
import com.psplauncher.feature.artwork.api.IgdbApi
import com.psplauncher.feature.artwork.api.IgdbGameInfo
import com.psplauncher.feature.artwork.api.ScrapeOptions
import com.psplauncher.feature.artwork.api.SsGameInfo
import com.psplauncher.feature.artwork.api.SsLookupResult
import com.psplauncher.feature.artwork.api.SsLookupDiagnostics
import com.psplauncher.feature.artwork.rom.RomHasher
import com.psplauncher.feature.artwork.rom.RomIdentity
import com.psplauncher.feature.artwork.api.ScreenScraperApi
import com.psplauncher.feature.artwork.api.SgdbApiKeyProvider
import com.psplauncher.feature.artwork.api.SteamGridDbApi
import com.psplauncher.feature.artwork.store.ArtworkStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C16 task 3.1 — `fetchForGame`'s provider steps extracted into a write-free `fetchCandidates`
 * at the existing "nothing found" seam (AD-12).
 *
 * Two promises are pinned: retrieval never touches a `games` column or an artwork file, and the
 * batch scraper's behaviour is unchanged by the split.
 */
class MetadataRepositoryCandidatesTest {

    private val gameDao = mockk<GameDao>(relaxed = true)
    private val screenScraper = mockk<ScreenScraperApi>(relaxed = true)
    private val steamGridDb = mockk<SteamGridDbApi>(relaxed = true)
    private val igdbApi = mockk<IgdbApi>(relaxed = true)
    private val sgdbKeyProvider = mockk<SgdbApiKeyProvider>(relaxed = true)
    private val artworkStore = mockk<ArtworkStore>(relaxed = true)
    // Stubbed rather than left relaxed: a relaxed RomIdentity hands back "" for its nullable
    // String fields, and the COALESCE assertion below would then be pinning mockk's default
    // instead of the repository's behaviour. Nothing here has a ROM to hash.
    private val romHasher = mockk<RomHasher> {
        coEvery { identify(any(), any()) } returns RomIdentity(crc32 = null, sizeBytes = null, fileName = null)
    }

    private val repo = MetadataRepository(
        context = mockk(relaxed = true),
        gameDao = gameDao,
        screenScraper = screenScraper,
        romHasher = romHasher,
        steamGridDb = steamGridDb,
        steamStoreApi = mockk(relaxed = true),
        igdbApi = igdbApi,
        sgdbKeyProvider = sgdbKeyProvider,
        imageLoader = mockk(relaxed = true),
        artworkStore = artworkStore,
        httpClient = mockk(relaxed = true),
        videoSnapTranscoder = mockk(relaxed = true),
        ssMediaCacheDao = mockk(relaxed = true),
    )

    /**
     * A ScreenScraper hit carrying text and nothing else.
     *
     * These cases are about what the repository writes and does not write, not about any one
     * provider; they used TheGamesDB only because it was the smallest thing to stub. With that
     * provider gone, ScreenScraper is the one that still supplies a title and a description, so
     * it stands in. `medias` stays empty so nothing tries to cache media URLs.
     */
    private val ssHit = SsGameInfo(
        ssId = 7L,
        title = "Scraped Title",
        description = "A description",
        developer = null,
        publisher = null,
        releaseYear = 1994,
        genre = null,
        players = null,
        ageRating = null,
        franchise = null,
        communityRating = null,
        releaseDate = null,
        artworkUrl = null,
        boxArtUrl = null,
        box3dUrl = null,
        physicalMediaUrl = null,
        screenshotUrl = null,
        heroUrl = null,
        logoUrl = null,
        manualUrl = null,
        videoUrl = null,
        videoRawUrl = null,
    )

    private fun ssReturns(info: SsGameInfo?) {
        coEvery { screenScraper.fetchGameInfo(any(), any(), any()) } returns SsLookupResult(
            info = info,
            diagnostics = SsLookupDiagnostics(
                fileName = null,
                platformId = "snes",
                systemId = null,
                userCredentialsPresent = false,
                sentCrc = false,
            ),
        )
    }

    private fun givenGame(userTitleOverride: String? = null) {
        coEvery { gameDao.getById(1L) } returns GameEntity(
            id = 1L,
            title = "raw_rom_name",
            platformId = "snes",
            romPath = null,
            packageName = null,
            emulatorPackage = null,
            artworkUri = null,
            heroUri = null,
            logoUri = null,
            description = null,
            developer = null,
            publisher = null,
            releaseYear = null,
            genre = null,
            steamGridDbId = null,
            userTitleOverride = userTitleOverride,
        )
        coEvery { screenScraper.isEnabled() } returns true
        coEvery { sgdbKeyProvider.getKey() } returns null
    }

    @Test
    fun `fetchCandidates writes no game column and saves no artwork`() = runTest {
        givenGame()
        ssReturns(ssHit)
        coEvery { igdbApi.hasCredentials() } returns true
        coEvery { igdbApi.fetchGameInfo(any(), any()) } returns
            IgdbGameInfo(artworkUrl = "https://igdb/cover.jpg", heroUrl = null, logoUrl = null)

        val candidates = repo.fetchCandidates(1L, "raw_rom_name", "snes", romPath = null)

        assertEquals(ssHit, candidates.ssInfo)
        assertEquals("https://igdb/cover.jpg", candidates.igdbInfo?.artworkUrl)
        assertFalse(candidates.isEmpty)
        // The only thing retrieval may ask of the games table is to READ the row.
        coVerify(exactly = 1) { gameDao.getById(1L) }
        confirmVerified(gameDao)
        confirmVerified(artworkStore)
    }

    @Test
    fun `fetchCandidates searches by the user's title override, not the raw title`() = runTest {
        givenGame(userTitleOverride = "Chrono Trigger")
        // IGDB is the provider being asked by title, so it has to be reachable for the call to
        // happen at all, and ScreenScraper has to come back empty or IGDB is skipped as
        // unnecessary (it only runs when SS left artwork open).
        coEvery { igdbApi.hasCredentials() } returns true
        ssReturns(null)

        val candidates = repo.fetchCandidates(1L, "raw_rom_name", "snes", romPath = null)

        assertEquals("Chrono Trigger", candidates.bestTitle)
        // Asserted against IGDB because it is now the provider that is asked BY TITLE:
        // ScreenScraper is addressed by ROM hash or saved id and never sees the string.
        coVerify { igdbApi.fetchGameInfo("snes", "Chrono Trigger") }
    }

    @Test
    fun `metadata-only retrieval never asks the artwork-only providers`() = runTest {
        givenGame()
        coEvery { igdbApi.hasCredentials() } returns true
        coEvery { sgdbKeyProvider.getKey() } returns "sgdb-key"

        val candidates = repo.fetchCandidates(
            1L, "raw_rom_name", "snes", romPath = null,
            options = ScrapeOptions(metadataOnly = true),
        )

        assertNull(candidates.igdbInfo)
        assertNull(candidates.sgdbGameId)
        coVerify(exactly = 0) { igdbApi.fetchGameInfo(any(), any()) }
        coVerify(exactly = 0) { steamGridDb.searchGame(any()) }
    }

    @Test
    fun `nothing found is empty candidates, and fetchForGame still writes nothing`() = runTest {
        givenGame()
        ssReturns(null)

        assertTrue(repo.fetchCandidates(1L, "raw_rom_name", "snes", romPath = null).isEmpty)

        val result = repo.fetchForGame(1L, "raw_rom_name", "snes", romPath = null)

        assertFalse(result.success)
        assertEquals("none", result.source)
        coVerify(exactly = 2) { gameDao.getById(1L) }
        confirmVerified(gameDao)
        confirmVerified(artworkStore)
    }

    @Test
    fun `fetchForGame still persists the winners through the COALESCE write`() = runTest {
        givenGame()
        ssReturns(ssHit)

        val result = repo.fetchForGame(
            1L, "raw_rom_name", "snes", romPath = null,
            options = ScrapeOptions(metadataOnly = true),
        )

        assertTrue(result.success)
        assertEquals("screenscraper", result.source)
        assertEquals("Scraped Title", result.scrapedTitle)
        coVerify(exactly = 1) {
            gameDao.updateMetadata(
                id = 1L,
                description = "A description",
                developer = null,
                publisher = null,
                releaseYear = 1994,
                genre = null,
                artworkUri = null,
                heroUri = null,
                logoUri = null,
                iconUri = null,
                boxArtUri = null,
                physicalMediaUri = null,
                box3dUri = null,
                // The title deliberately does NOT ride this write. COALESCE would overwrite a
                // name the library already shows, which is the rename this repository was
                // changed to stop; it goes through the fill-only query asserted below instead.
                scrapedTitle = null,
                players = null,
                ageRating = null,
                franchise = null,
                communityRating = null,
                releaseDate = null,
                // The provider's own id rides the write, which is the point of persisting it: a
                // re-scrape fetches by id and skips matching entirely.
                ssId = 7L,
                igdbId = null,
                steamGridDbId = null,
                romCrc32 = null,
            )
        }
    }

    /**
     * The other half of the same contract.
     *
     * Asserting only that the title is absent from the COALESCE write would pass just as well if
     * the scrape never persisted a title at all, which is the opposite bug: a game the scan knew
     * only as a filename would stay unnamed forever. Both halves are pinned, in the same test
     * class, because each one alone is satisfied by a broken implementation.
     */
    @Test
    fun `a scrape fills the title through the fill-only write`() = runTest {
        givenGame()
        ssReturns(ssHit)

        repo.fetchForGame(
            1L, "raw_rom_name", "snes", romPath = null,
            options = ScrapeOptions(metadataOnly = true),
        )

        coVerify(exactly = 1) { gameDao.fillScrapedTitleIfMissing(1L, "Scraped Title") }
    }

    @Test
    fun `a scrape does not touch the title of a game the user has named`() = runTest {
        // user_title_override outranks scraped_title entirely, so filling the column would be
        // dead data at best. The guard lives in MetadataRepository, not in the SQL, so the SQL's
        // own "IS NULL" clause cannot be what catches this.
        givenGame(userTitleOverride = "The Name I Chose")
        ssReturns(ssHit)

        repo.fetchForGame(
            1L, "raw_rom_name", "snes", romPath = null,
            options = ScrapeOptions(metadataOnly = true),
        )

        coVerify(exactly = 0) { gameDao.fillScrapedTitleIfMissing(any(), any()) }
    }
}
