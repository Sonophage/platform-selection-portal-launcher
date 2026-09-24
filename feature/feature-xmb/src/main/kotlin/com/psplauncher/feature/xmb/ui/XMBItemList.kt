package com.psplauncher.feature.xmb.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ImportContacts
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.psplauncher.core.ui.icons.AppIconContainerShape
import com.psplauncher.core.ui.icons.appIconBitmap
import com.psplauncher.core.ui.components.ControllerPromptGlyphs
import com.psplauncher.core.ui.icons.GameIconStyle
import com.psplauncher.core.ui.icons.LocalXmbIconOverrides
import com.psplauncher.core.ui.icons.OverrideGlyphSurface
import com.psplauncher.core.ui.icons.PortalIcon
import com.psplauncher.core.ui.icons.ThemedGlyph
import com.psplauncher.core.ui.icons.categoryIconFor
import com.psplauncher.core.ui.icons.systemIconRes
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.feature.xmb.viewmodel.GRID_COVER_COUNT
import com.psplauncher.core.domain.model.PlayState
import com.psplauncher.feature.xmb.viewmodel.pillsFor
import com.psplauncher.feature.xmb.viewmodel.XMBItem
import com.psplauncher.feature.xmb.viewmodel.XMBItemType
import com.psplauncher.core.ui.image.rememberArtworkModel
import com.psplauncher.themekit.XmbLayoutSpec
import androidx.compose.runtime.ReadOnlyComposable
import com.psplauncher.core.ui.theme.LocalPfpTextColors

// Game icons use the authentic PSP ICON0 ratio 144:80 (= 1.8), scaled for the list.
private val GAME_ICON_WIDTH = 126.dp
private val GAME_ICON_HEIGHT = 70.dp

// Every row is exactly this tall so the list viewport can be sized to a whole number of rows —
// this is what lets us "hard stop" at a row boundary and never render a partially-clipped row.
// Internal because XMBShell needs it too: the drill flyout's PIC0 logo centres on the active
// row, and it can only do that if it measures the row the same way the column lays it out.
internal val ROW_HEIGHT = 88.dp

// Gap between a wide artwork tile and its title. Small-icon rows get this spacing for free from
// their 58dp icon box; the 126dp artwork tiles have none, so the text butts against the art.
private val ARTWORK_TEXT_GAP = 16.dp

// The tap target is shorter than the full row so there are inert gaps between rows, and it wraps
// the content width so the empty space to the right of the text isn't clickable — both keep stray
// touches from selecting/launching items.
private val TAP_TARGET_HEIGHT = 72.dp

// Fixed-width slot every leading icon is centred in, so an icon's horizontal centre is independent
// of its own size — icons can grow without breaking the caticon alignment. Sized from the shared
// theme-kit layout spec (single source of truth for the tuned XMB geometry).
internal val LEADING_ICON_SLOT = XmbLayoutSpec.DEFAULT.itemIconSlotDp.dp
// Default size of the glyph/art inside that slot (selected rows additionally scale up via the row).
/**
 * The row whose progress is changing RIGHT NOW, and where it is up to.
 *
 * A scrubber that does not move is a picture of a scrubber. The Now Playing row's fraction is
 * baked into the item when the column is built, and XMBViewModel deliberately does not rebuild
 * that list on playback ticks — "never on the half-second position ticks" — because rebuilding a
 * four-row list twice a second to move three pixels is the wrong trade. So the bar reads the live
 * value here instead, and only the bar recomposes.
 *
 * Ambient rather than a parameter because it would otherwise thread through five signatures to
 * reach one Box, which is the same reason the icon overrides and the display mode ride this rail.
 *
 * Null for every row but one: [itemId] is the row it describes, and a row that is not it keeps
 * whatever it was built with — a resumed video's position does not move while you look at it.
 */
internal data class LiveRowProgress(
    val itemId: String,
    val fraction: Float,
    val label: String?,
)

internal val LocalLiveRowProgress = androidx.compose.runtime.compositionLocalOf<LiveRowProgress?> { null }

/** The scrubber under a playing or resumable row: a thin bar, the accent filling it. */
private val ScrubberWidth = 96.dp
private val ScrubberHeight = 3.dp

private val LEADING_ICON_SIZE = XmbLayoutSpec.DEFAULT.itemIconDp.dp
// Horizontal centre of a row's leading icon from the row's left edge: 18.dp row padding + half the
// slot. The grow/shrink scale pivots here, and the column is shifted so this lands on the caticon's
// vertical line. Shared with XMBShell's column offset so the two never drift apart.
internal val LEADING_ICON_CENTER = 18.dp + LEADING_ICON_SLOT / 2

// Classic PSP blue theme: the selected row is crisp white; unselected rows recede into a dimmer
// blue-white so they read against the saturated blue gradient.
// Resolved per theme rather than fixed white: on a pale scheme the selected row was the
// brightest thing on an already bright wallpaper. See PFPTheme.
private val PrimaryText: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.primary
// Resolved per theme rather than fixed: on a pale scheme a light label on a light
// wallpaper is unreadable, and every one of these was light. See PFPTheme.
private val SecondaryText: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.secondary
// Resolved per theme rather than fixed: on a pale scheme a light label on a light
// wallpaper is unreadable, and every one of these was light. See PFPTheme.
private val InactiveText: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.inactive
/** What every unselected ROW fades to when "Fade By Distance" is off. See XMBCategoryBar's 0.58. */
private const val FlatUnfocusedRowAlpha = 0.68f

// Soft dark halo behind the bright selected label — keeps white legible on the light wave.
private val SelectedTextShadow = Shadow(
    color = Color(0x73001627),
    offset = Offset.Zero,
    blurRadius = 12f,
)

// A row's inner content padding. Shared so anything that needs to land next to a row's artwork
// (e.g. the flyout cursor) can derive its position from the same number the row lays out with.
private val ROW_HORIZONTAL_PADDING = 18.dp

// "Text Shadow" (Display ▸ Appearance): the repo's standard directional drop shadow — the same
// values PspContextMenu / ControllerHintBar / DetailContextMenu use — applied to XMB row
// subtitles. The settings scaffold's SettingsTextShadow is feature-internal, so the same idiom is
// restated here for the shell (the XMB draws over the raw wallpaper, no scrim at all).
val XmbTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.75f),
    offset = Offset(0f, 2f),
    blurRadius = 4f,
)

// Physical-media memory-card art for rows that should read as a memory card but have no console icon
// of their own (collections). Mirrors the ViewModel's MEMORY_CARD_ASSET_URI.
internal const val MEMORY_CARD_DEFAULT_ART = "file:///android_asset/systems/physical-media/_default.png"

// ── Drill flyout layout ──────────────────────────────────────────────────────
// Left inset of the game-card column, measured from the flyout's left edge (which the caller has
// already shifted under the caticon). Clears the icon-only memory-card column and the ◀ that trails
// the active card, then seats the games just past it — kept tight so the games hug the active
// console icon and the PIC0 logo overlay (center-right) still has room to breathe.
private val DRILL_GAME_COLUMN_LEFT = 138.dp

