package com.psplauncher.feature.appbar

import com.psplauncher.core.domain.model.GamepadAction

const val SECTION_LIST_ROWS = 6

fun sectionMove(
    action: GamepadAction,
    index: Int,
    rowCount: Int,
    total: Int,
    listRows: Int = SECTION_LIST_ROWS,
): Int {
    if (total <= 0) return 0
    val cur = index.coerceIn(0, total - 1)
    val restCount = total - rowCount

    if (cur < rowCount) return when (action) {
        GamepadAction.NAVIGATE_LEFT  -> (cur - 1).coerceAtLeast(0)
        GamepadAction.NAVIGATE_RIGHT -> (cur + 1).coerceAtMost(rowCount - 1)
        GamepadAction.NAVIGATE_DOWN  -> if (restCount > 0) rowCount else cur
        else -> cur
    }

    val rows = listRows.coerceAtLeast(1)
    val local = cur - rowCount
    val row = local % rows
    val col = local / rows
    return when (action) {
        GamepadAction.NAVIGATE_UP ->
            if (row > 0) cur - 1 else if (rowCount > 0) 0 else cur
        GamepadAction.NAVIGATE_DOWN ->
            if (row < rows - 1 && local + 1 < restCount) cur + 1 else cur
        GamepadAction.NAVIGATE_LEFT ->
            if (col > 0) cur - rows else cur
        GamepadAction.NAVIGATE_RIGHT -> {
            val nextColumnStart = (col + 1) * rows
            if (nextColumnStart < restCount) rowCount + minOf(local + rows, restCount - 1) else cur
        }
        else -> cur
    }
}
