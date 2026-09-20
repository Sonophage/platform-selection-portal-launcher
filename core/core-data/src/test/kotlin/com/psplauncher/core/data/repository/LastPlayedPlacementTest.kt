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

/**
 * Moving Last Played to its home on a database that already has it somewhere else.
 *
 * Last Played shipped appended to the end of the bar and was given its place left of Game hours
 * later. Reconciliation deliberately never touches a category's position -- position is
 * user-editable and rewriting it every launch would undo a reorder -- so a one-shot does it
 * instead, and the caller guards it with a DataStore flag.
 *
 * The thing worth testing is that it SHIFTS rather than assigning numbers: a user who has already
 * rearranged their bar must keep that arrangement, with Last Played inserted into it, not a
 * canonical order imposed on top of it.
 */
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

        /** The bar as the user sees it: ids in position order. */
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
        // Every row still holds a position of its own; a shift that produced a tie would leave
        // the bar's order down to whatever the query happened to return.
        val positions = fake.rows.values.map { it.position }
        assertEquals(positions.size, positions.toSet().size, "two rows ended up sharing a position")
    }

    @Test
    fun `a bar the user has rearranged keeps its order, with Last Played slotted into it`() = runTest {
        // Game dragged to the front, Settings pushed to the back. The fix must respect that.
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
        // The control, and the reason this is safe to call before the flag is written: a fresh
        // install is seeded with Last Played already in place, and must not be shuffled.
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
        // Game is protected from deletion, but a restored archive can carry anything, and a
        // position computed from a missing row would be a silent guess.
        val fake = FakeCategories(listOf(category(BuiltInCategory.RECENTLY_PLAYED, 10)))

        assertFalse(CategoryRepositoryImpl(fake.dao).placeLastPlayedBeforeGames())

        assertEquals(10, fake.rows.getValue(BuiltInCategory.RECENTLY_PLAYED).position)
    }
}
