package com.psplauncher.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [DiscCeremony]'s timeline arithmetic.
 *
 * Its KDoc claims "the fractions are derived, so moving a phase boundary cannot leave the hand-off
 * pointing at the wrong moment". That was a claim, not a fact: moving the hand-off from the top of
 * the spin to the end of it left FadeOutFraction computed as `HandOffMs + SpinMs`, which counted
 * the spin twice and put the fade past the end of the animation. Nothing caught it but reading the
 * line. These make the claim true.
 *
 * Pure arithmetic on constants — no Compose runtime, no device.
 */
class DiscCeremonyTimelineTest {

    @Test
    fun `the phases are in order and none is empty`() {
        val marks = listOf(
            0f,
            DiscCeremony.FadeInFraction,
            DiscCeremony.SinkEndFraction,
            DiscCeremony.FadeOutFraction,
            1f,
        )
        marks.zipWithNext().forEach { (a, b) ->
            assertTrue("phase boundaries must increase: $a then $b", b > a)
        }
    }

    @Test
    fun `each fraction is its own millisecond boundary`() {
        // The bug in one assertion: a fraction that stops agreeing with the milliseconds it is
        // named after is a phase drawn at the wrong time, and every one of these is derived from
        // a different sum, so a mistake in any single sum shows up here alone.
        val total = DiscCeremony.TotalMs.toFloat()
        assertEquals(DiscCeremony.FadeInMs / total, DiscCeremony.FadeInFraction, 1e-6f)
        assertEquals(
            (DiscCeremony.FadeInMs + DiscCeremony.SinkMs) / total,
            DiscCeremony.SinkEndFraction,
            1e-6f,
        )
        assertEquals(
            (DiscCeremony.FadeInMs + DiscCeremony.SinkMs + DiscCeremony.SpinMs) / total,
            DiscCeremony.FadeOutFraction,
            1e-6f,
        )
    }

    @Test
    fun `total is exactly the four phases`() {
        assertEquals(
            DiscCeremony.FadeInMs + DiscCeremony.SinkMs + DiscCeremony.SpinMs + DiscCeremony.FadeOutMs,
            DiscCeremony.TotalMs,
        )
    }

    @Test
    fun `the hand-off is the instant the fade begins`() {
        // The whole point of the late hand-off: an activity's window takes the screen as soon as it
        // is ready, so anything started before the fade replaces the disc mid-animation and the
        // spin is never seen. The fade is the only overlap available, so these must be one moment.
        assertEquals(DiscCeremony.FadeOutFraction, DiscCeremony.HandOffFraction, 0f)
        assertEquals(DiscCeremony.TotalMs - DiscCeremony.FadeOutMs, DiscCeremony.HandOffMs)
    }

    @Test
    fun `the whole ceremony lands in the three-to-four second window`() {
        assertTrue(
            "the ceremony is ${DiscCeremony.TotalMs}ms, which is outside the intended 3-4s",
            DiscCeremony.TotalMs in 3_000..4_000,
        )
    }
}
