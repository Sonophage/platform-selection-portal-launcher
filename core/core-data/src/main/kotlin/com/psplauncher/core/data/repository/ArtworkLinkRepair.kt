package com.psplauncher.core.data.repository

import com.psplauncher.core.data.database.dao.GameDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repairs `games.artwork_uri` rows that name an image no longer on disk.
 *
 * The column is the XMB's per-game backdrop slot, and on a real library 125 of 147 rows pointed
 * at `{filesDir}/artwork/<id>/hero.jpg` inside an internal store that is now empty — the art
 * itself having moved to the user's SAF artwork tree, which `hero_uri` already names. Nothing
 * errored: the backdrop simply resolved to nothing and the wallpaper showed instead, for years
 * of rows, for as long as nobody compared the column against the filesystem.
 *
 * The filename those dead paths carry is `hero.jpg`, so the column held a copy of the hero to
 * begin with. Repointing it at the real hero restores what it used to mean rather than inventing
 * a new one.
 */
@Singleton
class ArtworkLinkRepair @Inject constructor(
    private val gameDao: GameDao,
    private val artworkAccent: ArtworkAccent,
) {

    /** What a run did. [repointed] + [cleared] is how many rows were wrong. */
    data class Report(val checked: Int, val repointed: Int, val cleared: Int) {
        val broken: Int get() = repointed + cleared

        fun message(): String = when {
            checked == 0 -> "No games to check."
            broken == 0 -> "All $checked background links resolve."
            cleared == 0 -> "Repaired $repointed of $checked background links."
            else -> "Repaired $repointed and cleared $cleared of $checked background links."
        }
    }

    /**
     * Checks every game that names a backdrop and repairs the ones that do not resolve.
     *
     * A row is only touched when its current value FAILS to decode, so a working link is never
     * second-guessed and a run over an already-healthy library writes nothing at all. When no
     * candidate decodes the row is cleared rather than left pointing at a file that is not
     * there: null is a fact the UI can act on, a dead path is one it has to discover.
     */
    suspend fun run(): Report = withContext(Dispatchers.IO) {
        val games = gameDao.getAll()
        var checked = 0
        var repointed = 0
        var cleared = 0
        for (game in games) {
            val current = game.artworkUri?.takeIf { it.isNotBlank() } ?: continue
            checked++
            if (artworkAccent.isReadable(current)) continue
            // Same order the XMB's backdrop resolver prefers, so the repaired column agrees with
            // what the screen would have fallen back to anyway.
            val replacement = artworkAccent.firstReadable(game.heroUri, game.boxArtUri, game.iconUri)
            gameDao.updateArtwork(game.id, replacement)
            if (replacement != null) repointed++ else cleared++
        }
        Report(checked, repointed, cleared).also {
            Timber.i("ArtworkLinkRepair: ${it.message()}")
        }
    }
}
