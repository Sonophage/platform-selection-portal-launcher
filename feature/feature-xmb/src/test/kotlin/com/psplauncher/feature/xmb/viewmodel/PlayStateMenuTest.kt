package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import com.psplauncher.core.domain.model.PlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Marking a game Playing / Completed / Backlog.
 *
 * The pair here is a menu id and an enum name. The submenu builds `pstate_${'$'}{state.name}` and the
 * handler reads it back with [PlayState.fromName], so a renamed constant does not break the build
 * and does not crash — the press simply stops doing anything, which is the failure nobody reports.
 * Same shape as the pill row's id pairing, one file over.
 */
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
        val ids = gameContextMenuItems(game(), state(), 1, false, null).map { it.id }
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

    /**
     * The clear row. "Unmarked" is a real row rather than an absence, because the only other way
     * out of a state would be picking a different one — and a flag you can set but not unset is
     * the shape of every flag that ends up stuck on.
     */
    @Test
    fun `the unmarked row clears rather than naming a state`() {
        assertNull(PlayState.fromName("none"))
    }

    /**
     * A value the enum does not have reads as unmarked, not as a crash. The column is TEXT and
     * nothing stops an older or newer build writing a name this one has never heard of.
     */
    @Test
    fun `an unknown stored value is unmarked`() {
        assertNull(PlayState.fromName("ABANDONED"))
        assertNull(PlayState.fromName(null))
    }

    /**
     * Three states, three distinct marks. They are drawn one glyph wide with no label beside them,
     * so two states sharing a glyph would be two states nobody can tell apart on the row.
     */
    @Test
    fun `the badges are distinguishable from each other`() {
        val marks = PlayState.entries.map { it.mark }
        assertEquals("two states draw the same badge: $marks", marks.distinct().size, marks.size)
        assertTrue("a badge is blank", marks.none { it.isBlank() })
    }
}
