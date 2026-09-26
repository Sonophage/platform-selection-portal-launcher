package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.GamepadAction

data class XmbPill(val id: String, val label: String)

internal fun pillsFor(item: XMBItem): List<XmbPill> = when {
    item.gameId != null -> buildList {
        add(if (item.isFavorite) XmbPill("unfavorite", "Unfavorite") else XmbPill("favorite", "Favorite"))

        if (!item.isAndroidApp) add(XmbPill("change_emulator", "Open with"))
        add(XmbPill("add_to_collection", "Collection"))
    }

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

internal sealed interface PillNav {
    data class Move(val index: Int) : PillNav

    data object ExitAndPass : PillNav

    data object Pass : PillNav
}

internal fun pillNav(action: GamepadAction, current: Int?, count: Int): PillNav {
    if (count <= 0) return PillNav.Pass
    return when (action) {
        GamepadAction.NAVIGATE_RIGHT -> when {
            current == null -> PillNav.Move(0)
            current < count - 1 -> PillNav.Move(current + 1)
            else -> PillNav.ExitAndPass
        }

        GamepadAction.NAVIGATE_LEFT -> when {
            current == null -> PillNav.Pass
            current > 0 -> PillNav.Move(current - 1)
            else -> PillNav.ExitAndPass
        }

        GamepadAction.NAVIGATE_DOWN -> if (current == null) PillNav.Move(0) else PillNav.Pass
        else -> PillNav.Pass
    }
}

data class PillCursor(val itemId: String, val index: Int)

val XMBUiState.pillRowVisible: Boolean
    get() = !onLastPlayedHome && focusedPills().isNotEmpty()

val XMBItem.isInstalledApp: Boolean get() = gameId == null && packageName != null

fun XMBUiState.focusedPills(): List<XmbPill> =
    currentItems.getOrNull(selectedItemIndex)?.let(::pillsFor).orEmpty()

val XMBUiState.focusedPillIndex: Int? get() = activePillIndex()

internal fun XMBUiState.activePillIndex(): Int? {
    val item = currentItems.getOrNull(selectedItemIndex) ?: return null
    val cursor = pillCursor?.takeIf { it.itemId == item.id } ?: return null
    val pills = pillsFor(item)
    if (pills.isEmpty()) return null
    return cursor.index.coerceIn(0, pills.lastIndex)
}
