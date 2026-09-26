package com.psplauncher.feature.artwork.match

enum class MatchProvider(val label: String) {
    SCREENSCRAPER("ScreenScraper"),
    IGDB("IGDB"),
    STEAMGRIDDB("SteamGridDB"),
    STEAM_STORE("Steam"),
}

data class ProviderCapability(
    val provider: MatchProvider,

    val addressableBySavedId: Boolean,

    val addressableByRomHash: Boolean,

    val addressableByStorefrontId: Boolean,

    val supportsTitleSearch: Boolean,

    val suppliesMetadata: Boolean,

    val suppliesArtwork: Boolean,
)

object ProviderCapabilities {
    private val table: Map<MatchProvider, ProviderCapability> = listOf(

        ProviderCapability(
            provider = MatchProvider.SCREENSCRAPER,
            addressableBySavedId = true,
            addressableByRomHash = true,
            addressableByStorefrontId = false,
            supportsTitleSearch = true,
            suppliesMetadata = true,
            suppliesArtwork = true,
        ),

        ProviderCapability(
            provider = MatchProvider.IGDB,
            addressableBySavedId = true,
            addressableByRomHash = false,
            addressableByStorefrontId = false,
            supportsTitleSearch = true,
            suppliesMetadata = false,
            suppliesArtwork = true,
        ),

        ProviderCapability(
            provider = MatchProvider.STEAMGRIDDB,
            addressableBySavedId = true,
            addressableByRomHash = false,
            addressableByStorefrontId = true,
            supportsTitleSearch = true,
            suppliesMetadata = false,
            suppliesArtwork = true,
        ),

        ProviderCapability(
            provider = MatchProvider.STEAM_STORE,
            addressableBySavedId = false,
            addressableByRomHash = false,
            addressableByStorefrontId = true,
            supportsTitleSearch = false,
            suppliesMetadata = true,
            suppliesArtwork = true,
        ),
    ).associateBy { it.provider }

    operator fun get(provider: MatchProvider): ProviderCapability = table.getValue(provider)

    val all: List<ProviderCapability> get() = MatchProvider.entries.map { table.getValue(it) }

    val searchable: List<MatchProvider> get() = all.filter { it.supportsTitleSearch }.map { it.provider }

    val metadataProviders: List<MatchProvider> get() = all.filter { it.suppliesMetadata }.map { it.provider }
}

data class GameCandidate(
    val provider: MatchProvider,
    val providerGameId: String,
    val title: String,
    val platformName: String? = null,
    val releaseYear: Int? = null,
    val thumbUrl: String? = null,

    val gameArtCount: Int? = null,
)

enum class MatchTier {
    SAVED_PROVIDER_ID,

    CONTENT_ID,

    EXACT_TITLE,
}

data class GameMatch(
    val candidate: GameCandidate,
    val tier: MatchTier,
    val userConfirmed: Boolean = false,
) {
    val matchKey: String get() = "${candidate.provider.name}:${candidate.providerGameId}"
}

data class MetadataPreset(
    val provider: MatchProvider,
    val title: String? = null,
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseYear: Int? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val ageRating: String? = null,
    val franchise: String? = null,
    val communityRating: Float? = null,
) {
    val isEmpty: Boolean
        get() = listOf(
            title, description, developer, publisher, releaseYear,
            releaseDate, genre, ageRating, franchise, communityRating,
        ).all { it == null || (it is String && it.isBlank()) }
}
