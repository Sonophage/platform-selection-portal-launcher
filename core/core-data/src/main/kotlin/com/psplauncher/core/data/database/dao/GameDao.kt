package com.psplauncher.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.psplauncher.core.data.database.entity.GameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query(
        """
        SELECT * FROM games
        WHERE (disc_set_key IS NULL AND is_missing = 0)
           OR (disc_set_key IS NOT NULL AND is_disc_primary = 1
               AND EXISTS (
                   SELECT 1 FROM games member
                   WHERE member.disc_set_key = games.disc_set_key
                     AND member.is_missing = 0
               ))
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE content_type = 'GAME'  AND is_missing = 0 ORDER BY title COLLATE NOCASE ASC")
    fun observeGamesOnly(): Flow<List<GameEntity>>

    @Query(
        """
        SELECT * FROM games
        WHERE content_type = 'GAME'
          AND (
              (disc_set_key IS NULL AND is_missing = 0)
              OR (disc_set_key IS NOT NULL AND is_disc_primary = 1
                  AND EXISTS (
                      SELECT 1 FROM games member
                      WHERE member.disc_set_key = games.disc_set_key
                        AND member.is_missing = 0
                  ))
          )
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    fun observeAllGames(): Flow<List<GameEntity>>

    @Query("UPDATE games SET content_type = :contentType WHERE id = :id")
    suspend fun setContentType(id: Long, contentType: String)

    @Query(
        """
        SELECT * FROM games
        WHERE (
            (disc_set_key IS NULL AND is_favorite = 1 AND is_missing = 0)
            OR (disc_set_key IS NOT NULL AND is_disc_primary = 1
                AND EXISTS (
                    SELECT 1 FROM games member
                    WHERE member.disc_set_key = games.disc_set_key
                      AND member.is_missing = 0
                )
                AND EXISTS (
                    SELECT 1 FROM games member
                    WHERE member.disc_set_key = games.disc_set_key
                      AND member.is_favorite = 1
                ))
        )
        ORDER BY favorite_sort_order ASC, title ASC
        """
    )
    fun observeFavorites(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE platform_id = :platformId AND is_missing = 0 ORDER BY title COLLATE NOCASE ASC")
    fun observeByPlatform(platformId: String): Flow<List<GameEntity>>

    @Query(
        """
        SELECT * FROM games
        WHERE platform_id = :platformId
          AND (
              (disc_set_key IS NULL AND is_missing = 0)
              OR (disc_set_key IS NOT NULL AND is_disc_primary = 1
                  AND EXISTS (
                      SELECT 1 FROM games member
                      WHERE member.disc_set_key = games.disc_set_key
                        AND member.is_missing = 0
                  ))
          )
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    fun observePlatformGames(platformId: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE platform_id = :platformId ORDER BY title COLLATE NOCASE ASC")
    suspend fun getByPlatformOnce(platformId: String): List<GameEntity>

    @Query("UPDATE games SET platform_id = :platformId, content_type = :contentType WHERE id = :id")
    suspend fun setPlatformAndContentType(id: Long, platformId: String, contentType: String)

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: Long): GameEntity?

    @Query("SELECT * FROM games WHERE disc_set_key = :discSetKey " +
            "ORDER BY is_disc_primary DESC, disc_number IS NULL ASC, disc_number ASC, id ASC"
    )
    suspend fun getDiscSetMembers(discSetKey: String): List<GameEntity>

    @Query("UPDATE games SET is_disc_primary = 0 WHERE disc_set_key = :discSetKey AND id != :gameId")
    suspend fun clearOtherDiscPrimaries(discSetKey: String, gameId: Long)

    @Query("UPDATE games SET is_disc_primary = CASE WHEN id = :discId THEN 1 ELSE 0 END WHERE disc_set_key = (SELECT disc_set_key FROM games WHERE id = :id)")
    suspend fun setPreferredDisc(id: Long, discId: Long)

    @Query("SELECT * FROM games WHERE rom_path = :romPath LIMIT 1")
    suspend fun getByRomPath(romPath: String): GameEntity?

    @Query("SELECT * FROM games WHERE package_name = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): GameEntity?

    @Query("SELECT * FROM games WHERE package_name = :packageName AND launch_shortcut_id IS NULL LIMIT 1")
    suspend fun getAppEntry(packageName: String): GameEntity?

    @Query("SELECT * FROM games WHERE package_name = :packageName AND launch_shortcut_id = :shortcutId LIMIT 1")
    suspend fun getLauncherShortcut(packageName: String, shortcutId: String): GameEntity?

    @Query("SELECT * FROM games WHERE launch_intent_uri = :intentUri LIMIT 1")
    suspend fun getByIntentUri(intentUri: String): GameEntity?

    @Query("SELECT * FROM games WHERE last_played_at IS NOT NULL  AND is_missing = 0 ORDER BY last_played_at DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int): Flow<List<GameEntity>>

    @Query(
        """
        SELECT * FROM games
        WHERE platform_id = :platformId
          AND last_played_at IS NOT NULL  
          AND is_missing = 0
        ORDER BY last_played_at DESC
        LIMIT :limit
        """
    )
    fun observeRecentByPlatform(platformId: String, limit: Int): Flow<List<GameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: GameEntity): Long

    @Query("SELECT date_added FROM games WHERE id = :id")
    suspend fun dateAddedOf(id: Long): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(games: List<GameEntity>)

    @Update
    suspend fun update(game: GameEntity)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM games WHERE platform_id = :platformId")
    suspend fun deleteByPlatform(platformId: String)

    @Query("SELECT COUNT(*) FROM games WHERE platform_id = :platformId AND is_missing = 0")
    suspend fun countByPlatform(platformId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM games primary_game
        WHERE primary_game.platform_id = :platformId
          AND primary_game.content_type = 'GAME'
          AND (
              (primary_game.disc_set_key IS NULL AND primary_game.is_missing = 0)
              OR (primary_game.disc_set_key IS NOT NULL AND primary_game.is_disc_primary = 1
                  AND EXISTS (
                      SELECT 1 FROM games member
                      WHERE member.disc_set_key = primary_game.disc_set_key
                        AND member.is_missing = 0
                  ))
          )
        """
    )
    suspend fun countGamesByPlatform(platformId: String): Int

    @Query("UPDATE games SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM games WHERE play_state = :state AND is_missing = 0")
    fun observePlayStateCount(state: String): kotlinx.coroutines.flow.Flow<Int>

    @Query(
        """
        SELECT * FROM games
        WHERE date_added > 0 AND is_missing = 0
        ORDER BY date_added DESC, id DESC
        """
    )
    fun observeRecentlyAdded(): kotlinx.coroutines.flow.Flow<List<GameEntity>>

    @Query("SELECT COUNT(*) FROM games WHERE date_added > 0 AND is_missing = 0")
    fun observeRecentlyAddedCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT * FROM games WHERE play_state = :state AND is_missing = 0 ORDER BY title COLLATE NOCASE")
    fun observeByPlayState(state: String): kotlinx.coroutines.flow.Flow<List<GameEntity>>

    @Query("UPDATE games SET play_state = :state WHERE id = :id")
    suspend fun setPlayState(id: Long, state: String?)

    @Query("UPDATE games SET favorite_sort_order = :order WHERE id = :id")
    suspend fun updateFavoriteSortOrder(id: Long, order: Int)

    @Query("UPDATE games SET user_note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)

    @Query("UPDATE games SET artwork_uri = :artworkUri WHERE id = :id")
    suspend fun updateArtwork(id: Long, artworkUri: String?)

    @Query("UPDATE games SET hero_uri = :heroUri WHERE id = :id")
    suspend fun updateHero(id: Long, heroUri: String?)

    @Query("UPDATE games SET logo_uri = :logoUri WHERE id = :id")
    suspend fun updateLogo(id: Long, logoUri: String?)

    @Query("UPDATE games SET hero_uri = :heroUri, logo_uri = :logoUri WHERE id = :id")
    suspend fun updateHeroAndLogo(id: Long, heroUri: String?, logoUri: String?)

    @Query(
        """
        UPDATE games
        SET total_play_time_millis = total_play_time_millis + :durationMillis,
            last_played_at = :playedAt
        WHERE id = :id
    """
    )
    suspend fun addPlayTime(id: Long, durationMillis: Long, playedAt: Long)

    @Query("UPDATE games SET last_played_at = :playedAt WHERE id = :id")
    suspend fun markOpened(id: Long, playedAt: Long)

    @Query("UPDATE games SET last_played_at = NULL WHERE id = :id")
    suspend fun clearLastPlayed(id: Long)

    @Query(
        """
        UPDATE games SET emulator_package = :emulatorPackage WHERE id = :id
    """
    )
    suspend fun setPreferredEmulator(id: Long, emulatorPackage: String?)

    @Query(
        "UPDATE games SET emulator_package = NULL " +
            "WHERE platform_id = :platformId AND content_type = 'GAME' AND emulator_package IS NOT NULL"
    )
    suspend fun clearPreferredEmulatorForPlatform(platformId: String)

    @Query("SELECT id, rom_path FROM games WHERE rom_path IS NOT NULL")
    suspend fun getAllRomPaths(): List<RomPathProjection>

    @Query("UPDATE games SET is_missing = 0, last_seen_at = :seenAt WHERE rom_path IN (:romPaths)")
    suspend fun markSeen(romPaths: List<String>, seenAt: Long)

    @Query("UPDATE games SET is_missing = 1 WHERE rom_path IN (:romPaths)")
    suspend fun markMissing(romPaths: List<String>)

    @Query(
        """
        SELECT * FROM games
        WHERE (disc_set_key IS NULL AND is_missing = 1)
           OR (disc_set_key IS NOT NULL AND is_disc_primary = 1
               AND NOT EXISTS (
                   SELECT 1 FROM games member
                   WHERE member.disc_set_key = games.disc_set_key
                     AND member.is_missing = 0
               ))
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    fun observeMissing(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAll(): List<GameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(games: List<GameEntity>)

    @Query("SELECT * FROM games WHERE artwork_uri IS NULL AND rom_path IS NOT NULL")
    suspend fun getGamesWithoutArtwork(): List<GameEntity>

    @Query(
        """
        UPDATE games SET
            description     = COALESCE(:description,  description),
            developer       = COALESCE(:developer,    developer),
            publisher       = COALESCE(:publisher,    publisher),
            release_year    = COALESCE(:releaseYear,  release_year),
            genre           = COALESCE(:genre,        genre),
            artwork_uri     = COALESCE(:artworkUri,   artwork_uri),
            hero_uri        = COALESCE(:heroUri,      hero_uri),
            logo_uri        = COALESCE(:logoUri,      logo_uri),
            icon_uri        = COALESCE(:iconUri,      icon_uri),
            box_art_uri     = COALESCE(:boxArtUri,    box_art_uri),
            physical_media_uri = COALESCE(:physicalMediaUri, physical_media_uri),
            box3d_uri       = COALESCE(:box3dUri,     box3d_uri),
            scraped_title   = COALESCE(:scrapedTitle, scraped_title),
            players         = COALESCE(:players,      players),
            age_rating      = COALESCE(:ageRating,    age_rating),
            franchise       = COALESCE(:franchise,    franchise),
            community_rating = COALESCE(:communityRating, community_rating),
            release_date    = COALESCE(:releaseDate,  release_date),
            ss_id           = COALESCE(:ssId,         ss_id),
            igdb_id         = COALESCE(:igdbId,       igdb_id),
            steam_grid_db_id = COALESCE(:steamGridDbId, steam_grid_db_id),
            rom_crc32       = COALESCE(:romCrc32,     rom_crc32)
        WHERE id = :id
    """
    )
    suspend fun updateMetadata(
        id: Long,
        description: String? = null,
        developer: String? = null,
        publisher: String? = null,
        releaseYear: Int? = null,
        genre: String? = null,
        artworkUri: String? = null,
        heroUri: String? = null,
        logoUri: String? = null,
        iconUri: String? = null,
        boxArtUri: String? = null,
        physicalMediaUri: String? = null,
        box3dUri: String? = null,
        scrapedTitle: String? = null,
        players: String? = null,
        ageRating: String? = null,
        franchise: String? = null,
        communityRating: Float? = null,
        releaseDate: String? = null,
        ssId: Long? = null,
        igdbId: Long? = null,
        steamGridDbId: Long? = null,
        romCrc32: String? = null,
    )

    @Query("UPDATE games SET scraped_title = :scrapedTitle WHERE id = :id")
    suspend fun updateScrapedTitle(id: Long, scrapedTitle: String?)

    @Query("UPDATE games SET scraped_title = :scrapedTitle WHERE id = :id AND scraped_title IS NULL")
    suspend fun fillScrapedTitleIfMissing(id: Long, scrapedTitle: String)

    @Query(
        """
        UPDATE games SET
            storefront         = COALESCE(:storefront,       storefront),
            storefront_game_id = COALESCE(:storefrontGameId, storefront_game_id)
        WHERE id = :id
    """
    )
    suspend fun updateStorefrontIdentity(id: Long, storefront: String?, storefrontGameId: String?)

    @Query(
        """
        UPDATE games SET
            package_name = :packageName,
            launch_shortcut_id = :shortcutId,
            launch_intent_uri = :launchIntentUri
        WHERE id = :id
    """
    )
    suspend fun attachLauncherHandle(
        id: Long,
        packageName: String?,
        shortcutId: String?,
        launchIntentUri: String?,
    )

    @Query(
        """
        UPDATE games SET
            ss_id            = CASE WHEN :provider = 'SCREENSCRAPER' THEN :providerGameId ELSE ss_id            END,
            igdb_id          = CASE WHEN :provider = 'IGDB'          THEN :providerGameId ELSE igdb_id          END,
            steam_grid_db_id = CASE WHEN :provider = 'STEAMGRIDDB'   THEN :providerGameId ELSE steam_grid_db_id END
        WHERE id = :id
    """
    )
    suspend fun updateProviderMatch(id: Long, provider: String, providerGameId: Long?)

    @Query("SELECT * FROM games WHERE storefront = :storefront AND storefront_game_id = :storefrontGameId")
    suspend fun getByStorefront(storefront: String, storefrontGameId: String): List<GameEntity>

    @Query(
        """
        UPDATE games SET
            description      = COALESCE(description,      :description),
            developer        = COALESCE(developer,        :developer),
            publisher        = COALESCE(publisher,        :publisher),
            release_year     = COALESCE(release_year,     :releaseYear),
            genre            = COALESCE(genre,            :genre),
            scraped_title    = COALESCE(scraped_title,    :scrapedTitle),
            age_rating       = COALESCE(age_rating,       :ageRating),
            franchise        = COALESCE(franchise,        :franchise),
            community_rating = COALESCE(community_rating, :communityRating),
            release_date     = COALESCE(release_date,     :releaseDate)
        WHERE id = :id
    """
    )
    suspend fun updateMetadataIfMissing(
        id: Long,
        description: String? = null,
        developer: String? = null,
        publisher: String? = null,
        releaseYear: Int? = null,
        genre: String? = null,
        scrapedTitle: String? = null,
        ageRating: String? = null,
        franchise: String? = null,
        communityRating: Float? = null,
        releaseDate: String? = null,
    )

    @Query("UPDATE games SET user_title_override = :override WHERE id = :id")
    suspend fun updateUserTitleOverride(id: Long, override: String?)

    @Query("UPDATE games SET icon_uri = :iconUri WHERE id = :id")
    suspend fun updateIconUri(id: Long, iconUri: String?)

    @Query("UPDATE games SET box_art_uri = :boxArtUri WHERE id = :id")
    suspend fun updateBoxArt(id: Long, boxArtUri: String?)

    @Query("UPDATE games SET physical_media_uri = :physicalMediaUri WHERE id = :id")
    suspend fun updatePhysicalMedia(id: Long, physicalMediaUri: String?)

    @Query("UPDATE games SET box3d_uri = :box3dUri WHERE id = :id")
    suspend fun updateBox3d(id: Long, box3dUri: String?)

    @Query("UPDATE games SET icon_display_mode = :mode WHERE id = :id")
    suspend fun updateIconDisplayMode(id: Long, mode: String?)

    @Query(
        """
        UPDATE games SET artwork_uri = NULL, hero_uri = NULL, logo_uri = NULL, icon_uri = NULL,
            box_art_uri = NULL, physical_media_uri = NULL, box3d_uri = NULL
    """
    )
    suspend fun clearAllArtworkRefs()

    @Query("UPDATE games SET artwork_key = COALESCE(artwork_key, :artworkKey) WHERE id = :id")
    suspend fun mintArtworkKey(id: Long, artworkKey: String)

    @Query("UPDATE games SET artwork_uri = NULL, hero_uri = NULL, logo_uri = NULL, icon_uri = NULL")
    suspend fun clearAllArtwork()

    @Query("UPDATE games SET artwork_uri = NULL, hero_uri = NULL, logo_uri = NULL, icon_uri = NULL WHERE id = :id")
    suspend fun clearArtworkForGame(id: Long)

    @Query("DELETE FROM games")
    suspend fun deleteAll()
}

data class RomPathProjection(
    val id: Long,
    val rom_path: String,
)