// ── Two-pane drill flyout (PSP/XMB style) ────────────────────────────────────
//
// Two columns sharing the same [belowTopY] line (the row directly under the caticon):
//
//   • LEFT — the PLATFORM MEMORY CARDS (the items: All Games / Favorites / consoles / collections),
//     rendered by the main [XMBItemList] itself so the cross is identical: the drilled-into card at
//     belowTopY with a ◀ trailing it, the previous card half-clipped above the bar. Icon-only.
//     This column is fixed while you run through the games.
//   • RIGHT — the GAME CARDS (rom icons), icon-only, in a centre-pinned column: the active game is
//     pinned on the belowTopY / ◀ line and the tween glides the next/previous card onto the pin.
//
//     [ card 3 ]   ½-clipped above the bar
//  ═══ caticon bar (right hidden) ═══
//     [ card 2 ] ◀      [ ACTIVE GAME ]   ← belowTopY: active memory card + ◀ + active game card
//     [ card 1 ]        [ game ]
//                       [ game ]
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun XmbDrillFlyout(
    siblings: List<XMBItem>,
    siblingIndex: Int,
    items: List<XMBItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    onItemLongPress: (Int) -> Unit,
    // Tap on a LEFT-column memory card. The caller decides what it means (tapping the active card
    // backs out of the drill); taps on other cards are delivered too so it can ignore them.
    onSiblingTap: (Int) -> Unit = {},
    iconStyle: GameIconStyle = GameIconStyle.PSP_RECTANGLE,
    // The Y of the category bar's top edge and bottom edge — passed the SAME values as the main XMB
    // so the drill is laid out identically: active row under the caticon, previous half-clipped above.
    barTopY: Dp = 40.dp,
    belowTopY: Dp = 152.dp,
    // Whether focused-row GIF icons may animate (see XMBItemList).
    iconAnimatingAllowed: Boolean = false,
    // See XmbVerticalListRow. Required, not defaulted: the flyout is the path where the missing
    // value went unnoticed, so it does not get to be optional here either.
    labelHiddenByPanel: Boolean,
    /**
     * Runs a tapped action pill. Required, not defaulted, for the same reason as
     * [labelHiddenByPanel] above: a silent default at one of three call sites is how the last
     * per-row flag got quietly disabled on the busiest path.
     */
    onPillActivated: (String) -> Unit,
    /** Which pill the controller cursor is on, or null while it is on the row. */
    focusedPillIndex: Int?,
    cardArtGrid: Boolean = true,
    metadataAsSubtitle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // LEFT: the memory-card cross — the main XMB list itself, icon-only, with a ◀ after the
        // active (drilled-into) card. Static while navigating games. Labels are hidden here so the
        // drilled console reads as a bare icon and the ◀ sits tight against it — the games are the
        // focus while drilled in, and the name already showed at the parent level.
        XMBItemList(
            items = siblings,
            selectedIndex = siblingIndex,
            onItemSelected = onSiblingTap,
            onItemLongPress = {},
            iconStyle = iconStyle,
            barTopY = barTopY,
            belowTopY = belowTopY,
            showLabels = false,
            drillCursorOnSelected = true,
            iconAnimatingAllowed = iconAnimatingAllowed,
            // Pills never draw here — this column is memory cards and showLabels is false — but
            // the callback is required rather than defaulted, so it is passed rather than guessed.
            onPillActivated = onPillActivated,
            focusedPillIndex = focusedPillIndex,
            modifier = Modifier.fillMaxHeight().width(DRILL_GAME_COLUMN_LEFT - 10.dp),
        )

        // RIGHT: the game cards — a single continuous column laid out (not scrolled) so the active
        // game sits exactly on the belowTopY / ◀ line, with the previous card contiguous directly
        // above it and the next below — uniform ROW_HEIGHT spacing throughout, no crossbar gap and
        // no scroll state to lag the highlight. Offset to the right of the memory-card column.
        XmbGameColumn(
            items = items,
            selectedIndex = selectedIndex,
            iconStyle = iconStyle,
            belowTopY = belowTopY,
            onItemSelected = onItemSelected,
            onItemLongPress = onItemLongPress,
            iconAnimatingAllowed = iconAnimatingAllowed,
            cardArtGrid = cardArtGrid,
            labelHiddenByPanel = labelHiddenByPanel,
            onPillActivated = onPillActivated,
            focusedPillIndex = focusedPillIndex,
            metadataAsSubtitle = metadataAsSubtitle,
            modifier = Modifier.fillMaxSize().padding(start = DRILL_GAME_COLUMN_LEFT),
        )
    }
}

// The flyout's game column: every game in one continuous column, laid out so [selectedIndex] lands on
// [belowTopY]. Because it's pure layout (no LazyColumn scroll), the active card is always exactly on
// the line — the highlight can't drift — and the rows keep uniform ROW_HEIGHT spacing with no gap
// above the active. Only the rows that can reach the viewport are rendered.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun XmbGameColumn(
    items: List<XMBItem>,
    selectedIndex: Int,
    iconStyle: GameIconStyle,
    belowTopY: Dp,
    labelHiddenByPanel: Boolean,
    onPillActivated: (String) -> Unit,
    /** Which pill the controller cursor is on, or null while it is on the row. */
    focusedPillIndex: Int?,
    cardArtGrid: Boolean = true,
    metadataAsSubtitle: Boolean = false,
    onItemSelected: (Int) -> Unit,
    onItemLongPress: (Int) -> Unit,
    iconAnimatingAllowed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        if (items.isEmpty()) return@BoxWithConstraints
        val sel = selectedIndex.coerceIn(0, items.lastIndex)
        // Window: only the rows that can land on screen above/below the active one (+2 buffer each way
        // so the next/previous card is always already composed before it scrolls into view).
        val rowsAbove = (belowTopY.value / ROW_HEIGHT.value).toInt() + 2
        val rowsBelow = ((maxHeight.value - belowTopY.value) / ROW_HEIGHT.value).toInt() + 2
        val first = (sel - rowsAbove).coerceAtLeast(0)
        val last = (sel + rowsBelow).coerceAtMost(items.lastIndex)
        // Place each row by its OWN absolute offset from the anchor line: the active row (i == sel)
        // lands exactly on belowTopY, earlier rows one ROW_HEIGHT up each, later rows one down each.
        // Independent placement (not a shared Column) guarantees rows past the active are laid out.
        for (i in first..last) {
            XmbVerticalListRow(
                item = items[i],
                isSelected = i == selectedIndex,
                // Text is allowed here; whether a row actually draws any is XmbVerticalListRow's
                // call. A real game shows its title only while it is the active row and nothing
                // on the right is already naming it (see showGameText).
                showText = true,
                // This was the one call site of three that never passed it, so showGameText
                // evaluated against the default `false` here and the active row kept its title
                // whatever was on the right. The comment above described the intended behaviour
                // and this path could not produce it. The parameter has no default any more, so
                cardArtGrid = cardArtGrid,
                // a fourth call site cannot repeat it.
                labelHiddenByPanel = labelHiddenByPanel,
                onPillActivated = onPillActivated,
                focusedPillIndex = focusedPillIndex,
                metadataAsSubtitle = metadataAsSubtitle,
                iconStyle = iconStyle,
                onClick = { onItemSelected(i) },
                onLongPress = { onItemLongPress(i) },
                showIcon = true,
                // Only the active card animates (its rows funnel through the same per-row gate).
                iconAnimatingAllowed = iconAnimatingAllowed,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
                    .offset(y = belowTopY + ROW_HEIGHT * (i - sel)),
            )
        }
    }
}

