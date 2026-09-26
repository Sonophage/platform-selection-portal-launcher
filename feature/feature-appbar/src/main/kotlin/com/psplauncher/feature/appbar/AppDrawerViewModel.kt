package com.psplauncher.feature.appbar

import com.psplauncher.core.domain.model.PlatformIds.ANDROID as ANDROID_PLATFORM_ID

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.sound.MenuSound
import com.psplauncher.core.ui.sound.MenuSoundPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val APP_SHORTCUT_PLATFORM_ID = "app_shortcut"

enum class AppFilter(val label: String, val subtitle: String) {
    RECENT("Recently Used", "Apps you've used lately"),
    APPS("Apps", "Everything that is not a game or an emulator"),
    EMULATORS("Emulators", "RetroArch, PPSSPP, Dolphin and more"),
    GAMES("Games", "Apps categorized as games");

    fun matches(app: InstalledApp): Boolean = when (this) {
        APPS -> !app.isGame && !app.isEmulator
        GAMES -> app.isGame
        EMULATORS -> app.isEmulator
        RECENT -> app.lastUsedAt > 0L
    }

    companion object {
        val DEFAULT = RECENT
    }
}

enum class AppMenuAction(val label: String) {
    ADD_TO_CROSS_BAR("Add to Cross Bar"),
    APP_INFO("App Info"),
    MARK_GAME("Mark as Game"),
    UNMARK_GAME("Unmark as Game"),
    UNINSTALL("Uninstall"),
}

data class AppDrawerUiState(
    val allApps: List<InstalledApp> = emptyList(),

    val sectionApps: List<InstalledApp> = emptyList(),

    val otherApps: List<InstalledApp> = emptyList(),

    val activeFilter: AppFilter = AppFilter.DEFAULT,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val selectedIndex: Int = 0,

    val usingTouch: Boolean = false,
    val hasUsageAccess: Boolean = false,

    val menuApp: InstalledApp? = null,
    val menuIndex: Int = 0,

    val confirmUninstall: InstalledApp? = null,

    val uninstallConfirmFocused: Boolean = false,

    val menuAppIsGame: Boolean = false,

    val filterCounts: Map<AppFilter, Int> = emptyMap(),

    val sectionListRows: Int = SECTION_LIST_ROWS,

) {
    val visibleApps: List<InstalledApp> get() = sectionApps + otherApps

    val sectionRowCount: Int get() = sectionApps.size

    val menuActions: List<AppMenuAction>
        get() = buildList {
            add(AppMenuAction.ADD_TO_CROSS_BAR)
            add(AppMenuAction.APP_INFO)
            add(if (menuAppIsGame) AppMenuAction.UNMARK_GAME else AppMenuAction.MARK_GAME)
            if (menuApp?.isSystemApp == false) add(AppMenuAction.UNINSTALL)
        }
}

