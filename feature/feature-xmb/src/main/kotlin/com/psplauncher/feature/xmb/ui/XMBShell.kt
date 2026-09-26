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
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.psplauncher.feature.xmb.ui.detail.DetailPanelPage
import com.psplauncher.feature.xmb.ui.detail.DetailPanelStrip
import com.psplauncher.feature.xmb.ui.detail.GameDetailPanel
import com.psplauncher.feature.xmb.ui.detail.resolvePanelPage
import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.ui.motion.MotionWallpaperPolicy
import com.psplauncher.core.ui.motion.rememberAppVisible
import com.psplauncher.core.ui.theme.LocalPfpTextColors
import androidx.compose.foundation.lazy.rememberLazyListState
import com.psplauncher.core.ui.components.LocalControllerConnected
import com.psplauncher.core.ui.components.NoMenuSelection
import com.psplauncher.core.ui.components.PspContextMenuOverlay
import com.psplauncher.core.ui.components.PspMenuRow
import com.psplauncher.core.ui.components.HintBarHeight
import com.psplauncher.core.ui.components.StatusStripHeight
import com.psplauncher.core.ui.components.DiscLaunchCeremony
import com.psplauncher.core.ui.components.ControllerHintEdgeGap
import com.psplauncher.core.ui.preview.DevicePreviews
import com.psplauncher.core.ui.preview.PfpPreview
import com.psplauncher.core.ui.theme.DefaultPFPColors
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
import com.psplauncher.feature.xmb.viewmodel.pillRowVisible
import com.psplauncher.feature.xmb.viewmodel.promptsFor
import com.psplauncher.feature.xmb.viewmodel.menuRows
import com.psplauncher.feature.xmb.viewmodel.RecentFilter
import com.psplauncher.feature.xmb.viewmodel.fanCoversToDraw
import com.psplauncher.feature.xmb.viewmodel.formatDuration
import com.psplauncher.feature.xmb.viewmodel.XMBUiState
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel

private const val XMB_BASELINE_HEIGHT_DP = 468f

private const val XMB_BASELINE_WIDTH_DP = 832f
private const val XMB_MAX_SCALE = 2.5f

private const val XMB_MIN_SCALE = 0.75f

private val DRILL_CROSSBAR_LEFT_MARGIN = 16.dp

private val CAT_BAR_HEIGHT = 112.dp

