package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import com.psplauncher.core.domain.model.HideLocationType
import com.psplauncher.core.domain.model.PlatformIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The context menus' conditional logic.
 *
 * None of this had a test before: the builders lived inside a 9,400-line ViewModel that no unit
 * test can construct, so every branch — disc count, collection membership, the missing bucket, the
 * gaming-category rules — was only ever checked by opening the menu on a device and looking.
 */
class ContextMenusTest {

    private fun category(id: String, gaming: Boolean = false) = Category(
        id = id, name = id, iconKey = id, type = CategoryType.BUILT_IN, position = 0,
    ).let { if (gaming) it.copy(isGamingCategory = true) else it }

    private fun state(
        categories: List<Category> = listOf(category(BuiltInCategory.GAMES, gaming = true)),
        selectedCategoryIndex: Int = 0,
        selectedCollectionId: Long? = null,
        selectedPlatformId: String? = null,
    ) = XMBUiState(
        categories = categories,
        selectedCategoryIndex = selectedCategoryIndex,
        selectedCollectionId = selectedCollectionId,
        selectedPlatformId = selectedPlatformId,
    )

    private fun game(
        platformId: String? = "psp",
        isFavorite: Boolean = false,
        packageName: String? = null,
        subtitle: String? = null,
    ) = XMBItem(
        id = "g1", title = "Crisis Core", gameId = 1L,
        platformId = platformId, isFavorite = isFavorite, packageName = packageName,
        subtitle = subtitle,
    )

    private fun ids(items: List<XMBContextMenuItem>) = items.map { it.id }

    // ── Disc count ────────────────────────────────────────────────────────

    @Test
    fun `choose disc appears only for a multi-disc set`() {
        // The only way to reach a non-primary disc when direct launch skips Game Detail's picker,
        // so a single-disc game offering it would be a dead row and a multi-disc one without it
        // would strand every disc but the first.
        assertFalse("choose_disc" in ids(gameContextMenuItems(game(), state(), 1, false, null)))
        assertTrue("choose_disc" in ids(gameContextMenuItems(game(), state(), 2, false, null)))
    }

    // ── Where the row came from ───────────────────────────────────────────

    @Test
    fun `remove from recent is offered only on the home shelf`() {
        assertFalse("remove_from_recent" in ids(gameContextMenuItems(game(), state(), 1, false, null)))
        assertTrue("remove_from_recent" in ids(gameContextMenuItems(game(), state(), 1, true, null)))
    }

    @Test
    fun `hide from here names the place, and vanishes where there is no place`() {
        val none = gameContextMenuItems(game(), state(), 1, false, null)
        assertFalse("hide_here" in ids(none))

        val inFavourites = gameContextMenuItems(
            game(), state(), 1, false,
            Triple(HideLocationType.FAVORITES, "", "Favorites"),
        )
        assertEquals(
            "Hide from Favorites",
            inFavourites.first { it.id == "hide_here" }.label,
        )
    }

    // ── Collections ───────────────────────────────────────────────────────

    @Test
    fun `remove from collection appears only inside one`() {
        assertFalse("remove_from_collection" in ids(gameContextMenuItems(game(), state(), 1, false, null)))
        assertTrue(
            "remove_from_collection" in
                ids(gameContextMenuItems(game(), state(selectedCollectionId = 7L), 1, false, null)),
        )
    }

    @Test
    fun `nothing destructive is offered from inside a collection`() {
        // Removing the game from the library while looking at it through a collection would delete
        // far more than the row you are pointing at.
        val items = ids(gameContextMenuItems(game(), state(selectedCollectionId = 7L), 1, false, null))
        assertFalse("remove_game" in items)
        assertFalse("remove_app" in items)
    }

    // ── The missing bucket ────────────────────────────────────────────────

    @Test
    fun `the missing bucket offers exactly one destructive action, and it is the permanent one`() {
        val items = gameContextMenuItems(
            game(), state(selectedPlatformId = XMBViewModel.MISSING_PLATFORM_ID), 1, false, null,
        )
        assertEquals(listOf("remove_missing"), items.filter { it.isDestructive }.map { it.id })
        // Not the ordinary remove as well: this bucket is the entry's last visible trace, and two
        // deletes that mean different things on one menu is how the wrong one gets pressed.
        assertFalse("remove_game" in ids(items))
    }

    // ── Gaming categories ─────────────────────────────────────────────────

    @Test
    fun `the main games category can only copy a game out, never move or remove it`() {
        val cats = listOf(category(BuiltInCategory.GAMES, gaming = true), category("retro", gaming = true))
        val items = ids(gameContextMenuItems(game(), state(cats, selectedCategoryIndex = 0), 1, false, null))
        assertTrue("add_category" in items)
        assertFalse("move_category" in items)
        assertFalse("remove_category" in items)
    }

