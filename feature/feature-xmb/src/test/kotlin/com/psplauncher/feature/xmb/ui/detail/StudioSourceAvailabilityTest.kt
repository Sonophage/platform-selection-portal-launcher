package com.psplauncher.feature.xmb.ui.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudioSourceAvailabilityTest {
    private val keyed = setOf(
        StudioSource.SCREENSCRAPER,
        StudioSource.STEAMGRIDDB,
        StudioSource.IGDB,
    )

    @Test
    fun `every source except the local picker needs credentials`() {
        assertEquals(
            StudioSource.entries.toSet() - StudioSource.LOCAL,
            keyed,
            "a source was added to StudioSource; decide whether it is keyed and teach " +
                "refreshProviderAvailability about it, then list it here",
        )
    }

    @Test
    fun `the local picker is the only source that can always be asked`() {
        assertTrue(StudioSource.LOCAL !in keyed)
    }

    @Test
    fun `ScreenScraper is a keyed source and is the one that defaults`() {
        assertTrue(StudioSource.SCREENSCRAPER in keyed)
        assertEquals(StudioSource.SCREENSCRAPER, StudioSource.entries.first())
    }
}
