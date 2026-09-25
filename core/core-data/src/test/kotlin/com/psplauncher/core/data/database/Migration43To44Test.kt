package com.psplauncher.core.data.database

import androidx.sqlite.execSQL
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * v44 — the Library section's two tables.
 *
 * The migration is purely additive, and the interesting claim is that word: an install that never
 * opens the section must come through with everything it already had. So this seeds a v43 database
 * with rows in an unrelated table and asserts they are still there afterwards, rather than only
 * asserting that the new tables exist.
 *
 * Only that claim is asserted here. `runMigrationsAndValidate` already compares the migrated
 * database against the exported v44 schema, so the shape of the tables, their foreign keys and
 * their indices are Room's to check: a test of those here cannot be made to fail on its own,
 * because the validator fires first and takes the whole class with it. The cascade is a real
 * behaviour worth pinning, so it is pinned where it can actually fail, against a live database,
 * in `BookCascadeTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration43To44Test {

    @get:Rule
    val helper = migrationTestHelper(DB)

    @Test
    fun `the two tables arrive empty and existing data is untouched`() {
        helper.createDatabase(43).use { db ->
            db.execSQL(
                "INSERT INTO photo_libraries (id, display_name, tree_uri, enabled, scan_recursively, " +
                    "photo_count, last_scanned_at, created_at, updated_at) " +
                    "VALUES ('p1', 'Holiday', 'content://tree/p1', 1, 1, 7, 100, 1, 2)"
            )
        }

        helper.runMigrationsAndValidate(44, MIGRATIONS).use { db ->
            assertEquals(0, db.count("SELECT COUNT(*) FROM book_libraries"))
            assertEquals(0, db.count("SELECT COUNT(*) FROM books"))

            // The neighbour table came through with its row and its values intact.
            assertEquals(1, db.count("SELECT COUNT(*) FROM photo_libraries"))
            db.singleRow("SELECT display_name, photo_count FROM photo_libraries WHERE id = 'p1'") {
                assertEquals("Holiday", it.getText(0))
                assertEquals(7L, it.getLong(1))
            }
        }
    }



    private companion object {
        const val DB = "migration-43-44-test.db"
        val MIGRATIONS = listOf(PFPDatabase.MIGRATION_43_44)
    }
}
