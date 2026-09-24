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
        // The brief was 3-4 seconds and is now six: three read as a wipe on the handheld rather
        // than as a ceremony, the disc's exit has since moved in front of the hand-off, and the
        // redesign asked for longer again — "i would make it longer to about 6 seconds". The
        // RANGE is what this test is for, not the exact number — it exists so a phase tweak
        // cannot quietly halve the thing or double it.
        //
        // The window moves WITH the intended value rather than being widened to swallow it. Left
        // at 4_200..6_000 it would still have passed, with the value sitting exactly on the
        // ceiling — a guard touching the thing it guards catches nothing in one direction and
        // fires on the next honest nudge for the wrong reason.
        //
        // Re-centred a second time when the case and the disc's exit from it were doubled on the
        // panel. That is a change of INTENT, which is the only thing a guard like this may follow;
        // widening it to 5_400..7_400 so both the old and the new number fit would have left it
        // asserting nothing in particular.
        //
        // What is being measured is what the user WAITS for, which ends at the hand-off.
        // Everything after it runs behind an app that is already taking the screen, so counting
        // the hold here would force the visible ceremony shorter every time the hold got longer,
        // which is backwards.
        assertTrue(
            "the ceremony takes ${DiscCeremony.HandOffMs}ms to hand off, outside the intended window",
            DiscCeremony.HandOffMs in 6_250..7_450,
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
    fun `the slit shuts exactly at the hand-off`() {
        // THE pair in this file, and the one with only one side guarded until now.
        //
        // SlitCloseEndMs is 650 and DiscOutMs is 650, and nothing but this makes those the same
        // number. Retune the disc's exit — the one phase anybody is likely to touch, since it is
        // the one that was split out for feeling wrong — and the slot goes on closing after the
        // launched app already owns the screen, or snaps shut early and leaves a dark gap the
        // ceremony was rewritten to remove.
        assertEquals(
            "the slot must finish closing on the hand-off, not around it",
            DiscCeremony.HandOffMs,
            DiscCeremony.DiscOutStartMs + DiscCeremony.SlitCloseEndMs,
        )
    }

    @Test
    fun `the disc comes out from behind the case, and the case goes before the sink`() {
        // Three orderings that together are the whole "it came out of a case" reading. Any one of
        // them inverted still animates, and still looks like something — just not like that.

        // It must be visible while the case is still there, or it is not emerging from anything.
        assertTrue(
            "the disc appears at ${DiscCeremony.DiscAppearMs}ms, after the case has begun leaving",
            DiscCeremony.DiscOpaqueMs < DiscCeremony.CaseFadeStartMs,
        )
        // The case must not still be on screen when the sink starts, or it sinks with the disc.
        assertTrue(
            "the case is still going at ${DiscCeremony.CaseFadeStartMs}ms and the sink starts at ${DiscCeremony.FadeInMs}ms",
            DiscCeremony.CaseFadeStartMs < DiscCeremony.FadeInMs,
        )
        // And it has to arrive alone first, or the two appear together and neither reads.
        assertTrue(
            "the case must be on screen by itself before the disc appears",
            DiscCeremony.CaseInMs <= DiscCeremony.DiscAppearMs,
        )
    }

    @Test
    fun `the light appears while the disc is still going through, and everything fits the tail`() {
        // The slit opening BEFORE the drop ends is deliberate: the light spills as the disc goes
        // through the slot, not after it has gone. Ordered the other way it reads as two separate
        // events rather than one.
        assertTrue(
            "the slot opens at ${DiscCeremony.SlitOpenStartMs}ms, after the disc has finished falling at ${DiscCeremony.DropMs}ms",
            DiscCeremony.SlitOpenStartMs < DiscCeremony.DropMs,
        )
        assertTrue(
            "the disc must finish falling inside its own phase",
            DiscCeremony.DropMs <= DiscCeremony.DiscOutMs,
        )
        // The slit's fade runs past the hand-off, so the timeline has to be long enough to draw
        // it — otherwise the overlay is torn down mid-fade and the light vanishes as a cut.
        assertTrue(
            "the slot is still fading at ${DiscCeremony.DiscOutStartMs + DiscCeremony.SlitFadeEndMs}ms but the ceremony ends at ${DiscCeremony.TotalMs}ms",
            DiscCeremony.DiscOutStartMs + DiscCeremony.SlitFadeEndMs <= DiscCeremony.TotalMs,
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
