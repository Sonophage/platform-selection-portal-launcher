package com.psplauncher.core.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RowShape = RoundedCornerShape(10.dp)

@Composable
private fun RowShell(
    focused: Boolean,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)

            .background(DetailRowFill, RowShape)
            .detailFocusRing(
                focused = focused,
                edge = DetailFocusEdge,
                fill = DetailFocusEdge.copy(alpha = 0.10f),
                shape = RowShape,
            )
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        content()
    }
}

@Composable
fun PfpDetailProgressRow(
    label: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    focused: Boolean = false,
    disclosure: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    RowShell(focused = focused, modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    color = DetailTextMuted.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = value,
                        color = DetailTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    if (secondary != null) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = secondary,
                            color = DetailTextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                PfpDetailProgressBar(
                    progress = progress,
                    track = detailPalette().track,
                    fill = DetailFocusEdge,
                )
            }
            if (disclosure) {
                Spacer(Modifier.width(10.dp))
                PfpChevronMark(color = DetailTextMuted, size = 14.dp)
            }
        }
    }
}

@Composable
fun PfpDetailProgressBar(
    progress: Float,
    track: Color,
    fill: Color,
    modifier: Modifier = Modifier,
) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(track),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(fill),
            )
        }
    }
}

@Composable
fun PfpDetailTextRow(
    label: String,
    text: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 3,
    focused: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    RowShell(focused = focused, modifier = modifier, onClick = onClick) {
        Text(
            text = label.uppercase(),
            color = DetailTextMuted.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = text,
            color = DetailTextPrimary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) {
            Spacer(Modifier.size(6.dp))
            Text(
                text = if (expanded) "Confirm to collapse" else "Confirm to read more",
                color = if (focused) DetailFocusEdge else DetailTextMuted.copy(alpha = 0.7f),
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun PfpDetailFieldBand(
    focused: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)

            .background(DetailRowFill, RowShape)
            .detailFocusRing(
                focused = focused,
                edge = DetailFocusEdge,
                fill = DetailFocusEdge.copy(alpha = 0.10f),
                shape = RowShape,
            )
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        WrappingFieldGrid(content = content)
    }
}

@Composable
fun PfpDetailField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    focused: Boolean = false,
    disclosure: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .then(
                if (focused) {
                    Modifier
                        .background(DetailFocusEdge.copy(alpha = 0.18f), shape)
                        .border(1.5.dp, DetailFocusEdge, shape)
                } else {
                    Modifier
                }
            )
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = if (focused) 8.dp else 0.dp, vertical = 4.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = DetailTextMuted.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                color = DetailTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (disclosure) {
                Spacer(Modifier.width(6.dp))
                PfpChevronMark(color = DetailTextMuted, size = 12.dp)
            }
        }
        if (secondary != null) {
            Text(
                text = secondary,
                color = DetailTextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WrappingFieldGrid(
    modifier: Modifier = Modifier,
    minCellWidth: Dp = 210.dp,
    horizontalGap: Dp = 18.dp,
    verticalGap: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gapPx = horizontalGap.roundToPx()
        val vGapPx = verticalGap.roundToPx()
        val minCellPx = minCellWidth.roundToPx().coerceAtLeast(1)
        val maxWidth = constraints.maxWidth
        val columns = ((maxWidth + gapPx) / (minCellPx + gapPx)).coerceAtLeast(1)
        val cellWidth = ((maxWidth - gapPx * (columns - 1)) / columns).coerceAtLeast(1)

        val placeables = ArrayList<Placeable>(measurables.size)
        val rowHeights = ArrayList<Int>()
        measurables.chunked(columns).forEach { row ->
            val placed = row.map { it.measure(Constraints.fixedWidth(cellWidth)) }
            placeables += placed
            rowHeights += placed.maxOf { it.height }
        }

        val totalHeight = rowHeights.sum() + vGapPx * (rowHeights.size - 1).coerceAtLeast(0)
        layout(maxWidth, totalHeight) {
            var y = 0
            var index = 0
            rowHeights.forEach { rowHeight ->
                var x = 0
                repeat(columns) {
                    if (index < placeables.size) {
                        placeables[index].placeRelative(x, y)
                        index++
                    }
                    x += cellWidth + gapPx
                }
                y += rowHeight + vGapPx
            }
        }
    }
}

val DetailRowSpacing: Dp = 12.dp
