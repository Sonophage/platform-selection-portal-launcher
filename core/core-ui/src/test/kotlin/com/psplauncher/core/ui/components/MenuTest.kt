package com.psplauncher.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuTest {
    private enum class Act { PLAY, FAVOURITE, MARK, COLLECT, EMULATOR, ICONS, LOCATION, HIDE, DELETE, UNLINK }

    private fun row(
        action: Act,
        group: MenuGroup = MenuGroup.MAIN,
        destructive: Boolean = false,
        confirms: Boolean = destructive,
        pinned: Boolean = false,
    ) = MenuRow(action, action.name, group, destructive, confirms, pinnedToRoot = pinned)

    private val fullMenu = listOf(
        row(Act.PLAY),
        row(Act.FAVOURITE, MenuGroup.LIBRARY, pinned = true),
        row(Act.MARK, MenuGroup.LIBRARY),
        row(Act.COLLECT, MenuGroup.LIBRARY),
        row(Act.EMULATOR, MenuGroup.SETTINGS),
        row(Act.ICONS, MenuGroup.SETTINGS),
        row(Act.LOCATION, MenuGroup.SETTINGS),
        row(Act.HIDE, MenuGroup.REMOVE),
        row(Act.DELETE, MenuGroup.REMOVE, destructive = true),
    )

    private fun menu(rows: List<MenuRow<Act>> = fullMenu) = MenuState("Gran Turismo 4", rows)

    private fun shown(state: MenuState<Act>) = state.rowsShown().map { it.action?.name ?: it.label }

    @Test
    fun `a group of two or more folds, a group of one does not, and pinned rows stay put`() {
        assertEquals(
            listOf("PLAY", "FAVOURITE", "Library", "Settings", "HIDE", "DELETE"),
            shown(menu()),
        )
    }

    @Test
    fun `the delete is the last row even though it is pinned to the root`() {
        assertEquals("DELETE", shown(menu()).last())
    }

    @Test
    fun `a withheld action is drawn nowhere, so a duplicate cannot be reached twice`() {
        val state = menu().copy(withheld = setOf(Act.FAVOURITE, Act.MARK))

        assertTrue("a withheld row was drawn", shown(state).none { it == "FAVOURITE" || it == "MARK" })
        assertEquals(
            "with MARK withheld the Library group is down to one row and should hoist",
            "COLLECT",
            shown(state)[1],
        )
    }

    @Test
    fun `choosing a folded row opens its group, keeping the object's title`() {
        val opened = menu().chose(shown(menu()).indexOf("Settings"))

        assertTrue(opened is MenuSelect.Replace)
        val submenu = (opened as MenuSelect.Replace).state
        assertEquals(listOf("EMULATOR", "ICONS", "LOCATION"), shown(submenu))
        assertEquals("Gran Turismo 4", submenu.title)
        assertEquals("Settings", submenu.subtitle)
        assertEquals(0, submenu.selectedIndex)
    }

    @Test
    fun `a submenu does not fold again, which would hide its rows behind themselves`() {
        val submenu = menu().submenuFor(MenuGroup.SETTINGS)!!
        assertEquals(3, submenu.rowsShown().size)
        assertTrue("a submenu folded itself", submenu.rowsShown().none { it.opensSubmenu })
    }

    @Test
    fun `a destructive row asks first, and the cursor opens on Cancel`() {
        val chosen = menu().chose(shown(menu()).indexOf("DELETE"))

        assertTrue("the delete ran without asking", chosen is MenuSelect.Replace)
        val confirm = (chosen as MenuSelect.Replace).state
        assertEquals(listOf(CONFIRM_CANCEL_LABEL, "DELETE"), confirm.rowsShown().map { it.label })
        assertEquals(0, confirm.selectedIndex)
        assertEquals("DELETE?", confirm.subtitle)
    }

    @Test
    fun `answering yes runs the action instead of asking again`() {
        val confirm = (menu().chose(shown(menu()).indexOf("DELETE")) as MenuSelect.Replace).state
        assertEquals(MenuSelect.Run(Act.DELETE), confirm.chose(1))
    }

    @Test
    fun `answering no climbs back to the menu it came from`() {
        val confirm = (menu().chose(shown(menu()).indexOf("DELETE")) as MenuSelect.Replace).state
        val cancelled = confirm.chose(0)

        assertTrue(cancelled is MenuSelect.Replace)
        assertEquals(shown(menu()), shown((cancelled as MenuSelect.Replace).state))
    }

    @Test
    fun `a row that only unlinks runs straight away`() {
        val rows = listOf(row(Act.UNLINK, MenuGroup.REMOVE, destructive = true, confirms = false))
        assertEquals(MenuSelect.Run(Act.UNLINK), MenuState("Playlist", rows).chose(0))
    }

    @Test
    fun `Back climbs one level and then reports there is nowhere left to go`() {
        val submenu = menu().submenuFor(MenuGroup.LIBRARY)!!
        assertEquals("Gran Turismo 4", submenu.back()?.title)
        assertNull("Back at the root should close rather than loop", menu().back())
    }

    @Test
    fun `the cursor enters from the end it was moved from and stops at the edges`() {
        val root = menu()
        assertEquals(0, root.moved(+1).selectedIndex)
        assertEquals(root.rowsShown().lastIndex, root.moved(-1).selectedIndex)

        val atTop = root.at(0)
        assertEquals("the cursor wrapped off the top", 0, atTop.moved(-1).selectedIndex)

        val atEnd = root.at(root.rowsShown().lastIndex)
        assertEquals("the cursor wrapped off the bottom", atEnd.selectedIndex, atEnd.moved(+1).selectedIndex)
    }
}
