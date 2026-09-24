package com.psplauncher.feature.xmb.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.psplauncher.feature.xmb.viewmodel.XMBContextMenuItem

// ── The right rail, 9h ───────────────────────────────────────────────────────
//
// Replaces the PSP-style panel this file used to adapt. Actions stack up the right edge; the
// focused one is a white capsule and the rest fade by distance from it. Up and down move it,
// matching the column, and the ViewModel still owns the cursor: this draws the rows it is given
// and reports an index back. Which rows, and how many, is railRows.
//
// The shape of a row lives in XmbRailCapsule, which the Last Played shelf's Play control wears too.

@Composable
fun ContextMenuOverlay(
    rows: List<XMBContextMenuItem>,
    selectedIndex: Int,
    onItemActivated: (index: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                // Full width, and INSET from the two bars. "Put it below the header and footer so
                // I can still see those" — the strip carries the clock and the battery and the
                // hint bar carries what the buttons do, and a menu that hides both takes away the
                // two things that were true before it opened.
                //
                // StripHeight and SpinePromptClearance, not a pair of new numbers: those already
                // mean "how tall is the status strip" and "how far up does the prompt row reach",
                // and a copy of either would be a copy that stops agreeing.
                val top = StripHeight.toPx()
                val bottom = size.height - SpinePromptClearance.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        1f to RailScrim,
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, (bottom - top).coerceAtLeast(0f)),
                )
            },
    ) {
        // Catches the press that lands anywhere else, the same job the panel's own dim did.
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(RailRowGap),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = RailEdgeGap),
        ) {
            rows.forEachIndexed { index, row ->
                // The column's own ramp, stretched over this rail's length so a nine-row menu
                // fades all the way down instead of cliffing at the ramp's third step. One
                // definition, in XmbDim; tuning it there tunes the crossbar and the column too.
                val target = XmbDim.smoothed(kotlin.math.abs(index - selectedIndex), rows.lastIndex)
                // Animated, so a press slides the whole ramp instead of restamping it.
                val dim by animateFloatAsState(target, tween(DimFadeMs), label = "railDim")
                XmbRailRow(
                    label = row.label,
                    focused = index == selectedIndex,
                    destructive = row.isDestructive,
                    dim = dim,
                    onClick = { onItemActivated(index) },
                )
            }
        }
    }
}

/**
 * The wash behind the rail: clear at the left edge, [RailScrim] at the right.
 *
 * It has been three things. A fixed half-screen ramp, then one measured off the rail's own width
 * so it grew with the longest label, and now a plain full-width gradient at lower opacity — "have
 * the gradient be fully wide horizontal". The width-tracking version is gone rather than kept
 * alongside: two ways of deciding where the dark starts is one too many.
 */
private val RailScrim = Color(0xC4080301)
private const val DimFadeMs = 160
