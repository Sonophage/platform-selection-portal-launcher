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
import androidx.compose.foundation.layout.height
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

const val NoMenuSelection = -1

private val TextDropShadow = Shadow(
    color = Color.Black.copy(alpha = 0.75f),
    offset = Offset(0f, 2f),
    blurRadius = 4f,
)

@Composable
fun <T> PspContextMenuOverlay(
    state: MenuState<T>,
    onRowActivated: (index: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,

    scrim: Color = XmbScrim,
) {
    val title = state.title
    val subtitle = state.subtitle
    val rows = state.rowsShown()
    val selectedIndex = state.selectedIndex ?: NoMenuSelection
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (rows.isNotEmpty() && selectedIndex >= 0) {
            listState.animateScrollToItem(selectedIndex.coerceIn(0, rows.lastIndex))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to scrim.copy(alpha = scrim.alpha * 0.45f),
                    1f to scrim,
                ),
            ),
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
                modifier = Modifier.widthIn(max = RailMaxText),
            )

            Text(
                text = subtitle.orEmpty(),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = RailSubtitleSize,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                style = TextStyle(shadow = TextDropShadow),
                maxLines = 1,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = RailMaxText),
            )

            Spacer(Modifier.height(RailTitleGap))

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
                        opensSubmenu = row.opensSubmenu,
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
    opensSubmenu: Boolean,
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
        XmbRailBadge(label = label, filled = focused, opensSubmenu = opensSubmenu, tint = tint)
    }
}

@Composable
private fun XmbRailBadge(
    label: String,
    filled: Boolean,
    opensSubmenu: Boolean = false,
    tint: Color? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(RailIcon)
            .clip(RoundedCornerShape(RailCorner))
            .background(
                when {
                    filled -> tint ?: RailInk
                    opensSubmenu -> Color.White
                    tint != null -> tint.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.12f)
                },
            ),
    ) {
        Text(
            text = label.trim().firstOrNull()?.uppercase() ?: "?",
            color = when {
                filled -> Color.White
                opensSubmenu -> RailInk
                else -> Color.White.copy(alpha = 0.85f)
            },
            fontSize = RailGlyphSize,
            fontWeight = FontWeight.Bold,
        )
    }
}

val XmbScrim = Color(0xF7050201)

internal val RailIcon = 29.dp
internal val RailCorner = 7.dp
internal val RailEdgeGap = 24.dp
internal val RailRowGap = 13.dp
private const val DimFadeMs = 160
private val RailGlyphSize = 13.sp
private val RailTextSize = 13.sp
private val RailTitleSize = 26.sp
private val RailSubtitleSize = 12.sp
private val RailTitleGap = 22.dp
private val RailPadStart = 14.dp
private val RailPadEnd = 4.dp
private val RailPadV = 4.dp
private val RailGap = 10.dp
private val RailMaxText = 300.dp
internal val RailInk = Color(0xFF1A0C03)
private val RailDestructive = Color(0xFFE2606A)

@CombinedPreviews
@Composable
fun PspContextMenuPreview() {
    val rows = listOf(
        MenuRow("play", "Play"),
        MenuRow("info", "Information"),
        MenuRow("fav", "Add to Favorites", checked = true),
        MenuRow<String>(null, "Settings", MenuGroup.SETTINGS, opensSubmenu = true),
        MenuRow("remove", "Remove From Library", isDestructive = true),
    )
    PfpPreview {
        PspContextMenuOverlay(
            state = MenuState("Gran Turismo 4", rows, subtitle = "Library", selectedIndex = 1),
            onRowActivated = {},
            onDismiss = {},
        )
    }
}
