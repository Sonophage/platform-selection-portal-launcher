package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.HideLocationType
import com.psplauncher.core.domain.model.PlatformIds

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
        add(XMBContextMenuItem("game_details", "Details"))

        add(XMBContextMenuItem("play", "Play", hidden = true))

        if (discCount > 1) add(XMBContextMenuItem("choose_disc", "Choose Disc"))
        if (item.platformId == PlatformIds.WINDOWS) {
            add(XMBContextMenuItem("export_game", "Export Game"))
        }

        add(
            XMBContextMenuItem(
                id = if (item.isFavorite) "unfavorite" else "favorite",
                label = if (item.isFavorite) "Remove from Favorites" else "Add to Favorites",
                heading = "Library",
            ),
        )

        add(XMBContextMenuItem("play_state", "Mark As"))
        if (onRecentShelf) add(XMBContextMenuItem("remove_from_recent", "Remove from Recent"))
        add(XMBContextMenuItem("add_to_collection", "Add to Collection"))
        if (inCollection) add(XMBContextMenuItem("remove_from_collection", "Remove from Collection"))
        add(XMBContextMenuItem("manage_collections", "Manage Collections"))

        if (inGamingCategory) {
            val hasOtherCustomCategory = state.categories.any {
                it.isGamingCategory && it.id != BuiltInCategory.GAMES && it.id != currentCat.id
            }

            var head: String? = "Category"
            fun headOnce(): String? = head.also { head = null }
            if (currentCat.id == BuiltInCategory.GAMES) {
                if (hasOtherCustomCategory) add(XMBContextMenuItem("add_category", "Add to Category", heading = headOnce()))
            } else {
                if (hasOtherCustomCategory) add(XMBContextMenuItem("move_category", "Move to Category", heading = headOnce()))
                add(XMBContextMenuItem("remove_category", "Remove from Category", heading = headOnce()))
                val pinned = item.subtitle == "Pinned"
                add(
                    XMBContextMenuItem(
                        if (pinned) "unpin_category" else "pin_category",
                        if (pinned) "Unpin" else "Pin",
                        heading = headOnce(),
                    ),
                )
            }
        }

        var thisGame: String? = "This Game"
        fun gameHead(): String? = thisGame.also { thisGame = null }
        if (!item.isAndroidApp) add(XMBContextMenuItem("change_emulator", "Change Emulator", heading = gameHead()))
        add(XMBContextMenuItem("icon_display", "Icon Display", heading = gameHead()))
        add(XMBContextMenuItem("file_location", "View File Location", heading = gameHead()))

        var removeHead: String? = "Remove"
        fun removeHead(): String? = removeHead.also { removeHead = null }
        hideLocation?.let { (_, _, label) ->
            add(XMBContextMenuItem("hide_here", "Hide from $label", heading = removeHead()))
        }
        if (inMissingBucket) {
            add(XMBContextMenuItem("remove_missing", "Remove permanently", isDestructive = true, heading = removeHead()))
        } else if (item.platformId == PlatformIds.ANDROID && item.packageName != null && !inCollection) {
            add(XMBContextMenuItem("unmark_game", "Unmark as Game", heading = removeHead()))
            add(XMBContextMenuItem("remove_app", "Remove from Library", isDestructive = true))
        } else if (!inCollection) {
            add(XMBContextMenuItem("remove_game", "Remove from Library", isDestructive = true, heading = removeHead()))
        }
    }
}

internal fun appContextMenuItems(
    state: XMBUiState,
    categoryId: String?,
    onRecentShelf: Boolean,
): List<XMBContextMenuItem> = buildList {
    add(XMBContextMenuItem("launch", "Launch"))
    add(XMBContextMenuItem("edit_app", "Edit App Details"))

    add(XMBContextMenuItem("mark_game", "Mark as Game"))

    add(XMBContextMenuItem("favorite", "Add to Favorites", heading = "Library"))

    if (onRecentShelf) add(XMBContextMenuItem("remove_from_recent", "Remove from Recent"))
    add(XMBContextMenuItem("add_to_collection", "Add to Collection"))
    add(XMBContextMenuItem("move", "Move to Category", heading = "Category"))
    add(XMBContextMenuItem("add", "Add to Category"))
    if (categoryId != null) {
        add(XMBContextMenuItem("remove", "Remove from Category"))
        add(XMBContextMenuItem("pin", "Pin to Category"))

        if (!onRecentShelf) {
            add(XMBContextMenuItem("hide_from_category", "Hide from ${state.categoryDisplayNameOf(categoryId)}"))
        }
    }
    add(XMBContextMenuItem("hide_everywhere", "Hide Everywhere", heading = "Remove"))
    add(XMBContextMenuItem("rename", "Rename Shortcut"))
}

internal fun videoFileContextMenuItems(
    isFavorite: Boolean,
    resumePositionMs: Long,
    hasWatchStamp: Boolean,
    inPlaylist: Boolean,
): List<XMBContextMenuItem> = buildList {
    add(XMBContextMenuItem("video_play", "Play"))
    if (resumePositionMs > 0) add(XMBContextMenuItem("video_resume", "Resume"))
    add(XMBContextMenuItem("video_favorite", if (isFavorite) "Remove from Favorites" else "Add to Favorites"))
    add(XMBContextMenuItem("video_add_playlist", "Add to Playlist"))
    if (inPlaylist) {
        add(XMBContextMenuItem("video_remove_playlist", "Remove from this Playlist", isDestructive = true))
    }
    add(XMBContextMenuItem("video_details", "Details"))

    if (hasWatchStamp) add(XMBContextMenuItem("video_remove_recent", "Remove from Recent"))
    add(XMBContextMenuItem("video_remove", "Remove From Library", isDestructive = true))
}

