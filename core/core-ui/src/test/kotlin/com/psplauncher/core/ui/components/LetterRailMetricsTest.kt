package com.psplauncher.core.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterRailMetricsTest {
    private val HANDHELD = 462.dp - StatusStripHeight - HintBarHeight
    private val TABLET = 668.dp - StatusStripHeight - HintBarHeight
    private val WHOLE_ALPHABET = 27

    private fun span(m: RailMetrics) = m.badge * m.rungs + (m.pitch - m.badge) * (m.rungs - 1)

    @Test
    fun `the rungs always fit the space they were measured against`() {
        for (height in 120..900 step 7) {
            for (anchors in 3..WHOLE_ALPHABET) {
                val m = railMetrics(height.dp, anchors)
                assertTrue(
                    "$anchors letters in ${height}dp came out ${span(m).value}dp tall across " +
                        "${m.rungs} rungs, which overflows the space it was measured against",
                    span(m) + RAIL_PADDING * 2 <= height.dp,
                )
            }
        }
    }

    @Test
    fun `a badge is never smaller than legible nor larger than the menu's own`() {
        for (height in 120..900 step 7) {
            val m = railMetrics(height.dp, WHOLE_ALPHABET)
            assertTrue("${m.badge.value}dp is below the legible floor", m.badge >= RailMinBadge)
            assertTrue("${m.badge.value}dp is above the menu's badge", m.badge <= RailMaxBadge)
        }
    }

    @Test
    fun `a short alphabet on a tall panel wears the menu's badge at full size`() {
        val m = railMetrics(TABLET, 8)
        assertEquals("8 letters in ${TABLET.value}dp has room to spare", RailMaxBadge.value, m.badge.value, 0.01f)
        assertEquals("nothing is bucketed when everything fits", 8, m.rungs)
    }

    @Test
    fun `the whole alphabet buckets on the handheld and does not on the tablet`() {
        val small = railMetrics(HANDHELD, WHOLE_ALPHABET)
        val large = railMetrics(TABLET, WHOLE_ALPHABET)

        assertTrue(
            "${HANDHELD.value}dp cannot hold $WHOLE_ALPHABET legible badges, so rungs must be dropped",
            small.rungs < WHOLE_ALPHABET,
        )
        assertEquals(
            "${TABLET.value}dp holds the whole alphabet, so nothing may be hidden",
            WHOLE_ALPHABET,
            large.rungs,
        )
        assertTrue("the taller panel must not produce a smaller badge", large.badge >= small.badge)
    }

    @Test
    fun `bucketing keeps the first letter, the count, and the running order`() {
        val kept = bucketIndices(anchorCount = WHOLE_ALPHABET, rungs = 20)

        assertEquals("one entry per rung", 20, kept.size)
        assertEquals("the first letter is never the one dropped", 0, kept.first())
        assertTrue("a bucket may not point past the letters it came from", kept.last() < WHOLE_ALPHABET)
        assertEquals("buckets run forwards and never repeat a letter", kept.sorted().distinct(), kept)
    }

    @Test
    fun `nothing is bucketed when every letter has a rung`() {
        assertEquals(List(12) { it }, bucketIndices(anchorCount = 12, rungs = 20))
    }

    @Test
    fun `a finger at each end of the strip lands on each end rung`() {
        val rungs = 20
        val pitch = 19f
        val span = pitch * rungs - 3f
        val extent = 400f

        assertEquals("the near end of the strip is the first rung", 0, rungAt(0f, extent, span, pitch, rungs))
        assertEquals(
            "the far end of the strip is the last rung",
            rungs - 1,
            rungAt(extent, extent, span, pitch, rungs),
        )
        assertEquals("a finger past the end clamps rather than throwing", 0, rungAt(-500f, extent, span, pitch, rungs))
    }

    @Test
    fun `the strip is read from where it is drawn, not from the edge of its touch area`() {
        val rungs = 4
        val pitch = 20f
        val span = pitch * rungs
        val extent = 200f

        assertEquals(
            "the rungs sit centred in a ${extent}px box, so the middle of the strip is a middle rung",
            2,
            rungAt(extent / 2f, extent, span, pitch, rungs),
        )
    }
}
