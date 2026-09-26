package com.psplauncher.feature.xmb.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.psplauncher.core.ui.theme.withArtTint
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.detail.DetailRowSpacing
import com.psplauncher.core.ui.detail.PfpDetailBackground
import com.psplauncher.core.ui.detail.LocalDetailViewportHeight
import com.psplauncher.core.ui.detail.PfpDetailQuickAction
import com.psplauncher.core.ui.detail.detailPalette
import com.psplauncher.core.ui.detail.PfpDetailBreadcrumb
import com.psplauncher.core.ui.detail.PfpDetailField
import com.psplauncher.core.ui.detail.PfpDetailFieldBand
import com.psplauncher.core.ui.detail.PfpDetailHelperFooter
import com.psplauncher.core.ui.detail.PfpDetailArtBackdrop
import com.psplauncher.core.ui.detail.PfpConfirmOverlay
import com.psplauncher.core.ui.detail.PfpDetailLaunchButton
import com.psplauncher.core.ui.detail.PfpDetailScaffold
import com.psplauncher.core.ui.detail.PfpDetailSectionLabel
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.core.ui.theme.menuCursorEdge
import com.psplauncher.core.ui.theme.menuCursorFill
import com.psplauncher.core.ui.components.PspContextMenuOverlay
import com.psplauncher.core.ui.components.PspMenuRow
import com.psplauncher.feature.xmb.ui.collection.CollectionPickerPanel
import com.psplauncher.feature.xmb.viewmodel.relativeDate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.ReadOnlyComposable
import com.psplauncher.core.ui.theme.LocalPfpTextColors

private val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.primary

private val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.secondary
private val ActionFail = Color(0xFFFF8A8A)

private const val OVERVIEW_EXPAND_THRESHOLD = 190

private val LOGO_TOP_GAP = 26.dp
private val LOGO_MAX_HEIGHT = 104.dp
private val LOGO_MAX_WIDTH = 460.dp
private val PLAY_BUTTON_WIDTH = 238.dp

private val PANEL_CHROME_HEIGHT = 130.dp
private val DETAILS_BUTTON_WIDTH = 196.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameDetailScreen(
    gameId: Long,
    onBack: () -> Unit,

    onNotifications: () -> Unit = {},
    pendingGamepadAction: GamepadAction? = null,
    onGamepadActionConsumed: () -> Unit = {},

    showTouchControls: Boolean = true,
    onTouchInput: () -> Unit = {},

    autoLaunch: Boolean = false,

    initialAction: String? = null,

    initialDiscId: Long? = null,
    modifier: Modifier = Modifier,
    viewModel: GameDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(gameId, initialDiscId) {
        viewModel.prepareForOpen()
        viewModel.loadGame(gameId, initialDiscId)
    }

    if (autoLaunch) {
        val loadedGameId = state.game?.id
        LaunchedEffect(loadedGameId) {
            if (loadedGameId == gameId) viewModel.launch(playSound = false)
        }
    }

    if (initialAction != null) {
        val loadedGameId = state.game?.id
        LaunchedEffect(loadedGameId, initialAction) {
            if (loadedGameId != gameId) return@LaunchedEffect

            DetailAction.entries.firstOrNull { it.name == initialAction }?.let(viewModel::activateAction)
        }
    }

    var revealed by rememberSaveable(gameId) { mutableStateOf(!autoLaunch) }
    if (!revealed) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) revealed = true
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(state.launchError) {
            if (state.launchError != null) revealed = true
        }
    }

    LaunchedEffect(state.closed) {
        if (state.closed) {
            viewModel.prepareForOpen()
            onBack()
        }
    }

    val routeAction: (GamepadAction) -> Unit = { action ->
        if (action == GamepadAction.HOME) onNotifications()
        else viewModel.handleGamepadAction(action)
    }

    LaunchedEffect(pendingGamepadAction) {
        if (pendingGamepadAction != null && !state.showArtworkStudio) {
            routeAction(pendingGamepadAction)
            onGamepadActionConsumed()
        }
    }

    if (!revealed) return

    if (state.isLoading) {
        PfpDetailBackground(modifier = modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = detailPalette().focus)
        }
        return
    }

    val game = state.game
    if (game == null) {
        LaunchedEffect(Unit) { viewModel.onPageLaidOut() }
        PfpDetailScaffold(
            modifier = modifier,
            header = {
                PfpDetailBreadcrumb(title = "Library", subtitle = "Game not found", onBack = onBack)
            },
        ) {
            Spacer(Modifier.height(24.dp))
            Text("This game is no longer in your library.", color = TextPrimary, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text("Press Back to return to the library.", color = TextMuted, fontSize = 13.sp)
        }
        return
    }

    if (state.showArtworkStudio) {
        GameThemed(state.artAccentArgb) {
            ArtworkStudioScreen(
                gameId = gameId,
                onClose = viewModel::onArtworkStudioClosed,
                pendingGamepadAction = pendingGamepadAction,
                onGamepadActionConsumed = onGamepadActionConsumed,
                showTouchControls = showTouchControls,
                onTouchInput = onTouchInput,
                modifier = modifier.fillMaxSize(),
            )
        }
        return
    }

    GameThemed(state.artAccentArgb) {
        GameDetailContent(
            state = state,
            game = game,
            onBack = onBack,
            showTouchControls = showTouchControls,
            onTouchInput = onTouchInput,
            viewModel = viewModel,
            onAction = routeAction,
            modifier = modifier,
        )
    }
}

