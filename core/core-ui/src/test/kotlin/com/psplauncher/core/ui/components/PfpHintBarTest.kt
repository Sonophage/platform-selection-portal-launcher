package com.psplauncher.core.ui.components

import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one bottom bar, and how it decides what goes where.
 *
 * Four screens drew four different footers — the crossbar's full-width bar, Search's inline row,
 * the App Drawer's pill and the detail pages' pill — and the crossbar's was the one to keep. It
 * could not simply be shared, because it took three named slots (back, primary, everything else)
 * and the other three screens have prompts that fit none of them: a D-pad legend that names a
 * position no setting remaps, and page keys on the shoulders.
 *
 * So the split is derived from the action each prompt already carries. These tests hold the
 * property that makes that safe: EVERY prompt handed in is drawn somewhere. A footer that
 * quietly dropped "Next page" would leave the manual viewer with no way out that it admits to.
 */
class PfpHintBarTest {

    private val back = ControllerPromptItem(GamepadAction.BACK, "Back")
    private val select = ControllerPromptItem(GamepadAction.SELECT, "Play")
    private val options = ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options")
    private val dpad = ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Scroll")

    @Test
    fun `back and confirm take the left, whatever order they arrive in`() {
        // Search listed Open before Back; the crossbar lists Back first. Both must produce the
        // same two slots, because "the button you press to get out is always in the same place"
        // is the whole reason back is left of the primary.
        //
        // WHICH is drawn first is the Row's business, not this function's — it draws back, then
        // the divider, then the primary, unconditionally. There is no branch there to test.
        val groups = hintBarGroups(listOf(select, back))
        assertSame(back, groups.back)
        assertSame(select, groups.primary)
        assertTrue("a left-hand prompt leaked to the right", groups.right.isEmpty())
    }

    /**
     * THE test this file exists for. The old three-slot bar had nowhere to put these.
     */
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
        // The App Drawer's six, which is the longest bar in the app.
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
        // Editing a note on the game's page: Cancel is the only press there is.
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

    /**
     * A multi-action prompt naming confirm among others is not the confirm.
     *
     * It cannot be: [ControllerPromptItem.tappableAction] refuses to dispatch a multi-action
     * prompt, so putting one in the primary slot would draw the loudest, most reachable position
     * on the bar as something a tap does nothing to.
     */
    @Test
    fun `a range prompt does not steal the primary slot`() {
        val range = ControllerPromptItem(listOf(GamepadAction.SELECT, GamepadAction.BACK), "Seek")
        val groups = hintBarGroups(listOf(range, back, select))
        assertSame("the range prompt took Back's place", back, groups.back)
        assertSame("the range prompt took the primary slot", select, groups.primary)
        assertEquals(listOf(range), groups.right)
    }
}
