package com.psplauncher.feature.library.scanner

import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.repository.GameRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

@Singleton
class DiscSetReconciler @Inject constructor(
    private val discSetBuilder: DiscSetBuilder,
    private val m3uPlaylistReader: M3uPlaylistReader,
    private val discRegionReader: DiscRegionReader,
    private val gameRepository: GameRepository,
) {
    suspend fun reconcilePlatform(platformId: String, existingRows: List<Game>, newRows: List<Game>): Int {
        var corrected = 0
        discSetBuilder.reconcile(existingRows + newRows, discRegionReader::read, m3uPlaylistReader::read)
            .forEach { changed ->
                try {
                    gameRepository.upsert(changed)
                    corrected++
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Timber.e(e, "Library scan — disc-set reconcile upsert failed for $platformId")
                }
            }
        return corrected
    }
}
