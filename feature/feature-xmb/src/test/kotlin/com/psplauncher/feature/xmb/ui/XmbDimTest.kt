package com.psplauncher.feature.xmb.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the distance ramp the crossbar and the item column share.
 *
 * The interesting failures are not "is stop two 0.55". They are the two ways a ramp like this goes
 * wrong in place: it stops being monotonic, so a slot further from the cursor is BRIGHTER than one
 * nearer it and the whole cue inverts while every individual number still looks plausible; and it
 * is asked for a distance past its end, which is the case it was never written for — the item
 * column below the bar can be seven rows deep on this panel and the ramp only names four.
 */
class XmbDimTest {

    @Test
    fun `the ramp is the running prototype's, scaled down a fifth`() {
        // 1c's logic says [1, .85, .55, .30]; 1a's mock draws .9 then .6 with no third stop. The
        // bundle contradicts itself, the running version was chosen, and then every unselected
        // stop was taken 20% further down on the panel. Worth stating once somewhere that fails if
        // they quietly drift back to either of the published sets.
        assertEquals(1.00f, XmbDim.ranked(0), 1e-6f)
        assertEquals(0.68f, XmbDim.ranked(1), 1e-6f)
        assertEquals(0.44f, XmbDim.ranked(2), 1e-6f)
        assertEquals(0.24f, XmbDim.ranked(3), 1e-6f)
    }

    @Test
    fun `the cursor's stop was not scaled with the rest`() {
        // The 20% went on the DIMMING. Scaling the selected stop too would fade the one slot the
        // whole cue exists to point at, and every other assertion here would still pass: the ramp
        // would stay monotonic, stay clamped, and stay in proportion.
        assertEquals("the selection must not be dimmed at all", 1f, XmbDim.ranked(0), 1e-6f)
    }

    @Test
    fun `the stops kept their proportions`() {
        // Multiplied, not subtracted. A flat 0.20 off would have left .10 at the far end — all but
        // invisible — while changing the near stop by proportionally much less, which is a
        // different ramp rather than the same one turned down.
        val nearToMid = XmbDim.ranked(1) / XmbDim.ranked(2)
        val midToFar = XmbDim.ranked(2) / XmbDim.ranked(3)
        assertEquals("the published ramp's near:mid ratio", 0.85f / 0.55f, nearToMid, 1e-4f)
        assertEquals("the published ramp's mid:far ratio", 0.55f / 0.30f, midToFar, 1e-4f)
    }

    @Test
    fun `further is never brighter`() {
        // The cue is "further away is fainter" and nothing else enforces it: the ramp is a literal
        // array, and one transposed pair would read as a cursor sitting somewhere it is not.
        var previous = Float.MAX_VALUE
        for (d in 0..12) {
            val alpha = XmbDim.ranked(d)
            assertTrue("distance $d is brighter than ${d - 1}: $alpha after $previous", alpha <= previous)
            previous = alpha
        }
    }

    @Test
    fun `distances past the end of the ramp stay visible`() {
        // The case the ramp was NOT written for. A column shows more rows than the ramp names, so
        // it is asked for 4, 7, 40 on a real screen. Anything that kept fading would reach zero and
        // delete rows that are still on the panel; anything that indexed straight in would throw.
        for (d in XmbDim.LastStep..64) {
            assertEquals(
                "distance $d must rest on the last stop rather than fading on",
                XmbDim.ranked(XmbDim.LastStep),
                XmbDim.ranked(d),
                1e-6f,
            )
        }
        assertTrue("the last stop must still be visible", XmbDim.ranked(XmbDim.LastStep) > 0f)
    }

    @Test
    fun `a negative distance is treated as the cursor`() {
        // Nothing should pass one -- both call sites compute a non-negative step -- but an array
        // index does not forgive, and a crash here would take the whole crossbar down.
        assertEquals(XmbDim.ranked(0), XmbDim.ranked(-5), 1e-6f)
    }
}
