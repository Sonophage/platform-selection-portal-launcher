package com.psplauncher.feature.xmb.ui.apppicker

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.PfpCheckBadge
import com.psplauncher.core.ui.components.PfpSearchField
import com.psplauncher.core.ui.components.StatusStripHeight
import com.psplauncher.core.ui.theme.StorefrontColors
import com.psplauncher.core.ui.theme.deriveStorefrontColors
import com.psplauncher.feature.xmb.viewmodel.AppPickerEntry
import com.psplauncher.feature.xmb.viewmodel.AppPickerState
import com.psplauncher.feature.xmb.viewmodel.PICKER_GRID_COLUMNS
import com.psplauncher.feature.xmb.viewmodel.pendingRemovals
import com.psplauncher.feature.xmb.viewmodel.visibleApps
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun AppPickerScreen(
    state: AppPickerState,
    onTileTapped: (Int) -> Unit,
    onTouchBrowse: (Int) -> Unit,
    onHeaderBack: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchDone: () -> Unit,
    onApply: () -> Unit,
    onConfirmRemoval: () -> Unit,
    onCancelRemoval: () -> Unit,
    modifier: Modifier = Modifier,

    onColumnsMeasured: (Int) -> Unit = {},
) {
    val sf = deriveStorefrontColors()
    val visible = state.visibleApps()

    Box(
        modifier = modifier
            .fillMaxSize()

            .background(Brush.verticalGradient(listOf(sf.backgroundDeep, sf.backgroundMid))),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = StatusStripHeight)) {
            AppPickerHeader(
                state = state,
                onSearchToggle = onSearchToggle,
                onSearchChange = onSearchChange,
                onSearchDone = onSearchDone,
                colors = sf,
            )

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val artworkSize = pickerAdaptiveArtworkSize(maxHeight)
                if (visible.isEmpty()) {
                    Text(
                        text = if (state.query.isBlank()) "No installed apps"
                        else "No installed apps match \"${state.query.trim()}\"",
                        color = sf.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    AppPickerGrid(
                        state = state,
                        visible = visible,
                        artworkSize = artworkSize,
                        onTileTapped = onTileTapped,
                        onTouchBrowse = onTouchBrowse,
                        colors = sf,
                        onColumnsMeasured = onColumnsMeasured,
                    )
                }
            }

            AppPickerHintBar(
                title = state.title,
                selectedCount = state.selected.size,
                confirmingRemovals = state.confirmingRemovals,
                colors = sf,
                modifier = Modifier.fillMaxWidth(),
                onAction = { action ->
                    when (action) {
                        GamepadAction.BACK ->
                            if (state.confirmingRemovals) onCancelRemoval() else onHeaderBack()
                        GamepadAction.SELECT ->
                            if (state.confirmingRemovals) onConfirmRemoval()
                            else onTileTapped(state.focusedIndex)
                        GamepadAction.CHANGE_SORT -> onSearchToggle(true)
                        GamepadAction.HOME -> onApply()
                        else -> Unit
                    }
                },
            )
        }

        if (state.confirmingRemovals) {
            RemovalConfirmPanel(
                labels = state.apps
                    .filter { it.packageName in state.pendingRemovals() }
                    .map { it.label },
                focusedOption = state.confirmFocusedOption,
                onConfirm = onConfirmRemoval,
                onCancel = onCancelRemoval,
                colors = sf,
            )
        }
    }
}

private val HEADER_HEIGHT = 56.dp

@Composable
private fun AppPickerHeader(
    state: AppPickerState,
    onSearchToggle: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchDone: () -> Unit,
    colors: StorefrontColors,
) {
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.searchActive) {
        if (state.searchActive) {
            withFrameNanos {}
            withFrameNanos {}
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PfpSearchField(
            query = state.query,
            active = state.searchActive,
            focusRequester = searchFocus,
            placeholder = "Search apps",
            onActivate = { onSearchToggle(true) },
            onQueryChange = onSearchChange,
            onDone = onSearchDone,
            colors = colors,
        )
    }
}

@Composable
private fun AppPickerGrid(
    state: AppPickerState,
    visible: List<AppPickerEntry>,
    artworkSize: Dp,
    onTileTapped: (Int) -> Unit,
    onTouchBrowse: (Int) -> Unit,
    colors: StorefrontColors,

    onColumnsMeasured: (Int) -> Unit,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(state.focusedIndex, state.usingTouch, visible.size) {
        if (!state.usingTouch && visible.isNotEmpty()) {
            gridState.animateScrollToItem(state.focusedIndex.coerceIn(0, visible.lastIndex))
        }
    }

    var fingerScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(gridState) {
        gridState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                fingerScrolled = true
                onTouchBrowse(gridState.firstVisibleItemIndex)
            }
        }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling && fingerScrolled) {
                    fingerScrolled = false
                    val info = gridState.layoutInfo
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    val nearest = info.visibleItemsInfo.minByOrNull { item ->
                        val itemCenter = item.offset.y + item.size.height / 2
                        kotlin.math.abs(itemCenter - center)
                    }?.index
                    if (nearest != null) onTouchBrowse(nearest)
                }
            }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gridWidth = maxWidth - PICKER_GRID_SIDE_PADDING * 2
        val columns = ((gridWidth + PICKER_TILE_GAP) / (PICKER_TILE_TARGET_WIDTH + PICKER_TILE_GAP))
            .toInt()
            .coerceIn(PICKER_GRID_MIN_COLUMNS, PICKER_GRID_MAX_COLUMNS)
        LaunchedEffect(columns) { onColumnsMeasured(columns) }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(horizontal = PICKER_GRID_SIDE_PADDING, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(PICKER_TILE_GAP),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(visible, key = { _, app -> app.packageName }) { index, app ->
                AppPickerTile(
                    entry = app,
                    isFocused = !state.usingTouch && index == state.focusedIndex,
                    isChecked = app.packageName in state.selected,
                    artworkSize = artworkSize,
                    onClick = { onTileTapped(index) },
                    colors = colors,
                )
            }
        }
    }
}

