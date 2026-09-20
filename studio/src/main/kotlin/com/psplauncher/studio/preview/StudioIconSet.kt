package com.psplauncher.studio.preview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.ImportContacts
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource

/**
 * The Studio's copy of the launcher's default icon set, keyed by
 * [com.psplauncher.themekit.IconSlots] keys. Category-bar and status glyphs are the
 * same assets the launcher ships (copied under `resources/xmb/`); item glyphs are the same
 * Material vectors the launcher's item rows reference inline.
 */
object StudioIconSet {

    /** Classpath resources for the raster/XML-vector slots. StudioIconSetTest covers keys. */
    internal val RESOURCE_SLOTS: Map<String, String> = mapOf(
        "catbar_games" to "xmb/catbar_games.png",
        "catbar_music" to "xmb/catbar_music.png",
        "catbar_video" to "xmb/catbar_video.png",
        "catbar_photos" to "xmb/catbar_photos.png",
        "catbar_settings" to "xmb/catbar_settings.png",
        "catbar_network" to "xmb/catbar_network.png",
        "catbar_appstore" to "xmb/catbar_appstore.png",
        "catbar_favorites" to "xmb/catbar_favorites.png",
        "catbar_library" to "xmb/catbar_library.xml",
        // Default memory-card art (launcher: systems/physical-media/_default.png) — one
        // asset, four semantic slots so themes can diverge per category.
        "item_memcard_games" to "xmb/item_memcard.png",
        "item_memcard_music" to "xmb/item_memcard.png",
        "item_memcard_video" to "xmb/item_memcard.png",
        "item_memcard_photos" to "xmb/item_memcard.png",
        // Settings rows' wrench badge (launcher: sysicon_settings).
        "item_settings" to "xmb/item_settings.png",
        "status_battery_full" to "xmb/ic_status_battery_full.xml",
        "status_battery_high" to "xmb/ic_status_battery_high.xml",
        "status_battery_medium" to "xmb/ic_status_battery_medium.xml",
        "status_battery_low" to "xmb/ic_status_battery_low.xml",
        "status_battery_charging" to "xmb/ic_status_battery_charging.xml",
        "status_bluetooth" to "xmb/ic_status_bluetooth.xml",
    )

    /**
     * Material glyphs for the item slots — keep in lockstep with the launcher's
     * XMBItemList leading icons (same vector per slot).
     */
    val ITEM_VECTORS: Map<String, ImageVector> = mapOf(
        "item_add" to Icons.Filled.Add,
        "item_missing" to Icons.Filled.HelpOutline,
        "item_video_folder" to Icons.Filled.Folder,
        "item_video_library" to Icons.Filled.VideoLibrary,
        "item_video_recent" to Icons.Filled.History,
        "item_video_favorites" to Icons.Filled.Star,
        "item_video_collections" to Icons.Filled.Bookmarks,
        "item_video_file" to Icons.Filled.Movie,
        "item_photo_folder" to Icons.Filled.Folder,
        "item_photo_file" to Icons.Filled.Photo,
        "item_photo_albums" to Icons.Filled.PhotoLibrary,
        "item_library_shelves" to Icons.Filled.CollectionsBookmark,
        "item_library_reader" to Icons.Filled.ImportContacts,
        "item_library_folder" to Icons.Filled.Folder,
        "item_library_book" to Icons.Filled.Book,
        "item_library_series" to Icons.Filled.Bookmarks,
        "item_camera" to Icons.Filled.PhotoCamera,
        "item_search" to Icons.Filled.Search,
        "item_music_track" to Icons.Filled.MusicNote,
        "item_playlist" to Icons.Filled.QueueMusic,
    )

    /** Default painter for a slot key, or a play-arrow stand-in for unknown keys. */
    @Composable
    fun defaultPainter(key: String): Painter {
        RESOURCE_SLOTS[key]?.let { return painterResource(it) }
        return rememberVectorPainter(ITEM_VECTORS[key] ?: Icons.Filled.PlayArrow)
    }
}
