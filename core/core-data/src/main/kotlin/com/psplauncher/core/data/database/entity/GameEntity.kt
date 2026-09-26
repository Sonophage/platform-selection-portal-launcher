package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameContentType
import com.psplauncher.core.domain.model.GameRegion
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "games",
    indices = [
        Index("platform_id"),
        Index("is_favorite"),
        Index("last_played_at"),
        Index("rom_path", unique = true),
        Index("artwork_key"),

        Index("disc_set_key"),

        Index("storefront", "storefront_game_id"),
    ]
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    @ColumnInfo(name = "platform_id")
    val platformId: String,

    @ColumnInfo(name = "rom_path")
    val romPath: String?,

    @ColumnInfo(name = "rom_uri")
    val romUri: String? = null,

    @ColumnInfo(name = "disc_set_key")
    val discSetKey: String? = null,

    @ColumnInfo(name = "disc_number")
    val discNumber: Int? = null,

    @ColumnInfo(name = "is_disc_primary")
    val isDiscPrimary: Boolean = false,

    @ColumnInfo(name = "region")
    val region: String? = null,

    @ColumnInfo(name = "package_name")
    val packageName: String?,

    @ColumnInfo(name = "emulator_package")
    val emulatorPackage: String?,

    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String?,

    @ColumnInfo(name = "hero_uri")
    val heroUri: String?,

    @ColumnInfo(name = "logo_uri")
    val logoUri: String?,

    @ColumnInfo(name = "icon_uri")
    val iconUri: String? = null,

    @ColumnInfo(name = "box_art_uri")
    val boxArtUri: String? = null,

    @ColumnInfo(name = "physical_media_uri")
    val physicalMediaUri: String? = null,

    @ColumnInfo(name = "box3d_uri")
    val box3dUri: String? = null,

    @ColumnInfo(name = "icon_display_mode")
    val iconDisplayMode: String? = null,

    val description: String?,
    val developer: String?,
    val publisher: String?,

    @ColumnInfo(name = "release_year")
    val releaseYear: Int?,

    val genre: String?,

    val players: String? = null,

    @ColumnInfo(name = "age_rating")
    val ageRating: String? = null,

    val franchise: String? = null,

    @ColumnInfo(name = "community_rating")
    val communityRating: Float? = null,

    @ColumnInfo(name = "release_date")
    val releaseDate: String? = null,

    @ColumnInfo(name = "steam_grid_db_id")
    val steamGridDbId: Long?,

    @ColumnInfo(name = "ss_id")
    val ssId: Long? = null,

    @ColumnInfo(name = "tgdb_id")
    val tgdbId: Long? = null,

    @ColumnInfo(name = "igdb_id")
    val igdbId: Long? = null,

    @ColumnInfo(name = "rom_crc32")
    val romCrc32: String? = null,

    @ColumnInfo(name = "artwork_key")
    val artworkKey: String? = null,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "favorite_sort_order")
    val favoriteSortOrder: Int = 0,

    @ColumnInfo(name = "total_play_time_millis")
    val totalPlayTimeMillis: Long = 0,

    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: Long? = null,

    @ColumnInfo(name = "date_added")
    val dateAdded: Long? = null,

    @ColumnInfo(name = "play_state")
    val playState: String? = null,

    @ColumnInfo(name = "user_note")
    val userNote: String? = null,

    @ColumnInfo(name = "is_manual_entry")
    val isManualEntry: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "scraped_title")
    val scrapedTitle: String? = null,

    @ColumnInfo(name = "user_title_override")
    val userTitleOverride: String? = null,

    @ColumnInfo(name = "content_type")
    val contentType: String = GameContentType.GAME.name,

    @ColumnInfo(name = "launch_shortcut_id")
    val launchShortcutId: String? = null,

    @ColumnInfo(name = "launch_intent_uri")
    val launchIntentUri: String? = null,

    @ColumnInfo(name = "launch_token")
    val launchToken: String? = null,

    val storefront: String? = null,

    @ColumnInfo(name = "storefront_game_id")
    val storefrontGameId: String? = null,

    @ColumnInfo(name = "is_missing")
    val isMissing: Boolean = false,

    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long? = null,
)

