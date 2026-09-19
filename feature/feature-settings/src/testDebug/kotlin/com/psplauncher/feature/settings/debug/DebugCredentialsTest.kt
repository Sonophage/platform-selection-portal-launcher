package com.psplauncher.feature.settings.debug

import com.psplauncher.core.common.security.SecretProtection
import com.psplauncher.feature.artwork.MetadataApiKeyProvider
import com.psplauncher.feature.artwork.api.SgdbApiKeyProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The debug-only credentials file: one `.properties` file that fills every artwork
 * credential at once, so a fresh debug install does not need each key typed in again.
 *
 * Debug source set only — the release build carries a stub loader and none of this code.
 */
class DebugCredentialsTest {

    // ── Parsing ───────────────────────────────────────────────────────────

    @Test
    fun `a full file fills every credential`() {
        val file = DebugCredentialsFile.parse(
            """
            # PlayFieldPortal debug credentials
            steamgriddb.apiKey = sgdb-key
            thegamesdb.apiKey = tgdb-key
            igdb.clientId = igdb-id
            igdb.clientSecret = igdb-secret
            screenscraper.username = ss-user
            screenscraper.password = ss-pass
            """.trimIndent().reader(),
        )

        assertEquals("sgdb-key", file.steamGridDbKey)
        assertEquals("tgdb-key", file.theGamesDbKey)
        assertEquals("igdb-id" to "igdb-secret", file.igdb)
        assertEquals("ss-user" to "ss-pass", file.screenScraper)
        assertTrue(file.problems.isEmpty())
    }

    @Test
    fun `blank and missing values are left out, not saved as empty`() {
        val file = DebugCredentialsFile.parse("steamgriddb.apiKey =\nthegamesdb.apiKey =    \n".reader())

        assertNull(file.steamGridDbKey)
        assertNull(file.theGamesDbKey)
        assertNull(file.igdb)
        assertTrue(file.isEmpty)
        assertTrue(file.problems.isEmpty())
    }

    @Test
    fun `half of a pair is reported and never saved`() {
        val file = DebugCredentialsFile.parse("igdb.clientId = igdb-id\n".reader())

        assertNull(file.igdb)
        assertTrue(file.problems.any { "igdb.clientSecret" in it })
    }

    @Test
    fun `a misspelled key is reported instead of silently ignored`() {
        val file = DebugCredentialsFile.parse("steamgridb.apiKey = typo".reader())

        assertTrue(file.isEmpty)
        assertTrue(file.problems.any { "steamgridb.apiKey" in it })
    }

    @Test
    fun `values are trimmed`() {
        val file = DebugCredentialsFile.parse("steamgriddb.apiKey =   spaced-key   ".reader())
        assertEquals("spaced-key", file.steamGridDbKey)
    }

    // ── Applying ──────────────────────────────────────────────────────────

    private val sgdb = mockk<SgdbApiKeyProvider>(relaxed = true)
    private val metadata = mockk<MetadataApiKeyProvider>(relaxed = true)
    private val loader = DebugCredentialsLoader(sgdb, metadata)

    private fun protectedSaves() {
        coEvery { sgdb.saveKey(any()) } returns SecretProtection.PROTECTED
        coEvery { metadata.saveTgdbKey(any()) } returns SecretProtection.PROTECTED
        coEvery { metadata.saveIgdbCredentials(any(), any()) } returns SecretProtection.PROTECTED
        coEvery { metadata.saveSsCredentials(any(), any()) } returns SecretProtection.PROTECTED
    }

    @Test
    fun `applying saves each credential through its own store`() = runTest {
        protectedSaves()
        val report = loader.apply(
            DebugCredentialsFile(
                steamGridDbKey = "sgdb-key",
                theGamesDbKey = "tgdb-key",
                igdb = "igdb-id" to "igdb-secret",
                screenScraper = "ss-user" to "ss-pass",
            ),
        )

        coVerify { sgdb.saveKey("sgdb-key") }
        coVerify { metadata.saveTgdbKey("tgdb-key") }
        coVerify { metadata.saveIgdbCredentials("igdb-id", "igdb-secret") }
        coVerify { metadata.saveSsCredentials("ss-user", "ss-pass") }
        assertEquals(
            listOf("SteamGridDB", "TheGamesDB", "IGDB", "ScreenScraper"),
            report.loaded,
        )
        assertTrue(report.failed.isEmpty())
        assertFalse(report.anyUnprotected)
    }

    @Test
    fun `a section missing from the file leaves that credential untouched`() = runTest {
        protectedSaves()
        loader.apply(DebugCredentialsFile(steamGridDbKey = "sgdb-key"))

        coVerify(exactly = 0) { metadata.saveTgdbKey(any()) }
        coVerify(exactly = 0) { metadata.clearTgdbKey() }
    }

    @Test
    fun `a keystore seal failure is reported`() = runTest {
        protectedSaves()
        coEvery { sgdb.saveKey(any()) } returns SecretProtection.UNPROTECTED

        val report = loader.apply(DebugCredentialsFile(steamGridDbKey = "sgdb-key"))

        assertTrue(report.anyUnprotected)
    }

    @Test
    fun `a store that fails is reported and the rest still save`() = runTest {
        protectedSaves()
        coEvery { metadata.saveTgdbKey(any()) } throws java.io.IOException("disk full")

        val report = loader.apply(
            DebugCredentialsFile(steamGridDbKey = "sgdb-key", theGamesDbKey = "tgdb-key", igdb = "i" to "s"),
        )

        assertEquals(listOf("SteamGridDB", "IGDB"), report.loaded)
        assertEquals(listOf("TheGamesDB"), report.failed)
    }

    // ── One call from text to a status line ───────────────────────────────

    @Test
    fun `loading text says what was filled and what went wrong`() = runTest {
        protectedSaves()
        coEvery { metadata.saveTgdbKey(any()) } throws java.io.IOException("disk full")

        val result = loader.load("steamgriddb.apiKey=k\nthegamesdb.apiKey=t\nigdb.clientId=only-half\n")

        assertTrue(result.status, "Loaded SteamGridDB" in result.status)
        assertTrue(result.status, "TheGamesDB" in result.status)
        assertTrue(result.status, "igdb.clientSecret" in result.status)
        assertFalse(result.anyUnprotected)
    }

    @Test
    fun `an empty file saves nothing`() = runTest {
        val result = loader.load("# nothing here")

        assertTrue(result.status, "No credentials" in result.status)
        coVerify(exactly = 0) { sgdb.saveKey(any()) }
    }

    @Test
    fun `a malformed file is reported rather than thrown`() = runTest {
        val result = loader.load("steamgriddb.apiKey=\\uZZZZ")

        assertTrue(result.status, "isn't a valid" in result.status)
        coVerify(exactly = 0) { sgdb.saveKey(any()) }
    }
}
