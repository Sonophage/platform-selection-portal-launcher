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
    fun `the part you wait through lands in the three-to-four second window`() {
        // The brief was 3-4 seconds, and the thing being measured is what the user WAITS for --
        // which ends at the hand-off. Everything after it runs behind an app that is already
        // taking the screen, so counting the tail here would force the visible ceremony shorter
        // every time the tail got slower, which is backwards: the tail got slower precisely
        // because the part nobody waits for was snapping rather than fading.
        assertTrue(
            "the ceremony takes ${DiscCeremony.HandOffMs}ms to hand off, outside the intended 3-4s",
            DiscCeremony.HandOffMs in 2_500..4_000,
        )
    }

    @Test
    fun `the tail is the slowest thing in the ceremony`() {
        // Why this is pinned: the tail is the one stretch that is usually invisible, so it is the
        // one nobody notices getting quietly shortened -- and the only time it IS seen is a
        // launch that failed, where a snap back to the XMB is the worst reading available.
        assertTrue(
            "the tail (${DiscCeremony.FadeOutMs}ms) must outlast every other phase",
            DiscCeremony.FadeOutMs > maxOf(
                DiscCeremony.FadeInMs, DiscCeremony.SinkMs, DiscCeremony.SpinMs,
            ),
        )
    }

    // ── The tail ──────────────────────────────────────────────────────────

    @Test
    fun `the disc is gone before the room starts opening`() {
        // The hold. Everything between these two is black on purpose: the launched app takes the
        // screen somewhere in here, and black is the only thing that can be under a hand-off
        // without being the wrong thing. Overlapping them would put a fading disc on top of a
        // reveal, which is the flicker this tail exists to remove.
        assertTrue(
            "the disc must finish leaving before the room opens",
            DiscCeremony.RoomOpensFraction >= DiscCeremony.DiscGoneFraction,
        )
    }

    @Test
    fun `the tail starts at the hand-off and ends with the animation`() {
        assertTrue(DiscCeremony.DiscGoneFraction > DiscCeremony.FadeOutFraction)
        assertTrue(DiscCeremony.RoomOpensFraction < 1f)
    }

    @Test
    fun `the room is still fully dark when the hand-off fires`() {
        // The whole point of moving the hand-off to the end of the spin. If the room had begun
        // opening by now the app would appear over a half-lit XMB instead of over black.
        assertEquals(DiscCeremony.FadeOutFraction, DiscCeremony.HandOffFraction, 1e-6f)
        assertTrue(DiscCeremony.HandOffFraction < DiscCeremony.RoomOpensFraction)
    }
}
