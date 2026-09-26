package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerHintBar
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.ControllerPromptStyle
import com.psplauncher.core.ui.components.LocalControllerPromptStyle
import com.psplauncher.core.ui.preview.CombinedPreviews
import com.psplauncher.core.ui.preview.PfpPreview

@Composable
fun ContextMenuHint(
    modifier: Modifier = Modifier,

    showSort: Boolean = false,

    showFilter: Boolean = false,

    showOptions: Boolean = true,

    showRootActions: Boolean = false,

    onAction: ((GamepadAction) -> Unit)? = null,
) {
    val items = buildList {
        if (showFilter) add(ControllerPromptItem(GamepadAction.CHANGE_SORT, "Filter"))
        else if (showSort) add(ControllerPromptItem(GamepadAction.CHANGE_SORT, "Sort"))
        if (showOptions) add(ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options"))
        if (showRootActions) {
            add(ControllerPromptItem(GamepadAction.OPEN_SEARCH, "Search"))

            add(ControllerPromptItem(GamepadAction.BACK, "Apps"))
        }
    }
    ControllerHintBar(items = items, modifier = modifier, onAction = onAction)
}

@CombinedPreviews
@Composable
fun ContextMenuHintPreview() {
    PfpPreview {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (family in ControllerDisplayType.entries) {
                CompositionLocalProvider(
                    LocalControllerPromptStyle provides ControllerPromptStyle(family = family),
                ) {
                    ContextMenuHint(showSort = true, showOptions = true)
                }
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}
