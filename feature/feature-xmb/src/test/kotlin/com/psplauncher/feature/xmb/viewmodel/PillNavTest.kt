package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Left and right on a row that has pills.
 *
 * The rule has to hold at its edges or the crossbar stops being reachable: a press swallowed one
 * time too many strands the cursor in a row of four buttons, and one swallowed too few steps the
 * category out from under someone who was aiming at Favorite.
 */
class PillNavTest {

    private val right = GamepadAction.NAVIGATE_RIGHT
    private val left = GamepadAction.NAVIGATE_LEFT

    @Test
    fun `a row with no pills passes every press through`() {
        // Platform cards, memory cards, media rows — most of the crossbar. If this ever returns
        // anything else the whole bar becomes unsteppable.
        assertEquals(PillNav.Pass, pillNav(right, current = null, count = 0))
        assertEquals(PillNav.Pass, pillNav(left, current = null, count = 0))
    }

    @Test
    fun `right enters at the first pill and left enters at the last`() {
        assertEquals(PillNav.Move(0), pillNav(right, current = null, count = 4))
        assertEquals(PillNav.Move(3), pillNav(left, current = null, count = 4))
    }

    @Test
    fun `inside the row the presses walk it`() {
        assertEquals(PillNav.Move(2), pillNav(right, current = 1, count = 4))
        assertEquals(PillNav.Move(0), pillNav(left, current = 1, count = 4))
    }

    @Test
    fun `falling off either end leaves the row and carries the press out with it`() {
        // NOT "spends the press leaving", which is the recents rail's rule and is a dead end here:
        // the rail has another way to open and this row does not, so a spent press would be
        // re-entered by the very next one and the crossbar would be unreachable from any game
        // list. See `the crossbar stays reachable` below, which is the assertion that failed.
        assertEquals(PillNav.ExitAndPass, pillNav(right, current = 3, count = 4))
        assertEquals(PillNav.ExitAndPass, pillNav(left, current = 0, count = 4))
    }

    @Test
    fun `a single pill is entered and left, never walked`() {
        assertEquals(PillNav.Move(0), pillNav(right, current = null, count = 1))
        assertEquals(PillNav.ExitAndPass, pillNav(right, current = 0, count = 1))
        assertEquals(PillNav.ExitAndPass, pillNav(left, current = 0, count = 1))
    }

    @Test
    fun `up and down are never the pill row's business`() {
        // They move the column cursor, which invalidates the pill cursor by itself — it is keyed
        // to the row's id. Swallowing them here would make a vertical press do nothing.
        listOf(GamepadAction.NAVIGATE_UP, GamepadAction.NAVIGATE_DOWN, GamepadAction.SELECT).forEach {
            assertEquals("$it must pass", PillNav.Pass, pillNav(it, current = 1, count = 4))
        }
    }

    @Test
    fun `the crossbar stays reachable, and costs five presses from a four-pill row`() {
        // The assertion this rule was built wrong for. Held down, right must EVENTUALLY hand a
        // press back to the crossbar; the first version never did, because leaving spent the
        // press and the next one walked straight back in. This loop ran forever.
        //
        // The number is the price of entering from both sides, recorded here so that if it ever
        // feels wrong there is something to argue with.
        var current: Int? = null
        var presses = 0
        repeat(20) {
            if (presses > 0 && current == null) return@repeat
            presses++
            when (val nav = pillNav(right, current, count = 4)) {
                is PillNav.Move -> current = nav.index
                PillNav.ExitAndPass -> current = null
                PillNav.Pass -> current = null
            }
        }
        assertEquals("presses to step one category from a four-pill row", 5, presses)
    }
}
