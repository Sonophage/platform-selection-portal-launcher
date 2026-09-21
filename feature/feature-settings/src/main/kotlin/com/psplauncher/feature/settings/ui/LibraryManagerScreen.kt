package com.psplauncher.feature.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.detail.PfpOverlayCard
import com.psplauncher.core.ui.detail.PfpOverlayTitle
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.preview.CombinedPreviews
import com.psplauncher.core.ui.preview.PfpPreview
import com.psplauncher.feature.launcher.PcLauncherAdapters
import com.psplauncher.feature.settings.viewmodel.ADD_CONSOLE_FOCUS_KEY
import com.psplauncher.feature.settings.viewmodel.EmulatorOption
import com.psplauncher.feature.settings.viewmodel.IMPORT_PC_FOCUS_KEY
import com.psplauncher.feature.settings.viewmodel.LibraryCardRow
import com.psplauncher.feature.settings.viewmodel.LibraryManagerUiState
import com.psplauncher.feature.settings.viewmodel.LibraryManagerViewModel
import com.psplauncher.feature.settings.viewmodel.LibraryStep
import com.psplauncher.feature.settings.viewmodel.PcGameRow
import com.psplauncher.feature.settings.viewmodel.PcLauncherRow
import com.psplauncher.feature.settings.viewmodel.PlatformOption
import com.psplauncher.feature.settings.viewmodel.RootFolderRow

@Composable
fun LibraryManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddAndroidApps: () -> Unit = {},
    // Open directly into the standalone Windows Games screen.
    startInImportPc: Boolean = false,
    startAtWindowsCard: Boolean = false,
    viewModel: LibraryManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(startInImportPc, startAtWindowsCard) {
        when {
            startInImportPc -> viewModel.openImportPcGames()
            startAtWindowsCard -> viewModel.openWindowsGamesRoot()
        }
    }

    // Picker for ES-DE folder setup: creates the system-folder structure under the
    // chosen folder (which also becomes the ROM Root).
    val setupPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> viewModel.onRomFolderSetupPicked(uri) }

    LaunchedEffect(state.awaitingRomRootSetup) {
        if (state.awaitingRomRootSetup) setupPicker.launch(null)
    }

    LibraryManagerContent(
        state = state,
        onBack = { if (!viewModel.onBack()) onBack() },
        onAddAndroidApps = onAddAndroidApps,
        onAddRomRoot = { it?.let { viewModel.addRomRoot(it) } },
        onRelinkRomRoot = { _, uri ->
            if (uri != null) viewModel.onRomRootRelinkPicked(uri)
        },
        onBeginRelink = { viewModel.beginRelinkRomRoot(it.treeUri) ?: Uri.EMPTY },
        onRemoveRomRoot = { viewModel.removeRomRoot(it.treeUri) },
        onScanRomRoot = { viewModel.scanRomRoot() },
        onOpenCardDetail = { viewModel.openCardDetail(it) },
        onStartAddConsole = { viewModel.startAddConsole() },
        onRequestRomFolderSetup = { viewModel.requestRomFolderSetup() },
        onScanAllConsoles = { viewModel.scanAllConsoles(it) },
        onDismissMessage = { viewModel.dismissMessage() },
        onPlatformChosen = { viewModel.onPlatformChosen(it) },
        onEmulatorChosen = { viewModel.onEmulatorChosen(it) },
        onConfirmAddConsole = { viewModel.confirmAddConsole(it) },
        onLoadEmulatorOptions = { viewModel.loadEmulatorOptionsForDetail() },
        onRemoveExtension = { p, e -> viewModel.removeExtension(p, e) },
        onAddExtension = { p, e -> viewModel.addExtension(p, e) },
        onScanConsole = { viewModel.scanConsole(it) },
        onBeginRename = { viewModel.beginRename(it) },
        onCancelRename = { viewModel.cancelRename() },
        onConfirmRename = { viewModel.confirmRename(it) },
        onToggleEnabled = { p, e -> viewModel.toggleEnabled(p, e) },
        onTogglePinned = { p, pin -> viewModel.togglePinned(p, pin) },
        onMoveCard = { p, up -> viewModel.moveCard(p, up) },
        onRemoveCard = { viewModel.removeCard(it) },
        onSetEmulatorForDetail = { viewModel.setEmulatorForDetail(it) },
        onOpenImportPcGames = { viewModel.openImportPcGames() },
        onSetVita3KFolder = { viewModel.setVita3KFolder(it) },
        onScanVitaGames = { viewModel.scanVitaGames() },
        onRemoveApp = { viewModel.removeApp(it) },
        onRefreshHomeStatus = { viewModel.refreshHomeStatus() },
        onScanPcGamesFolder = { viewModel.scanPcGamesFolder(it) },
        onExportManualPcGames = { viewModel.exportManualPcGames() },
        onImportPcGame = { viewModel.importPcGame(it) },
        onImportAllPcGames = { viewModel.importAllPcGames() },
        onTestLaunchPcGame = { l, id, s -> viewModel.testLaunchPcGame(l, id, s) },
        onAddPcGameById = { l, id, t, s -> viewModel.addPcGameById(l, id, t, s) },
        homeRoleIntentProvider = { viewModel.homeRoleIntent() },
        modifier = modifier
    )
}

