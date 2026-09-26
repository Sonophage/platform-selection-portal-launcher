package com.psplauncher.core.data.repository

import com.psplauncher.core.data.database.dao.GameDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkLinkRepair @Inject constructor(
    private val gameDao: GameDao,
    private val artworkAccent: ArtworkAccent,
) {
    data class Report(val checked: Int, val repointed: Int, val cleared: Int) {
        val broken: Int get() = repointed + cleared

        fun message(): String = when {
            checked == 0 -> "No games to check."
            broken == 0 -> "All $checked background links resolve."
            cleared == 0 -> "Repaired $repointed of $checked background links."
            else -> "Repaired $repointed and cleared $cleared of $checked background links."
        }
    }

    suspend fun run(): Report = withContext(Dispatchers.IO) {
        val games = gameDao.getAll()
        var checked = 0
        var repointed = 0
        var cleared = 0
        for (game in games) {
            val current = game.artworkUri?.takeIf { it.isNotBlank() } ?: continue
            checked++
            if (artworkAccent.isReadable(current)) continue

            val replacement = artworkAccent.firstReadable(game.heroUri, game.boxArtUri, game.iconUri)
            gameDao.updateArtwork(game.id, replacement)
            if (replacement != null) repointed++ else cleared++
        }
        Report(checked, repointed, cleared).also {
            Timber.i("ArtworkLinkRepair: ${it.message()}")
        }
    }
}
