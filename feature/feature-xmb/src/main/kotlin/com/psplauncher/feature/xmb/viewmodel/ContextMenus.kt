package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.HideLocationType
import com.psplauncher.core.domain.model.PlatformIds
import com.psplauncher.core.ui.components.MenuGroup

internal fun XMBUiState.currentCategoryOrNull(): Category? =
    categories.getOrNull(selectedCategoryIndex)

internal fun XMBUiState.categoryDisplayNameOf(id: String): String = when (id) {
    XMBViewModel.MUSIC_APPS_CATEGORY_ID -> "Music Apps"
    XMBViewModel.VIDEO_APPS_CATEGORY_ID -> "Video Apps"
    else -> categories.firstOrNull { it.id == id }?.name ?: id
}

internal fun gameContextMenuItems(
    item: XMBItem,
    state: XMBUiState,
    discCount: Int,
    onRecentShelf: Boolean,

    hideLocation: Triple<HideLocationType, String, String>?,
): List<XMBContextMenuItem> {
    val inCollection = state.selectedCollectionId != null
    val currentCat = state.currentCategoryOrNull()
    val inGamingCategory = currentCat?.isGamingCategory == true
    val inMissingBucket = state.selectedPlatformId == XMBViewModel.MISSING_PLATFORM_ID

    return buildList {
        add(XMBContextMenuItem("play", "Play", hidden = true))

        if (discCount > 1) add(XMBContextMenuItem("choose_disc", "Choose Disc"))
        if (item.platformId == PlatformIds.WINDOWS) {
            add(XMBContextMenuItem("export_game", "Export Game", group = MenuGroup.SETTINGS))
        }

        add(
            XMBContextMenuItem(
                action = if (item.isFavorite) "unfavorite" else "favorite",
                label = if (item.isFavorite) "Remove from Favorites" else "Add to Favorites",
                group = MenuGroup.LIBRARY,
                pinnedToRoot = true,
            ),
        )

        add(XMBContextMenuItem("play_state", "Mark As", group = MenuGroup.LIBRARY))
        if (onRecentShelf) add(XMBContextMenuItem("remove_from_recent", "Remove from Recent", group = MenuGroup.LIBRARY))
        add(XMBContextMenuItem("add_to_collection", "Add to Collection", group = MenuGroup.LIBRARY))
        if (inCollection) add(XMBContextMenuItem("remove_from_collection", "Remove from Collection", group = MenuGroup.LIBRARY))
        add(XMBContextMenuItem("manage_collections", "Manage Collections", group = MenuGroup.LIBRARY))

        if (inGamingCategory) {
            val hasOtherCustomCategory = state.categories.any {
                it.isGamingCategory && it.id != BuiltInCategory.GAMES && it.id != currentCat.id
            }

            if (currentCat.id == BuiltInCategory.GAMES) {
                if (hasOtherCustomCategory) add(XMBContextMenuItem("add_category", "Add to Category", group = MenuGroup.CATEGORY))
            } else {
                if (hasOtherCustomCategory) add(XMBContextMenuItem("move_category", "Move to Category", group = MenuGroup.CATEGORY))
                add(XMBContextMenuItem("remove_category", "Remove from Category", group = MenuGroup.CATEGORY))
                val pinned = item.subtitle == "Pinned"
                add(
                    XMBContextMenuItem(
                        if (pinned) "unpin_category" else "pin_category",
                        if (pinned) "Unpin" else "Pin",
                        group = MenuGroup.CATEGORY,
                    ),
                )
            }
        }

        add(XMBContextMenuItem("detail_title", "Edit Title", group = MenuGroup.METADATA))
        add(XMBContextMenuItem("detail_note", "Edit Note", group = MenuGroup.METADATA))
        add(XMBContextMenuItem("detail_ARTWORK", "Artwork", group = MenuGroup.METADATA))
        add(XMBContextMenuItem("detail_METADATA", "Update Metadata", group = MenuGroup.METADATA))
        add(XMBContextMenuItem("detail_MANUAL", "Manual", group = MenuGroup.METADATA))
        add(XMBContextMenuItem("detail_REFRESH", "Refresh Artwork", group = MenuGroup.METADATA))

        if (!item.isAndroidApp) add(XMBContextMenuItem("change_emulator", "Change Emulator", group = MenuGroup.SETTINGS))
        add(XMBContextMenuItem("icon_display", "Icon Display", group = MenuGroup.SETTINGS))
        add(XMBContextMenuItem("file_location", "View File Location", group = MenuGroup.SETTINGS))

        hideLocation?.let { (_, _, label) ->
            add(XMBContextMenuItem("hide_here", "Hide from $label", group = MenuGroup.REMOVE))
        }
        if (inMissingBucket) {
            add(XMBContextMenuItem("remove_missing", "Remove permanently", isDestructive = true, group = MenuGroup.REMOVE))
        } else if (item.platformId == PlatformIds.ANDROID && item.packageName != null && !inCollection) {
            add(XMBContextMenuItem("unmark_game", "Unmark as Game", group = MenuGroup.REMOVE))
            add(XMBContextMenuItem("remove_app", "Remove from Library", isDestructive = true, group = MenuGroup.REMOVE))
        } else if (!inCollection) {
            add(XMBContextMenuItem("remove_game", "Remove from Library", isDestructive = true, group = MenuGroup.REMOVE))
        }
    }
}

