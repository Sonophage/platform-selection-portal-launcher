package com.psplauncher.feature.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import android.os.Build
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.psplauncher.core.data.repository.MediaRootKind
import com.psplauncher.core.ui.preview.CombinedPreviews
import com.psplauncher.core.ui.preview.PfpScreenPreview
import com.psplauncher.feature.settings.ui.wizard.WizardCheckboxRow
import com.psplauncher.feature.settings.ui.wizard.WizardInfoText
import com.psplauncher.feature.settings.ui.wizard.WizardRootRow
import com.psplauncher.feature.settings.ui.wizard.WizardRow
import com.psplauncher.feature.settings.ui.wizard.WizardScaffold
import com.psplauncher.feature.settings.ui.wizard.WizardSplash
import com.psplauncher.feature.settings.ui.wizard.WizardSectionHeader
import com.psplauncher.feature.settings.ui.wizard.WizardTextField
import com.psplauncher.feature.settings.ui.wizard.WizardValueRow
import com.psplauncher.feature.settings.viewmodel.ArtworkSourceUi
import com.psplauncher.feature.settings.viewmodel.InitialSetupUiState
import com.psplauncher.feature.settings.viewmodel.InitialSetupViewModel
import com.psplauncher.feature.settings.viewmodel.RootFolderRow
import com.psplauncher.feature.settings.viewmodel.SetupStep

private enum class AddSlot { ROM, MUSIC, VIDEO, PHOTO, BOOK }

