package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.GamepadAction

// ── What the bottom bar says ──────────────────────────────────────────────────
//
// 12g. The primary action NAMES WHAT IT ACTS ON — "Open All Games", not "Open" — and B sits beside
// it behind a divider, so both ways to move are on the left and everything else is on the right.
//
// Pure, and one function, because the bar's whole job is to be true: a prompt that names an action
// the press does not do is worse than no prompt. Every branch below is a claim about what confirm
// and back will actually do from where the cursor is, and the only way to check a claim like that
// without a device is to be able to call it.

/** One side of the bar: a button, what it does, and — for the primary — what it does it to. */
data class XmbPrompt(val action: GamepadAction, val verb: String, val target: String? = null)

/** The whole bar. [primary] is null where confirm does nothing at all. */
data class XmbPrompts(
    val primary: XmbPrompt?,
    val back: XmbPrompt,
    val right: List<XmbPrompt>,
)

/**
 * What confirm does to [item], as a verb.
 *
 * Null for a row that confirm ignores — the empty-state placeholder every column falls back to
 * when it has nothing in it. A bar that said "Open Nothing here yet" would be naming a press that
 * does nothing, which is the one thing this is for.
 *
 * A game's verb follows DIRECT LAUNCH, because that setting is exactly the question "does confirm
 * start the game or open its page". The launch spine used to read the same flag for the same
 * reason before it became a rail row.
 */
internal fun primaryVerbFor(item: XMBItem?, directLaunch: Boolean): String? = when {
    item == null || item.type == XMBItemType.EMPTY -> null
    item.gameId != null -> if (directLaunch) "Play" else "Details"
    item.type == XMBItemType.MUSIC_TRACK -> "Play"
    item.type == XMBItemType.VIDEO_FILE -> "Play"
    item.type == XMBItemType.LIBRARY_BOOK -> "Read"
    item.type == XMBItemType.PHOTO_FILE -> "View"
    item.type == XMBItemType.ADD_ACTION -> "Add"
    item.type == XMBItemType.SEARCH -> "Search"
    item.packageName != null -> "Launch"
    else -> "Open"
}

/**
 * The bar, for whatever is on screen.
 *
 * Three shapes, in order of what owns the screen:
 *
 *  - the context rail open: confirm runs the focused action and back closes the rail. Nothing on
 *    the right, because everything the right would offer is already IN the rail.
 *  - at the crossbar root: back has nothing to leave, and opens the App Drawer — so it is named
 *    "Apps", after what it does rather than after where it would go.
 *  - drilled in: back goes up a level and says so.
 */
fun promptsFor(state: XMBUiState): XmbPrompts {
    val focused = state.currentItems.getOrNull(state.selectedItemIndex)

    // The notification sheet, which is above everything else and is named first for that reason.
    // Its prompts are per ROW, because the sheet holds two kinds of row: the media row's confirm
    // is a transport and has no dismissal, a notification's confirm opens the app that posted it
    // and Y clears it -- and Y is offered only where clearing actually works, since an ongoing
    // notice refuses silently.
    if (state.notificationsOpen) {
        val focus = state.focusedNotice
        val notice = (focus as? NoticeFocus.Notice)
            ?.let { row -> state.androidNotices.firstOrNull { it.key == row.key } }
        return XmbPrompts(
            primary = when {
                focus == NoticeFocus.Media -> XmbPrompt(
                    GamepadAction.SELECT,
                    if (state.musicPlayback.track != null) {
                        if (state.musicPlayback.isPlaying) "Pause" else "Play"
                    } else "Resume",
                    state.musicPlayback.track?.let { it.title ?: it.displayName } ?: state.resumeGame?.title,
                )
                notice?.canOpen == true -> XmbPrompt(GamepadAction.SELECT, "Open", notice.appLabel)
                else -> null
            },
            back = XmbPrompt(GamepadAction.BACK, "Close"),
            right = buildList {
                if (notice?.canDismiss == true) add(XmbPrompt(GamepadAction.OPEN_CONTEXT_MENU, "Clear"))
            },
        )
    }

    // The rail. Its primary follows the CURSOR, and the cursor can be nowhere: the menu opens
    // with nothing picked so confirm still launches the game it is open over, and the bar has to
    // say that rather than naming a row nobody has moved onto.
    state.activeContextMenu?.let { menu ->
        val row = menu.selectedIndex?.let { state.railRows().getOrNull(it) }
        val primaryVerb = primaryVerbFor(focused, state.directLaunch)
        return XmbPrompts(
            primary = when {
                row != null -> XmbPrompt(GamepadAction.SELECT, "Select", row.label)
                // Nothing picked: the menu's own primary, named after what it does to this row —
                // "Play" for a game, and nothing at all for a menu that has no such verb.
                menu.primaryId != null && primaryVerb != null ->
                    XmbPrompt(GamepadAction.SELECT, primaryVerb, focused?.title)
                else -> null
            },
            back = XmbPrompt(GamepadAction.BACK, "Close"),
            right = emptyList(),
        )
    }

    val right = buildList {
        // Sort and Filter are the same button doing two jobs, so they are mutually exclusive by
        // construction rather than by two callers remembering to be careful.
        when {
            state.canFilterRecents -> add(XmbPrompt(GamepadAction.CHANGE_SORT, "Filter"))
            state.canSortCurrentList -> add(XmbPrompt(GamepadAction.CHANGE_SORT, "Sort"))
        }
        if (state.focusedItemHasContextMenu) add(XmbPrompt(GamepadAction.OPEN_CONTEXT_MENU, "Options"))
        if (!state.isInSubItem) add(XmbPrompt(GamepadAction.OPEN_SEARCH, "Search"))
    }

    return XmbPrompts(
        primary = primaryVerbFor(focused, state.directLaunch)?.let {
            XmbPrompt(GamepadAction.SELECT, it, focused?.title)
        },
        // Back at the root opens the App Drawer. Naming it "Apps" is naming what the press does;
        // naming it "Back" there would be teaching a controller user something untrue.
        back = XmbPrompt(GamepadAction.BACK, if (state.isInSubItem) "Back" else "Apps"),
        right = right,
    )
}
