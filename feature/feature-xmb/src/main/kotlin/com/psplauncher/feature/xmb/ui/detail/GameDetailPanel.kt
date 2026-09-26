package com.psplauncher.feature.xmb.ui.detail

import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.ui.components.ControllerPromptGlyphs
import com.psplauncher.core.ui.detail.DetailMediaTileHeight
import com.psplauncher.core.ui.detail.DetailMediaTileWidth
import com.psplauncher.core.ui.detail.PfpDetailMediaTile
import com.psplauncher.core.ui.detail.detailPalette
import androidx.compose.ui.draw.clip
import com.psplauncher.core.ui.image.rememberArtworkModel
import com.psplauncher.feature.xmb.ui.Icon1VideoOverlay
import com.psplauncher.core.ui.theme.LocalPfpTextColors
import com.psplauncher.core.ui.theme.menuCursorEdge

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

/**
 * A pill, not a rounded rectangle.
 *
 * At 4dp with the padding it had, a tab read as a small BUTTON -- a box with a fill, competing
 * with the Play legend a few rows down. A soft capsule behind the current label, and nothing at
 * all behind the others, reads as a tab row.
 */
/** The same drop shadow the crossbar's row labels wear, for text drawn straight onto artwork. */
private val PanelTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.75f),
    offset = Offset(0f, 2f),
    blurRadius = 5f,
)

/**
 * The tab's shape, and it is the Artwork Studio's chip rather than a capsule of its own.
 *
 * These two rows answer the same question — "which of these views" — over the same kind of
 * surface, and they answered it two ways: a 50%-rounded capsule here, a 6dp chip there. One of
 * them had to give and the Studio's is the one drawn against an approved mock.
 */
private val StripTabShape = RoundedCornerShape(6.dp)
private val StripTabGap: Dp = 6.dp
private val StripShoulderSize: Dp = 13.dp
private val PanelCardShape = RoundedCornerShape(14.dp)

/**
 * The page strip: named tabs, not a row of icons.
 *
 * It was five 20dp glyphs in a black pill, and a glyph is a guess -- an "i" and a picture frame
 * do not tell you that one is the description and the other is the box art until you have pressed
 * both. The console this borrows from spells its tabs out, so this does too: the label IS the
 * affordance, and the row reads left to right like a sentence rather than like a toolbar.
 *
 * L1 and R1 sit at the ends because the shoulders are what walks it. They are drawn by the prompt
 * system, so a user on an Xbox pad sees LB and RB: this row must not be the one place in the shell
 * that hard-codes a button name.
 *
 * Drawn at one page: it is what says the shoulders do anything here, and hiding it there would
 * make them look dead on exactly the games with the least artwork -- the ones most worth going
 * to look for a scrape on. At ZERO it is not drawn; see the guard below.
 */
