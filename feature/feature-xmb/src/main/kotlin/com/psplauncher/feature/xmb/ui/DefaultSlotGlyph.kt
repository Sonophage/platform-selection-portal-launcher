package com.psplauncher.feature.xmb.ui

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ImportContacts
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.psplauncher.core.ui.icons.ConsoleIcon
import com.psplauncher.core.ui.icons.PortalIcon
import com.psplauncher.core.ui.icons.catbarIconKeyFor
import com.psplauncher.core.ui.icons.categoryIconFor
import com.psplauncher.core.ui.icons.systemIconRes
import com.psplauncher.themekit.IconSlot

internal sealed interface SlotGlyphDefault {
    data class Console(val platformId: String) : SlotGlyphDefault

    data class Drawable(@DrawableRes val resId: Int) : SlotGlyphDefault

    data class BundledAsset(val assetUri: String) : SlotGlyphDefault

    data class Vector(val image: ImageVector) : SlotGlyphDefault

    data object None : SlotGlyphDefault
}

internal fun defaultGlyphFor(slot: IconSlot): SlotGlyphDefault {
    if (slot.group == IconSlot.Group.CONSOLE) {
        return SlotGlyphDefault.Console(slot.key.removePrefix("sysicon_"))
    }

    catbarIconKeyFor(slot.key)?.let { iconKey ->
        return SlotGlyphDefault.Drawable(categoryIconFor(iconKey).resId)
    }

    XmbStatusIcons.forSlotKey(slot.key)?.let { return SlotGlyphDefault.Drawable(it) }
    return when (slot.key) {
        "item_memcard_games", "item_memcard_music", "item_memcard_video", "item_memcard_photos",
        -> SlotGlyphDefault.BundledAsset(MEMORY_CARD_DEFAULT_ART)

        "item_settings" -> SlotGlyphDefault.Drawable(systemIconRes("settings"))
        else -> ITEM_VECTORS[slot.key]?.let { SlotGlyphDefault.Vector(it) } ?: SlotGlyphDefault.None
    }
}

private val ITEM_VECTORS: Map<String, ImageVector> = mapOf(
    "item_add" to Icons.Filled.Add,
    "item_missing" to Icons.AutoMirrored.Filled.HelpOutline,
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
    "item_music_artists" to Icons.Filled.Person,
    "item_music_albums" to Icons.Filled.Album,
    "item_playlist" to Icons.AutoMirrored.Filled.QueueMusic,
)

@Composable
internal fun DefaultSlotGlyph(
    slot: IconSlot,
    contentDescription: String?,
    modifier: Modifier = Modifier,
): Boolean {
    when (val default = defaultGlyphFor(slot)) {
        is SlotGlyphDefault.Console -> ConsoleIcon(
            platformId = default.platformId,
            contentDescription = contentDescription,
            modifier = modifier,
        )
        is SlotGlyphDefault.Drawable -> PortalIcon(
            painter = painterResource(default.resId),
            contentDescription = contentDescription,
            modifier = modifier,
        )
        is SlotGlyphDefault.BundledAsset -> BundledSilhouetteIcon(
            assetUri = default.assetUri,
            modifier = modifier,
        )
        is SlotGlyphDefault.Vector -> PortalIcon(
            painter = rememberVectorPainter(default.image),
            contentDescription = contentDescription,
            modifier = modifier,
        )
        SlotGlyphDefault.None -> return false
    }
    return true
}