fun GameEntity.toDomain() = Game(
    id = id,
    title = title,
    platformId = platformId,
    romPath = romPath,
    romUri = romUri,
    discSetKey = discSetKey,
    discNumber = discNumber,
    isDiscPrimary = isDiscPrimary,
    region = GameRegion.fromName(region),
    packageName = packageName,
    emulatorPackage = emulatorPackage,
    artworkUri = artworkUri,
    heroUri = heroUri,
    logoUri = logoUri,
    iconUri = iconUri,
    boxArtUri = boxArtUri,
    physicalMediaUri = physicalMediaUri,
    box3dUri = box3dUri,
    iconDisplayMode = iconDisplayMode,
    description = description,
    developer = developer,
    publisher = publisher,
    releaseYear = releaseYear,
    genre = genre,
    players = players,
    ageRating = ageRating,
    franchise = franchise,
    communityRating = communityRating,
    releaseDate = releaseDate,
    steamGridDbId = steamGridDbId,
    ssId = ssId,
    igdbId = igdbId,
    romCrc32 = romCrc32,
    artworkKey = artworkKey,
    isFavorite = isFavorite,
    favoriteSortOrder = favoriteSortOrder,
    totalPlayTimeMillis = totalPlayTimeMillis,
    lastPlayedAt = lastPlayedAt,
    dateAdded = dateAdded,
    playState = playState,
    userNote = userNote,
    isManualEntry = isManualEntry,
    scrapedTitle = scrapedTitle,
    userTitleOverride = userTitleOverride,
    contentType = GameContentType.fromName(contentType),
    shortcutId = launchShortcutId,
    launchIntentUri = launchIntentUri,
    launchToken = launchToken,
    storefront = storefront,
    storefrontGameId = storefrontGameId,
    isMissing = isMissing,
    lastSeenAt = lastSeenAt,
)

fun Game.toEntity() = GameEntity(
    id = id,
    title = title,
    platformId = platformId,
    romPath = romPath,
    romUri = romUri,
    discSetKey = discSetKey,
    discNumber = discNumber,
    isDiscPrimary = isDiscPrimary,
    region = region?.name,
    packageName = packageName,
    emulatorPackage = emulatorPackage,
    artworkUri = artworkUri,
    heroUri = heroUri,
    logoUri = logoUri,
    iconUri = iconUri,
    boxArtUri = boxArtUri,
    physicalMediaUri = physicalMediaUri,
    box3dUri = box3dUri,
    iconDisplayMode = iconDisplayMode,
    description = description,
    developer = developer,
    publisher = publisher,
    releaseYear = releaseYear,
    genre = genre,
    players = players,
    ageRating = ageRating,
    franchise = franchise,
    communityRating = communityRating,
    releaseDate = releaseDate,
    steamGridDbId = steamGridDbId,
    ssId = ssId,
    igdbId = igdbId,
    romCrc32 = romCrc32,
    artworkKey = artworkKey,
    isFavorite = isFavorite,
    favoriteSortOrder = favoriteSortOrder,
    totalPlayTimeMillis = totalPlayTimeMillis,
    lastPlayedAt = lastPlayedAt,
    dateAdded = dateAdded,
    playState = playState,
    userNote = userNote,
    isManualEntry = isManualEntry,
    scrapedTitle = scrapedTitle,
    userTitleOverride = userTitleOverride,
    contentType = contentType.name,
    launchShortcutId = shortcutId,
    launchIntentUri = launchIntentUri,
    launchToken = launchToken,
    storefront = storefront,
    storefrontGameId = storefrontGameId,
    isMissing   = isMissing,
    lastSeenAt  = lastSeenAt,
)
