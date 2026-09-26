package com.psplauncher.feature.appbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toDrawable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.lightBackgroundAnchors
import com.psplauncher.core.ui.components.PspContextMenuOverlay
import com.psplauncher.core.ui.components.StatusStripHeight
import com.psplauncher.core.ui.components.HintBarHeight
import com.psplauncher.core.ui.preview.CombinedPreviews
import com.psplauncher.core.ui.preview.PfpPreview
import com.psplauncher.core.ui.theme.PFPColors
import com.psplauncher.core.ui.theme.StorefrontColors
import com.psplauncher.core.ui.theme.deriveStorefrontColors
import com.psplauncher.core.ui.theme.menuCursorEdge
import com.psplauncher.feature.appbar.appdrawer.AppDrawerCategoryTabs
import com.psplauncher.feature.appbar.appdrawer.AppDrawerSection
import com.psplauncher.feature.appbar.appdrawer.AppDrawerHeader
import com.psplauncher.feature.appbar.appdrawer.AppDrawerHintBar
import com.psplauncher.feature.appbar.appdrawer.UninstallConfirmDialog
import com.psplauncher.core.ui.components.rowsShown
import com.psplauncher.core.ui.components.XmbLetterRail
import com.psplauncher.feature.appbar.appdrawer.HEADER_HEIGHT

@OptIn(ExperimentalComposeUiApi::class)

private val NAVIGATION_ACTIONS = setOf(
    GamepadAction.NAVIGATE_UP,
    GamepadAction.NAVIGATE_DOWN,
    GamepadAction.NAVIGATE_LEFT,
    GamepadAction.NAVIGATE_RIGHT,
)

