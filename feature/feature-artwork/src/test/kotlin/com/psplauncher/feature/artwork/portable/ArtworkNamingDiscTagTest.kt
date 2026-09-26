package com.psplauncher.feature.artwork.portable

import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ArtworkNamingDiscTagTest {
    @Test
    fun `the paren form works today, which is why the gap is easy to miss`() {
        assertNotEquals(
            ArtworkNaming.slug("Final Fantasy VII (USA) (Disc 1)"),
            ArtworkNaming.slug("Final Fantasy VII (USA) (Disc 2)"),
        )
        assertEquals("final-fantasy-vii-disc2", ArtworkNaming.slug("Final Fantasy VII (USA) (Disc 2)"))
    }

    @Test
    fun `a trailing disc tag is safe, but by accident rather than by design`() {
        assertEquals("final-fantasy-vii-disc-1", ArtworkNaming.slug("Final Fantasy VII - Disc 1"))
        assertEquals("final-fantasy-vii-disc-2", ArtworkNaming.slug("Final Fantasy VII - Disc 2"))
    }

    @Test
    fun `an of-N disc tag keeps its own number`() {
        assertEquals("parasite-eve-ii-disc2", ArtworkNaming.slug("Parasite Eve II (Disc 2 of 3)"))
    }

    @Test
    @Ignore("ArtworkNaming.NORMALIZATION_VERSION is frozen at 1; fixing this needs a re-slug pass")
    fun `a bracketed disc tag must not collide with disc one`() {
        assertNotEquals(
            ArtworkNaming.slug("Panzer Dragoon Saga [Disc 1]"),
            ArtworkNaming.slug("Panzer Dragoon Saga [Disc 2]"),
        )
    }
}
