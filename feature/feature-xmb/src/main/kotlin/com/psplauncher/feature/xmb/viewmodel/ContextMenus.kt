package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.HideLocationType

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
        // Multi-disc sets: pick which disc to boot — the only way to reach a non-primary disc when
        // direct launch skips Game Detail's picker. Launches the chosen disc.
        if (discCount > 1) add(XMBContextMenuItem("choose_disc", "Choose Disc"))
        if (item.platformId == XMBViewModel.WINDOWS_PLATFORM_ID) {
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
            ),
        )
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
            if (currentCat.id == BuiltInCategory.GAMES) {
                if (hasOtherCustomCategory) add(XMBContextMenuItem("add_category", "Add to Category"))
            } else {
                if (hasOtherCustomCategory) add(XMBContextMenuItem("move_category", "Move to Category"))
                add(XMBContextMenuItem("remove_category", "Remove from Category"))
                val pinned = item.subtitle == "Pinned"
                add(
                    XMBContextMenuItem(
                        if (pinned) "unpin_category" else "pin_category",
                        if (pinned) "Unpin" else "Pin",
                    ),
                )
            }
        }

        // Emulator choice only applies to ROM-backed games; package-backed gaming apps launch via
        // their package/shortcut handle.
        if (!item.isAndroidApp) add(XMBContextMenuItem("change_emulator", "Change Emulator"))
        add(XMBContextMenuItem("icon_display", "Icon Display"))
        add(XMBContextMenuItem("file_location", "View File Location"))
        // Per-location hide for the spot this game is shown in (recoverable in Hidden Items).
        hideLocation?.let { (_, _, label) -> add(XMBContextMenuItem("hide_here", "Hide from $label")) }
        if (inMissingBucket) {
            // The only destructive action anywhere in the missing-ROM flow. Mechanically identical
            // to "Remove from Library" (delete row, file untouched), but labelled for what it means
            // here: this bucket is the entry's last visible trace, so removing it ends the line
            // rather than dropping it from one view. Everything else is recoverable by putting the
            // file back.
            add(XMBContextMenuItem("remove_missing", "Remove permanently", isDestructive = true))
        } else if (item.platformId == XMBViewModel.ANDROID_PLATFORM_ID && item.packageName != null && !inCollection) {
            add(XMBContextMenuItem("unmark_game", "Unmark as Game"))
            add(XMBContextMenuItem("remove_app", "Remove from Library", isDestructive = true))
        } else if (!inCollection) {
            // Every other game gets full delete too (confirmed first). Deleting a scanned ROM entry
            // leaves the file untouched — the next scan re-discovers it.
            add(XMBContextMenuItem("remove_game", "Remove from Library", isDestructive = true))
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
    add(XMBContextMenuItem("favorite", "Add to Favorites"))
    add(XMBContextMenuItem("add_to_collection", "Add to Collection"))
    add(XMBContextMenuItem("move", "Move to Category"))
    add(XMBContextMenuItem("add", "Add to Category"))
    if (categoryId != null) {
        add(XMBContextMenuItem("remove", "Remove from Category"))
        add(XMBContextMenuItem("pin", "Pin to Category"))
        // Per-location hide (recoverable in Settings ▸ Hidden Items).
        add(XMBContextMenuItem("hide_from_category", "Hide from ${state.categoryDisplayNameOf(categoryId)}"))
    }
    add(XMBContextMenuItem("hide_everywhere", "Hide Everywhere"))
    add(XMBContextMenuItem("rename", "Rename Shortcut"))
}