@Composable
private fun GameThemed(accentArgb: Long?, content: @Composable () -> Unit) {
    val base = LocalPFPColors.current
    val themed = remember(base, accentArgb) {
        if (accentArgb == null) base else base.withArtTint(Color(accentArgb.toInt()))
    }
    CompositionLocalProvider(LocalPFPColors provides themed, content = content)
}

@Composable
private fun GameDetailContent(
    state: GameDetailUiState,
    game: Game,
    onBack: () -> Unit,
    showTouchControls: Boolean,
    onTouchInput: () -> Unit,
    viewModel: GameDetailViewModel,

    onAction: (GamepadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pfpColors = LocalPFPColors.current
    val accentColor = state.platform?.accentColor?.let { Color(it) } ?: pfpColors.accentColor
    val focus = state.navFocusKey

    val pageScrollState = rememberScrollState()
    val mediaListState = rememberLazyListState()

    val requesterFor = remember { mutableStateMapOf<String, BringIntoViewRequester>() }
    val nodeY = remember { mutableStateMapOf<String, Float>() }

    val pageTopRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(Unit) {
        snapshotFlow { nodeY.toMap() }
            .distinctUntilChanged()
            .collect { viewModel.onNodeGeometry(it) }
    }

    LaunchedEffect(game.id) {
        snapshotFlow { nodeY.keys.toSet() }
            .filter { it.isNotEmpty() }
            .first()
        viewModel.onPageLaidOut()
    }

    LaunchedEffect(focus) {
        val key = focus ?: return@LaunchedEffect
        val mediaIndex = state.detailMedia.indexOfFirst { GameDetailKeys.media(mediaStableId(it)) == key }
        val target = if (mediaIndex >= 0) GameDetailKeys.MEDIA else key

        val inTopBand = key in TopBandKeys
        val requester = requesterFor[target]
        if (!inTopBand && requester == null) return@LaunchedEffect

        (if (inTopBand) pageTopRequester else requester)?.bringIntoView()
        if (mediaIndex >= 0) {
            mediaListState.scrollToItem(mediaIndex)
        }
    }

    PfpDetailScaffold(
        modifier = modifier

            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); onTouchInput() } },
        scrollState = pageScrollState,
        header = {
            PfpDetailBreadcrumb(
                title = state.platform?.name ?: game.platformId.uppercase(),
                subtitle = game.kindLabel(),
                onBack = onBack,
            )
        },
        footer = {
            PfpDetailHelperFooter(
                items = gameDetailHelperItems(state),
                visible = !showTouchControls && state.cursorVisible,
                onAction = onAction,
            )
        },

        backdrop = { PfpDetailArtBackdrop(game.heroUri ?: game.artworkUri ?: game.boxArtUri) },

        overlay = { GameDetailOverlays(state = state, game = game, viewModel = viewModel) },
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).bringIntoViewRequester(pageTopRequester))

        val panelContent = state.panelContent
        val panelPage = state.effectivePanelPage
        if (panelContent != null) {
            Spacer(Modifier.height(6.dp))
            DetailPanelStrip(
                pages = panelContent.pages,
                current = panelPage,
                onPageTapped = viewModel::onPanelPageTapped,
                modifier = Modifier.align(Alignment.End),
            )
            Spacer(Modifier.height(10.dp))

            val panelHeight = (LocalDetailViewportHeight.current - PANEL_CHROME_HEIGHT)
                .coerceAtLeast(180.dp)

            val panelBase = Modifier.fillMaxWidth().height(panelHeight)
            val panelModifier = if (panelPage == DetailPanelPage.GALLERY) {
                panelBase.detailNode(GameDetailKeys.MEDIA, requesterFor, nodeY)
            } else {
                panelBase
            }
            GameDetailPanel(
                content = panelContent,
                page = panelPage,

                titleFallback = true,
                focusedMediaId = focus?.removePrefix("game-detail:media:")?.takeIf {
                    focus.startsWith("game-detail:media:")
                },
                onMediaTapped = { viewModel.onNodeTapped(GameDetailKeys.media(mediaStableId(it))) },
                modifier = panelModifier,
            )
        }

        if (state.showDiscPicker) {
            Spacer(Modifier.height(DetailRowSpacing))
            DiscRow(
                members = state.discMembers,
                selectedId = state.selectedDiscId,
                focusedKey = focus,
                requesterFor = requesterFor,
                nodeY = nodeY,
                onSelect = { id -> viewModel.onNodeTapped(GameDetailKeys.disc(id)) },
                rowModifier = Modifier.detailNode(GameDetailKeys.DISCS, requesterFor, nodeY),
            )
        }

        if (state.launchError != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.launchError, color = ActionFail, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Get help",
                    color = detailPalette().focus,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { viewModel.requestLaunchHelp() },
                )
            }
        }

        Spacer(Modifier.height(DetailRowSpacing))
        Row(
            modifier = Modifier.detailNode(GameDetailKeys.ACTIONS, requesterFor, nodeY),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PfpDetailQuickAction(

                label = if (game.isFavorite) "Favorited" else "Favorite",
                icon = Icons.Filled.Favorite,
                focused = focus == GameDetailKeys.FAVORITE,
                available = true,
                onClick = { viewModel.onNodeTapped(GameDetailKeys.FAVORITE) },
                modifier = Modifier.detailNode(GameDetailKeys.FAVORITE, requesterFor, nodeY),
            )
            PfpDetailQuickAction(
                label = "Options",
                icon = Icons.Filled.Settings,
                focused = focus == GameDetailKeys.OPTIONS,
                available = true,
                onClick = { viewModel.onNodeTapped(GameDetailKeys.OPTIONS) },
                modifier = Modifier.detailNode(GameDetailKeys.OPTIONS, requesterFor, nodeY),
            )
            PfpDetailLaunchButton(
                label = "Play",
                icon = Icons.Filled.PlayArrow,
                focused = focus == GameDetailKeys.LAUNCH,
                onClick = { viewModel.onNodeTapped(GameDetailKeys.LAUNCH) },
                modifier = Modifier
                    .weight(1f)
                    .detailNode(GameDetailKeys.LAUNCH, requesterFor, nodeY),
            )
        }

        if (state.launchError == null) {
            (state.actionMessage ?: state.artworkMessage)?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = detailPalette().focus, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(if (state.launchError != null) 0.dp else DetailRowSpacing))
    }
}

