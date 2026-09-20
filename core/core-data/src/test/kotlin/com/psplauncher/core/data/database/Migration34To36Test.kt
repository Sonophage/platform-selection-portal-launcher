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
 * The two migrations in the 32..46 range that had exported schemas and no test of their own:
 * v35's `provider_game_links.ownership` and v36's `games.launch_token`.
 *
 * Both are one-line `ALTER TABLE ... ADD COLUMN`, which is exactly why they were skipped and
 * exactly why they are worth pinning. A nullable added column is the easiest migration to get
 * wrong in a way that only shows up on somebody's real database: name it differently from the
 * entity and Room's post-migration validation fails on launch; give it a NOT NULL without a
 * default and the ALTER itself throws on any table that already has rows.
 *
 * `runMigrationsAndValidate` compares the result against the exported schema, so these assert
 * against the real thing rather than a transcription of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration34To36Test {

    @get:Rule
    val helper = migrationTestHelper(DB)

    private companion object {
        // Its own database file, so these never share state with another migration test.
        const val DB = "migration-34-to-36-test"
    }

    @Test
    fun `v35 adds ownership to provider links and leaves existing links intact`() {
        helper.createDatabase(34).use { db ->
            db.execSQL(
                "INSERT INTO provider_game_links (game_id, provider, provider_game_id, source, resolved_at) " +
                    "VALUES (1, 'STEAMGRIDDB', '4242', 'SCRAPE', 1700000000000)",
            )
        }

        helper.runMigrationsAndValidate(35, listOf(PFPDatabase.MIGRATION_34_35)).use { db ->
            db.singleRow("SELECT provider, provider_game_id, ownership FROM provider_game_links") {
                // The existing link survives untouched...
                assertEquals("STEAMGRIDDB", it.getText(0))
                assertEquals("4242", it.getText(1))
                // ...and the new column arrives NULL: a link that predates ownership tracking
                // knows nothing about ownership, which is different from knowing it is unowned.
                assertTrue(it.isNull(2), "ownership must default to NULL, not a guessed value")
            }
        }
    }

    @Test
    fun `v36 adds launch_token to games and leaves existing games intact`() {
        helper.createDatabase(35).use { db ->
            db.execSQL(
                "INSERT INTO games (title, platform_id, is_favorite, favorite_sort_order, " +
                    "total_play_time_millis, is_manual_entry, created_at, content_type) " +
                    "VALUES ('Persona 4 Golden', 'psvita', 1, 0, 7200000, 0, 0, 'GAME')",
            )
        }

        helper.runMigrationsAndValidate(36, listOf(PFPDatabase.MIGRATION_35_36)).use { db ->
            db.singleRow("SELECT title, is_favorite, total_play_time_millis, launch_token FROM games") {
                assertEquals("Persona 4 Golden", it.getText(0))
                // Curation and playtime are the two things a careless ALTER can cost you.
                assertEquals(1, it.getLong(1).toInt())
                assertEquals(7_200_000L, it.getLong(2))
                // A Vita game scanned before ID-launch existed has no token until it is rescanned.
                assertTrue(it.isNull(3), "launch_token must default to NULL")
            }
        }
    }

    @Test
    fun `the pair runs end to end, which is what an established install actually does`() {
        // Nobody upgrades one version at a time. 34 to 36 in one go is the real path.
        helper.createDatabase(34).use { db ->
            db.execSQL(
                "INSERT INTO games (title, platform_id, is_favorite, favorite_sort_order, " +
                    "total_play_time_millis, is_manual_entry, created_at, content_type) " +
                    "VALUES ('Crash Bandicoot', 'psx', 0, 0, 0, 0, 0, 'GAME')",
            )
        }

        helper.runMigrationsAndValidate(
            36,
            listOf(PFPDatabase.MIGRATION_34_35, PFPDatabase.MIGRATION_35_36),
        ).use { db ->
            assertEquals(1, db.count("SELECT COUNT(*) FROM games"))
            db.singleRow("SELECT title, launch_token FROM games") {
                assertEquals("Crash Bandicoot", it.getText(0))
                assertTrue(it.isNull(1))
            }
        }
    }
}
