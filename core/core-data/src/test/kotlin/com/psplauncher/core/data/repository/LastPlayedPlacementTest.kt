package com.psplauncher.core.data.repository

import com.psplauncher.core.data.database.dao.CategoryDao
import com.psplauncher.core.data.database.entity.CategoryEntity
import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.CategoryType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LastPlayedPlacementTest {
    private class FakeCategories(seed: List<CategoryEntity>) {
        val rows = seed.associateBy { it.id }.toMutableMap()
        val dao: CategoryDao = mockk(relaxed = true)

        init {
            coEvery { dao.getById(any()) } answers { rows[firstArg<String>()] }
            coEvery { dao.getAll() } answers { rows.values.sortedBy { it.position } }
            coEvery { dao.updatePosition(any(), any()) } answers {
                val id = firstArg<String>()
                rows[id] = rows.getValue(id).copy(position = secondArg())
                Unit
            }
        }

        fun order(): List<String> = rows.values.sortedBy { it.position }.map { it.id }
    }

    private fun category(id: String, position: Int) = CategoryEntity(
        id = id, name = id, iconKey = "ic_$id", type = CategoryType.BUILT_IN.name, position = position,
    )

    @Test
    fun `Last Played moves from the end of the bar to immediately left of Game`() = runTest {
        val fake = FakeCategories(
            listOf(
                category(BuiltInCategory.SETTINGS, 0),
                category("videos", 3),
                category(BuiltInCategory.GAMES, 4),
                category("network", 5),
                category(BuiltInCategory.RECENTLY_PLAYED, 10),
            )
        )

        assertTrue(CategoryRepositoryImpl(fake.dao).placeLastPlayedBeforeGames())

        assertEquals(
            listOf(
                BuiltInCategory.SETTINGS,
                "videos",
                BuiltInCategory.RECENTLY_PLAYED,
                BuiltInCategory.GAMES,
                "network",
            ),
            fake.order(),
        )

        val positions = fake.rows.values.map { it.position }
        assertEquals(positions.size, positions.toSet().size, "two rows ended up sharing a position")
    }

    @Test
    fun `a bar the user has rearranged keeps its order, with Last Played slotted into it`() = runTest {
        val fake = FakeCategories(
            listOf(
                category(BuiltInCategory.GAMES, 0),
                category("videos", 1),
                category(BuiltInCategory.SETTINGS, 2),
                category(BuiltInCategory.RECENTLY_PLAYED, 10),
            )
        )

        assertTrue(CategoryRepositoryImpl(fake.dao).placeLastPlayedBeforeGames())

        assertEquals(
            listOf(BuiltInCategory.RECENTLY_PLAYED, BuiltInCategory.GAMES, "videos", BuiltInCategory.SETTINGS),
            fake.order(),
        )
    }

    @Test
    fun `it does nothing when Last Played is already left of Game`() = runTest {
        val fake = FakeCategories(
            listOf(
                category(BuiltInCategory.RECENTLY_PLAYED, 4),
                category(BuiltInCategory.GAMES, 5),
                category("network", 6),
            )
        )
        val before = fake.rows.mapValues { it.value.position }

        assertFalse(CategoryRepositoryImpl(fake.dao).placeLastPlayedBeforeGames())

        assertEquals(before, fake.rows.mapValues { it.value.position })
    }

    @Test
    fun `it does nothing when there is no Game column to sit beside`() = runTest {
        val fake = FakeCategories(listOf(category(BuiltInCategory.RECENTLY_PLAYED, 10)))

        assertFalse(CategoryRepositoryImpl(fake.dao).placeLastPlayedBeforeGames())

        assertEquals(10, fake.rows.getValue(BuiltInCategory.RECENTLY_PLAYED).position)
    }
}
