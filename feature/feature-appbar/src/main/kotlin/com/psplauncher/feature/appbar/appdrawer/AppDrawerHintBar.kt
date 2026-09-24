package com.psplauncher.feature.appbar.appdrawer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.PfpHintBar

// ── The App Drawer's footer ───────────────────────────────────────────────────
//
// The shared [PfpHintBar], the same bar the crossbar and the detail pages draw. What is in it is
// the drawer's own business — Back and Launch fall left, the page keys and the rest go right —
// but the look, the glyphs and the X/Y swap are decided in one place for the whole app.
//
// Rendered as a permanent footer row at the bottom of the drawer's Column (below the grid);
// visibility is alpha-driven by the caller, so the slot's height is reserved whether or not the
// hints are showing and content geometry never changes when they fade in or out.

@Composable
internal fun AppDrawerHintBar(
    modifier: Modifier = Modifier,
    /** Runs a tapped prompt through the drawer's own action handler. */
    onAction: ((GamepadAction) -> Unit)? = null,
) {
    PfpHintBar(
        items = listOf(
            ControllerPromptItem(GamepadAction.BACK, "Back"),
            ControllerPromptItem(GamepadAction.SELECT, "Launch"),
            ControllerPromptItem(GamepadAction.PREV_CATEGORY, "Prev"),
            ControllerPromptItem(GamepadAction.NEXT_CATEGORY, "Next"),
            ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options"),
            ControllerPromptItem(GamepadAction.CHANGE_SORT, "Search"),
        ),
        modifier = modifier,
        onAction = onAction,
    )
}
