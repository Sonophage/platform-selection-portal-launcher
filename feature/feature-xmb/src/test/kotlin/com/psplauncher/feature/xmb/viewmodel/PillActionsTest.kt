package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pair the pill row lives or dies on: every pill id must be an id its row's context menu
 * offers, because a pill dispatches by opening that menu and activating that row.
 *
 * A renamed menu id does not break the build and does not crash — the pill simply stops doing
 * anything, which nobody reports and nobody notices until they wonder why Favorite "never works".
 * So the menus here are the REAL ones, built from the same builders the ViewModel calls.
 */
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
        val menu = appContextMenuItems(state(), categoryId = null).map { it.id }
        pillsFor(app()).forEach { pill ->
            assertTrue(
                "pill '${pill.label}' dispatches '${pill.id}', which the app menu does not offer: $menu",
                pill.id in menu,
            )
        }
    }

    /**
     * Why a pill dispatches by ID and never by position.
     *
     * The rail drops every id the pill row already carries, so the two lists are not the same
     * list and never can be: an index taken from the menu addresses a DIFFERENT action in the one
     * activation reads. That is what shipped — Details ran Manage Collections, Favorite ran
     * whatever had slid up into its slot — and nothing threw, because both lists are the same
     * menu in the same order and every index in range is a real action.
     */
    @Test
    fun `the rail cannot address a pill, so a menu index cannot either`() {
        val item    = game()
        val menu    = gameContextMenuItems(item, state(), discCount = 1, onRecentShelf = false, hideLocation = null)
        val pillIds = pillsFor(item).map { it.id }.toSet()
        val rail    = railRows(menu, pillIds)

        assertTrue(
            "the rail drew a pill's own action: ${rail.map { it.id }.filter { it in pillIds }}",
            rail.none { it.id in pillIds },
        )

        // Concretely: the first pill's index in the menu names something else in the rail.
        val firstPill = pillsFor(item).first()
        val atThatIndex = rail.getOrNull(menu.indexOfFirst { it.id == firstPill.id })?.id
        assertTrue(
            "activating by index would have run '$atThatIndex' for the '${firstPill.label}' pill",
            atThatIndex != firstPill.id,
        )
    }

    @Test
    fun `the favourite pill follows the row it is drawn under`() {
        // Both halves, because a pill that always says "Favorite" and always dispatches "favorite"
        // would pass a test that only ever looked at an unfavourited game.
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
        // Each of these reaches a DIFFERENT context menu, or none, so app pills on any of them
        // would dispatch ids that menu has never heard of. A platform card and a music track both
        // carry enough of an XMBItem to look like an app row if the check is only "has a package".
        val platformCard = XMBItem(id = "card_psp", title = "PSP", platformId = "psp")
        val settingsRow = XMBItem(id = "settings_open", title = "Settings")
        val track = XMBItem(id = "t1", title = "Blue Monday", type = XMBItemType.MUSIC_TRACK)
        val collection = XMBItem(id = "col_1", title = "RPGs", collectionId = 1L, type = XMBItemType.COLLECTION)
        listOf(platformCard, settingsRow, track, collection).forEach {
            assertEquals("${'$'}{it.id} must draw no pill row", emptyList<XmbPill>(), pillsFor(it))
        }
    }
}
