package com.psplauncher.feature.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psplauncher.core.data.database.dao.BackupDao
import com.psplauncher.core.data.database.dao.CategoryDao
import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.core.data.database.dao.PlaySessionDao
import com.psplauncher.core.data.database.entity.AppOverrideEntity
import com.psplauncher.core.data.database.entity.CategoryEntity
import com.psplauncher.core.data.database.entity.CategoryItemEntity
import com.psplauncher.core.data.database.entity.CollectionEntity
import com.psplauncher.core.data.database.entity.CollectionGameEntity
import com.psplauncher.core.data.database.entity.GameEntity
import com.psplauncher.core.data.database.entity.HiddenPlacementEntity
import com.psplauncher.core.data.database.entity.MemoryCardEntity
import com.psplauncher.core.data.database.entity.MusicFolderEntity
import com.psplauncher.core.data.database.entity.MusicTrackEntity
import com.psplauncher.core.data.database.entity.BookEntity
import com.psplauncher.core.data.database.entity.BookLibraryEntity
import com.psplauncher.core.data.database.entity.PhotoEntity
import com.psplauncher.core.data.database.entity.PhotoLibraryEntity
import com.psplauncher.core.data.database.entity.PlatformEntity
import com.psplauncher.core.data.database.entity.PlaylistEntity
import com.psplauncher.core.data.database.entity.PlaylistTrackEntity
import com.psplauncher.core.data.database.entity.PlaySessionEntity
import com.psplauncher.core.data.database.entity.ThemeEntity
import com.psplauncher.core.data.database.entity.VideoEntity
import com.psplauncher.core.data.database.entity.VideoLibraryEntity
import com.psplauncher.core.data.database.entity.VideoPlaylistEntity
import com.psplauncher.core.data.database.entity.VideoPlaylistItemEntity
import com.psplauncher.core.common.security.KeystoreSecretCipher
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.data.repository.BackupFolderRepository
import com.psplauncher.core.data.repository.UiMediaStore
import com.psplauncher.core.domain.model.UiMediaSlot
import com.psplauncher.feature.backup.restore.RestoreArchive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class BackupResult {
    data class Success(val displayName: String) : BackupResult()
    data class Failure(val reason: String, val cause: Throwable? = null) : BackupResult()
}

data class BackupInfo(val name: String, val uri: Uri, val lastModified: Long)

sealed class RestoreResult {
    data class Success(val refusals: List<String> = emptyList()) : RestoreResult()
    data class Failure(val reason: String, val cause: Throwable? = null) : RestoreResult()
}

