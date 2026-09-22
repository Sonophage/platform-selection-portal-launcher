package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.ADD_MENU_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.ALL_BOOKS_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.ALL_MUSIC_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.ALL_PHOTOS_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.ALL_VIDEOS_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.BOOK_SERIES_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.BOOK_SHELVES_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.CAMERA_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.MEMORY_CARD_ASSET_URI
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.NOW_PLAYING_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.OPEN_READER_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.PHOTO_ALBUMS_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.PLAYLISTS_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.VIDEO_COLLECTIONS_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.VIDEO_LIBRARIES_ITEM_ID

/**
 * What each media column shows at its root, as pure functions of the state.
 *
 * The same move as ContextMenus.kt, for the same reason: these decide the shape of Music, Video,
 * Photo and Library, they are about to be restructured, and inside a 9,000-line ViewModel there
 * was no way to assert what a column contains. Moving them first means the restructure lands
 * somewhere a test can see it.
 *
 * Extensions on XMBUiState because that is all they read — every `_uiState.value.x` became `x`.
 * Anything that needs a repository stays in the ViewModel.
 */

// Music root: the static items (Now Playing, when something is playing; Playlist; Music Apps)
// followed by the single "All Music" memory-card item. The root folder is managed in Settings →
// Music; a getting-started "Add Music Folder" row shows until a root has been added and scanned
// (keyed off the scan completing, not the track count), then drops away.
/**
 * The Music root's own sections, without the app rows.
 *
 * Separate from [musicRootItems] because the flyout's sibling column wants only the drillable
 * sections, while the published list also carries the installed music apps. Publishing this
 * one by mistake would silently drop the apps, which is why it is named for what it is.
 */
internal fun XMBUiState.musicRootSections(): List<XMBItem> {
    val folders = musicFolders
    val totalTracks = folders.sumOf { it.trackCount }
    return buildList {
        // Now Playing — only when a track is loaded; clicking returns to the active song.
        musicPlayback.track?.let { track ->
            add(
                XMBItem(
                    id       = NOW_PLAYING_ITEM_ID,
                    title    = track.displayTitle,
                    subtitle = listOfNotNull("Now Playing", track.artist).joinToString("  ·  "),
                    coverUri = track.artUri,
                    type     = XMBItemType.MUSIC_TRACK,   // renders the album-cover leading tile
                )
            )
        }
        add(
            XMBItem(
                id       = PLAYLISTS_ITEM_ID,
                title    = "Playlist",
                subtitle = "Build and play your own track lists",
                type     = XMBItemType.PLAYLIST,
            )
        )
        // All scanned music collapses into one memory-card item (like All Games). Uses the
        // physical-media "_default.png" memory-card art rather than the blank console fallback.
        add(
            XMBItem(
                id       = ALL_MUSIC_ITEM_ID,
                title    = "Music",
                subtitle = countLabel(totalTracks, "track", "tracks"),
                coverUri = MEMORY_CARD_ASSET_URI,
                type     = XMBItemType.MEMORY_CARD,
            )
        )
    }
}

// Video root: browse rows first (Collections, Video Libraries), then the Video Apps counterpart
// directly above the "Videos" memory card (second-to-bottom). The root folder is managed in
// Settings → Video; a getting-started "Add Videos" row shows until a root has been added and
// scanned (keyed off the scan completing, not the video count), then drops away.
/** The Video root's own sections, without the app rows (see [musicRootSections]). */
internal fun XMBUiState.videoRootSections(): List<XMBItem> {
    val libraries = videoLibraries
    val totalVideos = libraries.sumOf { it.videoCount }
    return buildList {
        // The three curated views collapse into one "Collections" entry (drills into
        // Recently Watched / Favorites / Playlists) to keep the Video root uncluttered.
        add(
            XMBItem(
                id       = VIDEO_COLLECTIONS_ITEM_ID,
                title    = "Collections",
                subtitle = "Recently Watched, Favorites & Playlists",
                type     = XMBItemType.VIDEO_COLLECTIONS,
            )
        )
        add(
            XMBItem(
                id       = VIDEO_LIBRARIES_ITEM_ID,
                title    = "Video Libraries",
                subtitle = countLabel(libraries.size, "library", "libraries"),
                type     = XMBItemType.VIDEO_LIBRARY,
            )
        )
        add(
            XMBItem(
                id       = ALL_VIDEOS_ITEM_ID,
                title    = "Videos",
                subtitle = countLabel(totalVideos, "video", "videos"),
                coverUri = MEMORY_CARD_ASSET_URI,
                type     = XMBItemType.MEMORY_CARD,
            )
        )
    }
}