@Composable
fun InitialSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,

    firstRun: Boolean = false,
    onOpenLibraryManager: () -> Unit = {},

    onGoToLibrary: () -> Unit = {},
    viewModel: InitialSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    var splashDone by rememberSaveable { mutableStateOf(!firstRun) }
    if (!splashDone) {
        WizardSplash(onBegin = { splashDone = true })
        return
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetWizard() }
    }

    var pendingAdd by remember { mutableStateOf<AddSlot?>(null) }
    val addPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val slot = pendingAdd
        pendingAdd = null
        if (uri != null && slot != null) when (slot) {
            AddSlot.ROM   -> viewModel.addRomRoot(uri)
            AddSlot.MUSIC -> viewModel.addMediaRoot(MediaRootKind.MUSIC, uri)
            AddSlot.VIDEO -> viewModel.addMediaRoot(MediaRootKind.VIDEO, uri)
            AddSlot.PHOTO -> viewModel.addMediaRoot(MediaRootKind.PHOTO, uri)
            AddSlot.BOOK  -> viewModel.addMediaRoot(MediaRootKind.BOOK, uri)
        }
    }

    var pendingRelinkKind by remember { mutableStateOf<Pair<MediaRootKind, String>?>(null) }
    val relinkMediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val (kind, oldUri) = pendingRelinkKind ?: (null to null)
        pendingRelinkKind = null
        if (uri != null && kind != null && oldUri != null) viewModel.relinkMediaRoot(kind, oldUri, uri)
    }

    var pendingRelinkRom by remember { mutableStateOf<String?>(null) }
    val relinkRomPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val old = pendingRelinkRom
        pendingRelinkRom = null
        if (uri != null && old != null) viewModel.relinkRomRoot(old, uri)
    }

    val artworkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) viewModel.onArtworkFolderPicked(uri) }

    val retroPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) viewModel.linkRetroArch(uri) }

    val vitaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) viewModel.linkVitaFolder(uri) }

    val notificationRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshGrants() }
    val systemScreen = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshGrants() }

    val openSettingsScreen = LocalSettingsOpenScreen.current

    val step = state.step
    val stepNumber = state.stepNumber
    val canGoBack = step != SetupStep.WELCOME

    WizardScaffold(
        stepNumber = stepNumber,
        stepCount = state.stepCount,
        title = "Initial Setup",
        onBack = { if (!viewModel.previousStep() && !firstRun) onBack() },

        onSkip = onBack,
        backEnabled = canGoBack,
        message = state.message,
        onDismissMessage = viewModel::dismissMessage,
        heading = headingFor(step),
        hint = hintFor(step),
        contentKey = step,
        modifier = modifier,
    ) {
        when (step) {
            SetupStep.WELCOME -> WelcomePage(onStart = { viewModel.nextStep() })
            SetupStep.PERMISSIONS -> PermissionsPage(
                state = state,
                onRefresh = viewModel::refreshGrants,
                onGrantNotifications = {
                    notificationRequest.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                },
                onOpenUsageAccess = {
                    systemScreen.launch(
                        android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    )
                },
                onSetAsHome = { systemScreen.launch(viewModel.homeRoleIntent()) },
                onContinue = { viewModel.nextStep() },
            )
            SetupStep.ROM_ROOTS -> RootsPage(
                roots = state.romRoots,
                emptyText = "No ROM roots yet. Add the folder where your consoles' games live — " +
                    "one subfolder per console, scanned automatically.",
                addLabel = "Add ROM Root",
                addSublabel = "Grant a root folder with one subfolder per console",
                rescanLabel = "Rescan ROM Roots",
                rescanSublabel = "Auto-detect consoles and scan their games",

                onCreateFolders = viewModel::createStandardRomFolders,
                onAdd = { pendingAdd = AddSlot.ROM; addPicker.launch(null) },
                onRelink = { row -> pendingRelinkRom = row.treeUri; relinkRomPicker.launch(runCatching { Uri.parse(row.treeUri) }.getOrNull()) },
                onRemove = { viewModel.removeRomRoot(it.treeUri) },
                onRescan = viewModel::rescanRomRoots,
                onContinue = { viewModel.nextStep() },
                nextLabel = "Music",
            )
            SetupStep.MUSIC -> MediaRootsPage(
                roots = state.musicRoots,
                kindLabel = "Music",
                kind = MediaRootKind.MUSIC,
                emptyText = "No music roots yet. Add the folder where your music lives — several roots can span internal storage and an SD card.",
                addLabel = "Add Music Root",
                addSublabel = "Grant a root folder (e.g. /Music) — add several to span locations",
                onAdd = { pendingAdd = AddSlot.MUSIC; addPicker.launch(null) },
                onRelink = { row ->
                    pendingRelinkKind = MediaRootKind.MUSIC to row.treeUri
                    relinkMediaPicker.launch(runCatching { Uri.parse(row.treeUri) }.getOrNull())
                },
                onRemove = { viewModel.removeMediaRoot(MediaRootKind.MUSIC, it.treeUri) },
                onRescan = { viewModel.rescanMediaRoot(MediaRootKind.MUSIC) },
                onContinue = { viewModel.nextStep() },
                nextLabel = "Video",
            )
            SetupStep.VIDEO -> MediaRootsPage(
                roots = state.videoRoots,
                kindLabel = "Video",
                kind = MediaRootKind.VIDEO,
                emptyText = "No video roots yet. Add the folder where your videos live — several roots can span internal storage and an SD card.",
                addLabel = "Add Video Root",
                addSublabel = "Grant a root folder (e.g. /Videos) — add several to span locations",
                onAdd = { pendingAdd = AddSlot.VIDEO; addPicker.launch(null) },
                onRelink = { row ->
                    pendingRelinkKind = MediaRootKind.VIDEO to row.treeUri
                    relinkMediaPicker.launch(runCatching { Uri.parse(row.treeUri) }.getOrNull())
                },
                onRemove = { viewModel.removeMediaRoot(MediaRootKind.VIDEO, it.treeUri) },
                onRescan = { viewModel.rescanMediaRoot(MediaRootKind.VIDEO) },
                onContinue = { viewModel.nextStep() },
                nextLabel = "Photo",
            )
            SetupStep.PHOTO -> MediaRootsPage(
                roots = state.photoRoots,
                kindLabel = "Photo",
                kind = MediaRootKind.PHOTO,
                emptyText = "No photo roots yet. Add the folder where your photos live — several roots can span internal storage and an SD card.",
                addLabel = "Add Photo Root",
                addSublabel = "Grant a root folder (e.g. /Photos) — add several to span locations",
                onAdd = { pendingAdd = AddSlot.PHOTO; addPicker.launch(null) },
                onRelink = { row ->
                    pendingRelinkKind = MediaRootKind.PHOTO to row.treeUri
                    relinkMediaPicker.launch(runCatching { Uri.parse(row.treeUri) }.getOrNull())
                },
                onRemove = { viewModel.removeMediaRoot(MediaRootKind.PHOTO, it.treeUri) },
                onRescan = { viewModel.rescanMediaRoot(MediaRootKind.PHOTO) },
                onContinue = { viewModel.nextStep() },
                nextLabel = "Books",
            )
            SetupStep.BOOKS -> MediaRootsPage(
                roots = state.bookRoots,
                kindLabel = "Books",
                kind = MediaRootKind.BOOK,
                emptyText = "No book roots yet. Add the folder where your EPUBs and comics live — several roots can span internal storage and an SD card.",
                addLabel = "Add Book Root",
                addSublabel = "Grant a root folder (e.g. /Books) — add several to span locations",
                onAdd = { pendingAdd = AddSlot.BOOK; addPicker.launch(null) },
                onRelink = { row ->
                    pendingRelinkKind = MediaRootKind.BOOK to row.treeUri
                    relinkMediaPicker.launch(runCatching { Uri.parse(row.treeUri) }.getOrNull())
                },
                onRemove = { viewModel.removeMediaRoot(MediaRootKind.BOOK, it.treeUri) },
                onRescan = { viewModel.rescanMediaRoot(MediaRootKind.BOOK) },
                onContinue = { viewModel.nextStep() },
                nextLabel = "Artwork",
            )
            SetupStep.ARTWORK -> ArtworkPage(
                state = state,
                onPickFolder = { artworkPicker.launch(null) },
                onForget = viewModel::forgetArtworkFolder,
                onRemove = viewModel::forgetArtworkFolder,
                onImportNow = viewModel::importArtworkNow,
                onContinue = { viewModel.nextStep() },
                nextLabel = "Online Services",
            )
            SetupStep.SERVICES -> ServicesPage(
                state = state,
                onConnectSgdb = viewModel::connectSgdb,
                onTestIgdb = viewModel::testIgdbCredentials,
                onConnectIgdb = viewModel::connectIgdb,
                onTestSs = viewModel::testSsCredentials,
                onConnectSs = viewModel::connectScreenScraper,
                onContinue = { viewModel.nextStep() },
                nextLabel = if (state.vita3KInstalled) "Vita Data Folder"
                            else if (state.retroArchInstalled) "RetroArch" else "Finish",
            )
            SetupStep.VITA -> VitaPage(
                state = state,
                onLink = { vitaPicker.launch(null) },
                onForget = viewModel::forgetVitaFolder,
                onContinue = { viewModel.nextStep() },
                nextLabel = if (state.retroArchInstalled) "RetroArch" else "Finish",
            )
            SetupStep.RETROARCH -> RetroArchPage(
                state = state,
                onLink = { retroPicker.launch(null) },
                onRedetect = viewModel::redetectRetroArchCores,
                onUnlink = viewModel::unlinkRetroArch,
                onContinue = { viewModel.nextStep() },
            )
            SetupStep.PERSONALIZE -> PersonalizePage(
                autoFit = state.autoFitXmbLayout,
                onToggleAutoFit = viewModel::toggleAutoFitXmbLayout,
                onOpenScreen = { id ->

                    viewModel.parkForExcursion()
                    openSettingsScreen(id)
                },
                onContinue = { viewModel.nextStep() },
            )
            SetupStep.FINISH -> FinishPage(
                state = state,
                onOpenLibraryManager = onOpenLibraryManager,
                onGoToLibrary = onGoToLibrary,
                onFinish = {
                    viewModel.finishSetup()
                    onBack()
                },
            )
        }
    }
}

