package com.psplauncher.core.ui.detail

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backdrop tint runs clear-to-solid, top to bottom, and that direction is the whole design.
 *
 * Run the other way it still compiles, still renders, and still looks deliberate: the art would be
 * hidden exactly where the logo sits and exposed exactly under the overview and the information
 * band, where the text stops being readable. Both halves wrong at once, with nothing thrown.
 *
 * Asserted against the stops rather than a screenshot because the stops are what a future edit
 * will reorder.
 */
class DetailBackdropTest {

    private val page = Color(0xFF12121A)
    private val stops = detailBackdropStops(page)

    @Test
    fun `the tint gets stronger all the way down, never lighter`() {
        val alphas = stops.map { it.second.alpha }
        assertEquals(
            "a stop that dips would put a bright band across the middle of the page",
            alphas.sorted(),
            alphas,
        )
        assertEquals(alphas.size, alphas.distinct().size)
    }

    @Test
    fun `the top is nearly clear, so the logo sits on the artwork`() {
        // The page's top band is the only place the art is shown at anything like full strength.
        assertTrue("top stop ${stops.first().second.alpha} hides the artwork", stops.first().second.alpha < 0.2f)
    }

    @Test
    fun `the bottom is nearly solid, so the body text has a surface`() {
        assertTrue(
            "bottom stop ${stops.last().second.alpha} leaves body text over bare artwork",
            stops.last().second.alpha > 0.9f,
        )
    }

    @Test
    fun `the stops span the full height in order`() {
        val positions = stops.map { it.first }
        assertEquals(0f, positions.first(), 0f)
        assertEquals(1f, positions.last(), 0f)
        assertEquals("gradient positions must ascend", positions.sorted(), positions)
    }

    @Test
    fun `every stop keeps the page's own hue, so the art dissolves into the page`() {
        // Fading to black would end the artwork in a dark band that reads as the edge of a card.
        // The page tone follows the game's accent, so the art dissolves into the game's colour.
        stops.forEach { (at, color) ->
            assertEquals("stop at $at changed hue", page.red, color.red, 0f)
            assertEquals("stop at $at changed hue", page.green, color.green, 0f)
            assertEquals("stop at $at changed hue", page.blue, color.blue, 0f)
        }
    }
}
