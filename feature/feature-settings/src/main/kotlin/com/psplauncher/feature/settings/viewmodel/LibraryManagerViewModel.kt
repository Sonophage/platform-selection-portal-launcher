package com.psplauncher.feature.settings.viewmodel

import com.psplauncher.core.domain.model.PlatformIds.ANDROID as ANDROID_PLATFORM_ID

import com.psplauncher.core.domain.model.PlatformIds.WINDOWS as WINDOWS_PLATFORM_ID

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.data.platform.PlatformFolderHintResolver
import com.psplauncher.core.data.repository.FolderLinkStatus
import com.psplauncher.core.data.repository.MemoryCardRepository
import com.psplauncher.core.data.repository.RomRootRepository
import com.psplauncher.core.data.repository.SafGrants
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameContentType
import com.psplauncher.core.domain.repository.GameRepository
import com.psplauncher.feature.appbar.LauncherShortcutRepository
import com.psplauncher.feature.artwork.api.MetadataScrapeWorker
import com.psplauncher.feature.launcher.EmulatorProfileRepository
import com.psplauncher.feature.launcher.PcLauncherAdapters
import com.psplauncher.feature.launcher.PcLauncherCatalog
import com.psplauncher.feature.launcher.PcLauncherType
import com.psplauncher.feature.library.scanner.LibraryScanner
import com.psplauncher.feature.library.scanner.RomScanner
import com.psplauncher.feature.library.scanner.isScannable
import com.psplauncher.feature.library.scanner.scanOutcomeMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

const val ADD_CONSOLE_FOCUS_KEY = "add_console"

private const val PSVITA_PLATFORM_ID = "psvita"

private const val INVALID_GAME_ID_MESSAGE =
    "Enter a valid game ID — a number, or a local_… ID copied from the game's page in the launcher."

enum class LibraryStep { LIST, PICK_PLATFORM, PICK_EMULATOR, SCAN_PROMPT, CARD_DETAIL, IMPORT_PC }

const val IMPORT_PC_FOCUS_KEY = "import_pc_games"

data class PcLauncherRow(
    val type: PcLauncherType,
    val name: String,
    val installed: Boolean,
    val packageName: String?,
    val canAddById: Boolean,
)

data class PcGameRow(val gameId: Long, val title: String, val launcherName: String)

data class LibraryCardRow(
    val platformId: String,
    val displayName: String,
    val enabled: Boolean,
    val pinned: Boolean,
    val treeUri: String?,
    val romDirectory: String?,
    val emulatorName: String?,
    val extensions: List<String>,
    val gameCount: Int,
)

data class PlatformOption(val id: String, val name: String, val shortName: String)
data class LibraryAppRow(val gameId: Long, val label: String)

data class EmulatorOption(val id: String?, val name: String)

data class LibraryManagerUiState(
    val step: LibraryStep = LibraryStep.LIST,
    val cards: List<LibraryCardRow> = emptyList(),

    val platformOptions: List<PlatformOption> = emptyList(),
    val emulatorOptions: List<EmulatorOption> = emptyList(),
    val pendingPlatformId: String? = null,
    val pendingPlatformName: String? = null,
    val pendingDirectory: String? = null,
    val pendingEmulatorId: String? = null,

    val detailPlatformId: String? = null,

    val androidApps: List<LibraryAppRow> = emptyList(),

    val romRoots: List<RootFolderRow> = emptyList(),

    val pcLaunchers: List<PcLauncherRow> = emptyList(),
    val pcGames: List<PcGameRow> = emptyList(),

    val vita3KFolderLabel: String? = null,

    val isHomeLauncher: Boolean = false,

    val awaitingRomRootSetup: Boolean = false,
    val renameTargetPlatformId: String? = null,
    val scanningPlatformIds: Set<String> = emptySet(),
    val message: String? = null,

    val returnFocusKey: String? = null,
) {
    val detailCard: LibraryCardRow? get() = cards.firstOrNull { it.platformId == detailPlatformId }
}

