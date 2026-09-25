package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.PfpHintBar
import com.psplauncher.core.ui.components.StatusStripHeight
import androidx.compose.ui.text.style.TextOverflow
import com.psplauncher.core.ui.theme.LocalPfpTextColors
import com.psplauncher.core.ui.theme.StorefrontColors
import com.psplauncher.core.ui.theme.deriveStorefrontColors
import com.psplauncher.core.ui.theme.menuCursor

// Resolved per theme rather than fixed: this screen draws the theme's own background gradient,
// so a fixed white label is unreadable the moment the user picks a pale scheme. Same reason
// MusicTrackPicker and the INLINE hint style resolve theirs. See PFPTheme.
private val PickerText: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.primary

@Composable
fun GamePickerScreen(
    onConfirm: (selectedGameIds: Set<Long>, selectedCollectionIds: Set<Long>) -> Unit,
    onCancel: () -> Unit,
    pendingGamepadAction: GamepadAction? = null,
    onGamepadActionConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GamePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // Confirm/cancel both clear the picker — the ViewModel is retained across open/close, so
    // selections must not carry over to the next time the picker is opened.
    val confirmAndClear: () -> Unit = {
        val (gameIds, collectionIds) = viewModel.getSelectedItems()
        onConfirm(gameIds, collectionIds)
        viewModel.clearSelection()
    }
    val cancelAndClear: () -> Unit = {
        viewModel.clearSelection()
        onCancel()
    }

    // Scroll to keep selected item visible
    LaunchedEffect(state.selectedItemId) {
        if (state.selectedItemId != null) {
            val itemIds = buildPickerItemIds(state)
            val index = itemIds.indexOf(state.selectedItemId)
            if (index >= 0) {
                listState.animateScrollToItem(
                    index = index,
                    scrollOffset = -80,
                )
            }
        }
    }

    // Handle gamepad input
    LaunchedEffect(pendingGamepadAction) {
        if (pendingGamepadAction != null) {
            when (pendingGamepadAction) {
                GamepadAction.NAVIGATE_UP -> viewModel.moveSelection(-1)
                GamepadAction.NAVIGATE_DOWN -> viewModel.moveSelection(+1)
                GamepadAction.SELECT -> viewModel.activateSelection()
                GamepadAction.OPEN_CONTEXT_MENU -> viewModel.toggleSelectedPlatform()
                GamepadAction.BACK -> cancelAndClear()
                GamepadAction.HOME -> confirmAndClear()
                else -> {} // Other actions handled by parent
            }
            onGamepadActionConsumed()
        }
    }

    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val sf = deriveStorefrontColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            // The status strip is drawn over this screen by the shell, not by this screen: the
            // picker is chrome, so the clock and the battery stay on top of it. Reserving the
            // strip's height is what keeps the first row from ending up underneath them — the
            // same thing AppDrawerScreen, the detail pages and the app picker each do.
            .padding(top = StatusStripHeight)
            // The shared scrim, not this screen's own gradient. It used to draw the raw theme
            // gradient at a flat 0.94 alpha, which made it read as a different surface from the
            // App Drawer and the installed-app picker standing beside it; these anchors are
            // solved for contrast rather than chosen. See storefrontColorsFor.
            .background(Brush.verticalGradient(listOf(sf.backgroundDeep, sf.backgroundMid))),
    ) {

        // Content
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
        ) {
            // Platform groups - use identity-based selection
            for ((groupIndex, group) in state.platformGroups.withIndex()) {
                val platformId = group.platform.platformId

                item {
                    PlatformGroupHeader(
                        group = group,
                        isExpanded = state.platformExpandedStates[platformId] ?: true,
                        isSelected = pickerPlatformId(platformId) == state.selectedItemId,
                        onToggleExpanded = { viewModel.togglePlatformExpanded(platformId) },
                        onToggleSelectAll = { selectAll ->
                            viewModel.togglePlatformAllSelection(platformId, selectAll)
                        },
                    )
                }

                // Games in this platform
                if (state.platformExpandedStates[platformId] == true) {
                    items(group.games) { game ->
                        GamePickerRow(
                            title = game.displayTitle,
                            isSelected = pickerGameId(game.id) == state.selectedItemId,
                            isChecked = game.id in state.selectedGameIds,
                            onToggle = { viewModel.toggleGameSelection(game.id) },
                            modifier = Modifier.padding(start = 48.dp),
                        )
                    }
                }
            }

            // Collections section
            if (state.pcShortcuts.isNotEmpty()) {
                item {
                    Text(
                        text = "Collections",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PickerText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            .background(
                                if (PICKER_COLLECTIONS_HEADER == state.selectedItemId)
                                    sf.tileSelectedInner.copy(alpha = 0.2f)
                                else
                                    Color.Transparent
                            ),
                    )
                }

                items(state.pcShortcuts) { collection ->
                    GamePickerRow(
                        title = collection.name,
                        isSelected = pickerCollectionId(collection.id) == state.selectedItemId,
                        isChecked = collection.id in state.selectedCollectionIds,
                        onToggle = { viewModel.toggleCollectionSelection(collection.id) },
                        modifier = Modifier.padding(start = 32.dp),
                    )
                }
            }

        }

        // ── Permanent footer: the shared bottom bar ───────────────────────
        //
        // Two Material3 TextButtons used to sit here, and the prompts sat under the title at the
        // top — so this screen said "Cancel" twice, in two looks, at two ends, and named neither
        // of the buttons that do it. One bar now, the same one every other screen draws.
        GamePickerHintBar(
            selectedCount = state.selectedGameIds.size + state.selectedCollectionIds.size,
            colors = sf,
            modifier = Modifier.fillMaxWidth(),
            onAction = { action ->
                when (action) {
                    GamepadAction.BACK -> cancelAndClear()
                    GamepadAction.SELECT -> viewModel.activateSelection()
                    GamepadAction.OPEN_CONTEXT_MENU -> viewModel.toggleSelectedPlatform()
                    GamepadAction.HOME -> confirmAndClear()
                    else -> Unit
                }
            },
        )
    }
}

