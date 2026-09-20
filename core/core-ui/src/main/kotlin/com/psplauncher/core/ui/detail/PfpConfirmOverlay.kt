package com.psplauncher.core.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

/**
 * A two-choice confirmation drawn inside the launcher's own window.
 *
 * [destructiveFocused] / [cancelFocused] are mutually exclusive by contract; the caller derives
 * them from one focused key, so they cannot both be true.
 */
@Composable
fun PfpConfirmOverlay(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    destructiveFocused: Boolean,
    cancelFocused: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            // Tapping the scrim cancels -- never confirms. A stray tap outside a destructive
            // prompt must not be able to perform it.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 520.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF21A1A22))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                // Consume taps inside the panel so the scrim's cancel does not fire underneath.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 26.dp, vertical = 22.dp),
        ) {
            Text(
                text = title,
                color = DetailTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(shadow = DetailTextShadow),
            )
            Spacer(Modifier.height(10.dp))
            Text(text = message, color = DetailTextMuted, fontSize = 14.sp)
            Spacer(Modifier.height(22.dp))
            // STACKED, not side by side, and that is a navigation fact rather than a taste.
            //
            // The engine registers these two as top-level sibling nodes, and in this codebase
            // top-level siblings move with UP/DOWN -- a node's `children` are the LEFT/RIGHT axis
            // (see NavigationNode). Drawing them in a Row would put the cursor on an axis the
            // engine does not move along, so RIGHT would appear broken. A vertical pair is also
            // what a real XMB confirmation is.
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
                    focused = destructiveFocused,
                    onClick = onConfirm,
                    // Tinted at REST so the destructive choice is identifiable before it is
                    // focused. The focused treatment stays the shared inversion: exactly one
                    // control on screen is focused and it always looks the same.
                    fill = Color(0x33FF6B6B),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
