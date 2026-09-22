package com.psplauncher.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.preview.CombinedPreviews
import com.psplauncher.core.ui.preview.PfpPreview
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.core.ui.theme.menuCursorEdge

// ── PSP-style context menu panel ──────────────────────────────────────────────
//
// The canonical XMB sub-menu look: a translucent column anchored to the right
// edge over a light scrim, a plain title underlined by a thin rule, and the
// selected item marked by a soft horizontal glow band that bleeds to the screen
// edge (no boxed panel). Shared by the XMB's Y/Triangle menu and any settings
// screen that opens a per-item options menu — one source, no style drift.
//
// Controller navigation is the caller's job (selectedIndex in, activation out);
// this composable handles touch/click interaction.

/** One row of a [PspContextMenuOverlay]. */
data class PspMenuRow(
    val label: String,
    val isDestructive: Boolean = false,
    // Marks a current membership/selection (e.g. collections the item already belongs to).
    val checked: Boolean = false,
    // Group heading drawn above this row. Not a row itself: the cursor never lands on it, and
    // the index the caller gets back is still the index into this list.
    val heading: String? = null,
)

private val PanelWidth = 300.dp

// Black drop shadow on the menu text so it stays legible over the wave/backdrop.
private val TextDropShadow = Shadow(
    color = Color.Black.copy(alpha = 0.75f),
    offset = Offset(0f, 2f),
    blurRadius = 4f,
)

@Composable
fun PspContextMenuOverlay(
    title: String,
    rows: List<PspMenuRow>,
    selectedIndex: Int,
    onRowActivated: (index: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    // Dims what is behind the menu. This was 0x40 — 25% black — which is a fine scrim over a
    // plain gradient and far too little over artwork: on a game's options menu the hero art and
    // the metadata line stayed at near-full brightness right up to the panel's edge. The hardware
    // dims the whole cross hard behind its options menu, and the menu is modal here too.
    scrim: Color = Color(0x99000000),
) {
    val colors = LocalPFPColors.current
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (rows.isNotEmpty()) {
            listState.animateScrollToItem(selectedIndex.coerceIn(0, rows.size - 1))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrim)
            .clickable(onClick = onDismiss),
    ) {
        // Right-edge column: the theme's HUE at a surface's darkness, not the theme's colour.
        //
        // Two failures got fixed here and the second was only visible after the first. At alpha
        // 0.75 the panel was a wash, so on a game's flyout the PIC0 logo and the metadata line
        // read straight THROUGH it — "Change Emulator" sat on the word "FANTASY". Making it
        // opaque stopped that and revealed why the wash had been hiding it: on a game flyout the
        // wave colour is tinted by the ARTWORK, so an opaque panel came out as a full-strength
        // slab of whatever the box art happened to be — scarlet for one game, gold for another —
        // and the destructive row went red on red.
        //
        // So the hue is kept, because the menu should belong to the theme and to the game, and
        // the value is taken down to where a list of white labels and one red one both read. The
        // wave showing through is the scrim's job, never the panel's.
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(PanelWidth)
                .background(lerp(colors.waveColor, Color.Black, 0.62f).copy(alpha = 0.96f))
                .clickable(onClick = {}) // consume clicks so the scrim isn't triggered inside
                .padding(start = 28.dp, end = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            // ── Title ─────────────────────────────────────────────────────
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.92f),
                style = TextStyle(shadow = TextDropShadow),
                maxLines = 2,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            // Thin underline rule beneath the title.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.30f)),
            )

            // ── Items ──────────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                itemsIndexed(rows) { index, row ->
                    // The heading is drawn INSIDE the row's item, above it: it belongs to this
                    // row, and a separate list item would be one the cursor index has to skip.
                    row.heading?.let { heading ->
                        Text(
                            text = heading,
                            color = colors.textSecondary.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp,
                            style = LocalTextStyle.current.copy(shadow = TextDropShadow),
                            modifier = Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = if (index == 0) 0.dp else 12.dp,
                                bottom = 4.dp,
                            ),
                        )
                    }
                    PspContextMenuRow(
                        row        = row,
                        isSelected = index == selectedIndex,
                        onClick    = { onRowActivated(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PspContextMenuRow(
    row: PspMenuRow,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    // Accent-tinted glow so the cursor follows the chosen color scheme; blended toward white in
    // menuCursorEdge so a dark theme accent still reads clearly on the scrim.
    val glow = menuCursorEdge()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Horizontal glow band for the active item — brighter toward the
            // screen edge, fading out to the left. No border or rounded box.
            .background(
                if (isSelected) {
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        1f to glow.copy(alpha = 0.40f),
                    )
                } else {
                    Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Transparent)
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.label,
                fontSize = if (isSelected) 16.sp else 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    row.isDestructive && isSelected -> Color(0xFFFF7070)
                    row.isDestructive               -> Color(0xAAFF7070)
                    isSelected                      -> Color.White
                    else                            -> Color.White.copy(alpha = 0.62f)
                },
                style = TextStyle(shadow = TextDropShadow),
                modifier = Modifier.weight(1f, fill = false),
            )
            if (row.checked) {
                Spacer(Modifier.width(10.dp))
                PfpCheckMark(Color.White, size = 15.dp, shadow = TextDropShadow.color)
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@CombinedPreviews
@Composable
fun PspContextMenuPreview() {
    val rows = listOf(
        PspMenuRow("Play"),
        PspMenuRow("Information"),
        PspMenuRow("Delete", isDestructive = true),
        PspMenuRow("Add to Favorites", checked = true),
        PspMenuRow("Assign Album"),
    )
    PfpPreview {
        PspContextMenuOverlay(
            title = "Gran Turismo 4",
            rows = rows,
            selectedIndex = 1,
            onRowActivated = {},
            onDismiss = {},
        )
    }
}