private const val RETROARCH_PICK_HINT =
    "Open the picker's sidebar and choose RetroArch itself, not the /RetroArch folder"

private fun headingFor(step: SetupStep): String = when (step) {
    SetupStep.WELCOME     -> "Welcome to PSPLauncher."
    SetupStep.PERMISSIONS -> "Let the launcher off its leash."
    SetupStep.ROM_ROOTS   -> "Choose your ROM folders."
    SetupStep.MUSIC       -> "Choose your music folders."
    SetupStep.VIDEO       -> "Choose your video folders."
    SetupStep.PHOTO       -> "Choose your photo folders."
    SetupStep.BOOKS       -> "Choose your book folders."
    SetupStep.ARTWORK     -> "Choose your artwork folder."
    SetupStep.SERVICES    -> "Connect your artwork sources."
    SetupStep.VITA        -> "Set your Vita data folder."
    SetupStep.RETROARCH   -> "Link RetroArch's cores folder."
    SetupStep.PERSONALIZE -> "Make it yours."
    SetupStep.FINISH      -> "You're all set!"
}

private fun hintFor(step: SetupStep): String? = when (step) {
    SetupStep.PERMISSIONS -> "Three optional grants. Everything works without them — each one just turns something on."
    SetupStep.BOOKS     -> "EPUBs and comics. Add several roots to span internal storage and an SD card."
    SetupStep.PERSONALIZE -> "Each row opens the real screen and comes back here, so nothing you set is a wizard-only copy."
    SetupStep.WELCOME   -> "A few short steps to point the launcher at your stuff — every step is optional and can be changed later in Settings."
    SetupStep.ROM_ROOTS -> "Add one or more root folders — each console's games live in a subfolder under them."
    SetupStep.MUSIC     -> "Add several roots to span internal storage and an SD card."
    SetupStep.VIDEO     -> "Add several roots to span internal storage and an SD card."
    SetupStep.PHOTO     -> "Add several roots to span internal storage and an SD card."
    SetupStep.ARTWORK   -> "One folder hosts the artwork library — you can import into it right after."
    SetupStep.SERVICES  -> "All optional and free. SteamGridDB, IGDB, and ScreenScraper fetch game artwork and metadata."
    SetupStep.VITA      -> "Vita3K is installed — one grant links every installed Vita title for discovery and trophies."
    SetupStep.RETROARCH -> "Lets the launcher know exactly which cores you have, so only those are offered."
    SetupStep.FINISH    -> "Everything below can be adjusted anytime in Settings."
}

