package com.psplauncher.feature.settings.ui

import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertEquals
import org.junit.Test

class GridCursorTest {
    private val cols = 7
    private val count = 20

    private fun step(from: Int, action: GamepadAction) = gridCursorStep(from, cols, count, action)

    @Test
    fun `left and right stop at the edges of their own row`() {
        assertEquals(1, step(0, GamepadAction.NAVIGATE_RIGHT))
        assertEquals(0, step(1, GamepadAction.NAVIGATE_LEFT))

        assertEquals(6, step(6, GamepadAction.NAVIGATE_RIGHT))
        assertEquals(7, step(7, GamepadAction.NAVIGATE_LEFT))
    }

    @Test
    fun `down from a column the last row does not have lands on its final item`() {
        assertEquals(19, step(13, GamepadAction.NAVIGATE_DOWN))

        assertEquals(13, step(6, GamepadAction.NAVIGATE_DOWN))

        assertEquals(19, step(19, GamepadAction.NAVIGATE_DOWN))
        assertEquals(19, step(15, GamepadAction.NAVIGATE_DOWN))
    }

    @Test
    fun `only the first row reaches the header above the grid`() {
        assertEquals(GRID_CURSOR_HEADER, step(0, GamepadAction.NAVIGATE_UP))
        assertEquals(GRID_CURSOR_HEADER, step(6, GamepadAction.NAVIGATE_UP))

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
        assertEquals(9, step(9, GamepadAction.SELECT))
        assertEquals(9, step(9, GamepadAction.BACK))
        assertEquals(9, step(9, GamepadAction.OPEN_CONTEXT_MENU))
    }

    @Test
    fun `an empty or unmeasured grid parks on the header`() {
        assertEquals(GRID_CURSOR_HEADER, gridCursorStep(5, 0, 20, GamepadAction.NAVIGATE_DOWN))
        assertEquals(GRID_CURSOR_HEADER, gridCursorStep(5, 7, 0, GamepadAction.NAVIGATE_DOWN))
    }
}
