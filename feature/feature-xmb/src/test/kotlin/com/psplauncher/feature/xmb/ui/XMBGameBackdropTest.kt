package com.psplauncher.feature.xmb.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The still-over-video mask, which decides which half of the crossbar's background is a photograph
 * and which half is a clip.
 *
 * Worth its own test because every way of getting it wrong still renders. Reversed, the clip plays
 * under the labels and the still covers the empty side. Made solid throughout, the snap is
 * invisible and looks like a decode failure. Made transparent throughout, the artwork is gone and
 * looks like a missing file. None of it throws.
 */
class XMBGameBackdropTest {

    private val stops = xmbStillOverVideoStops()

    @Test
    fun `the left edge is fully solid, so the crossbar sits on artwork`() {
        assertEquals(0f, stops.first().first, 0f)
        assertEquals(
            "the category bar, the item list and the game's name all live here",
            1f,
            stops.first().second.alpha,
            0f,
        )
    }

    @Test
    fun `the right edge is fully clear, so the clip is actually visible`() {
        assertEquals(1f, stops.last().first, 0f)
        assertEquals("a mask that never clears hides the snap entirely", 0f, stops.last().second.alpha, 0f)
    }

    @Test
    fun `the transition happens around the middle of the screen, not at an edge`() {
        // "Half the background is the image" is the design. A fade that finished in the first
        // tenth would be a vignette; one that finished in the last tenth would be a clip nobody
        // can see. Both are what a careless tweak to the two constants produces.
        assertTrue("solid band ends at $XMB_STILL_SOLID_END", XMB_STILL_SOLID_END in 0.25f..0.55f)
        assertTrue("fade ends at $XMB_STILL_FADE_END", XMB_STILL_FADE_END in 0.55f..0.85f)
        assertTrue("the fade must have width", XMB_STILL_FADE_END > XMB_STILL_SOLID_END)
    }

    @Test
    fun `the mask only ever gets clearer, left to right`() {
        val alphas = stops.map { it.second.alpha }
        assertEquals(
            "an alpha that rises again would put a second band of artwork over the clip",
            alphas.sortedDescending(),
            alphas,
        )
        val positions = stops.map { it.first }
        assertEquals("gradient positions must ascend", positions.sorted(), positions)
    }

    @Test
    fun `the mask is greyscale, so it tints nothing`() {
        // DstIn reads the alpha channel; a coloured mask would still work and would still be a
        // mistake waiting for someone to read the colour as meaningful.
        stops.forEach { (at, color) ->
            assertTrue("stop at $at is not black or transparent", color == Color.Black || color == Color.Transparent)
        }
    }
}
