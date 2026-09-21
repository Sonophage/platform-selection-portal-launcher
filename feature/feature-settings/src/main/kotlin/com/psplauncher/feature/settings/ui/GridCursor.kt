package com.psplauncher.feature.settings.ui

import com.psplauncher.core.domain.model.GamepadAction

// ── Moving a cursor around a grid ─────────────────────────────────────────────
//
// The icon picker is a grid of fifty-odd images with one row above it, and it was the one prompt
// the controller could only escape from rather than use. The arithmetic is separated here because
// every mistake a grid cursor makes is at an edge: falling off the end of a short last row,
// wrapping from the left column to the right of the row above, or stepping up into the header row
// from the middle of the grid rather than only from its first row.

/** The row above the grid -- the picker's "Default" entry. */
const val GRID_CURSOR_HEADER = -1

/**
 * Where a directional press lands (pure — unit-tested).
 *
 * [index] is [GRID_CURSOR_HEADER] for the row above the grid, otherwise a position in it.
 * Movement CLAMPS rather than wraps: a cursor that jumps from the left of one row to the right of
 * the row above reads as a glitch, and the rail beside this prompt clamps too.
 *
 * Returns [index] unchanged for anything that is not a direction, so the caller can pass every
 * action through without deciding first which ones move.
 */
fun gridCursorStep(index: Int, columns: Int, count: Int, action: GamepadAction): Int {
    if (count <= 0 || columns <= 0) return GRID_CURSOR_HEADER
    return when (action) {
        GamepadAction.NAVIGATE_UP -> when {
            index == GRID_CURSOR_HEADER -> GRID_CURSOR_HEADER
            // Only the first row reaches the header. From anywhere else UP is a row up.
            index < columns -> GRID_CURSOR_HEADER
            else -> index - columns
        }
        GamepadAction.NAVIGATE_DOWN -> when {
            index == GRID_CURSOR_HEADER -> 0
            // A short last row is the usual way a grid cursor falls off the end: stepping down
            // from a column the last row does not have must land on its final item, not past it.
            else -> (index + columns).coerceAtMost(count - 1)
        }
        GamepadAction.NAVIGATE_LEFT -> when {
            index == GRID_CURSOR_HEADER -> GRID_CURSOR_HEADER
            index % columns == 0 -> index      // already at the left edge
            else -> index - 1
        }
        GamepadAction.NAVIGATE_RIGHT -> when {
            index == GRID_CURSOR_HEADER -> GRID_CURSOR_HEADER
            index % columns == columns - 1 -> index   // already at the right edge
            else -> (index + 1).coerceAtMost(count - 1)
        }
        else -> index
    }
}
