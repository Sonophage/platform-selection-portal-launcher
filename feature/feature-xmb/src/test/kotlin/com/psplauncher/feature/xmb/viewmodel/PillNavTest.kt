package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertEquals
import org.junit.Test

class PillNavTest {
    private val right = GamepadAction.NAVIGATE_RIGHT
    private val left = GamepadAction.NAVIGATE_LEFT
    private val down = GamepadAction.NAVIGATE_DOWN

    @Test
    fun `a row with no pills passes every press through`() {
        assertEquals(PillNav.Pass, pillNav(right, current = null, count = 0))
        assertEquals(PillNav.Pass, pillNav(left, current = null, count = 0))
    }

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

    @Test
    fun `down does nothing from inside the row, and up is not this function's business`() {
        assertEquals(PillNav.Pass, pillNav(down, current = 1, count = 4))
        listOf(GamepadAction.NAVIGATE_UP, GamepadAction.SELECT).forEach {
            assertEquals("$it must pass", PillNav.Pass, pillNav(it, current = 1, count = 4))
        }
    }

    @Test
    fun `a row with no pills still takes none of them`() {
        listOf(right, left, down).forEach {
            assertEquals("$it was taken by a row with no pills", PillNav.Pass, pillNav(it, null, count = 0))
        }
    }

    @Test
    fun `the crossbar stays reachable, and costs five presses from a four-pill row`() {
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
