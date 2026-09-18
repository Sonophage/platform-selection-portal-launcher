package com.psplauncher.feature.backup

import com.psplauncher.core.archive.ZipLimits

import kotlinx.serialization.Serializable

// v2 — adds the remaining user-owned tables (memory cards, platform prefs, app overrides,
// collections, themes, hidden placements, music/video/photo libraries + playlists) and bundles
// the internal-storage assets (game artwork, custom wallpaper, custom emulator profiles) under
// the "files/" prefix so a restore into a different package/data-dir is complete and portable.
// v1 backups (games/categories/settings only) still restore — missing entries are simply skipped.
const val BACKUP_FORMAT_VERSION = 2
const val BACKUP_FILE_EXTENSION = ".pfpbackup"

// Bundled internal-storage files are stored under this prefix, preserving their path relative to
// the app's filesDir (e.g. "files/artwork/12/hero.jpg"). Everything else in the ZIP is JSON.
const val BACKUP_FILES_PREFIX = "files/"

/**
 * Archive bounds for RESTORE. A backup is not a theme, and reading it with [ZipLimits]' defaults —
 * 512 entries, 32 MB per entry, 128 MB in total — is what those defaults are for: a downloaded
 * `.pfptheme` from a stranger. A real library's artwork alone runs to thousands of files and
 * gigabytes, so the defaults refuse archives THIS APP WROTE: `BackupManager` bundles `artwork/`,
 * `wallpaper/`, `custom-icons/` and `ui-media/` whole, with no cap on its side at all.
 *
 * The bound that still matters is a crafted archive inflating until the disk fills, so these are
 * ceilings rather than a contract — generous enough that no plausible library hits them, small
 * enough that a bomb does.
 *
 * ponytail: fixed ceilings; the honest guard is free-space-aware (refuse when inflating would
 * exhaust the volume) or bounding the WRITER so the two sides agree by construction.
 */
val BACKUP_ZIP_LIMITS = ZipLimits(
    maxEntries = 200_000,
    maxEntryBytes = 256L * 1024 * 1024,
    maxTotalBytes = 32L * 1024 * 1024 * 1024,
)

// Entry names inside the ZIP archive
object BackupEntry {
    const val MANIFEST       = "manifest.json"
    const val GAMES          = "games.json"
    const val CATEGORIES     = "categories.json"
    const val CATEGORY_ITEMS = "category_items.json"
    const val PLAY_SESSIONS  = "play_sessions.json"
    const val SETTINGS       = "settings.json"

    // v2 tables
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
    const val PHOTOS               = "photos.json"
}

@Serializable
data class BackupManifest(
    val formatVersion: Int    = BACKUP_FORMAT_VERSION,
    val appVersionCode: Int,
    val appVersionName: String,
    val createdAt: Long,              // epoch ms
    val gameCount: Int,
    val sessionCount: Int,
    val categoryCount: Int,
)

// Flattened settings snapshot stored as key → value string pairs
@Serializable
data class SettingsSnapshot(
    val entries: Map<String, String> = emptyMap(),
)
