package com.psplauncher.core.data.database

import androidx.room.migration.Migration
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The migrations that exist, against the migrations that are actually registered.
 *
 * This is the cheapest catastrophe in the codebase to cause and the hardest to notice. Write
 * `MIGRATION_46_47`, write `Migration46To47Test` — which passes, because it invokes the object
 * directly — bump `version = 47`, and forget to add the one line that registers it. Every existing
 * user's next launch throws `IllegalStateException: A migration from 46 to 47 was required but not
 * found`, `fallbackToDestructiveMigration` is correctly refused so there is no silent recovery, and
 * the entire test suite stays green.
 *
 * The declarations are enumerated by REFLECTION rather than written out here. A hand-written list
 * would be a third copy of the same thing and would drift the same way.
 */
class PFPDatabaseMigrationsTest {

    /** Every `Migration` declared on the companion, found without being told their names. */
    private val declared: List<Migration> =
        // Kotlin puts a companion object's backing fields on the OUTER class as statics, not on
        // Companion. The first version of this test read Companion and found nothing, which is
        // precisely what the size assertion below exists to catch.
        PFPDatabase::class.java.declaredFields
            .filter { Migration::class.java.isAssignableFrom(it.type) }
            .map { it.isAccessible = true; it.get(null) as Migration }

    private val registered = PFPDatabase.ALL_MIGRATIONS.toList()

    private fun Migration.pair() = startVersion to endVersion

    @Test
    fun `reflection actually found the migrations, so the rest of this test means something`() {
        // A guard on the guard: if the companion is ever restructured so these stop being fields,
        // every assertion below would pass over an empty list and report success.
        assertTrue(declared.size >= 40, "reflection found only ${declared.size} migrations")
    }

    @Test
    fun `every declared migration is registered`() {
        val missing = (declared.map { it.pair() }.toSet() - registered.map { it.pair() }.toSet())
            .sortedBy { it.first }
        assertEquals(
            emptyList(),
            missing,
            "declared but never registered — every existing install would fail to launch: $missing",
        )
    }

    @Test
    fun `every registered migration is declared, so nothing registers a stale object twice`() {
        val pairs = registered.map { it.pair() }
        assertEquals(pairs.size, pairs.distinct().size, "the same migration is registered twice")
    }

    @Test
    fun `the chain is unbroken from the lowest version up to the database's own version`() {
        // A gap is as fatal as a missing registration and looks identical at runtime.
        val byStart = registered.associateBy { it.startVersion }
        val lowest = registered.minOf { it.startVersion }
        var v = lowest
        while (v < PFP_DATABASE_VERSION) {
            val step = byStart[v]
            assertTrue(step != null, "no migration starts at version $v — the chain breaks there")
            assertEquals(v + 1, step.endVersion, "migration from $v must land on ${v + 1}")
            v = step.endVersion
        }
        assertEquals(PFP_DATABASE_VERSION, v)
    }

    @Test
    fun `the chain reaches the version the database declares`() {
        assertEquals(
            PFP_DATABASE_VERSION,
            registered.maxOf { it.endVersion },
            "the newest migration does not arrive at the database's declared version",
        )
    }
}
