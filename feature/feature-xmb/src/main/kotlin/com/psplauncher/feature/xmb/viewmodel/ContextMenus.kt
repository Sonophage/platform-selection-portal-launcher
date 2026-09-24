package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.HideLocationType
import com.psplauncher.core.domain.model.PlatformIds

/**
 * What every context menu contains, as pure functions of the state that was on screen when it
 * opened.
 *
 * They lived in XMBViewModel, which is 9,400 lines and impossible to construct in a unit test —
 * so the menus, which are almost entirely conditional logic, had no tests at all. Nothing asserted
 * that a multi-disc game offers Choose Disc, or that the Missing bucket offers exactly one
 * destructive action. RecentsShelf.kt is the precedent: a pure function, an exhaustive `when`, its
 * own test, and the ViewModel just calls it.
 *
 * Each returns the ROWS. Raising the menu — writing activeContextMenu, carrying the ids the
 * handlers parse back out — stays in the ViewModel, because that is state and this is content.
 */

// ── State the builders read ───────────────────────────────────────────────────
//
// Extensions on XMBUiState rather than arguments: they were private helpers on the ViewModel, and
// duplicating them here would be the list-and-its-mirror problem the shelf's KDoc warns about. The
// ViewModel's own helpers delegate to these, so there is one definition.

/** The category the crossbar is on, or null before the bar has loaded. */
internal fun XMBUiState.currentCategoryOrNull(): Category? =
    categories.getOrNull(selectedCategoryIndex)

/** What a category is called on screen — for menu labels like "Hide from Music Apps". */
internal fun XMBUiState.categoryDisplayNameOf(id: String): String = when (id) {
    XMBViewModel.MUSIC_APPS_CATEGORY_ID -> "Music Apps"
    XMBViewModel.VIDEO_APPS_CATEGORY_ID -> "Video Apps"
    else -> categories.firstOrNull { it.id == id }?.name ?: id
}

// ── The menus ─────────────────────────────────────────────────────────────────

/**
 * A game's options.
 *
 * The longest menu in the app and the most conditional: it gates on disc count, collection
 * membership, which kind of category it is being viewed from, whether the entry is an Android app,
 * the missing-ROM bucket, and whether this spot can be hidden.
 *
 * [onRecentShelf] is passed rather than derived because only the caller knows whether the row came
 * off the home shelf — the same game reached from Games has no "Remove from Recent" to offer.
 */
