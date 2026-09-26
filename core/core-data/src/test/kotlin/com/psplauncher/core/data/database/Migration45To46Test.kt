package com.psplauncher.core.data.database

import androidx.sqlite.execSQL
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration45To46Test {
    @get:Rule
    val helper = migrationTestHelper(DB)

    @Test
    fun `the achievement tables go and the library stays`() {
        helper.createDatabase(45).use { db ->
            db.execSQL(
                "INSERT INTO platforms (id, name, short_name, accent_color, is_pinned_to_bar, " +
                    "bar_position, rom_extensions) " +
                    "VALUES ('snes', 'SNES', 'SNES', 0, 1, 0, 'sfc,smc')"
            )
            db.execSQL(
                "INSERT INTO games (id, title, platform_id, is_disc_primary, is_favorite, " +
                    "favorite_sort_order, total_play_time_millis, is_manual_entry, created_at, " +
                    "content_type, is_missing) " +
                    "VALUES (1, 'Chrono Trigger', 'snes', 1, 1, 7, 4200, 0, 100, 'GAME', 0)"
            )

            db.execSQL(
                "INSERT INTO provider_game_links (game_id, provider, provider_game_id, source, resolved_at) " +
                    "VALUES (1, 'RETRO_ACHIEVEMENTS', '319', 'AUTO', 100)"
            )
            db.execSQL(
                "INSERT INTO steam_no_achievements (appid, checked_at) VALUES ('440', 100)"
            )
        }

        helper.runMigrationsAndValidate(46, MIGRATIONS).use { db ->

            assertEquals(1, db.count("SELECT COUNT(*) FROM games"))
            db.singleRow(
                "SELECT title, platform_id, is_favorite, total_play_time_millis FROM games WHERE id = 1"
            ) {
                assertEquals("Chrono Trigger", it.getText(0))
                assertEquals("snes", it.getText(1))
                assertEquals(1L, it.getLong(2))
                assertEquals(4200L, it.getLong(3))
            }
            assertEquals(1, db.count("SELECT COUNT(*) FROM platforms"))

            val tables = db.rows("SELECT name FROM sqlite_master WHERE type = 'table'") { it.getText(0) }
            for (gone in DROPPED) {
                assertFalse(gone in tables, "$gone should have been dropped")
            }
        }
    }

    private companion object {
        const val DB = "migration-45-46-test.db"
        val MIGRATIONS = listOf(PFPDatabase.MIGRATION_45_46)
        val DROPPED = listOf(
            "account_achievements",
            "account_achievement_sets",
            "achievement_match_notes",
            "provider_game_links",
            "steam_owned_games",
            "steam_no_achievements",
        )
    }
}
