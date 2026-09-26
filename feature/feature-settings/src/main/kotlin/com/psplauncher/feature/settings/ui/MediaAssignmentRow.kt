package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.XYLayout
import com.psplauncher.core.ui.components.ControllerPromptItem

private val DangerRed = Color(0xFFE55353)

@Composable
fun MediaAssignmentRow(
    label: String,
    focusKey: String,
    value: String,
    isAssigned: Boolean,
    onPick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,

    onPreview: (() -> Unit)? = null,

    onUseDefault: (() -> Unit)? = null,

    resetLabel: String = "Use the PSP default for $label",
    sublabel: String? = null,
) {
    SettingsRow(
        label = label,
        sublabel = sublabel,
        focusKey = focusKey,
        onClick = onPick,
        onFocusChangedExternal = onFocusChanged,
        value = value,
        actions = buildList {
            if (onPreview != null) add(
                SettingsRowAction(
                    "Preview $label", onPreview,
                    actionFocusBackgroundColor = lerp(SettingsAccent, Color.Black, 0.50f),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Preview $label",
                        tint = SettingsAccent,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(4.dp),
                    )
                }
            )
            if (isAssigned && onUseDefault != null) {
                add(
                    SettingsRowAction(
                        resetLabel, onUseDefault,
                        actionFocusBackgroundColor = lerp(DangerRed, Color.Black, 0.50f),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = resetLabel,
                            tint = SettingsSubtext,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(4.dp),
                        )
                    }
                )
            }
        },
    )
}

object MediaRowShortcuts {
    fun northFace(layout: XYLayout): GamepadAction = when (layout) {
        XYLayout.STANDARD -> GamepadAction.OPEN_CONTEXT_MENU
        XYLayout.SWAPPED -> GamepadAction.CHANGE_SORT
    }

    fun westFace(layout: XYLayout): GamepadAction = when (layout) {
        XYLayout.STANDARD -> GamepadAction.CHANGE_SORT
        XYLayout.SWAPPED -> GamepadAction.OPEN_CONTEXT_MENU
    }

    fun isNorthFace(action: GamepadAction, layout: XYLayout): Boolean = action == northFace(layout)

    fun isWestFace(action: GamepadAction, layout: XYLayout): Boolean = action == westFace(layout)

    fun promptsFor(
        layout: XYLayout,
        isAssigned: Boolean,
        resetLabel: String = "Use Default",
        previewLabel: String = "Preview",
    ): List<ControllerPromptItem> = buildList {
        if (isAssigned) add(ControllerPromptItem(action = northFace(layout), label = resetLabel))
        add(ControllerPromptItem(action = westFace(layout), label = previewLabel))
    }
}
