package com.psplauncher.feature.xmb.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The canvas scale, against the panels it actually has to serve.
 *
 * The formula lives in XMBShell as an expression inside a composable, so this is the arithmetic
 * rather than the composable — the same choice as XmbLetterRailFitTest, and for the same reason:
 * what can be wrong here is the number, and a rendered test would report whatever the layout
 * produced rather than whether it fits.
 *
 * It exists because the formula had no test and two of its three numbers were wrong about real
 * hardware. The floor was 1.0, which meant a panel narrower than the 832dp baseline could not
 * shrink and drew off its own edge; and the comment above the baseline claimed tablets were
 * unclamped for a reason the `minOf` had already made false.
 */
class XmbCanvasScaleTest {

    private val BASELINE_HEIGHT = 468f
    private val BASELINE_WIDTH = 832f
    private val MIN = 0.75f
    private val MAX = 2.5f

    /** The formula as XMBShell computes it. */
    private fun scale(widthDp: Float, heightDp: Float): Float =
        minOf(heightDp / BASELINE_HEIGHT, widthDp / BASELINE_WIDTH).coerceIn(MIN, MAX)

    @Test
    fun `a panel narrower than the baseline shrinks to fit instead of overflowing`() {
        // Unihertz Titan Elite: 638 x 640dp, measured off the device (1436x1440 at density 360).
        val titan = scale(638f, 640f)
        assertTrue(
            "a 638dp-wide panel must scale below 1 to lay out an 832dp-wide cross; got $titan",
            titan < 1f,
        )
        // And the cross must actually fit afterwards, which is the point of scaling at all.
        assertTrue("832dp of content in 638dp: ${BASELINE_WIDTH * titan}", BASELINE_WIDTH * titan <= 638f + 0.5f)
    }

    @Test
    fun `the square panel is bounded by width, not height`() {
        // 640/468 = 1.37 would magnify; 638/832 = 0.77 is the binding constraint. Taking minOf
        // is what stops a near-square screen ballooning off the height ratio.
        assertEquals(638f / BASELINE_WIDTH, scale(638f, 640f), 0.001f)
    }

    @Test
    fun `the handheld baseline renders at very close to one`() {
        // Konker Elite: 822 x 462dp. It is a hair under the baseline on both axes, so it lands
        // just below 1.0 rather than being rounded up to it. If this ever drifts far from 1 the
        // device this app is built on has started being magnified or shrunk by accident.
        val konker = scale(822f, 462f)
        assertTrue("the reference handheld should sit within 2% of 1.0; got $konker", konker in 0.98f..1.0f)
    }

    @Test
    fun `a 16 by 10 tablet is bounded by width and does exceed the height baseline`() {
        // The comment above XMB_BASELINE_HEIGHT_DP used to claim the clamp was what kept tablets
        // from exceeding the baseline. It is not: minOf picks the width ratio here, and the
        // resulting layout height is well over 468dp. Pinned so the claim cannot come back.
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