internal fun videoLibraryContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("video_lib_open", "Open"),
    XMBContextMenuItem("video_lib_manage", "Manage in Settings"),
)

internal fun videoPlaylistContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("open_video_playlist", "Open"),
    XMBContextMenuItem("rename_video_playlist", "Rename Playlist"),
    XMBContextMenuItem("delete_video_playlist", "Delete Playlist", isDestructive = true),
)

internal fun photoFileContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("photo_open", "Open"),
    XMBContextMenuItem("photo_set_wallpaper", "Set as Launcher Wallpaper"),
    XMBContextMenuItem("photo_remove", "Remove From Library", isDestructive = true),
)

internal fun photoLibraryContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("photo_lib_open", "Open"),
    XMBContextMenuItem("photo_lib_scan", "Scan Album"),
    XMBContextMenuItem("photo_lib_manage", "Manage in Settings"),
)

internal fun bookContextMenuItems(hasOpenStamp: Boolean): List<XMBContextMenuItem> = buildList {
    add(XMBContextMenuItem("book_open", "Read"))
    if (hasOpenStamp) add(XMBContextMenuItem("book_remove_recent", "Remove from Recent"))
    add(XMBContextMenuItem("book_remove", "Remove From Library", isDestructive = true))
}

internal fun musicTrackContextMenuItems(
    playlistId: Long?,
    hasPlayStamp: Boolean,
): List<XMBContextMenuItem> = buildList {
    add(XMBContextMenuItem("play", "Play"))
    add(XMBContextMenuItem("play_background", "Play in Background"))
    add(XMBContextMenuItem("add_to_playlist", "Add to Playlist"))
    if (playlistId != null) {
        add(XMBContextMenuItem("remove_from_playlist", "Remove from this Playlist", isDestructive = true))
    }
    if (hasPlayStamp) add(XMBContextMenuItem("remove_from_recent", "Remove from Recent"))
    add(XMBContextMenuItem("remove_track", "Remove From Library", isDestructive = true))
}

internal fun playlistRowContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("open_playlist", "Open"),
    XMBContextMenuItem("add_tracks", "Add Tracks"),
    XMBContextMenuItem("rename_playlist", "Rename Playlist"),
    XMBContextMenuItem("delete_playlist", "Delete Playlist", isDestructive = true),
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
    add(XMBContextMenuItem("update_metadata", "Update Metadata"))
    add(XMBContextMenuItem("scrape_missing_artwork", "Scrape Missing Artwork"))

    add(XMBContextMenuItem("icon_display_platform", "Icon Display ($iconDisplayLabel)"))
    if (pinned) add(XMBContextMenuItem("unpin", "Unpin")) else add(XMBContextMenuItem("pin", "Pin To Top"))
    add(XMBContextMenuItem("library_manager", "Open in Library Manager"))
    add(XMBContextMenuItem("hide", "Hide From Games"))

    if (platformId != PlatformIds.WINDOWS) {
        add(XMBContextMenuItem("remove", "Remove Memory Card", isDestructive = true))
    }
}

internal fun allGamesContextMenuItems(iconDisplayLabel: String): List<XMBContextMenuItem> = listOf(

    XMBContextMenuItem("library_manager", "Manage Library"),
    XMBContextMenuItem("import_pc_games", "Import PC Games"),
    XMBContextMenuItem("icon_display_global", "Icon Display ($iconDisplayLabel)"),
)

internal fun collectionRowContextMenuItems(
    isPinned: Boolean,
    hasOtherCategory: Boolean,
): List<XMBContextMenuItem> = buildList {
    add(XMBContextMenuItem("open_collection", "Open"))
    add(XMBContextMenuItem("rename_collection", "Rename Collection"))
    if (hasOtherCategory) add(XMBContextMenuItem("move_collection_category", "Move to Category"))
    add(
        XMBContextMenuItem(
            if (isPinned) "unpin_collection" else "pin_collection",
            if (isPinned) "Unpin" else "Pin",
        ),
    )
    add(XMBContextMenuItem("manage_collections", "Manage Collections"))
    add(XMBContextMenuItem("delete_collection", "Delete Collection", isDestructive = true))
}

internal const val CONTEXT_MENU_MAX_ROWS = 9

internal const val MENU_MORE_ITEM_ID = "menu_more"

internal fun List<XMBContextMenuItem>.splitForOverflow(
    limit: Int = CONTEXT_MENU_MAX_ROWS,
): Pair<List<XMBContextMenuItem>, List<XMBContextMenuItem>> {
    if (size <= limit) return this to emptyList()

    val room = limit - 1
    val boundary = (room downTo 1).firstOrNull { this[it].heading != null } ?: room
    return take(boundary) to drop(boundary)
}

internal fun List<XMBContextMenuItem>.withOverflowRow(
    limit: Int = CONTEXT_MENU_MAX_ROWS,
): List<XMBContextMenuItem> {
    val (visible, overflow) = splitForOverflow(limit)
    return if (overflow.isEmpty()) visible
    else visible + XMBContextMenuItem(MENU_MORE_ITEM_ID, "More…")
}

internal fun removeGameConfirmItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("cancel_remove_game", "Cancel"),
    XMBContextMenuItem("confirm_remove_game", "Remove", isDestructive = true),
)

internal fun removeMissingConfirmItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("cancel_remove_missing", "Cancel"),
    XMBContextMenuItem("confirm_remove_missing", "Remove permanently", isDestructive = true),
)
