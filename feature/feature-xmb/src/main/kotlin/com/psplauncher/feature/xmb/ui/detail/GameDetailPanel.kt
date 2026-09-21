package com.psplauncher.feature.xmb.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.detail.DetailMediaTileHeight
import com.psplauncher.core.ui.detail.DetailMediaTileWidth
import com.psplauncher.core.ui.detail.PfpDetailMediaTile
import com.psplauncher.core.ui.detail.detailPalette
import androidx.compose.ui.draw.clip
import com.psplauncher.core.ui.image.rememberArtworkModel
import com.psplauncher.feature.xmb.ui.Icon1VideoOverlay
import com.psplauncher.core.ui.theme.LocalPfpTextColors

// ── The Game Detail panel ────────────────────────────────────────────────────
//
// One component, two hosts. The crossbar draws it in its open right-hand side for whichever game
// the cursor is hovering; the drill-down page draws it full width for the game that was opened.
// Both walk it with L1/R1 and neither owns a game list — the crossbar already IS the list, which
// is why the drill-down does not grow a second one.
//
// It deliberately draws no footer and no Play button. Those belong to the host: the crossbar has
// its own chrome along the bottom and a second row of prompts there would be two footers arguing,
// and the drill-down's footer carries actions the hover state must not offer.

/** The strip's icon for a page. Its own function so the strip and any prompt cannot disagree. */
private fun DetailPanelPage.icon(): ImageVector = when (this) {
    DetailPanelPage.LOGO -> Icons.Filled.PictureInPictureAlt
    DetailPanelPage.BOX_ART -> Icons.Filled.Inventory2
    DetailPanelPage.VIDEO -> Icons.Filled.PlayCircleOutline
    DetailPanelPage.GALLERY -> Icons.Filled.Image
    DetailPanelPage.INFO -> Icons.Filled.Info
}

private val StripIconSize: Dp = 20.dp
private val StripCellSize: Dp = 28.dp
private val PanelCardShape = RoundedCornerShape(14.dp)

/**
 * The page strip, wearing the helper footer's pill.
 *
 * Every other pill in the shell is a ControllerHintBar — black at half alpha, a 10 dp corner and
 * 8 by 4 of padding around 20 dp glyphs — and this one sits in the opposite corner of the same
 * screen. The numbers are taken from that component rather than chosen again here, because two
 * copies of a number with a comment saying they must match is how a shell stops looking like one
 * thing. It is not a ControllerHintBar itself: those are a glyph plus a label with no selected
 * state, and this is a set of tabs where exactly one is current.
 *
 * Drawn even at one page: it is what says L1/R1 do anything here, and hiding it at one page would
 * make the shoulders look dead on exactly the games with the least artwork — the ones most worth
 * going to look for a scrape on.
 */
@Composable
fun DetailPanelStrip(
    pages: List<DetailPanelPage>,
    current: DetailPanelPage,
    modifier: Modifier = Modifier,
    onPageTapped: ((DetailPanelPage) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEach { page ->
            val selected = page == current
            Box(
                modifier = Modifier
                    .size(StripCellSize)
                    .background(
                        if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent,
                        RoundedCornerShape(6.dp),
                    )
                    .then(
                        if (onPageTapped != null) Modifier.clickable { onPageTapped(page) }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = page.icon(),
                    contentDescription = page.label,
                    // The footer's labels are white; its unselected state is the absence of a
                    // prompt rather than a dim one, so the dimming here is this component's own.
                    tint = if (selected) Color.White else Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(StripIconSize),
                )
            }
        }
    }
}

/**
 * The panel body for one page.
 *
 * [onMediaTapped] is null in the hover host: the crossbar's cursor is in the item list, so a media
 * tile there is something to look at and not something to reach.
 */
@Composable
fun GameDetailPanel(
    content: DetailPanelContent,
    page: DetailPanelPage,
    modifier: Modifier = Modifier,
    titleFallback: Boolean = true,
    /**
     * The media tile the host's cursor is on, by stable id, or null when the host has no cursor
     * in here. The crossbar passes null: its cursor is in the item list, so a tile there is
     * something to look at and not something to reach.
     */
    focusedMediaId: String? = null,
    onMediaTapped: ((DetailMedia) -> Unit)? = null,
) {
    // The strip is NOT drawn here. It lives in the host's chrome, under the top bar, so the whole
    // of this region belongs to the page — which is the difference between a box front you can
    // read and one you can identify.
    Column(modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // resolvePanelPage, not page, so a page that stops being available while the panel
            // is open (a scrape filling in box art, the cursor moving to a game with less art)
            // cannot leave the body drawing something the strip is no longer offering.
            when (resolvePanelPage(page, content.pages)) {
                DetailPanelPage.LOGO -> LogoPage(content, titleFallback)
                DetailPanelPage.BOX_ART -> BoxArtPage(content)
                DetailPanelPage.VIDEO -> VideoPage(content)
                DetailPanelPage.GALLERY -> GalleryPage(content, focusedMediaId, onMediaTapped)
                DetailPanelPage.INFO -> InfoPage(content)
            }
        }
    }
}

