package com.psplauncher.feature.artwork.api

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrapeStopMessageTest {
    @Test
    fun `every reason that stops the run explains itself and says what to do`() {
        val runStoppers = listOf(
            SsFailureReason.DAILY_QUOTA_EXCEEDED,
            SsFailureReason.BAD_DEV_CREDENTIALS,
            SsFailureReason.API_CLOSED,
            SsFailureReason.DISABLED,
        )

        assertTrue("the run-stopper list must not be empty", runStoppers.isNotEmpty())
        for (reason in runStoppers) {
            val message = scrapeStopMessage(reason)
            assertNotNull("$reason must have a message", message)
            assertTrue("$reason's message must not be empty", message!!.isNotBlank())

            assertTrue("$reason must name ScreenScraper: $message", message.contains("ScreenScraper"))
        }
    }

    @Test
    fun `the run-stopper list matches the one that actually stops the run`() {
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
        assertNull(scrapeStopMessage(SsFailureReason.NO_MATCH))
        assertNull(scrapeStopMessage(SsFailureReason.NETWORK_ERROR))
        assertNull(scrapeStopMessage(SsFailureReason.RATE_LIMITED))
        assertNull(scrapeStopMessage(SsFailureReason.TOO_MANY_UNRECOGNIZED))
        assertNull(scrapeStopMessage(null))
    }
}
