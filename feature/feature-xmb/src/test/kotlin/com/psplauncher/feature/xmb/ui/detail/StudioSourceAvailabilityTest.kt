package com.psplauncher.feature.xmb.ui.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Artwork Studio's source list and its availability check are a pair, and only one of them
 * was guarded.
 *
 * Every source but LOCAL talks to a service that needs an account or a key, and
 * refreshProviderAvailability decides which of them can be asked. It checked three of the four.
 * The one it missed was SCREENSCRAPER — the DEFAULT source — and the symptom was not a missing
 * badge: SsMediaCatalog returns null as soon as isEnabled() is false, ssResults turns that into
 * an empty list, and the grid then reported "ScreenScraper has nothing of this type for this
 * game" for a request that was never sent. A wrong answer, stated confidently, about a question
 * nobody asked.
 *
 * This test exists so the next source added to the enum cannot repeat it.
 */
class StudioSourceAvailabilityTest {

    /**
     * The sources that reach a remote service. Written out by hand ON PURPOSE: deriving it from
     * the same enum the production code reads would make this test agree with any mistake that
     * enum makes. Adding a source here without teaching refreshProviderAvailability about it is
     * what the assertion below is for.
     */
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
        // It reads a file the user hands it. Nothing to authenticate, so nothing to be missing.
        assertTrue(StudioSource.LOCAL !in keyed)
    }

    @Test
    fun `ScreenScraper is a keyed source and is the one that defaults`() {
        // Both halves matter. Keyed, so it has to be in the availability check; first in the
        // enum, so it is the source a user lands on without choosing -- which is why its silent
        // failure looked like an empty library rather than a missing key.
        assertTrue(StudioSource.SCREENSCRAPER in keyed)
        assertEquals(StudioSource.SCREENSCRAPER, StudioSource.entries.first())
    }
}