/**
 * A card's tile as a 2x2 of what is inside it.
 *
 * "JUST for emulation and media we add a four grid with art of whats inside" — a console card's
 * glyph says which console it is, which its own label already said; the covers say what you have
 * on it.
 *
 * FEWER THAN FOUR FILLS WHAT IT HAS AND LEAVES THE REST EMPTY — his call, and the reason the
 * quadrants are a fixed grid rather than a layout that reflows. A card with two games shows two
 * covers in the top row and space beneath, which reads as a part-filled shelf; the same two
 * stretched across the whole tile reads as a card that only ever had two.
 */
@Composable
private fun CardArtGrid(covers: List<String>, size: Dp, modifier: Modifier = Modifier) {
    val gap = size * 0.06f
    val cell = (size - gap) / 2
    Box(modifier.size(size)) {
        covers.take(GRID_COVER_COUNT).forEachIndexed { i, uri ->
            AsyncImage(
                model = rememberArtworkModel(uri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(cell)
                    .offset(
                        x = if (i % 2 == 0) 0.dp else cell + gap,
                        y = if (i < 2) 0.dp else cell + gap,
                    )
                    .clip(RoundedCornerShape(cell * 0.10f)),
            )
        }
    }
}

// One sibling icon — plain glyph (no tile/shadow), dimmed when not the active sibling. Video
// sections use vector glyphs (folder / library / movie); everything else uses console art.
// The sibling cross keeps ONE dim and does not take the distance ramp. It is a drill flyout's
// memory-card column, not a run you scroll: you step onto a sibling, you do not travel past four
// of them, so "further away" has nothing to mean here. Extending the ramp to it would be applying
// a rule to a surface its numbers were never about.
@Composable
private fun SiblingIcon(item: XMBItem, selected: Boolean) {
    val chip = if (selected) 56.dp else 40.dp
    val videoGlyph = when (item.type) {
        // Missing takes the vector path rather than console art: there is no sysicon for it, and
        // the console fallback is the blank sysicon_default. Same "?" glyph the Untracked row in
        // both meaning "we know about this entry but can't account for it".
        XMBItemType.MISSING         -> Icons.AutoMirrored.Filled.HelpOutline
        XMBItemType.VIDEO_FOLDER    -> Icons.Filled.Folder
        XMBItemType.VIDEO_LIBRARY   -> Icons.Filled.VideoLibrary
        XMBItemType.VIDEO_RECENT      -> Icons.Filled.History
        XMBItemType.VIDEO_FAVORITES   -> Icons.Filled.Star
        XMBItemType.VIDEO_COLLECTIONS -> Icons.Filled.Bookmarks
        XMBItemType.PHOTO_FOLDER    -> Icons.Filled.Folder
        XMBItemType.PHOTO_ALBUMS    -> Icons.Filled.PhotoLibrary
        XMBItemType.SEARCH          -> Icons.Filled.Search
        XMBItemType.MUSIC_ARTISTS   -> Icons.Filled.Person
        XMBItemType.MUSIC_ALBUMS    -> Icons.Filled.Album
        // The video "Playlists" section row (PLAYLIST type with no playlistId) uses a playlist glyph.
        XMBItemType.PLAYLIST        -> Icons.AutoMirrored.Filled.QueueMusic
        else                        -> null
    }
    Box(
        modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (videoGlyph != null) {
            ThemedGlyph(
                slotKey = itemSlotKeyFor(item.type) ?: "",
                defaultVector = videoGlyph,
                contentDescription = item.title,
                tint = LocalPFPColors.current.iconColor,
                // Layer alpha (not tint alpha) so custom untinted icons dim identically.
                modifier = Modifier.size(chip).alpha(if (selected) 1f else 0.5f),
            )
        } else {
            com.psplauncher.core.ui.icons.ConsoleIcon(
                platformId = consoleIconKeyFor(item),
                contentDescription = item.title,
                modifier = Modifier.size(chip).alpha(if (selected) 1f else 0.5f),
            )
        }
    }
}

// Themeable icon slot (theme-kit IconSlots key) for item types whose leading glyph is a
// Material vector. Null = the type's glyph is not a themeable slot (console art, covers).
// Kept in lockstep with the Theme Studio's StudioIconSet.ITEM_VECTORS defaults.
/**
 * Theme slot for rows that show the default memory-card art — the Music/Videos/Photos
 * library cards and Games-side collections. Null for per-console cards (console identity
 * stays uniform across themes) and anything with real user artwork.
 */
internal fun memoryCardSlotKeyFor(item: XMBItem): String? = when {
    item.type == XMBItemType.COLLECTION -> "item_memcard_games"
    item.type != XMBItemType.MEMORY_CARD -> null
    item.id == "all_music" -> "item_memcard_music"
    item.id == "all_videos" -> "item_memcard_video"
    item.id == "all_photos" -> "item_memcard_photos"
    else -> null
}

internal fun itemSlotKeyFor(type: XMBItemType): String? = when (type) {
    XMBItemType.ADD_ACTION -> "item_add"
    XMBItemType.MISSING -> "item_missing"
    XMBItemType.VIDEO_FOLDER -> "item_video_folder"
    XMBItemType.VIDEO_LIBRARY -> "item_video_library"
    XMBItemType.VIDEO_RECENT -> "item_video_recent"
    XMBItemType.VIDEO_FAVORITES -> "item_video_favorites"
    XMBItemType.VIDEO_COLLECTIONS -> "item_video_collections"
    XMBItemType.VIDEO_FILE -> "item_video_file"
    XMBItemType.PHOTO_FOLDER -> "item_photo_folder"
    XMBItemType.PHOTO_FILE -> "item_photo_file"
    XMBItemType.PHOTO_ALBUMS -> "item_photo_albums"
    XMBItemType.LIBRARY_SHELVES -> "item_library_shelves"
    XMBItemType.LIBRARY_READER -> "item_library_reader"
    XMBItemType.LIBRARY_FOLDER -> "item_library_folder"
    XMBItemType.LIBRARY_BOOK -> "item_library_book"
    XMBItemType.LIBRARY_SERIES -> "item_library_series"
    XMBItemType.CAMERA -> "item_camera"
    XMBItemType.SEARCH -> "item_search"
    XMBItemType.MUSIC_TRACK -> "item_music_track"
    XMBItemType.MUSIC_ARTISTS -> "item_music_artists"
    XMBItemType.MUSIC_ALBUMS -> "item_music_albums"
    XMBItemType.PLAYLIST -> "item_playlist"
    else -> null
}

// Maps a memory-card-style item to its sysicon key (mirrors XmbItemLeadingIcon's mapping).
private fun consoleIconKeyFor(item: XMBItem): String? = when (item.type) {
    XMBItemType.ALL_GAMES   -> "allgames"
    XMBItemType.FAVORITES   -> "favorites"
    XMBItemType.MEMORY_CARD -> item.platformId
    else                    -> null   // collections / unknown fall back to sysicon_default
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun XMBItemList(
    items: List<XMBItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    onItemLongPress: (Int) -> Unit,
    iconStyle: GameIconStyle = GameIconStyle.PSP_RECTANGLE,
    // Y of the category bar's TOP edge, measured from the top of this list.
    barTopY: Dp = 40.dp,
    // Y of the category bar's BOTTOM edge — where the selected item is seated, directly under the
    // caticon. The previous item sits one row ABOVE barTopY; the bar is the gap between them.
    belowTopY: Dp = 152.dp,
    // When false, rows render text-only (no game-icon artwork).
    showIcons: Boolean = true,
    // When false, rows render icon-only (no title/subtitle label). The drill flyout's memory-card
    // column uses this so the drilled console reads as a bare icon + ◀, tight against the games.
    showLabels: Boolean = true,
    // Whether the focused row's clear-logo overlay is currently on screen. The selected row hides
    // its own title only while that logo is actually visible -- never before it fades in, and
    // never for a game whose logo will not be drawn at all. Without this the identity of a
    // logo-bearing game was absent for the first 650ms and, on a game with no background art,
    // forever.
    labelHiddenByPanel: Boolean = false,
    onPillActivated: (String) -> Unit,
    /** Which pill the controller cursor is on, or null while it is on the row. */
    focusedPillIndex: Int?,
    cardArtGrid: Boolean = true,
    metadataAsSubtitle: Boolean = false,
    // When true, the selected row gets a ◀ drill cursor pinned directly to its right.
    drillCursorOnSelected: Boolean = false,
    // How far the dissolving previous item rises above the bar, in row heights (theme layout spec).
    previousRiseRows: Float = XmbLayoutSpec.DEFAULT.previousItemRiseRows,
    // "Fade By Distance" (Display ▸ Appearance): when true, unselected rows dim by how far they
    // unfocused dim — selection still reads by the row's scale and the bright label. Default
    // false = today's dimming.
    fadeByDistance: Boolean = true,
    // "Text Shadow" (Display ▸ Appearance): directional drop shadow behind row labels and
    // subtitles, so helper text stays readable over bright wallpaper regions. Default true —
    // without it the flat gray subtitle is the one label that washes out.
    textShadow: Boolean = true,
    // Whether focused-row GIF icons may animate (battery saver / blocking overlays gate it).
    // ANDed with per-row selection at the LocalIconAnimating provider.
    iconAnimatingAllowed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // The XMB cross, exactly as the hardware does it:
    //
    //     [ previous item ]   ← one row above the bar (the only thing shown above it)
    //     [ CATEGORY  BAR ]   ← fixed pivot
    //     [ SELECTED item ]   ← directly below the bar
    //     [ next item     ]
    //     [ next+1 …       ]
    //
    // Pressing down slides the whole column up one: the old selected becomes the previous (above the
    // bar) and the next becomes selected (below it). The bar is taller than a row, so the column is
    // rendered in two pieces — one item above, selected + following below — rather than one list.
    BoxWithConstraints(modifier = modifier.fillMaxWidth().fillMaxHeight().clipToBounds()) {
        // Render only rows that FULLY fit below the anchor — the active row plus however many whole
        // rows remain in the space beneath it. No trailing partial row is composed, so nothing gets
        // clipped to a half-height sliver at the bottom edge (on any screen size).
        val rowsBelow = ((maxHeight.value - belowTopY.value) / ROW_HEIGHT.value).toInt()
            .coerceAtLeast(1)
        val sel = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

        // BELOW the bar: the selected item first, then the items after it.
        if (items.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().offset(y = belowTopY)) {
                val last = minOf(items.size, sel + rowsBelow)
                for (i in sel until last) {
                    // Keyed by the row's stable id, NOT by its position.
                    //
                    // The window starts at the selected index, so stepping the cursor by one
                    // makes every call-site slot hold a different item. Unkeyed, Compose
                    // reuses groups positionally: all ~7 visible rows recompose and each
                    // AsyncImage inside them starts a fresh request, on every single D-pad
                    // press. Keyed, it moves the existing groups and composes only the row
                    // that newly entered the window.
                    key(items[i].id) {
                        XmbVerticalListRow(
                            labelHiddenByPanel = labelHiddenByPanel,
                            onPillActivated = onPillActivated,
                            focusedPillIndex = focusedPillIndex,
                            cardArtGrid = cardArtGrid,
                            metadataAsSubtitle = metadataAsSubtitle,
                            item = items[i],
                            isSelected = i == selectedIndex,
                            // The real PSP XMB labels EVERY first-level item (selected bright, the
                            // rest dimmed) — labels show unless the caller asks for an icon-only column
                            // (the drill flyout's memory-card cross).
                            showText = showLabels,
                            iconStyle = iconStyle,
                            onClick = { onItemSelected(i) },
                            onLongPress = { onItemLongPress(i) },
                            showIcon = showIcons,
                            trailingCursor = drillCursorOnSelected && i == selectedIndex,
                            fadeByDistance = fadeByDistance,
                            // The window below the bar starts AT the selection, so the row's
                            // offset into it is its distance — no abs() needed, it cannot go
                            // negative here.
                            distance = i - sel,
                            textShadow = textShadow,
                            iconAnimatingAllowed = iconAnimatingAllowed,
                            modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT),
                        )
                    }
                }
            }
        }

        // ABOVE the bar: only the immediately-previous item, and only its BOTTOM HALF — the top half
        // is clipped off above the bar, so it reads as "coming in" from behind the crossbar. The clip
        // window is half a row tall, seated just above the bar; the full-height row inside is shifted
        // up by half a row so its lower half lands in the window.
        if (selectedIndex in 1..items.lastIndex) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT / 2)
                    // Rise distance is theme-tunable: PSP-style wallpapers want the previous item
                    // fully clear of the caticon hexagon before it dissolves.
                    .offset(y = barTopY - ROW_HEIGHT * previousRiseRows)
                    .clipToBounds(),
                // Bottom-align a full-height row inside a half-height window: its top half is clipped.
                // requiredHeight keeps the row its full ROW_HEIGHT (the window would otherwise coerce
                // it down to half) so it overflows upward and the clip cuts the top half off.
                contentAlignment = Alignment.BottomStart,
            ) {
                XmbVerticalListRow(
                    item = items[selectedIndex - 1],
                    isSelected = false,
                    // Show the previous item's label too, so its name rises up through the
                    // crossbar with the icon (unless the column is icon-only).
                    showText = showLabels,
                    cardArtGrid = cardArtGrid,
                    // Unused while isSelected is false, but passed rather than defaulted: the
                    // parameter is required now precisely so nobody has to check that again.
                    labelHiddenByPanel = labelHiddenByPanel,
                    onPillActivated = onPillActivated,
                    focusedPillIndex = focusedPillIndex,
                    metadataAsSubtitle = metadataAsSubtitle,
                    iconStyle = iconStyle,
                    onClick = { onItemSelected(selectedIndex - 1) },
                    onLongPress = { onItemLongPress(selectedIndex - 1) },
                    showIcon = showIcons,
                    fadeByDistance = fadeByDistance,
                    // Exactly one row is ever drawn above the bar, so it is always one step out.
                    distance = 1,
                    textShadow = textShadow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(ROW_HEIGHT),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun XmbVerticalListRow(
    item: XMBItem,
    isSelected: Boolean,
    // Like the real XMB first-level column, rows are icon-only unless flagged: the caller shows text
    // only for the active row and the one directly below it (the "up next" preview).
    showText: Boolean,
    iconStyle: GameIconStyle,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    // When false, the leading game/console icon is omitted — the row is text-only.
    showIcon: Boolean = true,
    // When true, a ◀ drill cursor is drawn directly to the right of this row's content.
    trailingCursor: Boolean = false,
    // "Fade By Distance": when true, this unselected row dims by how far it is from the cursor.
    fadeByDistance: Boolean = true,
    /** Rows from the cursor. 0 is the selection; the row above the bar is 1, as is the one below. */
    distance: Int = 0,
    // "Text Shadow" (Display ▸ Appearance): drop shadow behind row helper text (subtitle).
    textShadow: Boolean = true,
    // Something to the right is already naming the focused game — its PIC0 logo, or the hover
    // panel on any of its other pages — so this row may hide its own title. False means the
    // title shows: a row is never allowed to be nameless.
    //
    // No default, deliberately. Two of this function's three call sites used to omit it and got
    // `false`, which silently disabled the rule on the drill flyout — the busiest path of the
    // three. A required parameter turns that from a thing you have to notice into a build error.
    labelHiddenByPanel: Boolean,
    onPillActivated: (String) -> Unit,
    /** Which pill the controller cursor is on, or null while it is on the row. */
    focusedPillIndex: Int?,
    cardArtGrid: Boolean = true,
    metadataAsSubtitle: Boolean = false,
    // Whether THIS row may animate its GIF icon — true only for the focused row, so exactly
    // one decoder runs at a time (decision 3). Provided per-row around the icon.
    iconAnimatingAllowed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Strong size delta between the locked selection and the rows scrolling past it — the PSP
    // "the cursor stays, the list breathes" feel.
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 0.9f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "xmbListRowScale",
    )
    val rowAlpha by animateFloatAsState(
        targetValue = when {
            isSelected -> 1f
            // The empty-state row keeps its own alpha either way: it is not a thing you are
            // navigating past, it is a message about there being nothing to navigate.
            item.type == XMBItemType.EMPTY -> 0.5f
            // "Fade By Distance" (Display ▸ Appearance): on, a row fades further the further it
            // sits from the cursor; off, every unselected row shares the one alpha it always had.
            fadeByDistance -> XmbDim.ranked(distance)
            else -> FlatUnfocusedRowAlpha
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "xmbListRowAlpha",
    )
    // On the same spring as the scale and the alpha above, so the bloom does not pop a frame
    // ahead of the row it belongs to. XmbGlow's contract asks for the animated value, not a
    // boolean, and the crossbar already honours it.
    val glow by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "xmbRowGlow",
    )
    // Shadow for row helper text: the subtitle is the only label with no separation treatment
    // (the title brightens + shadows when selected), so it's the text that washes out over the
    // bright half of a wallpaper. Same directional shadow idiom as PspContextMenu/ControllerHintBar.
    val subtitleStyle = if (textShadow) TextStyle(shadow = XmbTextShadow) else TextStyle.Default

    // Pivot the grow/shrink scale at the leading icon's centre (not the row centre) so the icon
    // never drifts horizontally as it scales — every row's icon stays on the caticon's vertical line.
    val density = LocalDensity.current
    val iconCenterPx = remember(density) { with(density) { LEADING_ICON_CENTER.toPx() } }
    var rowWidthPx by remember { mutableStateOf(0f) }
    // Outer row fills the slot for layout/centering; only the inner cluster is the tap target.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .onSizeChanged { rowWidthPx = it.width.toFloat() }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                if (rowWidthPx > 0f) {
                    transformOrigin = TransformOrigin((iconCenterPx / rowWidthPx).coerceIn(0f, 1f), 0.5f)
                }
            }
            .alpha(rowAlpha),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                // fill = false: shrink to the content width when the title is short (so the empty
                // trailing area stays inert), but cap at the available width so long titles still
                // truncate instead of overflowing.
                .weight(1f, fill = false)
                .height(TAP_TARGET_HEIGHT)
                // indication = null suppresses the Android ripple/highlight on tap & long-press —
                // the XMB communicates focus through its own cursor (scale + white label), and the
                // grey ripple rectangle broke the PSP look.
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongPress,
                )
                .padding(horizontal = ROW_HORIZONTAL_PADDING),
        ) {
            if (showIcon && !item.textOnly) {
                // Per-row animation gate: the provider scope covers just this row's icon, so a
                // GIF plays ONLY while its row is the selected one (decision 3).
                androidx.compose.runtime.CompositionLocalProvider(
                    com.psplauncher.core.ui.icons.LocalIconAnimating provides
                        (isSelected && iconAnimatingAllowed),
                ) {
                // Wrapped rather than pushed into XmbItemLeadingIcon: that is a `when` over a
                // dozen item types, each drawing its own shape, and the bloom is one thing behind
                // all of them.
                //
                // THE DESIGN'S 4px WHITE RING IS CUT, not pending. It was held back because it
                // wraps a uniform art tile and there was no such tile until the 2x2 art grid
                // existed. The grid shipped, which removed the reason — and looking at the result
                // the owner's answer was that the bloom is enough on its own (2026-09-24). Left
                // as a deferral this comment would read as work owed forever.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.xmbFocusGlow(
                        visible = glow,
                        reach = XmbGlow.RowReach,
                        alpha = XmbGlow.RowAlpha,
                    ),
                ) {
                    XmbItemLeadingIcon(
                        item = item,
                        iconStyle = iconStyle,
                        isSelected = isSelected,
                        cardArtGrid = cardArtGrid,
                    )
                }
                }
            }

            // Game entities are icon-first: NO text on any game row except the ACTIVE one. Non-game
            // rows keep their labels as always, and a textOnly row (e.g. Untracked) always labels.
            val showGameText = item.textOnly || !item.isRealGame || isSelected

            // THE LABEL FADES, it does not blink out, and it goes as soon as ANY page opens —
            // the logo included. "soon as logo hits title should fade then that should resolve
            // that other bug", and then "the subtitle should go as well". The other bug was a long
            // title printing straight through its own wordmark: "Castlevania : Curse of Darkness"
            // over the logo saying the same words.
            //
            // Title and subtitle leave together. They were briefly split — the name going on the
            // logo page and the facts staying, on the reasoning that a wordmark does not repeat
            // the year and the developer — which is true and was still the wrong call: the row
            // has ONE label, and half of it hanging on beside a wordmark reads as something that
            // failed to clear rather than as something kept on purpose.
            //
            // Only the PANEL-driven hide is faded. Moving the cursor still swaps the name
            // instantly between rows, because that is a cursor keeping up with a d-pad and a
            // crossfade on every press reads as lag rather than as polish.
            val panelHidesLabel = isSelected && item.isRealGame && !item.textOnly && labelHiddenByPanel
            val labelAlpha by animateFloatAsState(
                targetValue = if (panelHidesLabel) 0f else 1f,
                animationSpec = tween(220),
                label = "xmbRowLabelFade",
            )
            if (showText && showGameText && labelAlpha > 0f) {
                // start padding pushes the label clear of the wallpaper's vertical cross bar, so the
                // text doesn't butt against the black band (a small gap, PSP-style).
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .alpha(labelAlpha)
                        .padding(start = XmbLayoutSpec.DEFAULT.itemTextStartGapDp.dp),
                ) {
                    val titleColor = if (isSelected) PrimaryText else InactiveText
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            color = titleColor,
                            fontSize = if (isSelected) XmbLayoutSpec.DEFAULT.itemTextSelectedSp.sp
                            else XmbLayoutSpec.DEFAULT.itemTextSp.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            style = if (isSelected) TextStyle(shadow = SelectedTextShadow) else TextStyle.Default,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // The mark, AFTER the title and only when there is one.
                        //
                        // After, because a badge in front of the name indents every marked row and
                        // leaves the column's left edge ragged — the one thing a list of a hundred
                        // and fifty rows cannot afford. Nothing is drawn for an unmarked game, so
                        // an unsorted library looks exactly as it does today.
                        PlayState.fromName(item.playState)?.let { state ->
                            Spacer(Modifier.width(7.dp))
                            PlayStateBadge(state, dimmed = !isSelected)
                        }
                    }
                    // What the thing IS — year, genre, developer, players — in every state, not
                    // only while some page is open. A line under a name that changes identity
                    // depending on which panel page you are on is two lines sharing a slot.
                    //
                    // Falls back to the system-and-last-played line for a game with nothing
                    // scraped, and for anything that is not a game (a platform card's "64 games",
                    // a settings row's hint), so the slot never empties and nothing else moves.
                    val effectiveSubtitle =
                        if (metadataAsSubtitle) item.metadataLine ?: item.subtitle
                        else item.subtitle
                    effectiveSubtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                        Text(
                            text = subtitle,
                            color = SecondaryText,
                            fontSize = if (isSelected) 12.sp else 11.sp,
                            fontWeight = FontWeight.Normal,
                            style = subtitleStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    // THE SCRUBBER, under the meta line — 6a's "the playing track takes the focus
                    // slot, with a scrubber under its meta", and 6b's progress line on a resumed
                    // video. Only on the SELECTED row: an unselected row is a thing you might go
                    // to, and a bar on it is a fact about a session you are not in.
                    //
                    // Books have none and will not: progressFraction's own KDoc says why — a book
                    // opens in somebody else's reader, which never reports back.
                    if (isSelected) {
                        val live = LocalLiveRowProgress.current?.takeIf { it.itemId == item.id }
                        (live?.fraction ?: item.progressFraction)?.let { fraction ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Box(
                                    Modifier
                                        .width(ScrubberWidth)
                                        .height(ScrubberHeight)
                                        .clip(RoundedCornerShape(ScrubberHeight / 2))
                                        .background(Color.White.copy(alpha = 0.22f)),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                            .height(ScrubberHeight)
                                            .clip(RoundedCornerShape(ScrubberHeight / 2))
                                            .background(LocalPFPColors.current.accentColor),
                                    )
                                }
                                (live?.label ?: item.progressLabel)?.let { label ->
                                    Text(
                                        text = label,
                                        color = SecondaryText,
                                        fontSize = 11.sp,
                                        style = subtitleStyle,
                                        maxLines = 1,
                                        modifier = Modifier.padding(start = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                    // 9i's action pills, under everything else the row says about itself.
                    //
                    // The mock opens them on a press and pushes the rows below down; these stay —
                    // "it stays in column always visible". So they appear and vanish with the
                    // cursor and nothing else moves when you press anything.
                    //
                    // They go under the LABEL fade with the rest of the text: a row of actions
                    // hanging on beside a wordmark is the same mistake as half a label hanging on.
                    if (isSelected) {
                        val pills = pillsFor(item)
                        if (pills.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(PillGap),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                pills.forEachIndexed { index, pill ->
                                    XmbActionPill(
                                        label = pill.label,
                                        focused = index == focusedPillIndex,
                                        onClick = { onPillActivated(pill.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Drill cursor — a ◀ pinned directly to the RIGHT of the (active) card, vertically
            // centred by the Row's CenterVertically. Only the selected row sets this.
            if (trailingCursor) {
                Text(
                    text = "◀",
                    color = LocalPFPColors.current.accentColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

// Icon shadows/blooms dropped per design — selection is conveyed by the row's scale alone.
private fun Modifier.selectedIconBloom(isSelected: Boolean): Modifier = this

@Composable
private fun XmbItemLeadingIcon(
    item: XMBItem,
    iconStyle: GameIconStyle,
    isSelected: Boolean,
    cardArtGrid: Boolean = true,
) {
    // Material glyph rows follow the theme's unified icon color, matching the tinted
    // silhouette art (PortalIcon) — row alpha handles the unselected dimming.
    val iconTint = LocalPFPColors.current.iconColor

    // THE ART GRID IS CHECKED BEFORE THE TYPE, because by now it applies to rows of many types:
    // a console card, All Games, a music section, a video library. What they share is not a type,
    // it is having something inside worth previewing — which is exactly what insideCovers says.
    //
    // It still yields to an explicit per-slot pick. A user's custom icon and a collection's chosen
    // glyph are statements about THIS row; the grid is a global default, and a global default that
    // overrode them would invert the tier order the icon catalog is built on.
    val userPickedIcon = (memoryCardSlotKeyFor(item) ?: itemSlotKeyFor(item.type))
        ?.let { com.psplauncher.core.ui.icons.LocalCustomIcons.current[it] }
    if (cardArtGrid && item.insideCovers.isNotEmpty() && userPickedIcon == null && item.iconKey == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(LEADING_ICON_SIZE).selectedIconBloom(isSelected),
            ) {
                CardArtGrid(item.insideCovers, LEADING_ICON_SIZE)
            }
        }
        return
    }

    when {
        // Music tracks (and the "Now Playing" row) show a square album cover, falling back to a
        // framed music-note glyph when the track had no embedded art. The 58dp box keeps every
        // track's title left-aligned with the small-icon rows above/below.
        item.type == XMBItemType.MUSIC_TRACK -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(LEADING_ICON_SLOT),
            ) {
                if (item.coverUri != null) {
                    AsyncImage(
                        model = item.coverUri,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1B1B27)),
                    ) {
                        ThemedGlyph(
                            slotKey = itemSlotKeyFor(item.type) ?: "",
                            defaultVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
        // The Music root's "Artists" and "Albums" section rows.
        item.type == XMBItemType.MUSIC_ARTISTS -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Person, null, iconTint, Modifier.size(48.dp))
            }
        }
        item.type == XMBItemType.MUSIC_ALBUMS -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Album, null, iconTint, Modifier.size(48.dp))
            }
        }
        // Playlist rows (and the static "Playlist" item) use a queue-music glyph.
        item.type == XMBItemType.PLAYLIST -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(LEADING_ICON_SLOT),
            ) {
                ThemedGlyph(
                    slotKey = itemSlotKeyFor(item.type) ?: "",
                    defaultVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        // Video files show a landscape thumbnail (a frame grab), falling back to a movie glyph.
        item.type == XMBItemType.VIDEO_FILE -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(LEADING_ICON_SLOT),
            ) {
                if (item.coverUri != null) {
                    AsyncImage(
                        model = item.coverUri,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(width = 60.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 60.dp, height = 40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1B1B27)),
                    ) {
                        ThemedGlyph(
                            slotKey = itemSlotKeyFor(item.type) ?: "",
                            defaultVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
        // A video library / "All Videos" folder: custom artwork when set, else a folder glyph.
        item.type == XMBItemType.VIDEO_FOLDER -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                if (item.coverUri != null) {
                    AsyncImage(
                        model = item.coverUri,
                        contentDescription = null,
                        modifier = Modifier.size(LEADING_ICON_SIZE).clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Folder, null, iconTint, Modifier.size(48.dp))
                }
            }
        }
        // The static "Video Libraries" and "Android Video Apps" rows use glyphs.
        item.type == XMBItemType.VIDEO_LIBRARY -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.VideoLibrary, null, iconTint, Modifier.size(48.dp))
            }
        }
        item.type == XMBItemType.VIDEO_RECENT -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.History, null, iconTint, Modifier.size(48.dp))
            }
        }
        item.type == XMBItemType.VIDEO_FAVORITES -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Star, null, iconTint, Modifier.size(48.dp))
            }
        }
        // "Collections" root row — umbrella for Recently Watched / Favorites / Playlists.
        item.type == XMBItemType.VIDEO_COLLECTIONS -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Bookmarks, null, iconTint, Modifier.size(46.dp))
            }
        }
        // Photos show their cached thumbnail, falling back to a photo glyph for files whose
        // thumbnail couldn't be generated (corrupt/exotic formats).
        item.type == XMBItemType.PHOTO_FILE -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(LEADING_ICON_SLOT),
            ) {
                if (item.coverUri != null) {
                    AsyncImage(
                        model = item.coverUri,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(width = 60.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 60.dp, height = 40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1B1B27)),
                    ) {
                        ThemedGlyph(
                            slotKey = itemSlotKeyFor(item.type) ?: "",
                            defaultVector = Icons.Filled.Photo,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
        // An Album folder card in the Albums list — folder glyph, matching the Video libraries.
        item.type == XMBItemType.PHOTO_FOLDER -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Folder, null, iconTint, Modifier.size(48.dp))
            }
        }
        // The "Albums" section row at the Photo root.
        item.type == XMBItemType.PHOTO_ALBUMS -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.PhotoLibrary, null, iconTint, Modifier.size(48.dp))
            }
        }
        // Library (books) rows. Same glyphs the Theme Studio previews these slots with.
        item.type == XMBItemType.LIBRARY_SHELVES -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.CollectionsBookmark, null, iconTint, Modifier.size(46.dp))
            }
        }
        item.type == XMBItemType.LIBRARY_READER -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.ImportContacts, null, iconTint, Modifier.size(46.dp))
            }
        }
        item.type == XMBItemType.LIBRARY_FOLDER -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Folder, null, iconTint, Modifier.size(46.dp))
            }
        }
        // Books show the cover extracted from the EPUB, falling back to a book glyph for a file
        // that declares none. The tile is portrait, unlike video's and photo's landscape ones,
        // because a book jacket cropped to landscape is unrecognisable.
        // A series folder shows the cover of its earliest volume, same portrait tile as a book,
        // falling back to a bookmarks glyph when no volume in it had one.
        item.type == XMBItemType.LIBRARY_SERIES -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                if (item.coverUri != null) {
                    AsyncImage(
                        model = item.coverUri,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(4.dp)),
                    )
                } else {
                    ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Bookmarks, null, iconTint, Modifier.size(46.dp))
                }
            }
        }
        item.type == XMBItemType.LIBRARY_BOOK -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                if (item.coverUri != null) {
                    AsyncImage(
                        model = item.coverUri,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(4.dp)),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 40.dp, height = 56.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1B1B27)),
                    ) {
                        ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Book, null, iconTint, Modifier.size(26.dp))
                    }
                }
            }
        }
        // The Camera row (only present when a camera app exists).
        item.type == XMBItemType.CAMERA -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.PhotoCamera, null, iconTint, Modifier.size(48.dp))
            }
        }
        // A Search row: a magnifier rather than the "+" every other action row wears, because
        // it does not add anything.
        item.type == XMBItemType.SEARCH -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Search, null, iconTint, Modifier.size(44.dp))
            }
        }
        // "Add …" / "Create …" rows across Photo / Music / Video sections.
        item.type == XMBItemType.ADD_ACTION -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Add, null, iconTint, Modifier.size(44.dp))
            }
        }
        // Missing sits beside All Games / Favorites but is not console art — it gets the same "?"
        // a help glyph, at the memory-card icon size so it lines up with
        // the cards above it. Themeable via the item_missing slot like any other vector row.
        item.type == XMBItemType.MISSING -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(LEADING_ICON_SIZE).selectedIconBloom(isSelected),
                ) {
                    ThemedGlyph(
                        itemSlotKeyFor(item.type) ?: "",
                        Icons.AutoMirrored.Filled.HelpOutline,
                        null,
                        iconTint,
                        Modifier.size(LEADING_ICON_SIZE),
                    )
                }
            }
        }
        item.type == XMBItemType.ALL_GAMES ||
            item.type == XMBItemType.FAVORITES ||
            item.type == XMBItemType.MEMORY_CARD ||
            item.type == XMBItemType.COLLECTION -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(LEADING_ICON_SLOT),
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(LEADING_ICON_SIZE).selectedIconBloom(isSelected),
              ) {
                // A collection with a user-picked icon renders that catalog glyph; otherwise it uses
                // the physical-media memory-card art (_default.png) and reads as a memory card.
                val collectionIconKey = item.iconKey?.takeIf { item.type == XMBItemType.COLLECTION }
                // Explicit art (the "Music" card) loads directly; collections without a picked icon
                // fall back to the memory-card art instead of the blank sysicon_default.
                val memoryCardArt = item.coverUri
                    ?: MEMORY_CARD_DEFAULT_ART.takeIf { item.type == XMBItemType.COLLECTION && collectionIconKey == null }
                // Themes can replace the DEFAULT memory-card art per category — never a
                // user-picked collection glyph or real cover artwork. Two-tier: user pick,
                // then the applied theme's icon.
                val memcardOverride = if (
                    collectionIconKey == null &&
                    (memoryCardArt == null || memoryCardArt.startsWith("file:///android_asset/systems/physical-media/"))
                ) {
                    memoryCardSlotKeyFor(item)?.let { key ->
                        com.psplauncher.core.ui.icons.LocalCustomIcons.current[key]
                            ?: LocalXmbIconOverrides.current[key]
                    }
                } else {
                    null
                }
                // No art-grid branch here any more: it is handled once above the `when`, for
                // every row type that has covers, rather than in the one branch that had them first.
                if (memcardOverride != null) {
                    // Custom icons render as authored (untinted), like every slot — but the
                    // configured icon-legibility matte still draws behind the still frame.
                    com.psplauncher.core.ui.icons.CustomIconSurface(
                        icon = memcardOverride,
                        contentDescription = null,
                        modifier = Modifier.size(LEADING_ICON_SIZE),
                    )
                } else if (collectionIconKey != null) {
                    PortalIcon(
                        painter = painterResource(categoryIconFor(collectionIconKey).resId),
                        contentDescription = null,
                        modifier = Modifier.size(LEADING_ICON_SIZE),
                    )
                } else if (memoryCardArt != null) {
                    // The bundled physical-media memory-card art is a white silhouette — it
                    // follows the unified icon color like every other glyph (PortalIcon applies
                    // the SrcIn tint AND the icon-legibility matte). Real user/content artwork
                    // (custom collection covers) stays untinted and matte-free.
                    val isBundledSilhouette =
                        memoryCardArt.startsWith("file:///android_asset/systems/physical-media/")
                    if (isBundledSilhouette) {
                        BundledSilhouetteIcon(
                            assetUri = memoryCardArt,
                            modifier = Modifier.size(LEADING_ICON_SIZE),
                        )
                    } else {
                        AsyncImage(
                            model = memoryCardArt,
                            contentDescription = null,
                            modifier = Modifier.size(LEADING_ICON_SIZE),
                        )
                    }
                } else {
                    // Memory-card rows show their matching console icon. All Games gets the generic
                    // cartridge art (sysicon_allgames) and Favorites the star (sysicon_favorites),
                    // both to stand apart from the Game controller.
                    val iconKey = when (item.type) {
                        XMBItemType.MEMORY_CARD -> item.platformId
                        XMBItemType.ALL_GAMES   -> "allgames"
                        XMBItemType.FAVORITES   -> "favorites"
                        else                    -> null
                    }
                    // Override-aware console icon: user pick > theme sysicon > built-in art.
                    if (iconKey != null) {
                        com.psplauncher.core.ui.icons.ConsoleIcon(
                            platformId = iconKey,
                            contentDescription = null,
                            modifier = Modifier.size(LEADING_ICON_SIZE),
                        )
                    } else {
                        PortalIcon(
                            painter = painterResource(systemIconRes(iconKey)),
                            contentDescription = null,
                            modifier = Modifier.size(LEADING_ICON_SIZE),
                        )
                    }
                }
              }
            }
        }
        item.gameId != null -> {
            // Full 144:80 landscape tile (ratio 1.8) — the authentic PSP ICON0 rectangle.
            GameIcon(
                item = item,
                iconStyle = iconStyle,
                modifier = Modifier.size(width = GAME_ICON_WIDTH, height = GAME_ICON_HEIGHT),
            )
            Spacer(modifier = Modifier.width(ARTWORK_TEXT_GAP))
        }
        item.isAndroidApp && item.iconUri != null -> {
            // Apps the user has given artwork render the same 144:80 landscape tile as games, so
            // non-gaming categories (Video / Music / custom) look uniform. These rows stay
            // content_type ANDROID_APP, so artwork never makes them appear in All Games.
            GameIcon(
                item = item,
                iconStyle = iconStyle,
                modifier = Modifier.size(width = GAME_ICON_WIDTH, height = GAME_ICON_HEIGHT),
            )
            Spacer(modifier = Modifier.width(ARTWORK_TEXT_GAP))
        }
        item.isAndroidApp && item.packageName != null -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(LEADING_ICON_SLOT),
            ) {
                AppListIcon(
                    packageName = item.packageName,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        // Every Settings item — including "Android Settings" — shares the wrench badge
        // (sysicon_settings) so Settings reads like the
        // rest of the XMB — an icon + label per row — instead of a blank-led list. Sized and slotted
        // exactly like the memory-card console icons.
        item.id.startsWith("settings_") -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(LEADING_ICON_SLOT),
            ) {
                val settingsOverride = com.psplauncher.core.ui.icons.LocalCustomIcons.current["item_settings"]
                    ?: LocalXmbIconOverrides.current["item_settings"]
                if (settingsOverride != null) {
                    com.psplauncher.core.ui.icons.CustomIconSurface(
                        icon = settingsOverride,
                        contentDescription = null,
                        modifier = Modifier.size(LEADING_ICON_SIZE),
                    )
                } else {
                    PortalIcon(
                        painter = painterResource(systemIconRes("settings")),
                        contentDescription = null,
                        modifier = Modifier.size(LEADING_ICON_SIZE),
                    )
                }
            }
        }
        // Everything else still holds the icon gutter, empty rows included.
        //
        // A 12dp spacer here used to put an empty column's one line 62dp left of every other row
        // in the app ("Nothing played yet" began where its neighbours begin their glyph), so the
        // column's left edge went ragged at exactly the moment the user is least sure what they
        // are looking at. Holding the slot is what the hardware does: an empty Memory Stick still
        // draws the Memory Stick. There are two dozen sites that produce an EMPTY row, so this is
        // every empty column, not one screen.
        else -> Spacer(modifier = Modifier.width(LEADING_ICON_SLOT))
    }
}

