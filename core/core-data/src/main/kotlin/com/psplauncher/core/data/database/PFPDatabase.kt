package com.psplauncher.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.psplauncher.core.data.model.StorefrontIdentity
import com.psplauncher.core.data.database.dao.AppOverrideDao
import com.psplauncher.core.data.database.dao.ArtworkImportReportDao
import com.psplauncher.core.data.database.dao.ArtworkRecordDao
import com.psplauncher.core.data.database.dao.BackupDao
import com.psplauncher.core.data.database.dao.CategoryDao
import com.psplauncher.core.data.database.dao.CollectionDao
import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.core.data.database.dao.LaunchOutcomeDao
import com.psplauncher.core.data.database.dao.LibrarySourceDao
import com.psplauncher.core.data.database.dao.MemoryCardDao
import com.psplauncher.core.data.database.dao.MusicFolderDao
import com.psplauncher.core.data.database.dao.MusicTrackDao
import com.psplauncher.core.data.database.dao.PlaylistDao
import com.psplauncher.core.data.database.dao.PlaySessionDao
import com.psplauncher.core.data.database.dao.PlatformDao
import com.psplauncher.core.data.database.dao.ThemeDao
import com.psplauncher.core.data.database.dao.HiddenPlacementDao
import com.psplauncher.core.data.database.dao.BookDao
import com.psplauncher.core.data.database.dao.BookLibraryDao
import com.psplauncher.core.data.database.dao.PhotoDao
import com.psplauncher.core.data.database.dao.PhotoLibraryDao
import com.psplauncher.core.data.database.dao.ScanTombstoneDao
import com.psplauncher.core.data.database.dao.SsMediaCacheDao
import com.psplauncher.core.data.database.dao.VideoDao
import com.psplauncher.core.data.database.dao.VideoLibraryDao
import com.psplauncher.core.data.database.dao.VideoPlaylistDao
import com.psplauncher.core.data.database.entity.AppOverrideEntity
import com.psplauncher.core.data.database.entity.ArtworkImportReportEntity
import com.psplauncher.core.data.database.entity.ArtworkRecordEntity
import com.psplauncher.core.data.database.entity.CategoryEntity
import com.psplauncher.core.data.database.entity.CategoryItemEntity
import com.psplauncher.core.data.database.entity.CollectionEntity
import com.psplauncher.core.data.database.entity.CollectionGameEntity
import com.psplauncher.core.data.database.entity.GameEntity
import com.psplauncher.core.data.database.entity.LaunchOutcomeEntity
import com.psplauncher.core.data.database.entity.LibrarySourceEntity
import com.psplauncher.core.data.database.entity.MemoryCardEntity
import com.psplauncher.core.data.database.entity.MusicFolderEntity
import com.psplauncher.core.data.database.entity.MusicTrackEntity
import com.psplauncher.core.data.database.entity.PlaylistEntity
import com.psplauncher.core.data.database.entity.PlaylistTrackEntity
import com.psplauncher.core.data.database.entity.PlaySessionEntity
import com.psplauncher.core.data.database.entity.PlatformEntity
import com.psplauncher.core.data.database.entity.ThemeEntity
import com.psplauncher.core.data.database.entity.HiddenPlacementEntity
import com.psplauncher.core.data.database.entity.BookEntity
import com.psplauncher.core.data.database.entity.BookLibraryEntity
import com.psplauncher.core.data.database.entity.PhotoEntity
import com.psplauncher.core.data.database.entity.PhotoLibraryEntity
import com.psplauncher.core.data.database.entity.ScanTombstoneEntity
import com.psplauncher.core.data.database.entity.SsMediaCacheEntity
import com.psplauncher.core.data.database.entity.VideoEntity
import com.psplauncher.core.data.database.entity.VideoLibraryEntity
import com.psplauncher.core.data.database.entity.VideoPlaylistEntity
import com.psplauncher.core.data.database.entity.VideoPlaylistItemEntity

const val PFP_DATABASE_VERSION = 53