@Composable
private fun GameDetailOverlays(
    state: GameDetailUiState,
    game: Game,
    viewModel: GameDetailViewModel,
) {
    Box(Modifier.fillMaxSize()) {
        state.imageViewerUri?.let { imageUri ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
                    .clickable(onClick = viewModel::closeImageViewer),
            ) {
                coil3.compose.AsyncImage(
                    model = com.psplauncher.core.ui.image.rememberArtworkModel(imageUri),
                    contentDescription = "Media preview",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                )
            }
        }

        if (state.showVideoPlayer && state.videoUri != null) {
            GameVideoOverlay(
                videoUri = state.videoUri,
                onClose  = viewModel::closeVideoPlayer,
            )
        }

        AnimatedVisibility(state.showDetailsMenu, enter = fadeIn(), exit = fadeOut()) {
            PspContextMenuOverlay(
                title = game.displayTitle,
                rows = state.visibleDetailRows.map { row ->
                    PspMenuRow(
                        label = when (row) {
                            DetailQuickAction.FAVORITE ->
                                if (game.isFavorite) "Unfavorite" else "Favorite"
                            else -> row.label
                        },
                    )
                },
                selectedIndex = state.detailsIndex,
                onRowActivated = { viewModel.onDetailsRowTapped(state.visibleDetailRows[it]) },
                onDismiss = viewModel::closeDetailsMenu,
            )
        }

        AnimatedVisibility(state.showOptions, enter = fadeIn(), exit = fadeOut()) {
            PspContextMenuOverlay(
                title = "Options",
                rows = state.visibleActions.map { action ->
                    PspMenuRow(
                        label = action.dynamicLabel(game.isFavorite, state.isFetchingArtwork),
                        isDestructive = action == DetailAction.REMOVE,
                    )
                },
                selectedIndex = state.optionsIndex,
                onRowActivated = { viewModel.onOptionRowTapped(state.visibleActions[it]) },
                onDismiss = viewModel::closeOptions,
            )
        }

        AnimatedVisibility(state.showEmulatorPicker, enter = fadeIn(), exit = fadeOut()) {
            EmulatorPickerPanel(
                options      = state.emulatorPickerOptions,
                selectedId   = game.emulatorPackage,
                focusedIndex = state.emulatorPickerIndex,
                onPick       = viewModel::onEmulatorPickTapped,
                onClose      = viewModel::closeEmulatorPicker,
            )
        }

        AnimatedVisibility(state.metadataPreview != null, enter = fadeIn(), exit = fadeOut()) {
            state.metadataPreview?.let { preview ->
                MetadataPreviewPanel(
                    ui             = preview,
                    focusFill      = menuCursorFill(),
                    focusEdge      = menuCursorEdge(),
                    onSelectPolicy = viewModel::selectMetadataPolicy,
                    onCycleSource  = viewModel::cycleMetadataSource,
                    onToggleField  = viewModel::toggleMetadataField,
                    onApply        = viewModel::applyMetadataPreview,
                    onClose        = viewModel::closeMetadataPreview,
                )
            }
        }

        AnimatedVisibility(state.collectionPicker.visible, enter = fadeIn(), exit = fadeOut()) {
            CollectionPickerPanel(
                ui                  = state.collectionPicker,
                onRowClick          = viewModel::onCollectionRowClick,
                onClose             = viewModel::closeCollectionPicker,
                onCreateTextChanged = viewModel::onCreateCollectionTextChanged,
                onConfirmCreate     = viewModel::confirmCreateCollection,
                onCancelCreate      = viewModel::cancelCreateCollection,
            )
        }

        AnimatedVisibility(state.isEditingNote, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                NoteEditor(state.noteText, viewModel::onNoteChanged, viewModel::saveNote, viewModel::cancelNote)
            }
        }

        AnimatedVisibility(state.isEditingTitle, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                TitleEditor(
                    text       = state.titleText,
                    onChange   = viewModel::onTitleChanged,
                    onSave     = viewModel::saveTitle,
                    onReset    = viewModel::resetTitleToDefault,
                    onCancel   = viewModel::cancelTitleEdit,
                )
            }
        }

        state.manualViewerUri?.let { source ->
            ManualViewerOverlay(
                source      = source,
                title       = "${game.displayTitle} — Manual",
                page        = state.manualPage,
                scrollSteps = state.manualScrollSteps,
                onPageCount = viewModel::setManualPageCount,
                onPrevPage  = viewModel::manualPrevPage,
                onNextPage  = viewModel::manualNextPage,
                onClose     = viewModel::closeManualViewer,
            )
        }

        if (state.confirmRemove) {
            PfpConfirmOverlay(
                title = "Remove ${game.displayTitle}?",
                message = "Removes this game from your library. ROM and app files are not deleted.",
                confirmLabel = "Remove",
                cancelLabel = "Cancel",
                confirmFocused = state.navFocusKey == GameDetailKeys.CONFIRM_REMOVE,
                cancelFocused = state.navFocusKey == GameDetailKeys.CONFIRM_CANCEL,
                onConfirm = { viewModel.onNodeTapped(GameDetailKeys.CONFIRM_REMOVE) },
                onCancel = { viewModel.onNodeTapped(GameDetailKeys.CONFIRM_CANCEL) },
            )
        }
    }
}

