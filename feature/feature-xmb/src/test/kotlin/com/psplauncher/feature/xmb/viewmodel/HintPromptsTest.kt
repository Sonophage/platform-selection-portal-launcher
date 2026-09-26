package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HintPromptsTest {
    private fun state(
        items: List<XMBItem> = listOf(XMBItem(id = "g", title = "All Games", type = XMBItemType.ALL_GAMES)),
        selected: Int = 0,
        directLaunch: Boolean = true,
        menu: XMBContextMenu? = null,

        drilled: String? = null,
    ) = XMBUiState(
        categories = listOf(
            Category(
                id = BuiltInCategory.GAMES, name = "Game", iconKey = "ic_games",
                type = CategoryType.BUILT_IN, position = 0, isGamingCategory = true,
            ),
        ),
        currentItems = items,
        selectedItemIndex = selected,
        directLaunch = directLaunch,
        activeContextMenu = menu,
        selectedPlatformId = drilled,
    )

    @Test
    fun `the primary names what it acts on`() {
        val p = promptsFor(state()).primary
        assertEquals("Open", p?.verb)
        assertEquals("All Games", p?.target)
        assertEquals(GamepadAction.SELECT, p?.action)
    }

    @Test
    fun `a game's verb follows direct launch, because that setting IS the question`() {
        val game = listOf(XMBItem(id = "1", title = "Crisis Core", gameId = 1L))
        assertEquals("Play", promptsFor(state(items = game, directLaunch = true)).primary?.verb)
        assertEquals("Details", promptsFor(state(items = game, directLaunch = false)).primary?.verb)
    }

    @Test
    fun `each kind of row gets its own verb, not a generic Open`() {
        fun verb(item: XMBItem) = promptsFor(state(items = listOf(item))).primary?.verb
        assertEquals("Play", verb(XMBItem(id = "t", title = "T", type = XMBItemType.MUSIC_TRACK)))
        assertEquals("Play", verb(XMBItem(id = "v", title = "V", type = XMBItemType.VIDEO_FILE)))
        assertEquals("Read", verb(XMBItem(id = "b", title = "B", type = XMBItemType.LIBRARY_BOOK)))
        assertEquals("View", verb(XMBItem(id = "p", title = "P", type = XMBItemType.PHOTO_FILE)))
        assertEquals("Launch", verb(XMBItem(id = "a", title = "A", packageName = "com.x")))
        assertEquals("Open", verb(XMBItem(id = "c", title = "C", type = XMBItemType.COLLECTION)))
    }

    @Test
    fun `an empty column promises nothing`() {
        val empty = listOf(XMBItem(id = "e", title = "Nothing here yet", type = XMBItemType.EMPTY))
        assertNull(promptsFor(state(items = empty)).primary)
    }

    @Test
    fun `back is named after what it DOES, which at the root is opening the drawer`() {
        assertEquals("Apps", promptsFor(state()).back.verb)
        assertEquals("Back", promptsFor(state(drilled = "psp")).back.verb)
    }

    @Test
    fun `the rail owns the bar while it is open`() {
        val menu = XMBContextMenu(
            title = "Crisis Core",
            items = listOf(
                XMBContextMenuItem("icon_display", "Icon Display"),
                XMBContextMenuItem("file_location", "View File Location"),
            ),
            selectedIndex = 1,
        )
        val prompts = promptsFor(state(menu = menu))
        assertEquals("Select", prompts.primary?.verb)
        assertEquals("View File Location", prompts.primary?.target)
        assertEquals("Close", prompts.back.verb)
        assertTrue("the rail's own actions must not be repeated on the right", prompts.right.isEmpty())
    }

    @Test
    fun `Search is offered at the root and withdrawn inside a drill`() {
        assertTrue(promptsFor(state()).right.any { it.verb == "Search" })
        assertTrue(promptsFor(state(drilled = "psp")).right.none { it.verb == "Search" })
    }

    @Test
    fun `Sort and Filter are one button and never both`() {
        val right = promptsFor(state()).right.map { it.verb }
        assertTrue("Sort and Filter both offered: $right", right.count { it == "Sort" || it == "Filter" } <= 1)
    }
}