    @Test
    fun `a custom gaming category can remove and pin, and can move when there is somewhere to go`() {
        val cats = listOf(
            category(BuiltInCategory.GAMES, gaming = true),
            category("retro", gaming = true),
            category("handhelds", gaming = true),
        )
        val items = ids(gameContextMenuItems(game(), state(cats, selectedCategoryIndex = 1), 1, false, null))
        assertTrue("move_category" in items)
        assertTrue("remove_category" in items)
        assertTrue("pin_category" in items)
    }

    @Test
    fun `move is hidden when the only other gaming category is Main Game`() {
        // Main Game is never a destination, so a library of Main Game plus one custom category has
        // nowhere to move to — and this is what I got wrong writing the test above: I assumed two
        // categories meant a destination existed. Offering Move there opens an empty picker.
        val cats = listOf(category(BuiltInCategory.GAMES, gaming = true), category("retro", gaming = true))
        val items = ids(gameContextMenuItems(game(), state(cats, selectedCategoryIndex = 1), 1, false, null))
        assertFalse("move_category" in items)
        // The rows that do not depend on a destination are still there.
        assertTrue("remove_category" in items)
        assertTrue("pin_category" in items)
    }

    @Test
    fun `move and add are hidden when there is nowhere to move to`() {
        // Main Game is never a target, so a library with only Main Game has no destination and
        // offering the row would open a picker with nothing in it.
        val cats = listOf(category(BuiltInCategory.GAMES, gaming = true))
        val items = ids(gameContextMenuItems(game(), state(cats, selectedCategoryIndex = 0), 1, false, null))
        assertFalse("add_category" in items)
    }

    @Test
    fun `pin flips to unpin for a pinned row`() {
        val cats = listOf(category(BuiltInCategory.GAMES, gaming = true), category("retro", gaming = true))
        val items = ids(
            gameContextMenuItems(game(subtitle = "Pinned"), state(cats, selectedCategoryIndex = 1), 1, false, null),
        )
        assertTrue("unpin_category" in items)
        assertFalse("pin_category" in items)
    }

    // ── Favourite toggles ─────────────────────────────────────────────────

    @Test
    fun `favourite is a toggle, never both`() {
        val off = ids(gameContextMenuItems(game(isFavorite = false), state(), 1, false, null))
        assertTrue("favorite" in off)
        assertFalse("unfavorite" in off)

        val on = ids(gameContextMenuItems(game(isFavorite = true), state(), 1, false, null))
        assertTrue("unfavorite" in on)
        assertFalse("favorite" in on)
    }

    // ── Android entries ───────────────────────────────────────────────────

    @Test
    fun `an android game entry can be demoted rather than only deleted`() {
        val items = ids(
            gameContextMenuItems(
                game(platformId = XMBViewModel.ANDROID_PLATFORM_ID, packageName = "com.x"),
                state(), 1, false, null,
            ),
        )
        assertTrue("unmark_game" in items)
        assertTrue("remove_app" in items)
        assertFalse("remove_game" in items)
    }

    // ── The app menu ──────────────────────────────────────────────────────

    @Test
    fun `an app outside any category loses every per-category row`() {
        val items = ids(appContextMenuItems(state(), categoryId = null))
        listOf("remove", "pin", "hide_from_category").forEach {
            assertFalse("$it must not be offered with no category", it in items)
        }
        // And keeps the ones that still mean something.
        assertTrue("launch" in items)
        assertTrue("hide_everywhere" in items)
    }

    @Test
    fun `hide from category names the category`() {
        val cats = listOf(category("retro"))
        val items = appContextMenuItems(state(cats), categoryId = "retro")
        assertEquals("Hide from retro", items.first { it.id == "hide_from_category" }.label)
    }

    @Test
    fun `the music and video app categories get their display names, not their ids`() {
        // Their ids are "music" and "videos", which would read as lower-case nouns in a menu.
        val s = state()
        assertEquals("Music Apps", s.categoryDisplayNameOf(XMBViewModel.MUSIC_APPS_CATEGORY_ID))
        assertEquals("Video Apps", s.categoryDisplayNameOf(XMBViewModel.VIDEO_APPS_CATEGORY_ID))
    }

    // ── The media menus ───────────────────────────────────────────────────

