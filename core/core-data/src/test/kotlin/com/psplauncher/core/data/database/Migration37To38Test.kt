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
class Migration37To38Test {
    @get:Rule
    val helper = migrationTestHelper(DB)

    @Test
    fun `v38 adds the disc set columns without touching existing rows`() {
        helper.createDatabase(37).use { db ->
            db.execSQL(
                "INSERT INTO games (title, platform_id, is_favorite, favorite_sort_order, " +
                    "total_play_time_millis, is_manual_entry, created_at, content_type, is_missing) " +
                    "VALUES ('Chrono Trigger', 'snes', 1, 0, 0, 0, 0, 'GAME', 0)",
            )
        }

        helper.runMigrationsAndValidate(38, listOf(PFPDatabase.MIGRATION_37_38)).use { db ->
            db.singleRow("SELECT title, disc_set_key, disc_number, is_disc_primary FROM games") {
                assertEquals("Chrono Trigger", it.getText(0))

                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
                assertEquals(0, it.getLong(3).toInt())
            }
        }
    }

    private companion object {
        const val DB = "migration-38-test"
    }
}