// The bundled physical-media silhouettes (collections without a picked icon, and memory cards
// falling back to the default card art), decoded to a bitmap once and
// rendered through PortalIcon so they get the theme tint AND the icon-legibility matte like
// every other silhouette glyph. AsyncImage cannot host the matte — its intrinsic size is
// unknown until the image loads, which would desync the matte geometry. A decode failure
// degrades to the plain untinted image rather than dropping the row's icon.
@Composable
internal fun BundledSilhouetteIcon(assetUri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(assetUri) {
        runCatching {
            val assetPath = assetUri.removePrefix("file:///android_asset/")
            context.assets.open(assetPath).use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream).asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        PortalIcon(
            painter = BitmapPainter(bitmap),
            contentDescription = null,
            modifier = modifier,
        )
    } else {
        AsyncImage(model = assetUri, contentDescription = null, modifier = modifier)
    }
}

/**
 * An installed app in a crossbar row: its own art, on the wallpaper, with no tile behind it.
 *
 * This used to draw the whole `AdaptiveIconDrawable`, background layer included, which is why a
 * Network column showed a black square and a purple gradient square sitting in the same slot and
 * at the same size as a flat white magnifier. Sony never tinted third-party art either; it forced
 * a common container and left the art alone. Dropping the background layer is that, and the
 * reasoning is in [appIconBitmap].
 */
