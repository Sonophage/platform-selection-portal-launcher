package com.psplauncher.feature.xmb.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stretched ramp.
 *
 * What has to hold is that it is the SAME ramp: a second set of numbers for the rail would be two
 * dim rules on one screen, which is exactly what XmbDim's own KDoc exists to prevent. So the ends
 * are pinned to [XmbDim.ranked]'s and only the middle is new.
 */
class XmbDimSmoothTest {

    @Test
    fun `the cursor is never dimmed`() {
        assertEquals(1f, XmbDim.smoothed(distance = 0, span = 8), 0f)
    }

    @Test
    fun `both ends land exactly where the stepped ramp puts them`() {
        // The first faded stop and the last, whatever the span. If either drifts, the rail is
        // using numbers the rest of the screen does not.
        listOf(2, 3, 8, 20).forEach { span ->
            assertEquals("span $span, first step", XmbDim.ranked(1), XmbDim.smoothed(1, span), 0.0001f)
            assertEquals("span $span, last step", XmbDim.ranked(XmbDim.LastStep), XmbDim.smoothed(span, span), 0.0001f)
        }
    }

    @Test
    fun `a long list fades all the way down instead of cliffing`() {
        // The complaint this exists for: with ranked(), rows 3..8 are one identical alpha.
        val span = 8
        val alphas = (1..span).map { XmbDim.smoothed(it, span) }
        alphas.zipWithNext().forEach { (near, far) ->
            assertTrue("each step must be dimmer than the one before it: $alphas", far < near)
        }
    }

    @Test
    fun `a three-row rail matches the stepped ramp it replaces`() {
        // Short lists must not change appearance at all — the smoothing is for long ones.
        assertEquals(XmbDim.ranked(1), XmbDim.smoothed(1, 3), 0.0001f)
        assertEquals(XmbDim.ranked(3), XmbDim.smoothed(3, 3), 0.0001f)
    }

    @Test
    fun `distances past the span clamp rather than fading to nothing`() {
        assertEquals(XmbDim.smoothed(8, 8), XmbDim.smoothed(40, 8), 0f)
        assertTrue(XmbDim.smoothed(40, 8) > 0f)
    }

    @Test
    fun `a single-row list does not divide by zero`() {
        assertTrue(XmbDim.smoothed(1, span = 1) > 0f)
        assertTrue(XmbDim.smoothed(1, span = 0) > 0f)
    }
}
