package com.psplauncher.feature.backup

import com.psplauncher.core.archive.ZipLimits

import kotlinx.serialization.Serializable

const val BACKUP_FORMAT_VERSION = 2
const val BACKUP_FILE_EXTENSION = ".pfpbackup"

const val BACKUP_FILES_PREFIX = "files/"

val BACKUP_ZIP_LIMITS = ZipLimits(
    maxEntries = 200_000,
    maxEntryBytes = 256L * 1024 * 1024,
    maxTotalBytes = 32L * 1024 * 1024 * 1024,
)

object BackupEntry {
    const val MANIFEST       = "manifest.json"
    const val GAMES          = "games.json"
    const val CATEGORIES     = "categories.json"
    const val CATEGORY_ITEMS = "category_items.json"
    const val PLAY_SESSIONS  = "play_sessions.json"
    const val SETTINGS       = "settings.json"

    const val PLATFORMS            = "platforms.json"
    const val MEMORY_CARDS         = "memory_cards.json"
    const val APP_OVERRIDES        = "app_overrides.json"
    const val COLLECTIONS          = "collections.json"
    const val COLLECTION_GAMES     = "collection_games.json"
    const val THEMES               = "themes.json"
    const val HIDDEN_PLACEMENTS    = "hidden_placements.json"
    const val MUSIC_FOLDERS        = "music_folders.json"
    const val MUSIC_TRACKS         = "music_tracks.json"
    const val PLAYLISTS            = "playlists.json"
    const val PLAYLIST_TRACKS      = "playlist_tracks.json"
    const val VIDEO_LIBRARIES      = "video_libraries.json"
    const val VIDEOS               = "videos.json"
    const val VIDEO_PLAYLISTS      = "video_playlists.json"
    const val VIDEO_PLAYLIST_ITEMS = "video_playlist_items.json"
    const val PHOTO_LIBRARIES      = "photo_libraries.json"
    const val BOOK_LIBRARIES       = "book_libraries.json"
    const val BOOKS                = "books.json"
    const val PHOTOS               = "photos.json"
}

@Serializable
data class BackupManifest(
    val formatVersion: Int    = BACKUP_FORMAT_VERSION,
    val appVersionCode: Int,
    val appVersionName: String,
    val createdAt: Long,
    val gameCount: Int,
    val sessionCount: Int,
    val categoryCount: Int,
)

@Serializable
data class SettingsSnapshot(
    val entries: Map<String, String> = emptyMap(),
)
