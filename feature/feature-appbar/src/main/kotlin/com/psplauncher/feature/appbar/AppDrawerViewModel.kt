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

/**
 * Apps across a drawer grid. Eight, as 6e drew it, up from six.
 *
 * The App Drawer itself no longer has a grid: All Apps was the one tab that drew one, and it is
 * gone. This is now read only by StorefrontAppDrawer, which nothing reaches either. Left here
 * rather than deleted with the grid because it is not this change's to take.
 */
const val GRID_COLUMNS = 8

// The Android Memory Card's platform id, and the sentinel platform for rows that only back an
// app's artwork/favorites/collections without placing it in the library (mirrors XMBViewModel).
private const val APP_SHORTCUT_PLATFORM_ID = "app_shortcut"

/**
 * The app menu's sections, in the order they are shown.
 *
 * Declaration order IS the order of the drawer's tabs and of the Android column's rows, because
 * both are built from `entries` — see AppDrawerCategoryTabs and XMBViewModel.ANDROID_ITEMS. The
 * column's row ids are "drawer_" + name.lowercase(), which is how a row round-trips back to the
 * filter it opens, so a new entry here reaches both surfaces with nothing else to remember.
 *
 * [APPS] is apps ONLY — not emulators, not games. It is the owner's distinction and the reason
 * this enum has five entries instead of four: [ALL] answers "everything installed", which is a
 * different question from "the things that are just apps".
 *
 * A single app can appear under more than one section, and that is deliberate: an emulator the
 * user has also marked as a game is listed under both Emulators and Games, because it genuinely
 * is both and hiding it from one of them would make that section a lie.
 */
enum class AppFilter(val label: String, val subtitle: String) {
    RECENT("Recently Used", "Apps you've used lately"),
    APPS("Apps", "Everything that is not a game or an emulator"),
    EMULATORS("Emulators", "RetroArch, PPSSPP, Dolphin and more"),
    GAMES("Games", "Apps categorized as games");

    /**
     * Whether [app] belongs in this section.
     *
     * ONE definition. The drawer filtered its grid with one `when` over this enum and counted its
     * tabs with a second copy of the same `when`, so a section could have shown a count that did
     * not match the list underneath it — and the count is the half nobody checks.
     */
    fun matches(app: InstalledApp): Boolean = when (this) {
        APPS -> !app.isGame && !app.isEmulator
        GAMES -> app.isGame
        EMULATORS -> app.isEmulator
        RECENT -> app.lastUsedAt > 0L
    }

    companion object {
        /**
         * Where the app menu opens when nothing has asked for a particular section.
         *
         * Named, because there were two places that decided it: this ViewModel's initial state
         * and XMBViewModel.onOpenAppDrawer, which passed the literal "ALL". Changing one left the
         * other winning — the B-press route opened on All Apps no matter what the state said.
         */
        val DEFAULT = RECENT
    }
}

// One row in an app's long-press mini menu.
enum class AppMenuAction(val label: String) {
    /**
     * Put this app on the cross bar, in the column the drawer was opened over.
     *
     * 6e binds this to X. X here is Search, and stays Search: it is the only controller route to
     * the search box, which the same pass made permanent — a pin that stranded the search field
     * would be trading one reachable thing for another. A menu action also lets the row NAME its
     * destination, and a pin whose destination you cannot predict is worse than one extra press.
     */
    ADD_TO_CROSS_BAR("Add to Cross Bar"),
    APP_INFO("App Info"),
    MARK_GAME("Mark as Game"),
    UNMARK_GAME("Unmark as Game"),
    UNINSTALL("Uninstall"),
}