// ── The picker's footer ───────────────────────────────────────────────────────
//
// The shared [PfpHintBar]. The centre slot carries what the deleted header said — the screen's
// name and the live count — because that is context rather than a control, and it is where
// Settings and the installed-app picker already put context.

@Composable
private fun GamePickerHintBar(
    selectedCount: Int,
    colors: StorefrontColors,
    modifier: Modifier = Modifier,
    onAction: ((GamepadAction) -> Unit)? = null,
) {
    PfpHintBar(
        items = listOf(
            ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Navigate"),
            ControllerPromptItem(GamepadAction.SELECT, "Toggle"),
            // Only meaningful on a platform header, but the picker opens on one and the bar is
            // fixed chrome — a prompt that comes and goes as the cursor moves down a list reads
            // as flicker.
            ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Expand / Collapse"),
            ControllerPromptItem(GamepadAction.HOME, "Add"),
            ControllerPromptItem(GamepadAction.BACK, "Cancel"),
        ),
        modifier = modifier,
        onAction = onAction,
        centre = {
            Text(
                text = if (selectedCount == 0) "Add Games" else "Add Games  ·  $selectedCount selected",
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun PlatformGroupHeader(
    group: PlatformGameGroup,
    isExpanded: Boolean,
    isSelected: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleSelectAll: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = group.isAllSelected || group.selectedCount > 0,
            onCheckedChange = { selectAll -> onToggleSelectAll(selectAll) },
        )

        Text(
            text = group.platform.displayName,
            fontSize = if (isSelected) 15.sp else 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) PickerText else PickerText.copy(alpha = 0.8f),
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )

        Text(
            text = "${group.selectedCount}/${group.games.size}",
            fontSize = 12.sp,
            color = if (isSelected) PickerText else LocalPfpTextColors.current.inactive,
            modifier = Modifier.padding(end = 8.dp),
        )

        TextButton(onClick = onToggleExpanded) {
            Text(if (isExpanded) "▼" else "▶", fontSize = 12.sp, color = PickerText)
        }
    }
}

@Composable
private fun GamePickerRow(
    title: String,
    isSelected: Boolean,
    isChecked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
            .menuCursor(isSelected),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle() },
        )
        Text(
            text = title,
            color = if (isSelected) PickerText else PickerText.copy(alpha = 0.8f),
            fontSize = if (isSelected) 14.sp else 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
    }
}
