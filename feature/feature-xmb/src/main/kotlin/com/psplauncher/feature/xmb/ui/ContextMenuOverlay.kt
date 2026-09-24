package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.feature.xmb.viewmodel.XMBContextMenuItem

// ── The right rail, 9h ───────────────────────────────────────────────────────
//
// Replaces the PSP-style panel this file used to adapt. The actions stack up the right edge as
// small circles; the focused one grows LEFT into a solid capsule carrying its name, and its state
// line under it when it has one. Only that one is named — which is the whole point of the shape,
// and also what makes nine of them fit where nine labelled rows did not.
//
// Up and down move it, matching the column, and the ViewModel still owns the cursor: this draws
// XMBContextMenu exactly as it always did and reports an index back. The trimming — which rows,
// how many — is railRows, not here.
//
// Monogram circles rather than drawn glyphs, by decision: the app has no action-icon set (the
// catalogue is categories and consoles) and the drawer's own letter tiles already established the
// look. A glyph can replace any letter later without this file changing shape.
//
// The scrim reaches the middle of the screen. The design's own stops at 560px of 1920; "the
// gradient should reach mid screen" was the owner's correction, so it is half the width, opaque at
// the right edge and gone by the centre.

@Composable
fun ContextMenuOverlay(
    rows: List<XMBContextMenuItem>,
    selectedIndex: Int,
    onItemActivated: (index: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The scrim, on the outer box: transparent to the middle, near-opaque at the right edge.
    // Measured rather than eyeballed — (129,93,58) at x=960, (25,17,8) at x=1910 over album art.
    // It was briefly believed to be broken, off a resized screenshot; it was not.
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    RailScrimStart to Color.Transparent,
                    RailScrimSolid to RailScrim,
                    1f to RailScrim,
                ),
            ),
    ) {
        // Catches the press that lands anywhere else, the same job the panel's own dim did.
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(RailGap),
            // No fillMaxHeight: the column has to WRAP for CenterEnd to centre it. Filling the
            // height made it start at the top and run off the bottom, with the focused capsule
            // under the status strip.
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = RailEdgeGap),
        ) {
            rows.forEachIndexed { index, row ->
                RailAction(
                    row = row,
                    focused = index == selectedIndex,
                    // The column's own ramp, by steps from the cursor — the rail is a list being
                    // navigated and so it fades like every other list on this screen. One
                    // definition, in XmbDim; tuning it there tunes the crossbar and the column
                    // with it, which is the point.
                    dim = XmbDim.ranked(kotlin.math.abs(index - selectedIndex)),
                    onClick = { onItemActivated(index) },
                )
            }
        }
    }
}

@Composable
private fun RailAction(row: XMBContextMenuItem, focused: Boolean, dim: Float, onClick: () -> Unit) {
    val destructiveTint = if (row.isDestructive) DestructiveTint else null
    if (!focused) {
        // Named, like the focused one, and faded by distance instead of hidden. A badge alone was
        // a letter with nothing to disambiguate it — a platform card's rail read S, I, U, S, I, U,
        // O, H, with two of nearly everything. The dim is what keeps one row obviously the
        // subject while the rest stay readable.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .alpha(dim)
                .clip(RoundedCornerShape(RailCorner))
                .clickable(onClick = onClick)
                .padding(start = CapsulePadStart, end = CapsulePadEnd, top = CapsulePadV, bottom = CapsulePadV),
        ) {
            Text(
                text = row.label,
                color = if (row.isDestructive) DestructiveTint else Color.White,
                fontSize = CapsuleTitleSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = CapsuleMaxText),
            )
            Spacer(Modifier.width(CapsuleGap))
            Monogram(label = row.label, filled = false, tint = destructiveTint, onClick = null)
        }
        return
    }
    // The focused one: a capsule opening leftward, its circle staying on the rail's own line so
    // the column of icons below it does not step sideways when the cursor moves.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(RailCorner))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(start = CapsulePadStart, end = CapsulePadEnd, top = CapsulePadV, bottom = CapsulePadV),
    ) {
        // widthIn, not width: a fixed box truncated every label longer than "Resume" — the rail's
        // whole claim is that the focused action shows its name, and "Play in Back…" does not.
        // The cap keeps the longest of them ("Scrape Missing Artwork") inside the scrim.
        // One line. The design draws a state line under the name ("Slot 3 · today") and the owner
        // cut it: "remove the subtitles we won't use them". The couple of rows that had something
        // to say kept their brackets instead — "Icon Display (Box Art)" — which is where that fact
        // lived before and is the only place left for it.
        Text(
            text = row.label,
            color = if (row.isDestructive) DestructiveTint else CapsuleText,
            fontSize = CapsuleTitleSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = CapsuleMaxText),
        )
        Spacer(Modifier.width(CapsuleGap))
        Monogram(label = row.label, filled = true, tint = destructiveTint, onClick = null)
    }
}

/**
 * The circle, with the action's initial in it.
 *
 * [filled] is the focused state: a DARK circle with a light letter, sitting on the white capsule,
 * as the design draws it.
 *
 * Not the theme accent, which was the first attempt: on a monochrome wave the accent is near-white
 * and the circle — and its letter with it — disappeared into the capsule entirely. A focus cue
 * that is invisible under one of the app's own themes is not a focus cue.
 */
@Composable
private fun Monogram(label: String, filled: Boolean, tint: Color?, onClick: (() -> Unit)?) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(RailIcon)
            .clip(RoundedCornerShape(RailCorner))
            .background(
                when {
                    filled -> tint ?: CapsuleText
                    tint != null -> tint.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.12f)
                },
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
// a 67px circle on a 99px pitch, a 278x95px capsule, 24dp of right margin.
private val RailIcon = 29.dp
private val RailGap = 13.dp
private val RailEdgeGap = 24.dp
private val RailGlyphSize = 13.sp
private val CapsuleTitleSize = 13.sp
private val CapsulePadStart = 14.dp
private val CapsulePadEnd = 4.dp
private val CapsulePadV = 4.dp
private val CapsuleGap = 10.dp
private val CapsuleMaxText = 170.dp
private val CapsuleText = Color(0xFF1A0C03)
private val DestructiveTint = Color(0xFFE2606A)

// The scrim: clear until the middle, then down to solid before it reaches the rail, so the icons
// sit on black rather than on a ramp. It was a 90%-alpha edge stop, which left the right THIRD
// still showing the wallpaper through it — "make the gradient darker on the right of the screen".
private val RailScrim = Color(0xFF080301)
private const val RailScrimStart = 0.5f
private const val RailScrimSolid = 0.82f

/** Rounded squares, not circles, at the app drawer's own corner ratio — a quarter of the side. */
private val RailCorner = 7.dp