@Composable
private fun PermissionsPage(
    state: InitialSetupUiState,
    onRefresh: () -> Unit,
    onGrantNotifications: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onSetAsHome: () -> Unit,
    onContinue: () -> Unit,
) {
    LifecycleResumeEffect(Unit) {
        onRefresh()
        onPauseOrDispose { }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        WizardValueRow(
            label = "Notifications",
            value = if (state.hasNotifications) "Granted" else "Grant…",
            sublabel = "Tells you when a scan or a download has finished",
            focusKey = "perm_notifications",
            onClick = { if (!state.hasNotifications) onGrantNotifications() },
        )
    }
    WizardValueRow(
        label = "Usage Access",
        value = if (state.hasUsageAccess) "Granted" else "Grant…",
        sublabel = "Sorts the app drawer by what you opened last",
        onClick = { if (!state.hasUsageAccess) onOpenUsageAccess() },
    )
    WizardValueRow(
        label = "PSPLauncher as Home",
        value = if (state.isHomeLauncher) "Active" else "Set…",
        sublabel = if (state.isHomeLauncher) {
            "Home comes back here, and \"Add to home\" elsewhere imports the game"
        } else {
            "Makes Home come back here, not the stock launcher"
        },
        onClick = { if (!state.isHomeLauncher) onSetAsHome() },
    )
    WizardContinueRow("ROM Folders", onContinue)
}

@Composable
private fun PersonalizePage(
    autoFit: Boolean,
    onToggleAutoFit: (Boolean) -> Unit,
    onOpenScreen: (String) -> Unit,
    onContinue: () -> Unit,
) {
    WizardRow(
        label = "Theme",
        sublabel = "Colour scheme, accent and theme packs",
        focusKey = "personalize_theme",
        onClick = { onOpenScreen("settings_themes") },
    )
    WizardRow(
        label = "Sound",
        sublabel = "Menu sounds, menu music, and the boot and launch cues",
        onClick = { onOpenScreen("settings_audio") },
    )
    WizardRow(
        label = "Boot Logo",
        sublabel = "The boot sequence, your own boot video, and GameBoot",
        onClick = { onOpenScreen("settings_boot") },
    )
    WizardRow(
        label = "Wallpaper & Layout",
        sublabel = "Wallpaper, the wave, and where the crossbar sits",
        onClick = { onOpenScreen("settings_layout") },
    )

    WizardSectionHeader("Screen Size")
    WizardCheckboxRow(
        label = "Auto-fit the XMB layout (PSP proportions) — changeable anytime in Settings",
        checked = autoFit,
        onToggle = onToggleAutoFit,
        focusKey = "personalize_autofit",
    )
    WizardContinueRow("Finish", onContinue)
}

@Composable
private fun WizardContinueRow(label: String, onClick: () -> Unit) {
    Spacer(Modifier.height(4.dp))
    WizardRow(label = "Continue", sublabel = "Next: $label", onClick = onClick)
}

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    WizardRow(
        label = "Get Started",
        sublabel = "Permissions first, then your folders",
        focusKey = "welcome_start",
        onClick = onStart,
    )
}

@Composable
private fun RootsPage(
    roots: List<RootFolderRow>,
    emptyText: String,
    addLabel: String,
    addSublabel: String,
    rescanLabel: String,
    rescanSublabel: String,
    onCreateFolders: () -> Unit,
    onAdd: () -> Unit,
    onRelink: (RootFolderRow) -> Unit,
    onRemove: (RootFolderRow) -> Unit,
    onRescan: () -> Unit,
    onContinue: () -> Unit,
    nextLabel: String,
) {
    if (roots.isEmpty()) {
        WizardInfoText(emptyText)
    } else {
        roots.forEach { row ->
            WizardRootRow(
                name = row.name,
                sublabel = if (row.linked) "Consoles home under this root"
                           else "Access lost — use ✎ to re-grant access",
                onEdit = { onRelink(row) },
                onRemove = { onRemove(row) },
            )
        }
    }
    WizardRow(label = addLabel, sublabel = addSublabel, onClick = onAdd)
    if (roots.isNotEmpty()) {
        WizardRow(
            label = "Create Standard Folders",
            sublabel = "One subfolder per console, named for your games",
            onClick = onCreateFolders,
        )
    }
    WizardRow(label = rescanLabel, sublabel = rescanSublabel, onClick = onRescan)
    WizardContinueRow(nextLabel, onContinue)
}