@Composable
private fun LibraryManagerContent(
    state: LibraryManagerUiState,
    onBack: () -> Unit,
    onAddAndroidApps: () -> Unit,
    onAddRomRoot: (Uri?) -> Unit,
    onRelinkRomRoot: (RootFolderRow, Uri?) -> Unit,
    onBeginRelink: (RootFolderRow) -> Uri,
    onRemoveRomRoot: (RootFolderRow) -> Unit,
    onScanRomRoot: () -> Unit,
    onOpenCardDetail: (String) -> Unit,
    onStartAddConsole: () -> Unit,
    onRequestRomFolderSetup: () -> Unit,
    onScanAllConsoles: (removeMissing: Boolean) -> Unit,
    onDismissMessage: () -> Unit,
    onPlatformChosen: (PlatformOption) -> Unit,
    onEmulatorChosen: (EmulatorOption) -> Unit,
    onConfirmAddConsole: (scanNow: Boolean) -> Unit,
    onLoadEmulatorOptions: () -> Unit,
    onRemoveExtension: (platformId: String, ext: String) -> Unit,
    onAddExtension: (platformId: String, ext: String) -> Unit,
    onScanConsole: (platformId: String) -> Unit,
    onBeginRename: (platformId: String) -> Unit,
    onCancelRename: () -> Unit,
    onConfirmRename: (String) -> Unit,
    onToggleEnabled: (platformId: String, enabled: Boolean) -> Unit,
    onTogglePinned: (platformId: String, pinned: Boolean) -> Unit,
    onMoveCard: (platformId: String, up: Boolean) -> Unit,
    onRemoveCard: (platformId: String) -> Unit,
    onSetEmulatorForDetail: (EmulatorOption) -> Unit,
    onOpenImportPcGames: () -> Unit,
    onSetVita3KFolder: (Uri) -> Unit,
    onScanVitaGames: () -> Unit,
    onRemoveApp: (Long) -> Unit,
    onRefreshHomeStatus: () -> Unit,
    onScanPcGamesFolder: (Uri) -> Unit,
    onExportManualPcGames: () -> Unit,
    onImportPcGame: (PcGameRow) -> Unit,
    onImportAllPcGames: () -> Unit,
    onTestLaunchPcGame: (PcLauncherRow, String, String?) -> Unit,
    onAddPcGameById: (PcLauncherRow, String, String, String?) -> Unit,
    homeRoleIntentProvider: () -> android.content.Intent?,
    modifier: Modifier = Modifier,
) {
    val handleBack: () -> Unit = onBack

    when (state.step) {
        LibraryStep.LIST          -> LibraryListContent(state, handleBack, onAddRomRoot, onRelinkRomRoot, onBeginRelink, onRemoveRomRoot, onScanRomRoot, onOpenCardDetail, onStartAddConsole, onRequestRomFolderSetup, onScanAllConsoles, onDismissMessage, modifier)
        LibraryStep.PICK_PLATFORM -> PickPlatformContent(state, onBack = handleBack, onPlatformChosen = onPlatformChosen, modifier = modifier)
        LibraryStep.PICK_EMULATOR -> PickEmulatorContent(state, onBack = handleBack, onEmulatorChosen = onEmulatorChosen, modifier = modifier)
        LibraryStep.SCAN_PROMPT   -> ScanPromptContent(state, onBack = handleBack, onConfirmAddConsole = onConfirmAddConsole, modifier = modifier)
        LibraryStep.CARD_DETAIL   -> CardDetailContent(state, onBack = handleBack, onAddAndroidApps = onAddAndroidApps, onLoadEmulatorOptions = onLoadEmulatorOptions, onRemoveExtension = onRemoveExtension, onAddExtension = onAddExtension, onScanConsole = onScanConsole, onBeginRename = onBeginRename, onToggleEnabled = onToggleEnabled, onTogglePinned = onTogglePinned, onMoveCard = onMoveCard, onRemoveCard = onRemoveCard, onSetEmulatorForDetail = onSetEmulatorForDetail, onOpenImportPcGames = onOpenImportPcGames, onSetVita3KFolder = onSetVita3KFolder, onScanVitaGames = onScanVitaGames, onRemoveApp = onRemoveApp, modifier = modifier)
        LibraryStep.IMPORT_PC     -> ImportPcGamesContent(state, onBack = handleBack, onRefreshHomeStatus = onRefreshHomeStatus, onScanPcGamesFolder = onScanPcGamesFolder, onExportManualPcGames = onExportManualPcGames, onImportPcGame = onImportPcGame, onImportAllPcGames = onImportAllPcGames, onTestLaunchPcGame = onTestLaunchPcGame, onAddPcGameById = onAddPcGameById, onDismissMessage = onDismissMessage, homeRoleIntentProvider = homeRoleIntentProvider, modifier = modifier)
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────
    state.renameTargetPlatformId?.let { targetId ->
        val current = state.cards.firstOrNull { it.platformId == targetId }?.displayName ?: ""
        var text by remember(targetId) { mutableStateOf(current) }
        SettingsTextPromptOverlay(
            title = "Rename Memory Card",
            value = text,
            onValueChange = { text = it },
            onConfirm = { onConfirmRename(text) },
            onCancel = onCancelRename,
        )
    }
}

// ── LIST ──────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryListContent(
    state: LibraryManagerUiState,
    onBack: () -> Unit,
    onAddRomRoot: (Uri?) -> Unit,
    onRelinkRomRoot: (RootFolderRow, Uri?) -> Unit,
    onBeginRelink: (RootFolderRow) -> Uri,
    onRemoveRomRoot: (RootFolderRow) -> Unit,
    onScanRomRoot: () -> Unit,
    onOpenCardDetail: (String) -> Unit,
    onStartAddConsole: () -> Unit,
    onRequestRomFolderSetup: () -> Unit,
    onScanAllConsoles: (removeMissing: Boolean) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier,
) {
    val addRootPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> onAddRomRoot(uri) }
    
    var relinkTarget by remember { mutableStateOf<RootFolderRow?>(null) }
    val relinkRootPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> relinkTarget?.let { onRelinkRomRoot(it, uri) }; relinkTarget = null }

    SettingsScaffold(
        title = "Settings",
        subtitle = "Library Manager",
        onBack = onBack,
        modifier = modifier,
        restoreFocusKey = state.returnFocusKey,
    ) {
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {

            // ── ROM Root Access ─────────────────────────────────────────────────
            RootAccessSection(
                groupTitle  = "ROM Root Access",
                roots       = state.romRoots,
                addLabel    = "Add ROM Root",
                addSublabel = "Grant a root folder (e.g. /Roms) — or a second location like an SD card",
                onAddRoot    = { addRootPicker.launch(null) },
                onRelinkRoot = { 
                    relinkTarget = it
                    relinkRootPicker.launch(onBeginRelink(it)) 
                },
                onRemoveRoot = { onRemoveRomRoot(it) },
            )

            SettingsGroup("Consoles")

            val consoleCards = state.cards.filterNot { it.platformId == "windows" }
            if (consoleCards.isEmpty()) {
                Hint("No consoles configured. Add a console to create a Memory Card that appears inside Games.")
            } else {
                consoleCards.forEach { card ->
                    SettingsRow(
                        label    = card.displayName + if (!card.enabled) "  (Hidden)" else "",
                        sublabel = cardSublabel(card),
                        focusKey = card.platformId,
                        trailing = { if (card.pinned) Text("PINNED", color = SettingsAccent) },
                        onClick  = { onOpenCardDetail(card.platformId) },
                    )
                }
            }

            SettingsGroup("Manage")

            SettingsRow(
                label    = "Add Console",
                sublabel = "Pick a platform — its games live in the matching folder under your ROM Root",
                focusKey = ADD_CONSOLE_FOCUS_KEY,
                onClick  = { onStartAddConsole() },
            )
            SettingsRow(
                label    = "Set Up ROM Folders (ES-DE)",
                sublabel = "Pick an empty folder — PFP creates the standard ES-DE system folders " +
                    "(gba, snes, psx…) for you to copy games into. No guessing folder names",
                onClick  = { onRequestRomFolderSetup() },
            )
            val anyScannable = state.cards.any { it.enabled && (it.treeUri != null || it.romDirectory != null) }
            SettingsRow(
                label    = "Scan All Consoles",
                sublabel = when {
                    state.scanningPlatformIds.isNotEmpty() -> "Scanning ${state.scanningPlatformIds.size}…"
                    state.cards.none { it.treeUri != null || it.romDirectory != null } -> "Configure a ROM folder first"
                    else -> "Scan every enabled console's folder"
                },
                onClick  = if (anyScannable) ({ onScanAllConsoles(false) }) else null,
            )
            var confirmRescanAll by remember { mutableStateOf(false) }
            if (!confirmRescanAll) {
                SettingsRow(
                    label    = "Re-Scan All (Remove Missing)",
                    sublabel = "Also removes games whose ROM file no longer exists",
                    onClick  = if (anyScannable) ({ confirmRescanAll = true }) else null,
                )
            } else {
                SettingsRow(
                    label    = "Confirm: Re-Scan and Remove Missing Games?",
                    sublabel = "Removes library entries whose ROM file is gone. This can take a while with a large library",
                    onClick  = {
                        confirmRescanAll = false
                        onScanAllConsoles(true)
                    },
                )
                SettingsRow(
                    label   = "Cancel",
                    onClick = { confirmRescanAll = false },
                )
            }

            state.message?.let { MessageRow(it) { onDismissMessage() } }
        }
    }
}