internal fun gameContextMenuItems(
    item: XMBItem,
    state: XMBUiState,
    discCount: Int,
    onRecentShelf: Boolean,
    /**
     * Where this row is being shown, for "Hide from here" — (kind, id, label), or null where
     * hiding does not apply.
     *
     * Passed in, not derived. Its last branch names the Memory Card the game is on, and the card
     * list is a ViewModel field rather than ui state — I tried to reimplement this as a state
     * extension and silently dropped two of its branches, which would have offered "Hide from
     * here" on the wrong views. A parameter cannot be half-copied.
     */
    hideLocation: Triple<HideLocationType, String, String>?,
): List<XMBContextMenuItem> {
    val inCollection = state.selectedCollectionId != null
    val currentCat = state.currentCategoryOrNull()
    val inGamingCategory = currentCat?.isGamingCategory == true
    val inMissingBucket = state.selectedPlatformId == XMBViewModel.MISSING_PLATFORM_ID

    return buildList {
        // The explicit path to the edit surface, essential when direct launch makes confirm skip
        // straight into the game. Launch/title/note/scrape actions all live in Game Detail — the
        // menu stays navigational.
        add(XMBContextMenuItem("game_details", "View Game Details"))
        // PLAY IS IN EVERY GAME MENU AND DRAWN IN NONE OF THEM.
        //
        // It used to appear when direct launch was off, and on the recents shelf either way, and
        // the reasoning each time was about whether confirm already did it. Confirm always does it
        // now — the rail no longer takes A away from the game it is open over — so a visible row
        // is a second way to do what the button under your thumb is doing, on every surface.
        //
        // It stays in the menu, hidden, because that makes it the one definition of Play: the rail
        // cannot reach it, and confirm-with-nothing-picked dispatches "play" by id and runs this
        // entry's handler. Deleting it would mean a second path to the same verb.
        add(XMBContextMenuItem("play", "Play", hidden = true))
        // Multi-disc sets: pick which disc to boot — the only way to reach a non-primary disc when
        // direct launch skips Game Detail's picker. Launches the chosen disc.
        if (discCount > 1) add(XMBContextMenuItem("choose_disc", "Choose Disc"))
        if (item.platformId == PlatformIds.WINDOWS) {
            // Writes this game's .pfpgame file so a fresh install can bring it back with its
            // artwork. Offered on every PC game; the exporter explains a refusal.
            add(XMBContextMenuItem("export_game", "Export Game"))
        }
        // No "Edit App Details" here: package-backed GAME entries (PC shortcuts, Android gaming
        // apps) are games — art/title/note editing lives in Game Detail and the game rows below,
        // never the slim standard-app editor.
        add(
            XMBContextMenuItem(
                id = if (item.isFavorite) "unfavorite" else "favorite",
                label = if (item.isFavorite) "Remove from Favorites" else "Add to Favorites",
                heading = "Library",
            ),
        )
        // Beside Favorite, because it is the same kind of thing: something you say about a game
        // rather than something the launcher worked out. A submenu, not a toggle -- four states
        // do not fit on one row, and a row that cycled them would need you to read it to know
        // where you had got to.
        add(XMBContextMenuItem("play_state", "Mark As"))
        if (onRecentShelf) add(XMBContextMenuItem("remove_from_recent", "Remove from Recent"))
        add(XMBContextMenuItem("add_to_collection", "Add to Collection"))
        if (inCollection) add(XMBContextMenuItem("remove_from_collection", "Remove from Collection"))
        add(XMBContextMenuItem("manage_collections", "Manage Collections"))

        // Gaming category options. Games in the Main Game category can only be COPIED into another
        // category (never moved out or removed); custom gaming categories allow move / remove /
        // pin. Move/Add only appear when a real destination exists — a custom gaming category
        // other than the current one (Main Game is never a target).
        if (inGamingCategory) {
            val hasOtherCustomCategory = state.categories.any {
                it.isGamingCategory && it.id != BuiltInCategory.GAMES && it.id != currentCat.id
            }
            // First row of the group carries the heading, whichever one it turns out to be:
            // which rows exist depends on the category, and a heading hard-coded onto one of
            // them would vanish with it.
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

        // Emulator choice only applies to ROM-backed games; package-backed gaming apps launch via
        // their package/shortcut handle.
        var thisGame: String? = "This Game"
        fun gameHead(): String? = thisGame.also { thisGame = null }
        if (!item.isAndroidApp) add(XMBContextMenuItem("change_emulator", "Change Emulator", heading = gameHead()))
        add(XMBContextMenuItem("icon_display", "Icon Display", heading = gameHead()))
        add(XMBContextMenuItem("file_location", "View File Location", heading = gameHead()))
        // Per-location hide for the spot this game is shown in (recoverable in Hidden Items).
        var removeHead: String? = "Remove"
        fun removeHead(): String? = removeHead.also { removeHead = null }
        hideLocation?.let { (_, _, label) ->
            add(XMBContextMenuItem("hide_here", "Hide from $label", heading = removeHead()))
        }
        if (inMissingBucket) {
            // The only destructive action anywhere in the missing-ROM flow. Mechanically identical
            // to "Remove from Library" (delete row, file untouched), but labelled for what it means
            // here: this bucket is the entry's last visible trace, so removing it ends the line
            // rather than dropping it from one view. Everything else is recoverable by putting the
            // file back.
            add(XMBContextMenuItem("remove_missing", "Remove permanently", isDestructive = true, heading = removeHead()))
        } else if (item.platformId == PlatformIds.ANDROID && item.packageName != null && !inCollection) {
            add(XMBContextMenuItem("unmark_game", "Unmark as Game", heading = removeHead()))
            add(XMBContextMenuItem("remove_app", "Remove from Library", isDestructive = true))
        } else if (!inCollection) {
            // Every other game gets full delete too (confirmed first). Deleting a scanned ROM entry
            // leaves the file untouched — the next scan re-discovers it.
            add(XMBContextMenuItem("remove_game", "Remove from Library", isDestructive = true, heading = removeHead()))
        }
    }
}

/**
 * A plain Android app's options.
 *
 * [categoryId] is the category the app is being acted on from — null when the app is not being
 * shown inside one, which removes every per-category row at once.
 */
internal fun appContextMenuItems(
    state: XMBUiState,
    categoryId: String?,
): List<XMBContextMenuItem> = buildList {
    add(XMBContextMenuItem("launch", "Launch"))
    add(XMBContextMenuItem("edit_app", "Edit App Details"))
    // Promotes the app into the Android Memory Card as a real game.
    add(XMBContextMenuItem("mark_game", "Mark as Game"))
    // Shortcut actions — these materialize a launch shortcut (a games-table row referencing the
    // app by package) so it can live in Favorites / Collections without duplicating the app's
    // metadata. Works for every Android app, GameHub included.
    add(XMBContextMenuItem("favorite", "Add to Favorites", heading = "Library"))
    add(XMBContextMenuItem("add_to_collection", "Add to Collection"))
    add(XMBContextMenuItem("move", "Move to Category", heading = "Category"))
    add(XMBContextMenuItem("add", "Add to Category"))
    if (categoryId != null) {
        add(XMBContextMenuItem("remove", "Remove from Category"))
        add(XMBContextMenuItem("pin", "Pin to Category"))
        // Per-location hide (recoverable in Settings ▸ Hidden Items).
        add(XMBContextMenuItem("hide_from_category", "Hide from ${state.categoryDisplayNameOf(categoryId)}"))
    }
    add(XMBContextMenuItem("hide_everywhere", "Hide Everywhere", heading = "Remove"))
    add(XMBContextMenuItem("rename", "Rename Shortcut"))
}

// ── Video ─────────────────────────────────────────────────────────────────────

/**
 * A single video file's options.
 *
 * Takes the [com.psplauncher.core.domain.model.Video] rather than an id because three of its rows
 * are decided by the record: a resume point, a favourite flag, and a watch stamp. The ViewModel
 * fetches it first for exactly that reason.
 */
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
    // Only for a video that has actually been watched, so the row never appears on one that has
    // never been opened. Same rule as the game, book and track menus.
    if (hasWatchStamp) add(XMBContextMenuItem("video_remove_recent", "Remove from Recent"))
    add(XMBContextMenuItem("video_remove", "Remove From Library", isDestructive = true))
}

