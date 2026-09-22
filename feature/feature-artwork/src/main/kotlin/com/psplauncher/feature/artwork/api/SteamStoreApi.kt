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

// ── Steam's own store, for the games that came from it ────────────────────────
//
// The cheapest provider in the app, because for a Steam game there is nothing to identify: the PC
// importer already recorded the app id as `games.storefront_game_id` (see StorefrontIdentity), so
// this asks Steam about a game by the number Steam itself assigned. No search, no ranked
// candidates, no ambiguity to resolve, and no API key — `appdetails` is public.
//
// That is why this provider is worth having even though four others exist. Every one of them has
// to *guess* which game a Windows shortcut is, from a title that came out of a filename; this one
// is told. The games it serves are exactly the ones the other four do worst on.
//
// **The artwork is not in the API response.** Steam serves three assets at fixed paths derived
// from the app id, which is the other reason this is cheap: no media list to rank, no region to
// prefer, no per-asset request. The three happen to be exactly what a PSP-style page wants —
// a wide hero to sit behind the page, portrait box art for the card, and a transparent logo to
// carry the title. A Steam game had none of those before this.

/** Where Steam serves an app's store record. Public, unkeyed, rate-limited by IP. */
private const val STEAM_APPDETAILS = "https://store.steampowered.com/api/appdetails"

/** Steam's CDN for library assets, keyed by app id alone. */
private const val STEAM_ASSETS = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps"

/**
 * The three library assets Steam publishes per app, at paths derived from the id.
 *
 * Unverified until fetched: Steam publishes these for most store apps but not all, and a missing
 * one 404s rather than redirecting. Callers treat a failed download as "this game has no logo",
 * never as "the scrape failed".
 */
data class SteamAppArt(
    /** Wide backdrop, the page's own surface. */
    val heroUrl: String,
    /** 600x900 portrait box art. */
    val boxArtUrl: String,
    /** Transparent logo — the title, as art. */
    val logoUrl: String,
)

/**
 * The Steam app id for a library row's storefront pair, or null when the row is not a Steam game.
 *
 * ONE predicate for "this is addressable on Steam", because the alternative is the string "STEAM"
 * compared by hand at every site that wants to ask. The pair is stored rather than the id alone
 * precisely so that ("STEAM","620") and ("GOG","620") are different games — checking the id and
 * forgetting the store is how they become the same one.
 */
fun steamAppIdOf(storefront: String?, storefrontGameId: String?): String? {
    if (!storefront.equals("STEAM", ignoreCase = true)) return null
    val id = storefrontGameId?.trim().orEmpty()
    // Same shape rule as steamAppArt: a positive integer. Anything else is another store's id
    // that happened to be labelled STEAM, and asking Steam about it is a guaranteed miss.
    return id.takeIf { it.isNotEmpty() && it.all(Char::isDigit) && (it.toLongOrNull() ?: 0L) > 0L }
}

/** Build the asset URLs for [appId]. Pure: no network, no validation beyond the id's shape. */
fun steamAppArt(appId: String): SteamAppArt? {
    val id = appId.trim()
    // Steam app ids are positive integers. Anything else is a storefront pair from another store
    // that happened to be labelled STEAM, and guessing a URL from it would 404 four times.
    if (id.isEmpty() || !id.all { it.isDigit() } || id.toLongOrNull()?.let { it <= 0L } != false) return null
    return SteamAppArt(
        heroUrl   = "$STEAM_ASSETS/$id/library_hero.jpg",
        boxArtUrl = "$STEAM_ASSETS/$id/library_600x900.jpg",
        logoUrl   = "$STEAM_ASSETS/$id/logo.png",
    )
}

/** What the store record is worth taking. Text only — the art is [steamAppArt]. */
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
    /**
     * Steam's store record for [appId], or null when Steam does not serve one.
     *
     * Returns null rather than throwing for every expected miss — a delisted app, a region-blocked
     * app, a non-game (soundtracks and tools live in the same id space and answer `success: false`)
     * — because none of those is an error the user needs to see on a batch scrape. Genuine network
     * failures are logged and also fold into null: a scrape that skips one game is better than one
     * that stops.
     *
     * [language] follows Steam's own `l` parameter and only affects text.
     */
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

// ── Response shredding ────────────────────────────────────────────────────────
//
// Hand-read out of the JsonObject rather than modelled as a data class. Steam's appdetails payload
// is large, deeply optional and differs by app type — every field below is absent on some real
// app — so a strict model would be a list of nullable fields nobody reads, and a lenient one would
// hide a rename. Six accessors say exactly what is taken and nothing else.

private fun JsonObject.str(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }

private fun JsonObject.firstOfArray(key: String): String? = runCatching {
    this[key]?.let { it as? kotlinx.serialization.json.JsonArray }
        ?.firstOrNull()?.jsonPrimitive?.content
}.getOrNull()?.takeIf { it.isNotBlank() }

/** `genres` is a list of `{id, description}`; the first is the one Steam leads with. */
private fun JsonObject.firstDescription(key: String): String? = runCatching {
    this[key]?.let { it as? kotlinx.serialization.json.JsonArray }
        ?.firstOrNull()?.jsonObject?.get("description")?.jsonPrimitive?.content
}.getOrNull()?.takeIf { it.isNotBlank() }

/**
 * The year out of `release_date.date`, which is a LOCALISED free-text string — "12 Nov, 2020",
 * "Nov 12, 2020", "2020", "Coming soon". Only a four-digit year is taken, because that is the only
 * part with one meaning in every locale, and a wrong release date is worse than none.
 */
private fun JsonObject.releaseYear(): Int? = runCatching {
    val raw = this["release_date"]?.jsonObject?.get("date")?.jsonPrimitive?.content ?: return null
    Regex("(19|20)\\d{2}").find(raw)?.value?.toIntOrNull()
}.getOrNull()
