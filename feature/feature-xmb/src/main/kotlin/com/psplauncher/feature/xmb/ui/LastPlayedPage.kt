package com.psplauncher.feature.xmb.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.components.PfpMediaCard
import com.psplauncher.core.ui.theme.LocalPfpTextColors
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
    /**
     * Whether the column of other recents is in.
     *
     * Hidden is the default and the cursor walks the recents either way: what this changes is
     * whether the cards are DRAWN, never what UP and DOWN do. A hidden rail that also froze the
     * cursor would make the page a dead end until you found the gesture that opens it.
     */
    railVisible: Boolean,
    onPageTapped: (DetailPanelPage) -> Unit,
    onCardTapped: (Int) -> Unit,
    /** Tapping the artwork shows or hides the rail — touch's version of LEFT and RIGHT here. */
    onArtTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focused = items.getOrNull(selectedIndex)

    // Keep the focused card on screen. The column is laid out, not scrolled by the user, so
    // nothing else would bring a card back into view once the cursor walked past the edge.
    // Keyed on both: with the rail away the LazyColumn is not composed, so a scroll issued while
    // it was hidden goes nowhere and the cards come back showing the wrong window. Re-running it
    // when the rail arrives is what puts the focused card under the cursor.
    LaunchedEffect(selectedIndex, railVisible) {
        if (railVisible && selectedIndex in items.indices) listState.animateScrollToItem(selectedIndex)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            // The other recents, newest first. The focused one wears the app's menu cursor, the
            // same bright edge every other focused thing in the shell wears.
            // Slides in from the edge it lives on rather than fading: a fade would have the
            // cards appear on top of the artwork, and the point of the gesture is that a column
            // comes IN from the left.
            AnimatedVisibility(
                visible = railVisible && items.size > 1,
                enter = slideInHorizontally(tween(220)) { -it } + fadeIn(tween(220)),
                exit = slideOutHorizontally(tween(180)) { -it } + fadeOut(tween(180)),
            ) {
                Row {
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
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // The artwork is the rail's touch handle. Without this the shelf is the one
                    // screen a fresh install lands on where touch can see a single item and has
                    // no way to reach the rest — the rail came in on LEFT and nothing else.
                    // Deliberately NOT launch: Play has its own spine down the right edge, and a
                    // page whose whole surface launches something is a page you cannot explore.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onArtTapped,
                    ),
            ) {
                Spacer(Modifier.height(10.dp))
                if (content != null) {
                    GameDetailPanel(
                        content = content,
                        page = page,
                        // No row label anywhere on this page, so a logo-less game is named here.
                        titleFallback = true,
                        halfHeightLogo = true,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    // The tabs sit UNDER what they are switching, centred, between the title and
                    // the Play legend -- the order the reference stacks them in. They used to be
                    // pinned to the top-right corner, which put the row that says "there is more
                    // to see" as far from the thing it changes as the page allows, and made it
                    // read as chrome belonging to the status bar above it.
                    Spacer(Modifier.height(10.dp))
                    DetailPanelStrip(
                        // LOGO is not offered here. On a detail screen it is the resting state
                        // you walk back to; here the logo is the page's headline, drawn above
                        // this row whichever tab is current, so a tab for it would be a tab that
                        // changes nothing you can see.
                        pages = content.pages.filterNot { it == DetailPanelPage.LOGO },
                        current = page,
                        onPageTapped = onPageTapped,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
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

        // The title used to be repeated here, under the artwork that already says it in letters
        // ten times the size, with the filter names under that again. Both are gone: the filters
        // now sit in the centre of the top bar (see RecentFilterRow, placed by XMBShell) and the
        // title is the artwork.
        // Clears the corner pill. The tabs sit at the foot of the content column, and the pill
        // is drawn over this page by the shell -- without this the two overlap and the shoulders
        // come out from behind "Filter".
        Spacer(Modifier.height(44.dp))
    }
}

/**
 * The media filter, for the centre of the top bar.
 *
 * Lives here rather than in the strip because the strip knows nothing about recents and should
 * not learn: it is handed a slot and this fills it.
 *
 * Every filter named, the active one lit. Naming only the CURRENT one says what you are looking
 * at but not what else there is or which way X goes, so cycling was a guess until the label
 * changed.
 */
@Composable
fun RecentFilterRow(
    filter: RecentFilter,
    modifier: Modifier = Modifier,
    /**
     * Pick this filter. Every name is on screen at once, so a finger can go straight to the one
     * it wants — X still cycles, and did so alone until these became pressable.
     */
    onFilterTapped: (RecentFilter) -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecentFilter.entries.forEach { entry ->
            val active = entry == filter
            // The target is the full height of the strip and no more.
            //
            // It was `padding(vertical = 8.dp)` for one build, which is the obvious way to make
            // 8sp chrome hittable and is wrong here: the strip is a fixed 18.dp Box, so padding
            // grew each name past its container and the labels came out clipped to a few pixels
            // of glyph on a tablet. Filling the height gets the same press out of the space that
            // actually exists, and widening happens sideways, where there is room.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onFilterTapped(entry) },
                    )
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
            Text(
                text = entry.label,
                // White either way, dimmed rather than recoloured. These sit over whatever
                // artwork the focused item brought, and the theme is re-tinted from that same
                // artwork -- a themed colour here is drawn FROM the picture it must be read
                // against, which on a gold frame came out gold on gold.
                color = Color.White.copy(alpha = if (active) 1f else 0.45f),
                // Chrome-sized, like everything else in this band.
                fontSize = 8.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                style = TextStyle(shadow = XmbTextShadow),
            )
            }
        }
    }
}

/**
 * The launch spine: a bar down the edge, in the focused item's own colour, with the word running
 * down it and a shimmer travelling through.
 *
 * It replaces a Play pill that sat in the middle of the page -- a control in the one place the
 * cursor never goes, competing with the artwork for the centre. The spine is at the edge, says
 * the same word, and is pressable.
 *
 * The colour is the AMBIENT accent, not a parameter. This page is already re-tinted from the
 * focused item's artwork -- it is why the whole screen goes red on one row and gold on the next --
 * so reading the theme gets the item's colour for free and cannot disagree with the page it sits
 * on. A second derivation would be a second answer to the same question.
 *
 * The shimmer is on the WORD, travelling top to bottom through the letters -- the bar underneath
 * is a flat wash. It is what makes an edge read as something you can press rather than as a rule:
 * a static strip of colour is chrome, and one with movement in it is an invitation.
 */
/**
 * How far above the bottom edge the launch control ends, so the prompt pills in that corner stay
 * pressable. Sized to clear the hint bar, which is its glyph height plus its own small padding.
 *
 * Internal because the context rail's scrim insets itself by it: "how far up does the prompt row
 * reach" is one fact and the scrim needs the same answer this does.
 */
internal val SpinePromptClearance = 52.dp

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
