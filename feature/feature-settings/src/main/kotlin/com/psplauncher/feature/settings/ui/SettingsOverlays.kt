package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.PfpCheckMark
import com.psplauncher.core.ui.detail.PfpDetailLaunchButton
import com.psplauncher.core.ui.detail.PfpOverlayCard
import com.psplauncher.core.ui.detail.PfpOverlayTitle

// ── Settings prompts ──────────────────────────────────────────────────────────
//
// The settings screens' own family of in-window prompts. They exist for the reason every other
// overlay in this app does -- a Material3 AlertDialog renders into its own platform Window, so
// while it is up the Activity's dispatchKeyEvent never runs and no gamepad press reaches the app
// -- plus one reason specific to here.
//
// A settings screen has a live cursor of its own underneath. A prompt drawn over it must take the
// pad, or DOWN keeps walking the list behind the prompt and A fires whatever row it lands on.
// Each of these calls SettingsOverlayInput, so the scaffold hands it every action first and the
// list underneath goes still.
//
// The contract, the same in all of them: A performs the focused choice, B is the way out, and a
// tap on the scrim cancels and never confirms.

private val MESSAGE_COLOR = Color(0xCCFFFFFF)

/**
 * A two-choice confirmation over a settings screen.
 *
 * UP/DOWN moves between the two buttons, which is why this owns a cursor at all rather than
 * hard-wiring A to confirm: a destructive prompt where A is "yes" and there is nothing to move to
 * is how a reflexive A-press deletes something.
 *
 * [destructive] tints the confirm button at rest and starts the cursor on Cancel, which is the
 * arrangement a prompt that removes something needs. A non-destructive one starts on Confirm.
 */
