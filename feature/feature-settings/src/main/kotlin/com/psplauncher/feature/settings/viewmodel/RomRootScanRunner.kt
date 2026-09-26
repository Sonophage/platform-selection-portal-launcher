package com.psplauncher.feature.settings.viewmodel

import com.psplauncher.core.data.repository.MemoryCardRepository
import com.psplauncher.core.data.repository.RomRootRepository
import com.psplauncher.feature.library.scanner.LibraryScanner
import com.psplauncher.feature.library.scanner.RomRootDiscoveryScanner
import com.psplauncher.feature.library.scanner.ScanStatus
import com.psplauncher.feature.settings.pc.PcGameScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class RomRootScanReport(
    val scannedFolders: Int,
    val systemsWithGames: Int,
    val newCards: Int,
    val totalAdded: Int,
    val skipped: Int,
    val rootsCount: Int,
    val message: String,
)

@Singleton
class RomRootScanRunner @Inject constructor(
    private val romRootRepository: RomRootRepository,
    private val memoryCardRepository: MemoryCardRepository,
    private val romRootDiscoveryScanner: RomRootDiscoveryScanner,
    private val libraryScanner: LibraryScanner,
    private val pcGameScanner: PcGameScanner,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var inFlight: Job? = null

    fun kickoff() {
        if (inFlight?.isActive == true) return
        inFlight = scope.launch {
            runCatching { scan() }
                .onFailure { Timber.w(it, "Wizard ROM root scan failed") }
        }
    }

    suspend fun scan(): RomRootScanReport {
        val roots = romRootRepository.getAll()
        if (roots.isEmpty()) {
            return RomRootScanReport(
                scannedFolders = 0, systemsWithGames = 0, newCards = 0,
                totalAdded = 0, skipped = 0, rootsCount = 0,
                message = "Add a ROM Root first in Settings → Folder Access.",
            )
        }

        val catalog = memoryCardRepository.availablePlatformCatalog().associateBy { it.id }
        val haveCard = memoryCardRepository.getAll().map { it.platformId }.toMutableSet()

        var scannedFolders = 0
        val platformsWithGames = mutableSetOf<String>()
        var newCards = 0
        var totalAdded = 0
        var skipped = 0

        val discovery = romRootDiscoveryScanner.discover(roots)
        scannedFolders = discovery.scannedFolders
        platformsWithGames.addAll(discovery.discoveredPlatforms)
        newCards = discovery.newCards
        totalAdded = discovery.totalAdded
        skipped = discovery.skipped

        haveCard.filter { it != "windows" }.forEach { platformId ->
            val outcome = libraryScanner.scanPlatform(platformId, removeMissing = true)
            if (outcome.status == ScanStatus.COMPLETED &&
                (outcome.added > 0 || outcome.markedMissing > 0)
            ) {
                platformsWithGames.add(platformId)
                totalAdded += outcome.added
            }
        }

        val hadWindowsCard = "windows" in haveCard
        val pcReport = runCatching { pcGameScanner.scan() }
            .onFailure { Timber.e(it, "Auto-detect PC scan failed") }
            .getOrNull()
        if (!hadWindowsCard && memoryCardRepository.getById("windows") != null) {
            haveCard.add("windows")
            newCards++
        }
        if (pcReport != null && pcReport.newGames > 0) {
            platformsWithGames.add("windows")
            totalAdded += pcReport.newGames
        }

        val rootLabel = "${roots.size} root${if (roots.size == 1) "" else "s"}"
        val message = buildString {
            if (platformsWithGames.isEmpty()) {
                append("Scanned $scannedFolders folder(s) across $rootLabel; no new ROMs found. ")
                append("Copy games into the matching system folders and try again.")
            } else {
                append("Loaded ${platformsWithGames.size} system(s)")
                if (newCards > 0) append(" ($newCards new console(s))")
                append(", $totalAdded ROM(s) from $rootLabel.")
            }
            if (skipped > 0) append(" $skipped folder(s) skipped (library unreadable).")
        }
        Timber.i("ROM root autoload — folders=$scannedFolders systems=${platformsWithGames.size} new=$newCards roms=$totalAdded roots=${roots.size}")
        return RomRootScanReport(
            scannedFolders = scannedFolders,
            systemsWithGames = platformsWithGames.size,
            newCards = newCards,
            totalAdded = totalAdded,
            skipped = skipped,
            rootsCount = roots.size,
            message = message,
        )
    }
}
