package com.psplauncher.feature.artwork.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val STEAM_APPDETAILS = "https://store.steampowered.com/api/appdetails"

private const val STEAM_ASSETS = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps"

data class SteamAppArt(

    val heroUrl: String,

    val boxArtUrl: String,

    val logoUrl: String,
)

fun steamAppIdOf(storefront: String?, storefrontGameId: String?): String? {
    if (!storefront.equals("STEAM", ignoreCase = true)) return null
    val id = storefrontGameId?.trim().orEmpty()

    return id.takeIf { it.isNotEmpty() && it.all(Char::isDigit) && (it.toLongOrNull() ?: 0L) > 0L }
}

fun steamAppArt(appId: String): SteamAppArt? {
    val id = appId.trim()

    if (id.isEmpty() || !id.all { it.isDigit() } || id.toLongOrNull()?.let { it <= 0L } != false) return null
    return SteamAppArt(
        heroUrl   = "$STEAM_ASSETS/$id/library_hero.jpg",
        boxArtUrl = "$STEAM_ASSETS/$id/library_600x900.jpg",
        logoUrl   = "$STEAM_ASSETS/$id/logo.png",
    )
}

data class SteamAppDetails(
    val appId: String,
    val title: String,
    val developer: String?,
    val publisher: String?,
    val genre: String?,
    val releaseYear: Int?,
    val description: String?,
)

@Serializable
private data class SteamEnvelope(
    val success: Boolean = false,
    val data: JsonObject? = null,
)

@Singleton
class SteamStoreApi @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun appDetails(appId: String, language: String = "english"): SteamAppDetails? {
        val id = appId.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        return runCatching {
            val body: Map<String, SteamEnvelope> = client.get(STEAM_APPDETAILS) {
                parameter("appids", id)
                parameter("l", language)
            }.body()
            val data = body[id]?.takeIf { it.success }?.data ?: return null
            SteamAppDetails(
                appId = id,
                title = data.str("name") ?: return null,
                developer = data.firstOfArray("developers"),
                publisher = data.firstOfArray("publishers"),
                genre = data.firstDescription("genres"),
                releaseYear = data.releaseYear(),
                description = data.str("short_description")?.takeIf { it.isNotBlank() },
            )
        }.getOrElse {
            Timber.w(it, "Steam appdetails failed for app $id")
            null
        }
    }
}

private fun JsonObject.str(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }

private fun JsonObject.firstOfArray(key: String): String? = runCatching {
    this[key]?.let { it as? kotlinx.serialization.json.JsonArray }
        ?.firstOrNull()?.jsonPrimitive?.content
}.getOrNull()?.takeIf { it.isNotBlank() }

private fun JsonObject.firstDescription(key: String): String? = runCatching {
    this[key]?.let { it as? kotlinx.serialization.json.JsonArray }
        ?.firstOrNull()?.jsonObject?.get("description")?.jsonPrimitive?.content
}.getOrNull()?.takeIf { it.isNotBlank() }

private fun JsonObject.releaseYear(): Int? = runCatching {
    val raw = this["release_date"]?.jsonObject?.get("date")?.jsonPrimitive?.content ?: return null
    Regex("(19|20)\\d{2}").find(raw)?.value?.toIntOrNull()
}.getOrNull()
