package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.navigation.NavigationDirection
import com.psplauncher.core.navigation.gridMove

// ── Installed-app picker: pure logic ──────────────────────────────────────────
//
// The picker's selection / search / grid-navigation / diff rules, extracted from XMBViewModel
// so they are unit-testable without a ViewModel or coroutines (same shape as the testable
// predicates shouldShowContextMenuHint / shouldShowAppDrawerHint). XMBViewModel stays the
// owner: it calls these against its `appPicker` state field.
//
// The rules the redesign pins:
//   - toggle changes `selected` ONLY — never focus, never visibility (selection survives any
//     filter, so hiding a checked app cannot silently drop it);
//   - grid moves never wrap off a row edge or leave the grid;
//   - clampFocus returns a valid index for every input, including an empty filtered list;
//   - Apply diffs `selected` against `initialSelected` (membership at open time).

/** Apps the grid shows: label-filtered by [AppPickerState.query] when it is non-blank. */
internal fun AppPickerState.visibleApps(): List<AppPickerEntry> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return apps
    return apps.filter { it.label.lowercase().contains(q) }
}

/** Toggles [pkg]'s membership in `selected`. No-op for a package not in `apps`. */
internal fun AppPickerState.toggle(pkg: String): AppPickerState {
    if (apps.none { it.packageName == pkg }) return this
    return copy(selected = if (pkg in selected) selected - pkg else selected + pkg)
}

/**
 * Grid move over `visibleApps()` using the shared [gridMove] rules — no wrap in any direction,
 * no-op on an empty list. An illegal move leaves the cursor where it is, but still clears touch
 * mode, so what comes back is not always the receiver.
 */
internal fun AppPickerState.move(action: GamepadAction): AppPickerState {
    val visible = visibleApps()
    val direction = when (action) {
        GamepadAction.NAVIGATE_LEFT -> NavigationDirection.LEFT
        GamepadAction.NAVIGATE_RIGHT -> NavigationDirection.RIGHT
        GamepadAction.NAVIGATE_UP -> NavigationDirection.UP
        GamepadAction.NAVIGATE_DOWN -> NavigationDirection.DOWN
        else -> return this
    }
    // Touch mode ends on the first controller input — the cursor reappears where the last
    // touch browse/tap parked it (mirrors AppDrawerViewModel).
    val base = if (usingTouch) copy(usingTouch = false) else this
    // The count the grid actually DREW, not the constant: a panel that fits nine while the cursor
    // steps by seven puts RIGHT two tiles from the one under the eye, and stops LEFT at a row
    // edge that is not on the screen.
    val next = gridMove(base.focusedIndex, direction, columns = columns.coerceAtLeast(1), size = visible.size)
        ?: return base
    return base.copy(focusedIndex = next)
}

/** Clamps `focusedIndex` into the filtered list's range; 0 for an empty list. */
internal fun AppPickerState.clampFocus(): AppPickerState {
    val lastIndex = visibleApps().lastIndex
    val clamped = focusedIndex.coerceIn(0, lastIndex.coerceAtLeast(0))
    return if (clamped == focusedIndex) this else copy(focusedIndex = clamped)
}

/** Newly-checked packages — what Apply adds. */
internal fun AppPickerState.pendingAdds(): Set<String> = selected - initialSelected

/** Newly-unchecked packages — what Apply removes (after confirmation). */
internal fun AppPickerState.pendingRemovals(): Set<String> = initialSelected - selected

// ── Removal-confirmation modal: hard input boundary ─────────────────────────
//
// While the modal is up the dpad belongs to it: LEFT/RIGHT step between Cancel and Remove
// (no wrap), UP/DOWN are swallowed, and the grid cursor behind the scrim must not move.

/** Raises the modal, cursor parked on Cancel — a fresh prompt never pre-aims at the destructive option. */
internal fun AppPickerState.openConfirm(): AppPickerState =
    copy(confirmingRemovals = true, confirmFocusedOption = AppPickerState.CONFIRM_CANCEL)

/** LEFT/RIGHT step between the two options; every other action (incl. UP/DOWN) is a no-op. */
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

/** Closes the modal and re-parks the cursor on Cancel for the next prompt. */
internal fun AppPickerState.cancelConfirm(): AppPickerState =
    copy(confirmingRemovals = false, confirmFocusedOption = AppPickerState.CONFIRM_CANCEL)

// ── Where an app can be assigned ──────────────────────────────────────────────

/**
 * Whether [category]'s column is built from its assigned apps.
 *
 * False means an app added to it lands in the database and on no screen. Three columns never call
 * `AppCategoryRepository.appsForCategory`:
 *  - a gaming category builds its column from the games table;
 *  - Last Played is derived from last_played_at and nothing can be assigned to it — note it is
 *    `isGamingCategory = false`, so the flag alone does NOT catch it;
 *  - Settings builds its own hierarchy.
 *
 * Here rather than inside the ViewModel so the Last Played case can be tested: it is the one that
 * the obvious one-line version of this predicate gets wrong, and it is a silent wrong — the write
 * succeeds and the row simply never appears.
 */
internal fun categoryShowsApps(category: Category): Boolean =
    !category.isGamingCategory &&
        category.id != BuiltInCategory.RECENTLY_PLAYED &&
        category.id != BuiltInCategory.SETTINGS
