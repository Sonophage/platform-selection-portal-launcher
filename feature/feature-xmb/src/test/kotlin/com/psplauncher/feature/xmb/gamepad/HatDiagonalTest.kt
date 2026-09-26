package com.psplauncher.feature.xmb.gamepad

import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HatDiagonalTest {
    private val UP = GamepadAction.NAVIGATE_UP
    private val DOWN = GamepadAction.NAVIGATE_DOWN
    private val LEFT = GamepadAction.NAVIGATE_LEFT
    private val RIGHT = GamepadAction.NAVIGATE_RIGHT

    private fun dir(
        hatX: Float, hatY: Float,
        prevHatX: Float = 0f, prevHatY: Float = 0f,
        held: GamepadAction? = null,
    ) = hatDirectionNewestFirst(hatX, hatY, prevHatX, prevHatY, held)

    @Test fun `neutral is no direction`() {
        assertNull(dir(0f, 0f))
    }

    @Test fun `a single axis reports itself`() {
        assertEquals(LEFT, dir(-1f, 0f))
        assertEquals(RIGHT, dir(1f, 0f))
        assertEquals(UP, dir(0f, -1f))
        assertEquals(DOWN, dir(0f, 1f))
    }

    @Test fun `pressing LEFT while UP is held reports LEFT`() {
        assertEquals(LEFT, dir(hatX = -1f, hatY = -1f, prevHatX = 0f, prevHatY = -1f, held = UP))
    }

    @Test fun `pressing UP while LEFT is held reports UP`() {
        assertEquals(UP, dir(hatX = -1f, hatY = -1f, prevHatX = -1f, prevHatY = 0f, held = LEFT))
    }

    @Test fun `holding a diagonal keeps the direction already being navigated`() {
        assertEquals(UP, dir(hatX = -1f, hatY = -1f, prevHatX = -1f, prevHatY = -1f, held = UP))
        assertEquals(LEFT, dir(hatX = -1f, hatY = -1f, prevHatX = -1f, prevHatY = -1f, held = LEFT))
    }

    @Test fun `a true simultaneous diagonal keeps the historical Y tie-break`() {
        assertEquals(DOWN, dir(hatX = 1f, hatY = 1f, prevHatX = 0f, prevHatY = 0f, held = null))
    }

    @Test fun `releasing one axis of a diagonal falls back to the axis still held`() {
        assertEquals(DOWN, dir(hatX = 0f, hatY = 1f, prevHatX = -1f, prevHatY = 1f, held = LEFT))
    }

    @Test fun `deflection below the dead zone does not count`() {
        assertNull(dir(0.3f, -0.3f))
        assertEquals(UP, dir(hatX = 0.3f, hatY = -1f))
    }
}
