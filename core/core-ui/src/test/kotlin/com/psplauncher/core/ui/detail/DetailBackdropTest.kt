package com.psplauncher.core.ui.detail

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        stops.forEach { (at, color) ->
            assertEquals("stop at $at changed hue", page.red, color.red, 0f)
            assertEquals("stop at $at changed hue", page.green, color.green, 0f)
            assertEquals("stop at $at changed hue", page.blue, color.blue, 0f)
        }
    }
}
