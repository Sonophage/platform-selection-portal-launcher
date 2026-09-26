package com.psplauncher.core.data.repository

import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.repository.GameRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryReconciler @Inject constructor(
    private val gameRepository: GameRepository,
) {
    data class Result(val markedSeen: Int, val markedMissing: Int, val skipped: Boolean)

    suspend fun reconcile(
        dbGames: List<Game>,
        present: Set<String>?,
        scanErrored: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Result {
        val romPaths = dbGames.mapNotNull { it.romPath }

        if (scanErrored || present == null) return Result(0, 0, skipped = true)

        if (present.isEmpty() && romPaths.isNotEmpty()) return Result(0, 0, skipped = true)

        val seen = romPaths.filter { it in present }
        val gone = romPaths.filterNot { it in present }

        if (seen.isNotEmpty()) gameRepository.markSeen(seen, now)
        if (gone.isNotEmpty()) gameRepository.markMissing(gone)

        return Result(markedSeen = seen.size, markedMissing = gone.size, skipped = false)
    }
}
