package com.psplauncher.feature.artwork.api

import io.mockk.mockk
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenScraperApiTest {

    private val api = ScreenScraperApi(
        appContext = mockk(relaxed = true),
        httpClient = mockk(relaxed = true),
        credentials = mockk(relaxed = true),
    )

    // ScreenScraper serves these as HTTP 200 with a plain-text body — classification is what
    // keeps a batch run from hammering the API after a quota/credential failure.
    @Test
    fun `plain-text error bodies classify to typed reasons`() {
        assertEquals(SsFailureReason.API_CLOSED,
            api.failureForTextBody("API closed for non-registered members"))
        assertEquals(SsFailureReason.DAILY_QUOTA_EXCEEDED,
            api.failureForTextBody("Votre quota de scrape est atteint"))
        assertEquals(SsFailureReason.BAD_DEV_CREDENTIALS,
            api.failureForTextBody("Erreur de login : Verifiez vos identifiants developpeur !"))
        assertEquals(SsFailureReason.PARSE_ERROR,
            api.failureForTextBody("<html>some cdn error page</html>"))
    }

    @Test
    fun `name search keeps ranked hits and drops the empty padding entries`() {
        val body = """
            {"response":{"jeux":[
              {"id":"3506","noms":[{"region":"jp","text":"Final Fantasy VI Advance JP"},{"region":"us","text":"Final Fantasy VI Advance"}],
               "dates":[{"region":"us","text":"2007-02-05"}]},
              {"id":"421","noms":[{"region":"wor","text":"Final Fantasy VI"}]},
              {}
            ]}}
        """.trimIndent()

        val hits = api.parseSearch(body)

        assertEquals(
            listOf(
                SsSearchHit(ssId = 3506L, title = "Final Fantasy VI Advance", releaseYear = 2007),
                SsSearchHit(ssId = 421L, title = "Final Fantasy VI", releaseYear = null),
            ),
            hits,
        )
    }

    // ── Real jeuRecherche responses, captured on device 2026-09-10 (account ids redacted) ─────

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/screenscraper/$name")) { "missing fixture $name" }.readText()

    @Test
    fun `a real empty answer, padding entry and all, is no hits rather than a failure`() {
        assertEquals(emptyList<SsSearchHit>(), api.parseSearch(fixture("jeuRecherche-windows-no-hits.json")))
    }

    @Test
    fun `a real single-hit answer gives the game and its system`() {
        val hits = api.parseSearch(fixture("jeuRecherche-every-platform-tactics-ogre-reborn.json"))

        assertEquals(
            listOf(SsSearchHit(ssId = 478505L, title = "Tactics Ogre: Reborn", releaseYear = null, systemId = 284, systemName = "Playstation 5")),
            hits,
        )
    }

    @Test
    fun `a real every-platform answer keeps each hit's system, in ScreenScraper's order`() {
        val hits = checkNotNull(api.parseSearch(fixture("jeuRecherche-every-platform-tactics-ogre.json")))

        assertEquals(listOf(2293L, 27874L, 425726L, 478505L), hits.map { it.ssId })
        assertEquals(listOf("Super Nintendo", "PSP", "Switch", "Playstation 5"), hits.map { it.systemName })
        assertEquals(284, hits.last().systemId)
        // The PS5 release lists only publisher pictograms: no art of its own.
        assertEquals(listOf(2, 2, 2, 0), hits.map { it.gameArtCount })
    }

    @Test
    fun `a capture keeps the games and drops the account block and the echoed request`() {
        val body = """
            {"header":{"APIversion":"2.0","commandRequested":"https://x/jeuRecherche.php?ssid=someone"},
             "response":{"ssuser":{"id":"someone","numid":"42"},"jeux":[{"id":"478505"}]}}
        """.trimIndent()

        val scrubbed = ScreenScraperApi.scrubCapture(body)

        assertFalse(scrubbed.contains("someone"))
        assertFalse(scrubbed.contains("commandRequested"))
        assertFalse(scrubbed.contains("ssuser"))
        assertTrue(scrubbed.contains("\"APIversion\""))
        assertTrue(scrubbed.contains("\"478505\""))
    }

    // ── Request spacing, start to start (task M.4) ───────────────────────────

    @Test
    fun `a request that took longer than the interval sends the next one at once`() {
        // The previous request started at 0 and was a 10 s every-platform search.
        assertEquals(0L, ScreenScraperApi.waitBeforeNextRequest(nowMs = 10_000, lastStartMs = 0, intervalMs = 1_100))
    }

    @Test
    fun `back-to-back fast requests are spaced by the interval from their starts`() {
        // A 300 ms request that started at 5 000: the next may start at 6 100, not 300 ms + 1.1 s later.
        assertEquals(800L, ScreenScraperApi.waitBeforeNextRequest(nowMs = 5_300, lastStartMs = 5_000, intervalMs = 1_100))
        // The very first request has nothing to wait for.
        assertEquals(0L, ScreenScraperApi.waitBeforeNextRequest(nowMs = 5_300, lastStartMs = 0, intervalMs = 1_100))
    }

    @Test
    fun `the interval never drops below 1_1 s`() {
        // The account measured on device allows 3072 a minute: the floor holds.
        assertEquals(1_100L, ScreenScraperApi.requestIntervalMs(3_072))
        assertEquals(1_100L, ScreenScraperApi.requestIntervalMs(null))
        assertEquals(1_100L, ScreenScraperApi.requestIntervalMs(0))
        // A strict account is spread evenly across its minute, rounded up.
        assertEquals(2_000L, ScreenScraperApi.requestIntervalMs(30))
        assertEquals(1_429L, ScreenScraperApi.requestIntervalMs(42))
    }

    @Test
    fun `the per-minute limit is read from the account block`() {
        val user = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(SsUser.serializer(), """{"id":"someone","maxthreads":"1","maxrequestspermin":"3072"}""")

        assertEquals("3072", user.maxRequestsPerMinute)
    }

    @Test
    fun `account limits keep the numeric fields and never the account name or number`() {
        val body = """
            {"response":{"ssuser":{"id":"someone","numid":"42","maxthreads":"1",
             "maxrequestspermin":"20","favregion":"us"},"jeux":[]}}
        """.trimIndent()

        assertEquals(
            mapOf("maxthreads" to "1", "maxrequestspermin" to "20"),
            ScreenScraperApi.accountLimits(body),
        )
    }

    @Test
    fun `a body with no account block has no limits to log`() {
        assertNull(ScreenScraperApi.accountLimits("API closed for non-registered members"))
        assertNull(ScreenScraperApi.accountLimits("""{"response":{"jeux":[]}}"""))
    }

    @Test
    fun `a plain-text body is captured as it is`() {
        assertEquals(
            "API closed for non-registered members",
            ScreenScraperApi.scrubCapture("API closed for non-registered members"),
        )
    }

    @Test
    fun `a plain-text search error is not a search response`() {
        assertNull(api.parseSearch("API closed for non-registered members"))
    }

    /** The HTTP 400 on a Windows game: jeuInfos was sent a bare systemeid with nothing to match. */
    @Test
    fun `a lookup needs a game id or a ROM checksum or file name`() {
        val nothing = com.psplauncher.feature.artwork.rom.RomIdentity(crc32 = null, sizeBytes = null, fileName = null)
        val sizeOnly = com.psplauncher.feature.artwork.rom.RomIdentity(crc32 = null, sizeBytes = 4096L, fileName = null)

        assertEquals(false, ScreenScraperApi.canLookUp(rom = null, ssGameId = null))
        assertEquals(false, ScreenScraperApi.canLookUp(rom = nothing, ssGameId = null))
        assertEquals(false, ScreenScraperApi.canLookUp(rom = sizeOnly, ssGameId = null))
        assertEquals(true, ScreenScraperApi.canLookUp(rom = null, ssGameId = 42L))
        assertEquals(true, ScreenScraperApi.canLookUp(rom = nothing.copy(crc32 = "ABCD1234"), ssGameId = null))
        assertEquals(true, ScreenScraperApi.canLookUp(rom = nothing.copy(fileName = "game.nds"), ssGameId = null))
    }

    @Test
    fun `batch stopper flags cover quota and credential failures only`() {
        fun resultWith(reason: SsFailureReason) = SsLookupResult(
            info = null,
            diagnostics = SsLookupDiagnostics(
                fileName = "x.gba", platformId = "gba", systemId = 12,
                userCredentialsPresent = false, sentCrc = false, failureReason = reason,
            ),
        )
        assertEquals(true,  resultWith(SsFailureReason.DAILY_QUOTA_EXCEEDED).isBatchStopper)
        assertEquals(true,  resultWith(SsFailureReason.BAD_DEV_CREDENTIALS).isBatchStopper)
        assertEquals(true,  resultWith(SsFailureReason.API_CLOSED).isBatchStopper)
        assertEquals(false, resultWith(SsFailureReason.NO_MATCH).isBatchStopper)
        assertEquals(false, resultWith(SsFailureReason.RATE_LIMITED).isBatchStopper)
        assertEquals(true,  resultWith(SsFailureReason.TOO_MANY_UNRECOGNIZED).stopsUnhashedLookups)
        assertEquals(false, resultWith(SsFailureReason.TOO_MANY_UNRECOGNIZED).isBatchStopper)
    }

    // ── Threads (the account's `maxthreads`) ─────────────────────────────────

    @Test
    fun `an account's thread count is read, clamped, and never zero`() {
        // Zero is the one that matters. ScreenScraper sends "0" for an account whose allowance is
        // not set, and a semaphore built with zero permits is not a rate limit, it is a scrape
        // that hangs on its first request with no error anywhere.
        assertEquals(1, ScreenScraperApi.effectiveThreads("0"))
        assertEquals(1, ScreenScraperApi.effectiveThreads("-4"))

        // Absent or unparseable keeps one -- exactly what was hard-coded here before, so an
        // account that reports nothing is no worse off than it was.
        assertEquals(1, ScreenScraperApi.effectiveThreads(null))
        assertEquals(1, ScreenScraperApi.effectiveThreads(""))
        assertEquals(1, ScreenScraperApi.effectiveThreads("lots"))

        assertEquals(1, ScreenScraperApi.effectiveThreads("1"))
        assertEquals(4, ScreenScraperApi.effectiveThreads(" 4 "))

        // The ceiling. This number comes off the wire, and a wrong or hostile one must not open an
        // unbounded number of sockets on a handheld.
        assertEquals(ScreenScraperApi.MAX_THREADS, ScreenScraperApi.effectiveThreads("9999"))
    }

    @Test
    fun `two threads means two requests actually in the air at once`() = runBlocking {
        // The whole point of the change, and the one part of it a pure function cannot show.
        //
        // Before this, spacing and concurrency were the same mutex, held across the request: a
        // second request could not start until the first had COME BACK, so an account allowed four
        // threads still ran one at a time forever. The permit is now held across the request and
        // the spacing lock is not, and this is what tells those two arrangements apart.
        //
        // Real time, not virtual: the gate reads the wall clock, and the 1.1 s floor is what makes
        // the overlap observable at all. Each block outlives that floor on purpose -- with a block
        // shorter than the spacing, nothing would overlap however many permits there were, and the
        // test would pass against the very code it exists to reject.
        val api = ScreenScraperApi(
            appContext = mockk(relaxed = true),
            httpClient = mockk(relaxed = true),
            credentials = mockk(relaxed = true),
        )
        api.rememberRequestLimit(
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(SsUser.serializer(), """{"id":"someone","maxthreads":"2"}"""),
        )

        val inFlight = java.util.concurrent.atomic.AtomicInteger()
        val peak = java.util.concurrent.atomic.AtomicInteger()
        coroutineScope {
            repeat(2) {
                launch {
                    api.rateLimited("test") {
                        peak.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                        delay(2_000)
                        inFlight.decrementAndGet()
                    }
                }
            }
        }
        assertEquals("both requests should have been in the air together", 2, peak.get())
    }

    @Test
    fun `widening the limit mid-request does not hand back a permit that was never taken`() = runBlocking {
        // A permit leak, and the reason rateLimited reads the semaphore into a local first.
        //
        // rememberRequestLimit runs INSIDE a request -- it is fed from that request's own response
        // -- so the semaphore can be replaced while a permit is outstanding on the old one.
        // Releasing to `requestSlots` instead of to the instance the permit came from adds a
        // permit to the new semaphore that nobody ever acquired, and the account's limit is one
        // higher from then on, for the rest of the process, with nothing to show for it.
        //
        // Two permits after the swap, three callers: the third must wait. If the leak is there it
        // does not, and a scrape quietly runs one thread over what the account allows.
        val api = ScreenScraperApi(
            appContext = mockk(relaxed = true),
            httpClient = mockk(relaxed = true),
            credentials = mockk(relaxed = true),
        )
        api.rateLimited("first") {
            api.rememberRequestLimit(
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString(SsUser.serializer(), """{"id":"someone","maxthreads":"2"}"""),
            )
        }

        val inFlight = java.util.concurrent.atomic.AtomicInteger()
        val peak = java.util.concurrent.atomic.AtomicInteger()
        coroutineScope {
            repeat(3) {
                launch {
                    api.rateLimited("test") {
                        peak.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                        delay(2_500)
                        inFlight.decrementAndGet()
                    }
                }
            }
        }
        assertEquals("the swap must not have added a permit", 2, peak.get())
    }

    @Test
    fun `one thread still means one request at a time`() = runBlocking {
        // The other half of the pair: an account that allows one must still be serialised, and a
        // semaphore whose permits were released to a different instance than they were taken from
        // would quietly stop enforcing that.
        val api = ScreenScraperApi(
            appContext = mockk(relaxed = true),
            httpClient = mockk(relaxed = true),
            credentials = mockk(relaxed = true),
        )
        val inFlight = java.util.concurrent.atomic.AtomicInteger()
        val peak = java.util.concurrent.atomic.AtomicInteger()
        coroutineScope {
            repeat(2) {
                launch {
                    api.rateLimited("test") {
                        peak.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                        delay(300)
                        inFlight.decrementAndGet()
                    }
                }
            }
        }
        assertEquals("an unconfigured account must stay single-flight", 1, peak.get())
    }
}
