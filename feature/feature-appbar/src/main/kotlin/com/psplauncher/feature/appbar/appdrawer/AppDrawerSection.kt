package com.psplauncher.feature.appbar.appdrawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

// ── The 8q body ───────────────────────────────────────────────────────────────
//
// What every tab except All Apps draws: the tab's own apps as one large row that scrolls
// sideways, and every app that is NOT in the tab as a compact A-Z list under it, six rows tall,
// its columns running off to the right. Both halves scroll the same way, because the row is the
// one the owner asked to be able to "scroll to the left to view the hidden ones".
//
// [apps] is the matched apps followed by the rest, and [rowCount] is where the seam is, so this
// file never re-derives who belongs where — see SectionLayout.kt for why that matters.
//
// Monograms, not icons, by decision: a letter on a tile tinted to the current wave colour. The
// real Android icons are a different colour each, and the point of this view is that a tab reads
// as one block of things. The grid on All Apps still draws the icons.
//
// Sizes are the mock's pixels on its 1920x1080 frame divided by this panel's density of 2.3375,
// the same arithmetic ToastStyle does: 140px tile -> 60dp, 480px column -> 205dp, 58px row ->
// 25dp, 48px list tile -> 20.5dp.

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
) {
    val rowState = rememberLazyListState()
    val listState = rememberLazyGridState()
    // The seam, derived from the lists themselves rather than passed alongside them: a rowCount
    // that disagreed with the list it describes would put the cursor on a different app than the
    // one it is drawn under.
    val rowCount = matched.size

    // Two scrollers, one cursor: whichever half holds it gets scrolled to. Clamped, because a
    // cursor left past the end by a list that shrank mid-scroll would otherwise throw here.
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
            LazyHorizontalGrid(
                state = listState,
                // Fixed rows with items flowing rightward IS the mock's `grid-auto-flow: column`:
                // A-F down the first column, then G-L down the second. The cursor arithmetic in
                // sectionMove assumes exactly this, which is why left and right move by six.
                rows = GridCells.Fixed(SECTION_LIST_ROWS),
                horizontalArrangement = Arrangement.spacedBy(ColumnGap),
                modifier = Modifier.fillMaxWidth().height(ListRowHeight * SECTION_LIST_ROWS),
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

/**
 * A letter on a tile tinted to the current wave colour.
 *
 * [StorefrontColors.accentHue] rather than a fixed white wash, so the drawer changes with the
 * theme the way the rest of the launcher does. The focused tile is the same hue turned up, plus
 * the cursor edge every other focused thing in the app wears.
 */
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
            // The first character, whatever it is: an app called "8 Ball Pool" gets an 8, which
            // is the letter its A-Z position sorts under anyway.
            text = label.trim().firstOrNull()?.uppercase() ?: "?",
            color = colors.textPrimary,
            fontSize = glyphSize,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The list under the row is the tab's complement, so it says so rather than "All apps". */
private const val EverythingElse = "Everything else"

// The mock's pixels over 2.3375. The two label sizes are held at the bundle's own legibility
// floor of 28px (12sp) where the mock fell under it — the same call ToastStyle made, for the same
// reason: the floor is the rule the whole bundle is drawn against and 26px is a slip inside one
// card. The monogram glyphs keep the mock's ratio to their tile (0.42) instead, because a letter
// filling an icon-sized square is a glyph, not reading matter, and 12sp would not fit a 20dp box.
private val TileSize = 60.dp
private val TileCell = 68.dp
private val TileGap = 6.dp
private val TileCorner = 15.dp
private val TileGlyph = 25.sp
private val TileLabelSize = 12.sp
private val ListColumnWidth = 205.dp
private val ListRowHeight = 25.dp
private val ListTileSize = 20.dp
private val ListTileCorner = 5.dp
private val ListGlyph = 9.sp
private val ListLabelSize = 13.sp
private val ColumnGap = 17.dp
private val HeadingSize = 13.sp
