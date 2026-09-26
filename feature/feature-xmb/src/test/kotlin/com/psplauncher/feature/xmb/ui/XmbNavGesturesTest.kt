package com.psplauncher.feature.xmb.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XmbNavGesturesTest {
    private val stepPx = 64f
    private val flingPx = 420f
    private val backCommitPx = 72f

    @Test fun `travel below one step yields nothing`() {
        assertEquals(0, consumeWholeSteps(30f, stepPx))
        assertEquals(0, consumeWholeSteps(-63f, stepPx))
    }

    @Test fun `each step distance crossed yields one step, remainder carries`() {
        assertEquals(1, consumeWholeSteps(64f, stepPx))
        assertEquals(1, consumeWholeSteps(120f, stepPx))
        assertEquals(-2, consumeWholeSteps(-130f, stepPx))
    }

    @Test fun `long continuous slide yields many steps`() {
        assertEquals(5, consumeWholeSteps(5 * stepPx, stepPx))
    }

    @Test fun `remainder pattern ticks continuously across events`() {
        var acc = 0f
        var steps = 0
        listOf(40f, 40f, 40f, 40f).forEach { d ->
            acc += d
            val whole = consumeWholeSteps(acc, stepPx)
            steps += whole
            acc -= whole * stepPx
        }
        assertEquals(2, steps)
        assertEquals(32f, acc)
    }

    @Test fun `slow release grants no bonus`() {
        assertEquals(0, flingBonusSteps(200f, flingPx))
        assertEquals(0, flingBonusSteps(-300f, flingPx))
    }

    @Test fun `a fling grows with speed instead of stopping at two`() {
        assertEquals(3, flingBonusSteps(-800f, flingPx))
        assertEquals(5, flingBonusSteps(-1500f, flingPx))
        assertTrue(flingBonusSteps(-3000f, flingPx) > flingBonusSteps(-1500f, flingPx))
    }

    @Test fun `it is still bounded, so a flick can never become a free scroll`() {
        assertEquals(12, flingBonusSteps(-99_999f, flingPx))
        assertEquals(-12, flingBonusSteps(99_999f, flingPx))
    }

    @Test fun `a release just past the threshold is worth one step, not zero`() {
        assertEquals(1, flingBonusSteps(-(flingPx + 1f), flingPx))
        assertEquals(-1, flingBonusSteps(flingPx + 1f, flingPx))
    }

    @Test fun `fast down-flick grants upward bonus`() {
        assertEquals(-3, flingBonusSteps(800f, flingPx))
        assertEquals(-5, flingBonusSteps(1500f, flingPx))
    }

    @Test fun `a long enough leftward drag backs out`() {
        assertTrue(commitsSwipeBack(-72f, backCommitPx))
        assertTrue(commitsSwipeBack(-300f, backCommitPx))
    }

    @Test fun `a short leftward drag does not back out`() {
        assertFalse(commitsSwipeBack(-71f, backCommitPx))
        assertFalse(commitsSwipeBack(0f, backCommitPx))
    }

    @Test fun `a rightward drag never backs out, however far`() {
        assertFalse(commitsSwipeBack(72f, backCommitPx))
        assertFalse(commitsSwipeBack(9999f, backCommitPx))
    }
}
