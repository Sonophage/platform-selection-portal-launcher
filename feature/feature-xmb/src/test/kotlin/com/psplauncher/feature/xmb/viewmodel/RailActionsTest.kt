package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the right rail shows once the pills and the cap have had their say.
 *
 * The assertion that matters is the destructive one. Every builder in this app sorts removals
 * last, so the obvious `take(9)` keeps "Move to Category" and drops "Remove from Library" — a menu
 * that cannot remove anything, arrived at by writing the most natural line of code in the file.
 */
class RailActionsTest {

    private fun row(id: String, destructive: Boolean = false) =
        XMBContextMenuItem(id = id, label = id, isDestructive = destructive)

    @Test
    fun `a short menu is shown whole`() {
        val menu = listOf(row("a"), row("b"), row("c"))
        assertEquals(menu, railRows(menu, pillIds = emptySet()))
    }

    @Test
    fun `the pill row's actions are not drawn twice`() {
        val menu = listOf(row("game_details"), row("favorite"), row("icon_display"))
        assertEquals(
            listOf("icon_display"),
            railRows(menu, pillIds = setOf("game_details", "favorite")).map { it.id },
        )
    }

    @Test
    fun `the More row never reaches the rail`() {
        // The builders still produce it — they split long menus around it and the rail wants both
        // halves back. It must not become an icon that opens a list the rail exists to replace.
        val menu = listOf(row("a"), XMBContextMenuItem(MENU_MORE_ITEM_ID, "More…"), row("b"))
        assertEquals(listOf("a", "b"), railRows(menu, pillIds = emptySet()).map { it.id })
    }

    @Test
    fun `an overlong menu keeps every destructive row and cuts from the middle`() {
        val menu = (1..12).map { row("keep$it") } + listOf(row("remove", destructive = true))
        val shown = railRows(menu, pillIds = emptySet(), capacity = 5).map { it.id }
        assertEquals(5, shown.size)
        assertTrue("the removal must survive the cut", "remove" in shown)
        assertEquals(listOf("keep1", "keep2", "keep3", "keep4", "remove"), shown)
    }

    @Test
    fun `a menu that is nothing but destructive rows is not truncated into uselessness`() {
        // capacity - destructive.size goes negative here. Without the coerce this throws, and it
        // throws while raising a menu, which is a crash on a button press.
        val menu = (1..4).map { row("wipe$it", destructive = true) }
        assertEquals(4, railRows(menu, pillIds = emptySet(), capacity = 2).size)
    }

    @Test
    fun `the cap is what the design measured, not a round number`() {
        // Nine circles on a 99px pitch is what fits between the status strip and the hint bar.
        // If this changes, the rail layout changed with it.
        assertEquals(9, RAIL_CAPACITY)
    }
}
