package com.psplauncher.feature.artwork.api

import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.feature.artwork.MetadataRepository
import com.psplauncher.feature.artwork.match.MetadataApply
import com.psplauncher.feature.artwork.match.MetadataApplyPolicy
import com.psplauncher.feature.artwork.match.MetadataField
import com.psplauncher.feature.artwork.match.MetadataPreset
import com.psplauncher.feature.artwork.match.MetadataPreview
import com.psplauncher.feature.artwork.store.ArtworkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class ArtworkFetchResult(
    val gameId: Long,
    val title: String,
    val success: Boolean,
    val skipped: Boolean = false,
    val errorMessage: String? = null,
)

data class ArtworkStatus(
    val total: Int = 0,
    val complete: Int = 0,
    val missing: Int = 0,
    val stale: Int = 0,
)

data class ScrapeProgress(
    val current: Int,
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val title: String,
    val scrapeSource: String = "",
    val scrapeAsset: String = "",

    val stoppedReason: String? = null,
)

fun scrapeStopMessage(reason: SsFailureReason?): String? = when (reason) {
    SsFailureReason.DAILY_QUOTA_EXCEEDED ->
        "ScreenScraper's daily quota for this account ran out, so the rest was skipped. It resets tomorrow."
    SsFailureReason.BAD_DEV_CREDENTIALS ->
        "ScreenScraper rejected the app's developer credentials, so it was skipped. Check Scraping Sources."
    SsFailureReason.API_CLOSED ->
        "ScreenScraper is closed to non-members right now, so it was skipped. Other sources still ran."
    SsFailureReason.DISABLED ->
        "ScreenScraper has no credentials configured, so it was skipped. Other sources still ran."
    else -> null
}

