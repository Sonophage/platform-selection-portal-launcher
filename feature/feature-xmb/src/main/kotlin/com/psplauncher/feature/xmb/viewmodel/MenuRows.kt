package com.psplauncher.feature.xmb.viewmodel

internal fun menuRows(
    all: List<XMBContextMenuItem>,
    pillIds: Set<String>,
): List<XMBContextMenuItem> =
    all.filterNot { it.hidden || it.id in pillIds }.inMenuOrder()

fun XMBUiState.menuRows(): List<XMBContextMenuItem> {
    val menu = activeContextMenu ?: return emptyList()
    return menuRows(menu.items, focusedPills().map { it.id }.toSet())
}
