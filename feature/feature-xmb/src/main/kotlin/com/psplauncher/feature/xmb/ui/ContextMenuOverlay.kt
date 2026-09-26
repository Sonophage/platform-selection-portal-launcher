package com.psplauncher.feature.xmb.ui

import androidx.compose.animation.core.animateFloatAsState
import com.psplauncher.core.ui.components.StatusStripHeight
import com.psplauncher.core.ui.components.HintBarHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.psplauncher.feature.xmb.viewmodel.XMBContextMenuItem

// ── The right rail, 9h ───────────────────────────────────────────────────────
//
// Replaces the PSP-style panel this file used to adapt. Actions stack up the right edge; the
// focused one is a white capsule and the rest fade by distance from it. Up and down move it,
// matching the column, and the ViewModel still owns the cursor: this draws the rows it is given
// and reports an index back. Which rows, and how many, is railRows.
//
// The shape of a row lives in XmbRailCapsule. This is its only caller — see the note there.

@Composable
fun ContextMenuOverlay(
    rows: List<XMBContextMenuItem>,
    /** Null until the cursor is moved onto a row — see XMBContextMenu.selectedIndex. */
    selectedIndex: Int?,
    onItemActivated: (index: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            // Full screen. The status strip and the hint bar stay readable by drawing ON TOP of
            // this rather than by it stopping short of them — an inset scrim left a horizontal
            // seam across the whole width where it ended, which is a rule drawn on the screen for
            // no reason anyone looking at it could name.
            .background(Brush.horizontalGradient(0f to Color.Transparent, 1f to XmbScrim)),
    ) {
        // Catches the press that lands anywhere else, the same job the panel's own dim did.
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
        // The rail scrolls, and it stops at the chrome.
        //
        // It was an unbounded Column centred on the right edge: ten rows ran past both bands, so
        // the top row was drawn behind the clock and the bottom behind the hint bar, and there
        // was no way to reach either. The scrim's own comment reasons about staying clear of the
        // chrome — that was about the WASH; nothing stopped the rows themselves.
        //
        // verticalScroll rather than a LazyColumn: a context menu is a handful of rows that are
        // all composed anyway, and the cursor is the ViewModel's, so there is nothing to
        // virtualise and no scroll state to keep in step with an index.
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(RailRowGap),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                // Reserve both bands, then take what is left. The padding is OUTSIDE the scroll
                // so the rows scroll within the gap rather than under it.
                .padding(top = StatusStripHeight, bottom = HintBarHeight)
                .verticalScroll(rememberScrollState())
                .padding(end = RailEdgeGap),
        ) {
            rows.forEachIndexed { index, row ->
                // The column's own ramp, stretched over this rail's length so a nine-row menu
                // fades all the way down instead of cliffing at the ramp's third step. One
                // definition, in XmbDim; tuning it there tunes the crossbar and the column too.
                // Nothing picked: every row sits at the ramp's first unselected stop. Measuring
                // the distance from "nowhere" would draw a gradient pointing at a cursor that is
                // not there — brightest at the top, which reads exactly like row one is focused.
                val target = selectedIndex
                    ?.let { XmbDim.smoothed(kotlin.math.abs(index - it), rows.lastIndex) }
                    ?: XmbDim.smoothed(1, rows.lastIndex)
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

// The wash behind the rail is XmbScrim, ramped left to right. It has been three things: a fixed
// half-screen ramp, then one measured off the rail's own width so it grew with the longest label,
// and now a plain full-width gradient. The width-tracking version is gone rather than kept beside
// it — two ways of deciding where the dark starts is one too many.
private const val DimFadeMs = 160
