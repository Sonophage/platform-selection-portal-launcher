package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
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
import com.psplauncher.core.ui.detail.PfpDetailLaunchButton
import com.psplauncher.core.ui.components.PfpMediaCard
import com.psplauncher.core.ui.detail.detailPalette
import com.psplauncher.core.ui.image.rememberArtworkModel
import com.psplauncher.core.ui.theme.LocalPfpTextColors
import com.psplauncher.core.ui.theme.menuCursorEdge
import com.psplauncher.feature.xmb.ui.detail.DetailPanelContent
import com.psplauncher.feature.xmb.ui.detail.DetailPanelPage
import com.psplauncher.feature.xmb.ui.detail.DetailPanelStrip
import com.psplauncher.feature.xmb.ui.detail.GameDetailPanel
import com.psplauncher.feature.xmb.viewmodel.RecentFilter
import com.psplauncher.feature.xmb.viewmodel.XMBItem

// ── The home page ────────────────────────────────────────────────────────────
//
// Last Played is the leftmost column and the launcher opens on it. Standing here replaces the
// crossbar rather than adding to it: the caticon bar and the item list are gone, and the screen
// is the game you were last playing. RIGHT steps to the next category, which is how the bar
// returns.
//
// The shelf runs top to bottom in a left-hand gutter, so UP/DOWN change which game the screen is
// about and the hero gets the whole width beside it. That also means this file adds no D-pad
// handling at all: UP/DOWN are already the item cursor, and LEFT/RIGHT are already category
// steps.
//
// The hero is the same GameDetailPanel the crossbar hovers and the drill-down page uses, so its
// pages still walk with L1/R1 and a game still looks like itself in all three places.

// A portrait gutter, not a shelf. Narrow enough that the hero keeps the rest of the 1920px width
// and the column shows two and a half covers on the owner's 462 dp-tall screen.
private val CardWidth: Dp = 104.dp

@Composable
fun LastPlayedPage(
    items: List<XMBItem>,
    selectedIndex: Int,
    content: DetailPanelContent?,
    page: DetailPanelPage,
    listState: LazyListState,
    /**
     * What A does to a game right now. The button under the hero is the same action, so it says
     * Play when direct launch is on and Details when it is not — rather than promising one and
     * doing the other.
     */
    directLaunch: Boolean,
    /** Which media the shelf is showing. Named on screen because X cycles it blind otherwise. */
    filter: RecentFilter,
    onPageTapped: (DetailPanelPage) -> Unit,
    onCardTapped: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focused = items.getOrNull(selectedIndex)

    // Keep the focused card on screen. The column is laid out, not scrolled by the user, so
    // nothing else would bring a card back into view once the cursor walked past the edge.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) listState.animateScrollToItem(selectedIndex)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            // The other recents, newest first. The focused one wears the app's menu cursor, the
            // same bright edge every other focused thing in the shell wears.
            if (items.size > 1) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(CardWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        RecentCard(
                            item = item,
                            focused = index == selectedIndex,
                            onClick = { onCardTapped(index) },
                        )
                    }
                }
                Spacer(Modifier.width(28.dp))
            }

            Column(Modifier.weight(1f).fillMaxHeight()) {
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
                    // Where the RECENT badge used to be. The badge said what the column already
                    // says; this does something. Never focused — the cursor lives in the card
                    // column, and A on a card already fires this — so it reads as the label for
                    // the button you are holding rather than as a control to reach.
                    Spacer(Modifier.height(12.dp))
                    PfpDetailLaunchButton(
                        label = if (directLaunch) "Play" else "Details",
                        icon = if (directLaunch) Icons.Filled.PlayArrow else Icons.Filled.Info,
                        focused = false,
                        // Compact, because on this page it names the button in your hand rather
                        // than being a control to reach. At full size it was the loudest thing on
                        // a page whose subject is the artwork behind it.
                        compact = true,
                        onClick = { onCardTapped(selectedIndex) },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(118.dp),
                    )
                } else {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (filter == RecentFilter.ALL) "Nothing played yet."
                                   else "No recent ${filter.label.lowercase()}.",
                            color = LocalPfpTextColors.current.secondary,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            // What is focused, and -- when something is part-watched -- how much of it is left.
            // The bar on the card says there IS a resume point; this says what it costs.
            text = listOfNotNull(focused?.title, focused?.progressLabel).joinToString("  ·  ")
                .ifBlank { if (filter == RecentFilter.ALL) "Last Played" else "${filter.label} — nothing yet" },
            color = LocalPfpTextColors.current.primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // The hero art is the backdrop here, and it can be bright. Same shadow the crossbar's
            // row labels wear over the same kind of image.
            style = TextStyle(shadow = XmbTextShadow),
        )
        Spacer(Modifier.height(8.dp))
        // Every filter on one line, the active one lit. It used to name only the CURRENT filter,
        // which says what you are looking at but not what else there is or which way X goes --
        // so cycling was a guess until the label changed. A whole row costs one line and answers
        // both at a glance, the way the app drawer's tab strip does.
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            RecentFilter.entries.forEach { entry ->
                val active = entry == filter
                Text(
                    text = entry.label,
                    color = if (active) LocalPfpTextColors.current.primary
                            else LocalPfpTextColors.current.secondary.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    style = TextStyle(shadow = XmbTextShadow),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun RecentCard(item: XMBItem, focused: Boolean, onClick: () -> Unit) {
    // The shared card. This used to be its own renderer with its own focus edge, its own art
    // chain and its own missing-art fallback — three things the search grid and the app menu each
    // had a second copy of.
    PfpMediaCard(
        title = item.title,
        art = item.shelfCoverArt,
        subtitle = null,   // the hero above already names what this is
        focused = focused,
        onClick = onClick,
        width = CardWidth,
        progress = item.progressFraction,
    )
}
