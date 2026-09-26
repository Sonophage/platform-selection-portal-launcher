package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

internal fun stableKey(prefix: String, fr: FocusRequester, focusKey: String?): String =
    focusKey ?: "$prefix-${System.identityHashCode(fr)}"

internal class ControllerRowRegistration(
    val focusRequester: FocusRequester,
    val rowKey: String,

    val positionReporting: Modifier,
)

@Composable
internal fun rememberControllerRowRegistration(
    prefix: String,
    focusKey: String?,
    claimInitialFocus: Boolean,
    selectable: Boolean,
    enabled: Boolean = true,
    onSelect: (() -> Unit)?,
    onLongPress: (() -> Unit)? = null,
    trailingActionsFor: (rowKey: String) -> List<ControllerNavItem> = { emptyList() },
): ControllerRowRegistration {
    val focusRegistry = LocalSettingsFocusRegistry.current
    val registerFirst = LocalSettingsRegisterFirstFocusable.current
    val navigationOrder = LocalSettingsNavigationOrder.current
    val rowPositions = LocalSettingsRowPositions.current
    val rowSizes = LocalSettingsRowSizes.current
    val reportRemoved = LocalSettingsReportRemoved.current

    val focusRequester = remember { FocusRequester() }
    if (focusKey != null) {
        DisposableEffect(focusKey) {
            focusRegistry[focusKey] = focusRequester
            onDispose {
                if (focusRegistry[focusKey] === focusRequester) focusRegistry.remove(focusKey)
            }
        }
    }
    val rowKey = stableKey(prefix, focusRequester, focusKey)

    val navItem = ControllerNavItem(
        key = rowKey,
        focusable = true,
        selectable = selectable,
        enabled = enabled,
        onSelect = onSelect,
        onLongPress = onLongPress,
        trailingActions = trailingActionsFor(rowKey),
    )
    DisposableEffect(Unit) {
        navigationOrder?.add(focusRequester to navItem)
        if (claimInitialFocus) registerFirst(focusRequester)
        onDispose {
            navigationOrder?.removeAll { it.first === focusRequester }
            rowPositions?.remove(focusRequester)
            rowSizes?.remove(focusRequester)

            reportRemoved(focusRequester)
        }
    }
    SideEffect {
        val list = navigationOrder ?: return@SideEffect
        val index = list.indexOfFirst { it.first === focusRequester }
        if (index >= 0) list[index] = focusRequester to navItem
    }
    val positionReporting = if (rowPositions != null && rowSizes != null) {
        val positions = rowPositions
        val sizes = rowSizes
        val req = focusRequester
        Modifier.onGloballyPositioned {
            positions[req] = it.localToRoot(Offset.Zero).y
            sizes[req] = it.size.height.toFloat()
        }
    } else {
        Modifier
    }
    return ControllerRowRegistration(focusRequester, rowKey, positionReporting)
}

@Composable
internal fun SettingsRowActionButton(
    rowKey: String,
    index: Int,
    action: SettingsRowAction,
    onFocusedChanged: (Boolean) -> Unit,
) {
    val focusTracker = LocalSettingsFocusTracker.current
    val touchInput = LocalSettingsTouchInput.current
    val rowActionFrs = LocalSettingsRowActions.current
    val reportFocused = LocalSettingsReportFocused.current
    val reportRemoved = LocalSettingsReportRemoved.current
    val actionFr = remember { FocusRequester() }
    val actionKey = "$rowKey:action:$index"
    var actionFocused by remember { mutableStateOf(false) }
    DisposableEffect(actionKey) {
        val list = rowActionFrs?.getOrPut(rowKey) { mutableStateListOf() }
        list?.add(actionKey to actionFr)
        onDispose {
            list?.removeAll { it.second === actionFr }
            reportRemoved(actionFr)
        }
    }
    IconButton(
        onClick = action.onClick,
        modifier = Modifier
            .pointerInput(actionKey, action.onClick, action.onLongPress) {
                detectTapGestures(
                    onTap = { touchInput(); action.onClick() },
                    onLongPress = { touchInput(); action.onLongPress?.invoke() },
                )
            }
            .focusRequester(actionFr)
            .onFocusChanged { state ->
                actionFocused = state.isFocused
                onFocusedChanged(state.isFocused)
                if (state.isFocused) {
                    focusTracker(action.onClick)
                    reportFocused(actionFr)
                }
            }
            .focusable(),
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (actionFocused) action.actionFocusBackgroundColor else Color.Transparent,
                    RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            action.icon()
        }
    }
}
