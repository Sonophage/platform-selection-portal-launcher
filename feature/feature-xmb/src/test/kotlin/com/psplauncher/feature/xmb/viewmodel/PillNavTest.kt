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
    private val down = GamepadAction.NAVIGATE_DOWN

    @Test
    fun `a row with no pills passes every press through`() {
        // Platform cards, memory cards, media rows — most of the crossbar. If this ever returns
        // anything else the whole bar becomes unsteppable.
        assertEquals(PillNav.Pass, pillNav(right, current = null, count = 0))
        assertEquals(PillNav.Pass, pillNav(left, current = null, count = 0))
    }

    /**
     * RIGHT and DOWN enter it. LEFT does not, and used to.
     *
     * "Left enters at the last pill, so the row behaves the same whichever side you arrive from"
     * was true of this function and false on the screen. On a drilled-in list — All Games, a
     * platform card, a collection, which is where nearly every game row lives — LEFT is spent
     * backing out of the drill by a branch that returns before this function is reached. So the
     * row had one door on the screens that matter, and the mirror was decorative.
     *
     * Moving the pill check above that branch was the other option and is the one the owner
     * already refused on the recents shelf: four pills in front of the press that leaves a folder.
     */
    @Test
    fun `right and down enter the row, left does not`() {
        assertEquals(PillNav.Move(0), pillNav(right, current = null, count = 4))
        assertEquals(PillNav.Move(0), pillNav(down, current = null, count = 4))
        assertEquals(PillNav.Pass, pillNav(left, current = null, count = 4))
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
        assertEquals(PillNav.Move(0), pillNav(down, current = null, count = 1))
        assertEquals(PillNav.ExitAndPass, pillNav(right, current = 0, count = 1))
        assertEquals(PillNav.ExitAndPass, pillNav(left, current = 0, count = 1))
    }

    /**
     * DOWN is the way IN and nothing else.
     *
     * It was never the row's business at all until the row needed a door that every screen had.
     * Off the bottom of a column the press did nothing anywhere — including the home shelf, where
     * left and right are reserved for leaving and the pills had no controller route in at all.
     *
     * Leaving the row upward is NOT here: that is BACK's rule — spend the press, leave the
     * innermost thing — and it is handled where BACK's is. A second copy of it in this function
     * would be a second place to change it.
     */
    @Test
    fun `down does nothing from inside the row, and up is not this function's business`() {
        assertEquals(PillNav.Pass, pillNav(down, current = 1, count = 4))
        listOf(GamepadAction.NAVIGATE_UP, GamepadAction.SELECT).forEach {
            assertEquals("$it must pass", PillNav.Pass, pillNav(it, current = 1, count = 4))
        }
    }

    @Test
    fun `a row with no pills still takes none of them`() {
        // The guard that keeps DOWN dead where it was dead: a column of rows with no actions must
        // not swallow the press that says "I am at the bottom of this list".
        listOf(right, left, down).forEach {
            assertEquals("$it was taken by a row with no pills", PillNav.Pass, pillNav(it, null, count = 0))
        }
    }

    @Test
    fun `the crossbar stays reachable, and costs five presses from a four-pill row`() {
        // The assertion this rule was built wrong for. Held down, right must EVENTUALLY hand a
        // press back to the crossbar; the first version never did, because leaving spent the
        // press and the next one walked straight back in. This loop ran forever.
        //
        // The number is the price of the row being there at all, recorded here so that if it
        // ever feels wrong there is something to argue with. It did not change when LEFT stopped
        // entering: holding RIGHT enters, walks four, and hands the fifth press to the crossbar
        // either way.
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