@Composable
private fun AppListIcon(
    packageName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = remember(packageName) { context.appIconBitmap(packageName) } ?: return
    Image(
        bitmap = bitmap,
        contentDescription = null,
        // Always ours, never the publisher's. Stripping the background layer fixes the icons that
        // separate cleanly; the container is what makes the rest agree with them.
        modifier = modifier.clip(AppIconContainerShape),
    )
}

/**
 * One 9i pill: a rounded capsule with a short verb.
 *
 * Sized off the mock's 1920x1080 frame over this panel's density of 2.3375 — its 72px capsule and
 * 28px label become 31dp and 12sp. 28px is also the bundle's stated legibility floor, so the
 * label lands exactly on it rather than near it.
 *
 * The focused one is filled solid white with dark text, as the mock draws it. Left and right walk
 * into the row from either side; see pillNav for what each press costs.
 */
@Composable
private fun XmbActionPill(label: String, focused: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(PillHeight)
            .clip(RoundedCornerShape(PillHeight / 2))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = PillPadH),
    ) {
        Text(
            text = label,
            // Solid white capsule, dark text — 9i's own way of showing which pill is under the
            // cursor. It is the one place in this row that says a press will do something.
            color = if (focused) PillFocusedText else PrimaryText,
            fontSize = PillTextSize,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

// 9i's capsule: 72px tall, 24px of side padding, 12px between them, a 28px label.
private val PillHeight = 31.dp
private val PillPadH = 10.dp
private val PillGap = 5.dp
private val PillTextSize = 12.sp
private val PillFocusedText = Color(0xFF1A0C03)

/**
 * Playing / Completed / Backlog, as one glyph in a capsule.
 *
 * A mark and not a word: the row's title already competes with a meta line and a pill row, and
 * "Completed" spelled out beside a name is a second title. The colours are the launcher's own
 * success and muted tones rather than a new set — three more hexes for three states is three more
 * things to keep in step with the theme.
 */
@Composable
private fun PlayStateBadge(state: PlayState, dimmed: Boolean) {
    val tint = when (state) {
        PlayState.COMPLETED -> Color(0xFF6FD08C)
        PlayState.PLAYING   -> Color(0xFFFFDCAA)
        PlayState.BACKLOG   -> Color(0x99FFFFFF)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = if (dimmed) 0.10f else 0.20f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = state.mark,
            color = tint.copy(alpha = if (dimmed) 0.55f else 1f),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
