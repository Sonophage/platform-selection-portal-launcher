package com.psplauncher.feature.xmb.viewmodel

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