/**
 * The logo, over the game's own art, with nothing framing it — the art is the page.
 *
 * [titleFallback] is false on the crossbar and true in the drill-down, and that is not a style
 * choice. XMBItemList hides a row's title exactly when the shell draws its logo — one predicate,
 * hasVisibleLogo, read by both halves so they cannot disagree — so a logo-less game is already
 * showing its title in the list, and a title card here would be the crossbar saying it twice.
 * The drill-down has no such list, so there it is the only thing naming the game.
 */
@Composable
private fun LogoPage(content: DetailPanelContent, titleFallback: Boolean) {
    val logo = content.logoUri
    if (logo != null) {
        AsyncImage(
            model = rememberArtworkModel(logo),
            contentDescription = content.title,
            // Fit, never Crop: a trimmed logo is a wordmark with a letter missing.
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(8.dp),
        )
    } else if (titleFallback) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = content.title,
                color = LocalPfpTextColors.current.primary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(text = content.platformName, color = LocalPfpTextColors.current.secondary, fontSize = 13.sp)
        }
    }
}

/** Box art, contained. Only offered when [Game.boxArtUri] is set, so there is no empty state. */
@Composable
private fun BoxArtPage(content: DetailPanelContent) {
    AsyncImage(
        model = rememberArtworkModel(content.boxArtUri),
        contentDescription = content.title,
        // Fit for the same reason as the logo, and more so: box art is the one asset whose aspect
        // ratio carries information (a tall GBA box is not a square PS1 case).
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().padding(8.dp),
    )
}

/**
 * The game's video snap, playing in the panel.
 *
 * This page IS the snap's render site while it is open — the tile and the full-bleed background
 * both stand down (snapSiteFor returns PANEL), so there is still exactly one decoder on the clip.
 * Only offered when there is a video, so there is no empty state.
 */
@Composable
private fun VideoPage(content: DetailPanelContent) {
    val uri = content.videoUri ?: return
    Icon1VideoOverlay(
        videoUri = uri,
        // The overlay centre-crops to whatever bounds it is given, so the page's own box is all
        // the framing it needs.
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
    )
}

/** The media strip, as a page. Only offered when there is media, so there is no empty state. */
@Composable
private fun GalleryPage(
    content: DetailPanelContent,
    focusedMediaId: String?,
    onMediaTapped: ((DetailMedia) -> Unit)?,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(content.media, key = { mediaStableId(it) }) { item ->
            PfpDetailMediaTile(
                uri = item.uri,
                isVideo = item.isVideo,
                // The host says which tile is focused; the tile never decides for itself. The
                // hover panel passes null because its cursor is in the crossbar's item list.
                focused = mediaStableId(item) == focusedMediaId,
                onClick = { onMediaTapped?.invoke(item) },
                posterFallbackUri = content.posterFallbackUri,
                contentDescription = if (item.isVideo) "Video" else "Screenshot",
                modifier = Modifier.width(DetailMediaTileWidth).height(DetailMediaTileHeight),
            )
        }
    }
}

/**
 * The information card: the meta line, the description, then the two names — the title the library
 * shows and the filename on disk. Modelled on NeoStation's info page, which is a translucent card
 * over the art rather than a page of its own.
 */
@Composable
private fun InfoPage(content: DetailPanelContent) {
    val palette = detailPalette()
    Column(
        modifier = Modifier
            // Full height on purpose. The card's rows have to fit the region exactly: sized to
            // its content it overflowed and the bottom row was clipped away silently, and no
            // constant for the description's height survives a different region or density.
            // Bounded here, the description can take what is left and the filename always fits.
            .fillMaxSize()
            .background(palette.rowFill, PanelCardShape)
            .border(1.dp, palette.rowEdge, PanelCardShape)
            .padding(18.dp),
    ) {
        Text(
            text = content.metaLine ?: content.platformName,
            color = palette.textMuted,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = content.description?.takeIf { it.isNotBlank() } ?: "No description available.",
            color = palette.textPrimary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            // heightIn, not weight. This card wraps its height, and weight() divides the space
            // LEFT OVER in a column that has some — a wrap-height column has none, so the
            // description measured to zero and silently took the two rows under it off the card
            // with it. Seen on the device: the Info page rendered as a meta line and a void.
            // weight, now that the column above it has a bounded height to divide. This is the
            // same modifier that measured to zero when the column wrapped its content — the fix
            // was never the modifier, it was giving it something to take a share of.
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(12.dp))
        // The card names the game again. It did not, on the grounds that the crossbar row was
        // already doing it — but the row's label yields to the panel now (see focusedPanelVisible),
        // so dropping it here would leave the Info page as the one view that names nothing.
        Text(
            text = content.title,
            color = palette.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content.fileName?.let { fileName ->
            Text(
                text = fileName,
                color = palette.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