// ── PICK PLATFORM ───────────────────────────────────────────────────────────────

@Composable
private fun PickPlatformContent(
    state: LibraryManagerUiState,
    onBack: () -> Unit,
    onPlatformChosen: (PlatformOption) -> Unit,
    modifier: Modifier,
) {
    SettingsScaffold(title = "Add Console", subtitle = "Choose Platform", onBack = onBack, modifier = modifier) {
        // Registered like the list screens: the scaffold needs a scroll owner here for its
        // chrome drag-to-scroll and for controller keep-in-view. Registering is the whole fix;
        // the body itself is unchanged.
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            SettingsGroup("Supported Platforms")
            state.platformOptions.forEach { option ->
                SettingsRow(
                    label    = option.name,
                    sublabel = option.shortName,
                    onClick  = { onPlatformChosen(option) },
                )
            }
        }
    }
}

// ── PICK EMULATOR ───────────────────────────────────────────────────────────────

@Composable
private fun PickEmulatorContent(
    state: LibraryManagerUiState,
    onBack: () -> Unit,
    onEmulatorChosen: (EmulatorOption) -> Unit,
    modifier: Modifier,
) {
    SettingsScaffold(title = "Add Console", subtitle = "Assign Emulator", onBack = onBack, modifier = modifier) {
        // Registered like the list screens: the scaffold needs a scroll owner here for its
        // chrome drag-to-scroll and for controller keep-in-view. Registering is the whole fix;
        // the body itself is unchanged.
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            SettingsGroup(state.pendingPlatformName ?: "Emulator")
            if (state.emulatorOptions.all { it.id == null }) {
                Hint("No installed emulators detected for this platform. You can assign one later from the console's detail screen.")
            }
            state.emulatorOptions.forEach { option ->
                SettingsRow(label = option.name, onClick = { onEmulatorChosen(option) })
            }
        }
    }
}

