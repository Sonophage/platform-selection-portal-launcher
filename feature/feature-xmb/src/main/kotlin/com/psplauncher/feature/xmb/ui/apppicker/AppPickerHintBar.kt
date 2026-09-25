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

// ── The picker's footer ───────────────────────────────────────────────────────
//
// The shared [PfpHintBar], the same bar the crossbar, the App Drawer, Settings and the detail
// pages draw. It replaces a hand-placed INLINE prompt row, which was the picker's own fourth look
// at the same job.
//
// The centre slot carries what the deleted header used to say. The old header spent a 56dp row on
// a ‹ back arrow, the title, a live "N Selected" count and a magnifier button labelled "Search" —
// and B, the search key and the grid's own check badges already say three of those four. What is
// left is the title and the count, which are context rather than controls, and the bar's centre is
// where this app puts context: Settings puts the focused row's explanation in the same slot.

@Composable
internal fun AppPickerHintBar(
    title: String,
    selectedCount: Int,
    confirmingRemovals: Boolean,
    colors: StorefrontColors,
    modifier: Modifier = Modifier,
    onAction: ((GamepadAction) -> Unit)? = null,
) {
    // While the removal-confirmation modal is up the prompts describe the modal's controls — the
    // grid behind the scrim is inert, so naming its keys would name keys that do nothing.
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
