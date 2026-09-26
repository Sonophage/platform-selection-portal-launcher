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

class ContextMenusTest {
    private fun category(id: String, gaming: Boolean = false) = Category(
        id = id, name = id, iconKey = id, type = CategoryType.BUILT_IN, position = 0,
    ).let { if (gaming) it.copy(isGamingCategory = true) else it }

    private fun state(
        categories: List<Category> = listOf(category(BuiltInCategory.GAMES, gaming = true)),
        selectedCategoryIndex: Int = 0,
        selectedCollectionId: Long? = null,
        selectedPlatformId: String? = null,
        directLaunch: Boolean = false,
    ) = XMBUiState(
        categories = categories,
        selectedCategoryIndex = selectedCategoryIndex,
        selectedCollectionId = selectedCollectionId,
        selectedPlatformId = selectedPlatformId,
        directLaunch = directLaunch,
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

    @Test
    fun `choose disc appears only for a multi-disc set`() {
        assertFalse("choose_disc" in ids(gameContextMenuItems(game(), state(), 1, false, null)))
        assertTrue("choose_disc" in ids(gameContextMenuItems(game(), state(), 2, false, null)))
    }

    @Test
    fun `an app off the home shelf has no remove from recent`() {
        val items = ids(appContextMenuItems(state(), categoryId = null, onRecentShelf = false))
        assertFalse(
            "an app reached from a category has no shelf entry to remove, so offering it would " +
                "name an action with nothing to act on",
            items.contains("remove_from_recent"),
        )
    }

    @Test
    fun `the home shelf offers one way to take an app off it, not two`() {
        val cats = listOf(category(BuiltInCategory.GAMES, gaming = true), category("retro", gaming = true))
        val shelf = ids(appContextMenuItems(state(cats), categoryId = "retro", onRecentShelf = true))
        assertTrue("the working one is offered", shelf.contains("remove_from_recent"))
        assertFalse(
            "hide_from_category writes a CATEGORY record and the shelf reads only RECENTS, so " +
                "offering it here would be a second item for one intent that does nothing",
            shelf.contains("hide_from_category"),
        )

        val elsewhere = ids(appContextMenuItems(state(cats), categoryId = "retro", onRecentShelf = false))
        assertTrue("still offered everywhere else", elsewhere.contains("hide_from_category"))
    }

    @Test
    fun `an app on the home shelf can be removed from recent`() {
        val items = ids(appContextMenuItems(state(), categoryId = null, onRecentShelf = true))
        assertTrue(
            "the shelf is the one place an app's recency is visible, so it is the one place it " +
                "can be taken off — UsageStatsManager owns the timestamp and will not forget it",
            items.contains("remove_from_recent"),
        )
    }

    @Test
    fun `remove from recent is offered only on the home shelf`() {
        assertFalse("remove_from_recent" in ids(gameContextMenuItems(game(), state(), 1, false, null)))
        assertTrue("remove_from_recent" in ids(gameContextMenuItems(game(), state(), 1, true, null)))
    }

    @Test
    fun `hide from here names the place, and vanishes where there is no place`() {
        val none = gameContextMenuItems(game(), state(), 1, false, null)
        assertFalse("hide_here" in ids(none))

        val inFavorites = gameContextMenuItems(
            game(), state(), 1, false,
            Triple(HideLocationType.FAVORITES, "", "Favorites"),
        )
        assertEquals(
            "Hide from Favorites",
            inFavorites.first { it.id == "hide_here" }.label,
        )
    }

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
        val items = ids(gameContextMenuItems(game(), state(selectedCollectionId = 7L), 1, false, null))
        assertFalse("remove_game" in items)
        assertFalse("remove_app" in items)
    }

    @Test
    fun `the missing bucket offers exactly one destructive action, and it is the permanent one`() {
        val items = gameContextMenuItems(
            game(), state(selectedPlatformId = XMBViewModel.MISSING_PLATFORM_ID), 1, false, null,
        )
        assertEquals(listOf("remove_missing"), items.filter { it.isDestructive }.map { it.id })

        assertFalse("remove_game" in ids(items))
    }

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
        val cats = listOf(category(BuiltInCategory.GAMES, gaming = true), category("retro", gaming = true))
        val items = ids(gameContextMenuItems(game(), state(cats, selectedCategoryIndex = 1), 1, false, null))
        assertFalse("move_category" in items)

