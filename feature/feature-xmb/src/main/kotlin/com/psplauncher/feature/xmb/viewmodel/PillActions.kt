package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.GamepadAction

// ── The pill row, 9i ──────────────────────────────────────────────────────────
//
// A short row of actions under the focused row's meta line. The mock opens them on a press and
// pushes the rows below down; the owner's call was that they stay: "it stays in column always
// visible". So they are drawn whenever the cursor is on a row that has any, and nothing moves
// when you press anything.
//
// The pills do NOT carry their own handlers. Each one is an id the context menu already dispatches
// and already holds the context for — the game id, the category it is being viewed from, the
// memory card it sits on. Activating a pill opens that menu and activates that row, so a pill can
// never do a subtly different thing from the menu entry with the same name, and the day a handler
// changes there is one of it.
//
// That leaves exactly one thing to keep in step: an id here has to be an id the menu offers. It is
// a list and its mirror with only one side written down, which is the pair this codebase keeps
// getting bitten by, so PillActionsTest builds the REAL menus and asserts every id below appears
// in one. A pill whose id has been renamed away does nothing at all when pressed — it is not a
// crash, it is silence, which is the failure nobody reports.
//
// The labels are deliberately NOT the menu's. "Add to Favorites" is a menu row; "Favorite" is a
// pill, and 9i writes them that way. Only the ids are shared.

/** One action under the focused row. [id] is dispatched through the row's own context menu. */
data class XmbPill(val id: String, val label: String)

/**
 * The pills for [item], or empty for a row that has none.
 *
 * Games and Android apps only. Everything else — platform cards, collections, settings rows, the
 * media libraries — gets no row rather than a guessed one: the cost of a wrong pill is a press
 * that silently does nothing, and the actions those rows want are the ones item 13's rail will
 * carry anyway.
 *
 * Capped at four. 9i draws five and its first is Resume, which this build does not need: confirm
 * launches the game already when direct launch is on, and opens Game Detail when it is off.
 */
internal fun pillsFor(item: XMBItem): List<XmbPill> = when {
    item.gameId != null -> buildList {
        add(XmbPill("game_details", "Details"))
        add(if (item.isFavorite) XmbPill("unfavorite", "Unfavorite") else XmbPill("favorite", "Favorite"))
        // 9i's "Open with". Absent for a package-backed row: an Android game launches itself and
        // the game menu offers no emulator to change.
        if (!item.isAndroidApp) add(XmbPill("change_emulator", "Open with"))
        add(XmbPill("add_to_collection", "Collection"))
    }

    // Mirrors the LAST branch of the OPEN_CONTEXT_MENU dispatch, which reaches the app menu only
    // for a row that is not a game, a collection, a platform card or a media row. There is no
    // ANDROID_APP item type — an app row is a STANDARD row carrying a package name.
    item.packageName != null &&
        item.collectionId == null &&
        item.platformId == null &&
        item.type == XMBItemType.STANDARD -> listOf(
        XmbPill("launch", "Launch"),
        XmbPill("edit_app", "Edit"),
        XmbPill("favorite", "Favorite"),
        XmbPill("add_to_collection", "Collection"),
    )

    else -> emptyList()
}

// ── Reaching the pills with a controller ──────────────────────────────────────
//
// Left and right enter the row, from either side: right lands on the first pill, left on the
// last. Walk to the far end and the NEXT press in that direction leaves the row and steps the
// category, in one press.
//
// That last part is the whole rule and it was got wrong first. The recents rail's idiom is that
// the press which falls off the end is SPENT closing the thing — but the rail has another way to
// open, and this row does not: spending the press here means the very next one re-enters the row,
// and the crossbar becomes unreachable from any game or app list forever. The exit has to carry
// the press with it.
//
// The cost is five presses to cross a category from a four-pill row where it used to be one, paid
// only inside game and app lists; rows with no pills pass every press straight through.

/** What a left/right press means while the cursor is on a row that has pills. */
internal sealed interface PillNav {
    /** Enter the row, or move inside it, landing on [index]. */
    data class Move(val index: Int) : PillNav

    /**
     * Fall off the end: clear the pill cursor AND let the column handle this same press.
     *
     * Not "spend the press leaving". See the note above — a spent press here is a dead end.
     */
    data object ExitAndPass : PillNav

    /** Not ours — the column and the crossbar handle it as they always did. */
    data object Pass : PillNav
}

/**
 * Where a press takes the pill cursor. [current] is null when the cursor is still on the row
 * itself, [count] is how many pills that row has.
 *
 * Pure, because the alternative is reading it off a device: most of these branches only happen at
 * an edge, and an edge in a navigation rule is exactly where a press gets swallowed or doubled
 * without anyone being able to say which — or, as here, where a whole screen stops being
 * reachable in a way that only shows up after four presses in a row.
 */
internal fun pillNav(action: GamepadAction, current: Int?, count: Int): PillNav {
    if (count <= 0) return PillNav.Pass
    return when (action) {
        GamepadAction.NAVIGATE_RIGHT -> when {
            current == null -> PillNav.Move(0)
            current < count - 1 -> PillNav.Move(current + 1)
            else -> PillNav.ExitAndPass
        }
        // The mirror: left enters at the LAST pill, so the row behaves the same whichever side you
        // arrive from. Entering from the right only would halve the cost of crossing a category
        // and is a one-line change to the first branch here.
        GamepadAction.NAVIGATE_LEFT -> when {
            current == null -> PillNav.Move(count - 1)
            current > 0 -> PillNav.Move(current - 1)
            else -> PillNav.ExitAndPass
        }
        else -> PillNav.Pass
    }
}

/**
 * Where the pill cursor is, if it is anywhere.
 *
 * It carries the ROW's id, not just an index, so it invalidates itself: move the column cursor and
 * the stored id stops matching the focused row, and [activePillIndex] reads null without anyone
 * having to remember to clear it. The alternative — a bare index reset from every place that moves
 * the selection — is a list of call sites with nothing checking it is complete.
 */
data class PillCursor(val itemId: String, val index: Int)

/** The pills of whatever row the column cursor is on. */
internal fun XMBUiState.focusedPills(): List<XmbPill> =
    currentItems.getOrNull(selectedItemIndex)?.let(::pillsFor).orEmpty()

/**
 * The focused pill's index, or null when the cursor is still on the row itself.
 *
 * Clamped, because a row's pill count changes with the row: Favorite and Unfavorite are one pill,
 * but a package-backed game has no "Open with" and so has three where the one before it had four.
 */
val XMBUiState.focusedPillIndex: Int? get() = activePillIndex()

internal fun XMBUiState.activePillIndex(): Int? {
    val item = currentItems.getOrNull(selectedItemIndex) ?: return null
    val cursor = pillCursor?.takeIf { it.itemId == item.id } ?: return null
    val pills = pillsFor(item)
    if (pills.isEmpty()) return null
    return cursor.index.coerceIn(0, pills.lastIndex)
}
