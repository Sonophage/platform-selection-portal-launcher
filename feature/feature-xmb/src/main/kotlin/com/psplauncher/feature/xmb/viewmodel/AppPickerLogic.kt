package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.navigation.NavigationDirection
import com.psplauncher.core.navigation.gridMove

internal fun AppPickerState.visibleApps(): List<AppPickerEntry> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return apps
    return apps.filter { it.label.lowercase().contains(q) }
}

internal fun AppPickerState.toggle(pkg: String): AppPickerState {
    if (apps.none { it.packageName == pkg }) return this
    return copy(selected = if (pkg in selected) selected - pkg else selected + pkg)
}

internal fun AppPickerState.move(action: GamepadAction): AppPickerState {
    val visible = visibleApps()
    val direction = when (action) {
        GamepadAction.NAVIGATE_LEFT -> NavigationDirection.LEFT
        GamepadAction.NAVIGATE_RIGHT -> NavigationDirection.RIGHT
        GamepadAction.NAVIGATE_UP -> NavigationDirection.UP
        GamepadAction.NAVIGATE_DOWN -> NavigationDirection.DOWN
        else -> return this
    }

    val base = if (usingTouch) copy(usingTouch = false) else this

    val next = gridMove(base.focusedIndex, direction, columns = columns.coerceAtLeast(1), size = visible.size)
        ?: return base
    return base.copy(focusedIndex = next)
}

internal fun AppPickerState.clampFocus(): AppPickerState {
    val lastIndex = visibleApps().lastIndex
    val clamped = focusedIndex.coerceIn(0, lastIndex.coerceAtLeast(0))
    return if (clamped == focusedIndex) this else copy(focusedIndex = clamped)
}

internal fun AppPickerState.pendingAdds(): Set<String> = selected - initialSelected

internal fun AppPickerState.pendingRemovals(): Set<String> = initialSelected - selected

internal fun AppPickerState.openConfirm(): AppPickerState =
    copy(confirmingRemovals = true, confirmFocusedOption = AppPickerState.CONFIRM_CANCEL)

internal fun AppPickerState.moveConfirm(action: GamepadAction): AppPickerState {
    if (!confirmingRemovals) return this
    val next = when (action) {
        GamepadAction.NAVIGATE_LEFT  -> AppPickerState.CONFIRM_CANCEL
        GamepadAction.NAVIGATE_RIGHT -> AppPickerState.CONFIRM_REMOVE
        else -> return this
    }
    if (next == confirmFocusedOption) return this
    return copy(confirmFocusedOption = next)
}

internal fun AppPickerState.cancelConfirm(): AppPickerState =
    copy(confirmingRemovals = false, confirmFocusedOption = AppPickerState.CONFIRM_CANCEL)

internal fun categoryShowsApps(category: Category): Boolean =
    !category.isGamingCategory &&
        category.id != BuiltInCategory.RECENTLY_PLAYED &&
        category.id != BuiltInCategory.SETTINGS