        assertTrue("remove_category" in items)
        assertTrue("pin_category" in items)
    }

    @Test
    fun `move and add are hidden when there is nowhere to move to`() {
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

    @Test
    fun `favourite is a toggle, never both`() {
        val off = ids(gameContextMenuItems(game(isFavorite = false), state(), 1, false, null))
        assertTrue("favorite" in off)
        assertFalse("unfavorite" in off)

        val on = ids(gameContextMenuItems(game(isFavorite = true), state(), 1, false, null))
        assertTrue("unfavorite" in on)
        assertFalse("favorite" in on)
    }

    @Test
    fun `an android game entry can be demoted rather than only deleted`() {
        val items = ids(
            gameContextMenuItems(
                game(platformId = PlatformIds.ANDROID, packageName = "com.x"),
                state(), 1, false, null,
            ),
        )
        assertTrue("unmark_game" in items)
        assertTrue("remove_app" in items)
        assertFalse("remove_game" in items)
    }

    @Test
    fun `an app outside any category loses every per-category row`() {
        val items = ids(appContextMenuItems(state(), categoryId = null, onRecentShelf = false))
        listOf("remove", "pin", "hide_from_category").forEach {
            assertFalse("$it must not be offered with no category", it in items)
        }

        assertTrue("launch" in items)
        assertTrue("hide_everywhere" in items)
    }

    @Test
    fun `hide from category names the category`() {
        val cats = listOf(category("retro"))
        val items = appContextMenuItems(state(cats), categoryId = "retro", onRecentShelf = false)
        assertEquals("Hide from retro", items.first { it.id == "hide_from_category" }.label)
    }

    @Test
    fun `the music and video app categories get their display names, not their ids`() {
        val s = state()
        assertEquals("Music Apps", s.categoryDisplayNameOf(XMBViewModel.MUSIC_APPS_CATEGORY_ID))
        assertEquals("Video Apps", s.categoryDisplayNameOf(XMBViewModel.VIDEO_APPS_CATEGORY_ID))
    }

    @Test
    fun `recent removal is offered only where there is a stamp to clear`() {
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

    @Test
    fun `the windows card cannot be removed and offers its importer`() {
        val windows = ids(platformContextMenuItems(PlatformIds.WINDOWS, false, "Global: Icon"))
        assertTrue("import_pc_games" in windows)
        assertFalse("remove" in windows)

        val console = ids(platformContextMenuItems("psp", false, "Global: Icon"))
        assertFalse("import_pc_games" in console)
        assertTrue("remove" in console)
    }

    @Test
    fun `android libraries find apps where consoles scan folders`() {
        val android = ids(platformContextMenuItems(PlatformIds.ANDROID, false, "Global: Icon"))
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

    @Test
    fun `no menu ever repeats an id`() {
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
        val app = ids(appContextMenuItems(state(), "retro", onRecentShelf = false))
        assertEquals(app.distinct(), app)
    }

    @Test
    fun `play is in every game menu and drawn in none of them`() {
        listOf(true, false).forEach { direct ->
            listOf(true, false).forEach { shelf ->
                val where = "direct=$direct shelf=$shelf"
                val items = gameContextMenuItems(game(), state(directLaunch = direct), 1, shelf, null)
                val play = items.firstOrNull { it.id == "play" }
                assertTrue("$where: no play entry left to dispatch by id", play != null)
                assertTrue("$where: Play is drawn in the rail", play!!.hidden)
                assertFalse(
                    "$where: Play reached the rail anyway",
                    "play" in railRows(items, pillIds = emptySet()).map { it.id },
                )
            }
        }
    }

    @Test
    fun `every deep-linked Details row names a real DetailAction`() {
        val deepLinked = listOf("ARTWORK", "METADATA", "MANUAL", "REFRESH")
        deepLinked.forEach { name ->
            assertTrue(
                "the Details submenu writes detail_$name, which DetailAction does not have",
                com.psplauncher.feature.xmb.ui.detail.DetailAction.entries.any { it.name == name },
            )
        }
    }

    @Test
    fun `view game details is offered either way`() {
        listOf(true, false).forEach { direct ->
            assertTrue(
                "direct=$direct",
                "game_details" in ids(gameContextMenuItems(game(), state(directLaunch = direct), 1, false, null)),
            )
        }
    }

    @Test
    fun `a heading belongs to a row, and every group has exactly one`() {
        val items = gameContextMenuItems(game(), state(), 2, true, null)
        val headings = items.mapNotNull { it.heading }
        assertEquals("a heading is repeated", headings.distinct(), headings)
        assertTrue("no groups at all", headings.isNotEmpty())
        assertFalse("the first row starts a group", items.first().heading != null)
    }

    @Test
    fun `the category group's heading survives whichever of its rows exists`() {
        val main = category(BuiltInCategory.GAMES, gaming = true)
        val shooters = category("shooters", gaming = true)

        val all = listOf(main, shooters, category("rpgs", gaming = true))

        val fromMain = gameContextMenuItems(game(), state(all, 0), 1, false, null)
        assertEquals("Category", fromMain.first { it.id == "add_category" }.heading)

        val fromCustom = gameContextMenuItems(game(), state(all, 1), 1, false, null)
        assertEquals("Category", fromCustom.first { it.id == "move_category" }.heading)
        assertEquals(null, fromCustom.first { it.id == "remove_category" }.heading)
    }

    private fun rows(n: Int, headingsAt: Set<Int> = emptySet()) =
        (0 until n).map { XMBContextMenuItem("r$it", "Row $it", heading = "G$it".takeIf { _ -> it in headingsAt }) }

    @Test
    fun `a menu that fits is not collapsed`() {
        val short = rows(CONTEXT_MENU_MAX_ROWS)
        assertEquals(short to emptyList<XMBContextMenuItem>(), short.splitForOverflow())
        assertEquals(short, short.withOverflowRow())
    }

    @Test
    fun `More costs a row of the budget, so the panel never grows`() {
        val long = rows(20)
        assertEquals(CONTEXT_MENU_MAX_ROWS, long.withOverflowRow().size)
        assertEquals(MENU_MORE_ITEM_ID, long.withOverflowRow().last().id)
    }

    @Test
    fun `the split lands on a group boundary, so no heading is stranded`() {
        val items = rows(20, headingsAt = setOf(3, 6, 11))
        val (visible, overflow) = items.splitForOverflow()
        assertEquals(6, visible.size)
        assertEquals("r6", overflow.first().id)
        assertEquals(items.size, visible.size + overflow.size)
    }

    @Test
    fun `with no boundary to use, it splits on the budget rather than not at all`() {
        val (visible, overflow) = rows(20).splitForOverflow()
        assertEquals(CONTEXT_MENU_MAX_ROWS - 1, visible.size)
        assertEquals(20 - (CONTEXT_MENU_MAX_ROWS - 1), overflow.size)
    }

    @Test
    fun `nothing is lost between the visible menu and More`() {
        val items = gameContextMenuItems(game(), state(), 3, true, null)
        val (visible, overflow) = items.splitForOverflow()
        assertEquals(ids(items), ids(visible) + ids(overflow))
    }
}