@Composable
private fun MediaRootsPage(
    roots: List<RootFolderRow>,
    kindLabel: String,
    kind: MediaRootKind,
    emptyText: String,
    addLabel: String,
    addSublabel: String,
    onAdd: () -> Unit,
    onRelink: (RootFolderRow) -> Unit,
    onRemove: (RootFolderRow) -> Unit,
    onRescan: () -> Unit,
    onContinue: () -> Unit,
    nextLabel: String,
) {
    if (roots.isEmpty()) {
        WizardInfoText(emptyText)
    } else {
        roots.forEach { row ->
            WizardRootRow(
                name = row.name,
                sublabel = if (row.linked) "$kindLabel library lives here"
                           else "Access lost — use ✎ to re-grant access",
                onEdit = { onRelink(row) },
                onRemove = { onRemove(row) },
            )
        }
    }
    WizardRow(label = addLabel, sublabel = addSublabel, onClick = onAdd)
    WizardRow(label = "Rescan $kindLabel Library", sublabel = "Update the libraries from every root folder", onClick = onRescan)
    WizardContinueRow(nextLabel, onContinue)
}

@Composable
private fun ArtworkPage(
    state: InitialSetupUiState,
    onPickFolder: () -> Unit,
    onForget: () -> Unit,
    onRemove: () -> Unit,
    onImportNow: () -> Unit,
    onContinue: () -> Unit,
    nextLabel: String,
) {
    val folder = state.artworkFolderName
    if (folder == null) {
        WizardInfoText(
            "Pick a writable folder. The launcher sets up an artwork library inside it " +
                "automatically — games get art as they're added."
        )
    } else {
        WizardRootRow(
            name = folder,
            sublabel = "Artwork library — use ✎ to pick a different folder",
            onEdit = onPickFolder,
            onRemove = onRemove,
        )
    }
    WizardRow(
        label = if (folder == null) "Choose Artwork Folder" else "Change Artwork Folder",
        sublabel = if (folder == null) "One folder hosts the artwork library"
                   else "Pick a different folder — files are never deleted",
        onClick = onPickFolder,
    )
    if (state.artworkSources.isNotEmpty()) {
        WizardRow(
            label = "Import artwork now?",
            sublabel = "Copy ${state.sizeLabelForSources()} from the folder's import/ into the library",
            focusKey = "artwork_import_now",
            onClick = onImportNow,
        )
    } else if (folder != null) {
        WizardRow(
            label = "Artwork import",
            sublabel = "Nothing to import yet — add a launcher's media folder under import/",
            onClick = onImportNow,
        )
    }
    if (folder != null) {
        WizardRow(
            label = "Release artwork folder",
            sublabel = "Unlink it without touching any files",
            onClick = onForget,
        )
    }
    WizardContinueRow(nextLabel, onContinue)
}

private fun InitialSetupUiState.sizeLabelForSources(): String {
    val n = artworkSources.size
    val label = if (n == 1) artworkSources.first().label else "$n source folders"
    return label
}

@Composable
private fun ServicesPage(
    state: InitialSetupUiState,
    onConnectSgdb: (String) -> Unit,
    onTestIgdb: (String, String) -> Unit,
    onConnectIgdb: (String, String) -> Unit,
    onTestSs: (String, String) -> Unit,
    onConnectSs: (String, String) -> Unit,
    onContinue: () -> Unit,
    nextLabel: String,
) {
    var sgdbKeyDraft by remember(state.hasSgdb) { mutableStateOf("") }
    var igdbIdDraft by remember(state.hasIgdb) { mutableStateOf("") }
    var igdbSecretDraft by remember(state.hasIgdb) { mutableStateOf("") }
    var ssUserDraft by remember(state.hasScreenScraper) { mutableStateOf("") }
    var ssPassDraft by remember(state.hasScreenScraper) { mutableStateOf("") }

    WizardInfoText(
        "All accounts are optional and free. SteamGridDB, IGDB, and ScreenScraper " +
            "fetch game artwork and metadata for your library."
    )

    WizardSectionHeader("SteamGridDB")
    WizardTextField(
        label = if (state.hasSgdb) "API Key (saved)" else "API Key",
        value = sgdbKeyDraft,
        onValueChange = { sgdbKeyDraft = it },
        placeholder = if (state.hasSgdb) "••••••••  (tap to replace)" else "Paste your SteamGridDB key",
        isPassword = true,
    )
    if (sgdbKeyDraft.isNotBlank()) {
        WizardRow(label = "Connect SteamGridDB", onClick = { onConnectSgdb(sgdbKeyDraft) })
    }

    WizardSectionHeader("IGDB (Twitch)")
    WizardTextField(
        label = if (state.hasIgdb) "Client ID (saved)" else "Client ID",
        value = igdbIdDraft,
        onValueChange = { igdbIdDraft = it },
        placeholder = if (state.hasIgdb) "Tap to replace" else "Twitch Client ID",
    )
    WizardTextField(
        label = if (state.hasIgdb) "Client Secret (saved)" else "Client Secret",
        value = igdbSecretDraft,
        onValueChange = { igdbSecretDraft = it },
        placeholder = if (state.hasIgdb) "••••••••  (tap to replace)" else "Twitch Client Secret",
        isPassword = true,
    )
    state.igdbStatus?.let {
        WizardInfoText(it)
    }
    if (igdbIdDraft.isNotBlank() && igdbSecretDraft.isNotBlank()) {
        WizardRow(label = "Test Credentials", onClick = { onTestIgdb(igdbIdDraft, igdbSecretDraft) })
        WizardRow(label = "Connect IGDB", onClick = { onConnectIgdb(igdbIdDraft, igdbSecretDraft) })
    }

    if (state.ssEnabled) {
        WizardSectionHeader("ScreenScraper")
        if (state.hasScreenScraper) {
            WizardValueRow(label = "Connected as", value = state.ssUsername)
        }
        WizardTextField(
            label = if (state.hasScreenScraper) "Username (saved)" else "Username",
            value = ssUserDraft,
            onValueChange = { ssUserDraft = it },
            placeholder = if (state.hasScreenScraper) "Tap to replace" else "ScreenScraper username",
        )
        WizardTextField(
            label = if (state.hasScreenScraper) "Password (saved)" else "Password",
            value = ssPassDraft,
            onValueChange = { ssPassDraft = it },
            placeholder = if (state.hasScreenScraper) "••••••••  (tap to replace)" else "ScreenScraper password",
            isPassword = true,
        )
        state.ssStatus?.let { WizardInfoText(it) }
        if (ssUserDraft.isNotBlank() && ssPassDraft.isNotBlank()) {
            WizardRow(label = "Test Account", onClick = { onTestSs(ssUserDraft, ssPassDraft) })
            WizardRow(label = "Connect ScreenScraper", onClick = { onConnectSs(ssUserDraft, ssPassDraft) })
        }
    }

    WizardContinueRow(nextLabel, onContinue)
}