    @Test
    fun `recent removal is offered only where there is a stamp to clear`() {
        // Four media, one rule: a row that has never been opened is not on the shelf, so offering
        // to take it off is a row that does nothing. Each menu implements this separately, which
        // is exactly why it is worth asserting together.
        assertFalse("book_remove_recent" in ids(bookContextMenuItems(hasOpenStamp = false)))
        assertTrue("book_remove_recent" in ids(bookContextMenuItems(hasOpenStamp = true)))

        assertFalse("remove_from_recent" in ids(musicTrackContextMenuItems(null, hasPlayStamp = false)))
        assertTrue("remove_from_recent" in ids(musicTrackContextMenuItems(null, hasPlayStamp = true)))

        assertFalse(
            "video_remove_recent" in
                ids(videoFileContextMenuItems(false, 0L, hasWatchStamp = false, inPlaylist = false)),
        )
        assertTrue(
            "video_remove_recent" in
                ids(videoFileContextMenuItems(false, 0L, hasWatchStamp = true, inPlaylist = false)),
        )
    }

    @Test
    fun `resume appears only when there is somewhere to resume to`() {
        assertFalse("video_resume" in ids(videoFileContextMenuItems(false, 0L, false, false)))
        assertTrue("video_resume" in ids(videoFileContextMenuItems(false, 90_000L, false, false)))
    }

    @Test
    fun `removing from a playlist is offered only from inside one`() {
        assertFalse("video_remove_playlist" in ids(videoFileContextMenuItems(false, 0L, false, inPlaylist = false)))
        assertTrue("video_remove_playlist" in ids(videoFileContextMenuItems(false, 0L, false, inPlaylist = true)))

        assertFalse("remove_from_playlist" in ids(musicTrackContextMenuItems(playlistId = null, hasPlayStamp = false)))
        assertTrue("remove_from_playlist" in ids(musicTrackContextMenuItems(playlistId = 3L, hasPlayStamp = false)))
    }

    @Test
    fun `the now playing menu says what the button will do, not what is happening`() {
        assertEquals("Pause", nowPlayingContextMenuItems(isPlaying = true).first().label)
        assertEquals("Resume", nowPlayingContextMenuItems(isPlaying = false).first().label)
    }

    // ── Cards ─────────────────────────────────────────────────────────────

    @Test
    fun `the windows card cannot be removed and offers its importer`() {
        // It is managed by the PC import system: removing the card would orphan every imported
        // game, and the import section is the only way to put more in it.
        val windows = ids(platformContextMenuItems(PlatformIds.WINDOWS, false, "Global: Icon"))
        assertTrue("import_pc_games" in windows)
        assertFalse("remove" in windows)

        val console = ids(platformContextMenuItems("psp", false, "Global: Icon"))
        assertFalse("import_pc_games" in console)
        assertTrue("remove" in console)
    }

    @Test
    fun `android libraries find apps where consoles scan folders`() {
        val android = ids(platformContextMenuItems(XMBViewModel.ANDROID_PLATFORM_ID, false, "Global: Icon"))
        assertTrue("find_games" in android)
        assertFalse("scan_roms" in android)

        val console = ids(platformContextMenuItems("psp", false, "Global: Icon"))
        assertTrue("scan_roms" in console)
        assertFalse("find_games" in console)
    }

    @Test
    fun `pin flips to unpin on a pinned card`() {
        assertTrue("pin" in ids(platformContextMenuItems("psp", pinned = false, "Global: Icon")))
        assertTrue("unpin" in ids(platformContextMenuItems("psp", pinned = true, "Global: Icon")))
    }

    @Test
    fun `a collection can only be moved when somewhere valid exists`() {
        assertFalse(
            "move_collection_category" in
                ids(collectionRowContextMenuItems(isPinned = false, hasOtherCategory = false)),
        )
        assertTrue(
            "move_collection_category" in
                ids(collectionRowContextMenuItems(isPinned = false, hasOtherCategory = true)),
        )
    }

    // ── No duplicates anywhere ────────────────────────────────────────────

    @Test
    fun `no menu ever repeats an id`() {
        // The handlers dispatch on id, so a repeat is two rows where pressing either runs the first
        // one's action — and the game menu builds from six independent conditions.
        val cats = listOf(category(BuiltInCategory.GAMES, gaming = true), category("retro", gaming = true))
        val states = listOf(
            state(), state(cats, 1), state(selectedCollectionId = 7L),
            state(selectedPlatformId = XMBViewModel.MISSING_PLATFORM_ID),
        )
        states.forEach { st ->
            listOf(1, 2).forEach { discs ->
                listOf(false, true).forEach { shelf ->
                    val items = ids(gameContextMenuItems(game(), st, discs, shelf, null))
                    assertEquals("duplicate in $items", items.distinct(), items)
                }
            }
        }
        val app = ids(appContextMenuItems(state(), "retro"))
        assertEquals(app.distinct(), app)
    }
}
