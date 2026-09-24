package com.psplauncher.feature.xmb.viewmodel

// ── The right rail, 9h ────────────────────────────────────────────────────────
//
// What was a panel of labelled rows is a column of icons up the right edge, only the focused one
// showing its name. Same menu, same handlers, same cursor — only the drawing and the length change.
//
// Two rules decide the length, and neither is a second list of actions:
//
//  - the pill row under the focused item already carries its four, so the rail drops those ids.
//    They come from pillsFor, so there is one definition of which four and the rail reads it;
//  - what is left is capped at RAIL_CAPACITY, because the rail is a fixed column on a 1080px
//    screen and cannot scroll.
//
// There is no "More". That was the owner's call — "minimize the options" — and a More row would
// rebuild the long list the rail exists to replace. What falls past the cap is not reachable from
// the rail; it lives in Game Details, Library Manager and Settings.
//
// **Destructive rows are never what gets cut.** They sort last in every builder, so a plain
// take(9) would drop "Remove from Library" and keep "Move to Category" — the cheapest possible way
// to make a menu that cannot remove anything. They are held back and appended.

/**
 * Icons the rail can stack before it runs off the screen.
 *
 * Measured from the design: a 67px circle on a 99px pitch over the usable height between the
 * status strip and the hint bar. Nine is also what CONTEXT_MENU_MAX_ROWS already budgeted for the
 * panel this replaces, for the same reason — a menu you have to scroll hides its own last action.
 */
internal const val RAIL_CAPACITY = 9

/**
 * The rows the rail shows, in order.
 *
 * [all] is the whole menu — `items + overflow`, because the builders have already split long menus
 * around a "More" row and the rail wants the two halves back together before it does its own
 * cutting. Cutting the already-cut half would drop rows the cap had room for.
 *
 * [pillIds] is what the pill row under the focused item carries; those are dropped rather than
 * drawn twice.
 */
internal fun railRows(
    all: List<XMBContextMenuItem>,
    pillIds: Set<String>,
    capacity: Int = RAIL_CAPACITY,
): List<XMBContextMenuItem> {
    val rows = all.filterNot { it.id == MENU_MORE_ITEM_ID || it.id in pillIds }
    if (rows.size <= capacity) return rows
    val destructive = rows.filter { it.isDestructive }
    val rest = rows.filterNot { it.isDestructive }
    return rest.take((capacity - destructive.size).coerceAtLeast(0)) + destructive
}

/** The open menu's rail rows, or empty when no menu is up. The ONE list the cursor indexes into. */
fun XMBUiState.railRows(): List<XMBContextMenuItem> {
    val menu = activeContextMenu ?: return emptyList()
    return railRows(menu.items + menu.overflow, focusedPills().map { it.id }.toSet())
}