@Composable
private fun VitaPage(
    state: InitialSetupUiState,
    onLink: () -> Unit,
    onForget: () -> Unit,
    onContinue: () -> Unit,
    nextLabel: String,
) {
    val folder = state.vitaFolderName
    if (folder == null) {
        WizardInfoText(
            "Vita3K is installed. Grant its data (ux0) folder — often shared-storage " +
                "e.g. Roms/vita/ux0 — so PSP can discover installed Vita titles and read " +
                "trophies without granting per game."
        )
    } else {
        WizardRootRow(
            name = folder,
            sublabel = "Vita3K data folder — use ✎ to pick a different one",
            onEdit = onLink,
            onRemove = onForget,
        )
    }
    WizardRow(
        label = if (folder == null) "Set Vita3K Data Folder" else "Change Vita3K Data Folder",
        sublabel = if (folder == null) "Grant the ux0 folder (or the folder that contains it)"
                   else "Pick a different folder — files are never touched",
        onClick = onLink,
    )
    if (folder != null) {
        WizardRow(
            label = "Release Vita Data Folder",
            sublabel = "Unlink it without touching any files",
            onClick = onForget,
        )
    }
    WizardContinueRow(nextLabel, onContinue)
}

@Composable
private fun RetroArchPage(
    state: InitialSetupUiState,
    onLink: () -> Unit,
    onRedetect: () -> Unit,
    onUnlink: () -> Unit,
    onContinue: () -> Unit,
) {
    if (state.retroArchLinked) {
        WizardValueRow(
            label = "Cores Folder",
            value = when {
                state.retroArchCoreCount == null -> "Linked"
                state.retroArchCoreCount == 0 -> "No cores found"
                else -> "${state.retroArchCoreCount} cores detected"
            },

            sublabel = "Wrong folder? $RETROARCH_PICK_HINT".takeIf { state.retroArchCoreCount == 0 },
        )
        WizardRow(
            label = "Re-link Folder",
            sublabel = RETROARCH_PICK_HINT,
            onClick = onLink,
        )
        WizardRow(label = "Re-check Cores", sublabel = "After installing new cores", onClick = onRedetect)
        WizardRow(label = "Unlink RetroArch", sublabel = "Fall back to offering every curated core", onClick = onUnlink)
    } else {
        WizardInfoText(
            "RetroArch is installed. Link its folder so only the cores you actually have are " +
                "offered when launching games — otherwise every curated core is shown (unverified)."
        )
        WizardRow(label = "Link RetroArch", sublabel = RETROARCH_PICK_HINT, onClick = onLink)
    }
    WizardContinueRow("Finish", onContinue)
}