@Database(
    entities = [
        GameEntity::class,
        LaunchOutcomeEntity::class,
        PlatformEntity::class,
        CategoryEntity::class,
        CategoryItemEntity::class,
        PlaySessionEntity::class,
        LibrarySourceEntity::class,
        ThemeEntity::class,
        MemoryCardEntity::class,
        AppOverrideEntity::class,
        CollectionEntity::class,
        CollectionGameEntity::class,
        MusicFolderEntity::class,
        MusicTrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        VideoLibraryEntity::class,
        VideoEntity::class,
        VideoPlaylistEntity::class,
        VideoPlaylistItemEntity::class,
        HiddenPlacementEntity::class,
        PhotoLibraryEntity::class,
        PhotoEntity::class,
        ScanTombstoneEntity::class,
        ArtworkRecordEntity::class,
        ArtworkImportReportEntity::class,
        SsMediaCacheEntity::class,
        BookLibraryEntity::class,
        BookEntity::class,
    ],
    version = PFP_DATABASE_VERSION,
    exportSchema = true,
)
@TypeConverters(PFPTypeConverters::class)
abstract class PFPDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun launchOutcomeDao(): LaunchOutcomeDao
    abstract fun platformDao(): PlatformDao
    abstract fun categoryDao(): CategoryDao
    abstract fun playSessionDao(): PlaySessionDao
    abstract fun librarySourceDao(): LibrarySourceDao
    abstract fun themeDao(): ThemeDao
    abstract fun memoryCardDao(): MemoryCardDao
    abstract fun appOverrideDao(): AppOverrideDao
    abstract fun collectionDao(): CollectionDao
    abstract fun musicFolderDao(): MusicFolderDao
    abstract fun musicTrackDao(): MusicTrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun videoLibraryDao(): VideoLibraryDao
    abstract fun videoDao(): VideoDao
    abstract fun videoPlaylistDao(): VideoPlaylistDao
    abstract fun hiddenPlacementDao(): HiddenPlacementDao
    abstract fun photoLibraryDao(): PhotoLibraryDao
    abstract fun photoDao(): PhotoDao
    abstract fun bookLibraryDao(): BookLibraryDao
    abstract fun bookDao(): BookDao
    abstract fun scanTombstoneDao(): ScanTombstoneDao
    abstract fun backupDao(): BackupDao
    abstract fun artworkRecordDao(): ArtworkRecordDao
    abstract fun artworkImportReportDao(): ArtworkImportReportDao
    abstract fun ssMediaCacheDao(): SsMediaCacheDao

    companion object {
        const val DATABASE_NAME = "pfp_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE themes ADD COLUMN is_active INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_sources ADD COLUMN platform_id TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_cards (
                        platform_id TEXT NOT NULL PRIMARY KEY,
                        display_name TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        pinned INTEGER NOT NULL DEFAULT 0,
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        rom_directory TEXT,
                        supported_extensions TEXT NOT NULL DEFAULT '',
                        emulator_id TEXT,
                        scan_recursively INTEGER NOT NULL DEFAULT 1,
                        last_scanned_at INTEGER,
                        game_count INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                db.execSQL("DELETE FROM games")
                db.execSQL("DELETE FROM library_sources")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE category_items ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_overrides (
                        package_name TEXT NOT NULL PRIMARY KEY,
                        custom_label TEXT,
                        is_hidden INTEGER NOT NULL DEFAULT 0,
                        customized INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO categories (id, name, icon_key, type, position, is_visible)
                    VALUES ('app_store', 'App Store', 'ic_appstore', 'BUILT_IN', 6, 1)
                    """.trimIndent()
                )
                db.execSQL("UPDATE categories SET name = 'Photo' WHERE id = 'photos' AND name = 'Photos'")
                db.execSQL("UPDATE categories SET name = 'Video' WHERE id = 'videos' AND name = 'Videos'")
                db.execSQL("UPDATE categories SET name = 'Game'  WHERE id = 'games'  AND name = 'Games'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN icon_uri TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN scraped_title TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN user_title_override TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN content_type TEXT NOT NULL DEFAULT 'GAME'")

                db.execSQL("UPDATE games SET content_type = 'ANDROID_APP' WHERE package_name IS NOT NULL")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS collections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        sort_order INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS collection_games (
                        collection_id INTEGER NOT NULL,
                        game_id INTEGER NOT NULL,
                        added_at INTEGER NOT NULL,
                        PRIMARY KEY(collection_id, game_id),
                        FOREIGN KEY(collection_id) REFERENCES collections(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(game_id) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_collection_games_game_id ON collection_games (game_id)"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN launch_shortcut_id TEXT")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN launch_intent_uri TEXT")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collections ADD COLUMN category_id TEXT NOT NULL DEFAULT 'games'")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collections ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO platforms
                        (id, name, short_name, icon_res, accent_color, is_pinned_to_bar,
                         bar_position, preferred_emulator_package, rom_extensions)
                    VALUES
                        ('x360', 'Xbox 360', 'X360', 'ic_platform_xbox360', 4279270416, 0,
                         -1, 'emu.x360.mobile', 'iso,xex,zar,xbla')
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS music_folders (
                        id TEXT NOT NULL PRIMARY KEY,
                        display_name TEXT NOT NULL,
                        tree_uri TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        track_count INTEGER NOT NULL DEFAULT 0,
                        last_scanned_at INTEGER,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS music_tracks (
                        id TEXT NOT NULL PRIMARY KEY,
                        folder_id TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        title TEXT,
                        artist TEXT,
                        album TEXT,
                        duration_ms INTEGER,
                        mime_type TEXT,
                        size_bytes INTEGER,
                        last_modified INTEGER,
                        track_number INTEGER,
                        relative_path TEXT,
                        FOREIGN KEY(folder_id) REFERENCES music_folders(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_music_tracks_folder_id ON music_tracks (folder_id)"
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE music_tracks ADD COLUMN art_uri TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        sort_order INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlist_tracks (
                        playlist_id INTEGER NOT NULL,
                        track_id TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        added_at INTEGER NOT NULL,
                        PRIMARY KEY(playlist_id, track_id),
                        FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlist_id ON playlist_tracks (playlist_id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playlist_tracks_track_id ON playlist_tracks (track_id)"
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS video_libraries (
                        id TEXT NOT NULL PRIMARY KEY,
                        display_name TEXT NOT NULL,
                        tree_uri TEXT NOT NULL,
                        artwork_uri TEXT,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        scan_recursively INTEGER NOT NULL DEFAULT 1,
                        video_count INTEGER NOT NULL DEFAULT 0,
                        last_scanned_at INTEGER,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS videos (
                        id TEXT NOT NULL PRIMARY KEY,
                        library_id TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        title TEXT,
                        duration_ms INTEGER,
                        width INTEGER,
                        height INTEGER,
                        frame_rate REAL,
                        codec TEXT,
                        mime_type TEXT,
                        size_bytes INTEGER,
                        date_added INTEGER,
                        last_modified INTEGER,
                        relative_path TEXT,
                        thumbnail_uri TEXT,
                        custom_thumbnail_uri TEXT,
                        resume_position_ms INTEGER NOT NULL DEFAULT 0,
                        last_watched_at INTEGER,
                        FOREIGN KEY(library_id) REFERENCES video_libraries(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_videos_library_id ON videos (library_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_videos_uri ON videos (uri)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS video_playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        sort_order INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS video_playlist_items (
                        playlist_id INTEGER NOT NULL,
                        video_id TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        added_at INTEGER NOT NULL,
                        PRIMARY KEY(playlist_id, video_id),
                        FOREIGN KEY(playlist_id) REFERENCES video_playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_video_playlist_items_playlist_id ON video_playlist_items (playlist_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_video_playlist_items_video_id ON video_playlist_items (video_id)")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS hidden_placements (
                        item_key TEXT NOT NULL,
                        item_label TEXT NOT NULL,
                        location_type TEXT NOT NULL,
                        location_id TEXT NOT NULL,
                        location_label TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY(item_key, location_type, location_id)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_hidden_placements_item_key ON hidden_placements (item_key)")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS photo_libraries (
                        id TEXT NOT NULL PRIMARY KEY,
                        display_name TEXT NOT NULL,
                        tree_uri TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        scan_recursively INTEGER NOT NULL DEFAULT 1,
                        photo_count INTEGER NOT NULL DEFAULT 0,
                        last_scanned_at INTEGER,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS photos (
                        id TEXT NOT NULL PRIMARY KEY,
                        library_id TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        width INTEGER,
                        height INTEGER,
                        date_taken INTEGER,
                        last_modified INTEGER,
                        size_bytes INTEGER,
                        mime_type TEXT,
                        relative_path TEXT,
                        thumbnail_uri TEXT,
                        date_added INTEGER,
                        FOREIGN KEY(library_id) REFERENCES photo_libraries(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_library_id ON photos (library_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_uri ON photos (uri)")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_cards ADD COLUMN tree_uri TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN rom_uri TEXT")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collections ADD COLUMN icon_key TEXT")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS scan_tombstones (
                        rom_path TEXT NOT NULL,
                        platform_id TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY(rom_path)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "UPDATE platforms SET name = 'Windows Games' WHERE id = 'windows' AND name = 'Windows (Winlator)'"
                )

                db.execSQL(
                    """
                    UPDATE games SET platform_id = 'windows', content_type = 'GAME'
                    WHERE platform_id IN ('app_shortcut', 'windows')
                      AND package_name IN ('com.winlator', 'com.winlator.cmod', 'app.gamenative',
                                           'gamehub.lite', 'banner.hub', 'com.xiaoji.egggame')
                      AND (launch_shortcut_id IS NOT NULL OR launch_intent_uri IS NOT NULL)
                    """.trimIndent()
                )

                db.execSQL(
                    "UPDATE games SET content_type = 'GAME' WHERE platform_id = 'android' AND content_type = 'ANDROID_APP'"
                )
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val zipPlatforms = listOf(
                    "nes", "snes", "n64", "gb", "gbc", "gba", "nds", "virtualboy",
                    "megadrive", "mastersystem", "gamegear", "sega32x",
                    "atari2600", "atari5200", "atari7800", "atarilynx",
                    "pcengine", "ngp", "wonderswan", "wonderswancolor", "c64",
                ).joinToString(",") { "'$it'" }
                db.execSQL(
                    """
                    UPDATE platforms SET rom_extensions = rom_extensions || ',zip'
                    WHERE id IN ($zipPlatforms)
                      AND rom_extensions != '' AND rom_extensions NOT LIKE '%zip%'
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE memory_cards SET supported_extensions = supported_extensions || ',zip'
                    WHERE platform_id IN ($zipPlatforms)
                      AND supported_extensions != '' AND supported_extensions NOT LIKE '%zip%'
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN ss_id INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN tgdb_id INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN igdb_id INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN rom_crc32 TEXT")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN artwork_key TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_artwork_key ON games (artwork_key)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS artwork_index (
                        `key` TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        location TEXT NOT NULL,
                        doc_uri_or_path TEXT NOT NULL,
                        size_bytes INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(`key`, kind)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS artwork_import_reports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        source TEXT NOT NULL,
                        started_at INTEGER NOT NULL,
                        duration_ms INTEGER NOT NULL,
                        summary_json TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS artwork_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        game_id INTEGER NOT NULL,
                        platform_id TEXT NOT NULL,
                        artwork_type TEXT NOT NULL,
                        portable_name TEXT NOT NULL,
                        relative_path TEXT NOT NULL,
                        document_uri TEXT NOT NULL,
                        source TEXT NOT NULL,
                        size_bytes INTEGER NOT NULL,
                        width INTEGER,
                        height INTEGER,
                        checksum TEXT,
                        user_assigned INTEGER NOT NULL,
                        locked INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_artwork_records_game_id_artwork_type " +
                        "ON artwork_records (game_id, artwork_type)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_artwork_records_platform_id_artwork_type_portable_name " +
                        "ON artwork_records (platform_id, artwork_type, portable_name)"
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO artwork_records
                        (game_id, platform_id, artwork_type, portable_name, relative_path,
                         document_uri, source, size_bytes, user_assigned, locked, created_at, updated_at)
                    SELECT g.id, g.platform_id, ai.kind, '', '',
                           ai.doc_uri_or_path, 'import-esde', ai.size_bytes, 0, 0, ai.updated_at, ai.updated_at
                    FROM artwork_index ai JOIN games g ON g.artwork_key = ai.`key`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE IF EXISTS artwork_index")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN box_art_uri TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN physical_media_uri TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN box3d_uri TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN icon_display_mode TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN players TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN age_rating TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN franchise TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN community_rating REAL")
                db.execSQL("ALTER TABLE games ADD COLUMN release_date TEXT")

                db.execSQL(
                    """
                    UPDATE artwork_records SET artwork_type = 'BOX_ART'
                    WHERE artwork_type = 'ICON' AND source IN ('import-esde', 'relink')
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE games SET box_art_uri = (
                        SELECT ar.document_uri FROM artwork_records ar
                        WHERE ar.game_id = games.id AND ar.artwork_type = 'BOX_ART'
                    )
                    WHERE box_art_uri IS NULL
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE games SET icon_uri = NULL
                    WHERE icon_uri IS NOT NULL AND icon_uri = box_art_uri
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    UPDATE games SET physical_media_uri = (
                        SELECT ar.document_uri FROM artwork_records ar
                        WHERE ar.game_id = games.id AND ar.artwork_type = 'PHYSICAL_MEDIA'
                    )
                    WHERE physical_media_uri IS NULL
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ss_media_cache (
                        ss_id INTEGER NOT NULL PRIMARY KEY,
                        medias_json TEXT NOT NULL,
                        fetched_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE artwork_records ADD COLUMN origin_url TEXT")
                db.execSQL("ALTER TABLE artwork_records ADD COLUMN provider TEXT")
                db.execSQL("ALTER TABLE artwork_records ADD COLUMN prev_document_uri TEXT")
                db.execSQL("ALTER TABLE artwork_records ADD COLUMN prev_relative_path TEXT")
                db.execSQL("ALTER TABLE artwork_records ADD COLUMN prev_size_bytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE artwork_records ADD COLUMN crop_rect TEXT")
                db.execSQL("ALTER TABLE artwork_records ADD COLUMN has_original INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS achievement_sets (
                        game_id INTEGER NOT NULL,
                        provider TEXT NOT NULL,
                        provider_game_id TEXT NOT NULL,
                        bronze_total INTEGER NOT NULL,
                        silver_total INTEGER NOT NULL,
                        gold_total INTEGER NOT NULL,
                        bronze_earned INTEGER NOT NULL,
                        silver_earned INTEGER NOT NULL,
                        gold_earned INTEGER NOT NULL,
                        mastered INTEGER NOT NULL,
                        last_synced_at INTEGER,
                        PRIMARY KEY(game_id, provider),
                        FOREIGN KEY(game_id) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS achievements (
                        game_id INTEGER NOT NULL,
                        provider TEXT NOT NULL,
                        provider_achievement_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        tier TEXT NOT NULL,
                        global_rarity REAL NOT NULL,
                        icon_url TEXT,
                        is_hidden INTEGER NOT NULL,
                        is_earned INTEGER NOT NULL,
                        earned_at INTEGER,
                        PRIMARY KEY(game_id, provider, provider_achievement_id),
                        FOREIGN KEY(game_id) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS provider_game_links (
                        game_id INTEGER NOT NULL,
                        provider TEXT NOT NULL,
                        provider_game_id TEXT NOT NULL,
                        source TEXT NOT NULL,
                        resolved_at INTEGER NOT NULL,
                        PRIMARY KEY(game_id),
                        FOREIGN KEY(game_id) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS achievement_match_notes (
                        game_id INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        checked_at INTEGER NOT NULL,
                        PRIMARY KEY(game_id),
                        FOREIGN KEY(game_id) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS account_achievement_sets (
                        provider TEXT NOT NULL,
                        provider_game_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        icon_url TEXT,
                        bronze_total INTEGER NOT NULL,
                        silver_total INTEGER NOT NULL,
                        gold_total INTEGER NOT NULL,
                        bronze_earned INTEGER NOT NULL,
                        silver_earned INTEGER NOT NULL,
                        gold_earned INTEGER NOT NULL,
                        mastered INTEGER NOT NULL,
                        last_synced_at INTEGER,
                        PRIMARY KEY(provider, provider_game_id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS account_achievements (
                        provider TEXT NOT NULL,
                        provider_game_id TEXT NOT NULL,
                        provider_achievement_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        tier TEXT NOT NULL,
                        global_rarity REAL NOT NULL,
                        icon_url TEXT,
                        is_hidden INTEGER NOT NULL,
                        is_earned INTEGER NOT NULL,
                        earned_at INTEGER,
                        PRIMARY KEY(provider, provider_game_id, provider_achievement_id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO account_achievement_sets
                        (provider, provider_game_id, title, icon_url,
                         bronze_total, silver_total, gold_total,
                         bronze_earned, silver_earned, gold_earned, mastered, last_synced_at)
                    SELECT s.provider, s.provider_game_id, COALESCE(g.title, ''), NULL,
                           s.bronze_total, s.silver_total, s.gold_total,
                           s.bronze_earned, s.silver_earned, s.gold_earned, s.mastered, s.last_synced_at
                    FROM achievement_sets s LEFT JOIN games g ON g.id = s.game_id
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO account_achievements
                        (provider, provider_game_id, provider_achievement_id, title, description,
                         tier, global_rarity, icon_url, is_hidden, is_earned, earned_at)
                    SELECT a.provider, s.provider_game_id, a.provider_achievement_id, a.title,
                           a.description, a.tier, a.global_rarity, a.icon_url, a.is_hidden,
                           a.is_earned, a.earned_at
                    FROM achievements a
                    JOIN achievement_sets s ON s.game_id = a.game_id AND s.provider = a.provider
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE achievements")
                db.execSQL("DROP TABLE achievement_sets")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS provider_game_links_v2 (
                        game_id INTEGER NOT NULL,
                        provider TEXT NOT NULL,
                        provider_game_id TEXT NOT NULL,
                        source TEXT NOT NULL,
                        resolved_at INTEGER NOT NULL,
                        PRIMARY KEY(game_id, provider),
                        FOREIGN KEY(game_id) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO provider_game_links_v2 (game_id, provider, provider_game_id, source, resolved_at)
                    SELECT game_id, provider, provider_game_id, source, resolved_at FROM provider_game_links
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE provider_game_links")
                db.execSQL("ALTER TABLE provider_game_links_v2 RENAME TO provider_game_links")
            }
        }

        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS steam_owned_games (
                        appid TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        playtime_forever_minutes INTEGER NOT NULL,
                        synced_playtime_minutes INTEGER,
                        fetched_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS steam_no_achievements (
                        appid TEXT NOT NULL PRIMARY KEY,
                        checked_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE provider_game_links ADD COLUMN ownership TEXT")
            }
        }

        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN launch_token TEXT")
            }
        }

        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN is_missing INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN last_seen_at INTEGER")
            }
        }

        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN disc_set_key TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN disc_number INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN is_disc_primary INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE games
                    SET is_disc_primary = 0
                    WHERE disc_set_key IS NOT NULL
                      AND is_disc_primary = 1
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE games
                    SET is_disc_primary = 1
                    WHERE disc_set_key IS NOT NULL
                      AND id IN (
                          SELECT winner.id
                          FROM games winner
                          WHERE NOT EXISTS (
                              SELECT 1 FROM games better
                              WHERE better.disc_set_key = winner.disc_set_key
                                AND (
                                    (better.disc_number IS NULL AND winner.disc_number IS NOT NULL)
                                    OR (
                                        better.disc_number IS NOT NULL
                                        AND winner.disc_number IS NOT NULL
                                        AND better.disc_number < winner.disc_number
                                    )
                                    OR (
                                        (better.disc_number IS NULL AND winner.disc_number IS NULL
                                         OR better.disc_number IS NOT NULL AND winner.disc_number IS NOT NULL
                                            AND better.disc_number = winner.disc_number)
                                        AND better.id < winner.id
                                    )
                                )
                          )
                      )
                    """.trimIndent()
                )

                db.execSQL("DROP INDEX IF EXISTS index_games_one_disc_primary")
            }
        }

        val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_games_one_disc_primary")
                db.execSQL("ALTER TABLE games ADD COLUMN region TEXT")
            }
        }

        val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS launch_outcomes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        game_id INTEGER NOT NULL,
                        game_title TEXT NOT NULL,
                        platform_id TEXT,
                        emulator_id TEXT,
                        emulator_name TEXT,
                        core_path TEXT,
                        core_name TEXT,
                        source TEXT,
                        outcome TEXT NOT NULL,
                        failure_reason TEXT,
                        launched_at_ms INTEGER NOT NULL,
                        returned_at_ms INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_launch_outcomes_game_id ON launch_outcomes (game_id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_launch_outcomes_platform_id ON launch_outcomes (platform_id)"
                )
            }
        }

        val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE artwork_records ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE artwork_records ADD COLUMN provider_asset_id TEXT")
                db.execSQL("ALTER TABLE artwork_records ADD COLUMN crop_profile_key TEXT")

                db.execSQL("DROP INDEX IF EXISTS index_artwork_records_game_id_artwork_type")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_artwork_records_game_id_artwork_type_sort_order " +
                        "ON artwork_records (game_id, artwork_type, sort_order)"
                )
            }
        }

        val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN storefront TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN storefront_game_id TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_games_storefront_storefront_game_id " +
                        "ON games (storefront, storefront_game_id)"
                )

                val pending = mutableListOf<Triple<Long, String, String>>()
                db.query(
                    "SELECT id, launch_intent_uri FROM games WHERE launch_intent_uri IS NOT NULL"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val uri = cursor.getString(1) ?: continue
                        val (store, storeId) = StorefrontIdentity.fromLaunchIntentUri(uri) ?: continue
                        pending += Triple(id, store, storeId)
                    }
                }
                for ((id, store, storeId) in pending) {
                    db.execSQL(
                        "UPDATE games SET storefront = ?, storefront_game_id = ? WHERE id = ?",
                        arrayOf<Any>(store, storeId, id),
                    )
                }
            }
        }

        val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `book_libraries` (
                        `id` TEXT NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `tree_uri` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `scan_recursively` INTEGER NOT NULL,
                        `book_count` INTEGER NOT NULL,
                        `last_scanned_at` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `books` (
                        `id` TEXT NOT NULL,
                        `library_id` TEXT NOT NULL,
                        `uri` TEXT NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `title` TEXT,
                        `author` TEXT,
                        `last_modified` INTEGER,
                        `size_bytes` INTEGER,
                        `mime_type` TEXT,
                        `relative_path` TEXT,
                        `date_added` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`library_id`) REFERENCES `book_libraries`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_library_id` ON `books` (`library_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_uri` ON `books` (`uri`)")
            }
        }

        val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN series TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN series_index REAL")
                db.execSQL("ALTER TABLE books ADD COLUMN cover_uri TEXT")
            }
        }

        val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS account_achievements")
                db.execSQL("DROP TABLE IF EXISTS account_achievement_sets")
                db.execSQL("DROP TABLE IF EXISTS achievement_match_notes")
                db.execSQL("DROP TABLE IF EXISTS provider_game_links")
                db.execSQL("DROP TABLE IF EXISTS steam_owned_games")
                db.execSQL("DROP TABLE IF EXISTS steam_no_achievements")
            }
        }

        val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_disc_set_key ON games(disc_set_key)")
            }
        }

        val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS unmatched_roms")
            }
        }

        val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN poster_uri TEXT")
            }
        }

        val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE music_tracks ADD COLUMN album_artist TEXT")
            }
        }

        val MIGRATION_51_52 = object : Migration(51, 52) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN date_added INTEGER")
                db.execSQL("UPDATE games SET date_added = 0")
            }
        }

        val MIGRATION_52_53 = object : Migration(52, 53) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN play_state TEXT")
            }
        }

        val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE music_tracks ADD COLUMN last_played_at INTEGER")
                db.execSQL("ALTER TABLE books ADD COLUMN last_opened_at INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_music_tracks_last_played_at " +
                        "ON music_tracks(last_played_at)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_books_last_opened_at " +
                        "ON books(last_opened_at)"
                )
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
            MIGRATION_26_27,
            MIGRATION_27_28,
            MIGRATION_28_29,
            MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32,
            MIGRATION_32_33,
            MIGRATION_33_34,
            MIGRATION_34_35,
            MIGRATION_35_36,
            MIGRATION_36_37,
            MIGRATION_37_38,
            MIGRATION_38_39,
            MIGRATION_39_40,
            MIGRATION_40_41,
            MIGRATION_41_42,
            MIGRATION_42_43,
            MIGRATION_43_44,
            MIGRATION_44_45,
            MIGRATION_45_46,
            MIGRATION_46_47,
            MIGRATION_47_48,
            MIGRATION_48_49,
            MIGRATION_49_50,
            MIGRATION_50_51,
            MIGRATION_51_52,
            MIGRATION_52_53,
        )
    }
}