@Composable
fun XMBShellContainer(
    viewModel: XMBViewModel = hiltViewModel(),
    onSettingsLongPress: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
        onLetterRailTouch = viewModel::onLetterRailTouch,
        onLetterRailReleased = viewModel::onLetterRailReleased,
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
        onSearchColumnsMeasured = viewModel::onSearchColumnsMeasured,
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
        onAppPickerColumnsMeasured = viewModel::onAppPickerColumnsMeasured,
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

    onRecentFilterTapped: (RecentFilter) -> Unit = {},
    onRecentRailToggled: () -> Unit = {},

    onDrawerTypedCharConsumed: () -> Unit = {},
    onNotificationsToggled: () -> Unit = {},
    onNotificationsDismissed: () -> Unit = {},
    onNoticeTapped: (String) -> Unit = {},
    onNoticeDismissTapped: (String) -> Unit = {},
    onNoticeMediaPrimary: () -> Unit = {},
    onNoticeMediaPrev: () -> Unit = {},
    onNoticeMediaNext: () -> Unit = {},
    onOpenAppDrawer: () -> Unit = {},

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

    onPromptTapped: (com.psplauncher.core.domain.model.GamepadAction) -> Unit = {},

    onPillActivated: (String) -> Unit = {},

    focusedPillIndex: Int? = null,
    onCloseAppDrawer: () -> Unit = {},

    onAddAppToOpenCategory: (String) -> Unit = {},

    onLetterRailTouch: (Float) -> Unit = {},
    onLetterRailReleased: () -> Unit = {},
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

    onSearchColumnsMeasured: (Int) -> Unit = {},
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

    onAppPickerColumnsMeasured: (Int) -> Unit = {},
    onAppPickerDismiss: () -> Unit = {},
    onGamePickerConfirm: (Set<Long>, Set<Long>) -> Unit = { _, _ -> },
    onGamePickerDismiss: () -> Unit = {},
    onGamePickerActionConsumed: () -> Unit = {},
    onDismissInfoDialog: () -> Unit = {},
    onWindowsSetupConfirm: () -> Unit = {},
    onWindowsSetupDismiss: () -> Unit = {},
    onLaunchRecoveryAction: (com.psplauncher.feature.launcher.LaunchRecoveryAction) -> Unit = {},
) {
    val themeWave = uiState.themeColors.waveColor
    val themeAccent = uiState.themeColors.accentColor

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

    val xmbColors = remember(uiState.themeColors, xmbWave, xmbGameAccent) {
        uiState.themeColors.withWaveTint(xmbWave).copy(accentColor = xmbGameAccent)
    }
    PFPTheme(colors = xmbColors) {
      CompositionLocalProvider(
          com.psplauncher.core.ui.icons.LocalXmbIconOverrides provides uiState.iconOverrides,

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

          com.psplauncher.core.ui.icons.LocalCustomIcons provides uiState.customIcons,

          LocalIconDisplayMode provides uiState.iconDisplayMode,
          LocalIconDisplayModeByPlatform provides uiState.iconDisplayModeByPlatform,
          LocalFocusedGameVideo provides uiState.focusedGameVideo,

          LocalPanelShowingVideo provides (uiState.effectivePanelPage == DetailPanelPage.VIDEO),

          com.psplauncher.core.ui.icons.LocalIconLegibility provides uiState.iconLegibility,
      ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val baseDensity = LocalDensity.current

            val uiScale = minOf(
                maxHeight.value / XMB_BASELINE_HEIGHT_DP,
                maxWidth.value / XMB_BASELINE_WIDTH_DP,
            ).coerceIn(XMB_MIN_SCALE, XMB_MAX_SCALE)

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
                LocalControllerConnected provides rememberSystemStatus().controllerConnected,
            ) {
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density * uiScale * layoutAdjust.scale, baseDensity.fontScale),
            ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val waveCovered = uiState.showBootSequence ||
                uiState.activeVideoId != null || uiState.activeGameId != null ||
                uiState.activePhotoViewer != null ||
                uiState.activeAppId != null || uiState.activeAppDrawerFilter != null ||
                uiState.musicPlayerVisible ||

                false

            val powerThrottled = rememberWavePowerThrottle(
                respectBatterySaver  = uiState.respectBatterySaver,
                thermalThrottleAware = uiState.thermalThrottleAware,
            )

            val iconAnimatingAllowed = !powerThrottled && !uiState.hasBlockingOverlay

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

            val effectiveWaveStyle = if (waveCovered || powerThrottled) {
                uiState.waveStyle.frozen
            } else uiState.waveStyle

            val gameBootWaveStyle = if (powerThrottled) uiState.waveStyle.frozen else uiState.waveStyle
            XmbBackground(
                waveStyle           = effectiveWaveStyle,
                customWallpaperPath = uiState.customWallpaperPath,
                waveOverWallpaper   = uiState.waveOverWallpaper,
                wallpaperAccent     = uiState.wallpaperAccent,
                motionWallpaperPath = uiState.motionWallpaperPath,
                motionDecision      = motionDecision,

                waveDrawnByCaller   = true,
                modifier            = Modifier.fillMaxSize(),
            )

            val recentsListState = rememberLazyListState()
            val panelItem = uiState.hoverPanelItem

            val panelContent = uiState.hoverPanelContent

            val panelLogo = panelContent?.logoUri
            val panelPage = panelContent?.let { resolvePanelPage(uiState.effectivePanelPage, it.pages) }
            val panelShowingVideo = panelPage == DetailPanelPage.VIDEO

            val selectedItem = uiState.currentItems.getOrNull(uiState.selectedItemIndex)
            val selectedBg = uiState.focusedItemBackdrop?.takeIf { uiState.itemBackdropEnabled }

            val backdrop: XmbBackdrop? = when {
                selectedBg != null -> XmbBackdrop.Art(selectedBg)
                uiState.itemBackdropEnabled && selectedItem?.isAndroidApp == true &&
                    selectedItem.packageName != null -> XmbBackdrop.AppIcon(selectedItem.packageName)
                else -> null
            }

            val backgroundSnap = uiState.focusedGameVideo?.takeIf {
                shellSnapSite(it.placement, panelShowingVideo) == SnapSite.BACKGROUND &&
                    it.gameId == selectedItem?.gameId
            }

            Crossfade(targetState = backdrop, animationSpec = tween(180), label = "xmbGameBackground") { bg ->
                if (bg != null || backgroundSnap != null) {
                    Box(Modifier.fillMaxSize()) {
                        if (backgroundSnap != null) {
                            Icon1VideoOverlay(
                                videoUri = backgroundSnap.uri,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        when (bg) {
                            is XmbBackdrop.Art -> AsyncImage(
                                model = rememberArtworkModel(bg.uri),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()

                                    .then(if (backgroundSnap != null) Modifier.xmbStillOverVideo() else Modifier),
                            )

                            is XmbBackdrop.AppIcon -> XmbAppIconBackdrop(bg.packageName)
                            null -> Unit
                        }

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

                        if (backgroundSnap != null) {
                            Box(Modifier.fillMaxSize().background(Color(0x5905050C)))
                        }
                    }
                }
            }

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

            val aboveContextRail = when {
                uiState.activeContextMenu != null || uiState.notificationsOpen -> 1f
                !uiState.statusStripVisible -> 0f
                uiState.hasBlockingOverlay -> 1f
                else -> XmbChromeZ
            }

            var flash by remember { mutableStateOf<SystemToast?>(null) }
            LaunchedEffect(Unit) {
                SystemToasts.events.collect { toast ->
                    flash = toast
                    delay(if (toast.kind == ToastKind.ERROR) 5_200L else 3_200L)
                    if (flash?.id == toast.id) flash = null
                }
            }
            val notifications by SystemToasts.recent.collectAsState()

            val androidNotices = uiState.androidNotices
            val notificationsOpen = uiState.notificationsOpen

            val strip = LocalContext.current
            val androidAccess = remember(notificationsOpen) { AndroidNotifications.isEnabled(strip) }

            if (uiState.activeAppDrawerFilter == null &&
                uiState.musicBrowser == null &&
                uiState.search == null &&
                uiState.activeSettingsScreen == null &&
                uiState.activeGameId == null &&
                uiState.activeVideoId == null &&
                uiState.activeAppId == null &&
                uiState.activePhotoViewer == null &&

                uiState.customIconSession == null
            ) {
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

                        .xmbNavGestures(
                            onStepCategory = onStepCategory,
                            onStepItem = onStepItem,
                            onEdgeBack = onTouchBack,
                            stepScale = uiState.touchSensitivity.stepScale,
                        ),
                )
            } else {
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

            val stripOpened = uiState.panelStripOpen

            val metadataAsSubtitle = uiState.gameMetadataVisible

            val rowLabelHidden = panelContent != null && stripOpened
            val panelAlpha = if (onLogoPage) pic0Alpha else 1f

            if (panelContent != null && panelPage != null && stripOpened &&
                (!onLogoPage || (panelLogo != null && pic0Alpha > 0f))
            ) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    val panelWidthFraction = if (onLogoPage) 0.30f else 0.42f

                    val panelHeightFraction = if (onLogoPage) 0.38f else 0.70f
                    val logoCenterOffset: Dp = if (uiState.drillTitle != null) {
                        val contentTop = uiState.layoutSpec.contentTopPaddingDp.dp
                        val crossHeight = maxHeight - contentTop
                        val anchorTop = crossHeight * layoutAdjust.barTopFraction + CAT_BAR_HEIGHT
                        val rowCenter = contentTop + anchorTop + ROW_HEIGHT / 2

                        val halfPanel = panelHeightFraction / 2f
                        rowCenter.coerceIn(maxHeight * halfPanel, maxHeight * (1f - halfPanel)) -
                            maxHeight / 2
                    } else {
                        0.dp
                    }
                    GameDetailPanel(
                        content = panelContent,
                        page = panelPage,

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

            val fanCovers = fanCoversToDraw(
                insideCovers = uiState.currentItems.getOrNull(uiState.selectedItemIndex)?.insideCovers.orEmpty(),
                cardArtGrid = uiState.cardArtGrid,
            )
            if (fanCovers.isNotEmpty()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    XmbCoverFan(
                        covers = fanCovers,
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

            if (panelContent != null && panelPage != null && panelPage != DetailPanelPage.LOGO) {
                DetailPanelStrip(
                    pages = panelContent.pages,
                    current = panelPage,
                    onPageTapped = onPanelPageTapped,
                    modifier = Modifier
                        .align(Alignment.TopEnd)

                        .padding(top = StripHeight + ControllerHintEdgeGap, end = ControllerHintEdgeGap),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()

                    .padding(top = uiState.layoutSpec.contentTopPaddingDp.dp)

                    .xmbNavGestures(
                        onStepCategory = onStepCategory,
                        onStepItem = onStepItem,
                        onEdgeBack = onTouchBack,
                        stepScale = uiState.touchSensitivity.stepScale,

                        swipeBackEnabled = uiState.isInSubItem,
                        onSwipeBack = onTouchBack,
                    ),
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val catBarHeight = CAT_BAR_HEIGHT

                    val layoutSpec = uiState.layoutSpec

                    val barTop = maxHeight * layoutAdjust.barTopFraction

                    val columnBaseInset = XmbLeftAnchor + (CategorySlotWidth / 2) - LEADING_ICON_CENTER

                    val hShift = if (uiState.drillTitle != null) {
                        DRILL_CROSSBAR_LEFT_MARGIN - columnBaseInset
                    } else {
                        maxWidth * layoutAdjust.barLeftFraction
                    }

                    val anchorTop = barTop + catBarHeight
                    val startPad = columnBaseInset + hShift

                    if (uiState.drillTitle != null) {
                        XmbDrillFlyout(
                            onPillActivated = onPillActivated,
                            focusedPillIndex = focusedPillIndex,
                            siblings = uiState.drillSiblings,
                            siblingIndex = uiState.drillSiblingIndex,
                            items = uiState.currentItems,
                            selectedIndex = uiState.selectedItemIndex,
                            onItemSelected = onItemTap,
                            onItemLongPress = onItemLongPress,

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
            }
            }

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

                    title = game.displayTitle,
                    detail = "Continue",
                    artUri = game.artworkUri ?: game.iconUri,

                    progress = null,
                    elapsed = null,
                    isPlaying = false,
                    hasTransport = false,
                    primaryLabel = "Resume",
                )
            }

            val liveActivity = flash?.let { StripLiveActivity(art = null, title = it.title, detail = it.message) }
                ?: musicActivity
                ?: (notifications.size + androidNotices.size)
                    .takeIf { it > 0 }
                    ?.let { StripLiveActivity(art = null, title = countLabel(it, "notification"), detail = null) }

            val xmbContext = uiState.stripShowsXmbContext

            CompositionLocalProvider(LocalDensity provides baseDensity) {
            XmbPspStatusStrip(
                sortLabel = uiState.sortLabel.takeIf { xmbContext },
                showSortButton = uiState.resolvedShowTouchButton && xmbContext,
                onSortTapped = onXmbSortTapped,
                live = liveActivity,

                onLiveAreaTapped = onNotificationsToggled,

                hints = StripHints(
                    shoulder = uiState.panelStripOpen && xmbContext,

                    leftRight = uiState.pillRowVisible && xmbContext,
                ),

                centre = if (uiState.onLastPlayedHome && xmbContext) {
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
            }

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

            val rootActionsVisible = uiState.stripShowsXmbContext && !uiState.isInSubItem

            if (uiState.stripShowsXmbContext) {
                XmbLetterRail(
                    items = uiState.currentItems,
                    letterJump = uiState.letterJump,
                    onTouch = onLetterRailTouch,
                    onReleased = onLetterRailReleased,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)

                        .padding(top = StatusStripHeight, bottom = HintBarHeight, end = 4.dp)
                        .zIndex(XmbChromeZ),
                )
            }

            AnimatedVisibility(

                visible = uiState.notificationsOpen ||
                    ((uiState.showContextMenuHint || rootActionsVisible) && uiState.stripShowsXmbContext),
                enter = fadeIn(tween(200)),
                exit = ExitTransition.None,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(aboveContextRail),
            ) {
                CompositionLocalProvider(LocalDensity provides baseDensity) {
                    XmbHintBar(
                        prompts = promptsFor(uiState),
                        onAction = onPromptTapped,
                    )
                }
            }

            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, baseDensity.fontScale),
            ) {
            if (uiState.colorSchemePicker == null) {
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

                    showControllerHint = uiState.showAppDrawerHint,
                    onPromptTapped = onPromptTapped,

                    onTouchInteraction = onTouchInput,
                    onAddToCrossBar = onAddAppToOpenCategory,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            uiState.search?.let { search ->
                SearchScreen(
                    state = search,
                    onQueryChange = onSearchQueryChange,
                    onActivateAt = onSearchActivatedAt,
                    onBack = onSearchBack,

                    onColumnsMeasured = onSearchColumnsMeasured,
                    modifier = Modifier.fillMaxSize(),
                )
            }

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

            if (uiState.musicPlayerVisible) {
                MusicPlayerScreen(
                    state = uiState.musicPlayback,
                    onPlayPause = onMusicPlayPause,
                    onPrev = onMusicPrev,
                    onNext = onMusicNext,
                    onSeekTo = onMusicSeekTo,
                    onBack = onMusicPlayerBack,
                    onAction = onPromptTapped,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            uiState.activeContextMenu?.let { menu ->

                PspContextMenuOverlay(
                    title = menu.title,
                    rows = uiState.menuRows().map {
                        PspMenuRow(
                            label = it.label,
                            isDestructive = it.isDestructive,
                            checked = it.checked,
                            heading = it.heading,
                        )
                    },
                    selectedIndex = menu.selectedIndex ?: NoMenuSelection,
                    onRowActivated = onContextMenuItemActivated,
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
                    onSlotMove = onCustomIconsSlotFocused,
                    onDone = onCloseCustomIcons,
                    forwardedAction = uiState.pendingCustomIconsAction,
                    onActionConsumed = onCustomIconsActionConsumed,
                    modifier = Modifier.fillMaxSize(),
                )
            }

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

                    onColumnsMeasured = onAppPickerColumnsMeasured,
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
                    onNotifications = onNotificationsToggled,
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

            uiState.activeGameBoot?.let { request ->
                if (request.videoPath == null) {
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

                    waveStyle = gameBootWaveStyle,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            }
        }
            }
            }
        }
      }
    }
}

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
    PfpMessageOverlay(title = title, message = message, onDismiss = onDismiss)
}

@Composable
private fun LaunchRecoverySheet(
    recovery: com.psplauncher.feature.launcher.LaunchRecoveryRequest,
    cursor: Int,
    onAction: (com.psplauncher.feature.launcher.LaunchRecoveryAction) -> Unit,
) {
    val actions = com.psplauncher.feature.launcher.launchRecoveryActions(recovery)

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

private const val NotificationBarZ = 0.5f

private const val XmbChromeZ = 0.6f

