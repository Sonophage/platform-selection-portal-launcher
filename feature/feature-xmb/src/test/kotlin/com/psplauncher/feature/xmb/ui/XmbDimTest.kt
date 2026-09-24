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
    fun `the ramp is the running prototype's, not the static mock's`() {
        // 1c's logic says [1, .85, .55, .30]; 1a's mock draws .9 then .6 with no third stop. The
        // bundle contradicts itself and the running version was chosen, so the numbers are worth
        // stating once somewhere that fails if they quietly become the other set.
        assertEquals(1.00f, XmbDim.ranked(0), 1e-6f)
        assertEquals(0.85f, XmbDim.ranked(1), 1e-6f)
        assertEquals(0.55f, XmbDim.ranked(2), 1e-6f)
        assertEquals(0.30f, XmbDim.ranked(3), 1e-6f)
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
