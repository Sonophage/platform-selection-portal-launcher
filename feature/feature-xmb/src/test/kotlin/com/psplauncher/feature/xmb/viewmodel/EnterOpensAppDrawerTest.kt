package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enter opens the App Drawer on the crossbar, and confirms everywhere else.
 *
 * Enter is bound to SELECT — it IS the keyboard's confirm. Claiming it outright would leave a
 * keyboard user unable to open a folder, start a game, or pick a row in the options rail, which is
 * the same fault the keyboard pass shipped once already: a binding made to serve one screen that
 * silently broke another.
 *
 * So the whole feature is the exclusions, and this file is one case per exclusion. The screens
 * where confirm has real work are the ones that keep it.
 */
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
        // The default state is still booting, and boot is a blocking overlay — a fixture that left
        // it alone would be asserting about a key press over a boot screen.
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
        // Inside All Games or a console card, confirm opens the thing under the cursor. That is
        // the press a keyboard user needs most and the one screen they would notice losing.
        assertFalse(onCrossbar().copy(selectedPlatformId = "psp").enterOpensAppDrawer)
    }

    @Test
    fun `on the Last Played shelf it stays confirm`() {
        // Confirm launches what you were playing there — the main verb on the screen the launcher
        // opens to.
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
        // Something else owns the keyboard: the rail's confirm runs an action, the sheet's opens a
        // notification, Settings is a screen of its own.
        assertFalse(
            onCrossbar().copy(
                activeContextMenu = XMBContextMenu(title = "Options", items = emptyList()),
            ).enterOpensAppDrawer,
        )
        assertFalse(onCrossbar().copy(notificationsOpen = true).enterOpensAppDrawer)
        assertFalse(onCrossbar().copy(activeSettingsScreen = "settings_display").enterOpensAppDrawer)
    }

    @Test
    fun `while searching it is text, not a press`() {
        // Enter in a search field submits the search. Taking it would make the box impossible to
        // finish with — the exact shape of the bug where binding Q and E made them untypable.
        assertFalse(onCrossbar().copy(search = SearchState(scope = SearchScope.ALL)).enterOpensAppDrawer)
    }

    @Test
    fun `boot is not the crossbar`() {
        assertFalse(onCrossbar().copy(showBootSequence = true).enterOpensAppDrawer)
    }
}
