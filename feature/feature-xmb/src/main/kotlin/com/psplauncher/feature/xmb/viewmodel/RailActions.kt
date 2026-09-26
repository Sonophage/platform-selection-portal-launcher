package com.psplauncher.feature.xmb.viewmodel

internal const val RAIL_CAPACITY = 9

internal fun railRows(
    all: List<XMBContextMenuItem>,
    pillIds: Set<String>,
    capacity: Int = RAIL_CAPACITY,
): List<XMBContextMenuItem> {
    val rows = all.filterNot { it.hidden || it.id == MENU_MORE_ITEM_ID || it.id in pillIds }
    if (rows.size <= capacity) return rows
    val destructive = rows.filter { it.isDestructive }
    val rest = rows.filterNot { it.isDestructive }
    return rest.take((capacity - destructive.size).coerceAtLeast(0)) + destructive
}

fun XMBUiState.railRows(): List<XMBContextMenuItem> {
    val menu = activeContextMenu ?: return emptyList()
    return railRows(menu.items + menu.overflow, focusedPills().map { it.id }.toSet())
}
