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
import com.psplauncher.feature.xmb.viewmodel.isInstalledApp
import com.psplauncher.feature.xmb.viewmodel.XMBItem

private val CardWidth: Dp = 104.dp

@Composable
fun LastPlayedPage(
    items: List<XMBItem>,
    selectedIndex: Int,
    content: DetailPanelContent?,
    page: DetailPanelPage,
    listState: LazyListState,

    directLaunch: Boolean,

    filter: RecentFilter,

    railVisible: Boolean,
    onPageTapped: (DetailPanelPage) -> Unit,
    onCardTapped: (Int) -> Unit,

    onArtTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focused = items.getOrNull(selectedIndex)

    LaunchedEffect(selectedIndex, railVisible) {
        if (railVisible && selectedIndex in items.indices) listState.animateScrollToItem(selectedIndex)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
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

                        titleFallback = true,
                        halfHeightLogo = true,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )

                    Spacer(Modifier.height(10.dp))
                    DetailPanelStrip(

                        pages = content.pages.filterNot { it == DetailPanelPage.LOGO },
                        current = page,
                        onPageTapped = onPageTapped,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                } else {
                    val focused = items.getOrNull(selectedIndex)
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = when {
                                focused != null -> focused.title
                                filter == RecentFilter.ALL -> "Nothing played yet."
                                else -> "No recent ${filter.label.lowercase()}."
                            },
                            color = LocalPfpTextColors.current.secondary,
                            fontSize = if (focused != null) 22.sp else 15.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(44.dp))
    }
}

@Composable
fun RecentFilterRow(
    filter: RecentFilter,
    modifier: Modifier = Modifier,

    onFilterTapped: (RecentFilter) -> Unit = {},

    includeApps: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,

                onClick = { onFilterTapped(filter.next(includeApps)) },
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = filter.label,

            color = Color.White,

            fontSize = StripFontSize,
            lineHeight = StripFontSize * 1.25f,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(shadow = XmbTextShadow),
        )
    }
}

@Composable
private fun RecentCard(item: XMBItem, focused: Boolean, onClick: () -> Unit) {
    PfpMediaCard(
        title = item.title,
        art = item.shelfCoverArt,
        subtitle = null,

        initialOnly = item.isInstalledApp,
        focused = focused,
        onClick = onClick,
        width = CardWidth,
        progress = item.progressFraction,
    )
}
