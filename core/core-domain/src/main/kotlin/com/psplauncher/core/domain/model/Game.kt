package com.psplauncher.core.domain.model

data class Game(
    val id: Long = 0,
    val title: String,
    val platformId: String,
    val romPath: String?          = null,

    val romUri: String?           = null,

    val discSetKey: String? = null,
    val discNumber: Int? = null,
    val isDiscPrimary: Boolean = false,

    val region: GameRegion? = null,
    val packageName: String?      = null,
    val emulatorPackage: String?  = null,
    val artworkUri: String?       = null,
    val heroUri: String?          = null,
    val logoUri: String?          = null,
    val iconUri: String?          = null,

    val boxArtUri: String?        = null,
    val physicalMediaUri: String? = null,
    val box3dUri: String?         = null,

    val iconDisplayMode: String?  = null,
    val description: String?      = null,
    val developer: String?        = null,
    val publisher: String?        = null,
    val releaseYear: Int?         = null,
    val genre: String?            = null,

    val players: String?          = null,
    val ageRating: String?        = null,
    val franchise: String?        = null,
    val communityRating: Float?   = null,
    val releaseDate: String?      = null,
    val steamGridDbId: Long?      = null,

    val ssId: Long?               = null,
    val igdbId: Long?             = null,

    val romCrc32: String?         = null,

    val artworkKey: String?       = null,
    val isFavorite: Boolean = false,
    val favoriteSortOrder: Int = 0,
    val totalPlayTimeMillis: Long = 0,
    val lastPlayedAt: Long? = null,

    val dateAdded: Long?          = null,

    val playState: String?        = null,
    val userNote: String?   = null,
    val isManualEntry: Boolean = false,

    val scrapedTitle: String?      = null,

    val userTitleOverride: String? = null,

    val contentType: GameContentType = GameContentType.GAME,

    val shortcutId: String? = null,

    val launchIntentUri: String? = null,

    val launchToken: String? = null,

    val storefront: String? = null,
    val storefrontGameId: String? = null,

    val isMissing: Boolean = false,

    val lastSeenAt: Long? = null,
) {
    val displayTitle: String get() = userTitleOverride ?: scrapedTitle ?: title

    val discFaceUri: String? get() = listOfNotNull(boxArtUri, box3dUri, artworkUri, heroUri, iconUri)
        .firstOrNull { it.isNotBlank() }
}
