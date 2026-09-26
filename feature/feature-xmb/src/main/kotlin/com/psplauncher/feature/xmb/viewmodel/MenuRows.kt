package com.psplauncher.feature.xmb.viewmodel

internal fun menuRows(
    all: List<XMBContextMenuItem>,
    pillIds: Set<String>,
    bundle: Boolean = true,
): List<XMBContextMenuItem> {
    val visible = all.filterNot { it.hidden || it.id in pillIds }
    return if (bundle) visible.withSubmenuRows() else visible.inMenuOrder()
}

fun XMBUiState.menuRows(): List<XMBContextMenuItem> {
    val menu = activeContextMenu ?: return emptyList()
    return menuRows(menu.items, focusedPills().map { it.id }.toSet(), bundle = menu.parent == null)
}
