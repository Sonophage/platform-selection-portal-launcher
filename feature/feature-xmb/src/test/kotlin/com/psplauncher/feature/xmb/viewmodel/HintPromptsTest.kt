package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the bottom bar claims the buttons will do.
 *
 * The bar's whole job is to be true. A prompt naming an action the press does not perform is worse
 * than no prompt at all — it is the app teaching someone the wrong thing about their own
 * controller — and every branch here is one such claim.
 */
class HintPromptsTest {

    private fun state(
        items: List<XMBItem> = listOf(XMBItem(id = "g", title = "All Games", type = XMBItemType.ALL_GAMES)),
        selected: Int = 0,
        directLaunch: Boolean = true,
        menu: XMBContextMenu? = null,
        // Drilled in, expressed the way the state actually is: selectedPlatformId set is one of
        // the rungs drillOutStep reads. isInSubItem is derived, not stored, so it cannot be set.
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
        // The design's own line: "The primary action names what it acts on". Losing the target is
        // what makes a bar read as a legend rather than as a sentence about this row.
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
        // The placeholder every column falls back to when it has nothing in it. "Open Nothing here
        // yet" is the bar naming a press that does nothing, which is the one thing it is for.
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
        // Confirm runs the focused rail row and back closes the rail — and the right side is empty
        // because everything it would offer is already IN the rail.
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
    fun `the bar never names the sort knob — the strip owns it whole`() {
        // This assertion replaces "Sort and Filter are never both offered", which counted them
        // and allowed up to one. With neither offered any more that check passed at zero, which
        // is the shape of a test that cannot fail: it would have gone on passing if the prompt
        // came back in a third form.
        //
        // The contract now is that the bar does not name this button at all. The knob and its
        // value are one fact and the status strip states it whole — "⇅ All", "⇅ Title" — where
        // the bar used to carry the knob ("Filter") and the strip the value ("All"), two halves
        // in two bands with neither complete.
        val cases = listOf(state(), state(drilled = "psp"))
        cases.forEach { st ->
            val right = promptsFor(st).right.map { it.verb }
            assertTrue(
                "the bar must not name the sort knob; got $right",
                right.none { it == "Sort" || it == "Filter" },
            )
        }
    }
}