@Composable
fun DetailPanelStrip(
    pages: List<DetailPanelPage>,
    current: DetailPanelPage,
    modifier: Modifier = Modifier,
    onPageTapped: ((DetailPanelPage) -> Unit)? = null,
) {
    // Nothing at all with nothing to walk. Drawn at ONE page -- that is what says the shoulders
    // do anything here -- but at zero the row is two shoulder glyphs around a gap, which promises
    // a walk that goes nowhere. The home shelf reaches zero because it drops the logo tab.
    if (pages.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(StripTabGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControllerPromptGlyphs(
            icons = listOf(ControllerIcon.BUMPER_LEFT),
            label = "",
            glyphSize = StripShoulderSize,
        )
        pages.forEach { page ->
            val selected = page == current
            Box(
                modifier = Modifier
                    .clip(StripTabShape)
                    // The Studio's chip treatment: the current tab takes the cursor accent at
                    // 0.28, the rest a flat white at 0.07 rather than nothing at all. Every tab
                    // reads as a tab that way, which is what a row of them is for, and the accent
                    // says "you are here" in the colour the rest of the app already uses for a
                    // cursor.
                    //
                    // It was white-at-0.12 on the current tab and transparent on the others: one
                    // fill, several gaps. That stayed readable over artwork, which was the point,
                    // and the accent fill does the same job without needing the reader to notice
                    // an absence.
                    //
                    // Still NO border. The Studio draws one when its tab ZONE has the cursor —
                    // this row has no separate zone to be in, the shoulders walk it from
                    // anywhere, so a border here would mark a state that does not exist.
                    .background(
                        if (selected) menuCursorEdge().copy(alpha = 0.28f)
                        else Color.White.copy(alpha = 0.07f),
                        StripTabShape,
                    )
                    .then(
                        if (onPageTapped != null) Modifier.clickable { onPageTapped(page) }
                        else Modifier
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = page.label.uppercase(),
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.62f),
                    // Small, and NOT the Studio's 10.5sp even though the chip is now its chip.
                    // This row is drawn in two places: on the Last Played shelf and the crossbar
                    // it sits INSIDE the XMB canvas, which carries the crossbar's auto-fit on top
                    // of the user's scale, so a size chosen against the reference at 1x came out
                    // a third bigger again on this device and read as a headline rather than as
                    // chrome. The Studio's row is on a chrome screen and takes neither. Same
                    // shape, different scaling context, so the sizes stay apart on purpose.
                    fontSize = 8.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
        ControllerPromptGlyphs(
            icons = listOf(ControllerIcon.BUMPER_RIGHT),
            label = "",
            glyphSize = StripShoulderSize,
        )
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
    /** Draw the logo page at half height. The home shelf does; a detail screen does not. */
    halfHeightLogo: Boolean = false,
) {
    // The strip is NOT drawn here. It lives in the host's chrome, under the top bar, so the whole
    // of this region belongs to the page — which is the difference between a box front you can
    // read and one you can identify.
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // resolvePanelPage, not page, so a page that stops being available while the panel
            // is open (a scrape filling in box art, the cursor moving to a game with less art)
            // cannot leave the body drawing something the strip is no longer offering.
            when (resolvePanelPage(page, content.pages)) {
                DetailPanelPage.LOGO -> LogoPage(content, titleFallback, halfHeightLogo)
                DetailPanelPage.BOX_ART -> BoxArtPage(content)
                DetailPanelPage.VIDEO -> VideoPage(content)
                DetailPanelPage.GALLERY -> GalleryPage(content, focusedMediaId, onMediaTapped)
                DetailPanelPage.INFO -> InfoPage(content)
            }
        }
        // The play time sits UNDER the page, not above it. This panel's top edge is level with
        // the crossbar's category icons, so a caption there landed on the Photo icon — seen on
        // the device. It belongs to the game rather than to any one page, so it stays put while
        // L1/R1 walk the body above it.
        //
        // Null, not "0 min", when a game has never been launched from here: see panelPlayTime.
        content.playTime?.let { played ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "TIME PLAYED: ${played.uppercase()}",
                // White, with the crossbar's own shadow, NOT the theme's secondary. This line
                // sits on whatever artwork the item brought, and the theme is re-tinted from
                // that same artwork -- so a themed secondary is a colour drawn FROM the picture
                // it has to be legible against. On a bright frame it came out grey on gold and
                // could not be read at all.
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(shadow = PanelTextShadow),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
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
private fun LogoPage(content: DetailPanelContent, titleFallback: Boolean, halfHeight: Boolean = false) {
    val logo = content.logoUri
    if (logo != null) {
        AsyncImage(
            model = rememberArtworkModel(logo),
            contentDescription = content.title,
            // Fit, never Crop: a trimmed logo is a wordmark with a letter missing.
            contentScale = ContentScale.Fit,
            // Half the page on the home shelf: at full height a wordmark filled the screen and
            // left the tabs and the content with nothing but the bottom third, which is the
            // opposite of a page whose subject is the artwork BEHIND it.
            modifier = Modifier
                .fillMaxWidth()
                .then(if (halfHeight) Modifier.fillMaxHeight(0.5f) else Modifier.fillMaxHeight())
                .padding(8.dp),
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
