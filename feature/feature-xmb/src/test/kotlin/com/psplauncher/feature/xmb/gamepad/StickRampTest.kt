package com.psplauncher.feature.xmb.gamepad

import com.psplauncher.core.domain.model.StickSensitivity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How quickly a held direction accelerates, and how far the stick has to move to start.
 *
 * This is the "joystick sensitivity is too high" complaint, in two numbers. The stick's full-tilt
 * threshold was a fixed 0.90 — trivially easy to reach on a handheld thumbstick — and reaching it
 * SKIPPED the acceleration ramp entirely, jumping straight to the fastest interval. So an ordinary
 * push repeated at top speed from its very first repeat, at twice the D-pad's first-repeat rate
 * for the same intent. Full tilt now climbs the same ramp at double rate instead.
 */
class StickRampTest {

    // Standard scroll speed's tuning, written out so this test does not read the production table
    // and agree with whatever it says.
    private val base = 110L
    private val fast = 50L
    private val rampSteps = 5

    private fun interval(repeats: Int, magnitude: Float, tilt: Float = StickSensitivity.STANDARD.fullTilt) =
        rampedInterval(rampStepFor(repeats, magnitude, tilt), base, fast, rampSteps)

    @Test
    fun `a full tilt no longer starts at top speed`() {
        // THE regression. The first repeat of a full-tilt hold must not already be the fastest
        // interval — that is what made the stick feel twitchy.
        assertTrue(interval(repeats = 0, magnitude = 1f) > fast, "full tilt still teleports to top speed")
        assertEquals(base, interval(repeats = 0, magnitude = 1f))
    }

    @Test
    fun `a full tilt still accelerates faster than a gentle hold`() {
        // It has to remain an explicit "go faster" gesture the D-pad cannot make, or the setting
        // is just a slowdown.
        repeat(3) { r ->
            val tilted = interval(repeats = r + 1, magnitude = 1f)
            val gentle = interval(repeats = r + 1, magnitude = 0.6f)
            assertTrue(tilted <= gentle, "full tilt must not be slower at repeat ${r + 1}")
        }
        assertTrue(interval(repeats = 2, magnitude = 1f) < interval(repeats = 2, magnitude = 0.6f))
    }

    @Test
    fun `both reach the same top speed, full tilt just gets there sooner`() {
        assertEquals(fast, interval(repeats = rampSteps, magnitude = 0.6f))
        // Half the repeats, because it climbs two rungs each.
        assertEquals(fast, interval(repeats = rampSteps / 2 + 1, magnitude = 1f))
    }

    @Test
    fun `the ramp never speeds past its floor or starts below its ceiling`() {
        (0..20).forEach { r ->
            listOf(0.6f, 1f).forEach { m ->
                val i = interval(r, m)
                assertTrue(i in fast..base, "interval $i out of range at repeat $r, magnitude $m")
            }
        }
    }

    @Test
    fun `lower sensitivity needs a firmer push to count as full tilt`() {
        // A 0.9 deflection is full tilt on High and an ordinary hold on Low. That is the whole
        // point of the setting: same stick, different intent.
        assertTrue(0.9f >= StickSensitivity.HIGH.fullTilt)
        assertTrue(0.9f < StickSensitivity.LOW.fullTilt)
        assertEquals(base, interval(0, 0.9f, StickSensitivity.LOW.fullTilt))
    }

    @Test
    fun `every sensitivity needs a real push before it navigates at all`() {
        // No setting may make the stick hair-triggered — a resting thumb must never scroll.
        StickSensitivity.entries.forEach {
            assertTrue(it.deadZone >= 0.35f, "${it.name} dead zone ${it.deadZone} is too small")
            assertTrue(it.deadZone < it.fullTilt, "${it.name} would be full tilt the moment it registers")
        }
    }

    @Test
    fun `the default is calmer than the old fixed behaviour`() {
        // The old pair was deadZone 0.50 / fullTilt 0.90, and that is what felt wrong.
        assertTrue(StickSensitivity.STANDARD.deadZone > 0.50f)
        assertTrue(StickSensitivity.STANDARD.fullTilt > 0.90f)
        // ...and High is still there for anyone who liked it.
        assertTrue(StickSensitivity.HIGH.fullTilt <= 0.90f)
    }
}
