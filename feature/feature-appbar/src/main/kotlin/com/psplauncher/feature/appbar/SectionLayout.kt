package com.psplauncher.feature.appbar

import com.psplauncher.core.domain.model.GamepadAction

// ── The 8q body: a tab's own apps up top, everything else below ───────────────
//
// Every tab draws this: the apps that match the tab as one large row that scrolls sideways, and
// every app that does NOT match as a compact A-Z list under it, six rows tall, its columns
// running off to the right. There is no All Apps tab and no grid any more — the complement is
// what makes every installed app reachable from every tab.
//
// The two halves come from ONE rule. `AppFilter.matches` decides the top row and its negation
// decides the list, so a tab can never show an app twice or lose one between the two — the same
// reason the drawer's tab counts read from `matches` rather than counting the grid.
//
// The cursor is a single flat index into `visibleApps`, which is the matched apps followed by the
// rest. That is what lets Launch, the Y menu and Add to Cross Bar stay exactly as they were: they
// read `visibleApps[selectedIndex]` and neither knows nor cares which half it came from. Only the
// arithmetic below knows there are two shapes.

/**
 * Rows in the compact list. The mock's `grid-template-rows: repeat(6, 58px)`.
 *
 * It is the fill direction as well as the height: `grid-auto-flow: column` means A-F runs DOWN
 * the first column, not across the top. So a list index is `column * SECTION_LIST_ROWS + row`,
 * and that is the whole reason left/right move by six here and by one in the top row.
 */
const val SECTION_LIST_ROWS = 6

/**
 * Where the cursor lands after [action], as a flat index into the matched-then-rest list.
 *
 * [rowCount] is how many of [total] matched the tab, so `index < rowCount` is "in the top row".
 * Returns [index] unchanged for a move with nowhere to go — the caller compares before playing
 * the scroll sound, so a refused move stays silent.
 *
 * Nothing wraps. A drawer that wraps from the last app back to the first is the same press
 * meaning two different things depending on where you happen to be.
 *
 * **Down from the row enters the list at its first slot, and up from the list's top row returns
 * to the row's first tile.** Deliberately symmetric and deliberately stateless: the two halves
 * have different widths and different item counts, so there is no honest "same column" to land
 * on. Remembering the tile you left would need a second cursor to keep in step with this one.
 */
fun sectionMove(action: GamepadAction, index: Int, rowCount: Int, total: Int): Int {
    if (total <= 0) return 0
    val cur = index.coerceIn(0, total - 1)
    val restCount = total - rowCount

    if (cur < rowCount) return when (action) {
        GamepadAction.NAVIGATE_LEFT  -> (cur - 1).coerceAtLeast(0)
        GamepadAction.NAVIGATE_RIGHT -> (cur + 1).coerceAtMost(rowCount - 1)
        GamepadAction.NAVIGATE_DOWN  -> if (restCount > 0) rowCount else cur
        else -> cur
    }

    val local = cur - rowCount
    val row = local % SECTION_LIST_ROWS
    val col = local / SECTION_LIST_ROWS
    return when (action) {
        GamepadAction.NAVIGATE_UP ->
            if (row > 0) cur - 1 else if (rowCount > 0) 0 else cur
        GamepadAction.NAVIGATE_DOWN ->
            if (row < SECTION_LIST_ROWS - 1 && local + 1 < restCount) cur + 1 else cur
        GamepadAction.NAVIGATE_LEFT ->
            if (col > 0) cur - SECTION_LIST_ROWS else cur
        GamepadAction.NAVIGATE_RIGHT -> {
            // The column to the right may be a short one — the last column holds whatever is
            // left over. Landing on its bottom entry beats refusing the press.
            val nextColumnStart = (col + 1) * SECTION_LIST_ROWS
            if (nextColumnStart < restCount) rowCount + minOf(local + SECTION_LIST_ROWS, restCount - 1) else cur
        }
        else -> cur
    }
}
