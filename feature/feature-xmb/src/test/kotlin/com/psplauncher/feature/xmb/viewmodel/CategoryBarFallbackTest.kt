package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BUILT_IN_CATEGORIES
import com.psplauncher.core.domain.model.BuiltInCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryBarFallbackTest {
    @Test
    fun `the crossbar's fallback IS the canonical list, not a copy of it`() {
        assertSame(BUILT_IN_CATEGORIES, XMBViewModel.FALLBACK_CATEGORIES)
    }

    @Test
    fun `Library is in it, which is the drift this was written for`() {
        assertTrue(BUILT_IN_CATEGORIES.any { it.id == BuiltInCategory.LIBRARY })
    }

    @Test
    fun `a stored built-in keeps its canonical icon instead of the database's`() {
        val storedWithJunkIcon = BUILT_IN_CATEGORIES.map { it.copy(iconKey = "ic_WRONG") }

        val merged = canonicalXmbCategories(storedWithJunkIcon, XMBViewModel.FALLBACK_CATEGORIES)

        assertEquals(BUILT_IN_CATEGORIES.size, merged.size)
        merged.forEach { category ->
            val canonical = BUILT_IN_CATEGORIES.first { it.id == category.id }
            assertEquals("${category.id} lost its canonical icon", canonical.iconKey, category.iconKey)
        }
    }

    @Test
    fun `an unrecognised category really would keep the database's icon`() {
        val stranger = BUILT_IN_CATEGORIES.first().copy(id = "not_a_builtin", iconKey = "ic_WRONG")

        val merged = canonicalXmbCategories(listOf(stranger), XMBViewModel.FALLBACK_CATEGORIES)

        assertEquals("ic_WRONG", merged.first { it.id == "not_a_builtin" }.iconKey)
    }

    @Test
    fun `ids and positions are unique, so the bar cannot draw two of anything`() {
        val ids = BUILT_IN_CATEGORIES.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        val positions = BUILT_IN_CATEGORIES.map { it.position }
        assertEquals(positions.size, positions.distinct().size)
    }

    @Test
    fun `the defaults are an ORDER, not a set of reserved numbers`() {
        assertEquals(
            listOf(
                BuiltInCategory.RECENTLY_PLAYED,
                BuiltInCategory.SHELVES,
                BuiltInCategory.GAMES,
                "music",
                "videos",
                "photos",
                BuiltInCategory.LIBRARY,
                "network",
                BuiltInCategory.SETTINGS,
            ),
            BUILT_IN_CATEGORIES.sortedBy { it.position }.map { it.id },
        )
    }

    @Test
    fun `Settings is last, with room before it for a category the user makes`() {
        val settings = BUILT_IN_CATEGORIES.first { it.id == BuiltInCategory.SETTINGS }
        val others = BUILT_IN_CATEGORIES.filter { it.id != BuiltInCategory.SETTINGS }
        assertTrue("Settings is not last", others.all { it.position < settings.position })
        assertTrue(
            "no room between the last built-in and Settings for a custom category",
            settings.position > (others.maxOf { it.position } + 1),
        )
    }

    @Test
    fun `Last Played, then Shelves, then Game, with nothing between them`() {
        val recent = BUILT_IN_CATEGORIES.first { it.id == BuiltInCategory.RECENTLY_PLAYED }
        val shelves = BUILT_IN_CATEGORIES.first { it.id == BuiltInCategory.SHELVES }
        val games = BUILT_IN_CATEGORIES.first { it.id == BuiltInCategory.GAMES }
        assertEquals("Shelves must be the row immediately left of Game", games.position - 1, shelves.position)
        assertEquals("Last Played must be the row immediately left of Shelves", shelves.position - 1, recent.position)
    }

    @Test
    fun `no two built-ins share a position`() {
        val positions = BUILT_IN_CATEGORIES.map { it.position }
        assertEquals("two built-ins share a position", positions.size, positions.toSet().size)
    }
}
