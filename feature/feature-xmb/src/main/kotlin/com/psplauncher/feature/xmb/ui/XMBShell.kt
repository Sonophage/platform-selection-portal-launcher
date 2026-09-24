package com.psplauncher.feature.xmb.ui

import com.psplauncher.core.ui.notification.AndroidNotifications
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.psplauncher.core.ui.notification.SystemToast
import com.psplauncher.core.ui.notification.SystemToasts
import com.psplauncher.core.ui.notification.ToastKind
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import com.psplauncher.feature.xmb.viewmodel.countLabel
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.Arrangement
import com.psplauncher.core.ui.detail.PfpConfirmOverlay
import com.psplauncher.core.ui.detail.PfpDetailLaunchButton
import com.psplauncher.core.ui.detail.PfpMessageOverlay
import com.psplauncher.core.ui.detail.PfpOverlayCard
import com.psplauncher.core.ui.detail.PfpOverlayTitle
import com.psplauncher.core.ui.detail.PfpTextPromptOverlay
import com.psplauncher.core.ui.image.rememberArtworkModel
import com.psplauncher.core.domain.model.VideoSnapPlacement
import com.psplauncher.feature.xmb.ui.detail.DetailPanelPage
import com.psplauncher.feature.xmb.ui.detail.DetailPanelStrip
import com.psplauncher.feature.xmb.ui.detail.GameDetailPanel
import com.psplauncher.feature.xmb.ui.detail.detailPanelContentFor
import com.psplauncher.feature.xmb.ui.detail.resolvePanelPage
import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.ui.motion.MotionWallpaperPolicy
import com.psplauncher.core.ui.motion.rememberAppVisible
import androidx.compose.ui.text.style.TextOverflow
import com.psplauncher.core.ui.theme.LocalPfpTextColors
import androidx.compose.foundation.lazy.rememberLazyListState
import com.psplauncher.core.ui.components.DiscLaunchCeremony
import com.psplauncher.core.ui.components.ControllerHintEdgeGap
import com.psplauncher.core.ui.components.XmbTouchButton
import com.psplauncher.core.ui.preview.DevicePreviews
import com.psplauncher.core.ui.preview.PfpPreview
import com.psplauncher.core.ui.theme.DefaultPFPColors
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.core.ui.theme.withWaveTint
import com.psplauncher.core.ui.theme.PFPTheme
import com.psplauncher.feature.appbar.AppDrawerScreen
import com.psplauncher.feature.appbar.AppFilter
import com.psplauncher.feature.settings.ui.SettingsNavHost
import com.psplauncher.feature.xmb.preview.PreviewData
import com.psplauncher.feature.xmb.ui.app.AppDetailScreen
import com.psplauncher.feature.xmb.ui.detail.GameDetailScreen
import com.psplauncher.feature.xmb.ui.detail.VideoDetailScreen
import com.psplauncher.feature.xmb.ui.photo.PhotoViewerScreen
import com.psplauncher.feature.xmb.viewmodel.focusedPillIndex
import com.psplauncher.feature.xmb.viewmodel.focusedPills
import com.psplauncher.feature.xmb.viewmodel.pillRowVisible
import com.psplauncher.feature.xmb.viewmodel.promptsFor
import com.psplauncher.feature.xmb.viewmodel.railRows
import com.psplauncher.feature.xmb.viewmodel.RecentFilter
import com.psplauncher.feature.xmb.viewmodel.FAN_COVER_COUNT
import com.psplauncher.feature.xmb.viewmodel.formatDuration
import com.psplauncher.feature.xmb.viewmodel.XMBUiState
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel

// Uniform canvas-scale baseline = the handheld reference height in dp (AYN Thor landscape,
// 1080×1920 / 369dpi ⇒ 1080 / (369/160) ≈ 468dp). The scale resolves so the post-scale layout
// height in dp always equals this baseline (scaledHeightDp = realHeightDp / (realHeightDp/baseline)
// = baseline), i.e. every screen lays the XMB cross out in a Thor-sized vertical space and just
// magnifies to fill — so the item windowing (1 row above / 2 below) is IDENTICAL everywhere and no
// extra row clips in on a taller tablet. The cap only guards absurd configs; real tablets must NOT
// be clamped or their layout height would exceed the baseline and reveal a clipped extra row.
private const val XMB_BASELINE_HEIGHT_DP = 468f
// Baseline landscape WIDTH: the Thor is 1920x1080 => 832x468dp at its density (16:9). The canvas
// scale is bounded by BOTH axes (see uiScale), so a near-square / foldable panel is limited by its
// width instead of over-magnifying off the height ratio and overflowing horizontally.
private const val XMB_BASELINE_WIDTH_DP = 832f
private const val XMB_MAX_SCALE = 2.5f

// Left margin the memory-card cross is pinned to WHILE DRILLED IN, so the game flyout takes the
// centre-right of the screen. Small so the cross hugs the edge; the ◀ + game column ride along.
private val DRILL_CROSSBAR_LEFT_MARGIN = 16.dp

// Height of the caticon (category) bar band. A file constant rather than a local because the
// active row's line — barTop + this — is needed both by the cross itself and by the drill
// flyout's PIC0 logo, which centres on that row.
private val CAT_BAR_HEIGHT = 112.dp

/**
 * Stateful entry point for the XMB home screen: collects [XMBViewModel.uiState] and wires the
 * ViewModel's callbacks into the stateless [XMBShell]. This is what the host activity renders.
 */

@Composable
fun XMBShellContainer(
    viewModel: XMBViewModel = hiltViewModel(),
    onSettingsLongPress: () -> Unit = {},
) {
    // Lifecycle-aware collection: state observation stops while PFP is STOPPED (backgrounded behind
    // a game/emulator), so the shell isn't recomposing off-screen — less CPU/battery under load.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // "Save as Theme…" share hop: when the VM has a saved bundle waiting (icon editor flow),
    // fire ACTION_SEND through the FileProvider — the same contract ThemesSettingsViewModel's
    // share uses — then let the VM drop the one-shot.
    val shareContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(uiState.pendingThemeShareFile) {
        val file = uiState.pendingThemeShareFile ?: return@LaunchedEffect
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                shareContext,
                "${shareContext.packageName}.fileprovider",
                file,
            )
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            shareContext.startActivity(
                android.content.Intent.createChooser(send, "Share theme")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        viewModel.onThemeShareConsumed()
    }

    // Display ▸ Scale: scoped to the XMB ONLY. The factor is applied inside XMBShell's
    // canvas provider (cross, category bar, item list, status strip), and a matching
    // base-density reset provider restores the device density for every other screen —
    // Settings, detail screens, dialogs and overlays are never rescaled when the user
    // scales the XMB. Font scale rides along via density, keeping text and layout proportional.

    // The disc is composed OUTSIDE XMBShell, above everything it draws.
    //
    // It used to be the last child of the XMB canvas, which put it above the shell's own
    // overlays but still inside two things it has no business being inside: the canvas Box, and
    // the density provider that scales the XMB. A ceremony that covers the screen while another
    // app takes over should not be scaled by the crossbar's zoom, and "last child of the canvas"
    // is only "on top" for as long as nothing is ever composed beside the canvas.
    //
    // GameBoot stays where it is: it is games-only and the disc never runs for a game, so the
    // two can never be on screen together.
    Box(Modifier.fillMaxSize()) {
    XMBShell(
        uiState = uiState,
        onCategorySelected = viewModel::onCategoryTapped,
        onStepCategory = viewModel::stepCategory,
        onStepItem = viewModel::stepItem,
        onTouchBack = viewModel::onHomeBack,
        onTouchInput = viewModel::markTouchInput,
        onXmbSortTapped = viewModel::onSortLabelTapped,
        onPanelPageTapped = viewModel::onPanelPageTapped,
        onRecentFilterTapped = viewModel::setRecentFilter,
        onRecentRailToggled = viewModel::toggleRecentRail,
        onDrawerTypedCharConsumed = viewModel::onDrawerTypedCharConsumed,
        onNotificationsToggled = viewModel::toggleNotifications,
        onNotificationsDismissed = viewModel::closeNotifications,
        onNoticeTapped = viewModel::onNoticeTapped,
        onNoticeDismissTapped = viewModel::onNoticeDismissTapped,
        onNoticeMediaPrimary = viewModel::onNoticeMediaPrimary,
        onNoticeMediaPrev = viewModel::onNoticeMediaPrev,
        onNoticeMediaNext = viewModel::onNoticeMediaNext,
        onOpenAppDrawer = viewModel::onOpenAppDrawer,
        onItemTap = viewModel::onItemTap,
        onItemLongPress = viewModel::onItemLongPress,
        onPlatformLongPress = viewModel::onPlatformLongPress,
        onUserInteraction = viewModel::onUserInteraction,
        onBootComplete = viewModel::onBootSequenceComplete,
        onSettingsLongPress = onSettingsLongPress,
        // Up one level: a section's screen backs out to the root list, the root backs out of
        // Settings. onCloseSettingsScreen is still what actually leaves.
        onCloseSettingsScreen = viewModel::onSettingsBack,
        onOpenSettingsScreen = viewModel::onOpenSettingsScreen,
        onOpenXmbLayoutAdjust = viewModel::openXmbLayoutAdjust,
        onOpenCustomIcons = viewModel::openCustomIcons,
        onPreviewBootSequence = viewModel::previewBootSequence,
        onPreviewGameBoot = viewModel::previewGameBoot,
        onGameBootComplete = viewModel::onGameBootComplete,
        onGameBootHandOff = viewModel::onGameBootHandOff,
        onCloseCustomIcons = viewModel::closeCustomIcons,
        onCustomIconsActionConsumed = viewModel::onCustomIconsActionConsumed,
        onCustomIconsSlotFocused = viewModel::onCustomIconSlotFocused,
        onCustomIconGroupMove = viewModel::onCustomIconGroupMove,
        onCustomIconPicked = viewModel::onIconPicked,
        onCustomResetSlot = viewModel::onResetSlot,
        onCustomResetAll = viewModel::onResetAll,
        onSaveAsThemeRequested = viewModel::requestSaveCurrentLookAsTheme,
        onConfirmSaveAsTheme = viewModel::confirmSaveCurrentLookAsTheme,
        onDismissSaveAsTheme = viewModel::dismissSaveThemeNameDialog,
        onThemeShareConsumed = viewModel::onThemeShareConsumed,
        onSettingsActionConsumed = viewModel::consumeSettingsAction,
        onPromptTapped = viewModel::onPromptTapped,
        onPillActivated = viewModel::onPillActivated,
        focusedPillIndex = uiState.focusedPillIndex,
        onCloseAppDrawer = viewModel::onCloseAppDrawer,
        onAddAppToOpenCategory = viewModel::addAppToOpenCategory,
        onDrawerActionConsumed = viewModel::consumeDrawerAction,
        onCloseGameDetail = viewModel::onCloseGameDetail,
        onOpenLibraryManager = viewModel::openLibraryManager,
        onGoToLibrary = viewModel::goToLibrary,
        onGameDetailActionConsumed = viewModel::consumeGameDetailAction,
        onCloseVideoDetail = viewModel::onCloseVideoDetail,
        onVideoDetailActionConsumed = viewModel::consumeVideoDetailAction,
        onClosePhotoViewer = viewModel::onClosePhotoViewer,
        onPhotoViewerActionConsumed = viewModel::consumePhotoViewerAction,
        onCloseAppDetail = viewModel::onCloseAppDetail,
        onAppDetailActionConsumed = viewModel::consumeAppDetailAction,
        onContextMenuItemActivated = viewModel::onContextMenuItemActivatedAt,
        onContextMenuDismiss = viewModel::closeContextMenu,
        onOpenColorSchemePicker = viewModel::openColorSchemePicker,
        onColorSchemeHighlightedAt = viewModel::onColorSchemeHighlightedAt,
        onColorSchemeConfirm = viewModel::confirmColorSchemePicker,
        onColorSchemeCancel = viewModel::cancelColorSchemePicker,
        onCustomColorUpdate = viewModel::updateCustomColor,
        onCustomColorChannelMove = viewModel::moveCustomColorChannel,
        onCustomColorAdjust = viewModel::adjustCustomColor,
        onCustomColorConfirm = viewModel::confirmCustomColor,
        onCustomColorCancel = viewModel::cancelCustomColor,
        onXmbLayoutScale = viewModel::setXmbLayoutScale,
        onXmbLayoutHorizontal = viewModel::setXmbLayoutHorizontal,
        onXmbLayoutVertical = viewModel::setXmbLayoutVertical,
        onXmbLayoutToggleSliders = viewModel::toggleXmbLayoutSliders,
        onXmbLayoutReset = viewModel::resetXmbLayoutAdjust,
        onXmbLayoutSave = viewModel::saveXmbLayoutAdjust,
        onXmbLayoutCancel = viewModel::cancelXmbLayoutAdjust,
        onNamePromptTextChanged = viewModel::onNamePromptTextChanged,
        onConfirmAppRename = viewModel::onConfirmAppRename,
        onCancelAppRename = viewModel::onCancelAppRename,
        onConfirmCollectionName = viewModel::onConfirmCollectionName,
        onCancelCollectionName = viewModel::onCancelCollectionName,
        onConfirmPlaylistName = viewModel::onConfirmPlaylistName,
        onCancelPlaylistName = viewModel::onCancelPlaylistName,
        onMusicTrackPickerActivatedAt = viewModel::onMusicTrackPickerActivatedAt,
        onMusicTrackPickerConfirm = viewModel::onMusicTrackPickerConfirm,
        onMusicTrackPickerDismiss = viewModel::closeMusicTrackPicker,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onSearchActivatedAt = viewModel::onSearchActivatedAt,
        onSearchBack = viewModel::closeSearch,
        onOpenSearch = { viewModel.openSearch(com.psplauncher.feature.xmb.viewmodel.SearchScope.ALL) },
        onMusicBrowserQueryChange = viewModel::onMusicBrowserQueryChange,
        onMusicBrowserActivatedAt = viewModel::onMusicBrowserActivatedAt,
        onMusicBrowserLongPressAt = viewModel::onMusicBrowserLongPressAt,
        onMusicBrowserBack = viewModel::onMusicBrowserBack,
        onMusicBrowserSortTapped = viewModel::onMusicBrowserSortTapped,
        onMusicBrowserOptionsTapped = viewModel::onMusicBrowserOptionsTapped,
        onAppPickerTileTapped = viewModel::onAppPickerTileTapped,
        onAppPickerTouchBrowse = viewModel::onAppPickerTouchBrowse,
        onAppPickerHeaderBack = viewModel::onAppPickerHeaderBack,
        onAppPickerSearchToggle = viewModel::onAppPickerSearchToggle,
        onAppPickerQueryChange = viewModel::onAppPickerQueryChange,
        onAppPickerSearchDone = viewModel::onAppPickerSearchDone,
        onAppPickerApply = viewModel::onAppPickerApply,
        onAppPickerConfirmRemoval = viewModel::onAppPickerConfirmRemoval,
        onAppPickerCancelRemoval = viewModel::onAppPickerCancelRemoval,
        onAppPickerDismiss = viewModel::closeAppPicker,
        onGamePickerConfirm = viewModel::confirmGamePicker,
        onGamePickerDismiss = viewModel::closeGamePicker,
        onGamePickerActionConsumed = viewModel::consumeGamePickerAction,
        onDismissInfoDialog = viewModel::dismissInfoDialog,
        onWindowsSetupConfirm = viewModel::confirmWindowsSetupPrompt,
        onWindowsSetupDismiss = viewModel::dismissWindowsSetupPrompt,
        onLaunchRecoveryAction = viewModel::onLaunchRecoveryAction,
        onMusicPlayPause = viewModel::musicPlayPause,
        onMusicPrev = viewModel::musicPrev,
        onMusicNext = viewModel::musicNext,
        onMusicSeekTo = viewModel::musicSeekTo,
        onMusicPlayerBack = viewModel::closeMusicPlayer,
        onOpenAndroidLibraryPicker = viewModel::openAndroidLibraryPicker,
    )

    // The toast pill used to be hosted here. What a background task finished doing is the status
    // strip's left half now, and the rest of them are behind it — see XmbNotificationBar, which
    // lives down in the screen beside the strip it drops from.

    uiState.discCeremony?.let { ceremony ->
        DiscLaunchCeremony(
            art = ceremony.art,
            onHandOff = viewModel::onDiscCeremonyHandOff,
            onFinished = viewModel::onDiscCeremonyFinished,
            modifier = Modifier.fillMaxSize(),
        )
    }
    }

}

