package com.psplauncher.feature.appbar

import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertEquals
import org.junit.Test

class SectionLayoutTest {
    private fun move(action: GamepadAction, index: Int, rowCount: Int, total: Int) =
        sectionMove(action, index, rowCount, total)

    private val ROW = 8
    private val TOTAL = 44

    @Test
    fun `the top row walks one at a time and stops at both ends`() {
        assertEquals("right from the middle", 4, move(GamepadAction.NAVIGATE_RIGHT, 3, ROW, TOTAL))
        assertEquals("left from the middle", 2, move(GamepadAction.NAVIGATE_LEFT, 3, ROW, TOTAL))

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

    @Test
    fun `right steps one drawn column, whatever the panel measured`() {
        for (rows in 4..12) {
            val firstSlot = ROW
            val landed = sectionMove(GamepadAction.NAVIGATE_RIGHT, firstSlot, ROW, TOTAL, rows)
            assertEquals(
                "with a $rows-row grid, right from the list's first slot must land one column over",
                ROW + rows,
                landed,
            )
        }
    }

    @Test
    fun `down fills the drawn column before it stops`() {
        for (rows in 4..12) {
            var cur = ROW
            var steps = 0
            while (steps < rows * 2) {
                val next = sectionMove(GamepadAction.NAVIGATE_DOWN, cur, ROW, TOTAL, rows)
                if (next == cur) break
                cur = next; steps++
            }
            assertEquals(
                "a $rows-row column must be walkable to its last row and no further",
                rows - 1,
                steps,
            )
        }
    }

    @Test
    fun `the default row count is the documented constant`() {
        assertEquals(
            "sectionMove's default must match the grid's fallback, or an unmeasured panel desyncs",
            sectionMove(GamepadAction.NAVIGATE_RIGHT, ROW, ROW, TOTAL, SECTION_LIST_ROWS),
            sectionMove(GamepadAction.NAVIGATE_RIGHT, ROW, ROW, TOTAL),
        )
    }

    @Test
    fun `a zero or negative row count is survived, not crashed on`() {
        for (rows in -3..0) {
            sectionMove(GamepadAction.NAVIGATE_RIGHT, ROW, ROW, TOTAL, rows)
            sectionMove(GamepadAction.NAVIGATE_DOWN, ROW, ROW, TOTAL, rows)
        }
    }
}
