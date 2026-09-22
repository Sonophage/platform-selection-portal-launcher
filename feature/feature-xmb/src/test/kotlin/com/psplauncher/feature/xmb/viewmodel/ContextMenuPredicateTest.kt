package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [hasContextMenu] so the idle hint and the real Y/Triangle trigger stay in sync. Each
 * case mirrors a branch of `XMBViewModel.onItemLongPress` / `dispatchGamepadAction(OPEN_CONTEXT_MENU)`.
 */
class ContextMenuPredicateTest {

    private fun state(
        categoryId: String,
        item: XMBItem,
        musicNav: MusicNav = MusicNav.Root,
        videoNav: VideoNav = VideoNav.Root,
        photoNav: PhotoNav = PhotoNav.Root,
    ) = XMBUiState(
        categories = listOf(Category(categoryId, categoryId, categoryId, type = CategoryType.BUILT_IN, position = 0)),
        selectedCategoryIndex = 0,
        currentItems = listOf(item),
        selectedItemIndex = 0,
        musicNav = musicNav,
        videoNav = videoNav,
        photoNav = photoNav,
    )

    @Test
    fun `game row has a context menu`() {
        val item = XMBItem(id = "g1", title = "Crisis Core", gameId = 1L)
        assertTrue(item.hasContextMenu(state(BuiltInCategory.GAMES, item)))
    }

    @Test
    fun `platform row has a context menu`() {
        val item = XMBItem(id = "psp", title = "PSP", platformId = "psp")
        assertTrue(item.hasContextMenu(state(BuiltInCategory.GAMES, item)))
    }

    @Test
    fun `collection row has a context menu`() {
        val item = XMBItem(id = "c1", title = "RPGs", collectionId = 1L, type = XMBItemType.COLLECTION)
        assertTrue(item.hasContextMenu(state(BuiltInCategory.GAMES, item)))
    }

    @Test
    fun `all-games folder has a context menu`() {
        val item = XMBItem(id = "all", title = "All Games", type = XMBItemType.ALL_GAMES)
        assertTrue(item.hasContextMenu(state(BuiltInCategory.GAMES, item)))
    }

    @Test
    fun `app row has a context menu`() {
        val item = XMBItem(id = "app", title = "Spotify", packageName = "com.spotify.music")
        assertTrue(item.hasContextMenu(state(BuiltInCategory.GAMES, item)))
    }

    @Test
    fun `plain standard row has no context menu`() {
        val item = XMBItem(id = "x", title = "Nothing", type = XMBItemType.STANDARD)
        assertFalse(item.hasContextMenu(state(BuiltInCategory.GAMES, item)))
    }

    @Test
    fun `music track has a context menu`() {
        val item = XMBItem(id = "tr1", title = "Track", type = XMBItemType.MUSIC_TRACK)
        assertTrue(item.hasContextMenu(state(BuiltInCategory.MUSIC, item)))
    }

    @Test
    fun `now playing row has a context menu`() {
        val item = XMBItem(id = XMBViewModel.NOW_PLAYING_ITEM_ID, title = "Now Playing")
        assertTrue(item.hasContextMenu(state(BuiltInCategory.MUSIC, item)))
    }

    @Test
    fun `video file has a context menu`() {
        val item = XMBItem(id = "vid_1", title = "Clip", type = XMBItemType.VIDEO_FILE)
        assertTrue(item.hasContextMenu(state(BuiltInCategory.VIDEO, item)))
    }

    @Test
    fun `video file with wrong id prefix has no context menu`() {
        val item = XMBItem(id = "bad_1", title = "Clip", type = XMBItemType.VIDEO_FILE)
        assertFalse(item.hasContextMenu(state(BuiltInCategory.VIDEO, item)))
    }

    @Test
    fun `photo file has a context menu`() {
        val item = XMBItem(id = "pho_1", title = "Pic", type = XMBItemType.PHOTO_FILE)
        assertTrue(item.hasContextMenu(state(BuiltInCategory.PHOTO, item)))
    }

    @Test
    fun `a music track carries its menu into any column`() {
        // This asserted the opposite: that a track outside the Music column had no menu. That was
        // true of the old rule, which asked where the CURSOR was, and it is what made Y do nothing
        // on the music and book rows of the Last Played shelf — a column that is none of the
        // media libraries by definition. A track is a track wherever it is listed, and Search has
        // always shown them outside Music too.
        val item = XMBItem(id = "mt_1", title = "Track", type = XMBItemType.MUSIC_TRACK)

        assertTrue(item.hasContextMenu(state(BuiltInCategory.MUSIC, item)))
        assertTrue(item.hasContextMenu(state(BuiltInCategory.RECENTLY_PLAYED, item)))
        assertTrue(item.hasContextMenu(state(BuiltInCategory.GAMES, item)))
    }

    @Test
    fun `A and Y route a media row to the same library`() {
        // dispatchCategorySelection asked where the CURSOR was while hasContextMenu asked what the
        // ROW was, so on the Last Played shelf -- a column that is none of the media libraries --
        // Y opened a menu and A did nothing at all. Books, music and video were all dead to the
        // confirm button there. Both now read menuHostCategory, and this is the pair: whatever a
        // row's menu is built from is what its activation is dispatched to.
        val cursor = BuiltInCategory.RECENTLY_PLAYED
        val cases = mapOf(
            BuiltInCategory.LIBRARY to XMBItem(id = "book_1", title = "A Book", type = XMBItemType.LIBRARY_BOOK),
            BuiltInCategory.MUSIC to XMBItem(id = "mt_1", title = "A Track", type = XMBItemType.MUSIC_TRACK),
            BuiltInCategory.VIDEO to XMBItem(id = "vid_1", title = "A Film", type = XMBItemType.VIDEO_FILE),
            BuiltInCategory.PHOTO to XMBItem(id = "pho_1", title = "A Photo", type = XMBItemType.PHOTO_FILE),
        )
        for ((expected, item) in cases) {
            assertEquals(
                "${item.type} must dispatch to its own library from any column",
                expected,
                item.menuHostCategory(cursor),
            )
            assertTrue("${item.type} must also still offer its menu", item.hasContextMenu(state(cursor, item)))
        }
    }

    @Test
    fun `a row that belongs to no library still answers to the column it is in`() {
        // The other half, and the reason owningCategory returns null rather than guessing: a plain
        // row has no library of its own, so it falls back to the current category exactly as
        // before. Without this the change would have handed every anonymous row a media menu.
        val plain = XMBItem(id = "row_1", title = "Something", type = XMBItemType.STANDARD)

        assertFalse(plain.hasContextMenu(state(BuiltInCategory.RECENTLY_PLAYED, plain)))
    }

}
