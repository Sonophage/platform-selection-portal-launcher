package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── The rail's own shape ──────────────────────────────────────────────────────
//
// ONE surface wears it today: the right rail's rows (ContextMenuOverlay).
//
// It was written for two. The Last Played shelf's Play control was a LaunchSpine built from
// XmbRailRow, and this file's header argued at length that one look means one definition. That
// commit is 6e411c41, which deleted LaunchSpine; the argument outlived the second caller by a
// day. Left here as a note rather than as a claim, because the reasoning is still right and the
// second caller may come back — but a comment that says "two surfaces" when there is one is the
// kind that gets believed.
//
// The badge is a rounded square at the app drawer's corner ratio — a quarter of the side — so the
// letters here and the letters in the drawer are the same object.

/** A named action: the label, then its badge, right-aligned. White when [focused]. */
@Composable
internal fun XmbRailRow(
    label: String,
    focused: Boolean,
    destructive: Boolean = false,
    dim: Float = 1f,
    onClick: (() -> Unit)? = null,
) {
    val tint = if (destructive) RailDestructive else null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .alpha(if (focused) 1f else dim)
            .clip(RoundedCornerShape(RailCorner))
            .then(if (focused) Modifier.background(Color.White) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = RailPadStart, end = RailPadEnd, top = RailPadV, bottom = RailPadV),
    ) {
        Text(
            text = label,
            color = when {
                destructive -> RailDestructive
                focused -> RailInk
                else -> Color.White
            },
            fontSize = RailTextSize,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = RailMaxText),
        )
        Spacer(Modifier.width(RailGap))
        XmbRailBadge(label = label, filled = focused, tint = tint)
    }
}

/**
 * The circle-that-is-a-square, with the action's initial in it.
 *
 * [filled] inverts it to a DARK badge with a light letter, which is how it stays visible sitting
 * on the white capsule. It took the theme accent first, and on a monochrome wave that is
 * near-white — the badge and its letter disappeared into the capsule entirely. A focus cue that is
 * invisible under one of the app's own themes is not a focus cue.
 */
@Composable
internal fun XmbRailBadge(label: String, filled: Boolean, tint: Color? = null) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(RailIcon)
            .clip(RoundedCornerShape(RailCorner))
            .background(
                when {
                    filled -> tint ?: RailInk
                    tint != null -> tint.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.12f)
                },
            ),
    ) {
        Text(
            text = label.trim().firstOrNull()?.uppercase() ?: "?",
            color = if (filled) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = RailGlyphSize,
            fontWeight = FontWeight.Bold,
        )
    }
}

// Measured off the design against a 1920x1080 frame, over this panel's density of 2.3375:
// a 67px badge, a 95px-tall capsule, 24dp of right margin.
internal val RailIcon = 29.dp
internal val RailCorner = 7.dp
internal val RailEdgeGap = 24.dp
internal val RailRowGap = 13.dp
private val RailGlyphSize = 13.sp
private val RailTextSize = 13.sp
private val RailPadStart = 14.dp
private val RailPadEnd = 4.dp
private val RailPadV = 4.dp
private val RailGap = 10.dp

/** Wide enough for the longest label in any menu; the scrim covers whatever is drawn. */
private val RailMaxText = 300.dp

internal val RailInk = Color(0xFF1A0C03)

/**
 * The wash the rail and the notification sheet both lay down.
 *
 * One colour, two directions: the rail ramps it left-to-right off the right edge, the sheet ramps
 * it top-to-bottom off the top edge. "Mimic the context menu but the gradient is vertical" is one
 * surface treatment used twice, and a second near-black would be a second near-black to keep in
 * step the next time either is tuned.
 */
internal val XmbScrim = Color(0xC4080301)
internal val RailDestructive = Color(0xFFE2606A)
