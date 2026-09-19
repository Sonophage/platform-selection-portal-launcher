package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.core.ui.theme.menuCursorEdge
import com.psplauncher.core.ui.theme.solveScrimColor

// A themed, PSP-style right-edge context menu for the detail screens (Game / Photo / Video) whose
// option popups are driven by their own ViewModels rather than the XMB context-menu state. Visually
// identical to ContextMenuOverlay so every context menu in the app reads the same: a wave-color
// panel at 75% alpha anchored to the right edge, a titled header with a thin underline, and a
// horizontal accent glow on the selected row (no boxed rows). Follows the active color scheme.

private val DetailMenuWidth = 300.dp

/**
 * Panel opacity. It was 0.75, and at that value the page behind read straight through the list:
 * the game's title and its Launch button were legible ACROSS the menu rows. Raising it is the
 * whole fix -- solveScrimColor below does not help here, because it solves for text contrast,
 * which 0.75 already passed while still showing the page.
 */
private const val PANEL_ALPHA = 0.94f

private val DetailMenuTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.75f),
    offset = Offset(0f, 2f),
    blurRadius = 4f,
)

/**
 * One row in a [DetailContextMenu].
 *
 * [section] is a heading drawn ABOVE this row, not a row of its own. It rides on the row it
 * precedes so that a caller's row list stays index-for-index with the action list it was built
 * from: [selectedIndex] and [onRowClick] index the same list either way, and adding a heading can
 * never shift a selection onto the wrong action.
 */
data class DetailMenuRow(
    val label: String,
    val isDestructive: Boolean = false,
    val section: String? = null,
)

@Composable
fun DetailContextMenu(
    title: String,
    rows: List<DetailMenuRow>,
    selectedIndex: Int,
    onRowClick: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
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
            // Light scrim so the wave/photo stays visible behind, PSP-style; tap to dismiss.
            .background(Color(0x40000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(DetailMenuWidth)
                // solveScrimColor still guards the mirror case [PANEL_ALPHA] cannot: a theme
                // whose wave is itself near-white, where white rows would vanish at any opacity.
                // It darkens the wave along its own hue by the least amount that carries white
                // body text. Same solver the settings backdrop uses.
                .background(solveScrimColor(colors.waveColor, alpha = PANEL_ALPHA).copy(alpha = PANEL_ALPHA))
                // Consume clicks inside the panel so the scrim's dismiss doesn't fire.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(start = 28.dp, end = 40.dp, top = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.92f),
                style = TextStyle(shadow = DetailMenuTextShadow),
                maxLines = 2,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.30f)),
            )

            LazyColumn(
                state = listState,
                // A list longer than the panel used to end flush with the screen edge, so the
                // last row (Remove, on a game) was cut in half even while selected.
                contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
            ) {
                itemsIndexed(rows) { index, row ->
                    row.section?.let { DetailMenuSectionHeader(it, isFirst = index == 0) }
                    DetailMenuRowView(
                        row = row,
                        isSelected = index == selectedIndex,
                        onClick = { onRowClick(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailMenuSectionHeader(label: String, isFirst: Boolean) {
    Text(
        text = label.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 2.4.sp,
        color = Color.White.copy(alpha = 0.45f),
        style = TextStyle(shadow = DetailMenuTextShadow),
        modifier = Modifier.padding(top = if (isFirst) 0.dp else 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun DetailMenuRowView(
    row: DetailMenuRow,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val glow = menuCursorEdge()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) {
                    Brush.horizontalGradient(0f to Color.Transparent, 1f to glow.copy(alpha = 0.40f))
                } else {
                    Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Transparent)
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
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
            style = TextStyle(shadow = DetailMenuTextShadow),
        )
    }
}
