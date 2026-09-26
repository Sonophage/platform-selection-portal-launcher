package com.psplauncher.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.preview.CombinedPreviews
import com.psplauncher.core.ui.preview.PfpPreview

data class PspMenuRow(
    val label: String,
    val isDestructive: Boolean = false,

    val checked: Boolean = false,
)

const val NoMenuSelection = -1

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

    scrim: Color = XmbScrim,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (rows.isNotEmpty() && selectedIndex >= 0) {
            listState.animateScrollToItem(selectedIndex.coerceIn(0, rows.lastIndex))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.horizontalGradient(0f to Color.Transparent, 1f to scrim)),
    ) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = StatusStripHeight, bottom = HintBarHeight, end = RailEdgeGap),
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = RailTitleSize,
                fontWeight = FontWeight.Light,
                style = TextStyle(shadow = TextDropShadow),
                maxLines = 2,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = RailMaxText).padding(bottom = RailTitleGap),
            )

            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(RailRowGap),
                contentPadding = PaddingValues(bottom = RailRowGap),
            ) {
                itemsIndexed(rows) { index, row ->
                    val target = if (selectedIndex >= 0) {
                        XmbDim.smoothed(kotlin.math.abs(index - selectedIndex), rows.lastIndex)
                    } else {
                        XmbDim.smoothed(1, rows.lastIndex)
                    }
                    val dim by animateFloatAsState(target, tween(DimFadeMs), label = "railDim")

                    XmbRailRow(
                        label = row.label,
                        focused = index == selectedIndex,
                        destructive = row.isDestructive,
                        checked = row.checked,
                        dim = dim,
                        onClick = { onRowActivated(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun XmbRailRow(
    label: String,
    focused: Boolean,
    destructive: Boolean,
    checked: Boolean,
    dim: Float,
    onClick: () -> Unit,
) {
    val tint = if (destructive) RailDestructive else null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .alpha(if (focused) 1f else dim)
            .clip(RoundedCornerShape(RailCorner))
            .then(if (focused) Modifier.background(Color.White) else Modifier)
            .clickable(onClick = onClick)
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
        if (checked) {
            Spacer(Modifier.width(RailGap))
            PfpCheckMark(
                if (focused) RailInk else Color.White,
                size = 15.dp,
                shadow = TextDropShadow.color,
            )
        }
        Spacer(Modifier.width(RailGap))
        XmbRailBadge(label = label, filled = focused, tint = tint)
    }
}

@Composable
private fun XmbRailBadge(label: String, filled: Boolean, tint: Color? = null) {
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

val XmbScrim = Color(0xC4080301)

internal val RailIcon = 29.dp
internal val RailCorner = 7.dp
internal val RailEdgeGap = 24.dp
internal val RailRowGap = 13.dp
private const val DimFadeMs = 160
private val RailGlyphSize = 13.sp
private val RailTextSize = 13.sp
private val RailTitleSize = 17.sp
private val RailTitleGap = 18.dp
private val RailPadStart = 14.dp
private val RailPadEnd = 4.dp
private val RailPadV = 4.dp
private val RailGap = 10.dp
private val RailMaxText = 300.dp
private val RailInk = Color(0xFF1A0C03)
private val RailDestructive = Color(0xFFE2606A)

@CombinedPreviews
@Composable
fun PspContextMenuPreview() {
    val rows = listOf(
        PspMenuRow("Play"),
        PspMenuRow("Information"),
        PspMenuRow("Add to Favorites", checked = true),
        PspMenuRow("Assign Album"),
        PspMenuRow("Remove From Library", isDestructive = true),
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
