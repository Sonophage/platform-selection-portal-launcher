package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the crossbar keeps from the stored row and what it keeps from the constant.
 *
 * This exists because the two sides of that question disagreed for a long time with nothing
 * watching. `CategoryRepositoryImpl` calls built-ins the ones "the user may hide/reorder but never
 * delete" and its `move()` writes swapped positions to the database; `canonicalXmbCategories`
 * rebuilt every built-in from the constant and sorted by the constant's position. Both behaviours
 * were commented, each comment was authoritative, and the user's reorder wrote to the database and
 * changed nothing on screen.
 *
 * The split asserted here: **id and icon are the launcher's, name and position are the user's.**
 */
class CanonicalCategoryBarTest {

    private fun cat(
        id: String,
        name: String,
        position: Int,
        iconKey: String = "ic_$id",
        gaming: Boolean = false,
    ) = Category(
        id = id,
        name = name,
        iconKey = iconKey,
        type = CategoryType.BUILT_IN,
        position = position,
        isGamingCategory = gaming,
    )

    /** A stand-in for the launcher's constant, in its own fixed order. */
    private val fallbacks = listOf(
        cat(BuiltInCategory.SETTINGS, "Settings", 0),
        cat("photos", "Photo", 1),
        cat("music", "Music", 2),
        cat(BuiltInCategory.GAMES, "Game", 3, gaming = true),
    )

    @Test
    fun `reordering a built-in moves it on the bar`() {
        // The exact shape the user reported: Games dragged to the front, Settings sent to the end.
        // Before this, the bar answered with the constant's order and the reorder was invisible.
        val stored = listOf(
            cat(BuiltInCategory.GAMES, "Game", 0, gaming = true),
            cat("photos", "Photo", 1),
            cat("music", "Music", 2),
            cat(BuiltInCategory.SETTINGS, "Settings", 3),
        )
        val bar = canonicalXmbCategories(stored, fallbacks).map { it.id }
        assertEquals(listOf(BuiltInCategory.GAMES, "photos", "music", BuiltInCategory.SETTINGS), bar)
    }

    @Test
    fun `renaming a built-in shows the new name on the bar`() {
        // The bar draws category.name, so a discarded rename is visible on screen, not just in the
        // database. Renaming Games to Emulation is what this user actually did.
        val stored = listOf(cat(BuiltInCategory.GAMES, "Emulation", 0, gaming = true))
        val games = canonicalXmbCategories(stored, fallbacks).first { it.id == BuiltInCategory.GAMES }
        assertEquals("Emulation", games.name)
    }

    @Test
    fun `a blank stored name falls back rather than leaving the bar unlabelled`() {
        val stored = listOf(cat(BuiltInCategory.GAMES, "   ", 0, gaming = true))
        val games = canonicalXmbCategories(stored, fallbacks).first { it.id == BuiltInCategory.GAMES }
        assertEquals("Game", games.name)
    }

    @Test
    fun `the icon stays canonical even when the stored row disagrees`() {
        // iconKey is not editable through Category Manager, so a stored value is stale data from an
        // older seed rather than an instruction. Custom art rides customIconUri instead.
        val stored = listOf(cat(BuiltInCategory.GAMES, "Game", 0, iconKey = "ic_something_else", gaming = true))
        val games = canonicalXmbCategories(stored, fallbacks).first { it.id == BuiltInCategory.GAMES }
        assertEquals("ic_games", games.iconKey)
    }

    @Test
    fun `a hidden built-in is dropped from the bar`() {
        // "Show On Bar" works by the row being absent from the visible set handed in here. The
        // claim is about what is dropped, so it is asserted on the hidden ids rather than on the
        // whole order: Settings is always kept and, with no stored row, keeps the constant's
        // position, which is a separate rule with its own test below.
        val stored = listOf(cat(BuiltInCategory.GAMES, "Game", 0, gaming = true))
        val bar = canonicalXmbCategories(stored, fallbacks).map { it.id }
        assertEquals(listOf(BuiltInCategory.SETTINGS, BuiltInCategory.GAMES), bar)
        assertTrue("a hidden built-in must not reach the bar", "photos" !in bar && "music" !in bar)
    }

    @Test
    fun `Settings survives even when hidden, or there is no way back into the manager`() {
        val bar = canonicalXmbCategories(emptyList(), fallbacks)
        assertEquals(listOf(BuiltInCategory.SETTINGS), bar.map { it.id })
        // With no stored row there is no user preference to honour, so it keeps the constant's
        // position. That is the only case where the constant still decides where something sits.
        assertEquals(0, bar.single().position)
    }

    @Test
    fun `a custom category interleaves with built-ins by position`() {
        // Custom rows always carried their stored position. Built-ins carrying the constant's
        // position instead is what made the two sort schemes incomparable, so a custom category
        // could not be placed between two built-ins however the user ordered them.
        val stored = listOf(
            cat(BuiltInCategory.GAMES, "Game", 0, gaming = true),
            Category(
                id = "custom_android_10", name = "Android", iconKey = "ic_android",
                type = CategoryType.MANUAL, position = 1,
            ),
            cat("photos", "Photo", 2),
            cat(BuiltInCategory.SETTINGS, "Settings", 3),
        )
        val bar = canonicalXmbCategories(stored, fallbacks).map { it.id }
        assertEquals(
            listOf(BuiltInCategory.GAMES, "custom_android_10", "photos", BuiltInCategory.SETTINGS),
            bar,
        )
    }

    @Test
    fun `the gaming flag still comes from the database`() {
        // Pre-existing behaviour worth keeping pinned while the copy list is being edited: losing
        // it hides "Move to Category" for collections and suppresses live refresh.
        val stored = listOf(cat("photos", "Photo", 0, gaming = true))
        val photos = canonicalXmbCategories(stored, fallbacks).first { it.id == "photos" }
        assertTrue(photos.isGamingCategory)
    }
}
