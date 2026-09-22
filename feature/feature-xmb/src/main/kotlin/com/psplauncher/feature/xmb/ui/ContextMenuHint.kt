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

// ── Idle hint pill ────────────────────────────────────────────────────────────
//
// A small black rounded pill shown in the XMB's bottom-right (stacked above the App
// Drawer button) after the user has been idle for a moment. It names *actions*, so the
// glyphs track both the controller display style and a swapped X/Y layout, mirroring the
// reference PSP UI.
//
// The pill carries up to three prompts:
//   [ {PREV_CATEGORY}{NEXT_CATEGORY} Pages  {CHANGE_SORT} Sort  {OPEN_CONTEXT_MENU} Options ]
// Sort appears only where an X/Square press really re-sorts the list on screen
// (XMBUiState.canSortCurrentList), Options only where the focused item really has a
// context menu (XMBUiState.focusedItemHasContextMenu), and Pages only where the hovered game
// has a second panel page to walk to (XMBUiState.hoverPanelHasPages). All three are conditional
// because a pill promising an action that does nothing is worse than a smaller pill.
//
// Pages is one prompt over both shoulders rather than two prompts, which is what the
// multi-action ControllerPromptItem is for — and it is correctly not tappable, because a tap
// cannot say which shoulder was meant.
//
// Visibility is driven entirely by XMBUiState.showContextMenuHint (the shell/detail screens
// fade it in but remove it immediately when it becomes ineligible); this composable only renders
// its content.
//
// The pill chrome itself is the shared core-ui [ControllerHintBar] — the App Drawer renders the
// same pill for its own actions (see feature-appbar's AppDrawerHintBar).

@Composable
fun ContextMenuHint(
    modifier: Modifier = Modifier,
    /** Show the Pages half — the hovered game's panel has somewhere for L1/R1 to go. */
    showPages: Boolean = false,
    /** Show the Sort half — the current list responds to CHANGE_SORT. */
    showSort: Boolean = false,
    /** Show the Filter half — X cycles the home shelf's media instead of sorting. */
    showFilter: Boolean = false,
    /** Show the Options half — the focused item has a context menu. */
    showOptions: Boolean = true,
    /** Runs a tapped prompt. Null leaves the pill a legend (previews, and any caller that has
     *  no dispatcher to offer). */
    onAction: ((GamepadAction) -> Unit)? = null,
) {
    val items = buildList {
        if (showPages) add(
            ControllerPromptItem(
                listOf(GamepadAction.PREV_CATEGORY, GamepadAction.NEXT_CATEGORY),
                "Pages",
            ),
        )
        // Sort and Filter are the same button doing two jobs, so they are mutually exclusive
        // by construction rather than by both callers remembering to be careful.
        if (showFilter) add(ControllerPromptItem(GamepadAction.CHANGE_SORT, "Filter"))
        else if (showSort) add(ControllerPromptItem(GamepadAction.CHANGE_SORT, "Sort"))
        if (showOptions) add(ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options"))
    }
    ControllerHintBar(items = items, modifier = modifier, onAction = onAction)
}

// ── Previews ──────────────────────────────────────────────────────────────────

@CombinedPreviews
@Composable
fun ContextMenuHintPreview() {
    PfpPreview {
        // One pill per family, each given its own ambient style so the preview
        // still shows △ / Y / X side by side now that the hint reads context.
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (family in ControllerDisplayType.entries) {
                CompositionLocalProvider(
                    LocalControllerPromptStyle provides ControllerPromptStyle(family = family),
                ) {
                    ContextMenuHint(showPages = true, showSort = true, showOptions = true)
                }
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}