// ── SCAN PROMPT ─────────────────────────────────────────────────────────────────

@Composable
private fun ScanPromptContent(
    state: LibraryManagerUiState,
    onBack: () -> Unit,
    onConfirmAddConsole: (Boolean) -> Unit,
    modifier: Modifier,
) {
    SettingsScaffold(title = "Add Console", subtitle = "Scan Now?", onBack = onBack, modifier = modifier) {
        // Registered like the list screens: the scaffold needs a scroll owner here for its
        // chrome drag-to-scroll and for controller keep-in-view. Registering is the whole fix;
        // the body itself is unchanged.
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            SettingsGroup(state.pendingPlatformName ?: "New Console")
            SettingsValueRow(
                label = "ROM Directory",
                value = state.pendingDirectory?.substringAfterLast('/') ?: "Not set",
                sublabel = state.pendingDirectory,
            )
            SettingsRow(
                label    = "Scan Now",
                sublabel = "Create the Memory Card and scan its folder immediately",
                onClick  = { onConfirmAddConsole(true) },
            )
            SettingsRow(
                label    = "Add Without Scanning",
                sublabel = "Create the Memory Card now, scan later",
                onClick  = { onConfirmAddConsole(false) },
            )
        }
    }
}

// ── CARD DETAIL ─────────────────────────────────────────────────────────────────