@Composable
private fun FinishPage(
    state: InitialSetupUiState,
    onOpenLibraryManager: () -> Unit,
    onGoToLibrary: () -> Unit,
    onFinish: () -> Unit,
) {
    if (!state.anyFolderSet) {
        WizardInfoText(
            "Nothing was configured yet — every folder and service can be added anytime from Settings."
        )
    }
    WizardSectionHeader("Summary")
    WizardValueRow(label = "ROM Library", value = rootsShortLabel(state.romRoots))
    WizardValueRow(label = "Music", value = rootsShortLabel(state.musicRoots))
    WizardValueRow(label = "Video", value = rootsShortLabel(state.videoRoots))
    WizardValueRow(label = "Photo", value = rootsShortLabel(state.photoRoots))
    WizardValueRow(label = "Books", value = rootsShortLabel(state.bookRoots))
    WizardValueRow(label = "Artwork Library", value = state.artworkFolderName ?: "Not set")
    WizardValueRow(label = "SteamGridDB", value = if (state.hasSgdb) "Connected" else "Not set")
    WizardValueRow(label = "IGDB (Twitch)", value = state.igdbClientId.ifBlank { "Not set" })
    if (state.ssEnabled) {
        WizardValueRow(label = "ScreenScraper", value = state.ssUsername.ifBlank { "Not set" })
    }
    if (state.vita3KInstalled) {
        WizardValueRow(label = "Vita Data Folder", value = state.vitaFolderName ?: "Not set")
    }
    if (state.retroArchInstalled) {
        WizardValueRow(
            label = "RetroArch",
            value = if (state.retroArchLinked) "${state.retroArchCoreCount ?: 0} cores" else "Not linked",
        )
    }

    Spacer(Modifier.height(4.dp))
    if (state.romRoots.isNotEmpty()) {
        WizardRow(
            label = "Open Library Manager",
            sublabel = "Add consoles and scan the ROM roots you just set",
            onClick = onOpenLibraryManager,
        )

        WizardRow(
            label = "Go to your library",
            sublabel = "Jump straight to All Games",
            focusKey = "finish_go_library",
            onClick = onGoToLibrary,
        )
    }
    WizardRow(
        label = "Finish",
        sublabel = "Head to the launcher",
        focusKey = "finish_done",
        onClick = onFinish,
    )
}

private fun rootsShortLabel(roots: List<RootFolderRow>): String =
    when {
        roots.isEmpty() -> "Not set"
        roots.size == 1 -> roots.first().name
        else -> "${roots.first().name} +${roots.size - 1} more"
    }

@Composable
private fun WizardPagePreview(
    stepNumber: Int,
    stepCount: Int = 9,
    heading: String,
    hint: String?,
    backEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    PfpScreenPreview {
        WizardScaffold(
            stepNumber = stepNumber,
            stepCount = stepCount,
            title = "Initial Setup",
            onBack = {},
            backEnabled = backEnabled,
            heading = heading,
            hint = hint,
            content = content,
        )
    }
}

private val prefix = "content://preview/tree/primary%3A"

@CombinedPreviews
@Composable
private fun WelcomePagePreview() {
    WizardPagePreview(
        stepNumber = 1,
        heading = "Welcome to PSPLauncher.",
        hint = "A few short steps to point the launcher at your stuff — every step is optional and can be changed later in Settings.",
    ) {
        WelcomePage(onStart = {})
    }
}

@CombinedPreviews
@Composable
private fun RomRootsPagePreview() {
    WizardPagePreview(
        stepNumber = 2,
        heading = "Choose your ROM folders.",
        hint = "Add one or more root folders — each console's games live in a subfolder under them.",
    ) {
        RootsPage(
            roots = listOf(
                RootFolderRow("$prefix/ROMS", "ROMS/Sega", linked = true),
                RootFolderRow("$prefix/SDROMS", "SD/ROMS", linked = false),
            ),
            emptyText = "No ROM roots yet. Add the folder where your consoles' games live.",
            addLabel = "Add ROM Root",
            addSublabel = "Grant a root folder with one subfolder per console",
            rescanLabel = "Rescan ROM Roots",
            rescanSublabel = "Auto-detect consoles and scan their games",
            onCreateFolders = {},
            onAdd = {},
            onRelink = {},
            onRemove = {},
            onRescan = {},
            onContinue = {},
            nextLabel = "Music",
        )
    }
}

@Composable
private fun MediaRootsPagePreview(
    stepNumber: Int,
    heading: String,
    hint: String,
    kindLabel: String,
    kind: MediaRootKind,
    nextLabel: String,
) {
    WizardPagePreview(
        stepNumber = stepNumber,
        heading = heading,
        hint = hint,
    ) {
        MediaRootsPage(
            roots = listOf(
                RootFolderRow("$prefix/$kindLabel", "/$kindLabel", linked = true),
                RootFolderRow("$prefix/SD$kindLabel", "SD/$kindLabel", linked = true),
            ),
            kindLabel = kindLabel,
            kind = kind,
            emptyText = "No $kindLabel roots yet.",
            addLabel = "Add $kindLabel Root",
            addSublabel = "Grant a root folder — add several to span locations",
            onAdd = {},
            onRelink = {},
            onRemove = {},
            onRescan = {},
            onContinue = {},
            nextLabel = nextLabel,
        )
    }
}

