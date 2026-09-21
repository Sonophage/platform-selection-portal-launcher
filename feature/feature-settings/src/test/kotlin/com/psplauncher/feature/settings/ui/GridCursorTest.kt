package com.psplauncher.feature.settings.ui

import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The icon picker's cursor, at the edges where grid cursors go wrong.
 *
 * The catalogue is fifty-odd icons in a grid whose width depends on the screen, so the last row
 * is nearly always short. Every case below is one a user hits in the first ten seconds of using
 * the thing: the end of a short row, the left edge, the top row, and the row above the grid.
 */
class GridCursorTest {

    // Seven columns, twenty items: two full rows and a last row of six.
    private val cols = 7
    private val count = 20

    private fun step(from: Int, action: GamepadAction) = gridCursorStep(from, cols, count, action)

    @Test
    fun `left and right stop at the edges of their own row`() {
        assertEquals(1, step(0, GamepadAction.NAVIGATE_RIGHT))
        assertEquals(0, step(1, GamepadAction.NAVIGATE_LEFT))
        // Clamping, not wrapping: index 6 is the right edge of row 0, and RIGHT there must not
        // drop to index 7, the left of row 1.
        assertEquals(6, step(6, GamepadAction.NAVIGATE_RIGHT))
        assertEquals(7, step(7, GamepadAction.NAVIGATE_LEFT))
    }

    @Test
    fun `down from a column the last row does not have lands on its final item`() {
        // Index 13 is the last of row 1. Row 2 holds 14..19, so this one is fine.
        assertEquals(19, step(13, GamepadAction.NAVIGATE_DOWN))
        // Index 6 is column 6 of row 0; 6 + 7 = 13 exists, so it moves normally.
        assertEquals(13, step(6, GamepadAction.NAVIGATE_DOWN))
        // From the last row there is nowhere below, so it holds rather than running off the end.
        assertEquals(19, step(19, GamepadAction.NAVIGATE_DOWN))
        assertEquals(19, step(15, GamepadAction.NAVIGATE_DOWN))
    }

    @Test
    fun `only the first row reaches the header above the grid`() {
        assertEquals(GRID_CURSOR_HEADER, step(0, GamepadAction.NAVIGATE_UP))
        assertEquals(GRID_CURSOR_HEADER, step(6, GamepadAction.NAVIGATE_UP))
        // From the second row UP is a row up, not a jump out of the grid.
        assertEquals(0, step(7, GamepadAction.NAVIGATE_UP))
        assertEquals(7, step(14, GamepadAction.NAVIGATE_UP))
    }

    @Test
    fun `the header is a single row with nowhere sideways to go`() {
        assertEquals(GRID_CURSOR_HEADER, step(GRID_CURSOR_HEADER, GamepadAction.NAVIGATE_UP))
        assertEquals(GRID_CURSOR_HEADER, step(GRID_CURSOR_HEADER, GamepadAction.NAVIGATE_LEFT))
        assertEquals(GRID_CURSOR_HEADER, step(GRID_CURSOR_HEADER, GamepadAction.NAVIGATE_RIGHT))
        assertEquals(0, step(GRID_CURSOR_HEADER, GamepadAction.NAVIGATE_DOWN))
    }

    @Test
    fun `a non-directional press moves nothing`() {
        // The control. The caller passes every action through, so anything that is not a
        // direction has to be a no-op here rather than silently resetting the cursor.
        assertEquals(9, step(9, GamepadAction.SELECT))
        assertEquals(9, step(9, GamepadAction.BACK))
        assertEquals(9, step(9, GamepadAction.OPEN_CONTEXT_MENU))
    }

    @Test
    fun `an empty or unmeasured grid parks on the header`() {
        // Adaptive columns are computed at layout time, so the first composition can legitimately
        // ask with a column count of zero. Dividing by it would be the crash.
        assertEquals(GRID_CURSOR_HEADER, gridCursorStep(5, 0, 20, GamepadAction.NAVIGATE_DOWN))
        assertEquals(GRID_CURSOR_HEADER, gridCursorStep(5, 7, 0, GamepadAction.NAVIGATE_DOWN))
    }
}
