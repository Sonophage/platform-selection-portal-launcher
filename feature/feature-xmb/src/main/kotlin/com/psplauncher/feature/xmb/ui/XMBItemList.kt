package com.psplauncher.feature.xmb.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ImportContacts
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.icons.AppIconContainerShape
import com.psplauncher.core.ui.icons.appIconBitmap
import com.psplauncher.core.ui.icons.GameIconStyle
import com.psplauncher.core.ui.icons.LocalXmbIconOverrides
import com.psplauncher.core.ui.icons.PortalIcon
import com.psplauncher.core.ui.icons.ThemedGlyph
import com.psplauncher.core.ui.icons.categoryIconFor
import com.psplauncher.core.ui.icons.systemIconRes
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.feature.xmb.viewmodel.GRID_COVER_COUNT
import com.psplauncher.core.domain.model.PlayState
import androidx.compose.ui.graphics.vector.ImageVector
import com.psplauncher.feature.xmb.viewmodel.pillsFor
import com.psplauncher.feature.xmb.viewmodel.shelfCardFor
import com.psplauncher.feature.xmb.viewmodel.ShelfCard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.NewReleases
import com.psplauncher.feature.xmb.viewmodel.XMBItem
import com.psplauncher.feature.xmb.viewmodel.XMBItemType
import com.psplauncher.core.ui.image.rememberArtworkModel
import com.psplauncher.themekit.XmbLayoutSpec
import androidx.compose.runtime.ReadOnlyComposable
import com.psplauncher.core.ui.theme.LocalPfpTextColors
import com.psplauncher.core.ui.components.XmbDim

private val GAME_ICON_WIDTH = 126.dp
private val GAME_ICON_HEIGHT = 70.dp

internal val ROW_HEIGHT = 88.dp

private val ARTWORK_TEXT_GAP = 16.dp

private val TAP_TARGET_HEIGHT = 72.dp

internal val LEADING_ICON_SLOT = XmbLayoutSpec.DEFAULT.itemIconSlotDp.dp

internal data class LiveRowProgress(
    val itemId: String,
    val fraction: Float,
    val label: String?,
)

internal val LocalLiveRowProgress = androidx.compose.runtime.compositionLocalOf<LiveRowProgress?> { null }

private val ScrubberWidth = 96.dp
private val ScrubberHeight = 3.dp

private val LEADING_ICON_SIZE = XmbLayoutSpec.DEFAULT.itemIconDp.dp

internal val LEADING_ICON_CENTER = 18.dp + LEADING_ICON_SLOT / 2

private val PrimaryText: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.primary

private val SecondaryText: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.secondary

private val InactiveText: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.inactive

private const val FlatUnfocusedRowAlpha = 0.68f

private val SelectedTextShadow = Shadow(
    color = Color(0x73001627),
    offset = Offset.Zero,
    blurRadius = 12f,
)

private val ROW_HORIZONTAL_PADDING = 18.dp

val XmbTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.75f),
    offset = Offset(0f, 2f),
    blurRadius = 4f,
)

internal const val MEMORY_CARD_DEFAULT_ART = "file:///android_asset/systems/physical-media/_default.png"

