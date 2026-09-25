package com.psplauncher.feature.appbar.appdrawer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.psplauncher.core.domain.model.ControllerIcon
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
    /**
     * True while the drawer's own app menu is up.
     *
     * The bar used to fade to nothing here, taking the dispatcher with it, so the one moment the
     * drawer offered a list of unfamiliar choices was the one moment nothing on screen said what
     * B did. The XMB's context rail had already answered this the other way — it keeps its bar
     * and rewrites it (see HintPrompts) — and two screens answering one question differently is
     * the thing the shared bar exists to stop.
     */
    menuOpen: Boolean = false,
    /** Runs a tapped prompt through the drawer's own action handler. */
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