private val TILE_BORDER = 1.dp

internal val FRAME_ROOM = 8.dp

internal val MIN_ARTWORK_SIZE = 48.dp
internal val MAX_ARTWORK_SIZE = 72.dp

internal fun pickerAdaptiveArtworkSize(viewportHeight: Dp, rows: Int = 3): Dp {
    val tileFixedHeight = FRAME_ROOM + 6.dp + 30.dp + 8.dp
    val rowHeight = (viewportHeight - 28.dp - 14.dp * (rows - 1)) / rows
    return (rowHeight - tileFixedHeight).coerceIn(MIN_ARTWORK_SIZE, MAX_ARTWORK_SIZE)
}

private val PICKER_TILE_TARGET_WIDTH = 99.dp

private val PICKER_TILE_GAP = 10.dp

private val PICKER_GRID_SIDE_PADDING = 32.dp

private const val PICKER_GRID_MIN_COLUMNS = 3
private const val PICKER_GRID_MAX_COLUMNS = 12

private val FOCUS_TWEEN = 120
private val CHECK_TWEEN = 100

@Composable
private fun AppPickerTile(
    entry: AppPickerEntry,
    isFocused: Boolean,
    isChecked: Boolean,
    artworkSize: Dp,
    onClick: () -> Unit,
    colors: StorefrontColors,
) {
    val focus by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(FOCUS_TWEEN),
        label = "appPickerFocus",
    )
    val check by animateFloatAsState(
        targetValue = if (isChecked) 1f else 0f,
        animationSpec = tween(CHECK_TWEEN),
        label = "appPickerCheck",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(modifier = Modifier.size(artworkSize + FRAME_ROOM)) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(colors.selectionGlow.copy(alpha = colors.selectionGlow.alpha * focus)),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .border(TILE_BORDER, colors.tileSelectedEdge.copy(alpha = focus)),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .padding(2.dp)
                    .border(TILE_BORDER, colors.tileSelectedInner.copy(alpha = focus)),
            )

            Box(
                Modifier
                    .matchParentSize()
                    .background(colors.tileSelectedInner.copy(alpha = 0.10f * check)),
            )
            val icon = entry.icon
            if (icon != null) {
                Image(
                    painter = rememberDrawablePainter(icon),
                    contentDescription = entry.label,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(artworkSize),
                )
            } else {
                Spacer(Modifier.size(artworkSize))
            }

            PfpCheckBadge(
                fill = colors.tileSelectedEdge,
                markColor = colors.backgroundDeep,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .alpha(check),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.label,
            color = if (isFocused) colors.textPrimary else colors.textSecondary,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp,
        )
    }
}

@Composable
private fun ConfirmOption(
    label: String,
    focused: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
    colors: StorefrontColors,
) {
    val focus by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(CHECK_TWEEN),
        label = "confirmOptionFocus",
    )
    val baseColor = if (destructive) colors.destructive else colors.textSecondary
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
            .background(colors.tileSelectedInner.copy(alpha = 0.25f * focus), RoundedCornerShape(2.dp))
            .border(1.dp, colors.tileSelectedEdge.copy(alpha = focus), RoundedCornerShape(2.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (focus > 0.5f) colors.textPrimary else baseColor,
            fontSize = 14.sp,
            fontWeight = if (destructive) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun RemovalConfirmPanel(
    labels: List<String>,
    focusedOption: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    colors: StorefrontColors,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.overlayDim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.menuPanel)
                .border(1.dp, colors.chromeDivider.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
        ) {
            Text(
                text = "Remove ${labels.size} app(s) from this library?",
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 12.dp)
                    .background(colors.chromeDivider.copy(alpha = 0.25f)),
            )
            Text(
                text = labels.joinToString(", "),
                color = colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConfirmOption(
                    label = "Cancel",
                    focused = focusedOption == AppPickerState.CONFIRM_CANCEL,
                    onClick = onCancel,
                    colors = colors,
                )
                ConfirmOption(
                    label = "Remove",
                    focused = focusedOption == AppPickerState.CONFIRM_REMOVE,
                    onClick = onConfirm,
                    destructive = true,
                    colors = colors,
                )
            }
        }
    }
}