@Composable
fun AppDrawerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialFilter: AppFilter = AppFilter.DEFAULT,
    pendingGamepadAction: GamepadAction? = null,

    typedChar: String? = null,
    onTypedCharConsumed: () -> Unit = {},
    onGamepadActionConsumed: () -> Unit = {},

    showControllerHint: Boolean = false,

    letterRailHeld: Boolean = false,

    onTouchInteraction: () -> Unit = {},

    onAddToCrossBar: (String) -> Unit = {},

    onLaunchRom: (Long) -> Unit = {},

    onPromptTapped: ((GamepadAction) -> Unit)? = null,
    viewModel: AppDrawerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchActive by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(pendingGamepadAction) {
        if (pendingGamepadAction != null) {
            val overlayOpen = state.menuApp != null || state.confirmUninstall != null ||
                searchActive || state.letterCursor != null
            when {
                searchActive && pendingGamepadAction == GamepadAction.BACK -> {
                    searchActive = false
                    viewModel.setSearchQuery("")
                    keyboard?.hide()
                }

                searchActive && pendingGamepadAction in NAVIGATION_ACTIONS -> {
                    searchActive = false
                    keyboard?.hide()
                    viewModel.handleGamepadAction(pendingGamepadAction)
                }
                overlayOpen -> viewModel.handleGamepadAction(pendingGamepadAction)

                pendingGamepadAction == GamepadAction.BACK ->
                    if (state.letterFilter != null) viewModel.clearLetterFilter() else onBack()
                pendingGamepadAction == GamepadAction.CHANGE_SORT -> {
                    searchActive = !searchActive
                    if (!searchActive) viewModel.setSearchQuery("")
                }
                else -> viewModel.handleGamepadAction(pendingGamepadAction)
            }
            onGamepadActionConsumed()
        }
    }

    LaunchedEffect(state.pendingRomLaunch) {
        val id = state.pendingRomLaunch ?: return@LaunchedEffect
        onLaunchRom(id)
        viewModel.onRomLaunchHandled()
    }

    LaunchedEffect(letterRailHeld) {
        if (letterRailHeld) viewModel.openLetterJump() else viewModel.closeLetterJump()
    }

    LaunchedEffect(typedChar) {
        val ch = typedChar ?: return@LaunchedEffect

        searchActive = true
        viewModel.setSearchQuery(state.searchQuery + ch)
        onTypedCharConsumed()
    }

    val appliedInitial = remember { mutableStateOf(false) }
    if (!appliedInitial.value) {
        viewModel.setFilter(initialFilter)
        viewModel.setSearchQuery("")
        appliedInitial.value = true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppDrawerContent(
        onPromptTapped = onPromptTapped,

        onListRowsMeasured = viewModel::setSectionListRows,
        state = state,
        searchActive = searchActive,
        showControllerHint = showControllerHint,

        onBack = {
            onTouchInteraction()
            onBack()
        },
        onSearchQueryChange = { viewModel.setSearchQuery(it) },
        onSearchToggle = { active ->
            onTouchInteraction()
            searchActive = active
            if (!active) viewModel.setSearchQuery("")
        },
        onSearchDone = { keyboard?.hide() },
        onFilterSelected = { filter ->
            onTouchInteraction()
            viewModel.setFilter(filter)
        },
        onAppTapped = { index ->
            onTouchInteraction()

            if (searchActive) {
                searchActive = false
                keyboard?.hide()
            }
            viewModel.onAppTapped(index)
        },
        onAppLaunched = { viewModel.launchApp(it) },
        onAppMenu = { viewModel.openAppMenu(it) },
        onTouchBrowse = { index ->
            onTouchInteraction()
            if (searchActive) {
                searchActive = false
                keyboard?.hide()
            }
            viewModel.onTouchBrowse(index)
        },
        onMenuRowActivated = { index ->
            val picked = state.appMenu?.rowsShown()?.getOrNull(index)?.action
            if (picked == AppMenuAction.ADD_TO_CROSS_BAR) {
                state.menuApp?.let { onAddToCrossBar(it.packageName) }
            }
            viewModel.onMenuRowActivated(index)
        },
        onMenuAction = { action ->

            if (action == AppMenuAction.ADD_TO_CROSS_BAR) {
                state.menuApp?.let { onAddToCrossBar(it.packageName) }
            }
            viewModel.onMenuAction(action)
        },
            onLetterRailTouch = viewModel::onLetterRailTouch,
        onLetterRailReleased = viewModel::onLetterRailReleased,
        onCloseMenu = { viewModel.closeAppMenu() },
        onConfirmUninstall = { viewModel.confirmUninstall() },
        onCancelUninstall = { viewModel.cancelUninstall() },
        onGrantUsageAccess = { viewModel.openUsageAccessSettings() },
        modifier = modifier,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AppDrawerContent(
    state: AppDrawerUiState,
    searchActive: Boolean,
    showControllerHint: Boolean,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onSearchDone: () -> Unit,
    onFilterSelected: (AppFilter) -> Unit,
    onAppTapped: (Int) -> Unit,
    onAppLaunched: (String) -> Unit,
    onAppMenu: (InstalledApp) -> Unit,
    onTouchBrowse: (Int) -> Unit,
    onMenuAction: (AppMenuAction) -> Unit,
    onCloseMenu: () -> Unit,
    onConfirmUninstall: () -> Unit,
    onCancelUninstall: () -> Unit,
    onGrantUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
    onMenuRowActivated: (Int) -> Unit = {},

    onLetterRailTouch: (Int) -> Unit = {},
    onLetterRailReleased: () -> Unit = {},

    onPromptTapped: ((GamepadAction) -> Unit)? = null,

    onListRowsMeasured: (Int) -> Unit = {},
) {
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val sf = deriveStorefrontColors()

    LaunchedEffect(searchActive) {
        if (searchActive) {
            withFrameNanos {}
            withFrameNanos {}
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()

            .background(
                Brush.verticalGradient(
                    listOf(sf.backgroundDeep, sf.backgroundMid),
                )
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = StatusStripHeight)) {
            AppDrawerCategoryTabs(
                activeFilter = state.activeFilter,
                filterCounts = state.filterCounts,
                onFilterSelected = onFilterSelected,
                colors = sf,
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(
                            color = menuCursorEdge(),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    state.visibleApps.isEmpty() -> {
                        EmptyDrawerMessage(
                            filter = state.activeFilter,
                            hasQuery = state.searchQuery.isNotBlank(),
                            hasUsageAccess = state.hasUsageAccess,
                            onGrantUsageAccess = onGrantUsageAccess,
                            colors = sf,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        AppDrawerSection(
                            matched = state.sectionApps,
                            rest = state.otherApps,
                            selectedIndex = state.selectedIndex,
                            usingTouch = state.usingTouch,
                            filter = state.activeFilter,
                            onAppTapped = onAppTapped,
                            onAppLaunched = onAppLaunched,
                            onAppMenu = onAppMenu,
                            colors = sf,
                            onListRowsMeasured = onListRowsMeasured,
                        )
                    }
                }
            }

            AppDrawerHeader(
                searchQuery = state.searchQuery,
                searchActive = searchActive,
                searchFocus = searchFocus,
                onSearchToggle = onSearchToggle,
                onSearchChange = onSearchQueryChange,
                onSearchDone = onSearchDone,
                colors = sf,
            )

            val hintAlpha by animateFloatAsState(
                targetValue = if (showControllerHint && state.confirmUninstall == null) 1f else 0f,
                animationSpec = tween(200),
                label = "appDrawerHint",
            )

            AppDrawerHintBar(
                modifier = Modifier.alpha(hintAlpha),
                menuOpen = state.menuApp != null,
                onAction = onPromptTapped?.takeIf { hintAlpha > 0f },
            )
        }

        XmbLetterRail(
            letters = state.letterMenu,
            cursor = state.letterCursor,
            onTouch = onLetterRailTouch,
            onReleased = onLetterRailReleased,
            bottom = HEADER_HEIGHT + HintBarHeight,
        )

        state.appMenu?.let { menu ->
            PspContextMenuOverlay(
                state = menu,
                onRowActivated = onMenuRowActivated,
                onDismiss = onCloseMenu,
            )
        }

        state.confirmUninstall?.let { app ->
            UninstallConfirmDialog(
                app = app,
                confirmFocused = state.uninstallConfirmFocused,
                onConfirm = onConfirmUninstall,
                onCancel = onCancelUninstall,
            )
        }
    }
}

@Composable
private fun EmptyDrawerMessage(
    filter: AppFilter,
    hasQuery: Boolean,
    hasUsageAccess: Boolean,
    onGrantUsageAccess: () -> Unit,
    colors: StorefrontColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = when {
                hasQuery -> "No apps match your search"
                filter == AppFilter.GAMES -> "No games found"
                filter == AppFilter.EMULATORS -> "No emulators installed"
                filter == AppFilter.RECENT && !hasUsageAccess -> "Usage access needed"
                filter == AppFilter.RECENT -> "No recently used apps yet"
                else -> "No apps installed"
            },
            color = colors.textSecondary,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                hasQuery -> "Try a different search term"
                filter == AppFilter.GAMES -> "Apps marked as games in the Play Store appear here"
                filter == AppFilter.EMULATORS -> "Install RetroArch, PPSSPP, or another emulator"
                filter == AppFilter.RECENT && !hasUsageAccess -> "Grant access so PSP can sort apps by last used time"
                else -> ""
            },
            color = colors.textSecondary.copy(alpha = 0.6f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        if (filter == AppFilter.RECENT && !hasUsageAccess) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Open Usage Access",
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.searchField)
                    .border(1.dp, colors.searchBorder, RoundedCornerShape(2.dp))
                    .clickable { onGrantUsageAccess() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@CombinedPreviews
@Composable
fun AppDrawerScreenPreview() {
    PfpPreview {
        AppDrawerPreviewContent()
    }
}

@Preview(name = "Classic Blue", group = "App Drawer Accents")
@Composable
fun AppDrawerScreenPreviewClassicBlue() {
    PfpPreview(colors = accentPreviewColors(0xFF0055AAL)) { AppDrawerPreviewContent() }
}

@Preview(name = "Sunset Orange", group = "App Drawer Accents")
@Composable
fun AppDrawerScreenPreviewSunsetOrange() {
    PfpPreview(colors = accentPreviewColors(0xFFFF8A3DL)) { AppDrawerPreviewContent() }
}

@Preview(name = "Fresh Green", group = "App Drawer Accents")
@Composable
fun AppDrawerScreenPreviewFreshGreen() {
    PfpPreview(colors = accentPreviewColors(0xFF36C26BL)) { AppDrawerPreviewContent() }
}

@Preview(name = "Sakura Pink", group = "App Drawer Accents")
@Composable
fun AppDrawerScreenPreviewSakuraPink() {
    PfpPreview(colors = accentPreviewColors(0xFFE87FB0L)) { AppDrawerPreviewContent() }
}

@Preview(name = "Silver Mono", group = "App Drawer Accents")
@Composable
fun AppDrawerScreenPreviewSilverMono() {
    PfpPreview(colors = accentPreviewColors(0xFFB8C4D0L)) { AppDrawerPreviewContent() }
}

@Preview(name = "Golden Amber", group = "App Drawer Accents")
@Composable
fun AppDrawerScreenPreviewGoldenAmber() {
    PfpPreview(colors = accentPreviewColors(0xFFE0A32EL)) { AppDrawerPreviewContent() }
}

@Composable
private fun AppDrawerPreviewContent() {
    val mockIcon = android.graphics.Color.LTGRAY.toDrawable()
    val mockApps = listOf(
        InstalledApp("com.android.chrome", "Chrome", mockIcon, isGame = false, isEmulator = false),
        InstalledApp("org.ppsspp.ppsspp", "PPSSPP", mockIcon, isGame = false, isEmulator = true),
        InstalledApp("com.retroarch", "RetroArch", mockIcon, isGame = false, isEmulator = true),
        InstalledApp(
            "com.google.android.youtube",
            "YouTube",
            mockIcon,
            isGame = false,
            isEmulator = false
        ),
        InstalledApp(
            "com.psplauncher.launcher",
            "PSPLauncher",
            mockIcon,
            isGame = false,
            isEmulator = false
        ),
    )

    val mockCounts = AppFilter.entries.associateWith { filter -> mockApps.count(filter::matches) }
    val mockState = AppDrawerUiState(
        sectionApps = mockApps.filter(AppFilter.DEFAULT::matches),
        otherApps = mockApps.filterNot(AppFilter.DEFAULT::matches),
        activeFilter = AppFilter.DEFAULT,
        selectedIndex = 1,
        filterCounts = mockCounts,
    )
    AppDrawerContent(
        state = mockState,
        searchActive = false,

        showControllerHint = true,
        onBack = {},
        onSearchQueryChange = {},
        onSearchToggle = {},
        onSearchDone = {},
        onFilterSelected = {},
        onAppTapped = {},
        onAppLaunched = {},
        onAppMenu = {},
        onTouchBrowse = {},
        onMenuAction = {},
        onCloseMenu = {},
        onConfirmUninstall = {},
        onCancelUninstall = {},
        onGrantUsageAccess = {},
    )
}

private fun accentPreviewColors(waveArgb: Long): PFPColors {
    val (top, bottom) = lightBackgroundAnchors(waveArgb)
    return PFPColors(
        waveColor = Color(waveArgb),
        accentColor = Color.White,
        textPrimary = Color.White,
        textSecondary = Color.White.copy(alpha = 0.7f),
        backgroundOverlay = Color(0x88000000),
        selectedItem = Color.White,
        categoryBar = Color(0x00000000),
        backgroundTop = Color(top),
        backgroundBottom = Color(bottom),
    )
}