@HiltViewModel
class LibraryManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryCardRepository: MemoryCardRepository,
    private val romScanner: RomScanner,
    private val gameRepository: GameRepository,
    private val emulatorProfileRepository: EmulatorProfileRepository,
    private val romRootRepository: RomRootRepository,
    private val folderHintResolver: PlatformFolderHintResolver,
    private val launcherShortcutRepository: LauncherShortcutRepository,
    private val windowsLibrarySetup: com.psplauncher.core.data.repository.WindowsLibrarySetup,
    private val pcGameScanner: com.psplauncher.feature.settings.pc.PcGameScanner,
    private val vita3KLibrary: com.psplauncher.core.data.repository.Vita3KLibrary,
    private val vitaGameScanner: com.psplauncher.feature.library.scanner.VitaGameScanner,
    private val libraryScanner: LibraryScanner,
    private val romRootScanRunner: RomRootScanRunner,
    private val pcGameExporter: com.psplauncher.feature.settings.pc.PcGameExporter,
) : ViewModel() {
    private val _scratch = MutableStateFlow(LibraryManagerUiState())

    init {
        viewModelScope.launch {
            romRootRepository.roots.distinctUntilChanged().collect { refreshRomRoots() }
        }
    }

    val uiState: StateFlow<LibraryManagerUiState> = combine(
        memoryCardRepository.observeAll(),
        gameRepository.observeAll(),
        emulatorProfileRepository.profiles,
        vita3KLibrary.ux0TreeUriFlow,
        _scratch,
    ) { cards, games, profiles, vitaUx0, scratch ->
        val emulatorNames = profiles.associate { it.id to it.name }
        val counts = games.groupBy { it.platformId }.mapValues { it.value.size }
        scratch.copy(
            vita3KFolderLabel = vitaUx0?.let { uri ->
                RomRootRepository.rawPathOfTree(uri)?.substringAfterLast('/')
                    ?: Uri.decode(uri).substringAfterLast('/').substringAfterLast(':')
            },

            romRoots = scratch.romRoots.map { root ->
                val rootRaw = RomRootRepository.rawPathOfTree(root.treeUri)?.trimEnd('/')
                val homed = if (rootRaw == null) emptyList() else cards.mapNotNull { card ->
                    card.romDirectory?.takeIf { it.startsWith("$rootRaw/") }
                        ?.let { card.displayName.removeSuffix(" Memory Card") }
                }
                root.copy(consoles = homed.takeIf { it.isNotEmpty() }?.joinToString(", "))
            },
            cards = cards.map { card ->
                LibraryCardRow(
                    platformId = card.platformId,
                    displayName = card.displayName,
                    enabled = card.enabled,
                    pinned = card.pinned,
                    treeUri = card.treeUri,
                    romDirectory = card.romDirectory,
                    emulatorName = card.emulatorId?.let { emulatorNames[it] },
                    extensions = card.supportedExtensions,
                    gameCount = counts[card.platformId] ?: card.gameCount,
                )
            },
            androidApps = games.filter { it.platformId == ANDROID_PLATFORM_ID }
                .map { LibraryAppRow(it.id, it.displayTitle) }
                .sortedBy { it.label.lowercase() },

            pcGames = games.mapNotNull { g ->
                val launcher = PcLauncherCatalog.forPackage(g.packageName) ?: return@mapNotNull null
                if (g.shortcutId == null && g.launchIntentUri == null) return@mapNotNull null
                if (g.platformId == WINDOWS_PLATFORM_ID) return@mapNotNull null
                PcGameRow(g.id, g.displayTitle, launcher.displayName)
            }.sortedBy { it.title.lowercase() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryManagerUiState())

    fun removeApp(gameId: Long) {
        viewModelScope.launch {
            gameRepository.delete(gameId)
            memoryCardRepository.recountGames(ANDROID_PLATFORM_ID)
        }
    }

    fun onBack(): Boolean {
        val current = _scratch.value
        val step = current.step
        if (step == LibraryStep.LIST) return false

        if (step == LibraryStep.IMPORT_PC) {
           _scratch.update {
                it.copy(
                    step = LibraryStep.CARD_DETAIL,
                    detailPlatformId = WINDOWS_PLATFORM_ID,
                    returnFocusKey = null,
                )
            }
            return true
        }
        if (step == LibraryStep.CARD_DETAIL && current.detailPlatformId == WINDOWS_PLATFORM_ID) {
            _scratch.update {
                it.copy(
                    step = LibraryStep.LIST,
                    detailPlatformId = null,
                    returnFocusKey = WINDOWS_PLATFORM_ID,
                )
            }
            return true
        }
        resetToList()
        return true
    }

    private fun resetToList() {
        _scratch.update {
            it.copy(
                step = LibraryStep.LIST,
                platformOptions = emptyList(),
                emulatorOptions = emptyList(),
                pendingPlatformId = null,
                pendingPlatformName = null,
                pendingDirectory = null,
                pendingEmulatorId = null,
                detailPlatformId = null,
                androidApps = emptyList(),
                pcLaunchers = emptyList(),
                pcGames = emptyList(),
                renameTargetPlatformId = null,
                awaitingRomRootSetup   = false,
            )
        }
    }

    fun dismissMessage() = _scratch.update { it.copy(message = null) }

    fun startAddConsole() {
        viewModelScope.launch {
            val options = memoryCardRepository.unconfiguredPlatforms()

                .filter { it.id != WINDOWS_PLATFORM_ID }
                .map { PlatformOption(it.id, it.name, it.shortName) }
            if (options.isEmpty()) {
                _scratch.update { it.copy(message = "Every supported platform already has a Memory Card.") }
                return@launch
            }
            _scratch.update {
                it.copy(
                    step = LibraryStep.PICK_PLATFORM,
                    platformOptions = options,
                    pendingPlatformId = null,
                    pendingPlatformName = null,
                    pendingDirectory = null,
                    pendingEmulatorId = null,
                    returnFocusKey = ADD_CONSOLE_FOCUS_KEY,
                )
            }
        }
    }

    fun onPlatformChosen(option: PlatformOption) {
        if (option.id == ANDROID_PLATFORM_ID) {
            viewModelScope.launch {
                memoryCardRepository.addCard(
                    platformId = option.id,
                    displayName = defaultDisplayName(option.name),
                    romDirectory = null,
                    emulatorId = null,
                )
                resetToList()
                _scratch.update {
                    it.copy(message = "Android library added. Open it in Games → Find Games to add apps.")
                }
            }
            return
        }

        viewModelScope.launch {
            val roots = romRootRepository.getAll()
            if (roots.isEmpty()) {
                _scratch.update {
                    it.copy(message = "Set up a ROM Root first — grant your ROM folder under ROM Root Access, then add consoles.")
                }
                return@launch
            }
            var chosenRoot = roots.first()
            var folderName = folderHintResolver.esDeFolderName(option.id)
            outer@ for (rootUri in roots) {
                for (name in romScanner.listSubfolderNames(rootUri)) {
                    if (folderHintResolver.detectFromFolderName(name) == option.id) {
                        chosenRoot = rootUri
                        folderName = name
                        break@outer
                    }
                }
            }
            val rootRaw = RomRootRepository.rawPathOfTree(chosenRoot)
            val directory = rootRaw?.let { "${it.trimEnd('/')}/$folderName" }

            if (option.id == WINDOWS_PLATFORM_ID) {
                _scratch.update {
                    it.copy(
                        pendingPlatformId = option.id,
                        pendingPlatformName = option.name,
                        pendingDirectory = directory,
                        pendingEmulatorId = null,
                        step = LibraryStep.SCAN_PROMPT,
                    )
                }
                return@launch
            }
            val options = buildEmulatorOptions(option.id)
            _scratch.update {
                it.copy(
                    pendingPlatformId = option.id,
                    pendingPlatformName = option.name,
                    pendingDirectory = directory,
                    emulatorOptions = options,
                    step = LibraryStep.PICK_EMULATOR,
                )
            }
        }
    }

    fun onEmulatorChosen(option: EmulatorOption) {
        _scratch.update { it.copy(pendingEmulatorId = option.id, step = LibraryStep.SCAN_PROMPT) }
    }

    fun confirmAddConsole(scanNow: Boolean) {
        val s = _scratch.value
        val platformId = s.pendingPlatformId ?: return
        viewModelScope.launch {
            val displayName = defaultDisplayName(s.pendingPlatformName ?: platformId)
            memoryCardRepository.addCard(
                platformId = platformId,
                displayName = displayName,
                romDirectory = s.pendingDirectory,
                emulatorId = s.pendingEmulatorId,
            )

            resetToList()
            if (scanNow) {
                if (platformId == WINDOWS_PLATFORM_ID) scanPcGamesFolder()
                else scanConsole(platformId)
            }
        }
    }

    private fun defaultDisplayName(platformName: String): String = "$platformName Memory Card"

    private suspend fun buildEmulatorOptions(platformId: String?): List<EmulatorOption> {
        val installed =
            platformId?.let { emulatorProfileRepository.getProfilesForPlatform(it) } ?: emptyList()
        return buildList {
            installed.forEach { add(EmulatorOption(it.id, it.name)) }
            add(EmulatorOption(null, "Decide later"))
        }
    }

    fun openCardDetail(platformId: String) {
        _scratch.update {
            it.copy(
                step = LibraryStep.CARD_DETAIL,
                detailPlatformId = platformId,
                returnFocusKey = platformId
            )
        }

        if (platformId == WINDOWS_PLATFORM_ID) {
            viewModelScope.launch { runCatching { windowsLibrarySetup.ensure() } }
        }
    }

    fun toggleEnabled(platformId: String, enabled: Boolean) {
        viewModelScope.launch { memoryCardRepository.setEnabled(platformId, enabled) }
    }

    fun togglePinned(platformId: String, pinned: Boolean) {
        viewModelScope.launch { memoryCardRepository.setPinned(platformId, pinned) }
    }

    fun moveCard(platformId: String, up: Boolean) {
        viewModelScope.launch { memoryCardRepository.move(platformId, up) }
    }

    fun removeCard(platformId: String) {
        viewModelScope.launch {
            memoryCardRepository.remove(platformId)
            if (_scratch.value.detailPlatformId == platformId) resetToList()
        }
    }

    fun beginRename(platformId: String) =
        _scratch.update { it.copy(renameTargetPlatformId = platformId) }

    fun cancelRename() = _scratch.update { it.copy(renameTargetPlatformId = null) }

    fun confirmRename(newName: String) {
        val platformId = _scratch.value.renameTargetPlatformId ?: return
        val trimmed = newName.trim()
        viewModelScope.launch {
            if (trimmed.isNotEmpty()) memoryCardRepository.rename(platformId, trimmed)
            _scratch.update { it.copy(renameTargetPlatformId = null) }
        }
    }

    fun setEmulatorForDetail(option: EmulatorOption) {
        val platformId = _scratch.value.detailPlatformId ?: return
        viewModelScope.launch { memoryCardRepository.setEmulator(platformId, option.id) }
    }

    fun addExtension(platformId: String, ext: String) {
        val clean = ext.trim().lowercase().removePrefix(".").filter { it.isLetterOrDigit() }
        if (clean.isBlank()) return
        viewModelScope.launch {
            val card = memoryCardRepository.getById(platformId) ?: return@launch
            if (clean in card.supportedExtensions) return@launch
            memoryCardRepository.setExtensions(platformId, card.supportedExtensions + clean)
        }
    }

    fun removeExtension(platformId: String, ext: String) {
        viewModelScope.launch {
            val card = memoryCardRepository.getById(platformId) ?: return@launch
            memoryCardRepository.setExtensions(platformId, card.supportedExtensions - ext)
        }
    }

    fun loadEmulatorOptionsForDetail() {
        viewModelScope.launch {
            val platformId = _scratch.value.detailPlatformId
            _scratch.update { it.copy(emulatorOptions = buildEmulatorOptions(platformId)) }
        }
    }

    fun scanVitaGames() {
        if (PSVITA_PLATFORM_ID in _scratch.value.scanningPlatformIds) return
        viewModelScope.launch {
            _scratch.update { it.copy(scanningPlatformIds = it.scanningPlatformIds + PSVITA_PLATFORM_ID) }
            val result = runCatching { vitaGameScanner.scan() }
                .onFailure { Timber.e(it, "Vita scan failed") }
                .getOrNull()
            if (result != null && (result.added > 0 || result.updated > 0)) {
                runCatching { memoryCardRepository.recountGames(PSVITA_PLATFORM_ID) }
            }
            _scratch.update {
                it.copy(
                    scanningPlatformIds = it.scanningPlatformIds - PSVITA_PLATFORM_ID,
                    message = result?.message ?: "Vita scan failed — see the log.",
                )
            }
        }
    }

    fun setVita3KFolder(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            vita3KLibrary.setUx0Folder(uri)
            scanVitaGames()
        }
    }

    fun scrapeArtwork(platformId: String) {
        MetadataScrapeWorker.enqueue(
            context,
            MetadataScrapeWorker.MODE_MISSING,
            platformId = platformId,
        )
    }

    fun scanConsole(platformId: String, removeMissing: Boolean = false) {
        if (platformId in _scratch.value.scanningPlatformIds) return

        if (platformId == PSVITA_PLATFORM_ID) {
            scanVitaGames(); return
        }
        viewModelScope.launch {
            _scratch.update { it.copy(scanningPlatformIds = it.scanningPlatformIds + platformId) }
            val outcome = libraryScanner.scanPlatform(platformId, removeMissing)
            _scratch.update {
                it.copy(
                    scanningPlatformIds = it.scanningPlatformIds - platformId,
                    message = scanOutcomeMessage(outcome, removeMissing),
                )
            }
            Timber.i(
                "Library Manager scan complete for $platformId: " +
                        "${outcome.added} new, ${outcome.markedMissing} marked missing (status=${outcome.status})"
            )
        }
    }

    fun scanAllConsoles(removeMissing: Boolean = false) {
        viewModelScope.launch {
            memoryCardRepository.getAll()
                .filter { it.isScannable() }
                .forEach { scanConsole(it.platformId, removeMissing) }
        }
    }

    fun refreshRomRoots() {
        viewModelScope.launch {
            val persisted = SafGrants.persistedReadUris(context.contentResolver)
            val rows = romRootRepository.getAll().map { uri ->
                RootFolderRow(
                    uri,
                    rootDisplayName(uri),
                    SafGrants.linkStatus(uri, persisted) == FolderLinkStatus.LINKED
                )
            }
            _scratch.update { it.copy(romRoots = rows) }
        }
    }

    fun addRomRoot(uri: Uri) {
        viewModelScope.launch {
            romRootRepository.persist(uri, writable = true)

            romRootRepository.add(uri.toString())
            scanRomRoot()
        }
    }

    fun removeRomRoot(treeUri: String) {
        viewModelScope.launch {
            romRootRepository.remove(treeUri)
        }
    }

    private var pendingRelinkRomRoot: String? = null

    fun beginRelinkRomRoot(treeUri: String): Uri? {
        pendingRelinkRomRoot = treeUri
        return runCatching { Uri.parse(treeUri) }.getOrNull()
    }

    fun onRomRootRelinkPicked(uri: Uri?) {
        val old = pendingRelinkRomRoot ?: return
        pendingRelinkRomRoot = null
        if (uri == null) return
        viewModelScope.launch {
            romRootRepository.persist(uri)
            romRootRepository.replace(old, uri.toString())
            refreshRomRoots()
            scanRomRoot()
        }
    }

    fun openImportPcGames() {
        viewModelScope.launch {
            runCatching { windowsLibrarySetup.ensure() }
        }
        val pm = context.packageManager
        val launchers = PcLauncherCatalog.entries.map { def ->

            val installedPkg = PcLauncherCatalog.verifiedInstalledPackage(def, pm)
            PcLauncherRow(
                type = def.type,
                name = def.displayName,
                installed = installedPkg != null,
                packageName = installedPkg,
                canAddById = PcLauncherAdapters.forType(def.type) != null,
            )
        }
        _scratch.update {
            it.copy(
                step = LibraryStep.IMPORT_PC,
                pcLaunchers = launchers,
                isHomeLauncher = launcherShortcutRepository.isDefaultLauncher(),
                returnFocusKey = IMPORT_PC_FOCUS_KEY,
            )
        }
    }

    fun refreshHomeStatus() {
        _scratch.update { it.copy(isHomeLauncher = launcherShortcutRepository.isDefaultLauncher()) }
    }

    fun homeRoleIntent(): Intent = launcherShortcutRepository.homeRoleRequestIntent()

    fun testLaunchPcGame(row: PcLauncherRow, id: String, source: String?) {
        val pkg = row.packageName ?: return
        val intent = PcLauncherAdapters.forType(row.type, context.packageManager)
            ?.buildLaunchIntent(pkg, id, source)
        if (intent == null) {
            _scratch.update { it.copy(message = INVALID_GAME_ID_MESSAGE) }
            return
        }
        runCatching { context.startActivity(intent) }.onFailure { e ->
            Timber.e(e, "PC test launch failed for ${row.name}")
            _scratch.update {
                it.copy(message = "Test launch failed: ${e.message}. The launcher may block external starts.")
            }
        }
    }

    fun addPcGameById(row: PcLauncherRow, id: String, title: String?, source: String?) {
        val pkg = row.packageName ?: return
        val displayName = title?.trim().orEmpty()
        if (displayName.isBlank()) {
            _scratch.update { it.copy(message = "Enter the game's name — it's shown next to the ID in ${row.name}.") }
            return
        }
        val intent = PcLauncherAdapters.forType(row.type, context.packageManager)
            ?.buildLaunchIntent(pkg, id, source)
        if (intent == null) {
            _scratch.update { it.copy(message = INVALID_GAME_ID_MESSAGE) }
            return
        }
        val intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
        viewModelScope.launch {
            runCatching {
                if (gameRepository.getByIntentUri(intentUri) == null &&
                    findWindowsGame(pkg, displayName) == null
                ) {
                    gameRepository.upsert(
                        Game(
                            title = displayName,
                            platformId = WINDOWS_PLATFORM_ID,
                            packageName = pkg,
                            isManualEntry = true,
                            contentType = GameContentType.GAME,
                            launchIntentUri = intentUri,
                        )
                    )
                }
                ensureWindowsCard()
            }.fold(
                onSuccess = { _scratch.update { it.copy(message = "\"$displayName\" added to Windows Games.") } },
                onFailure = { e ->
                    Timber.e(e, "Add PC game by id failed for ${row.name}")
                    _scratch.update { it.copy(message = "Couldn't add game: ${e.message}") }
                },
            )
        }
    }

    fun scanPcGamesFolder(folder: Uri? = null) {
        viewModelScope.launch {
            if (folder != null) romRootRepository.persist(folder)
            val report = runCatching { pcGameScanner.scan(folder) }
                .onFailure { Timber.e(it, "PC scan failed") }
                .getOrNull()
            if (report == null) {
                _scratch.update { it.copy(message = "PC scan failed — see the log.") }
                return@launch
            }
            if (report.newGames > 0) ensureWindowsCard()
            _scratch.update { it.copy(message = report.message) }
        }
    }

    fun exportManualPcGames() {
        viewModelScope.launch {
            val report = runCatching { pcGameExporter.export() }
                .onFailure { Timber.e(it, "Manual PC game export failed") }
                .getOrNull()
            _scratch.update { it.copy(message = report?.message ?: "Export failed — see the log.") }
        }
    }

    private suspend fun ensureWindowsCard() {
        windowsLibrarySetup.ensure()
        memoryCardRepository.recountGames(WINDOWS_PLATFORM_ID)
    }

    private suspend fun findWindowsGame(packageName: String, title: String): Game? {
        val key = normalizePcTitle(title)
        return gameRepository.getByPlatform(WINDOWS_PLATFORM_ID).firstOrNull {
            it.packageName == packageName && normalizePcTitle(it.displayTitle) == key
        }
    }

    private fun normalizePcTitle(title: String): String =
        title.lowercase().filter { it.isLetterOrDigit() }

    fun importPcGame(row: PcGameRow) {
        viewModelScope.launch {
            runCatching {
                val game = gameRepository.getById(row.gameId) ?: error("Game not found")
                gameRepository.upsert(
                    game.copy(platformId = WINDOWS_PLATFORM_ID, contentType = GameContentType.GAME)
                )
                ensureWindowsCard()
            }.fold(
                onSuccess = { _scratch.update { it.copy(message = "\"${row.title}\" added to Windows Games.") } },
                onFailure = { e ->
                    Timber.e(e, "PC game import failed for ${row.gameId}")
                    _scratch.update { it.copy(message = "Couldn't import \"${row.title}\": ${e.message}") }
                },
            )
        }
    }

    fun importAllPcGames() {
        val rows = uiState.value.pcGames
        if (rows.isEmpty()) return
        viewModelScope.launch {
            var added = 0
            rows.forEach { row ->
                runCatching {
                    val game = gameRepository.getById(row.gameId) ?: error("Game not found")
                    gameRepository.upsert(
                        game.copy(
                            platformId = WINDOWS_PLATFORM_ID,
                            contentType = GameContentType.GAME
                        )
                    )
                    added++
                }.onFailure { Timber.e(it, "PC game import failed for ${row.gameId}") }
            }
            if (added > 0) ensureWindowsCard()
            _scratch.update { it.copy(message = "Imported $added PC game(s) into Windows Games.") }
        }
    }

    fun requestRomFolderSetup() {
        _scratch.update { it.copy(awaitingRomRootSetup = true) }
    }

    fun onRomFolderSetupPicked(uri: Uri?) {
        _scratch.update { it.copy(awaitingRomRootSetup = false) }
        if (uri == null) return
        viewModelScope.launch {
            romRootRepository.persist(uri, writable = true)
            romRootRepository.add(uri.toString())

            val names = memoryCardRepository.availablePlatformCatalog()
                .map { folderHintResolver.esDeFolderName(it.id) }
                .filter { it.isNotBlank() && it != "android" }
                .distinct()

            val result = romScanner.createSubfolders(uri.toString(), names)
            Timber.i("ES-DE setup — root=$uri created=${result.created} existing=${result.existing}")
            _scratch.update {
                it.copy(
                    message = "ROM Root ready: ${result.created} folder(s) created" +
                            (if (result.existing > 0) ", ${result.existing} already there" else "") +
                            ". Copy your games into the matching folders; the root will be scanned automatically when you add or replace it.",
                )
            }
        }
    }

    fun scanRomRoot() {
        viewModelScope.launch {
            val report = romRootScanRunner.scan()
            _scratch.update { it.copy(message = report.message) }
        }
    }
}