@Singleton
open class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val categoryDao: CategoryDao,
    private val playSessionDao: PlaySessionDao,
    private val backupDao: BackupDao,
    private val backupFolderRepository: BackupFolderRepository,
    private val uiMediaStore: UiMediaStore,
    private val categoryRepository: com.psplauncher.core.data.repository.CategoryRepositoryImpl,

    private val menuSound: com.psplauncher.core.ui.sound.MenuSoundPlayer,
) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    suspend fun createBackup(
        appVersionCode: Int,
        appVersionName: String,
        createdAt: Long,
    ): BackupResult {
        val backupFolder = backupFolderRepository.get()
        if (backupFolder.isNullOrBlank()) {
            return BackupResult.Failure(
                "No backup folder set. Choose one under Settings → Backup & Restore → Backup Folder."
            )
        }
        return runCatching {
        val games        = gameDao.getAll()
        val categories   = categoryDao.getAll()
        val items        = categoryDao.getAllItems()
        val sessions     = playSessionDao.getAll()
        val settings     = readSettingsSnapshot()

        val manifest = BackupManifest(
            appVersionCode = appVersionCode,
            appVersionName = appVersionName,
            createdAt = createdAt,
            gameCount = games.size,
            sessionCount = sessions.size,
            categoryCount = categories.size,
        )

        val fileName = "pfp_backup_${createdAt}${BACKUP_FILE_EXTENSION}"
        val tempFile = File(context.cacheDir, fileName)

        ZipOutputStream(tempFile.outputStream().buffered()).use { zip ->
            zip.writeJson(BackupEntry.MANIFEST,       json.encodeToString(BackupManifest.serializer(), manifest))
            zip.writeJson(BackupEntry.GAMES,          json.encodeToString(listSerializer<GameEntity>(), games))
            zip.writeJson(BackupEntry.CATEGORIES,     json.encodeToString(listSerializer<CategoryEntity>(), categories))
            zip.writeJson(BackupEntry.CATEGORY_ITEMS, json.encodeToString(listSerializer<CategoryItemEntity>(), items))
            zip.writeJson(BackupEntry.PLAY_SESSIONS,  json.encodeToString(listSerializer<PlaySessionEntity>(), sessions))
            zip.writeJson(BackupEntry.SETTINGS,       json.encodeToString(SettingsSnapshot.serializer(), settings))

            zip.writeJson(BackupEntry.PLATFORMS,            json.encodeToString(listSerializer<PlatformEntity>(),          backupDao.getPlatforms()))
            zip.writeJson(BackupEntry.MEMORY_CARDS,         json.encodeToString(listSerializer<MemoryCardEntity>(),        backupDao.getMemoryCards()))
            zip.writeJson(BackupEntry.APP_OVERRIDES,        json.encodeToString(listSerializer<AppOverrideEntity>(),       backupDao.getAppOverrides()))
            zip.writeJson(BackupEntry.COLLECTIONS,          json.encodeToString(listSerializer<CollectionEntity>(),        backupDao.getCollections()))
            zip.writeJson(BackupEntry.COLLECTION_GAMES,     json.encodeToString(listSerializer<CollectionGameEntity>(),    backupDao.getCollectionGames()))
            zip.writeJson(BackupEntry.THEMES,               json.encodeToString(listSerializer<ThemeEntity>(),             backupDao.getThemes()))
            zip.writeJson(BackupEntry.HIDDEN_PLACEMENTS,    json.encodeToString(listSerializer<HiddenPlacementEntity>(),   backupDao.getHiddenPlacements()))
            zip.writeJson(BackupEntry.MUSIC_FOLDERS,        json.encodeToString(listSerializer<MusicFolderEntity>(),       backupDao.getMusicFolders()))
            zip.writeJson(BackupEntry.MUSIC_TRACKS,         json.encodeToString(listSerializer<MusicTrackEntity>(),        backupDao.getMusicTracks()))
            zip.writeJson(BackupEntry.PLAYLISTS,            json.encodeToString(listSerializer<PlaylistEntity>(),          backupDao.getPlaylists()))
            zip.writeJson(BackupEntry.PLAYLIST_TRACKS,      json.encodeToString(listSerializer<PlaylistTrackEntity>(),     backupDao.getPlaylistTracks()))
            zip.writeJson(BackupEntry.VIDEO_LIBRARIES,      json.encodeToString(listSerializer<VideoLibraryEntity>(),      backupDao.getVideoLibraries()))
            zip.writeJson(BackupEntry.VIDEOS,               json.encodeToString(listSerializer<VideoEntity>(),             backupDao.getVideos()))
            zip.writeJson(BackupEntry.VIDEO_PLAYLISTS,      json.encodeToString(listSerializer<VideoPlaylistEntity>(),     backupDao.getVideoPlaylists()))
            zip.writeJson(BackupEntry.VIDEO_PLAYLIST_ITEMS, json.encodeToString(listSerializer<VideoPlaylistItemEntity>(), backupDao.getVideoPlaylistItems()))
            zip.writeJson(BackupEntry.PHOTO_LIBRARIES,      json.encodeToString(listSerializer<PhotoLibraryEntity>(),      backupDao.getPhotoLibraries()))
            zip.writeJson(BackupEntry.PHOTOS,               json.encodeToString(listSerializer<PhotoEntity>(),             backupDao.getPhotos()))
            zip.writeJson(BackupEntry.BOOK_LIBRARIES,       json.encodeToString(listSerializer<BookLibraryEntity>(),       backupDao.getBookLibraries()))
            zip.writeJson(BackupEntry.BOOKS,                json.encodeToString(listSerializer<BookEntity>(),              backupDao.getBooks()))

            val filesDir = context.filesDir
            BUNDLED_FILE_ROOTS.forEach { root -> zip.bundleTree(filesDir, root) }
        }

        val exported = exportToBackupFolder(backupFolder, tempFile, fileName)
        tempFile.delete()
        if (exported == null) {
            error("Could not write to the backup folder. Re-link it under Settings → Folder Access.")
        }
        fileName
    }.fold(
        onSuccess = {
            menuSound.play(com.psplauncher.core.ui.sound.MenuSound.NOTIFICATION)
            BackupResult.Success(it)
        },
        onFailure = { BackupResult.Failure(it.message ?: "Unknown error", it) },
    )
    }

    suspend fun restoreBackup(uri: Uri): RestoreResult = runCatching {
        val stream = context.contentResolver.openInputStream(uri)
            ?: return RestoreResult.Failure("Could not open backup file")

        val filesDir = context.filesDir
        val staging  = File(filesDir, RESTORE_STAGING_DIR)

        val bundle = stream.use {
            RestoreArchive.read(
                source       = it,
                staging      = staging,
                bundledRoots = BUNDLED_FILE_ROOTS,

                limits       = BACKUP_ZIP_LIMITS,
                selfPackage  = context.packageName,
            )
        }
        val entries = bundle.jsonEntries

        val manifest = entries[BackupEntry.MANIFEST]?.let {
            json.decodeFromString(BackupManifest.serializer(), it)
        } ?: run {
            bundle.discard()
            return RestoreResult.Failure("Backup is missing manifest")
        }

        if (manifest.formatVersion > BACKUP_FORMAT_VERSION) {
            bundle.discard()
            return RestoreResult.Failure(
                "Backup format v${manifest.formatVersion} is newer than this app supports (v$BACKUP_FORMAT_VERSION)"
            )
        }

        val games          = entries.decodeList<GameEntity>(BackupEntry.GAMES)
        val categories     = entries.decodeList<CategoryEntity>(BackupEntry.CATEGORIES)
        val catItems       = entries.decodeList<CategoryItemEntity>(BackupEntry.CATEGORY_ITEMS)
        val sessions       = entries.decodeList<PlaySessionEntity>(BackupEntry.PLAY_SESSIONS)
        val platforms      = entries.decodeList<PlatformEntity>(BackupEntry.PLATFORMS)
        val memoryCards    = entries.decodeList<MemoryCardEntity>(BackupEntry.MEMORY_CARDS)
        val appOverrides   = entries.decodeList<AppOverrideEntity>(BackupEntry.APP_OVERRIDES)
        val collections    = entries.decodeList<CollectionEntity>(BackupEntry.COLLECTIONS)
        val collectionGames = entries.decodeList<CollectionGameEntity>(BackupEntry.COLLECTION_GAMES)
        val themes         = entries.decodeList<ThemeEntity>(BackupEntry.THEMES)
        val hiddenPlaces   = entries.decodeList<HiddenPlacementEntity>(BackupEntry.HIDDEN_PLACEMENTS)
        val musicFolders   = entries.decodeList<MusicFolderEntity>(BackupEntry.MUSIC_FOLDERS)
        val musicTracks    = entries.decodeList<MusicTrackEntity>(BackupEntry.MUSIC_TRACKS)
        val playlists      = entries.decodeList<PlaylistEntity>(BackupEntry.PLAYLISTS)
        val playlistTracks = entries.decodeList<PlaylistTrackEntity>(BackupEntry.PLAYLIST_TRACKS)
        val videoLibraries = entries.decodeList<VideoLibraryEntity>(BackupEntry.VIDEO_LIBRARIES)
        val videos         = entries.decodeList<VideoEntity>(BackupEntry.VIDEOS)
        val videoPlaylists = entries.decodeList<VideoPlaylistEntity>(BackupEntry.VIDEO_PLAYLISTS)
        val videoPlItems   = entries.decodeList<VideoPlaylistItemEntity>(BackupEntry.VIDEO_PLAYLIST_ITEMS)
        val photoLibraries = entries.decodeList<PhotoLibraryEntity>(BackupEntry.PHOTO_LIBRARIES)
        val photos         = entries.decodeList<PhotoEntity>(BackupEntry.PHOTOS)
        val bookLibraries  = entries.decodeList<BookLibraryEntity>(BackupEntry.BOOK_LIBRARIES)
        val books          = entries.decodeList<BookEntity>(BackupEntry.BOOKS)

        val settings = entries[BackupEntry.SETTINGS]?.let {
            json.decodeFromString(SettingsSnapshot.serializer(), it)
        }

        val filesDirPath = filesDir.absolutePath
        bundle.commitFiles(filesDir)

        val remappedGames = games.map { g ->
            g.copy(
                artworkUri = rewriteFilesPath(g.artworkUri, filesDirPath),
                heroUri    = rewriteFilesPath(g.heroUri, filesDirPath),
                logoUri    = rewriteFilesPath(g.logoUri, filesDirPath),
                iconUri    = rewriteFilesPath(g.iconUri, filesDirPath),
            )
        }

        gameDao.deleteAll()
        playSessionDao.deleteAll()

        backupDao.clearCollectionGames()
        backupDao.clearCollections()
        backupDao.clearPlaylistTracks()
        backupDao.clearPlaylists()
        backupDao.clearMusicTracks()
        backupDao.clearMusicFolders()
        backupDao.clearVideoPlaylistItems()
        backupDao.clearVideoPlaylists()
        backupDao.clearVideos()
        backupDao.clearVideoLibraries()
        backupDao.clearPhotos()
        backupDao.clearPhotoLibraries()
        backupDao.clearBooks()
        backupDao.clearBookLibraries()
        backupDao.clearMemoryCards()
        backupDao.clearAppOverrides()
        backupDao.clearHiddenPlacements()
        backupDao.clearCategoryItems()

        if (remappedGames.isNotEmpty()) gameDao.insertAllReplace(remappedGames)
        if (sessions.isNotEmpty())      playSessionDao.insertAll(sessions)

        categories.forEach { categoryDao.upsert(it) }
        catItems.forEach   { categoryDao.addItem(it) }

        backupDao.insertMemoryCards(memoryCards)
        backupDao.insertAppOverrides(appOverrides)
        backupDao.insertCollections(collections)
        backupDao.insertCollectionGames(collectionGames)
        backupDao.insertHiddenPlacements(hiddenPlaces)
        backupDao.insertMusicFolders(musicFolders)
        backupDao.insertMusicTracks(musicTracks)
        backupDao.insertPlaylists(playlists)
        backupDao.insertPlaylistTracks(playlistTracks)
        backupDao.insertVideoLibraries(videoLibraries)
        backupDao.insertVideos(videos)
        backupDao.insertVideoPlaylists(videoPlaylists)
        backupDao.insertVideoPlaylistItems(videoPlItems)
        backupDao.insertPhotoLibraries(photoLibraries)
        backupDao.insertBookLibraries(bookLibraries)
        backupDao.insertBooks(books)
        backupDao.insertPhotos(photos)

        platforms.forEach { p ->
            backupDao.restorePlatformPrefs(p.id, p.preferredEmulatorPackage, p.isPinnedToBar, p.barPosition)
        }

        backupDao.insertThemes(themes)
        themes.firstOrNull { it.isActive }?.let { backupDao.setActiveTheme(it.id) }

        if (settings != null) restoreSettingsSnapshot(settings.remapWallpaper(filesDirPath))

        bundle.refusals
    }.fold(
        onSuccess = { refusals ->

            runCatching { uiMediaStore.pruneOrphans() }
                .onFailure { Timber.w(it, "Post-restore UI-media prune failed") }

            runCatching { categoryRepository.pruneRetiredCategories() }
                .onFailure { Timber.w(it, "Post-restore retired-category prune failed") }
            menuSound.play(com.psplauncher.core.ui.sound.MenuSound.NOTIFICATION)
            RestoreResult.Success(refusals)
        },
        onFailure = { RestoreResult.Failure(it.message ?: "Unknown error", it) },
    )

    protected open suspend fun exportToBackupFolder(treeUri: String, source: File, name: String): Uri? {
        val tree = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return null
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        val parentDoc = DocumentsContract.buildDocumentUriUsingTree(tree, rootDocId)
        val doc = runCatching {
            DocumentsContract.createDocument(context.contentResolver, parentDoc, MIME_BACKUP, name)
        }.getOrNull() ?: return null
        val ok = runCatching {
            context.contentResolver.openOutputStream(doc)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } != null
        }.getOrDefault(false)
        return if (ok) doc else null
    }

    open suspend fun listBackups(): List<BackupInfo> {
        val treeUri = backupFolderRepository.get()?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: return emptyList()
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
        val out = mutableListOf<BackupInfo>()
        runCatching {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val docId = c.getString(0) ?: continue
                    val name  = c.getString(1) ?: continue
                    if (!name.endsWith(BACKUP_FILE_EXTENSION)) continue
                    val lastModified = if (c.isNull(2)) 0L else c.getLong(2)
                    out.add(BackupInfo(name, DocumentsContract.buildDocumentUriUsingTree(treeUri, docId), lastModified))
                }
            }
        }.onFailure { Timber.w(it, "Could not list backups in SAF folder") }
        return out.sortedByDescending { it.lastModified }
    }

    private fun ZipOutputStream.bundleTree(filesDir: File, root: String) {
        val dir = File(filesDir, root)
        if (!dir.exists()) return
        dir.walkTopDown().filter { it.isFile }.forEach { f ->
            val entryName = BACKUP_FILES_PREFIX + f.relativeTo(filesDir).invariantSeparatorsPath
            putNextEntry(ZipEntry(entryName))
            f.inputStream().use { it.copyTo(this) }
            closeEntry()
        }
    }

    private fun ZipOutputStream.writeJson(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private inline fun <reified T> Map<String, String>.decodeList(name: String): List<T> =
        this[name]?.let { json.decodeFromString(listSerializer<T>(), it) } ?: emptyList()

    private fun rewriteFilesPath(path: String?, filesDirPath: String): String? {
        if (path.isNullOrEmpty()) return path
        val idx = path.indexOf(FILES_MARKER)
        if (idx < 0) return path
        return filesDirPath.trimEnd('/') + "/" + path.substring(idx + FILES_MARKER.length)
    }

    private fun SettingsSnapshot.remapWallpaper(filesDirPath: String): SettingsSnapshot {
        var entries = this.entries

        for (key in listOf(KEY_CUSTOM_WALLPAPER, KEY_MOTION_WALLPAPER)) {
            val current = entries[key] ?: continue
            val remapped = rewriteFilesPath(current, filesDirPath) ?: continue
            if (remapped != current) entries = entries + (key to remapped)
        }
        return if (entries != this.entries) copy(entries = entries) else this
    }

    protected open suspend fun readSettingsSnapshot(): SettingsSnapshot {
        val prefs = context.pfpDataStore.data.first()
        val entries = mutableMapOf<String, String>()

        BACKED_UP_STRING_KEYS.forEach { key ->
            prefs[key]?.let { entries[key.name] = it }
        }
        BACKED_UP_BOOLEAN_KEYS.forEach { key ->
            prefs[key]?.let { entries[key.name] = it.toString() }
        }
        BACKED_UP_FLOAT_KEYS.forEach { key ->
            prefs[key]?.let { entries[key.name] = it.toString() }
        }
        BACKED_UP_LONG_KEYS.forEach { key ->
            prefs[key]?.let { entries[key.name] = it.toString() }
        }
        BACKED_UP_INT_KEYS.forEach { key ->
            prefs[key]?.let { entries[key.name] = it.toString() }
        }

        return SettingsSnapshot(entries)
    }

    protected open suspend fun restoreSettingsSnapshot(snapshot: SettingsSnapshot) {
        context.pfpDataStore.edit { prefs ->
            prefs.clear()

            BACKED_UP_STRING_KEYS.forEach { key ->
                snapshot.entries[key.name]?.let { value ->

                    if (key.name in ENCRYPTED_CREDENTIAL_KEYS &&
                        !KeystoreSecretCipher.isUsableOnThisDevice(value)
                    ) {
                        Timber.i("Dropped un-decryptable credential on restore: ${key.name} (re-prompt)")
                        return@let
                    }
                    prefs[key] = value
                }
            }
            BACKED_UP_BOOLEAN_KEYS.forEach { key ->
                snapshot.entries[key.name]?.toBooleanStrictOrNull()?.let { prefs[key] = it }
            }
            BACKED_UP_FLOAT_KEYS.forEach { key ->
                snapshot.entries[key.name]?.toFloatOrNull()?.let { prefs[key] = it }
            }
            BACKED_UP_LONG_KEYS.forEach { key ->
                snapshot.entries[key.name]?.toLongOrNull()?.let { prefs[key] = it }
            }
            BACKED_UP_INT_KEYS.forEach { key ->
                snapshot.entries[key.name]?.toIntOrNull()?.let { prefs[key] = it }
            }
        }
    }

    private inline fun <reified T> listSerializer() =
        kotlinx.serialization.builtins.ListSerializer(
            kotlinx.serialization.serializer<T>()
        )

    companion object {
        private const val RESTORE_STAGING_DIR = ".pfp_restore_tmp"

        private const val MIME_BACKUP = "application/octet-stream"
    private const val FILES_MARKER = "/files/"
    private const val KEY_CUSTOM_WALLPAPER = "display_custom_wallpaper"

    private const val KEY_MOTION_WALLPAPER = "display_motion_wallpaper"

        private val BUNDLED_FILE_ROOTS = listOf(
            "artwork",
            "wallpaper",
            "emulator_profiles",
            "custom-icons",
            "ui-media",
        )

    private val BACKED_UP_STRING_KEYS = listOf(

        stringPreferencesKey("display_wave_style"),

        stringPreferencesKey("display_gameboot_mode"),
        stringPreferencesKey("display_color_scheme"),
        stringPreferencesKey("display_custom_wallpaper"),
        stringPreferencesKey("display_motion_wallpaper"),

        stringPreferencesKey("display_text_legibility"),

        stringPreferencesKey("display_icon_legibility"),
        stringPreferencesKey("display_xmb_layout_adjust"),
        stringPreferencesKey("pref_icon_display_mode"),

        stringPreferencesKey("pref_icon_display_mode_by_platform"),

        stringPreferencesKey("pref_video_snap_placement"),

        stringPreferencesKey("ra_username"),
        stringPreferencesKey("steam_id64"),
        stringPreferencesKey("theme_applied_name"),
        stringPreferencesKey("theme_layout_spec"),

            stringPreferencesKey("controller_scroll_speed"),
            stringPreferencesKey("controller_stick_sensitivity"),

            stringPreferencesKey("controller_mappings_v1"),
            stringPreferencesKey("controller_confirm_back_layout"),
            stringPreferencesKey("controller_xy_layout"),
            stringPreferencesKey("controller_display_type"),

            stringPreferencesKey("interface_touch_nav_button"),
            stringPreferencesKey("interface_touch_sensitivity"),

            stringPreferencesKey("music_default_player_package"),
            stringPreferencesKey("video_default_player"),
            stringPreferencesKey("books_default_reader"),

            stringPreferencesKey("library_rom_root_tree_uris"),
            stringPreferencesKey("library_rom_root_tree_uri"),

            stringPreferencesKey("music_root_tree_uris"),
            stringPreferencesKey("video_root_tree_uris"),
            stringPreferencesKey("photo_root_tree_uris"),
            stringPreferencesKey("book_root_tree_uris"),

            stringPreferencesKey("backup_folder_tree_uri"),

            stringPreferencesKey("artwork_folder_tree_uri"),
            stringPreferencesKey("artwork_storage_mode"),
            stringPreferencesKey("artwork_library_uuid"),

            stringPreferencesKey("sgdb_api_key"),
            stringPreferencesKey("tmdb_api_key"),
            stringPreferencesKey("igdb_client_id"),
            stringPreferencesKey("igdb_client_secret"),
            stringPreferencesKey("ss_username"),
            stringPreferencesKey("ss_password"),
        ) +

            UiMediaSlot.entries.map { UiMediaStore.displayNameKey(it) }

        private val ENCRYPTED_CREDENTIAL_KEYS = setOf(
            "sgdb_api_key",

            "tmdb_api_key",
            "igdb_client_secret",
            "ss_password",
        )

        private val BACKED_UP_BOOLEAN_KEYS = listOf(

            booleanPreferencesKey("android_card_seeded_v1"),

            booleanPreferencesKey("display_show_boot"),
            booleanPreferencesKey("display_boot_on_resume"),
            booleanPreferencesKey("display_thermal_aware"),
            booleanPreferencesKey("display_battery_saver"),
            booleanPreferencesKey("display_wave_over_wallpaper"),

            booleanPreferencesKey("theme_accent_from_wallpaper"),
            booleanPreferencesKey("interface_context_menu_hint"),

            booleanPreferencesKey("display_text_color_exact"),
            booleanPreferencesKey("display_text_contrast_notice_suppressed"),

            booleanPreferencesKey("display_solid_unfocused_icons"),
            booleanPreferencesKey("display_fade_by_distance"),
            booleanPreferencesKey("display_card_art_grid"),
            booleanPreferencesKey("display_recents_include_apps"),
            booleanPreferencesKey("display_text_shadow"),
            booleanPreferencesKey("pref_animated_icons"),
            booleanPreferencesKey("pref_xmb_game_metadata"),

        booleanPreferencesKey("pref_xmb_item_backdrop"),

        booleanPreferencesKey("artwork_crop_preview_enabled"),

            booleanPreferencesKey("pref_direct_game_launch"),

            booleanPreferencesKey("artwork_import_move_files"),
            booleanPreferencesKey("pref_dl_manuals"),
            booleanPreferencesKey("pref_dl_video_snaps"),

            booleanPreferencesKey("windows_library_setup_prompt"),

            booleanPreferencesKey("display_gameboot_enabled"),

            booleanPreferencesKey("display_launch_disc"),

            booleanPreferencesKey("controller_left_backs_out"),

            booleanPreferencesKey("sound_menu_enabled"),

            booleanPreferencesKey("sound_menu_music"),

            booleanPreferencesKey("pref_dl_clear_logos"),
            booleanPreferencesKey("pref_dl_heroes"),
            booleanPreferencesKey("pref_sgdb_heroes"),

            booleanPreferencesKey("library_setup_complete"),

            booleanPreferencesKey("initial_setup_seen"),

        )

        private val BACKED_UP_FLOAT_KEYS = listOf(
            floatPreferencesKey("interface_context_menu_hint_delay_seconds"),

            floatPreferencesKey("display_xmb_scale"),
            floatPreferencesKey("display_bar_top_fraction"),
            floatPreferencesKey("pref_icon1_linger_delay_seconds"),
        )

        internal val BACKED_UP_KEY_NAMES: Set<String>
            get() = (
                BACKED_UP_STRING_KEYS.map { it.name } +
                    BACKED_UP_BOOLEAN_KEYS.map { it.name } +
                    BACKED_UP_FLOAT_KEYS.map { it.name } +
                    BACKED_UP_LONG_KEYS.map { it.name } +
                    BACKED_UP_INT_KEYS.map { it.name }
                ).toSet()

        private val BACKED_UP_INT_KEYS = listOf<androidx.datastore.preferences.core.Preferences.Key<Int>>()

        private val BACKED_UP_LONG_KEYS = listOf(

            longPreferencesKey("display_text_color"),

            longPreferencesKey("theme_accent_override"),
            longPreferencesKey("theme_icon_color"),
            longPreferencesKey("custom_icons_stamp"),
            longPreferencesKey("ui_media_stamp"),
        )
    }
}