@Composable
fun SettingsConfirmOverlay(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    cancelLabel: String = "Cancel",
    destructive: Boolean = true,
) {
    // 0 = cancel, 1 = confirm. Cancel leads on a destructive prompt.
    var cursor by remember(destructive) { mutableIntStateOf(if (destructive) 0 else 1) }
    SettingsOverlayInput { action ->
        when (action) {
            GamepadAction.NAVIGATE_UP, GamepadAction.NAVIGATE_DOWN -> cursor = 1 - cursor
            GamepadAction.SELECT -> if (cursor == 0) onCancel() else onConfirm()
            GamepadAction.BACK -> onCancel()
            else -> Unit
        }
    }
    PfpOverlayCard(onScrimTap = onCancel) {
        PfpOverlayTitle(title)
        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(message, color = MESSAGE_COLOR, fontSize = 14.sp)
        }
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PfpDetailLaunchButton(
                label = cancelLabel,
                icon = null,
                focused = cursor == 0,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
            PfpDetailLaunchButton(
                label = confirmLabel,
                icon = null,
                focused = cursor == 1,
                onClick = onConfirm,
                fill = if (destructive) com.psplauncher.core.ui.detail.DestructiveConfirmFill else DetailRestFill,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The resting fill of a non-destructive button, matching PfpDetailLaunchButton's own default. */
private val DetailRestFill = Color(0x1FFFFFFF)

/** A message with one way out. A or B closes it. */
@Composable
fun SettingsMessageOverlay(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    dismissLabel: String = "OK",
) {
    SettingsOverlayInput { action ->
        // Read-only: there is nothing to choose, so both buttons mean the same thing. Anything
        // else is swallowed rather than passed down, or the list behind would move.
        if (action == GamepadAction.SELECT || action == GamepadAction.BACK) onDismiss()
    }
    PfpOverlayCard(onScrimTap = onDismiss) {
        PfpOverlayTitle(title)
        Spacer(Modifier.height(10.dp))
        Text(message, color = MESSAGE_COLOR, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        PfpDetailLaunchButton(
            label = dismissLabel,
            icon = null,
            focused = true,
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A single-choice list over a settings screen: pick a default player, a sound, a category.
 *
 * The cursor starts on whatever is already chosen, so A with no movement is a no-op rather than a
 * change. [selectedIndex] of -1 means nothing is chosen yet and the cursor starts at the top.
 */
@Composable
fun SettingsChoiceOverlay(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    var cursor by remember(options, selectedIndex) {
        mutableIntStateOf(selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)))
    }
    SettingsOverlayInput { action ->
        when (action) {
            GamepadAction.NAVIGATE_UP -> cursor = (cursor - 1).coerceAtLeast(0)
            GamepadAction.NAVIGATE_DOWN -> cursor = (cursor + 1).coerceAtMost(options.lastIndex)
            GamepadAction.SELECT -> if (options.isNotEmpty()) onPick(cursor)
            GamepadAction.BACK -> onCancel()
            else -> Unit
        }
    }
    PfpOverlayCard(onScrimTap = onCancel) {
        PfpOverlayTitle(title)
        Spacer(Modifier.height(12.dp))
        // Scrollable: a list of installed players or sound files has no fixed length, and a card
        // taller than the screen would put its last rows where nothing can reach them.
        Column(
            modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEachIndexed { index, label ->
                val focused = index == cursor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (focused) Color(0x33FFFFFF) else Color.Transparent)
                        .clickable { onPick(index) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        color = if (focused) Color.White else MESSAGE_COLOR,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    // Drawn, not a font glyph: a check character's shape and baseline come from
                    // whichever font the device falls back to. Same reasoning as PfpCheckMark's
                    // other callers.
                    if (index == selectedIndex) {
                        PfpCheckMark(Color(0xFF7ED957), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/**
 * A prompt with more than two ways out: "OK", "use my exact colour", "don't warn again".
 *
 * UP/DOWN walks the buttons, A takes the focused one, B takes [onCancel]. Two choices belong in
 * [SettingsConfirmOverlay], which says which of them is destructive; this one is for a set where
 * none of them is.
 */
@Composable
fun SettingsActionsOverlay(
    title: String,
    message: String,
    actions: List<Pair<String, () -> Unit>>,
    onCancel: () -> Unit,
) {
    var cursor by remember(actions.size) { mutableIntStateOf(0) }
    SettingsOverlayInput { action ->
        when (action) {
            GamepadAction.NAVIGATE_UP -> cursor = (cursor - 1).coerceAtLeast(0)
            GamepadAction.NAVIGATE_DOWN -> cursor = (cursor + 1).coerceAtMost(actions.lastIndex)
            GamepadAction.SELECT -> actions.getOrNull(cursor)?.second?.invoke()
            GamepadAction.BACK -> onCancel()
            else -> Unit
        }
    }
    PfpOverlayCard(onScrimTap = onCancel) {
        PfpOverlayTitle(title)
        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(message, color = MESSAGE_COLOR, fontSize = 14.sp)
        }
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEachIndexed { index, (label, onClick) ->
                PfpDetailLaunchButton(
                    label = label,
                    icon = null,
                    focused = index == cursor,
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * A name prompt over a settings screen.
 *
 * The card, the autofocused field and the keyboard's Done key all come from the shared
 * PfpTextPromptOverlay; what this adds is the settings pad contract, so A commits and B cancels
 * while the list underneath stays still.
 */
@Composable
fun SettingsTextPromptOverlay(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    placeholder: String = "",
    confirmLabel: String = "Save",
) {
    SettingsOverlayInput { action ->
        when (action) {
            GamepadAction.SELECT -> onConfirm()
            GamepadAction.BACK -> onCancel()
            // Everything else is swallowed: the soft keyboard owns the rest of the pad, and
            // letting a direction through would walk the list behind the prompt.
            else -> Unit
        }
    }
    com.psplauncher.core.ui.detail.PfpTextPromptOverlay(
        title = title,
        value = value,
        placeholder = placeholder,
        onValueChange = onValueChange,
        onConfirm = onConfirm,
        onCancel = onCancel,
        confirmLabel = confirmLabel,
    )
}