@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    private val appRepository: InstalledAppRepository,
    private val menuSound: MenuSoundPlayer,
    private val gameRepository: com.psplauncher.core.domain.repository.GameRepository,
    private val memoryCardRepository: com.psplauncher.core.data.repository.MemoryCardRepository,
    private val mediaLaunchGate: com.psplauncher.core.data.launch.MediaLaunchGate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppDrawerUiState())
    val uiState: StateFlow<AppDrawerUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val hasUsageAccess = appRepository.hasUsageAccess()
            val apps = appRepository.getInstalledApps()
            _uiState.update {
                it.copy(
                    allApps = apps,
                    isLoading = false,
                    hasUsageAccess = hasUsageAccess,
                )
            }
            applyFilter()
        }
    }

    fun setFilter(filter: AppFilter) {
        if (filter != _uiState.value.activeFilter) menuSound.play(MenuSound.SYSTEM_BROWSE)
        _uiState.update { it.copy(activeFilter = filter, selectedIndex = 0) }
        applyFilter()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, selectedIndex = 0) }
        applyFilter()
    }

    fun onAppSelected(index: Int) {
        _uiState.update { it.copy(selectedIndex = index) }
    }

    fun setSectionListRows(rows: Int) {
        if (rows > 0 && rows != _uiState.value.sectionListRows) {
            _uiState.update { it.copy(sectionListRows = rows) }
        }
    }

    fun onAppTapped(index: Int) {
        _uiState.update { it.copy(selectedIndex = index, usingTouch = true) }
    }

    fun onTouchBrowse(index: Int) {
        val size = _uiState.value.visibleApps.size
        if (size == 0) return
        _uiState.update { it.copy(selectedIndex = index.coerceIn(0, size - 1), usingTouch = true) }
    }

    fun launchApp(packageName: String) {
        menuSound.play(MenuSound.LAUNCH)

        val icon = _uiState.value.visibleApps.firstOrNull { it.packageName == packageName }?.icon
        viewModelScope.launch {
            mediaLaunchGate.awaitHandOff(icon)
            appRepository.launchApp(packageName)
        }
    }

    fun refresh() {
        loadApps()
    }

    fun openAppMenu(app: InstalledApp) {
        menuSound.play(MenuSound.SELECT)
        _uiState.update { it.copy(menuApp = app, menuIndex = 0, menuAppIsGame = false) }

        viewModelScope.launch {
            val entry = gameRepository.getAppEntry(app.packageName)
            val isGame = entry != null &&
                entry.platformId == ANDROID_PLATFORM_ID &&
                entry.contentType == com.psplauncher.core.domain.model.GameContentType.GAME
            _uiState.update {
                if (it.menuApp?.packageName == app.packageName) it.copy(menuAppIsGame = isGame) else it
            }
        }
    }

    fun openAppMenuForSelected() {
        val app = _uiState.value.visibleApps.getOrNull(_uiState.value.selectedIndex) ?: return
        openAppMenu(app)
    }

    fun closeAppMenu() = _uiState.update { it.copy(menuApp = null) }

    fun onMenuAction(action: AppMenuAction) {
        val app = _uiState.value.menuApp ?: return
        when (action) {
            AppMenuAction.APP_INFO -> {
                appRepository.openAppInfo(app.packageName)
                _uiState.update { it.copy(menuApp = null) }
            }
            AppMenuAction.MARK_GAME   -> { setMarkedAsGame(app, marked = true);  _uiState.update { it.copy(menuApp = null) } }
            AppMenuAction.UNMARK_GAME -> { setMarkedAsGame(app, marked = false); _uiState.update { it.copy(menuApp = null) } }

            AppMenuAction.UNINSTALL -> _uiState.update { it.copy(menuApp = null, confirmUninstall = app, uninstallConfirmFocused = false) }

            AppMenuAction.ADD_TO_CROSS_BAR -> _uiState.update { it.copy(menuApp = null) }
        }
    }

    private fun setMarkedAsGame(app: InstalledApp, marked: Boolean) {
        viewModelScope.launch {
            val existing = gameRepository.getAppEntry(app.packageName)
            if (marked) {
                if (existing == null) {
                    gameRepository.upsert(
                        com.psplauncher.core.domain.model.Game(
                            title         = app.label,
                            platformId    = ANDROID_PLATFORM_ID,
                            packageName   = app.packageName,
                            isManualEntry = true,
                            contentType   = com.psplauncher.core.domain.model.GameContentType.GAME,
                        )
                    )
                } else {
                    gameRepository.upsert(existing.copy(
                        platformId  = ANDROID_PLATFORM_ID,
                        contentType = com.psplauncher.core.domain.model.GameContentType.GAME,
                    ))
                }
            } else if (existing != null) {
                gameRepository.upsert(existing.copy(
                    platformId  = APP_SHORTCUT_PLATFORM_ID,
                    contentType = com.psplauncher.core.domain.model.GameContentType.ANDROID_APP,
                ))
            }
            memoryCardRepository.recountGames(ANDROID_PLATFORM_ID)
        }
    }

    fun confirmUninstall() {
        val app = _uiState.value.confirmUninstall ?: return
        appRepository.uninstallApp(app.packageName)
        _uiState.update { it.copy(confirmUninstall = null, uninstallConfirmFocused = false) }
    }

    fun cancelUninstall() = _uiState.update { it.copy(confirmUninstall = null, uninstallConfirmFocused = false) }

    fun openUsageAccessSettings() {
        appRepository.openUsageAccessSettings()
    }

    fun handleGamepadAction(action: GamepadAction) {
        val state = _uiState.value

        state.confirmUninstall?.let {
            when (action) {
                GamepadAction.SELECT ->
                    if (state.uninstallConfirmFocused) confirmUninstall() else cancelUninstall()
                GamepadAction.NAVIGATE_UP, GamepadAction.NAVIGATE_DOWN ->
                    _uiState.update { s -> s.copy(uninstallConfirmFocused = !s.uninstallConfirmFocused) }
                GamepadAction.BACK -> cancelUninstall()

                else -> Unit
            }
            return
        }

        state.menuApp?.let {
            val actions = state.menuActions

            if (actions.isEmpty()) return
            when (action) {
                GamepadAction.NAVIGATE_UP   -> _uiState.update { s -> s.copy(menuIndex = (s.menuIndex - 1 + actions.size) % actions.size) }
                GamepadAction.NAVIGATE_DOWN -> _uiState.update { s -> s.copy(menuIndex = (s.menuIndex + 1) % actions.size) }
                GamepadAction.SELECT        -> onMenuAction(actions[state.menuIndex.coerceIn(0, actions.size - 1)])

                GamepadAction.BACK               -> closeAppMenu()
                GamepadAction.OPEN_CONTEXT_MENU  -> closeAppMenu()
                GamepadAction.CHANGE_SORT        -> closeAppMenu()
                else -> Unit
            }
            return
        }

        if (action == GamepadAction.PREV_CATEGORY || action == GamepadAction.NEXT_CATEGORY) {
            val filters = AppFilter.values()
            val idx = filters.indexOf(state.activeFilter)
            val target = if (action == GamepadAction.PREV_CATEGORY) idx - 1 else idx + 1
            if (target in filters.indices) setFilter(filters[target])
            return
        }

        val size  = state.visibleApps.size
        if (size == 0) return

        if (state.usingTouch) _uiState.update { it.copy(usingTouch = false) }
        val cur = state.selectedIndex
        when (action) {
            GamepadAction.OPEN_CONTEXT_MENU -> openAppMenuForSelected()
            GamepadAction.NAVIGATE_LEFT, GamepadAction.NAVIGATE_RIGHT,
            GamepadAction.NAVIGATE_UP, GamepadAction.NAVIGATE_DOWN -> {
                val next = sectionMove(action, cur, state.sectionRowCount, size, state.sectionListRows)

                if (next != cur) {
                    _uiState.update { it.copy(selectedIndex = next) }
                    menuSound.play(MenuSound.SCROLL)
                }
            }
            GamepadAction.SELECT -> {
                val app = state.visibleApps.getOrNull(cur)
                if (app != null) launchApp(app.packageName)
            }

            else -> Unit
        }
    }

    private fun applyFilter() {
        val state = _uiState.value
        val query = state.searchQuery.trim().lowercase()

        val filtered = state.allApps
            .filter { app ->
                state.activeFilter.matches(app)
            }
            .filter { app ->
                query.isEmpty() || app.label.lowercase().contains(query)
            }
            .let { apps ->
                if (state.activeFilter == AppFilter.RECENT) {
                    apps.sortedByDescending { it.lastUsedAt }
                } else {
                    apps
                }
            }

        val counts = AppFilter.values().associateWith { filter ->
            state.allApps.count { app ->
                filter.matches(app)
            }
        }

        val rest = state.allApps
            .filter { app -> !state.activeFilter.matches(app) }
            .filter { app -> query.isEmpty() || app.label.lowercase().contains(query) }

        _uiState.update { it.copy(sectionApps = filtered, otherApps = rest, filterCounts = counts) }
    }
}