@Composable
private fun CardDetailContent(
    state: LibraryManagerUiState,
    onBack: () -> Unit,
    onAddAndroidApps: () -> Unit,
    onLoadEmulatorOptions: () -> Unit,
    onRemoveExtension: (String, String) -> Unit,
    onAddExtension: (String, String) -> Unit,
    onScanConsole: (String) -> Unit,
    onBeginRename: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onTogglePinned: (String, Boolean) -> Unit,
    onMoveCard: (String, Boolean) -> Unit,
    onRemoveCard: (String) -> Unit,
    onSetEmulatorForDetail: (EmulatorOption) -> Unit,
    onOpenImportPcGames: () -> Unit,
    onSetVita3KFolder: (Uri) -> Unit,
    onScanVitaGames: () -> Unit,
    onRemoveApp: (Long) -> Unit,
    modifier: Modifier,
) {
    // No card for this platform. It happens for real: Settings > Windows Games opens this
    // directly, and the Windows card only exists once a PC game has been imported. It used to
    // `return` here, which drew NOTHING -- no header, no rail, no Back, just the wallpaper, with
    // no way to tell a missing console from a broken screen.
    val card = state.detailCard
    if (card == null) {
        SettingsScaffold(
            title = "Library Manager",
            subtitle = "Not in your library",
            onBack = onBack,
            modifier = modifier,
        ) {
            val scrollState = rememberScrollState()
            LocalSettingsScrollStateRegistrar.current(scrollState)
            Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                // The explanation is a row's LABEL, not its sublabel: a sublabel feeds the
                // focused-row help band at the bottom, which is empty until something takes
                // focus -- and on a screen whose entire point is that it has nothing, that is
                // exactly when the user needs to be told why.
                SettingsGroup("Nothing here yet")
                SettingsValueRow(label = "No console to show", value = "")
                SettingsValueRow(
                    label = "Windows Games appears once a PC game has been imported.",
                    value = "",
                )
                SettingsValueRow(
                    label = "Other consoles appear once you add them in Library Manager.",
                    value = "",
                )
            }
        }
        return
    }

    var showEmulatorDialog by remember { mutableStateOf(false) }
    var showRemoveConfirm  by remember { mutableStateOf(false) }
    var newExt             by remember(card.platformId) { mutableStateOf("") }
    val isScanning = card.platformId in state.scanningPlatformIds
    val isAndroid = card.platformId == "android"
    val isWindows = card.platformId == "windows"
    val isVita    = card.platformId == "psvita"
    val vitaFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { onSetVita3KFolder(it) } }

    SettingsScaffold(title = "Library Manager", subtitle = card.displayName, onBack = onBack, modifier = modifier) {
        // Registered like the list screens: the scaffold needs a scroll owner here for its
        // chrome drag-to-scroll and for controller keep-in-view. Registering is the whole fix;
        // the body itself is unchanged.
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {

            if (isWindows) {
                SettingsGroup("Library")
                SettingsValueRow(
                    label    = "Games Directory",
                    value    = card.romDirectory?.substringAfterLast('/') ?: "Not set",
                    sublabel = card.romDirectory
                        ?: "Add a ROM Root — PFP creates and uses <root>/windows automatically",
                )
                SettingsValueRow(label = "Games", value = card.gameCount.toString())

                SettingsGroup("Actions")
                SettingsRow(
                    label    = "Import PC Games",
                    sublabel = "Exported games, Add by ID, and launcher status",
                    focusKey = IMPORT_PC_FOCUS_KEY,
                    onClick  = onOpenImportPcGames,
                )
            } else if (isVita) {
                SettingsGroup("Library")
                SettingsValueRow(
                    label    = "Vita3K Data Folder",
                    value    = state.vita3KFolderLabel ?: "Not set",
                    sublabel = state.vita3KFolderLabel
                        ?.let { "Reading installed titles from this ux0 folder" }
                        ?: "Pick your Vita3K ux0 folder (e.g. Roms/vita/ux0) so PFP can find games",
                    onClick  = { vitaFolderPicker.launch(null) },
                )
                SettingsValueRow(label = "Games", value = card.gameCount.toString())

                SettingsGroup("Actions")
                SettingsRow(
                    label    = "Scan For Vita Games",
                    sublabel = "Reads installed titles from ux0/app in your Vita3K data folder",
                    onClick  = if (!isScanning && state.vita3KFolderLabel != null) ({ onScanVitaGames() }) else null,
                )
            } else if (isAndroid) {
                SettingsGroup("Apps")
                SettingsRow(
                    label    = "Add Apps",
                    sublabel = "Pick installed apps to add to this library",
                    onClick  = onAddAndroidApps,
                )
                if (state.androidApps.isEmpty()) {
                    Hint("No apps yet — use Add Apps to pick installed apps for this library.")
                } else {
                    state.androidApps.forEach { app ->
                        SettingsRow(
                            label    = app.label,
                            trailing = { Text("Remove", color = SettingsAccent) },
                            onClick  = { onRemoveApp(app.gameId) },
                        )
                    }
                }
            } else {
                SettingsGroup("Library")
                SettingsValueRow(
                    label    = "ROM Directory",
                    value    = card.romDirectory?.substringAfterLast('/') ?: "Not set",
                    sublabel = card.romDirectory
                        ?: "Scans this console's folder under your ROM Root",
                )
                SettingsValueRow(
                    label   = "Emulator",
                    value   = card.emulatorName ?: "None",
                    onClick = { onLoadEmulatorOptions(); showEmulatorDialog = true },
                )
                SettingsValueRow(label = "Games", value = card.gameCount.toString())

                SettingsGroup("Supported Files")
                if (card.extensions.isEmpty()) {
                    Hint("No extensions set — add at least one so scanning can match this console's ROMs.")
                } else {
                    card.extensions.forEach { ext ->
                        SettingsRow(
                            label    = ".$ext",
                            trailing = { Text("Remove", color = SettingsAccent) },
                            onClick  = { onRemoveExtension(card.platformId, ext) },
                        )
                    }
                }
                SettingsTextFieldRow(
                    label         = "Add Extension",
                    value         = newExt,
                    onValueChange = { newExt = it },
                    placeholder   = "e.g. iso, chd, zip",
                    helper        = "Matched case-insensitively when scanning.",
                    helperPrompt  = ControllerPromptItem(GamepadAction.SELECT, "Type"),
                )
                newExt.trim().lowercase().removePrefix(".").filter { it.isLetterOrDigit() }
                    .takeIf { it.isNotBlank() }
                    ?.let { clean ->
                        SettingsRow(
                            label   = "Add \".$clean\"",
                            onClick = { onAddExtension(card.platformId, newExt); newExt = "" },
                        )
                    }

                SettingsGroup("Actions")
                SettingsRow(
                    label    = "Scan This Console",
                    sublabel = when {
                        isScanning -> "Scanning…"
                        card.romDirectory == null -> "ROM directory not configured"
                        else -> "Scan only this console's folder"
                    },
                    onClick  = if (!isScanning && card.romDirectory != null) ({ onScanConsole(card.platformId) }) else null,
                )
            }

            if (isAndroid) SettingsGroup("Actions")
            SettingsRow(label = "Rename Memory Card", onClick = { onBeginRename(card.platformId) })
            SettingsToggleRow(
                label    = "Show In Games",
                sublabel = "Enable or hide this Memory Card",
                checked  = card.enabled,
                onToggle = { onToggleEnabled(card.platformId, it) },
            )
            SettingsToggleRow(
                label    = "Pin To Top",
                checked  = card.pinned,
                onToggle = { onTogglePinned(card.platformId, it) },
            )
            SettingsRow(label = "Move Up",   onClick = { onMoveCard(card.platformId, true) })
            SettingsRow(label = "Move Down", onClick = { onMoveCard(card.platformId, false) })

            // The Windows Memory Card is managed by the PC import system and cannot be removed.
            if (!isWindows) {
                SettingsGroup("Danger Zone")
                SettingsRow(
                    label    = "Remove Memory Card",
                    sublabel = "Removes this console and its games. ROM files are not deleted.",
                    trailing = { Text("Remove", color = SettingsAccent) },
                    onClick  = { showRemoveConfirm = true },
                )
            }
        }
    }

    if (showEmulatorDialog) {
        EmulatorPickerDialog(
            options    = state.emulatorOptions,
            onSelect   = { onSetEmulatorForDetail(it); showEmulatorDialog = false },
            onDismiss  = { showEmulatorDialog = false },
        )
    }

    if (showRemoveConfirm) {
        SettingsConfirmOverlay(
            title = "Remove ${card.displayName}?",
            message = "This removes the console and its scanned games from the library. " +
                "ROM files on disk are not deleted.",
            confirmLabel = "Remove",
            onConfirm = { showRemoveConfirm = false; onRemoveCard(card.platformId) },
            onCancel = { showRemoveConfirm = false },
        )
    }
}