// Photo root, PSP-style: Camera (when a camera app exists) and Albums first, then the Photo Apps
// counterpart directly above the "Photos" memory card (second-to-bottom), with the "Add Photo
// Library" row last — it disappears once a library has been scanned (further libraries are added
// from Settings → Photo).
/** The Photo root's own sections, without the app rows (see [musicRootSections]). */
// cameraAvailable is a PackageManager query the ViewModel caches, not state — so it comes in
// as an argument rather than being half-copied to here.
internal fun XMBUiState.photoRootSections(cameraAvailable: Boolean): List<XMBItem> {
    val libraries = photoLibraries
    val totalPhotos = libraries.sumOf { it.photoCount }
    return buildList {
        if (cameraAvailable) {
            add(
                XMBItem(
                    id       = CAMERA_ITEM_ID,
                    title    = "Camera",
                    subtitle = "Open the camera",
                    type     = XMBItemType.CAMERA,
                )
            )
        }
        add(
            XMBItem(
                id       = PHOTO_ALBUMS_ITEM_ID,
                title    = "Albums",
                subtitle = countLabel(libraries.size, "album", "albums"),
                type     = XMBItemType.PHOTO_ALBUMS,
            )
        )
        add(
            XMBItem(
                id       = ALL_PHOTOS_ITEM_ID,
                title    = "Photos",
                subtitle = countLabel(totalPhotos, "photo", "photos"),
                coverUri = MEMORY_CARD_ASSET_URI,
                type     = XMBItemType.MEMORY_CARD,
            )
        )
    }
}

/** The Library root's own sections, without the app rows (see [musicRootSections]). */
internal fun XMBUiState.booksRootSections(): List<XMBItem> {
    val shelves = bookLibraries
    val totalBooks = shelves.sumOf { it.bookCount }
    val reader = defaultReader
    return buildList {
        // The reader, first, so the app you read in is one press away whether or not you are
        // opening something from the library. Hidden when no reader is set, since there is
        // nothing to open: the picker lives in Settings.
        if (reader != null) {
            add(
                XMBItem(
                    id       = OPEN_READER_ITEM_ID,
                    title    = defaultReaderLabel ?: "Open Reader",
                    subtitle = "Open your reader",
                    type     = XMBItemType.LIBRARY_READER,
                )
            )
        }
        // Only worth a row once there is a choice to make, by the same rule as Series below.
        // With a single shelf the row opens a list of one whose only entry holds every book
        // the Books row already holds, so it is two extra presses to reach the same place --
        // and it reads as a distinction the library does not actually have.
        if (shelves.size > 1) {
            add(
                XMBItem(
                    id       = BOOK_SHELVES_ITEM_ID,
                    title    = "Shelves",
                    subtitle = countLabel(shelves.size, "shelf", "shelves"),
                    type     = XMBItemType.LIBRARY_SHELVES,
                )
            )
        }
        // Only worth a row once something declares a series. A library of standalones would
        // otherwise carry a row that opens an empty list.
        val series = bookSeries
        if (series.isNotEmpty()) {
            add(
                XMBItem(
                    id       = BOOK_SERIES_ITEM_ID,
                    title    = "Series",
                    subtitle = countLabel(series.size, "series", "series"),
                    type     = XMBItemType.LIBRARY_SERIES,
                )
            )
        }
        add(
            XMBItem(
                id       = ALL_BOOKS_ITEM_ID,
                title    = "Books",
                subtitle = countLabel(totalBooks, "book", "books"),
                coverUri = MEMORY_CARD_ASSET_URI,
                type     = XMBItemType.MEMORY_CARD,
            )
        )
    }
}

/**
 * A column ends in ONE Add row.
 *
 * A media column can offer two of them at once -- point the library at a folder, and pick
 * apps to show -- and two adjacent rows both starting with "Add" is a menu pretending to be a
 * list. With more than one, they collapse into a single "Add" row that opens the rest as a
 * submenu, which is one more press only for the case that was ambiguous anyway. With one,
 * that row is shown as itself: wrapping a single choice in a menu would be pure ceremony.
 */
internal fun collapseAddRows(rows: List<XMBItem>): List<XMBItem> = when {
    rows.size <= 1 -> rows
    else -> listOf(
        XMBItem(
            id       = ADD_MENU_ITEM_ID,
            title    = "Add",
            subtitle = rows.joinToString("  ·  ") { it.title.removePrefix("Add ") },
            type     = XMBItemType.ADD_ACTION,
        )
    )
}
