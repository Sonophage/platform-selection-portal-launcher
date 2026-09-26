package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import com.psplauncher.core.domain.model.PlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.psplauncher.core.ui.components.MenuState

class PlayStateMenuTest {
    private fun state() = XMBUiState(
        categories = listOf(
            Category(
                id = BuiltInCategory.GAMES, name = "Game", iconKey = "ic_games",
                type = CategoryType.BUILT_IN, position = 0, isGamingCategory = true,
            ),
        ),
        selectedCategoryIndex = 0,
    )

    private fun game() = XMBItem(id = "g1", title = "Crisis Core", gameId = 1L, platformId = "psp")

    @Test
    fun `the game menu offers a way to mark it`() {
        val ids = gameContextMenuItems(game(), state(), 1, false, null).mapNotNull { it.action }
        assertTrue("no way into the Mark As submenu: $ids", "play_state" in ids)
    }

    @Test
    fun `every state's menu id resolves back to that state`() {
        PlayState.entries.forEach { state ->
            val id = "pstate_${state.name}"
            assertEquals(
                "the id the submenu writes for ${state.label} does not resolve back",
                state,
                PlayState.fromName(id.removePrefix("pstate_")),
            )
        }
    }

    @Test
    fun `the unmarked row clears rather than naming a state`() {
        assertNull(PlayState.fromName("none"))
    }

    @Test
    fun `an unknown stored value is unmarked`() {
        assertNull(PlayState.fromName("ABANDONED"))
        assertNull(PlayState.fromName(null))
    }

    @Test
    fun `the badges are distinguishable from each other`() {
        val marks = PlayState.entries.map { it.mark }
        assertEquals("two states draw the same badge: $marks", marks.distinct().size, marks.size)
        assertTrue("a badge is blank", marks.none { it.isBlank() })
    }
}
