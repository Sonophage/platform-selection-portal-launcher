package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PillActionsTest {
    private fun state(directLaunch: Boolean = true) = XMBUiState(
        categories = listOf(
            Category(
                id = BuiltInCategory.GAMES, name = "Game", iconKey = "ic_games",
                type = CategoryType.BUILT_IN, position = 0, isGamingCategory = true,
            ),
        ),
        selectedCategoryIndex = 0,
        directLaunch = directLaunch,
    )

    private fun game(isFavorite: Boolean = false, androidApp: Boolean = false) = XMBItem(
        id = "g1", title = "Crisis Core", gameId = 1L, platformId = "psp",
        isFavorite = isFavorite, isAndroidApp = androidApp,
        packageName = if (androidApp) "com.example.game" else null,
    )

    private fun app() = XMBItem(
        id = "a1", title = "Spotify", packageName = "com.spotify.music",
    )

    private fun gameMenuIds(item: XMBItem, directLaunch: Boolean = true) = gameContextMenuItems(
        item = item,
        state = state(directLaunch),
        discCount = 1,
        onRecentShelf = false,
        hideLocation = null,
    ).map { it.id }

    @Test
    fun `every game pill is a row the game menu offers`() {
        listOf(game(), game(isFavorite = true), game(androidApp = true)).forEach { item ->
            val menu = gameMenuIds(item)
            pillsFor(item).forEach { pill ->
                assertTrue(
                    "pill '${pill.label}' dispatches '${pill.id}', which the game menu does not offer: $menu",
                    pill.id in menu,
                )
            }
        }
    }

    @Test
    fun `every app pill is a row the app menu offers`() {
        val menu = appContextMenuItems(state(), categoryId = null, onRecentShelf = false).map { it.id }
        pillsFor(app()).forEach { pill ->
            assertTrue(
                "pill '${pill.label}' dispatches '${pill.id}', which the app menu does not offer: $menu",
                pill.id in menu,
            )
        }
    }

    @Test
    fun `the menu cannot address a pill, so a menu index cannot either`() {
        val item    = game()
        val menu    = gameContextMenuItems(item, state(), discCount = 1, onRecentShelf = false, hideLocation = null)
        val pillIds = pillsFor(item).map { it.id }.toSet()
        val rows    = menuRows(menu, pillIds)

        assertTrue(
            "the menu drew a pill's own action: ${rows.map { it.id }.filter { it in pillIds }}",
            rows.none { it.id in pillIds },
        )

        val firstPill = pillsFor(item).first()
        val atThatIndex = rows.getOrNull(menu.indexOfFirst { it.id == firstPill.id })?.id
        assertTrue(
            "activating by index would have run '$atThatIndex' for the '${firstPill.label}' pill",
            atThatIndex != firstPill.id,
        )
    }

    @Test
    fun `the pill row is never visible on the home shelf`() {
        val onShelf = XMBUiState(
            categories = listOf(
                Category(
                    id = BuiltInCategory.RECENTLY_PLAYED, name = "Last Played", iconKey = "ic_recent",
                    type = CategoryType.BUILT_IN, position = 0,
                ),
            ),
            selectedCategoryIndex = 0,
            currentItems = listOf(game()),
            selectedItemIndex = 0,
        )
        assertTrue("the fixture is not on the shelf", onShelf.onLastPlayedHome)
        assertTrue("this row does have pills", pillsFor(game()).isNotEmpty())
        assertFalse("a door was offered into a row that is not drawn", onShelf.pillRowVisible)
    }

    @Test
    fun `the favourite pill follows the row it is drawn under`() {
        assertEquals("Favorite", pillsFor(game(isFavorite = false)).first { it.id == "favorite" }.label)
        assertEquals("Unfavorite", pillsFor(game(isFavorite = true)).first { it.id == "unfavorite" }.label)
    }

    @Test
    fun `a package-backed game is offered no emulator to change`() {
        assertTrue(pillsFor(game(androidApp = true)).none { it.id == "change_emulator" })
        assertTrue(pillsFor(game(androidApp = false)).any { it.id == "change_emulator" })
    }

    @Test
    fun `rows with no pills get no row at all`() {
        val platformCard = XMBItem(id = "card_psp", title = "PSP", platformId = "psp")
        val settingsRow = XMBItem(id = "settings_open", title = "Settings")
        val track = XMBItem(id = "t1", title = "Blue Monday", type = XMBItemType.MUSIC_TRACK)
        val collection = XMBItem(id = "col_1", title = "RPGs", collectionId = 1L, type = XMBItemType.COLLECTION)
        listOf(platformCard, settingsRow, track, collection).forEach {
            assertEquals("${'$'}{it.id} must draw no pill row", emptyList<XmbPill>(), pillsFor(it))
        }
    }
}
