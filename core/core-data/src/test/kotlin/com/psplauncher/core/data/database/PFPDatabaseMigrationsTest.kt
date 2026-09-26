package com.psplauncher.core.data.database

import androidx.room.migration.Migration
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PFPDatabaseMigrationsTest {
    private val declared: List<Migration> =

        PFPDatabase::class.java.declaredFields
            .filter { Migration::class.java.isAssignableFrom(it.type) }
            .map { it.isAccessible = true; it.get(null) as Migration }

    private val registered = PFPDatabase.ALL_MIGRATIONS.toList()

    private fun Migration.pair() = startVersion to endVersion

    @Test
    fun `reflection actually found the migrations, so the rest of this test means something`() {
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
