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
 * v47 — an index on `disc_set_key`.
 *
 * An index is the one kind of migration whose failure mode is silence: get it wrong and every
 * query still returns the right rows, just slowly, and no test that checks results can tell.
 * So this checks the two things that are not about results.
 *
 * First, that the index exists afterwards and names the column it claims to. Second, and the
 * reason this test is worth writing at all, that the multi-disc rows a real library holds survive
 * a migration that touches the table they live in. `CREATE INDEX` should not be able to lose
 * data, but "should not be able to" is how the last several of these were described too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration46To47Test {

    @get:Rule
    val helper = migrationTestHelper(DB)

    @Test
    fun `the index arrives and the disc sets survive`() {
        helper.createDatabase(46).use { db ->
            db.execSQL(
                "INSERT INTO platforms (id, name, short_name, accent_color, is_pinned_to_bar, " +
                    "bar_position, rom_extensions) " +
                    "VALUES ('psx', 'PlayStation', 'PSX', 0, 1, 0, 'cue,bin')"
            )
            // A real disc set plus a standalone game: the index is for the correlated subquery
            // that tells these apart, so both shapes have to be present for the check to mean
            // anything about a real library.
            db.execSQL(
                "INSERT INTO games (id, title, platform_id, disc_set_key, disc_number, " +
                    "is_disc_primary, is_favorite, favorite_sort_order, total_play_time_millis, " +
                    "is_manual_entry, created_at, content_type, is_missing) " +
                    "VALUES (1, 'Final Fantasy VII (Disc 1)', 'psx', 'ff7', 1, 1, 0, 0, 0, 0, 100, 'GAME', 0)"
            )
            db.execSQL(
                "INSERT INTO games (id, title, platform_id, disc_set_key, disc_number, " +
                    "is_disc_primary, is_favorite, favorite_sort_order, total_play_time_millis, " +
                    "is_manual_entry, created_at, content_type, is_missing) " +
                    "VALUES (2, 'Final Fantasy VII (Disc 2)', 'psx', 'ff7', 2, 0, 0, 0, 0, 0, 100, 'GAME', 0)"
            )
            db.execSQL(
                "INSERT INTO games (id, title, platform_id, is_disc_primary, is_favorite, " +
                    "favorite_sort_order, total_play_time_millis, is_manual_entry, created_at, " +
                    "content_type, is_missing) " +
                    "VALUES (3, 'Chrono Cross', 'psx', 1, 0, 0, 0, 0, 100, 'GAME', 0)"
            )
        }

        helper.runMigrationsAndValidate(47, MIGRATIONS).use { db ->
            // Every row is still there, and the set is still a set.
            assertEquals(3, db.count("SELECT COUNT(*) FROM games"))
            assertEquals(2, db.count("SELECT COUNT(*) FROM games WHERE disc_set_key = 'ff7'"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM games WHERE disc_set_key = 'ff7' AND is_disc_primary = 1"))
            db.singleRow("SELECT title, disc_number FROM games WHERE id = 2") {
                assertEquals("Final Fantasy VII (Disc 2)", it.getText(0))
                assertEquals(2L, it.getLong(1))
            }

            // The index itself, by name and by the column it covers. Room's schema validation in
            // runMigrationsAndValidate would catch a missing index too, but it would report a
            // schema mismatch rather than saying which index and why it exists.
            val indexes = db.rows(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'games'"
            ) { it.getText(0) }
            assertTrue(
                "index_games_disc_set_key" in indexes,
                "index_games_disc_set_key missing; found $indexes",
            )
            val covered = db.rows("PRAGMA index_info('index_games_disc_set_key')") { it.getText(2) }
            assertEquals(listOf("disc_set_key"), covered)
        }
    }

    private companion object {
        const val DB = "migration-46-47-test.db"
        val MIGRATIONS = listOf(PFPDatabase.MIGRATION_46_47)
    }
}