@OptIn(UnstableApi::class)
@Composable
fun XMBShell(
    uiState: XMBUiState,
    onCategorySelected: (Int) -> Unit = {},
    onStepCategory: (Int) -> Unit = {},
    onStepItem: (Int) -> Unit = {},
    onTouchBack: () -> Unit = {},
    onTouchInput: () -> Unit = {},
    onXmbSortTapped: () -> Unit = {},
    onPanelPageTapped: (DetailPanelPage) -> Unit = {},
    // The Recent shelf by finger: pick a filter by name, and show or hide the cover rail. Both
    // were D-pad only, which left touch on the shelf able to see one item and reach no others.
    onRecentFilterTapped: (RecentFilter) -> Unit = {},
    onRecentRailToggled: () -> Unit = {},
    // The notification sheet. Its open state lives in the ViewModel now, so Start opens it and
    // BACK closes it; these are the finger's way to the same handlers.
    onDrawerTypedCharConsumed: () -> Unit = {},
    onNotificationsToggled: () -> Unit = {},
    onNotificationsDismissed: () -> Unit = {},
    onNoticeTapped: (String) -> Unit = {},
    onNoticeDismissTapped: (String) -> Unit = {},
    onNoticeMediaPrimary: () -> Unit = {},
    onNoticeMediaPrev: () -> Unit = {},
    onNoticeMediaNext: () -> Unit = {},
    onOpenAppDrawer: () -> Unit = {},
    // Row tap: move the cursor there, or activate if it's already selected (see XMBViewModel.onItemTap).
    onItemTap: (Int) -> Unit = {},
    onItemLongPress: (Int) -> Unit = {},
    onPlatformLongPress: (Int) -> Unit = {},
    onUserInteraction: () -> Unit = {},
    onBootComplete: () -> Unit = {},
    onSettingsLongPress: () -> Unit = {},
    onCloseSettingsScreen: () -> Unit = {},
    onOpenSettingsScreen: (String) -> Unit = {},
    onOpenXmbLayoutAdjust: () -> Unit = {},
    onOpenCustomIcons: () -> Unit = {},
    onPreviewBootSequence: () -> Unit = {},
    onPreviewGameBoot: () -> Unit = {},
    onGameBootComplete: () -> Unit = {},
    onGameBootHandOff: () -> Unit = {},
    onCloseCustomIcons: () -> Unit = {},
    onCustomIconsActionConsumed: () -> Unit = {},
    onCustomIconsSlotFocused: (Int) -> Unit = {},
    onCustomIconGroupMove: (Int) -> Unit = {},
    onCustomIconPicked: (String, android.net.Uri) -> Unit = { _, _ -> },
    onCustomResetSlot: (String) -> Unit = {},
    onCustomResetAll: () -> Unit = {},
    onSaveAsThemeRequested: () -> Unit = {},
    onConfirmSaveAsTheme: (String) -> Unit = {},
    onDismissSaveAsTheme: () -> Unit = {},
    onThemeShareConsumed: () -> Unit = {},
    onSettingsActionConsumed: () -> Unit = {},
    /** Runs a tapped hint prompt, through the same dispatcher a pad press uses. */
    onPromptTapped: (com.psplauncher.core.domain.model.GamepadAction) -> Unit = {},
    /** Runs a tapped 9i action pill, by the id its row's context menu dispatches. */
    onPillActivated: (String) -> Unit = {},
    /** Which pill the controller cursor is on, or null while it is on the row itself. */
    focusedPillIndex: Int? = null,
    onCloseAppDrawer: () -> Unit = {},
    /** Y menu's "Add to Cross Bar": the drawer names the app, the XMB knows the column. */
    onAddAppToOpenCategory: (String) -> Unit = {},
    onDrawerActionConsumed: () -> Unit = {},
    onCloseGameDetail: () -> Unit = {},
    onOpenLibraryManager: () -> Unit = {},
    onGoToLibrary: () -> Unit = {},
    onGameDetailActionConsumed: () -> Unit = {},
    onCloseVideoDetail: () -> Unit = {},
    onVideoDetailActionConsumed: () -> Unit = {},
    onClosePhotoViewer: () -> Unit = {},
    onPhotoViewerActionConsumed: () -> Unit = {},
    onCloseAppDetail: () -> Unit = {},
    onAppDetailActionConsumed: () -> Unit = {},
    onContextMenuItemActivated: (Int) -> Unit = {},
    onContextMenuDismiss: () -> Unit = {},
    onMusicPlayPause: () -> Unit = {},
    onMusicPrev: () -> Unit = {},
    onMusicNext: () -> Unit = {},
    onMusicSeekTo: (Int) -> Unit = {},
    onMusicPlayerBack: () -> Unit = {},
    onOpenAndroidLibraryPicker: () -> Unit = {},
    onOpenColorSchemePicker: () -> Unit = {},
    onColorSchemeHighlightedAt: (Int) -> Unit = {},
    onColorSchemeConfirm: () -> Unit = {},
    onColorSchemeCancel: () -> Unit = {},
    onCustomColorUpdate: (Int, Float) -> Unit = { _, _ -> },
    onCustomColorChannelMove: (Int) -> Unit = {},
    onCustomColorAdjust: (Float) -> Unit = {},
    onCustomColorConfirm: () -> Unit = {},
    onCustomColorCancel: () -> Unit = {},
    onXmbLayoutScale: (Float) -> Unit = {},
    onXmbLayoutHorizontal: (Float) -> Unit = {},
    onXmbLayoutVertical: (Float) -> Unit = {},
    onXmbLayoutToggleSliders: () -> Unit = {},
    onXmbLayoutReset: () -> Unit = {},
    onXmbLayoutSave: () -> Unit = {},
    onXmbLayoutCancel: () -> Unit = {},
    onNamePromptTextChanged: (String) -> Unit = {},
    onConfirmAppRename: (String) -> Unit = {},
    onCancelAppRename: () -> Unit = {},
    onConfirmCollectionName: (String) -> Unit = {},
    onCancelCollectionName: () -> Unit = {},
    onConfirmPlaylistName: (String) -> Unit = {},
    onCancelPlaylistName: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchActivatedAt: (Int) -> Unit = {},
    onSearchBack: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onMusicBrowserQueryChange: (String) -> Unit = {},
    onMusicBrowserActivatedAt: (Int) -> Unit = {},
    onMusicBrowserLongPressAt: (Int) -> Unit = {},
    onMusicBrowserBack: () -> Unit = {},
    onMusicBrowserSortTapped: () -> Unit = {},
    onMusicBrowserOptionsTapped: () -> Unit = {},
    onMusicTrackPickerActivatedAt: (Int) -> Unit = {},
    onMusicTrackPickerConfirm: () -> Unit = {},
    onMusicTrackPickerDismiss: () -> Unit = {},
    onAppPickerTileTapped: (Int) -> Unit = {},
    onAppPickerTouchBrowse: (Int) -> Unit = {},
    onAppPickerHeaderBack: () -> Unit = {},
    onAppPickerSearchToggle: (Boolean) -> Unit = {},
    onAppPickerQueryChange: (String) -> Unit = {},
    onAppPickerSearchDone: () -> Unit = {},
    onAppPickerApply: () -> Unit = {},
    onAppPickerConfirmRemoval: () -> Unit = {},
    onAppPickerCancelRemoval: () -> Unit = {},
    onAppPickerDismiss: () -> Unit = {},
    onGamePickerConfirm: (Set<Long>, Set<Long>) -> Unit = { _, _ -> },
    onGamePickerDismiss: () -> Unit = {},
    onGamePickerActionConsumed: () -> Unit = {},
    onDismissInfoDialog: () -> Unit = {},
    onWindowsSetupConfirm: () -> Unit = {},
    onWindowsSetupDismiss: () -> Unit = {},
    onLaunchRecoveryAction: (com.psplauncher.feature.launcher.LaunchRecoveryAction) -> Unit = {},
) {
    // The XMB wears the focused game's colour: the wave, the gradient behind it, and the accent
    // on the cursor. The THEME is the default and the resting state -- land on a row that is not
    // a game, or a game whose art has no hue, and the screen goes back to the user's colours.
    //
    // Animated, because the cursor moves. A colour that jumped would strobe down a long list, so
    // the fade is deliberately slower than a cursor step: a fast scroll reads as one drift rather
    // than forty flashes, and a cursor that passes straight through a game never fully takes its
    // colour on before the next one starts pulling it away.
    val themeWave = uiState.themeColors.waveColor
    val themeAccent = uiState.themeColors.accentColor
    // One switch gates BOTH halves. Gating only the picture would leave the whole palette still
    // following the cursor, which is the half the XMB argues with most.
    val itemColor = uiState.focusedItemAccentArgb
        ?.takeIf { uiState.itemBackdropEnabled }
        ?.let { Color(it.toInt()) }
    val xmbWave by androidx.compose.animation.animateColorAsState(
        targetValue = itemColor ?: themeWave,
        animationSpec = tween(durationMillis = 420),
        label = "xmbItemWave",
    )
    val xmbGameAccent by androidx.compose.animation.animateColorAsState(
        targetValue = itemColor ?: themeAccent,
        animationSpec = tween(durationMillis = 420),
        label = "xmbItemAccent",
    )
    // withWaveTint re-derives the background anchors from the wave through the same cascade the
    // theme itself was built with, so a game's colour produces the gradient that colour WOULD
    // have had as a theme -- not a tint laid over the theme's gradient.
    val xmbColors = remember(uiState.themeColors, xmbWave, xmbGameAccent) {
        uiState.themeColors.withWaveTint(xmbWave).copy(accentColor = xmbGameAccent)
    }
    PFPTheme(colors = xmbColors) {
      // The applied theme's custom icon slots ride alongside the palette: every themeable
      // glyph (crossbar, item rows, status strip) checks this map before its built-in art.
      CompositionLocalProvider(
          com.psplauncher.core.ui.icons.LocalXmbIconOverrides provides uiState.iconOverrides,
          // The playing track's position, live. The Music column's row carries a fraction from
          // when it was BUILT, and that list is not rebuilt on playback ticks by design, so this
          // is what actually moves the bar. Derived here rather than in the row so the row stays
          // ignorant of what music is.
          LocalLiveRowProgress provides uiState.musicPlayback.let { pb ->
              val total = pb.durationMs
              if (pb.track != null && total > 0) {
                  LiveRowProgress(
                      itemId = XMBViewModel.NOW_PLAYING_ITEM_ID,
                      fraction = (pb.positionMs.toFloat() / total).coerceIn(0f, 1f),
                      label = formatDuration(pb.positionMs.toLong()) + "  /  " +
                          formatDuration(total.toLong()),
                  )
              } else null
          },
          // The user's per-slot picks ride the same rail — the tier ABOVE the theme's icons
          // (user pick > theme icon > built-in, at every render site).
          com.psplauncher.core.ui.icons.LocalCustomIcons provides uiState.customIcons,
          // Icon display mode + the focused game's approved ICON1 snap ride the same rail so
          // the deeply nested tile composables never need them plumbed through params.
          LocalIconDisplayMode provides uiState.iconDisplayMode,
          LocalIconDisplayModeByPlatform provides uiState.iconDisplayModeByPlatform,
          LocalFocusedGameVideo provides uiState.focusedGameVideo,
          // Whether the hover panel has claimed the snap. Provided here, next to the snap
          // itself, so a tile several layers down cannot read one without the other.
          LocalPanelShowingVideo provides (uiState.effectivePanelPage == DetailPanelPage.VIDEO),
          // The icon-legibility treatment: PortalIcon + the theme-override glyph branches read
          // it ambiently, so every XMB silhouette glyph gets the matte from one provider.
          com.psplauncher.core.ui.icons.LocalIconLegibility provides uiState.iconLegibility,
      ) {
        // XMB-ONLY canvas scale. On screens taller than the handheld baseline (tablets), the
        // XMB cross is magnified so the tuned layout fills the screen. The override scope ends
        // at the cross — see the base-density reset provider further down — so Settings,
        // detail screens, dialogs and every other overlay keep the device's own density:
        // scaling the XMB never rescales any other screen. Clamped so the handheld is
        // untouched (scale = 1) and huge screens don't balloon. Safe because no layout reads
        // LocalConfiguration — everything measures via BoxWithConstraints/LocalDensity, which
        // this override feeds.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val baseDensity = LocalDensity.current
            // Fit to the SMALLER of the two axis ratios so a near-square / foldable screen (e.g. the
            // Z Fold inner display, ~1:1) is bounded by width and doesn't balloon to the height-based
            // scale — which would over-magnify everything and truncate the item labels. On a 16:9-ish
            // handheld or tablet both ratios are equal, so this is identical to the height-only scale.
            val uiScale = minOf(
                maxHeight.value / XMB_BASELINE_HEIGHT_DP,
                maxWidth.value / XMB_BASELINE_WIDTH_DP,
            ).coerceIn(1f, XMB_MAX_SCALE)
            // User layout tuning for THIS form factor (see XmbLayoutAdjust). The open editor's draft
            // wins; otherwise the saved bucket entry; otherwise the legacy scale + theme bar line, so
            // a device the user never tuned renders exactly as before.
            val config = LocalConfiguration.current
            val layoutAdjust = uiState.xmbLayoutAdjust?.draft
                ?: uiState.xmbLayoutAdjustMap[
                    com.psplauncher.themekit.XmbFormFactor.forSmallestWidthDp(config.smallestScreenWidthDp).key
                ]
                ?: com.psplauncher.themekit.XmbLayoutAdjust(
                    scale = uiState.xmbScale,
                    barLeftFraction = 0f,
                    barTopFraction = uiState.layoutSpec.barTopFraction,
                )
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density * uiScale * layoutAdjust.scale, baseDensity.fontScale),
            ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Freeze the wave's per-frame animation whenever an opaque fullscreen layer fully covers
            // it (boot, a detail/player overlay, the app drawer, the music player). Those hide the
            // wave anyway, so animating it just burns GPU/battery — worst case competing with the
            // video player. Settings/dialogs use a see-through scrim, so the wave keeps animating there.
            val waveCovered = uiState.showBootSequence ||
                uiState.activeVideoId != null || uiState.activeGameId != null ||
                uiState.activePhotoViewer != null ||
                uiState.activeAppId != null || uiState.activeAppDrawerFilter != null ||
                uiState.musicPlayerVisible ||
                // The icon editor is translucent (like Settings), so the wave stays alive
                // behind it — listed here to document that; layout adjust reads the same.
                false
            // Freeze the wave when it's hidden anyway, or when the device is conserving power
            // (battery saver / thermal throttle, unless opted out). This is ONE motion budget
            // that BOTH background layers read: with a wallpaper set the wave branch isn't
            // composed at all (nothing allocates), and the motion wallpaper obeys the same
            // inputs — covered, throttled, app-visible — releasing its decoder outright rather
            // than pausing it.
            val powerThrottled = rememberWavePowerThrottle(
                respectBatterySaver  = uiState.respectBatterySaver,
                thermalThrottleAware = uiState.thermalThrottleAware,
            )
            // The icon-animation budget: focused-row GIFs play only when the wave isn't
            // throttled (battery saver / thermal — the same budget the background obeys) and
            // no blocking overlay covers the XMB (reuses hasBlockingOverlay rather than
            // inventing a second condition). One motion budget, three consumers.
            val iconAnimatingAllowed = !powerThrottled && !uiState.hasBlockingOverlay
            // The app-visible leg: the composition survives ON_STOP (every game launch), and a
            // decoder running behind the emulator is the worst possible outcome for the motion
            // wallpaper. Folded into the same motion budget the wave obeys.
            val appVisible = rememberAppVisible()
            val motionDecision = MotionWallpaperPolicy.decide(
                MotionWallpaperPolicy.Inputs(
                    hasMotion = uiState.motionWallpaperPath != null,
                    hasPoster = uiState.customWallpaperPath != null,
                    style = uiState.waveStyle,
                    covered = waveCovered,
                    throttled = powerThrottled,
                    appVisible = appVisible,
                )
            )
            // Wave keeps its existing freeze semantics exactly: covered/throttled freezes it,
            // and with a wallpaper set the wave branch is simply not composed (so the old
            // "wallpaper set → frozen wave" clause is no longer needed as such).
            val effectiveWaveStyle = if (waveCovered || powerThrottled) {
                uiState.waveStyle.frozen
            } else uiState.waveStyle
            // GameBoot must NOT read effectiveWaveStyle. `waveCovered` means "something opaque is
            // covering the wave, so don't burn a frame rate nothing can see" — and during a
            // launch the thing covering it IS GameBoot (activeGameId is set on every Game Detail
            // launch). Feeding that back in tells GameBoot not to animate because GameBoot is on
            // screen, which froze the sequence on every real launch while the settings preview —
            // reached from a screen that is not in `waveCovered` — animated normally.
            //
            // The honest input is the motion BUDGET: the user's chosen style, frozen only when
            // the device is conserving power. Same value feeds the launch and the preview, so the
            // two can never diverge again.
            val gameBootWaveStyle = if (powerThrottled) uiState.waveStyle.frozen else uiState.waveStyle
            XmbBackground(
                waveStyle           = effectiveWaveStyle,
                customWallpaperPath = uiState.customWallpaperPath,
                waveOverWallpaper   = uiState.waveOverWallpaper,
                wallpaperAccent     = uiState.wallpaperAccent,
                motionWallpaperPath = uiState.motionWallpaperPath,
                motionDecision      = motionDecision,
                // The wave comes back AFTER the artwork below, not here. A cover, a film's
                // thumbnail or a game's key art is the background on this screen, and the wave
                // belongs over it rather than buried under it.
                waveDrawnByCaller   = true,
                modifier            = Modifier.fillMaxSize(),
            )

            // Per-row background art (XMB hover). Any row with art of its own, in any
            // category: an album cover, a video thumbnail, a photo or a book jacket backs the
            // shell exactly the way a game's key art does.
            //
            // It read artworkUri alone -- the dedicated background slot, with heroUri reserved
            // for the Game Detail banner. That rule was right and the data was not: 125 of the
            // 147 games here name an internal artwork path that no longer exists, so the slot
            // resolved to nothing and the wallpaper showed instead. The ViewModel now hands over
            // the first candidate that actually DECODED, which is the same image its colour came
            // from, so the backdrop and the tint over it can never be of two different pictures.
            // The hover panel, computed here rather than beside the code that draws it: the
            // full-bleed snap layer below has to know whether the panel has claimed the clip,
            // and it is composed before the foreground. Pure reads of uiState, no remember, so
            // the position is free.
            //
            // The crossbar's right-hand region, which used to draw only the PIC0 logo. It still draws exactly that by default; L1/R1 now walk it to the game's
            // box art or its information card. One component draws this region, shared with the
            // drill-down page, so the two cannot describe the same game differently.
            //
            // Gated on a real game WITH backdrop art: the region has always needed something
            // behind it, and a panel floating on the bare wallpaper reads as a stray card.
            val recentsListState = rememberLazyListState()
            val panelItem = uiState.hoverPanelItem
            // uiState.hoverPanelContent, not a build of it here: the shoulder walk reads the
            // same property, and the strip's tabs and where R1 lands have to be the same list.
            val panelContent = uiState.hoverPanelContent
            // The content's own logo field, which is already gated on hasVisibleLogo — the same
            // predicate XMBItemList reads to decide whether the row keeps its title. Reading the
            // item again here would be a second answer to one question.
            val panelLogo = panelContent?.logoUri
            val panelPage = panelContent?.let { resolvePanelPage(uiState.effectivePanelPage, it.pages) }
            val panelShowingVideo = panelPage == DetailPanelPage.VIDEO

            val selectedItem = uiState.currentItems.getOrNull(uiState.selectedItemIndex)
            val selectedBg = uiState.focusedItemBackdrop?.takeIf { uiState.itemBackdropEnabled }
            // PS3 placement: the approved snap plays full-bleed here instead of in the tile,
            // over the still art and UNDER the legibility scrim, so the crossbar keeps the same
            // contrast it has over a still background. Same FocusedGameVideo, same gates, same
            // single player — Icon1VideoOverlay centre-crops to whatever bounds it is given.
            // shellSnapSite, not a placement test: the shortcut of reading the placement alone
            // stopped being safe the moment a third site could claim the clip. With the panel on
            // its video page this returns PANEL and the full-bleed layer draws nothing.
            val backgroundSnap = uiState.focusedGameVideo?.takeIf {
                shellSnapSite(it.placement, panelShowingVideo) == SnapSite.BACKGROUND &&
                    it.gameId == selectedItem?.gameId
            }
            // 180ms, down from 320. The backdrop is the largest thing on screen and it changes on
            // every step of the cursor, so a long fade is the one animation that is always running
            // — and the wave now draws OVER it, which makes a slow swap underneath read as the
            // picture lagging behind the row that named it.
            Crossfade(targetState = selectedBg, animationSpec = tween(180), label = "xmbGameBackground") { bg ->
                if (bg != null || backgroundSnap != null) {
                    Box(Modifier.fillMaxSize()) {
                        // The clip goes UNDER the still, not over it. With a snap playing, the
                        // still is masked to the left of the screen and fades out across the
                        // middle (see XMBGameBackdrop), so the crossbar keeps solid artwork
                        // behind it and the open right-hand side carries the motion.
                        if (backgroundSnap != null) {
                            Icon1VideoOverlay(
                                videoUri = backgroundSnap.uri,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (bg != null) AsyncImage(
                            model = rememberArtworkModel(bg),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                // Full-bleed with no clip to reveal: masking it then would fade
                                // the artwork into the bare wallpaper for no reason.
                                .then(if (backgroundSnap != null) Modifier.xmbStillOverVideo() else Modifier),
                        )
                        // Legibility scrim over the artwork. Deliberately light-handed: heavier
                        // alphas dim the art too much, so darker photos lose their vibrancy — the
                        // icons/labels carry their own contrast (tiles, glows, text shadows).
                        //
                        // Tinted toward the focused game's own colour rather than a neutral
                        // near-black. Same alphas, so nothing gets darker; the difference is that
                        // the darkness now belongs to the artwork it is sitting on instead of
                        // reading as a grey sheet laid over it.
                        val scrimBase = androidx.compose.ui.graphics.lerp(
                            Color(0xFF05050C), xmbGameAccent, 0.22f,
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(
                                    0.0f to scrimBase.copy(alpha = 0.65f),
                                    0.5f to scrimBase.copy(alpha = 0.50f),
                                    1.0f to scrimBase.copy(alpha = 0.75f),
                                )
                            )
                        )
                        // A second, flat scrim for video only. The gradient above was tuned
                        // against a STILL image, where the eye settles and the text shadows do
                        // the rest. A snap does not settle: every frame changes the luminance
                        // under every label, and a bright one (pixel art, a white menu) takes
                        // the crossbar with it. Video pays for its own legibility rather than
                        // dimming every still background to cover the worst frame of a clip.
                        if (backgroundSnap != null) {
                            Box(Modifier.fillMaxSize().background(Color(0x5905050C)))
                        }
                    }
                }
            }

            // THE WAVE, ON TOP OF THE ARTWORK. The background is dynamic — whatever the cursor is
            // on backs the screen, and the chosen wallpaper is what it falls back to — so the wave
            // is the one constant, and it reads as the launcher's own surface only if it sits above
            // the picture rather than under it.
            //
            // Tinted by that picture's accent, the same way the wave over a wallpaper already is:
            // white strands over a photograph look like a layer from a different screen. The accent
            // comes from the image that actually DECODED, so the wave and the backdrop can never be
            // coloured from two different pictures.
            //
            // Drawn ONCE: XmbBackground was told to hold its own wave back. The artwork fades out
            // across the middle of the screen, so a second wave underneath would be visible right
            // there, at a different alpha.
            // THE WAVE REACTS. Slower when nothing has been pressed for a while, quicker while a
            // game is being launched, ordinary otherwise.
            //
            // Animated rather than switched: the surface is a continuous thing and a speed that
            // jumped would be a visible seam across it. 900ms, which is longer than anything else
            // on this screen on purpose — every other animation here is a response to a press and
            // has to keep up with one, and this is the opposite, a room changing its mind.
            //
            // The launch pulse is worth the frames it costs only because it is visible: the disc
            // ceremony fades in over 1.7s, so the wave is still on screen underneath it while it
            // quickens.
            val launching = uiState.discCeremony != null || uiState.activeGameBoot != null
            val waveSpeed by animateFloatAsState(
                targetValue = when {
                    launching -> 2.1f
                    uiState.idle -> 0.45f
                    else -> 1f
                },
                animationSpec = tween(900),
                label = "xmbWaveSpeed",
            )
            // AND IT BRIGHTENS. Speed alone is only legible if you are watching the strands; a
            // glow reads from the corner of the eye, which is where the screen is while you press
            // the button to leave it.
            //
            // It swells fast and falls away slowly — 260ms up against 1200 down. A launch is a
            // thing that HAPPENS and then is over, and a symmetrical fade would read as the
            // launcher pulsing on a timer rather than answering the press.
            val waveGlow by animateFloatAsState(
                targetValue = if (launching) 1.7f else 1f,
                animationSpec = tween(if (launching) 260 else 1200),
                label = "xmbWaveGlow",
            )
            WaveOverlay(
                waveStyle = effectiveWaveStyle,
                accentArgb = uiState.focusedItemAccentArgb ?: uiState.wallpaperAccent,
                modifier = Modifier.fillMaxSize(),
                speedScale = waveSpeed,
                glowScale = waveGlow,
            )

            // Hide the XMB foreground (status strip + category bar + item list) while a fullscreen
            // The status strip and the hint bar draw ABOVE the context rail while it is open, so
            // the clock, the battery and the button hints survive a menu opening. Declared out
            // here because the strip is inside the guard below and the hint bar is not.
            //
            // zIndex rather than moving those two after the rail in the Box: the rail sits inside
            // the base-density provider further down, and a composable moved across that boundary
            // is a composable drawn at a different size. And only WHILE the rail is open — a
            // permanent elevation would put the clock on top of the App Drawer and Settings,
            // which are meant to cover it.
            // The strip and the hint bar sit above the XMB's own content, not just in front of it
            // visually — the strip is drawn BEFORE the item list, so the list was taking the taps
            // aimed at the live-activity corner and the notification sheet would not open.
            //
            // It drops back to 0 under a real overlay: the App Drawer and Settings are meant to
            // cover the clock and say so. The rail and the notification sheet are the exceptions,
            // which is what overlayKeepsChrome is for.
            val aboveContextRail = when {
                uiState.activeContextMenu != null || uiState.notificationsOpen -> 1f
                uiState.hasBlockingOverlay -> 0f
                else -> XmbChromeZ
            }

            // The newest notification takes the strip's live slot for a few seconds, then hands it
            // back to whatever was there. Same dwell the pill used to have, and the same reasoning:
            // it is a courtesy, not a thing you have to dismiss. What it said stays in
            // SystemToasts.recent, which is what the bar below shows.
            var flash by remember { mutableStateOf<SystemToast?>(null) }
            LaunchedEffect(Unit) {
                SystemToasts.events.collect { toast ->
                    flash = toast
                    delay(if (toast.kind == ToastKind.ERROR) 5_200L else 3_200L)
                    if (flash?.id == toast.id) flash = null
                }
            }
            val notifications by SystemToasts.recent.collectAsState()
            // The device's notifications come through the state now, not a second collection
            // here: the input dispatcher acts on that list, and a cursor that walks one list
            // while the presses land on another is the pill row's bug in a different room.
            val androidNotices = uiState.androidNotices
            val notificationsOpen = uiState.notificationsOpen
            // Read from the secure setting, not kept as a flag: it is changed in Android's own
            // Settings, outside this process, so it is re-read whenever the sheet is opened.
            val strip = LocalContext.current
            val androidAccess = remember(notificationsOpen) { AndroidNotifications.isEnabled(strip) }


            // menu is open — only the wallpaper/wave background shows behind it. Restored
            // automatically when the menu closes. Besides the visual, this REMOVES the XMB's
            // clickable rows from composition, so a tap on the overlay's empty space can never fall
            // through and activate an XMB item behind it. Covers the app drawer, music browser,
            // Settings, and the fullscreen detail screens (Game / Video / App / Photo) — those now
            // use a translucent backdrop, so the XMB would otherwise show through them.
            if (uiState.activeAppDrawerFilter == null &&
                uiState.musicBrowser == null &&
                uiState.search == null &&
                uiState.activeSettingsScreen == null &&
                uiState.activeGameId == null &&
                uiState.activeVideoId == null &&
                uiState.activeAppId == null &&
                uiState.activePhotoViewer == null &&
                // The icon editor is translucent — the live XMB (with the custom look
                // applying behind it) IS the point, so the foreground stays composed.
                uiState.customIconSession == null
            ) {

            // ── Home ──────────────────────────────────────────────────────────
            // Last Played REPLACES the crossbar rather than sitting beside it: standing on the
            // leftmost column hides the caticon bar and the item list, and the screen becomes
            // the game you were last playing. RIGHT walks the recents and then steps to the next
            // category, which is what brings the bar back.
            // NOT crossfaded any more, and the transition did not lose anything by it.
            //
            // A Crossfade composes BOTH branches for its whole duration, and the else below is
            // the entire crossbar — bar, column, hover panel, pill row. Paying for two of those
            // trees every time the cursor steps on or off the shelf is what the owner could feel.
            //
            // What made it look like a move was never this fade. The BACKGROUND is drawn above
            // this, outside it, and already crossfades on its own over 320ms whenever the focused
            // item's backdrop changes — which stepping on or off the shelf always does. That is
            // the largest thing on screen and it is still fading; only the foreground, which is
            // mostly text and small tiles, now swaps on one frame.
            val onLastPlayedHome = uiState.onLastPlayedHome
            if (onLastPlayedHome) {
                LastPlayedPage(
                    items = uiState.currentItems,
                    selectedIndex = uiState.selectedItemIndex,
                    content = uiState.hoverPanelContent,
                    page = uiState.effectivePanelPage,
                    listState = recentsListState,
                    directLaunch = uiState.directLaunch,
                    filter = uiState.recentFilter,
                    railVisible = uiState.recentRailVisible,
                    onPageTapped = onPanelPageTapped,
                    onCardTapped = onItemTap,
                    onArtTapped = onRecentRailToggled,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = StripHeight)
                        // The same gesture layer the crossbar has, which the shelf never got.
                        // A horizontal swipe steps the category, and stepping off the shelf is
                        // what brings the bar back — so the one screen that HIDES the crossbar
                        // was the one screen with no touch way to reach it. Vertical walks the
                        // recents, matching UP and DOWN. Taps still fall through to the cards,
                        // the filter names and the spine.
                        .xmbNavGestures(
                            onStepCategory = onStepCategory,
                            onStepItem = onStepItem,
                            onEdgeBack = onTouchBack,
                            stepScale = uiState.touchSensitivity.stepScale,
                        ),
                )
            } else {

            // The PSP's icon → PIC1 → PIC0 stagger, kept: the logo arrives a beat after the
            // background and snaps away the instant the cursor moves, so the next game's logo is
            // never glimpsed before its own linger completes.
            //
            // The linger is the LOGO page's alone. A shoulder press is an answer to the user and
            // must land at once; waiting 650 ms to redraw a page they just asked for would read
            // as the button having missed.
            var pic0Visible by remember(panelLogo) { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(panelLogo) {
                if (panelLogo != null) {
                    kotlinx.coroutines.delay(650)
                    pic0Visible = true
                }
            }
            val pic0Alpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (pic0Visible && panelLogo != null) 1f else 0f,
                animationSpec = if (pic0Visible) tween(500) else androidx.compose.animation.core.snap(),
                label = "pic0Fade",
            )
            val onLogoPage = panelPage == DetailPanelPage.LOGO
            // HAS THE USER OPENED THE STRIP ON THIS GAME YET?
            //
            // panelPageGameId is stamped by every step and every tap on the strip, and
            // effectivePanelPage falls back to LOGO the moment the cursor is on a different game.
            // So "these two match" already means "the shoulders have been used on the game under
            // the cursor right now" — the resting state and the walked-to logo page were simply
            // indistinguishable before, because both report LOGO.
            //
            // They are distinguishable now because they have to be. At REST the crossbar shows the
            // row's own title and meta line and draws no panel at all; the logo is a page you
            // reach, not the thing that covers the name the moment you land on a game. The row
            // text used to survive about 650ms before the logo faded in over it, which made the
            // system-and-last-played line added for the redesign almost impossible to read.
            //
            // The home shelf is NOT affected: LastPlayedPage composes its own GameDetailPanel and
            // never comes through here. Recents keeps the logo it has always led with.
            val stripOpened = uiState.panelStripOpen

            // The focused row's subtitle is the game's scraped facts in EVERY state — "it should
            // always be the meta line. the one that starts with the year". It briefly swapped with
            // the strip's position, which meant the line under a game's name changed identity
            // depending on which page you happened to be on; one line, one meaning.
            //
            // Still gated on the Game Metadata setting, which is what turns those facts off
            // wholesale. With it off the row falls back to the system-and-last-played line.
            val metadataAsSubtitle = uiState.gameMetadataVisible
            // "Is anything on the right already naming this game?"
            //
            // Off the logo page the panel is 42% of the width and the label runs straight into
            // it, so the label goes. ON the logo page it goes only once a logo is actually
            // DRAWN — pic0Alpha, not merely "this game has one" — because the logo arrives a
            // beat after the background and a game that was nameless for those 650 ms is the bug
            // hasVisibleLogo's comment describes having already been fixed once.
            //
            // A logo-less game therefore keeps its label on the logo page, which is the whole
            // point: nothing else is naming it. Seen on the device as SKYRIM's wordmark with the
            // row's title printed across it.
            //
            // One val, two consumers (the crossbar list and the drill flyout). They were the pair
            // that disagreed — the flyout never received this at all — so they read one value.
            // ANY open page takes the row's label, the logo page included. At rest the row keeps
            // its name and its line; that is the only state in which it has them.
            val rowLabelHidden = panelContent != null && stripOpened
            val panelAlpha = if (onLogoPage) pic0Alpha else 1f
            // On the logo page this is the old condition unchanged, so a game with no logo shows
            // nothing here exactly as before. Off it, the panel is what the user asked for with
            // the shoulders and appears whether the game has a logo or not.
            if (panelContent != null && panelPage != null && stripOpened &&
                (!onLogoPage || (panelLogo != null && pic0Alpha > 0f))
            ) {
                // BoxWithConstraints, not Box: the vertical placement below is derived from the
                // screen height, and it MUST be measured here rather than reusing the shell's outer
                // maxHeight — that one is taken before the LocalDensity override above, so its dp
                // values mean a different number of pixels inside this subtree.
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    // At the XMB root the logo sits on the screen's centre line, as the PSP does.
                    // Inside the Games drill flyout it belongs to the ACTIVE GAME, so it centres on
                    // that card's row instead — otherwise the logo drifts away from the game it
                    // names whenever the user's crossbar position is anything but mid-screen.
                    //
                    // The flyout pins the active card at anchorTop (= barTop + the caticon bar)
                    // inside the content Box, which is inset by contentTopPadding. This rebuilds
                    // that same line here in the unpadded space, from the very constants the cross
                    // and the game column lay out with, so the two cannot drift apart.
                    // The logo page keeps the region it has always had. The other pages need
                    // more of it: 30% of the width is right for a wordmark and cramped for a
                    // portrait box or a paragraph, and the still art is solid out to 40% and
                    // gone by 68% (XMBGameBackdrop), so widening to 40% stays in the open side.
                    val panelWidthFraction = if (onLogoPage) 0.30f else 0.42f
                    // 70%, up from 62%: the strip used to take the top of this region and now
                    // sits in the chrome under the status bar, so the page gets what it was
                    // spending on its own tab row.
                    val panelHeightFraction = if (onLogoPage) 0.38f else 0.70f
                    val logoCenterOffset: Dp = if (uiState.drillTitle != null) {
                        val contentTop = uiState.layoutSpec.contentTopPaddingDp.dp
                        val crossHeight = maxHeight - contentTop
                        val anchorTop = crossHeight * layoutAdjust.barTopFraction + CAT_BAR_HEIGHT
                        val rowCenter = contentTop + anchorTop + ROW_HEIGHT / 2
                        // Keep the panel's CENTRE far enough from each edge that the panel itself
                        // stays on screen — half its own height, derived rather than the literal
                        // 19% that was correct only while the height was always 38%. A low
                        // crossbar must not push it off the bottom.
                        val halfPanel = panelHeightFraction / 2f
                        rowCenter.coerceIn(maxHeight * halfPanel, maxHeight * (1f - halfPanel)) -
                            maxHeight / 2
                    } else {
                        0.dp
                    }
                    GameDetailPanel(
                        content = panelContent,
                        page = panelPage,
                        // The strip is chrome, and on the logo page the crossbar should look
                        // exactly as it did before this change — so it appears only once the user
                        // has walked off the logo, which is the only way to get here.
                        // The row already shows the title for a logo-less game. See LogoPage.
                        titleFallback = false,
                        modifier = Modifier
                            .fillMaxWidth(panelWidthFraction)
                            .fillMaxHeight(panelHeightFraction)
                            .offset(y = logoCenterOffset)
                            .padding(end = 44.dp)
                            .alpha(panelAlpha),
                    )
                }
            }

            // The focused game's scraped one-liner used to be drawn HERE — right-aligned under
            // the logo, with an accent bar over it, on the logo page only.
            //
            // It is the row's subtitle now: "the metadata line is what I want as the subtitle
            // under the name of the game and removed from where time played would show up". That
            // band is where TIME PLAYED lives, and with both in it they printed through each
            // other — "2005 · CompilationTIME PLAYED: UNDER A MINUTElayers" on the device. One of
            // them had to leave, and the facts read better under the name they belong to than
            // right-aligned under a wordmark.

            // The fan of newest covers, for a Games-root card. See XmbCoverFan: it takes the
            // right-hand corner unconditionally because it and the hover panel can never both
            // apply — the panel wants a focused real GAME and this wants a card.
            val fanCovers = uiState.currentItems.getOrNull(uiState.selectedItemIndex)
                ?.insideCovers
                .orEmpty()
            if (fanCovers.isNotEmpty()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    XmbCoverFan(
                        covers = fanCovers.take(FAN_COVER_COUNT),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(
                                x = -maxWidth * XmbCoverFanPlacement.RightInsetFraction,
                                y = maxHeight * XmbCoverFanPlacement.TopFraction,
                            )
                            .size(
                                width = maxWidth * XmbCoverFanPlacement.WidthFraction,
                                height = maxHeight * XmbCoverFanPlacement.HeightFraction,
                            ),
                    )
                }
            }

            // The panel's page strip, directly under the status bar and in the opposite corner
            // from the helper footer, which is the pill it is wearing. Not on the logo page: that
            // view is the crossbar exactly as it was, and a tab row over it would be new chrome
            // on a screen nobody asked to change.
            if (panelContent != null && panelPage != null && panelPage != DetailPanelPage.LOGO) {
                DetailPanelStrip(
                    pages = panelContent.pages,
                    current = panelPage,
                    onPageTapped = onPanelPageTapped,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        // StripHeight, not a copy of 28: the gap under the status bar has to
                        // follow it if it ever changes.
                        .padding(top = StripHeight + ControllerHintEdgeGap, end = ControllerHintEdgeGap),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Slimmer than the status strip so the dissolving previous item can rise clear
                    // of the caticon hexagon before it's clipped (barTopFraction is balanced against
                    // this to keep the crossbar on the same screen line). From the theme layout spec.
                    .padding(top = uiState.layoutSpec.contentTopPaddingDp.dp)
                    // Touch gestures on the home screen, each mapped to a discrete D-pad action (see
                    // xmbNavGestures): horizontal swipe steps the category (left-edge → Back, and
                    // once drilled in, leftward → back out); vertical swipe steps the item
                    // list/flyout. Taps still pass through to the rows.
                    .xmbNavGestures(
                        onStepCategory = onStepCategory,
                        onStepItem = onStepItem,
                        onEdgeBack = onTouchBack,
                        stepScale = uiState.touchSensitivity.stepScale,
                        // Drilled in, the horizontal axis has nothing else to do — category
                        // stepping is locked — so a leftward swipe backs out one level, the same
                        // drill-out onTouchBack performs from the left edge.
                        swipeBackEnabled = uiState.isInSubItem,
                        onSwipeBack = onTouchBack,
                    ),
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // The XMB cross: the crossbar sits toward the vertical centre so first-level items
                    // appear BOTH above it (scrolled-past, dissolving) and below it. The bar is drawn
                    // ON TOP of a full-height item column; the active item is anchored just under the
                    // caticon (anchorTop = barTop + bar height) and previous items rise up through the
                    // bar band to dissolve. The column's leading icon is shifted right so it lands on
                    // the same vertical line as the caticon (centred in its slot).
                    val catBarHeight = CAT_BAR_HEIGHT
                    // Crossbar vertical position from the theme's layout spec — DEFAULT holds the
                    // pixel-tuned authentic-PSP geometry (caticon row ~25% of height); imported
                    // themes whose wallpaper draws its own cross band may override it.
                    val layoutSpec = uiState.layoutSpec
                    // Vertical crossbar position and horizontal shift come from the resolved layout
                    // adjustment (which itself defaults to the theme's bar line when untuned).
                    val barTop = maxHeight * layoutAdjust.barTopFraction
                    // Caticon centre minus the leading-icon inset ⇒ column shift that lands every
                    // item's icon centre on the caticon's vertical line (shared so the column offset
                    // and the row's scale pivot stay in lock-step).
                    val columnBaseInset = XmbLeftAnchor + (CategorySlotWidth / 2) - LEADING_ICON_CENTER
                    // Horizontal shift of the whole cross. Normally the user's per-form-factor layout
                    // offset; while drilled in, override it to PIN the cross to the left edge so the
                    // game flyout (cards + title + PIC0 logo) owns the centre-right of the screen —
                    // most useful on wide/foldable panels. The caticon bar and the flyout share this
                    // shift, so the ◀ stays tight to its game column; the cross just hugs the edge.
                    val hShift = if (uiState.drillTitle != null) {
                        DRILL_CROSSBAR_LEFT_MARGIN - columnBaseInset
                    } else {
                        maxWidth * layoutAdjust.barLeftFraction
                    }
                    // Active first-level item anchors just below the caticon bar, landing the selected
                    // item ~50% of height — matching the real PSP XMB (verified against the theme).
                    val anchorTop = barTop + catBarHeight
                    val startPad = columnBaseInset + hShift

                    if (uiState.drillTitle != null) {
                        // Drilled into a Games sub-item: two-pane flyout. LEFT = the platform MEMORY
                        // CARDS (items) as the main-XMB cross, icon-only, ◀ after the active card.
                        // RIGHT = the GAME CARDS (rom icons), icon-only, centre-pinned on that ◀ line.
                        // The caticon bar keeps its drilled-in "hidden right".
                        XmbDrillFlyout(
                            onPillActivated = onPillActivated,
                            focusedPillIndex = focusedPillIndex,
                            siblings = uiState.drillSiblings,
                            siblingIndex = uiState.drillSiblingIndex,
                            items = uiState.currentItems,
                            selectedIndex = uiState.selectedItemIndex,
                            onItemSelected = onItemTap,
                            onItemLongPress = onItemLongPress,
                            // Tapping the active memory card under the caticon backs out of the
                            // drill; taps on the other (dimmed) cards are ignored.
                            onSiblingTap = { i -> if (i == uiState.drillSiblingIndex) onTouchBack() },
                            labelHiddenByPanel = rowLabelHidden,
                            cardArtGrid = uiState.cardArtGrid,
                            metadataAsSubtitle = metadataAsSubtitle,
                            iconStyle = uiState.iconStyle,
                            barTopY = barTop,
                            belowTopY = anchorTop,
                            iconAnimatingAllowed = iconAnimatingAllowed,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxSize()
                                .padding(start = startPad, end = 24.dp),
                        )
                    } else {
                        AnimatedContent(
                            targetState = uiState.selectedCategoryIndex,
                            transitionSpec = {
                                // Under 200ms, all of it. The column slides in from below and
                                // the outgoing one leaves upward — the same shapes as before,
                                // quicker: 260 and 220 were long enough that stepping across the
                                // bar felt like waiting for each column rather than sweeping.
                                (fadeIn(tween(130)) + slideInVertically(tween(180)) { it / 8 })
                                    .togetherWith(fadeOut(tween(110)) + slideOutVertically(tween(140)) { -it / 10 })
                                    .using(SizeTransform(clip = false))
                            },
                            label = "xmbCategoryItems",
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxSize()
                                .padding(start = startPad, end = 24.dp),
                        ) { categoryIndex ->
                            // During a category transition AnimatedContent briefly composes BOTH the
                            // outgoing and incoming lists. Only the settled (current) category may
                            // render the selection highlight — otherwise the outgoing copy shows a
                            // duplicate enlarged row that slides away (a "second cursor").
                            val itemSelectedIndex =
                                if (categoryIndex == uiState.selectedCategoryIndex) uiState.selectedItemIndex else -1
                            XMBItemList(
                                onPillActivated = onPillActivated,
                                focusedPillIndex = focusedPillIndex,
                                items = uiState.currentItems,
                                selectedIndex = itemSelectedIndex,
                                onItemSelected = onItemTap,
                                onItemLongPress = onItemLongPress,
                                iconStyle = uiState.iconStyle,
                                barTopY = barTop,
                                belowTopY = anchorTop,
                                previousRiseRows = layoutSpec.previousItemRiseRows,
                                fadeByDistance = uiState.fadeByDistance,
                                textShadow = uiState.textShadow,
                                iconAnimatingAllowed = iconAnimatingAllowed,
                                labelHiddenByPanel = rowLabelHidden,
                                cardArtGrid = uiState.cardArtGrid,
                                metadataAsSubtitle = metadataAsSubtitle,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    // Category bar drawn ON TOP, pushed down to the crossbar line — the fixed pivot
                    // the first-level column appears to scroll beneath. The horizontal shift rides a
                    // composition local so the caticon bar tracks the item column as one cross.
                    // Last Played has no caticon. It is not a column — it REPLACES the whole
                    // screen — so a slot for it on the bar was an icon you could never see
                    // selected: stepping onto it takes the bar away with everything else.
                    // Stepping LEFT off Emulation still reaches it; the page is its own icon.
                    //
                    // Hidden from the BAR, not removed from the model: the category still exists,
                    // still holds the cursor, and the indices below map back to it, because the
                    // selection is the real list's and only the drawing is the short one.
                    // ...EXCEPT on touch, where it is the only way back.
                    //
                    // Hiding it fixed a controller problem: stepping right off the shelf passed
                    // through a slot you could never see selected. A finger has no equivalent of
                    // "step left off Emulation", so for touch the hidden slot is not tidier, it is
                    // a page with no door. The caticon comes back the moment the last input was a
                    // finger, and goes again on the next button press.
                    //
                    // Shelves is hidden by a different rule and for a different reason: it is
                    // hidden while EMPTY, on touch as much as on a pad, because an empty shelf
                    // column is not tidier or untidier — there is simply nothing in it. That rule
                    // is categoryReachable, and left/right reads the same one, so the bar cannot
                    // draw a column the pad refuses to step onto.
                    val barCategories = remember(
                        uiState.categories, uiState.lastInputWasTouch, uiState.shelfCards,
                    ) {
                        uiState.categories.filter { category ->
                            uiState.categoryReachable(category) &&
                                (uiState.lastInputWasTouch || category.id != BuiltInCategory.RECENTLY_PLAYED)
                        }
                    }
                    val barSelected = remember(barCategories, uiState.selectedCategoryIndex) {
                        uiState.categories.getOrNull(uiState.selectedCategoryIndex)
                            ?.let { current -> barCategories.indexOfFirst { it.id == current.id } }
                            ?.takeIf { it >= 0 }
                            ?: 0
                    }
                    CompositionLocalProvider(LocalXmbHorizontalShift provides hShift) {
                        XMBCategoryBar(
                            categories = barCategories,
                            selectedIndex = barSelected,
                            // The bar hands back ITS index; the cursor lives in the real list.
                            onCategorySelected = { barIndex ->
                                barCategories.getOrNull(barIndex)?.let { picked ->
                                    val real = uiState.categories.indexOfFirst { it.id == picked.id }
                                    if (real >= 0) onCategorySelected(real)
                                }
                            },
                            onCategoryLongPress = { index ->
                                val id = barCategories.getOrNull(index)?.id
                                if (id == BuiltInCategory.SETTINGS) onSettingsLongPress()
                            },
                            drilledIn = uiState.drillTitle != null,
                            fadeByDistance = uiState.fadeByDistance,
                            iconAnimatingAllowed = iconAnimatingAllowed,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(y = barTop)
                                .fillMaxWidth()
                                .height(catBarHeight),
                        )
                    }
                }
            }
            } // end: else — the crossbar, shown on every column but Last Played

            // The clock, the date and the battery, on EVERY column including Last Played.
            //
            // This used to live inside the else above, so the one screen that replaces the
            // crossbar was also the one screen with no status bar — while LastPlayedPage went on
            // padding itself down by StripHeight to make room for it, leaving an empty band where
            // the time should be. Out here both branches draw it, and it is still inside the
            // overlay guard, so the drawer and Settings cover it as before.
            // What the strip's left half shows. Music is the only source there is: the app's other
            // background work — scans, scrapes, imports, exports — reports through notifications
            // and publishes no progress the UI can read. The slot simply stays empty until one
            // does, which is also what the design's third card shows.
            val musicActivity = uiState.musicPlayback.track?.takeIf { uiState.musicPlayback.isPlaying }?.let { track ->
                StripLiveActivity(
                    art = track.artUri,
                    title = track.title ?: track.displayName,
                    detail = listOfNotNull(
                        track.artist,
                        formatDuration(uiState.musicPlayback.positionMs.toLong()) + " / " +
                            formatDuration(uiState.musicPlayback.durationMs.toLong()),
                    ).joinToString("  ·  "),
                )
            }

            // The sheet's top row. Music while there is music — PLAYING or PAUSED, unlike the
            // strip's live slot above, which is about what is happening right now; a paused track
            // is exactly what you open a transport to deal with. Otherwise the last game, as a
            // way back into it.
            val sheetMedia = uiState.musicPlayback.track?.let { track ->
                NoticeMedia(
                    title = track.title ?: track.displayName,
                    detail = track.artist,
                    artUri = track.artUri,
                    progress = uiState.musicPlayback.durationMs
                        .takeIf { it > 0 }
                        ?.let { uiState.musicPlayback.positionMs.toFloat() / it },
                    elapsed = formatDuration(uiState.musicPlayback.positionMs.toLong()) + "  /  " +
                        formatDuration(uiState.musicPlayback.durationMs.toLong()),
                    isPlaying = uiState.musicPlayback.isPlaying,
                    hasTransport = true,
                    primaryLabel = if (uiState.musicPlayback.isPlaying) "\u23f8" else "\u25b6",
                )
            } ?: uiState.resumeGame?.let { game ->
                NoticeMedia(
                    // displayTitle, not title. `title` is the row as the scan wrote it — the ROM's
                    // filename with its illegal characters sanitised — so this row alone called
                    // the game "The Elder Scrolls V_ Skyrim Special Edition" while every other
                    // surface said it with the colon. Game.displayTitle is the one rule:
                    // userTitleOverride, then the scraped name, then the filename.
                    title = game.displayTitle,
                    detail = "Continue",
                    artUri = game.artworkUri ?: game.iconUri,
                    // No position to report. A bar sitting at zero would be a claim that you are
                    // at the start of something, which is not what "last played" knows.
                    progress = null,
                    elapsed = null,
                    isPlaying = false,
                    hasTransport = false,
                    primaryLabel = "Resume",
                )
            }

            // In order: the report that just landed, then whatever is playing, then a count of
            // what is waiting behind the corner. The count covers BOTH sections of the sheet,
            // because it is a count of what that press opens — one number for one place.
            //
            // The last case is also what keeps the corner PRESSABLE with nothing playing. Without
            // it the notifications are there and unreachable.
            val liveActivity = flash?.let { StripLiveActivity(art = null, title = it.title, detail = it.message) }
                ?: musicActivity
                ?: (notifications.size + androidNotices.size)
                    .takeIf { it > 0 }
                    ?.let { StripLiveActivity(art = null, title = countLabel(it, "notification"), detail = null) }

            XmbPspStatusStrip(
                sortLabel = uiState.sortLabel,
                showSortButton = uiState.resolvedShowTouchButton,
                onSortTapped = onXmbSortTapped,
                live = liveActivity,
                onLiveAreaTapped = onNotificationsToggled,
                // The two navigation hints, each shown only where the press does something.
                // Shoulder: the hover panel's pages, which exist only on a game that has them.
                // Left/right: stepping the crossbar, which a drilled-in list does not do.
                hints = StripHints(
                    shoulder = uiState.panelStripOpen,
                    // NOT on the crossbar. Stepping left and right between categories is the
                    // first thing anyone does on this screen and does not need announcing —
                    // "the dpad hint isn't needed on the main screen". It is shown where the
                    // press does the less obvious thing: walking into a row's pill actions,
                    // which only rows that HAVE pills offer, and never on the home shelf where
                    // left and right are reserved for leaving it.
                    leftRight = uiState.pillRowVisible,
                ),
                // The home shelf's media filter rides in the middle of the bar. Only there: it
                // is the only column X filters, and a row of media names over the crossbar would
                // be naming something that column does not have.
                centre = if (uiState.onLastPlayedHome) {
                    {
                        RecentFilterRow(
                            filter = uiState.recentFilter,
                            modifier = Modifier.align(Alignment.Center),
                            onFilterTapped = onRecentFilterTapped,
                            includeApps = uiState.recentsIncludeApps,
                        )
                    }
                } else null,
                modifier = Modifier.align(Alignment.TopCenter).zIndex(aboveContextRail),
            )

            // The notifications, pulled down from the strip they are posted into. Above the XMB
            // foreground and below everything after it, which is where the strip itself sits.
            XmbNotificationBar(
                open = notificationsOpen,
                items = notifications,
                android = androidNotices,
                androidAccessGranted = androidAccess,
                onGrantAndroidAccess = {
                    onNotificationsDismissed()
                    runCatching {
                        strip.startActivity(
                            AndroidNotifications.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                media = sheetMedia,
                focus = uiState.focusedNotice,
                onNoticeTapped = onNoticeTapped,
                onNoticeDismissTapped = onNoticeDismissTapped,
                onMediaPrimary = onNoticeMediaPrimary,
                onMediaPrev = onNoticeMediaPrev,
                onMediaNext = onNoticeMediaNext,
                onDismiss = onNotificationsDismissed,
                onClear = { SystemToasts.clear(); onNotificationsDismissed() },
                modifier = Modifier.zIndex(NotificationBarZ),
            )

            // No launch control on the shelf itself any more. It was a spine down the right
            // edge, then briefly a rail capsule in the same place; it is a row in the context
            // rail now, where every other thing you can do to an item already lives.
            } // end: XMB foreground hidden while music browser is open

            // Button hint pill: [ X Sort   Y Options ], with the controller-style glyphs, and
            // TAPPABLE — a tap runs the action through the same dispatcher the pad uses. Up by
            // default rather than after an idle pause (Display ▸ Button Hints, and its delay,
            // still govern both). Driven by uiState.showContextMenuHint; each half appears only
            // where that action really does something, so the pill shrinks to just Options on an
            // unsortable list and to just Sort on an item with no context menu.
            //
            // It shows while drilled in too (the game flyout, a library's files) — those rows have
            // context menus and sort, and are where the affordance is least discoverable.
            //
            // It is also the only thing in this corner now. Search and the App Drawer used to be
            // two large square buttons beneath it, which pushed the pill up 68dp so the two would
            // not overlap -- two rows of controls in one corner, saying the same kind of thing at
            // two different sizes. They are prompts in this row now, on the same terms as Filter
            // and Options: same size, same look, named by the button that does them.
            //
            // NOT gated on touch mode, unlike the buttons they replace. A footer that grows two
            // prompts when you put the controller down is the opposite of uniform, and on a pad
            // these two are real bindings a user should be told about: Search is Select at the
            // root and Apps is Back at the root. The buttons could be touch-only because they
            // were touch-only affordances; a named prompt is for both hands.
            val rootActionsVisible =
                (!uiState.hasBlockingOverlay || uiState.overlayKeepsChrome) && !uiState.isInSubItem
            AnimatedVisibility(
                // Shown when EITHER half has something to say: the root actions are a touch
                // affordance with their own visibility rule, and hiding them behind the hint's
                // rule would have taken Search and Apps off screen with the hint.
                // The rail is the one blocking overlay this survives: "the header and hints still
                // show on top of the context screen". Everything else still takes it away.
                //
                // NOTE the prompts it shows are still the LIST's — Sort, Options, Search, Apps —
                // while the rail wants Select and Close. That is item 12g's job, which redoes this
                // bar to follow whatever is open; until then the bar is visible and its words are
                // about the screen behind the menu.
                visible = (uiState.showContextMenuHint || rootActionsVisible) &&
                    (!uiState.hasBlockingOverlay || uiState.overlayKeepsChrome),
                enter = fadeIn(tween(200)),
                exit = ExitTransition.None,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(aboveContextRail),
            ) {
                // Full width and flush to the bottom edge: it IS the page's footer now, not a
                // pill lying on the page, so it takes no inset of its own.
                XmbHintBar(
                    prompts = promptsFor(uiState),
                    onAction = onPromptTapped,
                )
            }

            // Everything from here down is a separate screen or overlay (Settings, app
            // drawer, music, pickers, dialogs, detail screens) — not part of the XMB cross.
            // Reset to the device's base density so the XMB-only canvas scale above stops at
            // the cross: scaling the XMB never rescales any of these.
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, baseDensity.fontScale),
            ) {

            // The Settings screen is suppressed while the color-scheme picker is open so
            // the live wave preview shows through behind the picker (PSP-style).
            if (uiState.colorSchemePicker == null) {
                // Hidden while the player status view is open on top of it (opened from the
                // Settings player card); closing that view brings Settings straight back.
                uiState.activeSettingsScreen?.let { screenId ->
                    SettingsNavHost(
                        screenId = screenId,
                        onBack = onCloseSettingsScreen,
                        pendingGamepadAction = uiState.pendingSettingsAction,
                        onGamepadActionConsumed = onSettingsActionConsumed,
                        onPromptTapped = onPromptTapped,
                        showControllerHint = uiState.showSettingsHint,
                        leftBacksOut = uiState.leftBacksOut,
                        lastInputWasTouch = uiState.lastInputWasTouch,
                        onTouchInteraction = onTouchInput,
                        onOpenColorSchemePicker = onOpenColorSchemePicker,
                        onOpenXmbLayoutAdjust = onOpenXmbLayoutAdjust,
                        onOpenCustomIcons = onOpenCustomIcons,
                        onPreviewBootSequence = onPreviewBootSequence,
                        onPreviewGameBoot = onPreviewGameBoot,
                        onAddAndroidApps = onOpenAndroidLibraryPicker,
                        onOpenLibraryManager = onOpenLibraryManager,
                        onGoToLibrary = onGoToLibrary,
                        onOpenScreen = onOpenSettingsScreen,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Boot sequence draws ABOVE the settings layer: on a fresh install the setup wizard
            // is already composed beneath it, so the boot dissolve reveals the wizard — the XMB
            // is never on screen first. The animation holds on a black frame until BOTH the
            // notification-permission dialog is resolved AND the first-run check has decided
            // (wizard opened or not), so that guarantee is by construction, not by timing.
            // Startup order: permission dialog (black hold) -> boot animation -> wizard or XMB.
            if (uiState.showBootSequence) {
                if (uiState.startupPermissionsSettled && uiState.initialSetupDecided) {
                    BootSequenceOverlay(
                        onComplete = onBootComplete,
                        bootVideoPath = uiState.bootVideoPath,
                        bootAudioPath = uiState.bootAudioPath,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                }
            }

            uiState.activeAppDrawerFilter?.let { filterName ->
                val initialFilter = runCatching { AppFilter.valueOf(filterName) }
                    .getOrDefault(AppFilter.DEFAULT)
                AppDrawerScreen(
                    initialFilter = initialFilter,
                    onBack = onCloseAppDrawer,
                    pendingGamepadAction = uiState.pendingDrawerAction,
                    typedChar = uiState.pendingDrawerTypedChar,
                    onTypedCharConsumed = onDrawerTypedCharConsumed,
                    onGamepadActionConsumed = onDrawerActionConsumed,
                    // The drawer renders its own hint pill (same system as the XMB's
                    // ContextMenuHint — see shouldShowAppDrawerHint), and its prompts are
                    // tappable through the same dispatcher a pad press uses.
                    showControllerHint = uiState.showAppDrawerHint,
                    onPromptTapped = onPromptTapped,
                    // Drawer touches are reported to the shared input-source tracker, which is
                    // what drives the contextual touch-navigation button.
                    onTouchInteraction = onTouchInput,
                    onAddToCrossBar = onAddAppToOpenCategory,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Library search — above the music browser, because a track opened from a search
            // raises the player and this must not be sitting on top of it afterwards.
            uiState.search?.let { search ->
                SearchScreen(
                    state = search,
                    onQueryChange = onSearchQueryChange,
                    onActivateAt = onSearchActivatedAt,
                    onBack = onSearchBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Fullscreen searchable music browser (Music / Playlist) — rendered before the player
            // and context menu so a track's options menu and the player draw on top of it.
            uiState.musicBrowser?.let { browser ->
                MusicBrowserScreen(
                    state = browser,
                    onQueryChange = onMusicBrowserQueryChange,
                    onActivateAt = onMusicBrowserActivatedAt,
                    onLongPressAt = onMusicBrowserLongPressAt,
                    onBack = onMusicBrowserBack,
                    onSortTapped = onMusicBrowserSortTapped,
                    onOptionsTapped = onMusicBrowserOptionsTapped,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // In-app music player — rendered before the context menu so the Y options menu
            // (Play in Background) draws on top of it.
            if (uiState.musicPlayerVisible) {
                MusicPlayerScreen(
                    state = uiState.musicPlayback,
                    onPlayPause = onMusicPlayPause,
                    onPrev = onMusicPrev,
                    onNext = onMusicNext,
                    onSeekTo = onMusicSeekTo,
                    onBack = onMusicPlayerBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            uiState.activeContextMenu?.let { menu ->
                // railRows, not menu.items: the rail drops what the pill row already carries and
                // caps the rest, and the ViewModel indexes the SAME list, so the cursor and the
                // drawing cannot disagree about which action is row three.
                ContextMenuOverlay(
                    rows = uiState.railRows(),
                    selectedIndex = menu.selectedIndex,
                    onItemActivated = onContextMenuItemActivated,
                    onDismiss = onContextMenuDismiss,
                )
            }

            uiState.colorSchemePicker?.let { picker ->
                ColorSchemePickerOverlay(
                    state = picker,
                    onHighlightedAt = onColorSchemeHighlightedAt,
                    onConfirm = onColorSchemeConfirm,
                    onDismiss = onColorSchemeCancel,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            uiState.customColorPicker?.let { picker ->
                CustomColorPickerOverlay(
                    state = picker,
                    onChannelFraction = onCustomColorUpdate,
                    onConfirm = onCustomColorConfirm,
                    onCancel = onCustomColorCancel,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            uiState.xmbLayoutAdjust?.let { session ->
                XmbLayoutAdjustOverlay(
                    draft = session.draft,
                    slidersVisible = session.slidersVisible,
                    onScale = onXmbLayoutScale,
                    onHorizontal = onXmbLayoutHorizontal,
                    onVertical = onXmbLayoutVertical,
                    onToggleSliders = onXmbLayoutToggleSliders,
                    onReset = onXmbLayoutReset,
                    onSave = onXmbLayoutSave,
                    onCancel = onXmbLayoutCancel,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Live "Customize XMB Icons" editor — rendered beside the layout editor, over the
            // real XMB. The foreground stays composed (see the guard above) so the columns and
            // the crossbar keep reflecting each pick as it lands.
            uiState.customIconSession?.let { session ->
                CustomIconsOverlay(
                    session = session,
                    customIcons = uiState.customIcons,
                    themeIcons = uiState.iconOverrides,
                    onSlotFocused = onCustomIconsSlotFocused,
                    onIconPicked = onCustomIconPicked,
                    onResetSlot = onCustomResetSlot,
                    onResetAll = onCustomResetAll,
                    onSaveAsTheme = onSaveAsThemeRequested,
                    onGroupMove = onCustomIconGroupMove,
                    onSlotMove = onCustomIconsSlotFocused, // touch fallback routes through the VM cursor
                    onDone = onCloseCustomIcons,
                    forwardedAction = uiState.pendingCustomIconsAction,
                    onActionConsumed = onCustomIconsActionConsumed,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Save-as-theme name dialog — reuses the shell's rename-dialog pattern.
            uiState.saveThemeNameDialog?.let { dialog ->
                CollectionNameDialog(
                    title = dialog.title,
                    text = dialog.text,
                    onTextChange = onNamePromptTextChanged,
                    onConfirm = onConfirmSaveAsTheme,
                    onCancel = onDismissSaveAsTheme,
                )
            }

            uiState.renameAppTarget?.let {
                AppRenameDialog(
                    text = uiState.renameAppText,
                    onTextChange = onNamePromptTextChanged,
                    onConfirm = onConfirmAppRename,
                    onCancel = onCancelAppRename,
                )
            }

            uiState.collectionNameDialog?.let { dialog ->
                CollectionNameDialog(
                    title = dialog.title,
                    text = dialog.text,
                    onTextChange = onNamePromptTextChanged,
                    onConfirm = onConfirmCollectionName,
                    onCancel = onCancelCollectionName,
                    placeholder = dialog.placeholder,
                    confirmLabel = dialog.confirmLabel,
                )
            }

            uiState.playlistNameDialog?.let { dialog ->
                CollectionNameDialog(
                    title = dialog.title,
                    text = dialog.text,
                    onTextChange = onNamePromptTextChanged,
                    onConfirm = onConfirmPlaylistName,
                    onCancel = onCancelPlaylistName,
                )
            }

            uiState.infoDialog?.let { dialog ->
                InfoDialog(
                    title = dialog.title,
                    message = dialog.message,
                    onDismiss = onDismissInfoDialog,
                )
            }

            // One-time follow-up to the pin workflow: a PC game was saved before the Windows
            // Library had a directory; offer to finish setup now (A) or later (B).
            //
            // That comment was a lie for as long as this was an AlertDialog: the ViewModel's A/B
            // branch for showWindowsSetupPrompt was correct and unreachable, because a dialog's
            // own platform Window means dispatchKeyEvent never runs. In-window, it is true again.
            //
            // confirmFill = null: "Set Up" offers to finish a job, it does not destroy anything,
            // so it must not wear the destructive red. Set Up is marked focused because A does
            // it; there is no cursor to move here, only the two fixed buttons.
            if (uiState.showWindowsSetupPrompt) {
                PfpConfirmOverlay(
                    title = "Finish your Windows Library",
                    message = "A PC game was added, but the Windows Games library has no folder " +
                        "yet. Set it up in Library Manager so game folders can be scanned.",
                    confirmLabel = "Set Up",
                    cancelLabel = "Later",
                    confirmFocused = true,
                    cancelFocused = false,
                    confirmFill = null,
                    onConfirm = onWindowsSetupConfirm,
                    onCancel = onWindowsSetupDismiss,
                )
            }

            // Launch recovery sheet (B1): raised by the shared LaunchDispatcher when a game-path
            // launch failed or the emulator never reached the foreground. Offers a retry, a
            // different emulator, the per-system defaults screen, and a copyable diagnostic —
            // a repair surface instead of a dead end.
            uiState.launchRecovery?.let { recovery ->
                LaunchRecoverySheet(
                    recovery = recovery,
                    cursor = uiState.launchRecoveryCursor,
                    onAction = onLaunchRecoveryAction,
                )
            }

            uiState.musicTrackPicker?.let { picker ->
                MusicTrackPicker(
                    state = picker,
                    onActivateAt = onMusicTrackPickerActivatedAt,
                    onConfirm = onMusicTrackPickerConfirm,
                    onDismiss = onMusicTrackPickerDismiss,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            uiState.appPicker?.let { picker ->
                com.psplauncher.feature.xmb.ui.apppicker.AppPickerScreen(
                    state = picker,
                    onTileTapped = onAppPickerTileTapped,
                    onTouchBrowse = onAppPickerTouchBrowse,
                    onHeaderBack = onAppPickerHeaderBack,
                    onSearchToggle = onAppPickerSearchToggle,
                    onSearchChange = onAppPickerQueryChange,
                    onSearchDone = onAppPickerSearchDone,
                    onApply = onAppPickerApply,
                    onConfirmRemoval = onAppPickerConfirmRemoval,
                    onCancelRemoval = onAppPickerCancelRemoval,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            uiState.gamePickerCategoryId?.let {
                GamePickerScreen(
                    onConfirm = onGamePickerConfirm,
                    onCancel = onGamePickerDismiss,
                    pendingGamepadAction = uiState.pendingGamePickerAction,
                    onGamepadActionConsumed = onGamePickerActionConsumed,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            uiState.activeGameId?.let { gameId ->
                GameDetailScreen(
                    gameId = gameId,
                    onBack = onCloseGameDetail,
                    autoLaunch = uiState.activeGameAutoLaunch,
                    initialAction = uiState.activeGameAction,
                    initialDiscId = uiState.activeGameDiscId,
                    pendingGamepadAction = uiState.pendingGameDetailAction,
                    onGamepadActionConsumed = onGameDetailActionConsumed,
                    showTouchControls = uiState.resolvedShowTouchButton,
                    onTouchInput = onTouchInput,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            uiState.activeAppId?.let { appId ->
                AppDetailScreen(
                    gameId = appId,
                    collectionCategoryId = uiState.activeAppCollectionCategoryId,
                    onBack = onCloseAppDetail,
                    pendingGamepadAction = uiState.pendingAppDetailAction,
                    onGamepadActionConsumed = onAppDetailActionConsumed,
                    showTouchControls = uiState.resolvedShowTouchButton,
                    onTouchInput = onTouchInput,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            uiState.activeVideoId?.let { videoId ->
                VideoDetailScreen(
                    videoId = videoId,
                    onBack = onCloseVideoDetail,
                    autoPlay = uiState.activeVideoAutoPlay,
                    pendingGamepadAction = uiState.pendingVideoDetailAction,
                    onGamepadActionConsumed = onVideoDetailActionConsumed,
                    onTouchInput = onTouchInput,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            uiState.activePhotoViewer?.let { request ->
                PhotoViewerScreen(
                    photoId = request.photoId,
                    libraryId = request.libraryId,
                    openWallpaperPreview = request.openWallpaperPreview,
                    onBack = onClosePhotoViewer,
                    pendingGamepadAction = uiState.pendingPhotoViewerAction,
                    onGamepadActionConsumed = onPhotoViewerActionConsumed,
                    onTouchInput = onTouchInput,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // GameBoot draws ABOVE every screen and overlay — the Game Detail page it launches
            // from, the settings screen it previews from, the app drawer, everything: while it is
            // on screen the launch is suspended on the gate (or, for a settings preview, nothing is
            // launching at all). It must be the last child of this Box; any screen composed after
            // it (the old position sat below GameDetailScreen) covers the sequence. Leaving
            // composition releases its players before the emulator gets the screen.
            uiState.activeGameBoot?.let { request ->
                if (request.videoPath == null) {
                    // The built-in GameBoot presentation IS the launch disc now — one ceremony for
                    // every kind of media, rather than a title card for games and a disc for
                    // everything else. GameBoot's switch still decides whether games get one at
                    // all, and a user who supplied their own clip still gets their clip below.
                    //
                    // Unlike the book and music paths, the launch here is suspended on
                    // GameBootGate, so the hand-off RELEASES the gate instead of starting anything
                    // itself; the emulator then loads under the spin.
                    DiscLaunchCeremony(
                        art = request.coverArt,
                        onHandOff = onGameBootHandOff,
                        onFinished = onGameBootComplete,
                        modifier = Modifier.fillMaxSize(),
                    )
                    return@let
                }
                GameBootOverlay(
                    gameTitle = request.gameTitle,
                    onComplete = onGameBootComplete,
                    videoPath = request.videoPath,
                    audioPath = request.audioPath,
                    // The motion budget, NOT effectiveWaveStyle — see gameBootWaveStyle above.
                    // A frozen or reduced style draws one still frame instead of the sweeps
                    // (GameBoot runs on every launch, unlike the once-per-start boot sequence),
                    // still at full length, so the launch waits for the whole presentation.
                    waveStyle = gameBootWaveStyle,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            } // end: base-density reset — non-XMB screens render unscaled
        } // end: XMB canvas Box
            } // end: CompositionLocalProvider (XMB-only canvas scale)
        } // end: BoxWithConstraints (uniform canvas scale)
      } // end: CompositionLocalProvider (LocalXmbIconOverrides)
    }

}

// ── Name prompts ──────────────────────────────────────────────────────────────
//
// Both of these were AlertDialogs, and both were controller-deaf for the reason measured on the
// tablet: an AlertDialog renders into its own platform Window, so the Activity's dispatchKeyEvent
// never runs and the gamepad pipeline never sees a press. On the "New Collection" prompt A, B and
// the D-pad all did nothing and only touch could escape.
//
// PfpTextPromptOverlay draws in the launcher's own window instead, which is the whole fix: the
// BACK branches in XMBViewModel (onCancelAppRename, onCancelCollectionName, onCancelPlaylistName)
// were always correct and were simply unreachable. They needed no change.

// Both wrappers are stateless: the text lives in XMBUiState (XMBViewModel.onNamePromptTextChanged)
// because the gamepad path needs to read it. A press of A arrives at the ViewModel, not here, so a
// half-typed name kept in a local remember would be invisible to the button that confirms it.

@Composable
private fun AppRenameDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    PfpTextPromptOverlay(
        title = "Rename Shortcut",
        value = text,
        placeholder = "Shortcut name",
        onValueChange = onTextChange,
        onConfirm = { onConfirm(text) },
        onCancel = onCancel,
    )
}

@Composable
private fun CollectionNameDialog(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    placeholder: String = "e.g. RPGs, Currently Playing",
    confirmLabel: String = "Save",
) {
    PfpTextPromptOverlay(
        title = title,
        value = text,
        placeholder = placeholder,
        onValueChange = onTextChange,
        onConfirm = { onConfirm(text) },
        onCancel = onCancel,
        confirmLabel = confirmLabel,
    )
}

@Composable
private fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    // In-window: the ViewModel has always closed this on A or B, and could never hear either
    // while it was an AlertDialog with its own platform Window.
    PfpMessageOverlay(title = title, message = message, onDismiss = onDismiss)
}

@Composable
private fun LaunchRecoverySheet(
    recovery: com.psplauncher.feature.launcher.LaunchRecoveryRequest,
    cursor: Int,
    onAction: (com.psplauncher.feature.launcher.LaunchRecoveryAction) -> Unit,
) {
    // This is the surface a failed launch drops you on, which is exactly the moment a controller
    // has to work -- and it was the one place in the app where it mostly could not. A had Retry, B
    // had Dismiss, and the other three buttons were touch targets on a device whose whole premise
    // is a pad. The order was hard-coded too, so the lead action was Retry no matter what had
    // happened: on a revoked storage grant that is the one thing the message directly above it has
    // just finished saying will fail again.
    //
    // The buttons now come from launchRecoveryActions, which puts the remedy for THIS failure
    // first, and the cursor walks them.
    val actions = com.psplauncher.feature.launcher.launchRecoveryActions(recovery)
    // Resolved, not a literal: this body text used to be a hardcoded 0xCCFFFFFF, which is how the
    // card ended up with a theme-resolved dark title over permanently white body copy.
    val bodyColor = com.psplauncher.core.ui.theme.LocalPfpTextColors.current.secondary
    PfpOverlayCard(onScrimTap = { onAction(com.psplauncher.feature.launcher.LaunchRecoveryAction.DISMISS) }) {
        PfpOverlayTitle("Couldn't launch ${recovery.gameTitle}")
        Spacer(Modifier.height(10.dp))
        Text(recovery.message, color = bodyColor, fontSize = 14.sp)
        recovery.historyLine?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = bodyColor.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEachIndexed { index, (action, label) ->
                PfpDetailLaunchButton(
                    label = label,
                    icon = null,
                    focused = index == cursor.coerceIn(0, actions.lastIndex),
                    onClick = { onAction(action) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@DevicePreviews
@Composable
private fun PreviewXMBDefault() {
    PfpPreview {
        XMBShell(uiState = PreviewData.defaultState)
    }
}

@OptIn(UnstableApi::class)
@DevicePreviews
@Composable
private fun PreviewXMBEmpty() {
    PfpPreview {
        XMBShell(uiState = PreviewData.emptyLibraryState)
    }
}

@OptIn(UnstableApi::class)
@DevicePreviews
@Composable
private fun PreviewXMBBoot() {
    PfpPreview {
        XMBShell(uiState = PreviewData.bootState)
    }
}

@OptIn(UnstableApi::class)
@Preview(name = "XMB - Red Theme", widthDp = 960, heightDp = 540)
@Composable
private fun PreviewXMBRedTheme() {
    val redColors = DefaultPFPColors.copy(
        backgroundTop = Color(0xFF8B0000),
        backgroundBottom = Color(0xFFB22222),
        waveColor = Color(0xFFFF4500)
    )
    PfpPreview(colors = redColors) {
        XMBShell(uiState = PreviewData.defaultState)
    }
}

/**
 * Where the notification bar sits in the shell's stack.
 *
 * Above the XMB foreground so it covers the crossbar it drops over, and below 1f so the status
 * strip — the thing you pressed to open it — still draws on top while the rail is up.
 */
private const val NotificationBarZ = 0.5f

/**
 * Where the status strip and the hint bar sit over the XMB's own content.
 *
 * Above the notification sheet, because the strip is what you pressed to open it and pressing it
 * again is how it closes.
 */
private const val XmbChromeZ = 0.6f

/** How long the shelf and the crossbar cross over. Short: it covers a step, not an entrance. */
