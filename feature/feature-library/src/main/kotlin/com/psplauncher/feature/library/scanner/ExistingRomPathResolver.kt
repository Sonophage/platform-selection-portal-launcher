package com.psplauncher.feature.library.scanner

import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.repository.GameRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class ExistingRomPathResolver @Inject constructor(
    private val gameRepository: GameRepository,
) {
    data class Baseline(val games: List<Game>, val romPaths: Set<String>)

    suspend fun baselineFor(platformId: String): Baseline {
        val games = try {
            gameRepository.getByPlatform(platformId)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            throw IllegalStateException("Could not read the library for $platformId: ${e.message}", e)
        }

        return Baseline(games, games.mapNotNull { it.romPath }.toSet())
    }
}
