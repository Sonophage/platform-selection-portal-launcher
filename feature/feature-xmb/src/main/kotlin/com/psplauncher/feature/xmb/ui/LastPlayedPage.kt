package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.detail.detailPalette
import com.psplauncher.core.ui.image.rememberArtworkModel
import com.psplauncher.core.ui.theme.LocalPfpTextColors
import com.psplauncher.core.ui.theme.menuCursorEdge
import com.psplauncher.feature.xmb.ui.detail.DetailPanelContent
import com.psplauncher.feature.xmb.ui.detail.DetailPanelPage
import com.psplauncher.feature.xmb.ui.detail.DetailPanelStrip
import com.psplauncher.feature.xmb.ui.detail.GameDetailPanel
import com.psplauncher.feature.xmb.viewmodel.XMBItem

// ── The home page ────────────────────────────────────────────────────────────
//
// Last Played is the leftmost column and the launcher opens on it. Standing here replaces the
// crossbar rather than adding to it: the caticon bar and the item list are gone, and the screen
// is the game you were last playing. RIGHT walks the other recents and then hands the screen
// back to the crossbar, which is how the bar returns.
//
// The hero is the same GameDetailPanel the crossbar hovers and the drill-down page uses, so its
// pages still walk with L1/R1 and a game still looks like itself in all three places.

// Small on purpose. The screen is 462 dp tall on the owner's handheld, so a 210 dp shelf took
// nearly half of it and left the hero a postage stamp — seen on the device. The hero is the page;
// the shelf is a way to reach the other four.
private val CardWidth: Dp = 96.dp
private val CardHeight: Dp = 134.dp

@Composable
fun LastPlayedPage(
    items: List<XMBItem>,
    selectedIndex: Int,
    content: DetailPanelContent?,
    page: DetailPanelPage,
    listState: LazyListState,
    onPageTapped: (DetailPanelPage) -> Unit,
    onCardTapped: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focused = items.getOrNull(selectedIndex)

    // Keep the focused card on screen. The row is laid out, not scrolled by the user, so nothing
    // else would bring a card back into view once the cursor walked past the edge.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) listState.animateScrollToItem(selectedIndex)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        Spacer(Modifier.height(10.dp))
        if (content != null) {
            DetailPanelStrip(
                pages = content.pages,
                current = page,
                onPageTapped = onPageTapped,
                modifier = Modifier.align(Alignment.End),
            )
            Spacer(Modifier.height(8.dp))
            GameDetailPanel(
                content = content,
                page = page,
                // No row label anywhere on this page, so a logo-less game is named here.
                titleFallback = true,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nothing played yet.",
                    color = LocalPfpTextColors.current.secondary,
                    fontSize = 15.sp,
                )
            }
        }

        // The other recents, newest first. The focused one wears the app's menu cursor, the same
        // bright edge every other focused thing in the shell wears.
        if (items.size > 1) {
            Spacer(Modifier.height(12.dp))
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    RecentCard(
                        item = item,
                        focused = index == selectedIndex,
                        onClick = { onCardTapped(index) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = focused?.let { "Last Played: ${it.title}" } ?: "Last Played",
            color = LocalPfpTextColors.current.primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // The hero art is the backdrop here, and it can be bright. Same shadow the crossbar's
            // row labels wear over the same kind of image.
            style = TextStyle(shadow = XmbTextShadow),
        )
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun RecentCard(item: XMBItem, focused: Boolean, onClick: () -> Unit) {
    val palette = detailPalette()
    val shape = RoundedCornerShape(10.dp)
    // Box art first: this row is a shelf of covers, and the landscape grid art the crossbar tiles
    // use reads as a screenshot at this size. Falls back to whatever the row does have.
    val art = item.boxArtUri ?: item.artworkUri ?: item.heroUri ?: item.iconUri
    Box(
        modifier = Modifier
            .width(CardWidth)
            .height(CardHeight)
            .clip(shape)
            .background(palette.rowFill)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) menuCursorEdge() else palette.rowEdge,
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            AsyncImage(
                model = rememberArtworkModel(art),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = item.title,
                color = palette.textMuted,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}