internal fun appContextMenuItems(
    state: XMBUiState,
    categoryId: String?,
    onRecentShelf: Boolean,
): List<XMBContextMenuItem> = buildList {
    add(XMBContextMenuItem("launch", "Launch"))

    add(XMBContextMenuItem("mark_game", "Mark as Game", group = MenuGroup.LIBRARY))
    add(XMBContextMenuItem("favorite", "Add to Favorites", group = MenuGroup.LIBRARY, pinnedToRoot = true))
    if (onRecentShelf) add(XMBContextMenuItem("remove_from_recent", "Remove from Recent", group = MenuGroup.LIBRARY))
    add(XMBContextMenuItem("add_to_collection", "Add to Collection", group = MenuGroup.LIBRARY))

    add(XMBContextMenuItem("edit_app", "Edit App Details", group = MenuGroup.SETTINGS))
    add(XMBContextMenuItem("rename", "Rename Shortcut", group = MenuGroup.SETTINGS))

    add(XMBContextMenuItem("move", "Move to Category", group = MenuGroup.CATEGORY))
    add(XMBContextMenuItem("add", "Add to Category", group = MenuGroup.CATEGORY))
    if (categoryId != null) {
        add(XMBContextMenuItem("remove", "Remove from Category", group = MenuGroup.CATEGORY))
        add(XMBContextMenuItem("pin", "Pin to Category", group = MenuGroup.CATEGORY))

        if (!onRecentShelf) {
            add(XMBContextMenuItem("hide_from_category", "Hide from ${state.categoryDisplayNameOf(categoryId)}", group = MenuGroup.CATEGORY))
        }
    }
    add(XMBContextMenuItem("hide_everywhere", "Hide Everywhere", group = MenuGroup.REMOVE))
}

internal fun videoFileContextMenuItems(
    isFavorite: Boolean,
    resumePositionMs: Long,
    hasWatchStamp: Boolean,
    inPlaylist: Boolean,
): List<XMBContextMenuItem> = buildList {
    add(XMBContextMenuItem("video_play", "Play"))
    if (resumePositionMs > 0) add(XMBContextMenuItem("video_resume", "Resume"))
    add(XMBContextMenuItem("video_details", "Details"))

    add(XMBContextMenuItem("video_favorite", if (isFavorite) "Remove from Favorites" else "Add to Favorites", group = MenuGroup.LIBRARY, pinnedToRoot = true))
    add(XMBContextMenuItem("video_add_playlist", "Add to Playlist", group = MenuGroup.LIBRARY))
    if (hasWatchStamp) add(XMBContextMenuItem("video_remove_recent", "Remove from Recent", group = MenuGroup.LIBRARY))

    if (inPlaylist) {
        add(XMBContextMenuItem("video_remove_playlist", "Remove from this Playlist", isDestructive = true, confirms = false, group = MenuGroup.REMOVE))
    }
    add(XMBContextMenuItem("video_remove", "Remove From Library", isDestructive = true, group = MenuGroup.REMOVE))
}

internal fun videoLibraryContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("video_lib_open", "Open"),
    XMBContextMenuItem("video_lib_manage", "Manage in Settings", group = MenuGroup.SETTINGS),
)

internal fun videoPlaylistContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("open_video_playlist", "Open"),
    XMBContextMenuItem("rename_video_playlist", "Rename Playlist", group = MenuGroup.SETTINGS),
    XMBContextMenuItem("delete_video_playlist", "Delete Playlist", isDestructive = true, group = MenuGroup.REMOVE),
)

internal fun photoFileContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("photo_open", "Open"),
    XMBContextMenuItem("photo_set_wallpaper", "Set as Launcher Wallpaper", group = MenuGroup.SETTINGS),
    XMBContextMenuItem("photo_remove", "Remove From Library", isDestructive = true, group = MenuGroup.REMOVE),
)

internal fun photoLibraryContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("photo_lib_open", "Open"),
    XMBContextMenuItem("photo_lib_scan", "Scan Album", group = MenuGroup.SETTINGS),
    XMBContextMenuItem("photo_lib_manage", "Manage in Settings", group = MenuGroup.SETTINGS),
)