@Composable
private fun EmulatorPickerDialog(
    options: List<EmulatorOption>,
    onSelect: (EmulatorOption) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsChoiceOverlay(
        title = "Set Emulator",
        options = options.map { it.name },
        // Nothing is pre-chosen here: the row that opens this already shows the current
        // emulator, and marking one as selected would claim a choice the caller has not made.
        selectedIndex = -1,
        onPick = { onSelect(options[it]) },
        onCancel = onDismiss,
    )
}

// ── IMPORT PC GAMES ─────────────────────────────────────────────────────────────

@Composable
private fun ImportPcGamesContent(
    state: LibraryManagerUiState,
    onBack: () -> Unit,
    onRefreshHomeStatus: () -> Unit,
    onScanPcGamesFolder: (Uri) -> Unit,
    onExportManualPcGames: () -> Unit,
    onImportPcGame: (PcGameRow) -> Unit,
    onImportAllPcGames: () -> Unit,
    onTestLaunchPcGame: (PcLauncherRow, String, String?) -> Unit,
    onAddPcGameById: (PcLauncherRow, String, String, String?) -> Unit,
    onDismissMessage: () -> Unit,
    homeRoleIntentProvider: () -> android.content.Intent?,
    modifier: Modifier,
) {
    var addTarget by remember { mutableStateOf<PcLauncherRow?>(null) }

    // Picking a folder IS the scan trigger: on pick, scan that folder for exports one-shot.
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { onScanPcGamesFolder(it) } }

    val homeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onRefreshHomeStatus() }

    SettingsScaffold(title = "Library", subtitle = "Import PC Games", onBack = onBack, modifier = modifier) {
        // Registered like the list screens: the scaffold needs a scroll owner here for its
        // chrome drag-to-scroll and for controller keep-in-view. Registering is the whole fix;
        // the body itself is unchanged.
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {

            state.message?.let { MessageRow(it) { onDismissMessage() } }

            SettingsGroup("Add To Home Capture")
            SettingsValueRow(
                label    = "PSPLauncher as Home",
                value    = if (state.isHomeLauncher) "Active" else "Set…",
                sublabel = if (state.isHomeLauncher)
                    "Using \"Add to home\" inside a supported launcher imports the game here automatically"
                else
                    "Set PFP as your Home app so a launcher's \"Add to home\" option imports the game into PFP",
                onClick  = { runCatching { homeLauncher.launch(homeRoleIntentProvider()) } },
            )

            SettingsGroup("Exported Games")
            SettingsRow(
                label    = "Scan Import Folder",
                sublabel = "Pick the folder your launcher exports to — PFP scans it for GameNative / " +
                    "Winlator exports (.steam · .epic · .gog · .amazon · .pcgame · .desktop) and PFP's own " +
                    ".pfpgame exports, and imports them",
                onClick  = { importPicker.launch(null) },
            )
            SettingsRow(
                label    = "Export Manual Games",
                sublabel = "Writes a .pfpgame file into <windows>/import for each game added by ID or captured, " +
                    "and each pin with artwork, so a fresh install can bring them back with their artwork",
                onClick  = onExportManualPcGames,
            )

            SettingsGroup("PC Launchers")
            state.pcLaunchers.forEach { launcher ->
                when {
                    !launcher.installed -> SettingsValueRow(label = launcher.name, value = "Not installed")
                    launcher.canAddById -> SettingsValueRow(
                        label    = launcher.name,
                        value    = "Add by ID…",
                        sublabel = "Add a game by its ID — or use the app's own Add-to-home option",
                        onClick  = { addTarget = launcher },
                    )
                    else -> SettingsValueRow(
                        label    = launcher.name,
                        value    = "Installed",
                        sublabel = "No add-by-ID support — export the game to <windows>/import instead",
                    )
                }
            }

            SettingsGroup("Found Games (${state.pcGames.size})")
            if (state.pcGames.isEmpty()) {
                Hint("No PC games captured yet.")
            } else {
                state.pcGames.forEach { row ->
                    SettingsValueRow(
                        label    = row.title,
                        sublabel = row.launcherName,
                        value    = "Import",
                        onClick  = { onImportPcGame(row) },
                    )
                }
                SettingsRow(
                    label    = "Import All",
                    sublabel = "Add every found game to a collection named after its launcher",
                    onClick  = onImportAllPcGames,
                )
            }
        }
    }

    addTarget?.let { launcher ->
        AddPcGameDialog(
            launcher = launcher,
            onTest   = { id, source -> onTestLaunchPcGame(launcher, id, source) },
            onAdd    = { id, title, source -> onAddPcGameById(launcher, id, title, source); addTarget = null },
            onDismiss = { addTarget = null },
        )
    }
}

