package com.psplauncher.core.data.database

import androidx.sqlite.execSQL
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

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
