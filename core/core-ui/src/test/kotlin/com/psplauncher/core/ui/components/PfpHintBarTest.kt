package com.psplauncher.core.ui.components

import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PfpHintBarTest {
    private val back = ControllerPromptItem(GamepadAction.BACK, "Back")
    private val select = ControllerPromptItem(GamepadAction.SELECT, "Play")
    private val options = ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options")
    private val dpad = ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Scroll")

    @Test
    fun `back and confirm take the left, whatever order they arrive in`() {
        val groups = hintBarGroups(listOf(select, back))
        assertSame(back, groups.back)
        assertSame(select, groups.primary)
        assertTrue("a left-hand prompt leaked to the right", groups.right.isEmpty())
    }

    @Test
    fun `a fixed-glyph legend and the page keys are drawn, on the right`() {
        val prev = ControllerPromptItem(GamepadAction.PREV_CATEGORY, "Prev page")
        val next = ControllerPromptItem(GamepadAction.NEXT_CATEGORY, "Next page")
        val groups = hintBarGroups(listOf(dpad, prev, next, back))

        assertSame(back, groups.back)
        assertNull("the manual viewer has no confirm; the bar must not invent one", groups.primary)
        assertEquals(listOf(dpad, prev, next), groups.right)
    }

    @Test
    fun `nothing is lost and nothing is drawn twice`() {
        val items = listOf(
            back,
            ControllerPromptItem(GamepadAction.SELECT, "Launch"),
            ControllerPromptItem(GamepadAction.PREV_CATEGORY, "Prev"),
            ControllerPromptItem(GamepadAction.NEXT_CATEGORY, "Next"),
            options,
            ControllerPromptItem(GamepadAction.CHANGE_SORT, "Search"),
        )
        val groups = hintBarGroups(items)
        val drawn = listOfNotNull(groups.back, groups.primary) + groups.right

        assertEquals("a prompt was dropped or duplicated", items.size, drawn.size)
        assertEquals(items.toSet(), drawn.toSet())
    }

    @Test
    fun `a bar with no confirm still draws its back`() {
        val groups = hintBarGroups(listOf(back))
        assertSame(back, groups.back)
        assertNull(groups.primary)
        assertTrue(groups.right.isEmpty())
    }

    @Test
    fun `a bar with no back still draws its confirm`() {
        val groups = hintBarGroups(listOf(select, options))
        assertNull(groups.back)
        assertSame(select, groups.primary)
        assertEquals(listOf(options), groups.right)
    }

    @Test
    fun `a range prompt does not steal the primary slot`() {
        val range = ControllerPromptItem(listOf(GamepadAction.SELECT, GamepadAction.BACK), "Seek")
        val groups = hintBarGroups(listOf(range, back, select))
        assertSame("the range prompt took Back's place", back, groups.back)
        assertSame("the range prompt took the primary slot", select, groups.primary)
        assertEquals(listOf(range), groups.right)
    }
}
