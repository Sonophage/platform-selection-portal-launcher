package com.psplauncher.feature.xmb.ui

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
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
import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.ui.motion.MotionWallpaperPolicy
import com.psplauncher.core.ui.motion.rememberAppVisible
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

    XMBShell(
        uiState = uiState,
        onCategorySelected = viewModel::onCategoryTapped,
        onStepCategory = viewModel::stepCategory,
        onStepItem = viewModel::stepItem,
        onTouchBack = viewModel::onHomeBack,
        onTouchInput = viewModel::markTouchInput,
        onXmbSortTapped = viewModel::onSortLabelTapped,
        onOpenAppDrawer = viewModel::onOpenAppDrawer,
        onItemTap = viewModel::onItemTap,
        onItemLongPress = viewModel::onItemLongPress,
        onPlatformLongPress = viewModel::onPlatformLongPress,
        onUserInteraction = viewModel::onUserInteraction,
        onBootComplete = viewModel::onBootSequenceComplete,
        onSettingsLongPress = onSettingsLongPress,
        onCloseSettingsScreen = viewModel::onCloseSettingsScreen,
        onOpenSettingsScreen = viewModel::onOpenSettingsScreen,
        onOpenXmbLayoutAdjust = viewModel::openXmbLayoutAdjust,
        onOpenCustomIcons = viewModel::openCustomIcons,
        onPreviewBootSequence = viewModel::previewBootSequence,
        onPreviewGameBoot = viewModel::previewGameBoot,
        onGameBootComplete = viewModel::onGameBootComplete,
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
        onCloseAppDrawer = viewModel::onCloseAppDrawer,
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
    onCloseAppDrawer: () -> Unit = {},
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
          // The user's per-slot picks ride the same rail — the tier ABOVE the theme's icons
          // (user pick > theme icon > built-in, at every render site).
          com.psplauncher.core.ui.icons.LocalCustomIcons provides uiState.customIcons,
          // Icon display mode + the focused game's approved ICON1 snap ride the same rail so
          // the deeply nested tile composables never need them plumbed through params.
          LocalIconDisplayMode provides uiState.iconDisplayMode,
          LocalIconDisplayModeByPlatform provides uiState.iconDisplayModeByPlatform,
          LocalFocusedGameVideo provides uiState.focusedGameVideo,
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
                motionWallpaperPath = uiState.motionWallpaperPath,
                motionDecision      = motionDecision,
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
            val selectedItem = uiState.currentItems.getOrNull(uiState.selectedItemIndex)
            val selectedBg = uiState.focusedItemBackdrop?.takeIf { uiState.itemBackdropEnabled }
            // PS3 placement: the approved snap plays full-bleed here instead of in the tile,
            // over the still art and UNDER the legibility scrim, so the crossbar keeps the same
            // contrast it has over a still background. Same FocusedGameVideo, same gates, same
            // single player — Icon1VideoOverlay centre-crops to whatever bounds it is given.
            val backgroundSnap = uiState.focusedGameVideo?.takeIf {
                it.placement == VideoSnapPlacement.BACKGROUND && it.gameId == selectedItem?.gameId
            }
            Crossfade(targetState = selectedBg, animationSpec = tween(320), label = "xmbGameBackground") { bg ->
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

            // Hide the XMB foreground (status strip + category bar + item list) while a fullscreen
            // menu is open — only the wallpaper/wave background shows behind it. Restored
            // automatically when the menu closes. Besides the visual, this REMOVES the XMB's
            // clickable rows from composition, so a tap on the overlay's empty space can never fall
            // through and activate an XMB item behind it. Covers the app drawer, music browser,
            // Settings, and the fullscreen detail screens (Game / Video / App / Photo) — those now
            // use a translucent backdrop, so the XMB would otherwise show through them.
            if (uiState.activeAppDrawerFilter == null &&
                uiState.musicBrowser == null &&
                uiState.activeSettingsScreen == null &&
                uiState.activeGameId == null &&
                uiState.activeVideoId == null &&
                uiState.activeAppId == null &&
                uiState.activePhotoViewer == null &&
                // The icon editor is translucent — the live XMB (with the custom look
                // applying behind it) IS the point, so the foreground stays composed.
                uiState.customIconSession == null
            ) {

            // PIC0-style logo overlay — the focused game's clear logo fades in center-right
            // over the hover background, a beat AFTER the background lands (the PSP's
            // icon → PIC1 → PIC0 stagger). Fades out instantly with any focus move.
            val selectedLogo = uiState.currentItems.getOrNull(uiState.selectedItemIndex)
                ?.takeIf { it.hasVisibleLogo }?.logoUri
            var pic0Visible by remember(selectedLogo) { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(selectedLogo) {
                if (selectedLogo != null) {
                    kotlinx.coroutines.delay(650)
                    pic0Visible = true
                }
            }
            // Fade-in only: stepping the cursor must hide the logo INSTANTLY (snap), so the
            // next game's logo is never glimpsed before its own linger completes.
            val pic0Alpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (pic0Visible && selectedLogo != null) 1f else 0f,
                animationSpec = if (pic0Visible) tween(500) else androidx.compose.animation.core.snap(),
                label = "pic0Fade",
            )
            if (selectedLogo != null && pic0Alpha > 0f) {
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
                    val logoCenterOffset: Dp = if (uiState.drillTitle != null) {
                        val contentTop = uiState.layoutSpec.contentTopPaddingDp.dp
                        val crossHeight = maxHeight - contentTop
                        val anchorTop = crossHeight * layoutAdjust.barTopFraction + CAT_BAR_HEIGHT
                        val rowCenter = contentTop + anchorTop + ROW_HEIGHT / 2
                        // The logo is 38% of the height and centred, so keep its centre within
                        // [19%, 81%] — a low crossbar must not push it off the bottom edge.
                        rowCenter.coerceIn(maxHeight * 0.19f, maxHeight * 0.81f) - maxHeight / 2
                    } else {
                        0.dp
                    }
                    AsyncImage(
                        model = rememberArtworkModel(selectedLogo),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.30f)
                            .fillMaxHeight(0.38f)
                            .offset(y = logoCenterOffset)
                            .padding(end = 44.dp)
                            .alpha(pic0Alpha),
                    )
                }
            }

            // The focused game's scraped one-liner, right-aligned under where the logo sits.
            // Deliberately NOT a row label: XMBItemList's rule is that a game with a logo shows
            // no text, because the logo IS the identity. This is the other half of the PS3's
            // game info -- what the thing IS, not what it is called -- so it lives with the
            // logo rather than in the list.
            val metadataLine = uiState.currentItems.getOrNull(uiState.selectedItemIndex)
                ?.takeIf { uiState.gameMetadataVisible && it.isRealGame }
                ?.metadataLine
            var metaVisible by remember(metadataLine) { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(metadataLine) {
                if (metadataLine != null) {
                    // The same 650 ms as the logo, so the two land together rather than the
                    // text arriving first and the logo catching up.
                    kotlinx.coroutines.delay(650)
                    metaVisible = true
                }
            }
            val metaAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (metaVisible && metadataLine != null) 1f else 0f,
                animationSpec = if (metaVisible) tween(500) else androidx.compose.animation.core.snap(),
                label = "gameMetaFade",
            )
            if (metadataLine != null && metaAlpha > 0f) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    // The accent rule: a short bar in the game's own colour, directly above its
                    // facts. It is the one place the colour is stated OUTRIGHT rather than mixed
                    // into something else, which is what makes the tint elsewhere read as
                    // deliberate instead of as a cast on the artwork.
                    Box(
                        modifier = Modifier
                            .offset(y = maxHeight * 0.22f - 14.dp)
                            .padding(end = 44.dp)
                            .width(64.dp)
                            .height(3.dp)
                            .alpha(metaAlpha)
                            .background(xmbGameAccent),
                    )
                    androidx.compose.material3.Text(
                        text = metadataLine,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                blurRadius = 5f,
                            ),
                        ),
                        // The logo box is 38% of the height, centred, so its lower edge is at
                        // 19%. This clears it by a line.
                        modifier = Modifier
                            .offset(y = maxHeight * 0.22f)
                            .padding(end = 44.dp)
                            .alpha(metaAlpha),
                    )
                }
            }

            XmbPspStatusStrip(
                sortLabel = uiState.sortLabel,
                showSortButton = uiState.resolvedShowTouchButton,
                onSortTapped = onXmbSortTapped,
                modifier = Modifier.align(Alignment.TopCenter),
            )

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
                            siblings = uiState.drillSiblings,
                            siblingIndex = uiState.drillSiblingIndex,
                            items = uiState.currentItems,
                            selectedIndex = uiState.selectedItemIndex,
                            onItemSelected = onItemTap,
                            onItemLongPress = onItemLongPress,
                            // Tapping the active memory card under the caticon backs out of the
                            // drill; taps on the other (dimmed) cards are ignored.
                            onSiblingTap = { i -> if (i == uiState.drillSiblingIndex) onTouchBack() },
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
                                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 8 })
                                    .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(180)) { -it / 10 })
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
                                items = uiState.currentItems,
                                selectedIndex = itemSelectedIndex,
                                onItemSelected = onItemTap,
                                onItemLongPress = onItemLongPress,
                                iconStyle = uiState.iconStyle,
                                barTopY = barTop,
                                belowTopY = anchorTop,
                                previousRiseRows = layoutSpec.previousItemRiseRows,
                                solidUnfocusedIcons = uiState.solidUnfocusedIcons,
                                textShadow = uiState.textShadow,
                                iconAnimatingAllowed = iconAnimatingAllowed,
                                focusedLogoVisible = pic0Alpha > 0f,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    // Category bar drawn ON TOP, pushed down to the crossbar line — the fixed pivot
                    // the first-level column appears to scroll beneath. The horizontal shift rides a
                    // composition local so the caticon bar tracks the item column as one cross.
                    CompositionLocalProvider(LocalXmbHorizontalShift provides hShift) {
                        XMBCategoryBar(
                            categories = uiState.categories,
                            selectedIndex = uiState.selectedCategoryIndex,
                            onCategorySelected = onCategorySelected,
                            onCategoryLongPress = { index ->
                                val id = uiState.categories.getOrNull(index)?.id
                                if (id == BuiltInCategory.SETTINGS) onSettingsLongPress()
                            },
                            drilledIn = uiState.drillTitle != null,
                            solidUnfocusedIcons = uiState.solidUnfocusedIcons,
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
            } // end: XMB foreground hidden while music browser is open

            // Bottom-right App Drawer affordance. Shown only at the XMB root — while drilled into a
            // sub-item it's hidden entirely (going back is done by the left-edge swipe or by tapping
            // the active memory-card icon under the caticon, so no Back button is needed here).
            // Also hidden whenever an overlay/dialog is up. Visibility follows the last input source
            // (Auto) or the user's override, and fades in/out so picking up a controller cleanly
            // hides the touch-only affordance.
            AnimatedVisibility(
                visible = uiState.resolvedShowTouchButton && !uiState.hasBlockingOverlay && !uiState.isInSubItem,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(220)),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                AppDrawerButton(
                    onClick = onOpenAppDrawer,
                    modifier = Modifier.padding(bottom = 24.dp, end = 20.dp),
                )
            }

            // Idle hint pill: [ X Sort   Y Options ], with the controller-style glyphs, fading
            // in after the user has been idle. Controller-only — touch input suppresses it.
            // Driven by uiState.showContextMenuHint (set by XMBViewModel's idle timer); each half
            // appears only where that action really does something, so the pill shrinks to just
            // Options on an unsortable list and to just Sort on an item with no context menu.
            //
            // It shows while drilled in too (the game flyout, a library's files) — those rows have
            // context menus and sort, and are where the affordance is least discoverable.
            //
            // Sits in the App Drawer button's slot when that button is hidden, and stacks above it
            // when both are up, so the two affordances never overlap.
            val drawerButtonVisible =
                uiState.resolvedShowTouchButton && !uiState.hasBlockingOverlay && !uiState.isInSubItem
            AnimatedVisibility(
                visible = uiState.showContextMenuHint &&
                    uiState.activeContextMenu == null &&
                    !uiState.hasBlockingOverlay,
                enter = fadeIn(tween(200)),
                exit = ExitTransition.None,
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                ContextMenuHint(
                    showSort = uiState.canSortCurrentList,
                    showOptions = uiState.focusedItemHasContextMenu,
                    modifier = Modifier.padding(
                        bottom = if (drawerButtonVisible) 76.dp else 24.dp,
                        end = 20.dp,
                    ),
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
                    .getOrDefault(AppFilter.ALL)
                AppDrawerScreen(
                    initialFilter = initialFilter,
                    onBack = onCloseAppDrawer,
                    pendingGamepadAction = uiState.pendingDrawerAction,
                    onGamepadActionConsumed = onDrawerActionConsumed,
                    // The drawer renders its own idle controller hint pill (same fade system as
                    // the XMB's ContextMenuHint — see shouldShowAppDrawerHint).
                    showControllerHint = uiState.showAppDrawerHint,
                    // Drawer touches are reported to the shared input-source tracker so a finger
                    // tap/browse suppresses that hint exactly like touch on the XMB does.
                    onTouchInteraction = onTouchInput,
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
                    showTouchControls = uiState.resolvedShowTouchButton,
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
                ContextMenuOverlay(
                    menu = menu,
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
                    pendingGamepadAction = uiState.pendingVideoDetailAction,
                    onGamepadActionConsumed = onVideoDetailActionConsumed,
                    showTouchControls = uiState.resolvedShowTouchButton,
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
                    showTouchControls = uiState.resolvedShowTouchButton,
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
) {
    PfpTextPromptOverlay(
        title = title,
        value = text,
        placeholder = "e.g. RPGs, Currently Playing",
        onValueChange = onTextChange,
        onConfirm = { onConfirm(text) },
        onCancel = onCancel,
    )
}

/** Bottom-corner touch button that opens the app drawer — a 2×2 grid glyph drawn on a Canvas
 *  (no icon-library dependency). Controller users reach the drawer with BACK at the root instead. */
@Composable
private fun AppDrawerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Delegates the frame to the shared themed touch button; only the 2×2 grid glyph is local.
    XmbTouchButton(onClick = onClick, modifier = modifier) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val cell = size.minDimension * 0.38f
            val gap = size.minDimension - 2 * cell
            val radius = CornerRadius(cell * 0.28f, cell * 0.28f)
            val shadowOffset = size.minDimension * 0.06f
            for (row in 0..1) {
                for (col in 0..1) {
                    val x = col * (cell + gap)
                    val y = row * (cell + gap)
                    // Soft dark shadow cell behind, then the bright glyph cell on top.
                    drawRoundRect(
                        color = Color(0x99000000),
                        topLeft = Offset(x + shadowOffset, y + shadowOffset),
                        size = Size(cell, cell),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(x, y),
                        size = Size(cell, cell),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
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
    onAction: (com.psplauncher.feature.launcher.LaunchRecoveryAction) -> Unit,
) {
    // This is the surface a failed launch drops you on, which is exactly the moment a controller
    // has to work -- and as an AlertDialog it was the one place in the app where it could not.
    // The ViewModel already answers A with Retry and B with Dismiss; the rest are touch targets,
    // as they were before, and Retry is drawn focused because A is what performs it.
    PfpOverlayCard(onScrimTap = { onAction(com.psplauncher.feature.launcher.LaunchRecoveryAction.DISMISS) }) {
        PfpOverlayTitle("Couldn't launch ${recovery.gameTitle}")
        Spacer(Modifier.height(10.dp))
        Text(recovery.message, color = Color(0xCCFFFFFF), fontSize = 14.sp)
        recovery.historyLine?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0x99FFFFFF), fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PfpDetailLaunchButton(
                label = "Retry",
                icon = null,
                focused = true,
                onClick = { onAction(com.psplauncher.feature.launcher.LaunchRecoveryAction.RETRY) },
                modifier = Modifier.fillMaxWidth(),
            )
            PfpDetailLaunchButton(
                label = "Change Emulator",
                icon = null,
                focused = false,
                onClick = { onAction(com.psplauncher.feature.launcher.LaunchRecoveryAction.CHANGE_EMULATOR) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (recovery.resolved != null) {
                PfpDetailLaunchButton(
                    label = "Change per-system default",
                    icon = null,
                    focused = false,
                    onClick = { onAction(com.psplauncher.feature.launcher.LaunchRecoveryAction.PER_SYSTEM_DEFAULTS) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PfpDetailLaunchButton(
                label = "Copy diagnostics",
                icon = null,
                focused = false,
                onClick = { onAction(com.psplauncher.feature.launcher.LaunchRecoveryAction.COPY_DIAGNOSTIC) },
                modifier = Modifier.fillMaxWidth(),
            )
            PfpDetailLaunchButton(
                label = "Dismiss",
                icon = null,
                focused = false,
                onClick = { onAction(com.psplauncher.feature.launcher.LaunchRecoveryAction.DISMISS) },
                modifier = Modifier.fillMaxWidth(),
            )
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
