package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.psplauncher.core.ui.components.MenuState

class EnterOpensAppDrawerTest {
    private val games = Category(
        id = BuiltInCategory.GAMES, name = "Game", iconKey = "ic_games",
        type = CategoryType.BUILT_IN, position = 2, isGamingCategory = true,
    )
    private val recent = Category(
        id = BuiltInCategory.RECENTLY_PLAYED, name = "Last Played", iconKey = "ic_recent",
        type = CategoryType.BUILT_IN, position = 0,
    )

    private fun onCrossbar() = XMBUiState(

        showBootSequence = false,
        categories = listOf(recent, games),
        selectedCategoryIndex = 1,
        currentItems = listOf(XMBItem(id = "g1", title = "Crisis Core", gameId = 1L, platformId = "psp")),
    )

    @Test
    fun `on the crossbar it opens the drawer`() {
        assertTrue(onCrossbar().enterOpensAppDrawer)
    }

    @Test
    fun `drilled into a list it stays confirm`() {
        assertFalse(onCrossbar().copy(selectedPlatformId = "psp").enterOpensAppDrawer)
    }

    @Test
    fun `on the Last Played shelf it stays confirm`() {
        val shelf = onCrossbar().copy(selectedCategoryIndex = 0)
        assertTrue("the fixture is not on the shelf", shelf.onLastPlayedHome)
        assertFalse(shelf.enterOpensAppDrawer)
    }

    @Test
    fun `in the pill row it stays confirm`() {
        val inPills = onCrossbar().copy(pillCursor = PillCursor(itemId = "g1", index = 0))
        assertTrue("the fixture is not in the pill row", inPills.activePillIndex() != null)
        assertFalse(inPills.enterOpensAppDrawer)
    }

    @Test
    fun `under any overlay it stays confirm`() {
        assertFalse(
            onCrossbar().copy(
                activeContextMenu = XMBContextMenu(state = MenuState(title = "Options", rows = emptyList())),
            ).enterOpensAppDrawer,
        )
        assertFalse(onCrossbar().copy(notificationsOpen = true).enterOpensAppDrawer)
        assertFalse(onCrossbar().copy(activeSettingsScreen = "settings_display").enterOpensAppDrawer)
    }

    @Test
    fun `while searching it is text, not a press`() {
        assertFalse(onCrossbar().copy(search = SearchState(scope = SearchScope.ALL)).enterOpensAppDrawer)
    }

    @Test
    fun `boot is not the crossbar`() {
        assertFalse(onCrossbar().copy(showBootSequence = true).enterOpensAppDrawer)
    }
}
