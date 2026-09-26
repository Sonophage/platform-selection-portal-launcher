package com.psplauncher.feature.library.scanner

import com.psplauncher.core.data.platform.PlatformFolderHintResolver
import com.psplauncher.core.data.repository.MemoryCardRepository
import com.psplauncher.core.data.repository.RomRootRepository
import com.psplauncher.core.domain.repository.GameRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

@Singleton
class RomRootDiscoveryScanner @Inject constructor(
    private val memoryCardRepository: MemoryCardRepository,
    private val romRootRepository: RomRootRepository,
    private val romScanner: RomScanner,
    private val gameRepository: GameRepository,
    private val folderHintResolver: PlatformFolderHintResolver,
    private val existingRomPathResolver: ExistingRomPathResolver,
    private val discSetReconciler: DiscSetReconciler,
) {
    data class Report(
        val scannedFolders: Int,
        val discoveredPlatforms: Set<String>,
        val newCards: Int,
        val totalAdded: Int,
        val skipped: Int,
    )

    suspend fun discover(): Report = discover(romRootRepository.getAll())

    suspend fun discover(roots: List<String>): Report {
        if (roots.isEmpty()) return Report(0, emptySet(), 0, 0, 0)

        val catalog = memoryCardRepository.availablePlatformCatalog().associateBy { it.id }
        val haveCard = memoryCardRepository.getAll().map { it.platformId }.toMutableSet()

        var scannedFolders = 0
        val discovered = mutableSetOf<String>()
        var newCards = 0
        var totalAdded = 0
        var skipped = 0

        for (rootUri in roots) {
            val rootRaw = RomRootRepository.rawPathOfTree(rootUri)
            for (name in romScanner.listSubfolderNames(rootUri)) {
                scannedFolders++
                val platformId = folderHintResolver.detectFromFolderName(name) ?: continue
                val platform = catalog[platformId] ?: continue
                val childDocId = RomRootRepository.childDocIdOf(rootUri, name) ?: continue

                val exts = memoryCardRepository.getById(platformId)?.supportedExtensions
                    ?.takeIf { it.isNotEmpty() } ?: platform.romExtensions
                if (exts.isEmpty()) continue

                val baseline = try {
                    existingRomPathResolver.baselineFor(platformId)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Timber.e(e, "Auto-detect skipped $platformId — could not read its library")
                    skipped++
                    continue
                }

                val found = firstComplete(
                    romScanner.scanTree(
                        rootUri,
                        exts,
                        platformId,
                        true,
                        baseline.romPaths,
                        startDocId = childDocId,
                    )
                )?.newGames.orEmpty()

                if (found.isEmpty()) continue

                if (platformId !in haveCard) {
                    memoryCardRepository.addCard(
                        platformId = platformId,
                        displayName = "${platform.name} Memory Card",
                        romDirectory = rootRaw?.let { "${it.trimEnd('/')}/$name" },
                        emulatorId = null,
                    )
                    haveCard.add(platformId)
                    newCards++
                }
                found.forEach { gameRepository.upsert(it) }

                discSetReconciler.reconcilePlatform(platformId, baseline.games, found)
                memoryCardRepository.recordScan(platformId, System.currentTimeMillis())
                discovered.add(platformId)
                totalAdded += found.size
            }
        }

        if (discovered.isNotEmpty()) {
            Timber.i(
                "ROM root discovery — folders=$scannedFolders systems=${discovered.size} " +
                    "new=$newCards roms=$totalAdded roots=${roots.size}",
            )
        }
        return Report(
            scannedFolders = scannedFolders,
            discoveredPlatforms = discovered,
            newCards = newCards,
            totalAdded = totalAdded,
            skipped = skipped,
        )
    }

    private suspend fun firstComplete(flow: Flow<ScanResult>): ScanResult.Complete? {
        var complete: ScanResult.Complete? = null
        flow.collect { if (it is ScanResult.Complete) complete = it }
        return complete
    }
}
