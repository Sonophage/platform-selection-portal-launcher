package com.psplauncher.core.data.database

import androidx.sqlite.execSQL
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

            assertEquals(3, db.count("SELECT COUNT(*) FROM games"))
            assertEquals(2, db.count("SELECT COUNT(*) FROM games WHERE disc_set_key = 'ff7'"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM games WHERE disc_set_key = 'ff7' AND is_disc_primary = 1"))
            db.singleRow("SELECT title, disc_number FROM games WHERE id = 2") {
                assertEquals("Final Fantasy VII (Disc 2)", it.getText(0))
                assertEquals(2L, it.getLong(1))
            }

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
