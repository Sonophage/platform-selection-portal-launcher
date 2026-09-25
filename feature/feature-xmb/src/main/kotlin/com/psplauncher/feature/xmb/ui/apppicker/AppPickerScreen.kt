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

// ── Installed-app picker (grid) ───────────────────────────────────────────────
//
// The shared picker for the Android library ("Find Games" / "Add Android Apps") and the
// Video / Music / Photo "Add Apps" flows. It is the app's own chrome, and it is built out of the
// app's chrome: core-ui's [PfpSearchField] for the header and [PfpHintBar] for the footer, the
// same two the App Drawer draws.
//
// It used to draw neither. It had a 56dp header of its own — a ‹ back arrow, the title, a live
// "N Selected" count and a second magnifier button labelled "Search" beside a box that already
// said Search — and an INLINE prompt row for a footer, which was a fourth look at the bar every
// other screen had settled on. The title and the count moved to the bar's centre slot, and the
// rest went: B and the search key are named on the bar now, on this screen as on every other.
//
// It is also drawn UNDER the global status strip rather than over it. The picker fills the screen,
// but filling the screen is not the same as owning it: you are still inside the launcher here, the
// same as in the drawer or Settings, so the clock, the battery and the notification corner stay.
// That is [XMBUiState.chromeOverlay]'s half of the partition, and the top padding below is what
// keeps this screen's own content clear of the strip drawn on top of it.
//
// Stateless: driven entirely by [AppPickerState] plus callbacks, so the XMB shell wires it
// exactly like every other overlay. Focus and selection are independent layers — the check
// badge survives the cursor moving away (doc §7's most-repeated requirement).

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
) {
    val sf = deriveStorefrontColors()
    val visible = state.visibleApps()

    Box(
        modifier = modifier
            .fillMaxSize()
            // No whole-background dismiss tap: with a grid and a search field it is an easy
            // accidental cancel. Back and the bar's B are the exits.
            .background(Brush.verticalGradient(listOf(sf.backgroundDeep, sf.backgroundMid))),
    ) {
        // The strip is drawn over this screen by the shell, not by this screen. Reserving its
        // height here is the same thing AppDrawerScreen and the detail pages do, and it is why
        // the header below can never end up underneath the clock.
        Column(modifier = Modifier.fillMaxSize().padding(top = StatusStripHeight)) {
            AppPickerHeader(
                state = state,
                onSearchToggle = onSearchToggle,
                onSearchChange = onSearchChange,
                onSearchDone = onSearchDone,
                colors = sf,
            )

            // BoxWithConstraints puts the viewport height in composition scope, so the adaptive
            // artwork size is resolved BEFORE the first tile composes — tiles render at their
            // final size on frame one, no resize jump.
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
                    )
                }
            }

            // ── Permanent footer: the shared bottom bar (never fades) ──────
            //
            // Always drawn, so grid geometry never depends on whether prompts are showing.
            // Tapping a prompt runs the same callback the pad press runs — the bar knows the
            // action, and this screen already holds the callback for each one.
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

        // Removal confirmation — centered panel over the grid, raised by the ViewModel
        // (confirmingRemovals) so controller SELECT and the touch buttons hit one path.
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

// ── Header: one search field, the width of the screen ─────────────────────────
//
// The App Drawer's header in a second place. The field, the caret rule and the magnifier are
// core-ui's [PfpSearchField]; what is left here is the picker's placement of it and the focus
// handshake, which belongs to whoever owns the FocusRequester.

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

    // Two-frame focus idiom (AppDrawerScreen): the field must be composed before the
    // FocusRequester can grab it.
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

// ── Grid ──────────────────────────────────────────────────────────────────────

@Composable
private fun AppPickerGrid(
    state: AppPickerState,
    visible: List<AppPickerEntry>,
    artworkSize: Dp,
    onTileTapped: (Int) -> Unit,
    onTouchBrowse: (Int) -> Unit,
    colors: StorefrontColors,
) {
    val gridState = rememberLazyGridState()

    // Scroll-into-view, clamped. Keyed on visible.size too, so a list that shrank under a
    // stationary cursor re-clamps (doc §16) instead of leaving the LazyColumn off the end.
    LaunchedEffect(state.focusedIndex, state.usingTouch, visible.size) {
        if (!state.usingTouch && visible.isNotEmpty()) {
            gridState.animateScrollToItem(state.focusedIndex.coerceIn(0, visible.lastIndex))
        }
    }

    // Touch reconciliation, same shape as AppDrawerGrid: drag-start parks the hidden cursor;
    // scroll-settle parks it on the tile nearest the viewport centre.
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

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(PICKER_GRID_COLUMNS),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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

// ── Tile: focus chrome (drawer-faithful) + independent selection check badge ──

private val TILE_BORDER = 1.dp
// Chrome room around the artwork: outer border + 2dp gap + inner hairline on each side.
private val FRAME_ROOM = 8.dp

// ── Adaptive row sizing ──────────────────────────────────────────────────────
//
// This used to mirror AppDrawerGridItem.adaptiveArtworkSize, and that tile is gone: the App
// Drawer draws the shared PfpMediaCard at a fixed 2:3 now, so nothing adapts there and the sizing
// it needed went with it. The picker still shrinks its own artwork, because the picker still has
// the problem the drawer stopped having — a square icon grid that must fit three rows on a short
// viewport. Self-contained now, mirroring nothing.
//
// The picker guarantees three full rows are visible with nothing clipped: on a short viewport
// the artwork shrinks from its 72dp resting size toward the 48dp floor so a row always fits
// three times between the header and the footer. Tall viewports never inflate past 72dp.
//
// It measures the room it is actually given, so the status strip's reserved height and the bar's
// height are already subtracted by the time this runs — there is no second copy of either number
// here to drift from the originals.

private val MIN_ARTWORK_SIZE = 48.dp
private val MAX_ARTWORK_SIZE = 72.dp

private fun pickerAdaptiveArtworkSize(viewportHeight: Dp, rows: Int = 3): Dp {
    // Frame room + label spacer + a 2-line 11sp label block + the tile's vertical padding.
    val tileFixedHeight = FRAME_ROOM + 6.dp + 30.dp + 8.dp
    val rowHeight = (viewportHeight - 28.dp - 14.dp * (rows - 1)) / rows
    return (rowHeight - tileFixedHeight).coerceIn(MIN_ARTWORK_SIZE, MAX_ARTWORK_SIZE)
}
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
            .clickable(onClick = onClick)   // whole tile is the touch target — never just the badge
            .padding(vertical = 4.dp),
    ) {
        Box(modifier = Modifier.size(artworkSize + FRAME_ROOM)) {
            // ── Focus layer: alpha-driven only — no scale, no bounce, no elevation, so tile
            // geometry never shifts. (The geometry came from AppDrawerGridItem, which no longer
            // exists; it lives on here and nowhere else.) ──
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
            // ── Selection layer: persistent low-alpha tint so a checked tile reads from
            // across the grid, independent of the cursor. ──
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
            // Check badge — upper-right, independent of focus; survives the cursor leaving.
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

// ── Removal confirmation panel (hand-built scrim + panel, like UninstallConfirmDialog) ──
//
// A hard input boundary: while it is up, dpad LEFT/RIGHT step the highlight between Cancel
// and Remove, SELECT activates the highlighted option, and BACK cancels — routed through
// XMBViewModel (confirmFocusedOption), never the grid behind the scrim.

/** One modal button row. Focus chrome is alpha/border only — geometry never shifts. */
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
