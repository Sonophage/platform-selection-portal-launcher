package com.psplauncher.feature.artwork.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SgdbSearchResponse(
    val success: Boolean,
    val data: List<SgdbGame> = emptyList(),
)

@Serializable
data class SgdbGame(
    val id: Long,
    val name: String,
    @SerialName("release_date") val releaseDate: Long? = null,
    val types: List<String> = emptyList(),
)

@Serializable
data class SgdbArtResponse(
    val success: Boolean,
    val data: List<SgdbArtItem> = emptyList(),
)

@Serializable
data class SgdbArtItem(
    val id: Long,
    val url: String,
    val thumb: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val style: String? = null,
)

enum class SgdbArtType(val endpoint: String) {
    GRID("grids"),
    HERO("heroes"),
    LOGO("logos"),
    ICON("icons"),
}

private const val BASE_URL = "https://www.steamgriddb.com/api/v2"

@Singleton
class SteamGridDbApi @Inject constructor(
    private val httpClient: HttpClient,
    private val apiKeyProvider: SgdbApiKeyProvider,
) {
    suspend fun getSteamAppId(gameId: Long): String? {
        val key = apiKeyProvider.getKey() ?: return null
        return runCatching {
            val json: JsonElement = httpClient.get("$BASE_URL/games/id/$gameId") {
                header("Authorization", "Bearer $key")
                parameter("platformdata", "steam")
            }.body()
            val appId = json.jsonObject["data"]?.jsonObject
                ?.get("external_platform_data")?.jsonObject
                ?.get("steam")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("id")?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
            Timber.d("SGDB steam appid for game %d → %s", gameId, appId)
            appId
        }.getOrNull()
    }

    suspend fun getGameBySteamAppId(appId: String): SgdbGame? {
        val key = apiKeyProvider.getKey() ?: return null
        if (appId.isBlank() || !appId.all(Char::isDigit)) return null
        return runCatching {
            val json: JsonElement = httpClient.get("$BASE_URL/games/steam/$appId") {
                header("Authorization", "Bearer $key")
            }.body()
            val data = json.jsonObject["data"]?.jsonObject ?: return@runCatching null
            val id = data["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@runCatching null
            val name = data["name"]?.jsonPrimitive?.content ?: return@runCatching null
            SgdbGame(id = id, name = name)
        }.onFailure { Timber.d(it, "SGDB steam appid lookup failed for %s", appId) }.getOrNull()
    }

    suspend fun searchGame(name: String): Result<List<SgdbGame>> = runCatching {
        val key = apiKeyProvider.getKey()
            ?: error("SteamGridDB API key not configured")

        val response: SgdbSearchResponse = httpClient.get("$BASE_URL/search/autocomplete/$name") {
            header("Authorization", "Bearer $key")
        }.body()

        if (!response.success) error("SteamGridDB search failed for: $name")
        response.data.also { Timber.d("SGDB search '$name' → ${it.size} results") }
    }

    suspend fun getArt(
        gameId: Long,
        type: SgdbArtType,
        styles: List<String> = emptyList(),
        dimensions: List<String> = emptyList(),

        includeNsfw: Boolean = false,
    ): Result<List<SgdbArtItem>> = runCatching {
        val key = apiKeyProvider.getKey()
            ?: error("SteamGridDB API key not configured")

        val response: SgdbArtResponse = httpClient.get("$BASE_URL/${type.endpoint}/game/$gameId") {
            header("Authorization", "Bearer $key")
            if (styles.isNotEmpty()) parameter("styles", styles.joinToString(","))
            if (dimensions.isNotEmpty()) parameter("dimensions", dimensions.joinToString(","))
            parameter("nsfw", if (includeNsfw) "any" else "false")
        }.body()

        if (!response.success) error("SGDB art fetch failed for gameId=$gameId type=$type")
        response.data.also { Timber.d("SGDB ${type.name} for $gameId → ${it.size} results") }
    }

    suspend fun getBestHorizontalGridUrl(gameId: Long): String? =
        getArt(gameId, SgdbArtType.GRID, dimensions = listOf("920x430", "460x215"))
            .getOrNull()
            ?.firstOrNull()
            ?.url

    suspend fun getBestGridUrl(gameId: Long): String? =
        getArt(gameId, SgdbArtType.GRID)
            .getOrNull()
            ?.firstOrNull()
            ?.url

    suspend fun getBestHeroUrl(gameId: Long): String? =
        getArt(gameId, SgdbArtType.HERO)
            .getOrNull()
            ?.firstOrNull()
            ?.url

    suspend fun getBestLogoUrl(gameId: Long): String? =
        getArt(gameId, SgdbArtType.LOGO)
            .getOrNull()
            ?.firstOrNull()
            ?.url
}
