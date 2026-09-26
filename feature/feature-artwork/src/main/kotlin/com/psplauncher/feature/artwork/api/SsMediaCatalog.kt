package com.psplauncher.feature.artwork.api

import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.core.data.database.dao.SsMediaCacheDao
import com.psplauncher.core.data.database.entity.SsMediaCacheEntity
import com.psplauncher.feature.artwork.rom.RomHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SsMediaCatalog @Inject constructor(
    private val screenScraper: ScreenScraperApi,
    private val ssMediaCacheDao: SsMediaCacheDao,
    private val gameDao: GameDao,
    private val romHasher: RomHasher,
) {
    private val inFlight = Mutex()

    suspend fun mediasFor(gameId: Long, matchedSsId: Long? = null): List<SsCachedMedia>? = inFlight.withLock {
        withContext(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@withContext null

            if (matchedSsId != null && matchedSsId != game.ssId) {
                ssMediaCacheDao.get(matchedSsId)?.let { row ->
                    SsMediaSelection.decode(row.mediasJson)?.let { return@withContext it }
                }
                if (!screenScraper.isEnabled()) return@withContext null
                val info = screenScraper.fetchGameInfo(game.platformId, rom = null, ssGameId = matchedSsId).info
                    ?: return@withContext null
                if (info.medias.isNotEmpty()) {
                    ssMediaCacheDao.upsert(
                        SsMediaCacheEntity(matchedSsId, SsMediaSelection.encode(info.medias), System.currentTimeMillis())
                    )
                }
                Timber.i("SS catalog lookup for title-matched ssId=$matchedSsId (gameId=$gameId, not persisted) → ${info.medias.size} medias")
                return@withContext info.medias
            }

            game.ssId?.let { id ->
                ssMediaCacheDao.get(id)?.let { row ->
                    SsMediaSelection.decode(row.mediasJson)?.let { return@withContext it }
                }
            }

            if (!screenScraper.isEnabled()) return@withContext null
            val rom = if (game.ssId == null) romHasher.identify(game.romPath, game.romUri) else null
            val info = screenScraper.fetchGameInfo(game.platformId, rom, game.ssId).info
                ?: return@withContext null

            val matchedId = info.ssId
            if (matchedId != null && info.medias.isNotEmpty()) {
                ssMediaCacheDao.upsert(
                    SsMediaCacheEntity(matchedId, SsMediaSelection.encode(info.medias), System.currentTimeMillis())
                )
            }

            gameDao.updateMetadata(
                id = gameId,
                description = info.description,
                developer = info.developer,
                publisher = info.publisher,
                releaseYear = info.releaseYear,
                genre = info.genre,
                players = info.players,
                ageRating = info.ageRating,
                franchise = info.franchise,
                communityRating = info.communityRating,
                releaseDate = info.releaseDate,
                ssId = matchedId,
                romCrc32 = rom?.crc32,
            )

            if (game.userTitleOverride == null) {
                info.title?.let { gameDao.fillScrapedTitleIfMissing(gameId, it) }
            }
            Timber.i("SS catalog live lookup for gameId=$gameId → ssId=$matchedId, ${info.medias.size} medias")
            info.medias
        }
    }
}
