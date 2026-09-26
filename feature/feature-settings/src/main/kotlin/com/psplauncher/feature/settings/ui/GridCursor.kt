package com.psplauncher.feature.settings.ui

import com.psplauncher.core.domain.model.GamepadAction

const val GRID_CURSOR_HEADER = -1

fun gridCursorStep(index: Int, columns: Int, count: Int, action: GamepadAction): Int {
    if (count <= 0 || columns <= 0) return GRID_CURSOR_HEADER
    return when (action) {
        GamepadAction.NAVIGATE_UP -> when {
            index == GRID_CURSOR_HEADER -> GRID_CURSOR_HEADER

            index < columns -> GRID_CURSOR_HEADER
            else -> index - columns
        }
        GamepadAction.NAVIGATE_DOWN -> when {
            index == GRID_CURSOR_HEADER -> 0

            else -> (index + columns).coerceAtMost(count - 1)
        }
        GamepadAction.NAVIGATE_LEFT -> when {
            index == GRID_CURSOR_HEADER -> GRID_CURSOR_HEADER
            index % columns == 0 -> index
            else -> index - 1
        }
        GamepadAction.NAVIGATE_RIGHT -> when {
            index == GRID_CURSOR_HEADER -> GRID_CURSOR_HEADER
            index % columns == columns - 1 -> index
            else -> (index + 1).coerceAtMost(count - 1)
        }
        else -> index
    }
}