data class AppDrawerUiState(
    val allApps: List<InstalledApp> = emptyList(),
    /**
     * The tab's OWN apps: the large row of the 8q body, and the whole grid on All Apps.
     */
    val sectionApps: List<InstalledApp> = emptyList(),
    /**
     * Every app the tab does NOT hold, for the compact list under the row. Empty on All Apps,
     * which has nothing to contrast against and draws the grid instead.
     */
    val otherApps: List<InstalledApp> = emptyList(),
    /**
     * Where the drawer opens: Recently Used.
     *
     * It opened on All Apps, which is 45 icons in alphabetical order — a list you have to read
     * rather than recognise. What someone opening an app menu on a handheld usually wants is the
     * thing they were using, and that section already has a real empty state ("Usage access
     * needed", with the grant prompt), so landing here is useful even when it is empty.
     */
    val activeFilter: AppFilter = AppFilter.DEFAULT,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val selectedIndex: Int = 0,
    // True while the user is browsing by touch: the grid cursor is hidden (fingers don't need
    // one) and the auto-scroll-to-selection effect is suppressed so it can't fight the finger.
    // Flips false on the first d-pad action, revealing the cursor at the last touch position.
    val usingTouch: Boolean = false,
    val hasUsageAccess: Boolean = false,
    // Long-press mini menu: the app it targets (null = closed) and the focused row.
    val menuApp: InstalledApp? = null,
    val menuIndex: Int = 0,
    // Uninstall guard rail: the app awaiting the in-app confirmation (null = no dialog).
    val confirmUninstall: InstalledApp? = null,
    /**
     * Which button the uninstall prompt's cursor is on. False is Cancel, and it opens there every
     * time: a destructive prompt never opens with the cursor on the destructive answer, so a
     * reflex press cancels rather than uninstalls.
     */
    val uninstallConfirmFocused: Boolean = false,
    // True when the menu app is marked as a game (an android-platform GAME row exists for it).
    val menuAppIsGame: Boolean = false,
    /** Per-filter app counts (unfiltered by search query) for the category rail. */
    val filterCounts: Map<AppFilter, Int> = emptyMap(),
    /**
     * How many rows the compact list was DRAWN with, reported by AppDrawerSection.
     *
     * The other half of a pair. The grid divides the panel's height by a row's height and this is
     * the answer, so `sectionMove` steps the cursor by a real column instead of by a constant.
     * Six was that constant, and it is still the value here for the window between construction
     * and the first measurement landing.
     */
    val sectionListRows: Int = SECTION_LIST_ROWS,
    /**
     * How many of [visibleApps] matched [activeFilter] — the rest are the complement below them.
     *
     * [visibleApps] is the tab's own apps followed by every app that is NOT in the tab, in that
     * order, so one flat cursor addresses both halves of the 8q body and Launch / the Y menu /
     * Add to Cross Bar keep reading `visibleApps[selectedIndex]` without knowing there are two.
     *
     * On All Apps the complement is empty, so this equals `visibleApps.size` and zero means the
     * drawer is empty in either view — which is why the empty-state gate reads this and not
     * `visibleApps.isEmpty()`.
     */
) {
    /**
     * Both halves as one list, the tab's own apps first.
     *
     * DERIVED, not stored beside them. The cursor is a single flat index into this, which is what
     * lets Launch, the Y menu and Add to Cross Bar keep reading `visibleApps[selectedIndex]`
     * without knowing the body has two shapes. Holding the two halves and a separate "where the
     * seam is" would be a pair that must agree with only one of them guarded — and a seam that
     * disagreed with its lists puts the cursor on a different app than the one it is drawn under.
     */
    val visibleApps: List<InstalledApp> get() = sectionApps + otherApps

    /** Where the seam falls in [visibleApps]. */
    val sectionRowCount: Int get() = sectionApps.size


    // Add to Cross Bar leads: it is the one action here that changes the screen you came from.
    // App Info for every app; Mark/Unmark as Game toggles library membership; Uninstall only
    // for non-system apps (guard rail).
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

    /** Touch tap on a grid tile: moves the (hidden) cursor there and enters touch mode. */
    /** [AppDrawerSection] measured the panel; keep the cursor's column height in step with it. */
    fun setSectionListRows(rows: Int) {
        if (rows > 0 && rows != _uiState.value.sectionListRows) {
            _uiState.update { it.copy(sectionListRows = rows) }
        }
    }

    fun onAppTapped(index: Int) {
        _uiState.update { it.copy(selectedIndex = index, usingTouch = true) }
    }

    /** Touch scroll settled: silently park the cursor on the tile nearest the viewport centre so a
     *  later switch to the d-pad starts where the finger left off. No sound — nothing visible moves. */
    fun onTouchBrowse(index: Int) {
        val size = _uiState.value.visibleApps.size
        if (size == 0) return
        _uiState.update { it.copy(selectedIndex = index.coerceIn(0, size - 1), usingTouch = true) }
    }

    fun launchApp(packageName: String) {
        // Fires for both controller SELECT and a touch tap on an app tile.
        menuSound.play(MenuSound.LAUNCH)
        // Behind the disc, like everything else that opens. The icon is the Drawable already in
        // hand from the tile — the drawer never had a uri for it, which is why the gate takes Any?.
        val icon = _uiState.value.visibleApps.firstOrNull { it.packageName == packageName }?.icon
        viewModelScope.launch {
            mediaLaunchGate.awaitHandOff(icon)
            appRepository.launchApp(packageName)
        }
    }

    fun refresh() {
        loadApps()
    }

    // ── Long-press mini menu ────────────────────────────────────────────────────

    fun openAppMenu(app: InstalledApp) {
        menuSound.play(MenuSound.SELECT)
        _uiState.update { it.copy(menuApp = app, menuIndex = 0, menuAppIsGame = false) }
        // Resolve the Mark/Unmark row async; the menu is already visible.
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

    /** Opens the menu for the currently-focused grid app (controller hold). */
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
            // Guard rail: show an in-app confirmation before the system uninstall flow.
            AppMenuAction.UNINSTALL -> _uiState.update { it.copy(menuApp = null, confirmUninstall = app, uninstallConfirmFocused = false) }
            // Answered by the HOST, not here. The destination is "the column the drawer is open
            // over", and the drawer does not know which that is — it is handed a filter, not a
            // category. AppDrawerScreen routes it out; this only closes the menu.
            AppMenuAction.ADD_TO_CROSS_BAR -> _uiState.update { it.copy(menuApp = null) }
        }
    }

    // Marking puts the app in the Android Memory Card as a real game (counts in All Games, joins
    // gaming categories); unmarking demotes it to a decoration row (app_shortcut sentinel) so its
    // artwork, favorites and collection memberships survive a later re-mark.
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
        // The app list refreshes on ON_RESUME when the user returns from the uninstall dialog.
    }

    fun cancelUninstall() = _uiState.update { it.copy(confirmUninstall = null, uninstallConfirmFocused = false) }

    fun openUsageAccessSettings() {
        appRepository.openUsageAccessSettings()
    }

    fun handleGamepadAction(action: GamepadAction) {
        val state = _uiState.value

        // Uninstall confirmation captures input first.
        //
        // This branch was written correct and was unreachable: the prompt was a Material3
        // AlertDialog, which draws into its own platform Window, so dispatchKeyEvent never ran and
        // nothing here ever fired. Now that it is drawn in the launcher's own window the rule has
        // to be a real two-button one rather than "SELECT confirms, anything else cancels" — with
        // a cursor on screen, a D-pad press that silently cancelled would be a trap.
        state.confirmUninstall?.let {
            when (action) {
                GamepadAction.SELECT ->
                    if (state.uninstallConfirmFocused) confirmUninstall() else cancelUninstall()
                GamepadAction.NAVIGATE_UP, GamepadAction.NAVIGATE_DOWN ->
                    _uiState.update { s -> s.copy(uninstallConfirmFocused = !s.uninstallConfirmFocused) }
                GamepadAction.BACK -> cancelUninstall()
                // Everything else is swallowed rather than falling through to the list behind.
                else -> Unit
            }
            return
        }

        // Mini menu captures input while open.
        state.menuApp?.let {
            val actions = state.menuActions
            // Empty menuActions would make the modulo below divide by zero — park the cursor.
            if (actions.isEmpty()) return
            when (action) {
                GamepadAction.NAVIGATE_UP   -> _uiState.update { s -> s.copy(menuIndex = (s.menuIndex - 1 + actions.size) % actions.size) }
                GamepadAction.NAVIGATE_DOWN -> _uiState.update { s -> s.copy(menuIndex = (s.menuIndex + 1) % actions.size) }
                GamepadAction.SELECT        -> onMenuAction(actions[state.menuIndex.coerceIn(0, actions.size - 1)])
                // The options module holds controller focus while it's up; BACK is its close
                // gesture and only pops the menu — XMBViewModel forwards BACK here, so it never
                // closes the drawer while the menu is open (the drawer only closes on a BACK on
                // the plain grid). Hold/Y and X dismiss the menu too, as before.
                GamepadAction.BACK               -> closeAppMenu()
                GamepadAction.OPEN_CONTEXT_MENU  -> closeAppMenu()
                GamepadAction.CHANGE_SORT        -> closeAppMenu()
                else -> Unit
            }
            return
        }

        // L1/R1 — cycle through the filter tabs. Checked BEFORE the empty-grid guard on purpose:
        // a filter with zero apps (Recently Used before usage access is granted, or any list
        // emptied by a search) must never strand the cursor — category cycling always works, so
        // the user can always move out of an empty section.
        if (action == GamepadAction.PREV_CATEGORY || action == GamepadAction.NEXT_CATEGORY) {
            val filters = AppFilter.values()
            val idx = filters.indexOf(state.activeFilter)
            val target = if (action == GamepadAction.PREV_CATEGORY) idx - 1 else idx + 1
            if (target in filters.indices) setFilter(filters[target])
            return
        }

        val size  = state.visibleApps.size
        if (size == 0) return
        // Controller input ends touch mode: the cursor appears at the position the last touch
        // browse/tap parked it, and navigation continues from there.
        if (state.usingTouch) _uiState.update { it.copy(usingTouch = false) }
        val cur = state.selectedIndex
        when (action) {
            // Hold a button to open the focused app's mini menu (controller equivalent of long-press).
            GamepadAction.OPEN_CONTEXT_MENU -> openAppMenuForSelected()
            GamepadAction.NAVIGATE_LEFT, GamepadAction.NAVIGATE_RIGHT,
            GamepadAction.NAVIGATE_UP, GamepadAction.NAVIGATE_DOWN -> {
                val next = sectionMove(action, cur, state.sectionRowCount, size, state.sectionListRows)
                // The sound follows the move, not the press: a refused move at an edge is silent,
                // which is how the grid behaved when each direction guarded itself.
                if (next != cur) {
                    _uiState.update { it.copy(selectedIndex = next) }
                    menuSound.play(MenuSound.SCROLL)
                }
            }
            GamepadAction.SELECT -> {
                val app = state.visibleApps.getOrNull(cur)
                if (app != null) launchApp(app.packageName)
            }
            // (L1/R1 category cycling is handled above the empty-grid guard.)
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

        // Compute per-filter counts (unfiltered by search query) for the category rail.
        val counts = AppFilter.values().associateWith { filter ->
            state.allApps.count { app ->
                filter.matches(app)
            }
        }

        // Everything the tab does NOT hold, for the compact list under the row. From the negation
        // of the same predicate, never a second list of its own: one rule decides both halves, so
        // an app cannot appear twice or fall between them — which is also what makes every app
        // reachable now that there is no All Apps tab to fall back on.
        val rest = state.allApps
            .filter { app -> !state.activeFilter.matches(app) }
            .filter { app -> query.isEmpty() || app.label.lowercase().contains(query) }

        _uiState.update { it.copy(sectionApps = filtered, otherApps = rest, filterCounts = counts) }
    }
}