@Composable
private fun DiscRow(
    members: List<Game>,
    selectedId: Long?,
    focusedKey: String?,
    requesterFor: MutableMap<String, BringIntoViewRequester>,
    nodeY: MutableMap<String, Float>,
    onSelect: (Long) -> Unit,
    rowModifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PfpDetailSectionLabel("Discs")
        LazyRow(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(
                items = members,
                key = { _, member -> GameDetailKeys.disc(member.id) },
            ) { _, member ->
                val key = GameDetailKeys.disc(member.id)
                val isFocused = focusedKey == key
                val isSelected = selectedId == member.id
                val label = member.discNumber?.let { "Disc $it" } ?: "Playlist"

                Column(
                    modifier = Modifier
                        .widthIn(min = 116.dp)
                        .detailNode(key, requesterFor, nodeY)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) detailPalette().focus.copy(alpha = 0.14f) else detailPalette().rowFill)
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = when {
                                isFocused -> detailPalette().focus
                                isSelected -> detailPalette().focus.copy(alpha = 0.55f)
                                else -> detailPalette().rowEdge
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable(role = Role.Button) { onSelect(member.id) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = when {
                            member.isMissing -> "Missing"
                            isSelected -> "Preferred"
                            else -> "Available"
                        },
                        color = if (member.isMissing) ActionFail else TextMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameInformationBand(
    game: Game,
    state: GameDetailUiState,
    focusedKey: String?,
    requesterFor: MutableMap<String, BringIntoViewRequester>,
    nodeY: MutableMap<String, Float>,
    viewModel: GameDetailViewModel,
) {
    val resolved = state.resolvedLaunch
    PfpDetailFieldBand(
        focused = focusedKey == GameDetailKeys.INFO,
        modifier = Modifier.detailNode(GameDetailKeys.INFO, requesterFor, nodeY),

        onClick = if (state.showEmulatorAction) ({ viewModel.onNodeTapped(GameDetailKeys.INFO) }) else null,
    ) {
        game.releaseYear?.let { year ->
            PfpDetailField(label = "Released", value = year.toString())
        }
        game.developer?.takeIf { it.isNotBlank() }?.let {
            PfpDetailField(label = "Developer", value = it)
        }

        game.publisher?.takeIf { !it.isNullOrBlank() && !it.equals(game.developer, ignoreCase = true) }?.let {
            PfpDetailField(label = "Publisher", value = it)
        }
        game.genre?.takeIf { it.isNotBlank() }?.let {
            PfpDetailField(label = "Genre", value = it)
        }

        game.lastPlayedAt?.takeIf { it > 0L }?.let {
            PfpDetailField(label = "Last played", value = relativeDate(it))
        }
        if (game.totalPlayTimeMillis > 0) {
            PfpDetailField(label = "Play time", value = formatPlayTime(game.totalPlayTimeMillis))
        }
        if (!state.isPackageBacked && resolved != null) {
            PfpDetailField(
                label = "Emulator",
                value = resolved.profile.name,
                secondary = listOfNotNull(
                    resolved.coreName?.let { "Core: $it" },
                    resolved.source.label.lowercase(),
                ).joinToString("  ·  ").takeIf { it.isNotBlank() },
                disclosure = true,
            )
        }
    }
}

internal fun gameDetailHelperItems(state: GameDetailUiState): List<ControllerPromptItem> = when {
    state.imageViewerUri != null || state.showVideoPlayer ->
        listOf(
            ControllerPromptItem(GamepadAction.SELECT, "Close"),
            ControllerPromptItem(GamepadAction.BACK, "Back"),
        )
    state.manualViewerUri != null ->
        listOf(
            ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Scroll"),
            ControllerPromptItem(GamepadAction.PREV_CATEGORY, "Prev page"),
            ControllerPromptItem(GamepadAction.NEXT_CATEGORY, "Next page"),
            ControllerPromptItem(GamepadAction.BACK, "Close"),
        )
    state.confirmRemove ->
        listOf(
            ControllerPromptItem(GamepadAction.SELECT, "Remove"),
            ControllerPromptItem(GamepadAction.BACK, "Cancel"),
        )
    state.isEditingNote || state.isEditingTitle ->
        listOf(ControllerPromptItem(GamepadAction.BACK, "Cancel"))
    state.metadataPreview != null ->
        listOf(
            ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Navigate"),
            ControllerPromptItem(GamepadAction.SELECT, "Apply / Toggle"),
            ControllerPromptItem(GamepadAction.NAVIGATE_LEFT, "Policy"),
            ControllerPromptItem(GamepadAction.PREV_CATEGORY, "Source"),
            ControllerPromptItem(GamepadAction.BACK, "Close"),
        )
    state.showEmulatorPicker ->
        listOf(
            ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Navigate"),
            ControllerPromptItem(GamepadAction.SELECT, "Choose"),
            ControllerPromptItem(GamepadAction.BACK, "Cancel"),
        )
    state.collectionPicker.visible ->
        listOf(
            ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Navigate"),
            ControllerPromptItem(GamepadAction.SELECT, "Toggle"),
            ControllerPromptItem(GamepadAction.BACK, "Close"),
        )
    state.showOptions || state.showDetailsMenu ->
        listOf(
            ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Navigate"),
            ControllerPromptItem(GamepadAction.SELECT, "Select"),
            ControllerPromptItem(GamepadAction.BACK, "Close"),
        )
    else -> listOf(
        ControllerPromptItem(GamepadAction.SELECT, confirmLabelFor(state)),
        ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options"),
        ControllerPromptItem(GamepadAction.BACK, "Back"),
    )
}

private fun confirmLabelFor(state: GameDetailUiState): String {
    val focus = state.navFocusKey ?: return "Play"
    val media = state.detailMedia.firstOrNull { GameDetailKeys.media(mediaStableId(it)) == focus }
    return when {
        media != null -> if (media.isVideo) "Play" else "Preview"
        focus == GameDetailKeys.OVERVIEW ->
            if (state.descriptionExpanded) "Collapse" else "Read more"
        focus.startsWith("game-detail:disc:") -> "Choose disc"
        focus == GameDetailKeys.DETAILS -> "Details"
        focus == GameDetailKeys.INFO && state.showEmulatorAction -> "Change emulator"
        else -> "Play"
    }
}

private val TopBandKeys = setOf(
    GameDetailKeys.ACTIONS,
    GameDetailKeys.FAVORITE,
    GameDetailKeys.OPTIONS,
    GameDetailKeys.LAUNCH,
)

private fun Modifier.detailNode(
    key: String,
    requesterFor: MutableMap<String, BringIntoViewRequester>,
    nodeY: MutableMap<String, Float>,
): Modifier = this
    .bringIntoViewRequester(requesterFor.getOrPut(key) { BringIntoViewRequester() })
    .onGloballyPositioned { coordinates -> nodeY[key] = coordinates.positionInRoot().y }

private fun Game.kindLabel(): String = when {
    shortcutId != null || launchIntentUri != null -> "PC Shortcut"
    romPath == null && packageName != null        -> "Game App"
    else                                          -> "ROM"
}

@Composable
private fun GameLogoBlock(logoUri: String?, title: String, platform: String) {
    Column {
        Text(
            text = platform,
            color = TextMuted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        if (logoUri != null) {
            coil3.compose.AsyncImage(
                model = com.psplauncher.core.ui.image.rememberArtworkModel(logoUri),
                contentDescription = title,

                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier
                    .heightIn(max = LOGO_MAX_HEIGHT)
                    .widthIn(max = LOGO_MAX_WIDTH),
            )
        } else {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = LOGO_MAX_WIDTH),
            )
        }
    }
}

private fun formatPlayTime(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1     -> "Under a minute"
        minutes < 60    -> "$minutes min"
        else            -> "${minutes / 60} h ${minutes % 60} min"
    }
}

private val OptionsPanelMaxHeight: Dp = 440.dp
private val OptionsRowScrollStep: Dp = 58.dp

private fun DetailAction.dynamicLabel(favorite: Boolean, refreshing: Boolean): String = when (this) {
    DetailAction.FAVORITE -> if (favorite) "Unfavorite" else "Favorite"
    DetailAction.REFRESH -> if (refreshing) "Refreshing..." else "Refresh"
    else -> label
}

@Composable
private fun NoteEditor(text: String, onChange: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xF20A0A14))
            .padding(16.dp),
    ) {
        Text("Edit Note", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            label = { Text("Note", color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = menuCursorEdge(),
                unfocusedBorderColor = Color(0x44FFFFFF),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = menuCursorEdge(),
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            maxLines = 4,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel", color = TextMuted) }
            TextButton(onClick = onSave) { Text("Save", color = menuCursorEdge(), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun TitleEditor(
    text: String,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xF20A0A14))
            .padding(16.dp),
    ) {
        Text("Edit Title", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Changes the display name only — the ROM file is not renamed.",
            color = TextMuted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            label = { Text("Display Title", color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = menuCursorEdge(),
                unfocusedBorderColor = Color(0x44FFFFFF),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = menuCursorEdge(),
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            singleLine = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onReset) { Text("Reset to Default", color = TextMuted, fontSize = 12.sp) }
            Row {
                TextButton(onClick = onCancel) { Text("Cancel", color = TextMuted) }
                TextButton(onClick = onSave) { Text("Save", color = menuCursorEdge(), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun EmulatorPickerPanel(
    options: List<com.psplauncher.core.domain.model.EmulatorProfile>,
    selectedId: String?,
    focusedIndex: Int,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 460.dp)
                .fillMaxWidth(0.86f)
                .heightIn(max = OptionsPanelMaxHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xF20A0A14))
                .clickable(enabled = false) {}
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Choose Emulator",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Up/Down  Navigate  •  Select  Confirm  •  B  Cancel",
                color = TextMuted.copy(alpha = 0.5f),
                fontSize = 10.sp,
            )

            val scrollState = rememberScrollState()
            val stepPx = with(androidx.compose.ui.platform.LocalDensity.current) { OptionsRowScrollStep.roundToPx() }
            LaunchedEffect(focusedIndex) { scrollState.animateScrollTo(focusedIndex * stepPx) }

            Column(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEachIndexed { index, profile ->
                    val isFocused  = focusedIndex == index
                    val isSelected = selectedId != null && (profile.id == selectedId || profile.packageName == selectedId)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isFocused  -> menuCursorFill()
                                    isSelected -> menuCursorFill().copy(alpha = 0.17f)
                                    else       -> Color(0xFF1B1B26)
                                }
                            )
                            .then(
                                if (isFocused) Modifier.border(1.5.dp, menuCursorEdge(), RoundedCornerShape(8.dp))
                                else if (isSelected) Modifier.border(1.dp, menuCursorEdge().copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .clickable { onPick(profile.id) }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.name, color = TextPrimary, fontSize = 14.sp, maxLines = 1)
                            Text(
                                profile.packageName,
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                        if (isSelected) {
                            com.psplauncher.core.ui.components.PfpCheckMark(
                                com.psplauncher.core.ui.detail.detailPalette().focus,
                                Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameVideoOverlay(videoUri: String, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var videoSize by remember(videoUri) { mutableStateOf<androidx.media3.common.VideoSize?>(null) }
    val player = remember(videoUri) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUri))
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) { videoSize = size }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) onClose()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.view.TextureView(ctx).also { view ->
                    player.setVideoTextureView(view)
                    view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                        applyFit(v as android.view.TextureView, videoSize)
                    }
                }
            },
            update = { view -> applyFit(view, videoSize) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun applyFit(view: android.view.TextureView, size: androidx.media3.common.VideoSize?) {
    val vw = size?.width?.toFloat() ?: return
    val vh = size.height.toFloat()
    if (vw <= 0f || vh <= 0f || view.width == 0 || view.height == 0) return
    val viewW = view.width.toFloat()
    val viewH = view.height.toFloat()
    val scale = minOf(viewW / vw, viewH / vh)
    val matrix = android.graphics.Matrix().apply {
        setScale((vw * scale) / viewW, (vh * scale) / viewH, viewW / 2f, viewH / 2f)
    }
    view.setTransform(matrix)
}
