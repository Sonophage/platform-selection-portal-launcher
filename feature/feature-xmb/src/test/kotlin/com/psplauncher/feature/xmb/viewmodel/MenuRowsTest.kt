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
            menuRows(menu, pillIds = emptySet(), bundle = false).map { it.id },
        )
    }

    @Test
    fun `a group of two or more collapses into one row that opens a submenu`() {
        val menu = listOf(
            row("play"),
            row("emulator", MenuGroup.SETTINGS),
            row("icons", MenuGroup.SETTINGS),
        )
        val rows = menuRows(menu, pillIds = emptySet())

        assertEquals(listOf("play", groupRowId(MenuGroup.SETTINGS)), rows.map { it.id })
        assertEquals("the submenu row is not marked as one", true, rows.last().opensSubmenu)
        assertEquals("Settings", rows.last().label)
    }

    @Test
    fun `a group of one is hoisted to the root rather than hidden behind a press`() {
        val menu = listOf(row("play"), row("icons", MenuGroup.SETTINGS))
        val rows = menuRows(menu, pillIds = emptySet())

        assertEquals(listOf("play", "icons"), rows.map { it.id })
        assertEquals("a lone row was buried in a submenu", false, rows.last().opensSubmenu)
    }

    @Test
    fun `the rows that must always be one press away stay at the root`() {
        val menu = listOf(
            row("play"),
            row("favorite", MenuGroup.LIBRARY),
            row("mark_as", MenuGroup.LIBRARY),
            row("collections", MenuGroup.LIBRARY),
            row("hide", MenuGroup.REMOVE),
            row("remove_game", MenuGroup.REMOVE, destructive = true),
        )
        val rows = menuRows(menu, pillIds = emptySet()).map { it.id }

        assertEquals(
            listOf("play", "favorite", groupRowId(MenuGroup.LIBRARY), "hide", "remove_game"),
            rows,
        )
    }

    @Test
    fun `a submenu is not bundled again, which would make it unreachable`() {
        val menu = listOf(
            row("play"),
            row("emulator", MenuGroup.SETTINGS),
            row("icons", MenuGroup.SETTINGS),
            row("location", MenuGroup.SETTINGS),
        )
        val parent = XMBContextMenu(title = "Gran Turismo 4", items = menu)
        val submenu = parent.submenuFor(groupRowId(MenuGroup.SETTINGS))

        assertEquals("no submenu was built", true, submenu != null)
        assertEquals(
            listOf("emulator", "icons", "location"),
            menuRows(submenu!!.items, pillIds = emptySet(), bundle = false).map { it.id },
        )
        assertEquals("the submenu cannot be backed out of", parent, submenu.parent)
        assertEquals("the title stopped naming the object", "Gran Turismo 4", submenu.title)
        assertEquals("Settings", submenu.subtitle)
        assertEquals("the cursor is not on the first row", 0, submenu.selectedIndex)
    }

    @Test
    fun `a row that stays at the root is never pulled into a submenu`() {
        val menu = listOf(
            row("favorite", MenuGroup.LIBRARY),
            row("mark_as", MenuGroup.LIBRARY),
            row("collections", MenuGroup.LIBRARY),
        )
        val submenu = XMBContextMenu(title = "x", items = menu).submenuFor(groupRowId(MenuGroup.LIBRARY))

        assertEquals(listOf("mark_as", "collections"), submenu!!.items.map { it.id })
    }


    @Test
    fun `the delete is still the last row once its group is bundled`() {
        val menu = listOf(
            row("play"),
            row("hide_here", MenuGroup.REMOVE),
            row("hide_everywhere", MenuGroup.REMOVE),
            row("remove_game", MenuGroup.REMOVE, destructive = true),
        )
        val rows = menuRows(menu, pillIds = emptySet()).map { it.id }

        assertEquals(
            "the delete floated above the rows it should sit under",
            "remove_game",
            rows.last(),
        )
        assertEquals(listOf("play", groupRowId(MenuGroup.REMOVE), "remove_game"), rows)
    }
}
