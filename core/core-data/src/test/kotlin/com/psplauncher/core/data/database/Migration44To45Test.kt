package com.psplauncher.core.data.database

import androidx.sqlite.execSQL
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v45 — series and cover art on `books`.
 *
 * The claim worth testing is that an existing library survives the column add. A user who scanned
 * books on v44 has rows this migration must not disturb, and `ALTER TABLE ADD COLUMN` on SQLite
 * rewrites nothing, so the rows should come through byte for byte with the three new columns
 * reading null until a rescan fills them.
 *
 * As in [Migration43To44Test], the shape of the table is deliberately NOT asserted here:
 * `runMigrationsAndValidate` compares the result against the exported v45 schema and fires before
 * any assertion in this class, so a column-shape test here could never be made to fail on its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration44To45Test {

    @get:Rule
    val helper = migrationTestHelper(DB)

    @Test
    fun `an existing book survives with its values and reads null for the new columns`() {
        helper.createDatabase(44).use { db ->
            db.execSQL(
                "INSERT INTO book_libraries (id, display_name, tree_uri, enabled, scan_recursively, " +
                    "book_count, last_scanned_at, created_at, updated_at) " +
                    "VALUES ('l1', 'Shelf', 'content://tree/l1', 1, 1, 1, 100, 1, 2)"
            )
            db.execSQL(
                "INSERT INTO books (id, library_id, uri, display_name, title, author, " +
                    "last_modified, size_bytes, mime_type, relative_path, date_added) " +
                    "VALUES ('b1', 'l1', 'content://doc/b1', 'Dune.epub', 'Dune', 'Frank Herbert', " +
                    "5000, 900, 'application/epub+zip', 'scifi', 42)"
            )
        }

        helper.runMigrationsAndValidate(45, MIGRATIONS).use { db ->
            assertEquals(1, db.count("SELECT COUNT(*) FROM books"))
            db.singleRow(
                "SELECT title, author, relative_path, size_bytes, series, series_index, cover_uri " +
                    "FROM books WHERE id = 'b1'"
            ) {
                // What was already there is still there, unchanged.
                assertEquals("Dune", it.getText(0))
                assertEquals("Frank Herbert", it.getText(1))
                assertEquals("scifi", it.getText(2))
                assertEquals(900L, it.getLong(3))
                // The three new columns exist and are empty until a rescan reads the EPUB.
                assertTrue(it.isNull(4), "series should be null before a rescan")
                assertTrue(it.isNull(5), "series_index should be null before a rescan")
                assertTrue(it.isNull(6), "cover_uri should be null before a rescan")
            }
        }
    }

    private companion object {
        const val DB = "migration-44-45-test.db"
        val MIGRATIONS = listOf(PFPDatabase.MIGRATION_44_45)
    }
}