/** A video library card: open, or go and manage it. */
internal fun videoLibraryContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("video_lib_open", "Open"),
    XMBContextMenuItem("video_lib_manage", "Manage in Settings"),
)

/** A video playlist row. */
internal fun videoPlaylistContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("open_video_playlist", "Open"),
    XMBContextMenuItem("rename_video_playlist", "Rename Playlist"),
    XMBContextMenuItem("delete_video_playlist", "Delete Playlist", isDestructive = true),
)

// ── Photo ─────────────────────────────────────────────────────────────────────

/**
 * A photo row.
 *
 * Viewing actions (zoom, rotate) live in the fullscreen viewer's own menu; the list row only opens,
 * sets a wallpaper, or removes.
 */
internal fun photoFileContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("photo_open", "Open"),
    XMBContextMenuItem("photo_set_wallpaper", "Set as Launcher Wallpaper"),
    XMBContextMenuItem("photo_remove", "Remove From Library", isDestructive = true),
)

/** An album card. */
internal fun photoLibraryContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("photo_lib_open", "Open"),
    XMBContextMenuItem("photo_lib_scan", "Scan Album"),
    XMBContextMenuItem("photo_lib_manage", "Manage in Settings"),
)

// ── Books ─────────────────────────────────────────────────────────────────────

/** A book. [hasOpenStamp] is what decides whether it is on the recents shelf to be taken off. */
internal fun bookContextMenuItems(hasOpenStamp: Boolean): List<XMBContextMenuItem> = buildList {
    add(XMBContextMenuItem("book_open", "Read"))
    if (hasOpenStamp) add(XMBContextMenuItem("book_remove_recent", "Remove from Recent"))
    add(XMBContextMenuItem("book_remove", "Remove From Library", isDestructive = true))
}

// ── Music ─────────────────────────────────────────────────────────────────────

/**
 * A track. [playlistId] non-null means the track is being seen from inside a playlist, which is
 * the only place it can be removed from one.
 */
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

/** A music playlist row. */
internal fun playlistRowContextMenuItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("open_playlist", "Open"),
    XMBContextMenuItem("add_tracks", "Add Tracks"),
    XMBContextMenuItem("rename_playlist", "Rename Playlist"),
    XMBContextMenuItem("delete_playlist", "Delete Playlist", isDestructive = true),
)

/** The player's own menu, while something is playing. */
internal fun nowPlayingContextMenuItems(isPlaying: Boolean): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("music_playpause", if (isPlaying) "Pause" else "Resume"),
    XMBContextMenuItem("music_close", "Stop and Close"),
)

// ── Cards ─────────────────────────────────────────────────────────────────────

/**
 * A Memory Card's options.
 *
 * [iconDisplayLabel] is rendered into a row label, so it is passed already resolved — the card's
 * own override or the global mode it is following, which is a ViewModel lookup.
 */