private val DRILL_GAME_COLUMN_LEFT = 138.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun XmbDrillFlyout(
    siblings: List<XMBItem>,
    siblingIndex: Int,
    items: List<XMBItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    onItemLongPress: (Int) -> Unit,

    onSiblingTap: (Int) -> Unit = {},
    iconStyle: GameIconStyle = GameIconStyle.PSP_RECTANGLE,

    barTopY: Dp = 40.dp,
    belowTopY: Dp = 152.dp,

    iconAnimatingAllowed: Boolean = false,

    labelHiddenByPanel: Boolean,

    onPillActivated: (String) -> Unit,

    focusedPillIndex: Int?,
    pillFade: Float = 1f,
    cardArtGrid: Boolean = true,
    metadataAsSubtitle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
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

            onPillActivated = onPillActivated,
            focusedPillIndex = focusedPillIndex,
            pillFade = pillFade,
            modifier = Modifier.fillMaxHeight().width(DRILL_GAME_COLUMN_LEFT - 10.dp),
        )

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
            pillFade = pillFade,
            metadataAsSubtitle = metadataAsSubtitle,
            modifier = Modifier.fillMaxSize().padding(start = DRILL_GAME_COLUMN_LEFT),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun XmbGameColumn(
    items: List<XMBItem>,
    selectedIndex: Int,
    iconStyle: GameIconStyle,
    belowTopY: Dp,
    labelHiddenByPanel: Boolean,
    onPillActivated: (String) -> Unit,

    focusedPillIndex: Int?,
    pillFade: Float = 1f,
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

        val rowsAbove = (belowTopY.value / ROW_HEIGHT.value).toInt() + 2
        val rowsBelow = ((maxHeight.value - belowTopY.value) / ROW_HEIGHT.value).toInt() + 2
        val first = (sel - rowsAbove).coerceAtLeast(0)
        val last = (sel + rowsBelow).coerceAtMost(items.lastIndex)

        for (i in first..last) {
            XmbVerticalListRow(
                item = items[i],
                isSelected = i == selectedIndex,

                showText = true,

                cardArtGrid = cardArtGrid,

                labelHiddenByPanel = labelHiddenByPanel,
                onPillActivated = onPillActivated,
                focusedPillIndex = focusedPillIndex,
                pillFade = pillFade,
                metadataAsSubtitle = metadataAsSubtitle,
                iconStyle = iconStyle,
                onClick = { onItemSelected(i) },
                onLongPress = { onItemLongPress(i) },
                showIcon = true,

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

@Composable
private fun SiblingIcon(item: XMBItem, selected: Boolean) {
    val chip = if (selected) 56.dp else 40.dp
    val videoGlyph = when (item.type) {
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

private fun consoleIconKeyFor(item: XMBItem): String? = when (item.type) {
    XMBItemType.ALL_GAMES   -> "allgames"
    XMBItemType.FAVORITES   -> "favorites"
    XMBItemType.MEMORY_CARD -> item.platformId
    else                    -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun XMBItemList(
    items: List<XMBItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    onItemLongPress: (Int) -> Unit,
    iconStyle: GameIconStyle = GameIconStyle.PSP_RECTANGLE,

    barTopY: Dp = 40.dp,

    belowTopY: Dp = 152.dp,

    showIcons: Boolean = true,

    showLabels: Boolean = true,

    labelHiddenByPanel: Boolean = false,
    onPillActivated: (String) -> Unit,

    focusedPillIndex: Int?,
    pillFade: Float = 1f,
    cardArtGrid: Boolean = true,
    metadataAsSubtitle: Boolean = false,

    drillCursorOnSelected: Boolean = false,

    previousRiseRows: Float = XmbLayoutSpec.DEFAULT.previousItemRiseRows,

    fadeByDistance: Boolean = true,

    textShadow: Boolean = true,

    iconAnimatingAllowed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().fillMaxHeight().clipToBounds()) {
        val rowsBelow = ((maxHeight.value - belowTopY.value) / ROW_HEIGHT.value).toInt()
            .coerceAtLeast(1)
        val sel = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

        if (items.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().offset(y = belowTopY)) {
                val last = minOf(items.size, sel + rowsBelow)
                for (i in sel until last) {
                    key(items[i].id) {
                        XmbVerticalListRow(
                            labelHiddenByPanel = labelHiddenByPanel,
                            onPillActivated = onPillActivated,
                            focusedPillIndex = focusedPillIndex,
                            pillFade = pillFade,
                            cardArtGrid = cardArtGrid,
                            metadataAsSubtitle = metadataAsSubtitle,
                            item = items[i],
                            isSelected = i == selectedIndex,

                            showText = showLabels,
                            iconStyle = iconStyle,
                            onClick = { onItemSelected(i) },
                            onLongPress = { onItemLongPress(i) },
                            showIcon = showIcons,
                            trailingCursor = drillCursorOnSelected && i == selectedIndex,
                            fadeByDistance = fadeByDistance,

                            distance = i - sel,
                            textShadow = textShadow,
                            iconAnimatingAllowed = iconAnimatingAllowed,
                            modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT),
                        )
                    }
                }
            }
        }

        if (selectedIndex in 1..items.lastIndex) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT / 2)

                    .offset(y = barTopY - ROW_HEIGHT * previousRiseRows)
                    .clipToBounds(),

                contentAlignment = Alignment.BottomStart,
            ) {
                XmbVerticalListRow(
                    item = items[selectedIndex - 1],
                    isSelected = false,

                    showText = showLabels,
                    cardArtGrid = cardArtGrid,

                    labelHiddenByPanel = labelHiddenByPanel,
                    onPillActivated = onPillActivated,
                    focusedPillIndex = focusedPillIndex,
                    pillFade = pillFade,
                    metadataAsSubtitle = metadataAsSubtitle,
                    iconStyle = iconStyle,
                    onClick = { onItemSelected(selectedIndex - 1) },
                    onLongPress = { onItemLongPress(selectedIndex - 1) },
                    showIcon = showIcons,
                    fadeByDistance = fadeByDistance,

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

    showText: Boolean,
    iconStyle: GameIconStyle,
    onClick: () -> Unit,
    onLongPress: () -> Unit,

    showIcon: Boolean = true,

    trailingCursor: Boolean = false,

    fadeByDistance: Boolean = true,

    distance: Int = 0,

    textShadow: Boolean = true,

    labelHiddenByPanel: Boolean,
    onPillActivated: (String) -> Unit,

    focusedPillIndex: Int?,
    pillFade: Float = 1f,
    cardArtGrid: Boolean = true,
    metadataAsSubtitle: Boolean = false,

    iconAnimatingAllowed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 0.9f,

        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "xmbListRowScale",
    )
    val rowAlpha by animateFloatAsState(
        targetValue = when {
            isSelected -> 1f

            item.type == XMBItemType.EMPTY -> 0.5f

            fadeByDistance -> XmbDim.ranked(distance)
            else -> FlatUnfocusedRowAlpha
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "xmbListRowAlpha",
    )

    val glow by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "xmbRowGlow",
    )

    val subtitleStyle = if (textShadow) TextStyle(shadow = XmbTextShadow) else TextStyle.Default

    val density = LocalDensity.current
    val iconCenterPx = remember(density) { with(density) { LEADING_ICON_CENTER.toPx() } }
    var rowWidthPx by remember { mutableStateOf(0f) }

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

                .weight(1f, fill = false)
                .height(TAP_TARGET_HEIGHT)

                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongPress,
                )
                .padding(horizontal = ROW_HORIZONTAL_PADDING),
        ) {
            if (showIcon && !item.textOnly) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.psplauncher.core.ui.icons.LocalIconAnimating provides
                        (isSelected && iconAnimatingAllowed),
                ) {
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

            val showGameText = item.textOnly || !item.isRealGame || isSelected

            val panelHidesLabel = isSelected && item.isRealGame && !item.textOnly && labelHiddenByPanel
            val labelAlpha by animateFloatAsState(
                targetValue = if (panelHidesLabel) 0f else 1f,
                animationSpec = tween(220),
                label = "xmbRowLabelFade",
            )
            if (showText && showGameText && labelAlpha > 0f) {
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

                        PlayState.fromName(item.playState)?.let { state ->
                            Spacer(Modifier.width(7.dp))
                            PlayStateBadge(state, dimmed = !isSelected)
                        }
                    }

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

                    if (isSelected && pillFade > 0f) {
                        val pills = pillsFor(item)
                        if (pills.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(PillGap),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 6.dp).alpha(pillFade),
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

private fun Modifier.selectedIconBloom(isSelected: Boolean): Modifier = this

@Composable
private fun XmbItemLeadingIcon(
    item: XMBItem,
    iconStyle: GameIconStyle,
    isSelected: Boolean,
    cardArtGrid: Boolean = true,
) {
    val iconTint = LocalPFPColors.current.iconColor

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

        item.type == XMBItemType.VIDEO_COLLECTIONS -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Bookmarks, null, iconTint, Modifier.size(46.dp))
            }
        }

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

        item.type == XMBItemType.PHOTO_FOLDER -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Folder, null, iconTint, Modifier.size(48.dp))
            }
        }

        item.type == XMBItemType.PHOTO_ALBUMS -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.PhotoLibrary, null, iconTint, Modifier.size(48.dp))
            }
        }

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

        item.type == XMBItemType.CAMERA -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.PhotoCamera, null, iconTint, Modifier.size(48.dp))
            }
        }

        item.type == XMBItemType.SEARCH -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Search, null, iconTint, Modifier.size(44.dp))
            }
        }

        item.type == XMBItemType.ADD_ACTION -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                ThemedGlyph(itemSlotKeyFor(item.type) ?: "", Icons.Filled.Add, null, iconTint, Modifier.size(44.dp))
            }
        }

        item.type == XMBItemType.SHELF -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(LEADING_ICON_SLOT)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(LEADING_ICON_SIZE).selectedIconBloom(isSelected),
                ) {
                    Icon(
                        imageVector = shelfGlyphFor(item.id),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(LEADING_ICON_SIZE),
                    )
                }
            }
        }
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
                val collectionIconKey = item.iconKey?.takeIf { item.type == XMBItemType.COLLECTION }

                val memoryCardArt = item.coverUri
                    ?: MEMORY_CARD_DEFAULT_ART.takeIf { item.type == XMBItemType.COLLECTION && collectionIconKey == null }

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

                if (memcardOverride != null) {
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
                    val iconKey = when (item.type) {
                        XMBItemType.MEMORY_CARD -> item.platformId
                        XMBItemType.ALL_GAMES   -> "allgames"
                        XMBItemType.FAVORITES   -> "favorites"
                        else                    -> null
                    }

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
            GameIcon(
                item = item,
                iconStyle = iconStyle,
                modifier = Modifier.size(width = GAME_ICON_WIDTH, height = GAME_ICON_HEIGHT),
            )
            Spacer(modifier = Modifier.width(ARTWORK_TEXT_GAP))
        }
        item.isAndroidApp && item.iconUri != null -> {
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

        else -> Spacer(modifier = Modifier.width(LEADING_ICON_SLOT))
    }
}

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

        modifier = modifier.clip(AppIconContainerShape),
    )
}

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

            color = if (focused) PillFocusedText else PrimaryText,
            fontSize = PillTextSize,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private val PillHeight = 31.dp
private val PillPadH = 10.dp
private val PillGap = 5.dp
private val PillTextSize = 12.sp
private val PillFocusedText = Color(0xFF1A0C03)

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

private fun shelfGlyphFor(cardId: String): ImageVector = when (val shelf = shelfCardFor(cardId)) {
    is ShelfCard.Favorites -> Icons.Filled.Star
    is ShelfCard.RecentlyAdded -> Icons.Filled.NewReleases
    is ShelfCard.Marked -> when (shelf.state) {
        PlayState.PLAYING -> Icons.Filled.PlayCircle
        PlayState.COMPLETED -> Icons.Filled.CheckCircle
        PlayState.BACKLOG -> Icons.Filled.Bookmarks
    }
    null -> Icons.Filled.Star
}
