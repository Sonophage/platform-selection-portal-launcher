package com.psplauncher.feature.xmb.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.psplauncher.core.common.format.formatByteSize
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.Video
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.ui.components.PfpHintBar
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.detail.PfpConfirmOverlay
import com.psplauncher.core.ui.detail.PfpDetailLaunchButton
import com.psplauncher.core.ui.detail.PfpMessageOverlay
import com.psplauncher.core.ui.detail.PfpOverlayCard
import com.psplauncher.core.ui.detail.PfpOverlayTitle
import com.psplauncher.core.ui.detail.PfpTextPromptOverlay
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.core.ui.theme.menuCursor
import com.psplauncher.core.ui.theme.menuCursorEdge
import com.psplauncher.core.ui.components.PspContextMenuOverlay
import com.psplauncher.core.ui.components.PspMenuRow
import com.psplauncher.feature.xmb.video.VideoPlayerScreen
import androidx.compose.runtime.ReadOnlyComposable
import com.psplauncher.core.ui.theme.LocalPfpTextColors

// Neutral dark surfaces stay fixed; accent colors come from the active theme so this screen
// follows the chosen color scheme.
private val PageBg = Color(0xFF06060C)
private val ActionFill = Color(0xFF1B1B26)
// Resolved per theme rather than fixed: on a pale scheme a light label on a light
// wallpaper is unreadable, and every one of these was light. See PFPTheme.
private val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.primary
// Resolved per theme rather than fixed: on a pale scheme a light label on a light
// wallpaper is unreadable, and every one of these was light. See PFPTheme.
private val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.secondary

