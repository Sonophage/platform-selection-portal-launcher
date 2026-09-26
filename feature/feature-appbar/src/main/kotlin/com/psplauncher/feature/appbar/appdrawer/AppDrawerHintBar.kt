package com.psplauncher.feature.appbar.appdrawer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.PfpHintBar

@Composable
internal fun AppDrawerHintBar(
    modifier: Modifier = Modifier,

    menuOpen: Boolean = false,

    onAction: ((GamepadAction) -> Unit)? = null,
) {
    PfpHintBar(
        items = if (menuOpen) {
            listOf(
                ControllerPromptItem(GamepadAction.BACK, "Close"),
                ControllerPromptItem(GamepadAction.SELECT, "Select"),
                ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Navigate"),
            )
        } else {
            listOf(
                ControllerPromptItem(GamepadAction.BACK, "Back"),
                ControllerPromptItem(GamepadAction.SELECT, "Launch"),
                ControllerPromptItem(GamepadAction.PREV_CATEGORY, "Prev"),
                ControllerPromptItem(GamepadAction.NEXT_CATEGORY, "Next"),
                ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options"),
                ControllerPromptItem(GamepadAction.CHANGE_SORT, "Search"),
            )
        },
        modifier = modifier,
        onAction = onAction,
    )
}