internal fun bookContextMenuItems(hasOpenStamp: Boolean): List<XMBContextMenuItem> = buildList {
    add(XMBContextMenuItem("book_open", "Read"))
    if (hasOpenStamp) add(XMBContextMenuItem("book_remove_recent", "Remove from Recent", group = MenuGroup.LIBRARY))
    add(XMBContextMenuItem("book_remove", "Remove From Library", isDestructive = true, group = MenuGroup.REMOVE))
}

internal fun musicTrackContextMenuItems(
    playlistId: Long?,
    hasPlayStamp: Boolean,
): List<XMBContextMenuItem> = buildList {
    add(XMBContextMenuItem("play", "Play"))
    add(XMBContextMenuItem("play_background", "Play in Background"))

    add(XMBContextMenuItem("add_to_playlist", "Add to Playlist", group = MenuGroup.LIBRARY))
    if (hasPlayStamp) add(XMBContextMenuItem("remove_from_recent", "Remove from Recent", group = MenuGroup.LIBRARY))

    if (playlistId != null) {
        add(XMBContextMenuItem("remove_from_playlist", "Remove from this Playlist", isDestructive = true, confirms = false, group = MenuGroup.REMOVE))
    }
    add(XMBContextMenuItem("remove_track", "Remove From Library", isDestructive = true, group = MenuGroup.REMOVE))
}

internal fun playlistRowContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("open_playlist", "Open"),
    XMBContextMenuItem("add_tracks", "Add Tracks", group = MenuGroup.LIBRARY),
    XMBContextMenuItem("rename_playlist", "Rename Playlist", group = MenuGroup.SETTINGS),
    XMBContextMenuItem("delete_playlist", "Delete Playlist", isDestructive = true, group = MenuGroup.REMOVE),
)

internal fun nowPlayingContextMenuItems(isPlaying: Boolean): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("music_playpause", if (isPlaying) "Pause" else "Resume"),
    XMBContextMenuItem("music_close", "Stop and Close"),
)

internal fun platformContextMenuItems(
    platformId: String,
    pinned: Boolean,
    iconDisplayLabel: String,
): List<XMBContextMenuItem> = buildList {
    if (platformId == PlatformIds.ANDROID) add(XMBContextMenuItem("find_games", "Find Games"))
    else add(XMBContextMenuItem("scan_roms", "Scan This Console"))

    if (platformId == PlatformIds.WINDOWS) {
        add(XMBContextMenuItem("import_pc_games", "Import PC Games"))
    }
    add(XMBContextMenuItem("update_metadata", "Update Metadata", group = MenuGroup.SETTINGS))
    add(XMBContextMenuItem("scrape_missing_artwork", "Scrape Missing Artwork", group = MenuGroup.SETTINGS))

    add(XMBContextMenuItem("icon_display_platform", "Icon Display ($iconDisplayLabel)", group = MenuGroup.SETTINGS))
    add(XMBContextMenuItem("library_manager", "Open in Library Manager", group = MenuGroup.SETTINGS))

    if (pinned) add(XMBContextMenuItem("unpin", "Unpin", group = MenuGroup.CATEGORY))
    else add(XMBContextMenuItem("pin", "Pin To Top", group = MenuGroup.CATEGORY))

    add(XMBContextMenuItem("hide", "Hide From Games", group = MenuGroup.REMOVE))

    if (platformId != PlatformIds.WINDOWS) {
        add(XMBContextMenuItem("remove", "Remove Memory Card", isDestructive = true, group = MenuGroup.REMOVE))
    }
}

internal fun allGamesContextMenuItems(iconDisplayLabel: String): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("import_pc_games", "Import PC Games"),
    XMBContextMenuItem("library_manager", "Manage Library", group = MenuGroup.SETTINGS),
    XMBContextMenuItem("icon_display_global", "Icon Display ($iconDisplayLabel)", group = MenuGroup.SETTINGS),
)

internal fun collectionRowContextMenuItems(
    isPinned: Boolean,
    hasOtherCategory: Boolean,
): List<XMBContextMenuItem> = buildList {
    add(XMBContextMenuItem("open_collection", "Open"))

    add(XMBContextMenuItem("manage_collections", "Manage Collections", group = MenuGroup.LIBRARY))
    add(XMBContextMenuItem("rename_collection", "Rename Collection", group = MenuGroup.SETTINGS))

    if (hasOtherCategory) add(XMBContextMenuItem("move_collection_category", "Move to Category", group = MenuGroup.CATEGORY))
    add(
        XMBContextMenuItem(
            if (isPinned) "unpin_collection" else "pin_collection",
            if (isPinned) "Unpin" else "Pin",
            group = MenuGroup.CATEGORY,
        ),
    )

    add(XMBContextMenuItem("delete_collection", "Delete Collection", isDestructive = true, group = MenuGroup.REMOVE))
}
