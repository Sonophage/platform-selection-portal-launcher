package com.psplauncher.feature.xmb.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmbCanvasScaleTest {
    private val BASELINE_HEIGHT = 468f
    private val BASELINE_WIDTH = 832f
    private val MIN = 0.75f
    private val MAX = 2.5f

    private fun scale(widthDp: Float, heightDp: Float): Float =
        minOf(heightDp / BASELINE_HEIGHT, widthDp / BASELINE_WIDTH).coerceIn(MIN, MAX)

    @Test
    fun `a panel narrower than the baseline shrinks to fit instead of overflowing`() {
        val titan = scale(638f, 640f)
        assertTrue(
            "a 638dp-wide panel must scale below 1 to lay out an 832dp-wide cross; got $titan",
            titan < 1f,
        )

        assertTrue("832dp of content in 638dp: ${BASELINE_WIDTH * titan}", BASELINE_WIDTH * titan <= 638f + 0.5f)
    }

    @Test
    fun `the square panel is bounded by width, not height`() {
        assertEquals(638f / BASELINE_WIDTH, scale(638f, 640f), 0.001f)
    }

    @Test
    fun `the handheld baseline renders at very close to one`() {
        val konker = scale(822f, 462f)
        assertTrue("the reference handheld should sit within 2% of 1.0; got $konker", konker in 0.98f..1.0f)
    }

    @Test
    fun `a 16 by 10 tablet is bounded by width and does exceed the height baseline`() {
        val tablet = scale(1280f, 800f)
        assertEquals(1280f / BASELINE_WIDTH, tablet, 0.001f)
        val laidOutHeight = 800f / tablet
        assertTrue("16:10 lays out $laidOutHeight dp of height against a 468 baseline", laidOutHeight > BASELINE_HEIGHT)
    }

    @Test
    fun `the floor and ceiling both hold`() {
        assertEquals("an absurdly narrow panel stops at the floor", MIN, scale(100f, 100f), 0.001f)
        assertEquals("an absurdly large one stops at the ceiling", MAX, scale(9000f, 9000f), 0.001f)
    }
}
