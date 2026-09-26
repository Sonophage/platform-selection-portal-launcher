package com.psplauncher.core.domain.repository

import com.psplauncher.core.domain.model.PlayState
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.PlaySession
import com.psplauncher.core.domain.model.RecentPlatform
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    fun observeAll(): Flow<List<Game>>

    fun observeGamesOnly(): Flow<List<Game>>

    fun observeAllGames(): Flow<List<Game>>
    fun observeFavorites(): Flow<List<Game>>

    fun observeByPlayState(state: PlayState): Flow<List<Game>>

    fun observePlayStateCount(state: PlayState): Flow<Int>

    fun observeRecentlyAdded(): Flow<List<Game>>

    fun observeRecentlyAddedCount(): Flow<Int>
    fun observeByPlatform(platformId: String): Flow<List<Game>>

    fun observePlatformGames(platformId: String): Flow<List<Game>>

    suspend fun getByPlatform(platformId: String): List<Game>

    fun observeRecentlyPlayed(limit: Int): Flow<List<Game>>
    fun observeRecentPlatforms(limit: Int): Flow<List<RecentPlatform>>
    suspend fun getById(id: Long): Game?

    suspend fun getDiscSetMembers(discSetKey: String): List<Game>
    suspend fun getByPackageName(packageName: String): Game?

    suspend fun getAppEntry(packageName: String): Game?

    suspend fun getLauncherShortcut(packageName: String, shortcutId: String): Game?

    suspend fun getByIntentUri(intentUri: String): Game?
    suspend fun upsert(game: Game): Long
    suspend fun delete(id: Long)
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    suspend fun setPlayState(id: Long, state: PlayState?)
    suspend fun updateFavoriteSortOrder(id: Long, order: Int)
    suspend fun updateNote(id: Long, note: String?)
    suspend fun updateBoxArt(id: Long, uri: String?)
    suspend fun updateHeroArt(id: Long, uri: String?)
    suspend fun updateLogoArt(id: Long, uri: String?)
    suspend fun updateIconArt(id: Long, uri: String?)
    suspend fun setPreferredEmulator(id: Long, profileIdOrPackage: String?)

    suspend fun clearPreferredEmulatorForPlatform(platformId: String)

    suspend fun setPreferredDisc(id: Long, discId: Long)

    suspend fun clearLastPlayed(id: Long)
    suspend fun recordPlaySession(session: PlaySession)

    suspend fun markOpened(id: Long, playedAt: Long)
    suspend fun getMissingRoms(): List<Game>
    suspend fun updateScrapedTitle(id: Long, scrapedTitle: String?)

    suspend fun updateStorefrontIdentity(id: Long, storefront: String?, storefrontGameId: String?)

    suspend fun attachLauncherHandle(
        id: Long,
        packageName: String?,
        shortcutId: String?,
        launchIntentUri: String?,
    )

    suspend fun updateProviderMatch(id: Long, provider: String, providerGameId: Long?)

    suspend fun getByStorefront(storefront: String, storefrontGameId: String): List<Game>

    suspend fun updateUserTitleOverride(id: Long, override: String?)

    suspend fun updateBoxArtTile(id: Long, uri: String?)
    suspend fun updatePhysicalMediaArt(id: Long, uri: String?)
    suspend fun updateBox3dArt(id: Long, uri: String?)

    suspend fun setIconDisplayMode(id: Long, mode: String?)

    fun observeMissing(): Flow<List<Game>>
    suspend fun markSeen(romPaths: List<String>, seenAt: Long)
    suspend fun markMissing(romPaths: List<String>)
}
