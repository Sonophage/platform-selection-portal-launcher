package com.psplauncher.core.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── In-window confirmation ────────────────────────────────────────────────────
//
// NOT a Material3 AlertDialog, and that is the entire point.
//
// An AlertDialog renders into its own platform Window. While it holds focus the Activity's
// dispatchKeyEvent is never called -- and that is where this app's whole gamepad pipeline lives
// (GamepadInputHandler, reached from MainActivity). Verified on device: with a confirmation
// AlertDialog open, BUTTON_B did nothing, BUTTON_A did nothing, the D-pad did nothing, and the
// system Back key dismissed only the soft keyboard. The dialog could be escaped by touch alone,
// while the helper footer underneath it promised "A Enter / B Back".
//
// On a handheld that hides the navigation bar and neuters system Back, that is a trap with no
// controller exit at all. So confirmations are drawn HERE, in the launcher's own window, the same
// way PspContextMenuOverlay and DetailContextMenu already are -- which is why those work.
//
// This composable is display only. The caller owns which button the cursor is on, because the
// cursor belongs to the navigation engine, and a second opinion about it here is how two things
// end up disagreeing about the same state.

/** Resting tint for a confirm button that destroys something. */
val DestructiveConfirmFill = Color(0x33FF6B6B)

/**
 * A two-choice confirmation drawn inside the launcher's own window.
 *
 * [confirmFocused] / [cancelFocused] are mutually exclusive by contract; the caller derives them
 * from one focused key, so they cannot both be true.
 *
 * [confirmFill] tints the confirm button at rest. It defaults to the destructive red because
 * that is what most confirmations here are, and a prompt that merely offers to do something
 * useful (finish setting up a library, say) passes null so it is not painted as a warning.
 */
@Composable
fun PfpConfirmOverlay(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    confirmFocused: Boolean,
    cancelFocused: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    confirmFill: Color? = DestructiveConfirmFill,
) {
    PfpOverlayCard(onScrimTap = onCancel, modifier = modifier) {
        PfpOverlayTitle(title)
        Spacer(Modifier.height(10.dp))
        Text(text = message, color = DetailTextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        // STACKED, not side by side, and that is a navigation fact rather than a taste.
        //
        // The engine registers these two as top-level sibling nodes, and in this codebase
        // top-level siblings move with UP/DOWN -- a node's `children` are the LEFT/RIGHT axis
        // (see NavigationNode). Drawing them in a Row would put the cursor on an axis the engine
        // does not move along, so RIGHT would appear broken. A vertical pair is also what a real
        // XMB confirmation is.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Cancel LEADS. It is the safe choice and it is where the cursor starts.
            PfpDetailLaunchButton(
                label = cancelLabel,
                icon = null,
                focused = cancelFocused,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
            PfpDetailLaunchButton(
                label = confirmLabel,
                icon = null,
                focused = confirmFocused,
                onClick = onConfirm,
                // Tinted at REST so a destructive choice is identifiable before it is focused.
                // The focused treatment stays the shared inversion: exactly one control on screen
                // is focused and it always looks the same.
                fill = confirmFill ?: DetailButtonRest,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
