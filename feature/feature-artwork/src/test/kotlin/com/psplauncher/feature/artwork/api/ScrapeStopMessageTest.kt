package com.psplauncher.feature.artwork.api

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a scrape says when it stops early.
 *
 * ScreenScraper's failures have been typed for a long time and reached only Timber, so a run that
 * stopped because a daily quota ran out looked exactly like a run over a library of games nobody
 * has heard of: a count of failures and nothing else. Those two want opposite responses -- come
 * back tomorrow, versus this will never work until something changes -- and the user could not
 * tell them apart.
 *
 * The rule under test is which reasons are worth saying. A reason that stops the whole RUN must
 * produce a message; one that fails a single lookup must not, because it is already counted and
 * repeating it per game is noise.
 */
class ScrapeStopMessageTest {

    @Test
    fun `every reason that stops the run explains itself and says what to do`() {
        val runStoppers = listOf(
            SsFailureReason.DAILY_QUOTA_EXCEEDED,
            SsFailureReason.BAD_DEV_CREDENTIALS,
            SsFailureReason.API_CLOSED,
            SsFailureReason.DISABLED,
        )
        // Guard on the guard: an empty list would make the loop below assert nothing.
        assertTrue("the run-stopper list must not be empty", runStoppers.isNotEmpty())
        for (reason in runStoppers) {
            val message = scrapeStopMessage(reason)
            assertNotNull("$reason must have a message", message)
            assertTrue("$reason's message must not be empty", message!!.isNotBlank())
            // Naming the service matters: the user is looking at a screen that lists four of them
            // and needs to know which one stopped.
            assertTrue("$reason must name ScreenScraper: $message", message.contains("ScreenScraper"))
        }
    }

    @Test
    fun `the run-stopper list matches the one that actually stops the run`() {
        // The pair that must agree. SsLookupResult.isBatchStopper decides whether a run stops;
        // scrapeStopMessage decides whether the user is told why. A reason in one and not the
        // other is either a silent stop or a message for something that never happened.
        for (reason in SsFailureReason.entries) {
            val stopsRun = SsLookupResult(
                info = null,
                diagnostics = SsLookupDiagnostics(
                    fileName = null,
                    platformId = "psx",
                    systemId = null,
                    userCredentialsPresent = false,
                    sentCrc = false,
                    failureReason = reason,
                ),
            ).isBatchStopper
            val hasMessage = scrapeStopMessage(reason) != null
            assertTrue(
                "$reason: stops the run = $stopsRun but has a message = $hasMessage",
                stopsRun == hasMessage,
            )
        }
    }

    @Test
    fun `a per-game failure stays quiet`() {
        // The control. These are counted as failures and the run continues, so an explanation
        // would be attached to a run that did not stop.
        assertNull(scrapeStopMessage(SsFailureReason.NO_MATCH))
        assertNull(scrapeStopMessage(SsFailureReason.NETWORK_ERROR))
        assertNull(scrapeStopMessage(SsFailureReason.RATE_LIMITED))
        assertNull(scrapeStopMessage(SsFailureReason.TOO_MANY_UNRECOGNIZED))
        assertNull(scrapeStopMessage(null))
    }
}