@CombinedPreviews
@Composable
private fun MusicRootsPagePreview() = MediaRootsPagePreview(
    stepNumber = 3,
    heading = "Choose your music folders.",
    hint = "Add several roots to span internal storage and an SD card.",
    kindLabel = "Music",
    kind = MediaRootKind.MUSIC,
    nextLabel = "Video",
)

@CombinedPreviews
@Composable
private fun VideoRootsPagePreview() = MediaRootsPagePreview(
    stepNumber = 4,
    heading = "Choose your video folders.",
    hint = "Add several roots to span internal storage and an SD card.",
    kindLabel = "Video",
    kind = MediaRootKind.VIDEO,
    nextLabel = "Photo",
)

@CombinedPreviews
@Composable
private fun PhotoRootsPagePreview() = MediaRootsPagePreview(
    stepNumber = 5,
    heading = "Choose your photo folders.",
    hint = "Add several roots to span internal storage and an SD card.",
    kindLabel = "Photo",
    kind = MediaRootKind.PHOTO,
    nextLabel = "Artwork",
)

@CombinedPreviews
@Composable
private fun ArtworkPagePreview() {
    WizardPagePreview(
        stepNumber = 6,
        heading = "Choose your artwork folder.",
        hint = "One folder hosts the artwork library — you can import into it right after.",
    ) {
        ArtworkPage(
            state = InitialSetupUiState(
                artworkFolderName = "ArtworkLibrary",
                artworkSources = listOf(ArtworkSourceUi("gpSP (PSP Game Boy Advance)", 3)),
            ),
            onPickFolder = {},
            onForget = {},
            onRemove = {},
            onImportNow = {},
            onContinue = {},
            nextLabel = "Online Services",
        )
    }
}

@CombinedPreviews
@Composable
private fun ServicesPagePreview() {
    WizardPagePreview(
        stepNumber = 7,
        heading = "Connect your artwork sources.",
        hint = "All optional and free. SteamGridDB, IGDB, and ScreenScraper fetch game artwork and metadata.",
    ) {
        ServicesPage(
            state = InitialSetupUiState(
                hasSgdb = true,
                igdbClientId = "client_id_abc",
                ssEnabled = true,
                ssUsername = "scraper_user",
            ),
            onConnectSgdb = {},
            onTestIgdb = { _, _ -> },
            onConnectIgdb = { _, _ -> },
            onTestSs = { _, _ -> },
            onConnectSs = { _, _ -> },
            onContinue = {},
            nextLabel = "Achievement Services",
        )
    }
}

@CombinedPreviews
@Composable
private fun VitaPagePreview() {
    WizardPagePreview(
        stepNumber = 9,
        heading = "Set your Vita data folder.",
        hint = "Vita3K is installed — one grant links every installed Vita title for discovery and trophies.",
    ) {
        VitaPage(
            state = InitialSetupUiState(
                vita3KInstalled = true,
                vitaFolderName = "Roms/vita/ux0",
            ),
            onLink = {},
            onForget = {},
            onContinue = {},
            nextLabel = "RetroArch",
        )
    }
}

@CombinedPreviews
@Composable
private fun RetroArchPagePreview() {
    WizardPagePreview(
        stepNumber = 10,
        heading = "Link RetroArch's cores folder.",
        hint = "Lets the launcher know exactly which cores you have, so only those are offered.",
    ) {
        RetroArchPage(
            state = InitialSetupUiState(
                retroArchInstalled = true,
                retroArchLinked = true,
                retroArchCoreCount = 42,
            ),
            onLink = {},
            onRedetect = {},
            onUnlink = {},
            onContinue = {},
        )
    }
}

@CombinedPreviews
@Composable
private fun FinishPagePreview() {
    WizardPagePreview(
        stepNumber = 11,
        heading = "You're all set!",
        hint = "Everything below can be adjusted anytime in Settings.",
    ) {
        FinishPage(
            state = InitialSetupUiState(
                romRoots = listOf(RootFolderRow("$prefix/ROMS", "/ROMS", linked = true)),
                musicRoots = listOf(RootFolderRow("$prefix/Music", "/Music", linked = true)),
                videoRoots = listOf(RootFolderRow("$prefix/Videos", "/Videos/SD", linked = true)),
                photoRoots = listOf(RootFolderRow("$prefix/DCIM", "DCIM/Camera", linked = true)),
                artworkFolderName = "ArtworkLibrary",
                hasSgdb = true,
                igdbClientId = "client_id_abc",
                ssEnabled = true,
                ssUsername = "scraper_user",
                retroArchInstalled = true,
                retroArchLinked = true,
                retroArchCoreCount = 42,
            ),
            onOpenLibraryManager = {},
            onGoToLibrary = {},
            onFinish = {},
        )
    }
}
