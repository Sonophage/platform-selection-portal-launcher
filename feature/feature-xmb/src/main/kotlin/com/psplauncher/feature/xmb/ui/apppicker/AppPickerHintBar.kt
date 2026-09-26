package com.psplauncher.feature.xmb.ui.apppicker

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.PfpHintBar
import com.psplauncher.core.ui.theme.StorefrontColors

@Composable
internal fun AppPickerHintBar(
    title: String,
    selectedCount: Int,
    confirmingRemovals: Boolean,
    colors: StorefrontColors,
    modifier: Modifier = Modifier,
    onAction: ((GamepadAction) -> Unit)? = null,
) {
    val items = if (confirmingRemovals) {
        listOf(
            ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Choose"),
            ControllerPromptItem(GamepadAction.SELECT, "Confirm"),
            ControllerPromptItem(GamepadAction.BACK, "Cancel"),
        )
    } else {
        listOf(
            ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Navigate"),
            ControllerPromptItem(GamepadAction.SELECT, "Toggle"),
            ControllerPromptItem(GamepadAction.CHANGE_SORT, "Search"),
            ControllerPromptItem(GamepadAction.HOME, "Apply"),
            ControllerPromptItem(GamepadAction.BACK, "Cancel"),
        )
    }

    PfpHintBar(
        items = items,
        modifier = modifier,
        onAction = onAction,
        centre = {
            Text(
                text = if (selectedCount == 0) title else "$title  ·  $selectedCount selected",
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
