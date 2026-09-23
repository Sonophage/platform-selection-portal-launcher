package com.psplauncher.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [DiscCeremony]'s timeline arithmetic.
 *
 * Its KDoc claims "the fractions are derived, so moving a phase boundary cannot leave the hand-off
 * pointing at the wrong moment". That was a claim, not a fact: moving the hand-off from the top of
 * the spin to the end of it left the fade fraction computed as `HandOffMs + SpinMs`, which counted
 * the spin twice and put the fade past the end of the animation. Nothing caught it but reading the
 * line. These make the claim true.
 *
 * The timeline has since been split again — the disc's departure is its own phase and the hand-off
 * moved behind it — for the reason the fourth test here records.
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
            DiscCeremony.DiscOutStartFraction,
            DiscCeremony.DiscGoneFraction,
            DiscCeremony.RoomOpensFraction,
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
            DiscCeremony.DiscOutStartFraction,
            1e-6f,
        )
        assertEquals(
            (DiscCeremony.FadeInMs + DiscCeremony.SinkMs + DiscCeremony.SpinMs +
                DiscCeremony.DiscOutMs) / total,
            DiscCeremony.DiscGoneFraction,
            1e-6f,
        )
    }

    @Test
    fun `total is exactly the five phases`() {
        assertEquals(
            DiscCeremony.FadeInMs + DiscCeremony.SinkMs + DiscCeremony.SpinMs +
                DiscCeremony.DiscOutMs + DiscCeremony.HoldMs,
            DiscCeremony.TotalMs,
        )
    }

    @Test
    fun `nothing is launched until the disc has finished leaving`() {
        // The one that matters, and the reason the timeline was split.
        //
        // An activity's window takes the screen the instant it is ready, and with the window
        // transition suppressed that is often a few hundred milliseconds. While the hand-off sat
        // at the START of the disc's exit, the app arrived on top of a disc that was still
        // visibly going — read on the device as the ceremony being "cut off", which it was, at
        // the last thing in it.
        //
        // These two being one instant is what makes that impossible: by the time anything else
        // can take the screen there is nothing of the ceremony left to interrupt.
        assertEquals(DiscCeremony.DiscGoneFraction, DiscCeremony.HandOffFraction, 0f)
        assertEquals(
            DiscCeremony.FadeInMs + DiscCeremony.SinkMs + DiscCeremony.SpinMs + DiscCeremony.DiscOutMs,
            DiscCeremony.HandOffMs,
        )
    }

    @Test
    fun `the part you wait through lands in the intended window`() {
        // The brief was 3-4 seconds and is now around five: three read as a wipe on the handheld
        // rather than as a ceremony, and the disc's exit has since moved in front of the hand-off
        // as well. The RANGE is what this test is for, not the exact number — it exists so a
        // phase tweak cannot quietly halve the thing or double it.
        //
        // What is being measured is what the user WAITS for, which ends at the hand-off.
        // Everything after it runs behind an app that is already taking the screen, so counting
        // the hold here would force the visible ceremony shorter every time the hold got longer,
        // which is backwards.
        assertTrue(
            "the ceremony takes ${DiscCeremony.HandOffMs}ms to hand off, outside the intended window",
            DiscCeremony.HandOffMs in 4_200..6_000,
        )
    }

    @Test
    fun `the disc's exit is long enough to read as one`() {
        // It is the last thing seen, and the only phase whose whole job is getting out of the
        // way. Short enough and it becomes the snap this split exists to remove.
        assertTrue(
            "the disc leaves in ${DiscCeremony.DiscOutMs}ms, which is a cut rather than an exit",
            DiscCeremony.DiscOutMs >= 450,
        )
    }

    @Test
    fun `the hold never shortens back into a snap`() {
        // The hold is the one stretch that is usually invisible, so it is the one nobody notices
        // getting quietly shortened — and the only time it IS seen is a launch that failed, where
        // a snap back to the XMB is the worst reading available. At 900, in the old single-phase
        // shape, the room came back in about a third of a second; that measurement is where this
        // floor comes from.
        assertTrue(
            "the hold (${DiscCeremony.HoldMs}ms) has shortened back toward a snap",
            DiscCeremony.HoldMs >= 700,
        )
    }

    @Test
    fun `the room only starts opening after the hand-off`() {
        // Everything between the hand-off and the open is black on purpose: the launched app
        // takes the screen somewhere in there, and black is the only thing that can be under a
        // hand-off without being the wrong thing. Opening earlier would put a reveal of the XMB
        // underneath an app that is about to cover it.
        assertTrue(
            "the room must not start opening before the hand-off",
            DiscCeremony.RoomOpensFraction > DiscCeremony.HandOffFraction,
        )
        assertTrue(DiscCeremony.RoomOpensFraction < 1f)
    }
}