@Composable
private fun AddPcGameDialog(
    launcher: PcLauncherRow,
    onTest: (id: String, source: String?) -> Unit,
    onAdd: (id: String, title: String, source: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val adapter = PcLauncherAdapters.forType(launcher.type)
    var id by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(adapter?.sources?.firstOrNull()) }

    // Two fields and a source row: not one of the shared shapes, so it uses the card directly.
    // A and B are wired the way every other settings prompt is; the fields themselves are typed
    // into with the keyboard, as they were.
    SettingsOverlayInput { action ->
        when (action) {
            GamepadAction.SELECT -> if (id.isNotBlank() && title.isNotBlank()) onAdd(id, title, source)
            GamepadAction.BACK -> onDismiss()
            else -> Unit
        }
    }
    PfpOverlayCard(onScrimTap = onDismiss) {
        PfpOverlayTitle("Add ${launcher.name} game")
        Spacer(Modifier.height(10.dp))
        adapter?.idPrompt?.let { Text(it, color = SettingsSubtext, fontSize = 12.sp) }
        OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("Game ID") }, singleLine = true)
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Game name") }, singleLine = true)
        if (adapter != null && adapter.sources.isNotEmpty()) {
            Text("Source", color = SettingsSubtext, fontSize = 12.sp)
            Row {
                adapter.sources.forEach { s ->
                    Text(
                        text = s,
                        color = if (s == source) SettingsAccent else SettingsSubtext,
                        modifier = Modifier
                            .clickable { source = s }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row {
            TextButton(onClick = { onTest(id, source) }) { Text("Test Launch") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                onClick = { onAdd(id, title, source) },
                enabled = id.isNotBlank() && title.isNotBlank(),
            ) { Text("Add") }
        }
    }
}

// ── Shared bits ─────────────────────────────────────────────────────────────────

private fun cardSublabel(card: LibraryCardRow): String = buildString {
    append(card.romDirectory ?: "No ROM directory")
    append("  ·  ${card.gameCount} game${if (card.gameCount == 1) "" else "s"}")
}

@Composable
private fun Hint(text: String) {
    Text(text = text, color = SettingsSubtext, modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp))
}

@Composable
private fun MessageRow(message: String, onDismiss: () -> Unit) {
    SettingsRow(
        label    = message,
        sublabel = "Tap to dismiss",
        trailing = { Text("✕", color = SettingsAccent, fontWeight = FontWeight.Bold) },
        onClick  = onDismiss,
    )
}

// ── Previews ──────────────────────────────────────────────────────────────────

@CombinedPreviews
@Composable
fun LibraryManagerScreenPreview() {
    PfpPreview {
        LibraryManagerContent(
            state = SettingsPreviewData.libraryListState,
            onBack = {},
            onAddAndroidApps = {},
            onAddRomRoot = {},
            onRelinkRomRoot = { _, _ -> },
            onBeginRelink = { Uri.EMPTY },
            onRemoveRomRoot = {},
            onScanRomRoot = {},
            onOpenCardDetail = {},
            onStartAddConsole = {},
            onRequestRomFolderSetup = {},
            onScanAllConsoles = {},
            onDismissMessage = {},
            onPlatformChosen = {},
            onEmulatorChosen = {},
            onConfirmAddConsole = {},
            onLoadEmulatorOptions = {},
            onRemoveExtension = { _, _ -> },
            onAddExtension = { _, _ -> },
            onScanConsole = {},
            onBeginRename = {},
            onCancelRename = {},
            onConfirmRename = {},
            onToggleEnabled = { _, _ -> },
            onTogglePinned = { _, _ -> },
            onMoveCard = { _, _ -> },
            onRemoveCard = {},
            onSetEmulatorForDetail = {},
            onOpenImportPcGames = {},
            onSetVita3KFolder = {},
            onScanVitaGames = {},
            onRemoveApp = {},
            onRefreshHomeStatus = {},
            onScanPcGamesFolder = {},
            onExportManualPcGames = {},
            onImportPcGame = {},
            onImportAllPcGames = {},
            onTestLaunchPcGame = { _, _, _ -> },
            onAddPcGameById = { _, _, _, _ -> },
            homeRoleIntentProvider = { null }
        )
    }
}