internal fun platformContextMenuItems(
    platformId: String,
    pinned: Boolean,
    iconDisplayLabel: String,
): List<XMBContextMenuItem> = buildList {
    // Android libraries pick installed apps; consoles scan ROM folders.
    if (platformId == PlatformIds.ANDROID) add(XMBContextMenuItem("find_games", "Find Games"))
    else add(XMBContextMenuItem("scan_roms", "Scan This Console"))
    // The Windows card is import-driven — surface its Import PC Games section here too.
    if (platformId == PlatformIds.WINDOWS) {
        add(XMBContextMenuItem("import_pc_games", "Import PC Games"))
    }
    add(XMBContextMenuItem("update_metadata", "Update Metadata"))
    add(XMBContextMenuItem("scrape_missing_artwork", "Scrape Missing Artwork"))
    // Icon display for THIS console only. Games on other Memory Cards are untouched; "Use Global
    // Setting" here clears the console's override.
    add(XMBContextMenuItem("icon_display_platform", "Icon Display ($iconDisplayLabel)"))
    if (pinned) add(XMBContextMenuItem("unpin", "Unpin")) else add(XMBContextMenuItem("pin", "Pin To Top"))
    add(XMBContextMenuItem("library_manager", "Open in Library Manager"))
    add(XMBContextMenuItem("hide", "Hide From Games"))
    // The Windows Memory Card is managed by the PC import system and cannot be removed.
    if (platformId != PlatformIds.WINDOWS) {
        add(XMBContextMenuItem("remove", "Remove Memory Card", isDestructive = true))
    }
}

/** The All Games card is not a real Memory Card, so it gets its own slim menu. */
internal fun allGamesContextMenuItems(iconDisplayLabel: String): List<XMBContextMenuItem> = listOf(
    // Scanning (missing-ROM pass, full re-scan) lives in the Library settings.
    XMBContextMenuItem("library_manager", "Manage Library"),
    XMBContextMenuItem("import_pc_games", "Import PC Games"),
    XMBContextMenuItem("icon_display_global", "Icon Display ($iconDisplayLabel)"),
)

/**
 * A collection row. [hasOtherCategory] is whether anywhere valid exists to move it to — game
 * collections move between gaming categories, app collections between app ones.
 */
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

// ── Overflow ──────────────────────────────────────────────────────────────────

/**
 * How many rows a context menu shows before the rest go behind "More".
 *
 * Nine, because the panel holds about that many without scrolling on this screen, and a menu you
 * have to scroll is one where the last action is invisible until you go looking for it. The
 * game menu reaches fourteen.
 */
internal const val CONTEXT_MENU_MAX_ROWS = 9

/** The row that opens the overflow. Its id is what the ViewModel matches to swap the lists. */
internal const val MENU_MORE_ITEM_ID = "menu_more"

/**
 * Split a menu into what it shows and what goes behind "More".
 *
 * Splits on a GROUP boundary wherever one is available inside the budget, so a heading never
 * ends up stranded above the More row with its rows on the other side of it. With no boundary
 * to use -- a long first group -- it splits on the budget and the tail simply starts without a
 * heading, which the submenu's own title covers.
 *
 * Returns the whole menu and an empty tail when it already fits: a "More" row holding one
 * action is a worse menu than one extra row.
 */
internal fun List<XMBContextMenuItem>.splitForOverflow(
    limit: Int = CONTEXT_MENU_MAX_ROWS,
): Pair<List<XMBContextMenuItem>, List<XMBContextMenuItem>> {
    if (size <= limit) return this to emptyList()
    // One row of the budget is spent on "More" itself.
    val room = limit - 1
    val boundary = (room downTo 1).firstOrNull { this[it].heading != null } ?: room
    return take(boundary) to drop(boundary)
}

/** [splitForOverflow]'s visible half, with the More row appended when there is a tail. */
internal fun List<XMBContextMenuItem>.withOverflowRow(
    limit: Int = CONTEXT_MENU_MAX_ROWS,
): List<XMBContextMenuItem> {
    val (visible, overflow) = splitForOverflow(limit)
    return if (overflow.isEmpty()) visible
    else visible + XMBContextMenuItem(MENU_MORE_ITEM_ID, "More…")
}

// ── Destructive confirms ─────────────────────────────────────────────────────
//
// Both of these opened with the cursor on their destructive answer. The rail's Remove From Library
// is reached by holding DOWN to the bottom and pressing A, and the prompt that came up answered a
// second A with yes — the press that gets you there is the press most likely to arrive again. The
// App Drawer's uninstall prompt was fixed for exactly this.
//
// Here rather than inline in the ViewModel because "Cancel is first" is a rule with more than one
// instance, and a rule with instances and no test is a rule that holds until someone adds a third.

/** The two-step confirm for removing a game from the library. Cancel first, always. */
internal fun removeGameConfirmItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("cancel_remove_game", "Cancel"),
    XMBContextMenuItem("confirm_remove_game", "Remove", isDestructive = true),
)

/**
 * The same, for a game whose file is already gone.
 *
 * Its copy states the consequence rather than the action: there is no putting this one back, so
 * "Remove permanently" is the honest label and Cancel is still what the cursor lands on.
 */
internal fun removeMissingConfirmItems(): List<XMBContextMenuItem> = listOf(
    XMBContextMenuItem("cancel_remove_missing", "Cancel"),
    XMBContextMenuItem("confirm_remove_missing", "Remove permanently", isDestructive = true),
)
