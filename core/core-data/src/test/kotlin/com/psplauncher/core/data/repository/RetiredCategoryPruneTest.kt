package com.psplauncher.core.data.repository

import com.psplauncher.core.data.database.dao.CategoryDao
import com.psplauncher.core.data.database.entity.CategoryEntity
import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.CategoryType
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Retiring a built-in category is a change to CODE that old DATA still contradicts. Two paths put
 * a retired row back in front of the user:
 *
 *  - an install first seeded by a build that still had the column, and
 *  - a restore, because `BackupManager` upserts whatever categories the archive carried — and the
 *    archive is usually written by the OLDER app you are migrating away from.
 *
 * The row that comes back is worse than a stale one: the icon it names is gone from the catalog,
 * and the shell has no branch for it, so it draws blank and does nothing when selected.
 */
class RetiredCategoryPruneTest {

    /** Minimal in-memory stand-in for the three calls the sweep and the reconcile make. */
    private class FakeCategories(seed: Map<String, CategoryEntity> = emptyMap()) {
        val rows = seed.toMutableMap()
        val clearedItemsFor = mutableListOf<String>()
        val dao: CategoryDao = mockk(relaxed = true)

        init {
            coEvery { dao.getById(any()) } answers { rows[firstArg<String>()] }
            coEvery { dao.deleteById(any()) } answers { rows.remove(firstArg<String>()); Unit }
            coEvery { dao.clearCategory(any()) } answers { clearedItemsFor += firstArg<String>(); Unit }
            val inserted = slot<List<CategoryEntity>>()
            coEvery { dao.insertAll(capture(inserted)) } answers {
                // INSERT OR IGNORE: never overwrite a row the user already has.
                inserted.captured.forEach { rows.putIfAbsent(it.id, it) }
                Unit
            }
        }
    }

    private fun category(id: String) = CategoryEntity(
        id = id, name = id, iconKey = "ic_$id", type = CategoryType.BUILT_IN.name, position = 0,
    )

    @Test
    fun `reconcile removes a retired column an old install or restored backup left behind`() = runTest {
        val retired = BuiltInCategory.RETIRED_IDS.first()
        val fake = FakeCategories(mapOf(retired to category(retired), BuiltInCategory.GAMES to category(BuiltInCategory.GAMES)))

        CategoryRepositoryImpl(fake.dao).reconcileBuiltInCategories()

        assertNull(fake.rows[retired], "$retired should have been swept")
        assertTrue(retired in fake.clearedItemsFor, "its category items should go with it")
        assertTrue(BuiltInCategory.GAMES in fake.rows, "a live built-in must survive the sweep")
    }

    @Test
    fun `a retired id is never also seeded as a live built-in`() = runTest {
        // The pair that must agree. If an id is left in both lists the reconcile deletes it and
        // immediately seeds it again on every cold start — the column flickers back and the sweep
        // above looks broken for reasons nothing else explains.
        val fake = FakeCategories()

        CategoryRepositoryImpl(fake.dao).reconcileBuiltInCategories()

        val seeded = fake.rows.keys
        val both = seeded intersect BuiltInCategory.RETIRED_IDS
        assertEquals(emptySet(), both, "retired ids are still being seeded as built-ins: $both")
        assertFalse(
            BuiltInCategory.RETIRED_IDS.any { it in CategoryRepositoryImpl.PROTECTED_BUILTINS },
            "a retired id must not remain protected from deletion",
        )
    }
}
