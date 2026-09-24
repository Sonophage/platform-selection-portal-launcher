package com.psplauncher.feature.appbar

import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The 8q body's cursor: one flat index over two differently shaped halves.
 *
 * What these are really guarding is that the index stays a valid position in `visibleApps` for
 * every press from every position. It is the same index Launch, the Y menu and Add to Cross Bar
 * read, so a move that lands one past the end of a short column does not misdraw a cursor — it
 * acts on the wrong app, or on none.
 */
class SectionLayoutTest {

    private fun move(action: GamepadAction, index: Int, rowCount: Int, total: Int) =
        sectionMove(action, index, rowCount, total)

    // 8 emulators up top, 36 other apps below.
    private val ROW = 8
    private val TOTAL = 44

    @Test
    fun `the top row walks one at a time and stops at both ends`() {
        assertEquals("right from the middle", 4, move(GamepadAction.NAVIGATE_RIGHT, 3, ROW, TOTAL))
        assertEquals("left from the middle", 2, move(GamepadAction.NAVIGATE_LEFT, 3, ROW, TOTAL))
        // Nothing wraps. A drawer where the last app is one press from the first makes the same
        // press mean two different things depending on where you are.
        assertEquals("left at the start", 0, move(GamepadAction.NAVIGATE_LEFT, 0, ROW, TOTAL))
        assertEquals("right at the end", ROW - 1, move(GamepadAction.NAVIGATE_RIGHT, ROW - 1, ROW, TOTAL))
    }

    @Test
    fun `down enters the list and up comes back to the first tile`() {
        val firstListSlot = move(GamepadAction.NAVIGATE_DOWN, 5, ROW, TOTAL)
        assertEquals("down from the row enters the list at its first slot", ROW, firstListSlot)
        assertEquals("up from the list's top row returns to the row", 0, move(GamepadAction.NAVIGATE_UP, firstListSlot, ROW, TOTAL))
    }

    @Test
    fun `the list is filled column-first, so down is one and right is six`() {
        // This is the assertion that fails if the list is ever built row-first: down would have
        // to move by the column count and right by one, which is the exact opposite of this.
        assertEquals("down moves to the next row of the same column", ROW + 1, move(GamepadAction.NAVIGATE_DOWN, ROW, ROW, TOTAL))
        assertEquals("right moves a whole column", ROW + SECTION_LIST_ROWS, move(GamepadAction.NAVIGATE_RIGHT, ROW, ROW, TOTAL))
        assertEquals("left moves a whole column back", ROW, move(GamepadAction.NAVIGATE_LEFT, ROW + SECTION_LIST_ROWS, ROW, TOTAL))
    }

    @Test
    fun `down stops at the bottom of a column instead of spilling into the next`() {
        val bottomOfFirstColumn = ROW + SECTION_LIST_ROWS - 1
        assertEquals("the column ends here", bottomOfFirstColumn, move(GamepadAction.NAVIGATE_DOWN, bottomOfFirstColumn, ROW, TOTAL))
    }

    @Test
    fun `right into a short last column lands on its last entry`() {
        // 8 up top and 20 below: the last column holds two entries, at rows 0 and 1. Coming at it
        // from row 5 of the full column beside it, the naive +6 is past the end of the list — an
        // index that would read a different app than the one under the cursor, or none at all.
        val total = ROW + 20
        val row5OfThirdColumn = ROW + 2 * SECTION_LIST_ROWS + 5
        assertEquals("clamped to the short column's last entry", total - 1, move(GamepadAction.NAVIGATE_RIGHT, row5OfThirdColumn, ROW, total))
    }

    @Test
    fun `right refuses when there is no column to the right`() {
        val total = ROW + 12
        val lastColumnTop = ROW + SECTION_LIST_ROWS
        assertEquals("no fourth column exists", lastColumnTop, move(GamepadAction.NAVIGATE_RIGHT, lastColumnTop, ROW, total))
    }

    @Test
    fun `a tab with no matching apps is all list, and up does nothing`() {
        // Recently Used before usage access is granted, or Games on a device with none.
        assertEquals("up has no row to return to", 0, move(GamepadAction.NAVIGATE_UP, 0, 0, 30))
        assertEquals("down still walks the list", 1, move(GamepadAction.NAVIGATE_DOWN, 0, 0, 30))
    }

    @Test
    fun `a tab that holds every app has no list to enter`() {
        assertEquals("down has nowhere to go", 3, move(GamepadAction.NAVIGATE_DOWN, 3, 10, 10))
    }

    @Test
    fun `an empty drawer parks the cursor rather than returning a negative index`() {
        assertEquals("nothing to select", 0, move(GamepadAction.NAVIGATE_DOWN, 0, 0, 0))
    }
}
