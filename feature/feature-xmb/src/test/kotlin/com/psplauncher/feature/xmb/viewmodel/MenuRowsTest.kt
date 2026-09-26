package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuRowsTest {
    private fun row(id: String, group: MenuGroup = MenuGroup.MAIN, destructive: Boolean = false) =
        XMBContextMenuItem(id = id, label = id, group = group, isDestructive = destructive)

    @Test
    fun `a short menu is shown whole`() {
        val menu = listOf(row("a"), row("b"), row("c"))
        assertEquals(menu, menuRows(menu, pillIds = emptySet()))
    }

    @Test
    fun `the pill row's actions are not drawn twice`() {
        val menu = listOf(row("game_details"), row("favorite"), row("icon_display"))
        assertEquals(
            listOf("icon_display"),
            menuRows(menu, pillIds = setOf("game_details", "favorite")).map { it.id },
        )
    }

    @Test
    fun `a hidden row dispatches but is never drawn`() {
        val menu = listOf(row("a"), XMBContextMenuItem("play", "Play", hidden = true), row("b"))
        assertEquals(listOf("a", "b"), menuRows(menu, pillIds = emptySet()).map { it.id })
    }

    @Test
    fun `a long menu keeps every row, because the panel scrolls`() {
        val menu = (1..13).map { row("keep$it") } + row("remove", MenuGroup.REMOVE, destructive = true)
        val shown = menuRows(menu, pillIds = emptySet()).map { it.id }

        assertEquals("a row was dropped rather than scrolled to", menu.size, shown.size)
        assertTrue("the removal must survive", "remove" in shown)
        assertEquals("the destructive row is last", "remove", shown.last())
    }

    @Test
    fun `the groups run main, library, settings, category, remove`() {
        val scrambled = listOf(
            row("wipe", MenuGroup.REMOVE, destructive = true),
            row("category", MenuGroup.CATEGORY),
            row("setting", MenuGroup.SETTINGS),
            row("library", MenuGroup.LIBRARY),
            row("play"),
        )
        assertEquals(
            listOf("play", "library", "setting", "category", "wipe"),
            menuRows(scrambled, pillIds = emptySet()).map { it.id },
        )
    }

    @Test
    fun `the order within a group is the order the builder wrote`() {
        val menu = listOf(
            row("second", MenuGroup.LIBRARY),
            row("first", MenuGroup.MAIN),
            row("third", MenuGroup.LIBRARY),
        )
        assertEquals(
            listOf("first", "second", "third"),
            menuRows(menu, pillIds = emptySet()).map { it.id },
        )
    }

}
