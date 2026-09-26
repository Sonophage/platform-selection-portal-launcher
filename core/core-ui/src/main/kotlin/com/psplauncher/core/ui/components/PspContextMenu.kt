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
import androidx.compose.foundation.layout.PaddingValues
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

data class PspMenuRow(
    val label: String,
    val isDestructive: Boolean = false,

    val checked: Boolean = false,

    val heading: String? = null,
)

const val NoMenuSelection = -1

private val PanelWidth = 300.dp

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
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(PanelWidth)
                .background(lerp(colors.waveColor, Color.Black, 0.62f).copy(alpha = 0.96f))
                .clickable(onClick = {})
                .padding(start = 28.dp, end = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.92f),
                style = TextStyle(shadow = TextDropShadow),
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

                contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
            ) {
                itemsIndexed(rows) { index, row ->

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
    val glow = menuCursorEdge()
    Box(
        modifier = Modifier
            .fillMaxWidth()

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
