package com.psplauncher.core.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── The shape every in-window dialog takes ────────────────────────────────────
//
// Three overlays had drawn this same scrim and card independently before it was extracted, and a
// fourth was about to. That is the point at which the copies start disagreeing about a corner
// radius or which taps are swallowed.
//
// Why any of them exist at all: a Material3 AlertDialog renders into its own platform Window, so
// while it is up the Activity's dispatchKeyEvent never runs -- and that is where this app's whole
// gamepad pipeline lives. Measured on a tablet: with a dialog open, A did nothing, B did nothing,
// the D-pad did nothing, and the system Back key dismissed only the soft keyboard. On a handheld
// that hides the navigation bar, that is a trap with no controller exit.

/** Widest the card grows before its text starts wrapping for the sake of it. */
private val CARD_MAX_WIDTH: Dp = 520.dp
private val CARD_MIN_WIDTH: Dp = 320.dp

/**
 * A dimmed full-screen scrim with a centred card, drawn inside the launcher's own window.
 *
 * [onScrimTap] is the cancelling action, never the confirming one: a stray tap outside a prompt
 * must not be able to perform it. Taps inside the card are swallowed so they do not fall through.
 */
@Composable
fun PfpOverlayCard(
    onScrimTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            // The soft keyboard covers the bottom of the screen. Padding here rather than in each
            // overlay means a card that grows a text field later cannot forget it.
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onScrimTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = CARD_MIN_WIDTH, max = CARD_MAX_WIDTH)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF21A1A22))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 24.dp, vertical = 20.dp),
            content = content,
        )
    }
}

/** The card's heading. Separate so every overlay's title is the same size and weight. */
@Composable
fun PfpOverlayTitle(text: String) {
    Text(
        text = text,
        color = DetailTextPrimary,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        style = TextStyle(shadow = DetailTextShadow),
    )
}

/**
 * A message with one way out, drawn in the launcher's own window.
 *
 * The read-only end of the family: "here is what happened, press A". The caller's ViewModel
 * already answers A and B for these -- they were simply unreachable behind a dialog window.
 */
@Composable
fun PfpMessageOverlay(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Close",
    /** True when the cursor is on the button. A one-button prompt normally leaves this true. */
    dismissFocused: Boolean = true,
) {
    PfpOverlayCard(onScrimTap = onDismiss, modifier = modifier) {
        PfpOverlayTitle(title)
        Spacer(Modifier.height(10.dp))
        Text(text = message, color = DetailTextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PfpDetailLaunchButton(
                label = dismissLabel,
                icon = null,
                focused = dismissFocused,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
