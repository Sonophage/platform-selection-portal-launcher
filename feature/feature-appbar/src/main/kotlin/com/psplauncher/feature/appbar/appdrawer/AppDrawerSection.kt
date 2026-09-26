package com.psplauncher.feature.appbar.appdrawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.theme.StorefrontColors
import com.psplauncher.core.ui.theme.menuCursorEdge
import com.psplauncher.feature.appbar.AppFilter
import com.psplauncher.feature.appbar.InstalledApp
import com.psplauncher.feature.appbar.SECTION_LIST_ROWS

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppDrawerSection(
    matched: List<InstalledApp>,
    rest: List<InstalledApp>,
    selectedIndex: Int,
    usingTouch: Boolean,
    filter: AppFilter,
    onAppTapped: (Int) -> Unit,
    onAppLaunched: (String) -> Unit,
    onAppMenu: (InstalledApp) -> Unit,
    colors: StorefrontColors,

    onListRowsMeasured: (Int) -> Unit = {},
) {
    val rowState = rememberLazyListState()
    val listState = rememberLazyGridState()

    val rowCount = matched.size

    LaunchedEffect(selectedIndex, usingTouch, rowCount) {
        if (usingTouch) return@LaunchedEffect
        if (selectedIndex < rowCount) {
            if (matched.isNotEmpty()) rowState.animateScrollToItem(selectedIndex.coerceIn(0, matched.lastIndex))
        } else if (rest.isNotEmpty()) {
            listState.animateScrollToItem((selectedIndex - rowCount).coerceIn(0, rest.lastIndex))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        SectionHeading(filter.label, colors)
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(TileGap),
            contentPadding = PaddingValues(vertical = 6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(matched, key = { _, app -> app.packageName }) { index, app ->
                BigTile(
                    app = app,
                    focused = !usingTouch && index == selectedIndex,
                    colors = colors,
                    onClick = { onAppTapped(index); onAppLaunched(app.packageName) },
                    onLongClick = { onAppTapped(index); onAppMenu(app) },
                )
            }
        }

        if (rest.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionHeading(EverythingElse, colors)

            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val rows = (maxHeight / ListRowHeight).toInt().coerceIn(MIN_LIST_ROWS, MAX_LIST_ROWS)

                LaunchedEffect(rows) { onListRowsMeasured(rows) }
                LazyHorizontalGrid(
                    state = listState,

                    rows = GridCells.Fixed(rows),
                    horizontalArrangement = Arrangement.spacedBy(ColumnGap),
                    modifier = Modifier.fillMaxWidth().height(ListRowHeight * rows),
                ) {
                    gridItemsIndexed(rest, key = { _, app -> app.packageName }) { localIndex, app ->
                        val index = rowCount + localIndex
                        ListRow(
                            app = app,
                            focused = !usingTouch && index == selectedIndex,
                            colors = colors,
                            onClick = { onAppTapped(index); onAppLaunched(app.packageName) },
                            onLongClick = { onAppTapped(index); onAppMenu(app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String, colors: StorefrontColors) {
    Text(
        text = text,
        color = colors.textSecondary,
        fontSize = HeadingSize,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BigTile(
    app: InstalledApp,
    focused: Boolean,
    colors: StorefrontColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(TileCell)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Monogram(app.label, TileSize, TileCorner, TileGlyph, focused, colors)
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = colors.textPrimary,
            fontSize = TileLabelSize,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    app: InstalledApp,
    focused: Boolean,
    colors: StorefrontColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(ListColumnWidth)
            .height(ListRowHeight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Monogram(app.label, ListTileSize, ListTileCorner, ListGlyph, focused, colors)
        Spacer(Modifier.width(8.dp))
        Text(
            text = app.label,
            color = if (focused) colors.textPrimary else colors.textSecondary,
            fontSize = ListLabelSize,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Monogram(
    label: String,
    size: Dp,
    corner: Dp,
    glyphSize: TextUnit,
    focused: Boolean,
    colors: StorefrontColors,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(colors.accentHue.copy(alpha = if (focused) 0.38f else 0.14f))
            .then(if (focused) Modifier.border(2.dp, menuCursorEdge(), shape) else Modifier),
    ) {
        Text(

            text = label.trim().firstOrNull()?.uppercase() ?: "?",
            color = colors.textPrimary,
            fontSize = glyphSize,
            fontWeight = FontWeight.Bold,
        )
    }
}

private const val EverythingElse = "Everything else"

private val TileSize = 60.dp
private val TileCell = 68.dp
private val TileGap = 6.dp
private val TileCorner = 15.dp
private val TileGlyph = 25.sp
private val TileLabelSize = 12.sp
private val ListColumnWidth = 205.dp
private val ListRowHeight = 25.dp

private const val MIN_LIST_ROWS = 4
private const val MAX_LIST_ROWS = 12
private val ListTileSize = 20.dp
private val ListTileCorner = 5.dp
private val ListGlyph = 9.sp
private val ListLabelSize = 13.sp
private val ColumnGap = 17.dp
private val HeadingSize = 13.sp
