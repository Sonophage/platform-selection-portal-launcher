package com.psplauncher.core.data.repository

import com.psplauncher.core.data.database.dao.CategoryDao
import com.psplauncher.core.data.database.entity.CategoryEntity
import com.psplauncher.core.domain.model.BUILT_IN_CATEGORIES
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
 * Correcting the Network column's name on a database seeded while it was called "Online".
 *
 * `BUILT_IN_CATEGORIES` has said "Network" for a while, but `reconcileBuiltInCategories` adds
 * built-ins with INSERT OR IGNORE and deliberately never writes a name, because a name is
 * user-editable. So an established install keeps whatever it was seeded with, and the correction
 * has to be a targeted one-shot.
 *
 * The reason this needs a test rather than a glance is the blast radius of getting it slightly
 * wrong. The obvious implementation -- sync every built-in's name from the definition -- would
 * also rewrite the Game column, which on the owner's device he has renamed to "Emulation". A fix
 * for a cosmetic stale label is not allowed to silently undo a rename the user made on purpose,
 * and "it only touched the row it was asked to" is exactly the property that is invisible until
 * someone loses their name.
 */
class StaleNetworkNameTest {

    private class FakeCategories(seed: List<CategoryEntity>) {
        val rows = seed.associateBy { it.id }.toMutableMap()
        val dao: CategoryDao = mockk(relaxed = true)

        init {
            coEvery { dao.getById(any()) } answers { rows[firstArg<String>()] }
            coEvery { dao.getAll() } answers { rows.values.sortedBy { it.position } }
            coEvery { dao.update(any()) } answers {
                val row = firstArg<CategoryEntity>()
                rows[row.id] = row
                Unit
            }
        }

        fun nameOf(id: String): String? = rows[id]?.name
    }

    private fun category(id: String, name: String) = CategoryEntity(
        id = id, name = name, iconKey = "ic_$id", type = CategoryType.BUILT_IN.name, position = 0,
    )

    /** The name the bar's one definition currently gives the Network column. */
    private val liveNetworkName =
        BUILT_IN_CATEGORIES.first { it.id == CategoryRepositoryImpl.NETWORK_CATEGORY_ID }.name

    @Test
    fun `a column still called Online is renamed to whatever the definition now says`() = runTest {
        val fake = FakeCategories(
            listOf(category(CategoryRepositoryImpl.NETWORK_CATEGORY_ID, "Online"))
        )

        assertTrue(CategoryRepositoryImpl(fake.dao).renameStaleOnlineColumn())

        assertEquals(liveNetworkName, fake.nameOf(CategoryRepositoryImpl.NETWORK_CATEGORY_ID))
    }

    @Test
    fun `a Game column the user renamed is left alone`() = runTest {
        val fake = FakeCategories(
            listOf(
                category(CategoryRepositoryImpl.NETWORK_CATEGORY_ID, "Online"),
                category(BuiltInCategory.GAMES, "Emulation"),
                category(BuiltInCategory.LIBRARY, "Library"),
            )
        )

        CategoryRepositoryImpl(fake.dao).renameStaleOnlineColumn()

        // The one row it was asked about changed; nothing else did.
        assertEquals(liveNetworkName, fake.nameOf(CategoryRepositoryImpl.NETWORK_CATEGORY_ID))
        assertEquals("Emulation", fake.nameOf(BuiltInCategory.GAMES))
        assertEquals("Library", fake.nameOf(BuiltInCategory.LIBRARY))
    }

    @Test
    fun `a Network column the user has already named something else is not touched`() = runTest {
        val fake = FakeCategories(
            listOf(category(CategoryRepositoryImpl.NETWORK_CATEGORY_ID, "Internet"))
        )

        assertFalse(CategoryRepositoryImpl(fake.dao).renameStaleOnlineColumn())

        assertEquals("Internet", fake.nameOf(CategoryRepositoryImpl.NETWORK_CATEGORY_ID))
    }

    @Test
    fun `a column already reading the live name is a no-op`() = runTest {
        val fake = FakeCategories(
            listOf(category(CategoryRepositoryImpl.NETWORK_CATEGORY_ID, liveNetworkName))
        )

        assertFalse(CategoryRepositoryImpl(fake.dao).renameStaleOnlineColumn())
    }

    @Test
    fun `a database with no Network row at all reports nothing to do`() = runTest {
        val fake = FakeCategories(listOf(category(BuiltInCategory.GAMES, "Game")))

        assertFalse(CategoryRepositoryImpl(fake.dao).renameStaleOnlineColumn())
    }
}
