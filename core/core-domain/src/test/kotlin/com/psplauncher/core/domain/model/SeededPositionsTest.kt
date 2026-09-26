package com.psplauncher.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeededPositionsTest {
    private fun category(id: String, position: Int) =
        Category(id = id, name = id, iconKey = id, type = CategoryType.BUILT_IN, position = position)

    private val defaults = listOf(
        category("recently_played", 0),
        category("games", 1),
        category("library", 5),
        category("settings", 11),
    )

    @Test
    fun `a fresh database gets the defaults exactly as written`() {
        assertEquals(defaults, seededPositions(defaults, existingIds = emptySet(), highestExisting = null))
    }

    @Test
    fun `a row the database already has is not moved`() {
        val seeded = seededPositions(
            defaults,
            existingIds = setOf("recently_played", "games", "settings"),
            highestExisting = 20,
        )
        assertEquals(0, seeded.first { it.id == "recently_played" }.position)
        assertEquals(1, seeded.first { it.id == "games" }.position)
        assertEquals(11, seeded.first { it.id == "settings" }.position)
    }

    @Test
    fun `a row it has never seen is appended past everything, not dropped on its default`() {
        val seeded = seededPositions(
            defaults,
            existingIds = setOf("recently_played", "games", "settings"),
            highestExisting = 20,
        )
        assertEquals(21, seeded.first { it.id == "library" }.position)
    }

    @Test
    fun `several new rows are appended in order and do not collide with each other`() {
        val seeded = seededPositions(defaults, existingIds = setOf("games"), highestExisting = 7)
        val positions = seeded.filter { it.id != "games" }.map { it.position }
        assertEquals(listOf(8, 9, 10), positions)
        assertEquals("appended rows collided", positions.size, positions.toSet().size)
    }

    @Test
    fun `nothing appended can land on a position the database already holds`() {
        val existingTop = 11
        val seeded = seededPositions(defaults, existingIds = setOf("settings"), highestExisting = existingTop)
        val appended = seeded.filter { it.id != "settings" }
        assertTrue(
            "an appended row landed at or below the highest existing position",
            appended.all { it.position > existingTop },
        )
    }
}
