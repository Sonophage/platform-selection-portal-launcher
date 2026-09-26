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
class Migration36To37Test {
    @get:Rule
    val helper = migrationTestHelper(DB)

    @Test
    fun `v37 adds is_missing and last_seen_at without touching existing rows`() {
        helper.createDatabase(36).use { db ->
            db.execSQL(
                "INSERT INTO games (title, platform_id, is_favorite, favorite_sort_order, " +
                    "total_play_time_millis, is_manual_entry, created_at, content_type) " +
                    "VALUES ('Chrono Trigger', 'snes', 1, 0, 0, 0, 0, 'GAME')",
            )
        }

        helper.runMigrationsAndValidate(37, listOf(PFPDatabase.MIGRATION_36_37)).use { db ->
            db.singleRow("SELECT title, is_favorite, is_missing, last_seen_at FROM games") {
                assertEquals("Chrono Trigger", it.getText(0))
                assertEquals(1, it.getLong(1).toInt())

                assertEquals(0, it.getLong(2).toInt())
                assertTrue(it.isNull(3))
            }
        }
    }

    private companion object {
        const val DB = "migration-37-test"
    }
}
