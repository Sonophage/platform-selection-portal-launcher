package com.psplauncher.feature.library.scanner

import com.psplauncher.core.data.repository.LibraryReconciler
import com.psplauncher.core.data.repository.MemoryCardRepository
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.MemoryCard
import com.psplauncher.core.domain.repository.GameRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ScannerIoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object LibraryScannerModule {
    @Provides
    @ScannerIoDispatcher
    fun provideScannerIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

enum class ScanStatus { COMPLETED, SKIPPED_NO_SOURCE, SKIPPED_BUSY, FAILED }

data class PlatformScanOutcome(
    val platformId: String,
    val displayName: String,
    val status: ScanStatus,
    val added: Int = 0,
    val markedMissing: Int = 0,
    val surveyTrusted: Boolean = false,
    val errorMessage: String? = null,
)

fun MemoryCard.isScannable(): Boolean =
    enabled && (!treeUri.isNullOrBlank() || !romDirectory.isNullOrBlank()) && supportedExtensions.isNotEmpty()

@Singleton
class LibraryScanner @Inject constructor(
    private val memoryCardRepository: MemoryCardRepository,
    private val gameRepository: GameRepository,
    private val scanSourceResolver: ScanSourceResolver,
    private val existingRomPathResolver: ExistingRomPathResolver,
    private val libraryReconciler: LibraryReconciler,
    private val discSetReconciler: DiscSetReconciler,
    @ScannerIoDispatcher private val ioDispatcher: CoroutineDispatcher,

    private val menuSound: com.psplauncher.core.ui.sound.MenuSoundPlayer,
) {
    private val busyPlatforms = ConcurrentHashMap.newKeySet<String>()

    suspend fun scanPlatform(platformId: String, removeMissing: Boolean): PlatformScanOutcome {
        val card = memoryCardRepository.getById(platformId)
            ?: return PlatformScanOutcome(
                platformId  = platformId,
                displayName = platformId,
                status      = ScanStatus.SKIPPED_NO_SOURCE,
                errorMessage = "Memory Card not found.",
            )

        if (!busyPlatforms.add(platformId)) {
            return PlatformScanOutcome(platformId, card.displayName, ScanStatus.SKIPPED_BUSY)
        }
        return try {
            withContext(ioDispatcher) { scanLocked(card, removeMissing) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Timber.e(e, "Library scan failed for $platformId")
            PlatformScanOutcome(
                platformId, card.displayName, ScanStatus.FAILED,
                errorMessage = e.message ?: "Scan failed — see the log.",
            )
        } finally {
            busyPlatforms.remove(platformId)
        }
    }

    suspend fun scanAllEnabled(removeMissing: Boolean): List<PlatformScanOutcome> {
        val eligible = memoryCardRepository.getAll().filter { it.isScannable() }
        val outcomes = eligible.map { scanPlatform(it.platformId, removeMissing) }

        menuSound.play(com.psplauncher.core.ui.sound.MenuSound.NOTIFICATION)
        return outcomes
    }

    private suspend fun scanLocked(card: MemoryCard, removeMissing: Boolean): PlatformScanOutcome {
        val platformId = card.platformId

        val baseline = try {
            existingRomPathResolver.baselineFor(platformId)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Timber.e(e, "Library scan — existing-path baseline failed for $platformId")
            return PlatformScanOutcome(
                platformId, card.displayName, ScanStatus.FAILED,
                errorMessage = e.message ?: "Could not read the library.",
            )
        }
        val dbGames = baseline.games

        val sources = scanSourceResolver.sourcesFor(card)
        if (sources.isEmpty()) {
            return PlatformScanOutcome(platformId, card.displayName, ScanStatus.SKIPPED_NO_SOURCE)
        }

        val existing = baseline.romPaths.toMutableSet()

        var added = 0
        var scanErrored = false
        var firstSourceError: String? = null

        val scannedGames = mutableListOf<Game>()

        var present: MutableSet<String>? = mutableSetOf()
        var firstWriteFailure: String? = null

        sourceLoop@ for (source in sources) {
            source(existing).collect { result ->
                when (result) {
                    is ScanResult.Complete -> {
                        scannedGames.addAll(result.newGames)
                        for (game in result.newGames) {
                            try {
                                gameRepository.upsert(game)
                                game.romPath?.let(existing::add)
                                added++
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                Timber.e(e, "Library scan — upsert failed for $platformId")
                                if (firstWriteFailure == null) {
                                    firstWriteFailure = e.message ?: "Could not save a scanned game."
                                }
                            }
                        }
                        result.presentRomPaths?.let { paths -> present?.addAll(paths) } ?: run { present = null }
                    }
                    is ScanResult.Error -> {
                        scanErrored = true
                        if (firstSourceError == null) firstSourceError = result.message
                    }
                    else -> Unit
                }
            }

            if (firstWriteFailure != null) break@sourceLoop
        }

        if (firstWriteFailure != null) {
            return PlatformScanOutcome(
                platformId   = platformId,
                displayName  = card.displayName,
                status       = ScanStatus.FAILED,
                added        = added,
                surveyTrusted = false,
                errorMessage = firstWriteFailure,
            )
        }

        discSetReconciler.reconcilePlatform(platformId, dbGames, scannedGames)

        var removed = 0
        if (removeMissing) {
            removed = libraryReconciler.reconcile(dbGames, present, scanErrored).markedMissing
        }

        if (added > 0 || removed > 0) {
            memoryCardRepository.recordScan(platformId, System.currentTimeMillis())
            memoryCardRepository.recountGames(platformId)
        }

        Timber.i("Library scan complete for $platformId: $added new, $removed marked missing")
        return PlatformScanOutcome(
            platformId    = platformId,
            displayName   = card.displayName,
            status        = ScanStatus.COMPLETED,
            added         = added,
            markedMissing = removed,
            surveyTrusted = !scanErrored && present != null,
            errorMessage  = if (scanErrored) firstSourceError else null,
        )
    }
}

fun scanOutcomeMessage(outcome: PlatformScanOutcome, removeMissing: Boolean): String =
    when (outcome.status) {
        ScanStatus.SKIPPED_NO_SOURCE ->
            "${outcome.displayName}: ${outcome.errorMessage ?: "ROM folder not configured."}"
        ScanStatus.SKIPPED_BUSY -> "${outcome.displayName}: scan already in progress."
        ScanStatus.FAILED -> "${outcome.displayName}: ${outcome.errorMessage ?: "scan failed."}"
        ScanStatus.COMPLETED ->
            "${outcome.displayName}: " + buildString {
                append(if (outcome.added == 0) "no new ROMs" else "${outcome.added} new ROM(s) added")
                if (removeMissing) {
                    append(if (outcome.markedMissing == 0) ", none missing" else ", ${outcome.markedMissing} marked missing")
                }
                outcome.errorMessage?.let { append(" ($it)") }
            }
    }