@UnstableApi
@Composable
fun VideoDetailScreen(
    videoId: String,
    onBack: () -> Unit,
    pendingGamepadAction: GamepadAction? = null,
    onGamepadActionConsumed: () -> Unit = {},
    onTouchInput: () -> Unit = {},
    // Direct-play mode (from search): fire the page's own primary action as soon as THIS film has
    // loaded, instead of waiting on a second A press. The page still opens underneath and is what
    // you come back to. Mirrors GameDetailScreen's autoLaunch.
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: VideoDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val thumbnailPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.onThumbnailPicked(uri) }

    LaunchedEffect(videoId) { viewModel.loadVideo(videoId) }
    // Keyed on the LOADED film's id, not on a loaded flag: the ViewModel is retained across
    // open/close, so on reopen it still holds the previous film and a boolean key fires the
    // effect against that stale row -- which is how the game path once launched the wrong ROM.
    //
    // The primary action rather than play(0): the page decides whether the first button is Play
    // or Resume, and a half-watched film that restarted from zero because search took a different
    // route would be a second answer to a question the page already answers.
    if (autoPlay) {
        val loadedVideoId = state.video?.id
        LaunchedEffect(loadedVideoId) {
            if (loadedVideoId == videoId) state.primaryActions.firstOrNull()?.let(viewModel::activate)
        }
    }
    // Reset `closed` after handling it: the ViewModel is retained across open/close, so a stale
    // closed=true would otherwise instantly re-close the detail the next time it's opened (needing
    // a second tap).
    LaunchedEffect(state.closed) { if (state.closed) { onBack(); viewModel.onClosedHandled() } }
    LaunchedEffect(state.pickThumbnail) {
        if (state.pickThumbnail) {
            thumbnailPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
            viewModel.consumeThumbnailPick()
        }
    }
    // Detail-level input only when the player overlay isn't up (the player consumes input itself).
    LaunchedEffect(pendingGamepadAction, state.playing) {
        val action = pendingGamepadAction ?: return@LaunchedEffect
        if (!state.playing) {
            viewModel.handleGamepadAction(action)
            onGamepadActionConsumed()
        }
    }

    // When PFP regains focus after an external hand-off, drop the launch overlay and refresh this
    // video's metadata (resume / last-watched) — no rescan, no focus/scroll reset.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onReturnedFromExternal()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Defensive timeout: if the hand-off never backgrounded us, don't let the overlay stick.
    LaunchedEffect(state.handedOffToPlayer) {
        if (state.handedOffToPlayer) {
            kotlinx.coroutines.delay(8000)
            viewModel.clearExternalOverlay()
        }
    }

    if (state.isLoading) {
        Box(modifier.fillMaxSize().background(PageBg)) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = menuCursorEdge())
        }
        return
    }
    val video = state.video ?: run { onBack(); return }
    val pfpColors = LocalPFPColors.current

    // Same translucent theme-gradient backdrop as the Music browser, so the XMB wave stays visible
    // behind and all full-screen menus read consistently.
    Box(
        modifier = modifier
            .fillMaxSize()
            // Any touch marks the input source as touch (revealing the Back pill) without consuming.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); onTouchInput() } }
            .background(
                Brush.verticalGradient(
                    0f to pfpColors.backgroundTop.copy(alpha = 0.72f),
                    1f to pfpColors.backgroundBottom.copy(alpha = 0.90f),
                )
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().widthIn(max = 920.dp).align(Alignment.Center)
                // BEFORE the scroll, so it shrinks the viewport rather than adding scrollable
                // space under the content. Padding inside a scroller is just more to scroll
                // past: the Play button still came to rest under the prompt row, because the
                // row is a fixed child of the same Box and does not move with the content.
                .padding(bottom = PromptRowClearance)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Prominent thumbnail card, crisp (no fade), as the focal point. The 56dp spacer that
            // used to sit above it was clearing a header pill row that no longer exists.
            video.effectiveThumbnailUri?.let { thumb ->
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                )
            }

            Text(video.displayTitle, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(metadataLine(video), color = TextMuted, fontSize = 13.sp)
            if (video.resumePositionMs > 0) {
                Text("Resume at ${fmtTime(video.resumePositionMs)}", color = menuCursorEdge(), fontSize = 12.sp)
            }

            Spacer(Modifier.height(6.dp))

            val primaries = state.primaryActions
            primaries.forEachIndexed { i, action ->
                // The lead action used to carry a green fill to mark it out. It no longer needs
                // one: exactly one button is focused, the focused one inverts to near-white, and
                // that is a louder mark than a colour ever was.
                DetailButton(
                    label = action.label,
                    icon = when (action) {
                        VideoDetailAction.RESUME  -> Icons.Filled.Replay
                        VideoDetailAction.RESTART -> Icons.Filled.SkipPrevious
                        else                      -> Icons.Filled.PlayArrow
                    },
                    focused = state.mainFocus == i,
                    onClick = { viewModel.activate(action) },
                )
            }
            state.actionMessage?.let {
                Text(it, color = menuCursorEdge(), fontSize = 12.sp)
                LaunchedEffect(it) { kotlinx.coroutines.delay(2500); viewModel.dismissMessage() }
            }
        }

        // One footer, and it is the touch surface too.
        //
        // This screen was the last one carrying big floating pills that said what a prompt row
        // says. It kept them because it had no footer to move them into; it has one now. The
        // prompts fire through handleGamepadAction — the same dispatcher the pad uses — so Back
        // still closes the Options menu first and backs out second without a second copy of that
        // rule living out here.
        //
        // Not conditional on input mode. A row that appears only for touch is a different screen
        // for touch, which is the thing being undone.
        if (!state.playing) {
            PfpHintBar(
                items = videoDetailHelperItems(state),
                onAction = viewModel::handleGamepadAction,
                // Flush to the bottom edge: the bar is the page's footer, not a pill lying on it,
                // so it takes no inset of its own. This was the last INLINE prompt row in the app.
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (state.showOptions) {
            PspContextMenuOverlay(
                title = "Options",
                rows = state.optionsActions.map { action ->
                    val label = if (action == VideoDetailAction.FAVORITE) {
                        if (video.isFavorite) "Remove from Favorites" else "Add to Favorites"
                    } else action.label
                    PspMenuRow(label)
                },
                selectedIndex = state.optionsIndex,
                onRowActivated = { viewModel.activate(state.optionsActions[it]) },
                onDismiss = viewModel::closeOptions,
            )
        }

        if (state.showPlaylistPicker) {
            PlaylistPicker(
                options = state.playlistOptions,
                selectedIndex = state.playlistPickerIndex,
                onRowClick = viewModel::onPlaylistRowClick,
            )
        }

        if (state.creatingPlaylist) {
            RenameDialog(
                title = "New Playlist",
                confirmLabel = "Create",
                text = state.newPlaylistName,
                onTextChange = viewModel::onNewPlaylistNameChange,
                onConfirm = viewModel::confirmCreatePlaylist,
                onCancel = viewModel::cancelCreatePlaylist,
            )
        }

        if (state.infoVisible) {
            InfoDialog(video = video, onDismiss = { viewModel.handleGamepadAction(GamepadAction.BACK) })
        }

        if (state.isEditingTitle) {
            RenameDialog(
                text = state.titleText,
                onTextChange = viewModel::onTitleChanged,
                onConfirm = viewModel::saveTitle,
                onCancel = viewModel::cancelTitleEdit,
            )
        }

        if (state.confirmRemove) {
            // Cancel is drawn focused: it is the safe choice, and B performs it.
            PfpConfirmOverlay(
                title = "Remove from library?",
                message = "\"${video.displayTitle}\" will be removed from this library. " +
                    "The file on disk is not deleted.",
                confirmLabel = "Remove",
                cancelLabel = "Cancel",
                confirmFocused = false,
                cancelFocused = true,
                onConfirm = viewModel::confirmRemove,
                onCancel = { viewModel.handleGamepadAction(GamepadAction.BACK) },
            )
        }

        // External-player launch error (real dialog, controller-dismissible via A/B).
        state.launchError?.let { err ->
            PfpMessageOverlay(
                title = "Can't play video",
                message = err,
                onDismiss = viewModel::dismissLaunchError,
                dismissLabel = "OK",
            )
        }

        // The hand-off to an external player used to draw a themed card here — a thumbnail, a
        // spinner and "Launching…". The launch disc replaced it: one ceremony for every kind of
        // media, drawn by the XMB shell, which sits above this screen. Two would have been worse
        // than redundant, because the disc fades out to reveal what is behind it and what was
        // behind it was the spinner rather than the player.
        //
        // state.handedOffToPlayer stays: it is still the "we handed off" flag that
        // onReturnedFromExternal reads. It simply no longer draws anything.

        // Fullscreen player overlay.
        if (state.playing) {
            VideoPlayerScreen(
                videos = state.siblings.ifEmpty { listOf(video) },
                startIndex = state.siblings.indexOfFirst { it.id == video.id }.coerceAtLeast(0),
                startPositionMs = state.playStartPositionMs,
                onSaveResume = viewModel::saveResume,
                onExit = viewModel::onPlaybackExit,
                pendingGamepadAction = pendingGamepadAction,
                onGamepadActionConsumed = onGamepadActionConsumed,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailButton(
    label: String,
    icon: ImageVector,
    focused: Boolean,
    onClick: () -> Unit,
) {
    // Auto-scroll into view when a controller focuses this button, so the whole action list is
    // reachable inside the scrolling content even when the thumbnail pushes it below the fold.
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) bringIntoView.bringIntoView() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            // The same pill and the same inversion as the game and app pages, so one idiom
            // covers every detail page rather than the video page keeping its own.
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (focused) com.psplauncher.core.ui.detail.DetailButtonFocusFill
                else com.psplauncher.core.ui.detail.DetailButtonRest
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val content = if (focused) com.psplauncher.core.ui.detail.DetailButtonFocusText else TextPrimary
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlaylistPicker(
    options: List<VideoPlaylistOption>,
    selectedIndex: Int,
    onRowClick: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            modifier = Modifier.padding(36.dp).width(320.dp)
                .background(Color(0xF0101018), RoundedCornerShape(14.dp)).padding(vertical = 12.dp),
        ) {
            Text("Add to Playlist", color = menuCursorEdge(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            options.forEachIndexed { i, opt ->
                Text(
                    (if (opt.checked) "● " else "○ ") + opt.name,
                    color = if (i == selectedIndex) Color.White else TextMuted,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onRowClick(i) }
                        .menuCursor(i == selectedIndex)
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                )
            }
            val createIndex = options.size
            Text(
                "+ Create New Playlist",
                color = if (createIndex == selectedIndex) Color.White else menuCursorEdge(),
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth()
                    .clickable { onRowClick(createIndex) }
                    .menuCursor(createIndex == selectedIndex)
                    .padding(horizontal = 20.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun InfoDialog(video: Video, onDismiss: () -> Unit) {
    // A field list rather than a message, so it uses the card directly instead of
    // PfpMessageOverlay. Same chrome, same scrim-cancels rule.
    PfpOverlayCard(onScrimTap = onDismiss) {
        PfpOverlayTitle(video.displayTitle)
        Spacer(Modifier.height(10.dp))
        InfoRow("Duration", fmtTime(video.durationMs ?: 0))
        video.resolutionLabel?.let { InfoRow("Resolution", it) }
        video.codec?.let { InfoRow("Format", it) }
        video.mimeType?.let { InfoRow("Type", it) }
        video.sizeBytes?.let { InfoRow("Size", formatByteSize(it)) }
        video.relativePath?.let { InfoRow("Location", it) }
        InfoRow("File", video.displayName)
        Spacer(Modifier.height(18.dp))
        PfpDetailLaunchButton(
            label = "OK",
            icon = null,
            focused = true,
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, color = TextPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun RenameDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    title: String = "Rename Title",
    confirmLabel: String = "Save",
) {
    PfpTextPromptOverlay(
        title = title,
        value = text,
        placeholder = "",
        onValueChange = onTextChange,
        onConfirm = onConfirm,
        onCancel = onCancel,
        confirmLabel = confirmLabel,
    )
}

private fun metadataLine(video: Video): String = buildList {
    video.durationMs?.let { add(fmtTime(it)) }
    video.resolutionLabel?.let { add(it) }
    video.codec?.let { add(it) }
}.joinToString("  ·  ").ifEmpty { video.displayName }

private fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** The prompt row's own height plus its gap from the edge — what the body must stay above. */
private val PromptRowClearance = 44.dp

// ── Helper footer ─────────────────────────────────────────────────────────────

/**
 * What the prompt row offers, for the context the page is actually in.
 *
 * Pure function of the state, like [gameDetailHelperItems], so the two screens' rows can be
 * compared in a test rather than by eye — and so a row promising an action the dispatcher ignores
 * in that context is catchable without a device.
 *
 * Every entry names exactly one action on purpose: a prompt naming two cannot be tapped, and this
 * row is the touch surface.
 */
internal fun videoDetailHelperItems(state: VideoDetailUiState): List<ControllerPromptItem> = when {
    state.launchError != null -> listOf(ControllerPromptItem(GamepadAction.SELECT, "Dismiss"))
    state.confirmRemove -> listOf(
        ControllerPromptItem(GamepadAction.SELECT, "Remove"),
        ControllerPromptItem(GamepadAction.BACK, "Cancel"),
    )
    state.creatingPlaylist || state.isEditingTitle ->
        listOf(ControllerPromptItem(GamepadAction.BACK, "Cancel"))
    state.showPlaylistPicker || state.showOptions -> listOf(
        ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Navigate"),
        ControllerPromptItem(GamepadAction.SELECT, "Select"),
        ControllerPromptItem(GamepadAction.BACK, "Close"),
    )
    state.infoVisible -> listOf(ControllerPromptItem(GamepadAction.BACK, "Close"))
    else -> listOfNotNull(
        // The lead action is named for what it does to THIS film: a part-watched one resumes.
        state.primaryActions.firstOrNull()?.let { ControllerPromptItem(GamepadAction.SELECT, it.label) },
        ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options"),
        ControllerPromptItem(GamepadAction.BACK, "Back"),
    )
}