@Singleton
class ArtworkRepository @Inject constructor(
    private val imageCache: ArtworkImageCache,
    private val gameDao: GameDao,
    private val metadataRepository: MetadataRepository,
    private val scrapePreferences: ArtworkScrapePreferences,
    private val artworkStore: ArtworkStore,
    private val internalStore: com.psplauncher.feature.artwork.store.InternalArtworkStore,
    private val ssMediaCacheDao: com.psplauncher.core.data.database.dao.SsMediaCacheDao,
) {
    suspend fun fetchMissingArtwork(
        onProgress: (current: Int, total: Int, title: String) -> Unit,
    ): List<ArtworkFetchResult> = withContext(Dispatchers.IO) {
        val games = gameDao.getGamesWithoutArtwork()
        Timber.i("Metadata fetch started — ${games.size} games need artwork")
        val results = mutableListOf<ArtworkFetchResult>()

        metadataRepository.fetchMissingMetadata { current, total ->
            val title = games.getOrNull(current - 1)?.title ?: ""
            onProgress(current, total, title)
        }

        games.forEach { game ->
            val updated = gameDao.getById(game.id)
            val success = updated?.artworkUri != null
            results += ArtworkFetchResult(game.id, game.title, success,
                errorMessage = if (!success) "No artwork found" else null)
        }

        Timber.i("Metadata fetch complete — ${results.count { it.success }} succeeded")
        results
    }

    suspend fun fetchArtworkForGame(gameId: Long, title: String): ArtworkFetchResult {
        val game = gameDao.getById(gameId)
            ?: return ArtworkFetchResult(gameId, title, false, errorMessage = "Game not found")

        val result = metadataRepository.fetchForGame(
            gameId     = gameId,
            title      = title,
            platformId = game.platformId,
            romPath    = game.romPath,
        )
        return ArtworkFetchResult(gameId, title, result.success, errorMessage = result.message.takeIf { !result.success })
    }

    suspend fun fetchMetadataPreview(gameId: Long): MetadataPreview? = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext null
        val candidates = metadataRepository.fetchCandidates(
            gameId     = gameId,
            title      = game.title,
            platformId = game.platformId,
            romPath    = game.romPath,
            options    = ScrapeOptions(metadataOnly = true, bypassSsCache = true),
        )
        MetadataPreview(MetadataApply.currentOf(game), MetadataApply.presetsFrom(candidates))
    }

    suspend fun applyMetadata(
        gameId: Long,
        incoming: MetadataPreset,
        policy: MetadataApplyPolicy,
        chosen: Set<MetadataField> = emptySet(),
    ): Set<MetadataField> = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext emptySet()
        val plan = MetadataApply.plan(MetadataApply.currentOf(game), incoming, policy, chosen)
        if (plan.isEmpty()) return@withContext emptySet()

        val description     = plan[MetadataField.DESCRIPTION] as String?
        val developer       = plan[MetadataField.DEVELOPER] as String?
        val publisher       = plan[MetadataField.PUBLISHER] as String?
        val releaseYear     = plan[MetadataField.RELEASE_YEAR] as Int?
        val genre           = plan[MetadataField.GENRE] as String?
        val scrapedTitle    = plan[MetadataField.TITLE] as String?
        val ageRating       = plan[MetadataField.AGE_RATING] as String?
        val franchise       = plan[MetadataField.FRANCHISE] as String?
        val communityRating = plan[MetadataField.COMMUNITY_RATING] as Float?
        val releaseDate     = plan[MetadataField.RELEASE_DATE] as String?

        if (policy == MetadataApplyPolicy.FILL_MISSING_ONLY) {
            gameDao.updateMetadataIfMissing(
                id = gameId, description = description, developer = developer, publisher = publisher,
                releaseYear = releaseYear, genre = genre, scrapedTitle = scrapedTitle,
                ageRating = ageRating, franchise = franchise, communityRating = communityRating,
                releaseDate = releaseDate,
            )
        } else {
            gameDao.updateMetadata(
                id = gameId, description = description, developer = developer, publisher = publisher,
                releaseYear = releaseYear, genre = genre, scrapedTitle = scrapedTitle,
                ageRating = ageRating, franchise = franchise, communityRating = communityRating,
                releaseDate = releaseDate,
            )
        }
        plan.keys
    }

    suspend fun clearSsMediaCache() = ssMediaCacheDao.clearAll()

    fun evictFromImageCache(uris: Collection<String>) = imageCache.evict(uris)

    suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        imageCache.diskSizeBytes() + internalStore.footprint().second
    }

    suspend fun clearCache() {
        imageCache.clear()
        artworkStore.deleteAll()
        gameDao.clearAllArtworkRefs()
        ssMediaCacheDao.clearAll()
        Timber.i("Artwork cache + stored artwork state cleared")
    }

    private fun isValidArtworkRef(uri: String?): Boolean = artworkStore.isValidRef(uri)

    private fun primaryArtRefs(g: com.psplauncher.core.data.database.entity.GameEntity) =
        listOf(g.artworkUri, g.boxArtUri, g.logoUri)

    private fun needsArtwork(g: com.psplauncher.core.data.database.entity.GameEntity): Boolean =
        primaryArtRefs(g).any { !isValidArtworkRef(it) }

    suspend fun computeStatus(): ArtworkStatus = withContext(Dispatchers.IO) {
        val games = gameDao.getAll()
        var complete = 0
        var missing = 0
        var stale = 0
        games.forEach { g ->
            val refs = primaryArtRefs(g)
            when {
                refs.all { isValidArtworkRef(it) }                        -> complete++
                refs.any { !it.isNullOrBlank() && !isValidArtworkRef(it) } -> stale++
                else                                                     -> missing++
            }
        }
        ArtworkStatus(total = games.size, complete = complete, missing = missing, stale = stale)
            .also { Timber.i("Artwork status: $it") }
    }

    suspend fun clearAllArtwork() = withContext(Dispatchers.IO) {
        gameDao.clearAllArtwork()
        artworkStore.deleteAll()
        clearCache()
        Timber.i("All artwork cleared (db refs + files + cache)")
    }

    suspend fun reScrapeAllGames(onProgress: (ScrapeProgress) -> Unit): ScrapeProgress =
        withContext(Dispatchers.IO) {
            clearAllArtwork()

            fetchForGames(gameDao.getAll().map { it.id to Triple(it.title, it.platformId, it.romPath) }, onProgress, bypassSsCache = true)
        }

    suspend fun scrapeMissingOnly(onProgress: (ScrapeProgress) -> Unit): ScrapeProgress =
        withContext(Dispatchers.IO) {
            val targets = gameDao.getAll().filter { needsArtwork(it) }

            targets.filter { !it.artworkUri.isNullOrBlank() && !isValidArtworkRef(it.artworkUri) }
                .forEach { gameDao.clearArtworkForGame(it.id) }
            fetchForGames(targets.map { it.id to Triple(it.title, it.platformId, it.romPath) }, onProgress)
        }

    suspend fun scrapeMissingForPlatform(platformId: String, onProgress: (ScrapeProgress) -> Unit): ScrapeProgress =
        withContext(Dispatchers.IO) {
            val targets = gameDao.getAll().filter { it.platformId == platformId && needsArtwork(it) }
            targets.filter { !it.artworkUri.isNullOrBlank() && !isValidArtworkRef(it.artworkUri) }
                .forEach { gameDao.clearArtworkForGame(it.id) }
            fetchForGames(targets.map { it.id to Triple(it.title, it.platformId, it.romPath) }, onProgress)
        }

    suspend fun updateMetadataForPlatform(platformId: String, onProgress: (ScrapeProgress) -> Unit): ScrapeProgress =
        withContext(Dispatchers.IO) {
            val games = gameDao.getAll().filter { it.platformId == platformId }
            fetchForGames(games.map { it.id to Triple(it.title, it.platformId, it.romPath) }, onProgress, metadataOnly = true)
        }

    private suspend fun fetchForGames(
        games: List<Pair<Long, Triple<String, String, String?>>>,
        onProgress: (ScrapeProgress) -> Unit,
        bypassSsCache: Boolean = false,
        metadataOnly: Boolean = false,
    ): ScrapeProgress {
        metadataRepository.resetSsBatchGuards()
        val options = scrapePreferences.getOptions().copy(bypassSsCache = bypassSsCache, metadataOnly = metadataOnly)
        var ok = 0
        var fail = 0
        games.forEachIndexed { index, (id, info) ->
            val (title, platformId, romPath) = info
            onProgress(ScrapeProgress(index + 1, games.size, ok, fail, title))
            val result = runCatching {
                metadataRepository.fetchForGame(
                    gameId   = id,
                    title    = title,
                    platformId = platformId,
                    romPath  = romPath,
                    options  = options,
                    onAssetProgress = { source, asset ->
                        onProgress(ScrapeProgress(index + 1, games.size, ok, fail, title,
                            scrapeSource = source, scrapeAsset = asset))
                    },
                )
            }.getOrNull()
            if (result?.success == true) ok++ else fail++
            if (index < games.size - 1) delay(500)
        }
        return ScrapeProgress(
            games.size, games.size, ok, fail, "",
            stoppedReason = scrapeStopMessage(metadataRepository.ssStopReason),
        )
            .also { Timber.i("Scrape complete: ${it.succeeded} ok, ${it.failed} failed of ${it.total}") }
    }
}
