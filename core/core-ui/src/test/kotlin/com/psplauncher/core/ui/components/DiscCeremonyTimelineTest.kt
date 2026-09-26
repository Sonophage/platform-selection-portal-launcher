package com.psplauncher.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(DiscCeremony.DiscGoneFraction, DiscCeremony.HandOffFraction, 0f)
        assertEquals(
            DiscCeremony.FadeInMs + DiscCeremony.SinkMs + DiscCeremony.SpinMs + DiscCeremony.DiscOutMs,
            DiscCeremony.HandOffMs,
        )
    }

    @Test
    fun `the part you wait through lands in the intended window`() {
        assertTrue(
            "the ceremony takes ${DiscCeremony.HandOffMs}ms to hand off, outside the intended window",
            DiscCeremony.HandOffMs in 6_250..7_450,
        )
    }

    @Test
    fun `the disc's exit is long enough to read as one`() {
        assertTrue(
            "the disc leaves in ${DiscCeremony.DiscOutMs}ms, which is a cut rather than an exit",
            DiscCeremony.DiscOutMs >= 450,
        )
    }

    @Test
    fun `the hold never shortens back into a snap`() {
        assertTrue(
            "the hold (${DiscCeremony.HoldMs}ms) has shortened back toward a snap",
            DiscCeremony.HoldMs >= 700,
        )
    }

    @Test
    fun `the slit shuts exactly at the hand-off`() {
        assertEquals(
            "the slot must finish closing on the hand-off, not around it",
            DiscCeremony.HandOffMs,
            DiscCeremony.DiscOutStartMs + DiscCeremony.SlitCloseEndMs,
        )
    }

    @Test
    fun `the disc comes out from behind the case, and the case goes before the sink`() {
        assertTrue(
            "the disc appears at ${DiscCeremony.DiscAppearMs}ms, after the case has begun leaving",
            DiscCeremony.DiscOpaqueMs < DiscCeremony.CaseFadeStartMs,
        )

        assertTrue(
            "the case is still going at ${DiscCeremony.CaseFadeStartMs}ms and the sink starts at ${DiscCeremony.FadeInMs}ms",
            DiscCeremony.CaseFadeStartMs < DiscCeremony.FadeInMs,
        )

        assertTrue(
            "the case must be on screen by itself before the disc appears",
            DiscCeremony.CaseInMs <= DiscCeremony.DiscAppearMs,
        )
    }

    @Test
    fun `the light appears while the disc is still going through, and everything fits the tail`() {
        assertTrue(
            "the slot opens at ${DiscCeremony.SlitOpenStartMs}ms, after the disc has finished falling at ${DiscCeremony.DropMs}ms",
            DiscCeremony.SlitOpenStartMs < DiscCeremony.DropMs,
        )
        assertTrue(
            "the disc must finish falling inside its own phase",
            DiscCeremony.DropMs <= DiscCeremony.DiscOutMs,
        )

        assertTrue(
            "the slot is still fading at ${DiscCeremony.DiscOutStartMs + DiscCeremony.SlitFadeEndMs}ms but the ceremony ends at ${DiscCeremony.TotalMs}ms",
            DiscCeremony.DiscOutStartMs + DiscCeremony.SlitFadeEndMs <= DiscCeremony.TotalMs,
        )
    }

    @Test
    fun `the room only starts opening after the hand-off`() {
        assertTrue(
            "the room must not start opening before the hand-off",
            DiscCeremony.RoomOpensFraction > DiscCeremony.HandOffFraction,
        )
        assertTrue(DiscCeremony.RoomOpensFraction < 1f)
    }
}
