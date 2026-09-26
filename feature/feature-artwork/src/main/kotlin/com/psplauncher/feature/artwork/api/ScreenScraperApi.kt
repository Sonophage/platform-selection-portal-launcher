package com.psplauncher.feature.artwork.api

import android.content.Context
import com.psplauncher.core.common.logging.LogRedaction
import com.psplauncher.feature.artwork.BuildConfig
import com.psplauncher.feature.artwork.credentials.MetadataCredentialSource
import com.psplauncher.feature.artwork.credentials.ScreenScraperCredentials
import com.psplauncher.feature.artwork.rom.RomIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Serializable
data class SsResponse(
    val response: SsGameResponse? = null,
)

@Serializable
data class SsGameResponse(
    @SerialName("jeu") val game: SsGame? = null,
    @SerialName("ssuser") val user: SsUser? = null,
)

@Serializable
data class SsSearchResponse(
    val response: SsSearchBody? = null,
)

@Serializable
data class SsSearchBody(
    @SerialName("jeux") val games: List<SsGame> = emptyList(),
    @SerialName("ssuser") val user: SsUser? = null,
)

data class SsSearchHit(
    val ssId: Long,
    val title: String,
    val releaseYear: Int?,
    val systemId: Int? = null,
    val systemName: String? = null,

    val gameArtCount: Int = 0,
)

class SsSearchFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
data class SsUser(
    val id: String? = null,
    @SerialName("maxthreads")        val maxThreads: String? = null,
    @SerialName("maxrequestspermin") val maxRequestsPerMinute: String? = null,
    @SerialName("requeststoday")     val requestsToday: String? = null,
    @SerialName("maxrequestsperday") val maxRequestsPerDay: String? = null,
)

@Serializable
data class SsGame(
    val id: String? = null,
    @SerialName("noms") val names: List<SsLocalizedText> = emptyList(),
    @SerialName("synopsis") val synopsis: List<SsLocalizedText> = emptyList(),
    @SerialName("dates") val dates: List<SsLocalizedText> = emptyList(),
    @SerialName("genres") val genres: List<SsGenre> = emptyList(),

    @SerialName("familles") val families: List<SsGenre> = emptyList(),
    @SerialName("classifications") val classifications: List<SsClassification> = emptyList(),
    @SerialName("developpeur") val developer: SsText? = null,
    @SerialName("editeur") val publisher: SsText? = null,
    @SerialName("joueurs") val players: SsText? = null,
    @SerialName("note") val rating: SsRating? = null,
    @SerialName("medias") val medias: List<SsMedia> = emptyList(),

    @SerialName("systeme") val system: SsSystem? = null,
)

@Serializable
data class SsSystem(val id: String? = null, val text: String? = null)

@Serializable
data class SsClassification(val type: String? = null, val text: String? = null)

@Serializable
data class SsLocalizedText(
    val region: String? = null,
    @SerialName("langue") val language: String? = null,
    val text: String,
)

@Serializable
data class SsText(val text: String? = null)

@Serializable
data class SsRating(val text: String? = null, val nb: String? = null)

@Serializable
data class SsGenre(
    val id: String? = null,
    @SerialName("noms") val names: List<SsLocalizedText> = emptyList(),
)

@Serializable
data class SsMedia(
    val type: String,

    val parent: String? = null,
    val region: String? = null,
    val url: String? = null,
    val format: String? = null,
)

data class SsGameInfo(
    val ssId: Long?,
    val title: String?,
    val description: String?,
    val developer: String?,
    val publisher: String?,
    val releaseYear: Int?,
    val genre: String?,
    val players: String?,
    val ageRating: String?,
    val franchise: String?,
    val communityRating: Float?,
    val releaseDate: String?,
    val artworkUrl: String?,
    val boxArtUrl: String?,
    val box3dUrl: String?,
    val physicalMediaUrl: String?,
    val screenshotUrl: String?,
    val heroUrl: String?,
    val logoUrl: String?,
    val manualUrl: String?,
    val videoUrl: String?,
    val videoRawUrl: String?,

    val medias: List<SsCachedMedia> = emptyList(),
)

enum class SsFailureReason {
    DISABLED,
    NO_SYSTEM_ID_MAPPING,
    BAD_DEV_CREDENTIALS,
    API_CLOSED,
    RATE_LIMITED,
    DAILY_QUOTA_EXCEEDED,
    TOO_MANY_UNRECOGNIZED,
    NO_MATCH,
    NETWORK_ERROR,
    PARSE_ERROR,
}

data class SsLookupDiagnostics(
    val fileName: String?,
    val platformId: String,
    val systemId: Int?,
    val userCredentialsPresent: Boolean,
    val sentCrc: Boolean,
    val httpStatus: Int? = null,
    val failureReason: SsFailureReason? = null,
    val failureDetail: String? = null,
    val quota: SsUser? = null,
)

data class SsLookupResult(
    val info: SsGameInfo?,
    val diagnostics: SsLookupDiagnostics,
) {
    val success: Boolean get() = info != null

    val isBatchStopper: Boolean get() = diagnostics.failureReason in setOf(
        SsFailureReason.DISABLED,
        SsFailureReason.BAD_DEV_CREDENTIALS,
        SsFailureReason.API_CLOSED,
        SsFailureReason.DAILY_QUOTA_EXCEEDED,
    )

    val stopsUnhashedLookups: Boolean get() =
        diagnostics.failureReason == SsFailureReason.TOO_MANY_UNRECOGNIZED
}

@Singleton
class ScreenScraperApi @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val httpClient: HttpClient,
    private val credentials: MetadataCredentialSource,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile private var requestSlots = Semaphore(1)
    @Volatile private var maxThreads = 1
    private val spacingGate = Mutex()
    private var lastRequestStartedAt = 0L

    @Volatile private var maxRequestsPerMinute: Int? = null

    private val _quota = kotlinx.coroutines.flow.MutableStateFlow<SsUser?>(null)
    val quota: kotlinx.coroutines.flow.StateFlow<SsUser?> get() = _quota

    suspend fun isEnabled(): Boolean = credentials.screenScraperNow() != null

    val isEnabledFlow: Flow<Boolean> = credentials.screenScraper.map { it != null }

    suspend fun fetchGameInfo(
        platformId: String,
        rom: RomIdentity?,
        ssGameId: Long? = null,
    ): SsLookupResult {
        val systemId = PLATFORM_IDS[platformId]
        val creds = credentials.screenScraperNow()
        val baseDiag = SsLookupDiagnostics(
            fileName   = rom?.fileName,
            platformId = platformId,
            systemId   = systemId,
            userCredentialsPresent = creds?.userId != null,
            sentCrc    = rom?.crc32 != null,
        )

        if (creds == null) {
            return SsLookupResult(null, baseDiag.copy(
                failureReason = SsFailureReason.DISABLED,
                failureDetail = "No ScreenScraper developer account — add one in Settings ▸ Artwork",
            ))
        }
        if (systemId == null && ssGameId == null) {
            return SsLookupResult(null, baseDiag.copy(
                failureReason = SsFailureReason.NO_SYSTEM_ID_MAPPING,
                failureDetail = "No ScreenScraper system id for platform '$platformId'",
            ))
        }
        if (!canLookUp(rom, ssGameId)) {
            return SsLookupResult(null, baseDiag.copy(
                failureReason = SsFailureReason.NO_MATCH,
                failureDetail = "No ScreenScraper id and no ROM file to identify '$platformId' game by — match it by title",
            ))
        }

        return try {
            val response: HttpResponse = rateLimited("jeuInfos") { httpClient.get("$BASE/jeuInfos.php") {
                credentialParams(creds)
                if (ssGameId != null) {
                    parameter("gameid", ssGameId)
                } else {
                    parameter("systemeid", systemId)
                    parameter("romtype",   "rom")
                    rom?.fileName?.let  { parameter("romnom",    it) }
                    rom?.sizeBytes?.let { parameter("romtaille", it) }
                    rom?.crc32?.let     { parameter("crc",       it) }
                }
            } }

            val diag = baseDiag.copy(httpStatus = response.status.value)
            failureForStatus(response.status.value)?.let { (reason, detail) ->
                Timber.w("ScreenScraper: $detail (platform=$platformId file='${rom?.fileName}')")
                return SsLookupResult(null, diag.copy(failureReason = reason, failureDetail = detail))
            }

            val bodyText = response.bodyAsText()
            logAccountLimitsOnce(bodyText)
            captureBodyInDebug("jeuInfos-${ssGameId ?: slugOf(rom?.fileName ?: "rom")}", bodyText)
            val parsed = runCatching { json.decodeFromString(SsResponse.serializer(), bodyText) }
                .getOrElse { e ->
                    val prefix = bodyText.take(160)
                    Timber.w("ScreenScraper: non-JSON body for '${rom?.fileName}': '$prefix'")
                    return SsLookupResult(null, diag.copy(
                        failureReason = failureForTextBody(prefix),
                        failureDetail = prefix,
                    ))
                }

            val quota = parsed.response?.user
            rememberRequestLimit(quota)
            val game  = parsed.response?.game
            if (game == null) {
                return SsLookupResult(null, diag.copy(
                    failureReason = SsFailureReason.NO_MATCH,
                    failureDetail = "No game found for file '${rom?.fileName}'",
                    quota = quota,
                ))
            }

            val info = game.toInfo()
            Timber.i("ScreenScraper: match ssId=${info.ssId} '${info.title}' for '${rom?.fileName}'")
            SsLookupResult(info, diag.copy(quota = quota))
        } catch (e: Exception) {
            Timber.w(e, "ScreenScraper: network error for '${rom?.fileName}'")
            SsLookupResult(null, baseDiag.copy(
                failureReason = SsFailureReason.NETWORK_ERROR,
                failureDetail = e.message ?: "Network error",
            ))
        }
    }

    suspend fun searchGames(platformId: String?, title: String): List<SsSearchHit> {
        val creds = credentials.screenScraperNow() ?: return emptyList()
        if (title.isBlank()) return emptyList()
        val systemId = platformId?.let { PLATFORM_IDS[it] }
        val (status, body) = try {
            rateLimited("jeuRecherche") { httpClient.get("$BASE/jeuRecherche.php") {
                credentialParams(creds)
                parameter("recherche", title)
                systemId?.let { parameter("systemeid", it) }

                timeout { socketTimeoutMillis = SEARCH_SOCKET_TIMEOUT_MS }
            } }.let { it.status.value to it.bodyAsText() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "ScreenScraper search failed for '$title'")
            throw SsSearchFailedException("No answer from ScreenScraper", e)
        }
        failureForStatus(status)?.let { (_, detail) ->
            Timber.w("ScreenScraper search: $detail for '$title' (platform=$platformId)")
            throw SsSearchFailedException(detail)
        }
        logAccountLimitsOnce(body)
        captureBodyInDebug("jeuRecherche-${systemId ?: "any"}-${slugOf(title)}", body)
        val parsed = decodeSearch(body)
            ?: throw SsSearchFailedException("ScreenScraper answered with an error instead of results")
        rememberRequestLimit(parsed.response?.user)
        val hits = hitsOf(parsed)
        Timber.d("ScreenScraper search '$title' (system ${systemId ?: "any"}) → ${hits.size} hits")
        return hits
    }

    private suspend fun captureBodyInDebug(name: String, body: String) {
        if (!BuildConfig.DEBUG) return
        withContext(Dispatchers.IO) {
            try {
                val dir = File(appContext.cacheDir, "ss-captures").apply { mkdirs() }
                val file = File(dir, "$name.json")
                file.writeText(LogRedaction.redact(scrubCapture(body)))
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_CAPTURES)?.forEach { it.delete() }
                Timber.d("ScreenScraper body (${body.length} chars) saved to ${file.absolutePath}")
            } catch (e: IOException) {
                Timber.w("ScreenScraper body not saved: ${e.message}")
            }
        }
    }

    private fun slugOf(text: String): String =
        text.lowercase(Locale.ROOT).replace(NON_SLUG, "-").trim('-').take(40)

    internal fun parseSearch(body: String): List<SsSearchHit>? = decodeSearch(body)?.let(::hitsOf)

    private fun decodeSearch(body: String): SsSearchResponse? =
        runCatching { json.decodeFromString(SsSearchResponse.serializer(), body) }
            .onFailure { Timber.w("ScreenScraper search: non-JSON body '${body.take(160)}'") }
            .getOrNull()

    private fun hitsOf(parsed: SsSearchResponse): List<SsSearchHit> =
        parsed.response?.games.orEmpty().mapNotNull { game ->
            val info = game.toInfo()
            val id = info.ssId ?: return@mapNotNull null
            val title = info.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SsSearchHit(
                ssId = id,
                title = title,
                releaseYear = info.releaseYear,
                systemId = game.system?.id?.toIntOrNull(),
                systemName = game.system?.text?.takeIf { it.isNotBlank() },
                gameArtCount = game.medias.count { it.parent == "jeu" },
            )
        }

    suspend fun fetchUserInfo(username: String, password: String): SsUser? = runCatching {
        val creds = credentials.screenScraperNow() ?: return null
        val response = rateLimited("ssuserInfos") { httpClient.get("$BASE/ssuserInfos.php") {
            credentialParams(creds.copy(userId = username, userPassword = password))
        } }
        if (response.status.value != 200) return null
        json.decodeFromString(SsUserInfoResponse.serializer(), response.bodyAsText()).response?.user
    }.onFailure { Timber.w(it, "ScreenScraper: ssuserInfos failed") }.getOrNull()

    private fun HttpRequestBuilder.credentialParams(creds: ScreenScraperCredentials) {
        parameter("devid",       creds.devId)
        parameter("devpassword", creds.devPassword)
        parameter("softname",    BuildConfig.SS_SOFT_NAME)
        parameter("output",      "json")
        creds.userId?.let {
            parameter("ssid",       it)
            parameter("sspassword", creds.userPassword.orEmpty())
        }
    }

    internal suspend fun <T> rateLimited(endpoint: String, block: suspend () -> T): T {
        val askedAt = System.currentTimeMillis()

        val slots = requestSlots
        slots.acquire()
        try {
            val queuedAt = System.currentTimeMillis()
            val startedAt = spacingGate.withLock {
                val wait = waitBeforeNextRequest(
                    System.currentTimeMillis(), lastRequestStartedAt, requestIntervalMs(maxRequestsPerMinute),
                )
                if (wait > 0) delay(wait)
                System.currentTimeMillis().also { lastRequestStartedAt = it }
            }
            try {
                return block()
            } finally {
                Timber.d(
                    "ScreenScraper gate %s: waited %d ms (queued %d, spaced %d), request %d ms, %d thread(s)",
                    endpoint, startedAt - askedAt, queuedAt - askedAt, startedAt - queuedAt,
                    System.currentTimeMillis() - startedAt, maxThreads,
                )
            }
        } finally {
            slots.release()
        }
    }

    internal fun rememberRequestLimit(user: SsUser?) {
        user ?: return
        user.maxRequestsPerMinute?.toIntOrNull()?.let { maxRequestsPerMinute = it }
        val threads = effectiveThreads(user.maxThreads)
        if (threads != maxThreads) {
            Timber.d("ScreenScraper threads: %d → %d (account says %s)", maxThreads, threads, user.maxThreads)
            maxThreads = threads
            requestSlots = Semaphore(threads)
        }
        if (user.requestsToday != null || user.maxRequestsPerDay != null) _quota.value = user
    }

    private fun logAccountLimitsOnce(body: String) {
        if (accountLimitsLogged.get()) return
        val limits = accountLimits(body) ?: return
        if (accountLimitsLogged.compareAndSet(false, true)) Timber.d("ScreenScraper account limits: %s", limits)
    }

    private fun failureForStatus(status: Int): Pair<SsFailureReason, String>? = when (status) {
        200  -> null
        400  -> SsFailureReason.PARSE_ERROR to "HTTP 400 — malformed request"
        401, 426 -> SsFailureReason.API_CLOSED to "HTTP $status — API closed for non-members right now"
        403  -> SsFailureReason.BAD_DEV_CREDENTIALS to "HTTP 403 — developer credentials rejected"
        404  -> SsFailureReason.NO_MATCH to "HTTP 404 — no game matched"
        429  -> SsFailureReason.RATE_LIMITED to "HTTP 429 — thread/minute limit reached"
        430  -> SsFailureReason.DAILY_QUOTA_EXCEEDED to "HTTP 430 — daily quota exceeded"
        431  -> SsFailureReason.TOO_MANY_UNRECOGNIZED to "HTTP 431 — too many unrecognized ROMs today"
        else -> SsFailureReason.NETWORK_ERROR to "HTTP $status"
    }

    internal fun failureForTextBody(prefix: String): SsFailureReason = when {
        prefix.contains("API closed", ignoreCase = true)        -> SsFailureReason.API_CLOSED
        prefix.contains("quota", ignoreCase = true)             -> SsFailureReason.DAILY_QUOTA_EXCEEDED
        prefix.contains("identifiants", ignoreCase = true) ||
        prefix.contains("Erreur de login", ignoreCase = true)   -> SsFailureReason.BAD_DEV_CREDENTIALS
        else                                                    -> SsFailureReason.PARSE_ERROR
    }

    private fun SsGame.toInfo(): SsGameInfo {
        val title = names.firstOrNull { it.region == "us" }?.text
            ?: names.firstOrNull { it.region == "wor" }?.text
            ?: names.firstOrNull()?.text

        val description = synopsis.firstOrNull { it.language == "en" }?.text
            ?: synopsis.firstOrNull()?.text

        val yearStr = dates.firstOrNull { it.region == "us" }?.text
            ?: dates.firstOrNull { it.region == "wor" }?.text
            ?: dates.firstOrNull()?.text

        val genre = genres.firstOrNull()?.names
            ?.firstOrNull { it.language == "en" }?.text
            ?: genres.firstOrNull()?.names?.firstOrNull()?.text

        val franchise = families.firstOrNull()?.names
            ?.firstOrNull { it.language == "en" }?.text
            ?: families.firstOrNull()?.names?.firstOrNull()?.text

        val ageRating = (
            classifications.firstOrNull { it.type.equals("ESRB", ignoreCase = true) }
                ?: classifications.firstOrNull { it.type.equals("PEGI", ignoreCase = true) }
                ?: classifications.firstOrNull()
            )?.let { c -> c.text?.takeIf { it.isNotBlank() }?.let { "${c.type.orEmpty()} $it".trim() } }

        val communityRating = rating?.text?.toFloatOrNull()?.div(20f)?.coerceIn(0f, 1f)

        val cachedMedias = medias.mapNotNull { m ->
            m.url?.let { SsCachedMedia(type = m.type, region = m.region, url = it, format = m.format) }
        }
        val urls = SsMediaSelection.urls(cachedMedias)

        return SsGameInfo(
            ssId        = id?.toLongOrNull(),
            title       = title,
            description = description,
            developer   = developer?.text,
            publisher   = publisher?.text,
            releaseYear = yearStr?.take(4)?.toIntOrNull(),
            genre       = genre,
            players     = players?.text,
            ageRating   = ageRating,
            franchise   = franchise,
            communityRating = communityRating,
            releaseDate = yearStr?.takeIf { it.length >= 8 },
            artworkUrl  = urls.artworkUrl,
            boxArtUrl   = urls.boxArtUrl,
            box3dUrl    = urls.box3dUrl,
            physicalMediaUrl = urls.physicalMediaUrl,
            screenshotUrl = urls.screenshotUrl,
            heroUrl     = urls.heroUrl,
            logoUrl     = urls.logoUrl,
            manualUrl   = urls.manualUrl,
            videoUrl    = urls.videoUrl,
            videoRawUrl = urls.videoRawUrl,
            medias      = cachedMedias,
        )
    }

    companion object {
        private const val BASE = "https://api.screenscraper.fr/api2"
        private const val MIN_REQUEST_INTERVAL_MS = 1_100L

        internal const val MAX_THREADS = 8
        private const val SEARCH_SOCKET_TIMEOUT_MS = 40_000L

        internal fun requestIntervalMs(maxRequestsPerMinute: Int?): Long {
            val perMinute = maxRequestsPerMinute?.takeIf { it > 0 } ?: return MIN_REQUEST_INTERVAL_MS
            return maxOf(MIN_REQUEST_INTERVAL_MS, (60_000L + perMinute - 1) / perMinute)
        }

        internal fun effectiveThreads(maxThreads: String?): Int =
            maxThreads?.trim()?.toIntOrNull()?.coerceIn(1, MAX_THREADS) ?: 1

        internal fun waitBeforeNextRequest(nowMs: Long, lastStartMs: Long, intervalMs: Long): Long =
            (intervalMs - (nowMs - lastStartMs)).coerceAtLeast(0L)

        private val accountLimitsLogged = AtomicBoolean(false)

        private val ACCOUNT_IDENTIFIERS = setOf("id", "numid")

        internal fun accountLimits(body: String): Map<String, String>? {
            val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
            val user = (root["response"] as? JsonObject)?.get("ssuser") as? JsonObject ?: return null
            return user
                .filterKeys { it !in ACCOUNT_IDENTIFIERS }
                .mapNotNull { (key, value) ->
                    (value as? JsonPrimitive)?.content?.takeIf { it.toLongOrNull() != null }?.let { key to it }
                }
                .toMap()
        }

        private val NON_SLUG = Regex("[^a-z0-9]+")
        private const val MAX_CAPTURES = 20
        private val CAPTURE_JSON = Json { prettyPrint = true }

        internal fun scrubCapture(body: String): String {
            val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return body
            val scrubbed = JsonObject(
                root.mapValues { (key, value) ->
                    when {
                        key == "header" && value is JsonObject -> JsonObject(value - "commandRequested")
                        key == "response" && value is JsonObject -> JsonObject(value - "ssuser")
                        else -> value
                    }
                },
            )
            return CAPTURE_JSON.encodeToString(JsonElement.serializer(), scrubbed)
        }

        internal fun canLookUp(rom: RomIdentity?, ssGameId: Long?): Boolean =
            ssGameId != null || rom?.crc32 != null || rom?.fileName != null

        val PLATFORM_IDS = mapOf(
            "psx"            to 57,
            "ps2"            to 58,
            "ps3"            to 59,
            "psp"            to 61,
            "psvita"         to 62,
            "nes"            to 3,
            "snes"           to 4,
            "n64"            to 14,
            "gb"             to 9,
            "gbc"            to 10,
            "gba"            to 12,
            "nds"            to 15,
            "n3ds"           to 17,
            "gc"             to 13,
            "wii"            to 16,
            "wiiu"           to 18,
            "switch"         to 225,
            "virtualboy"     to 11,
            "megadrive"      to 1,
            "mastersystem"   to 2,
            "gamegear"       to 21,
            "saturn"         to 22,
            "dreamcast"      to 23,
            "segacd"         to 20,
            "sega32x"        to 19,
            "atari2600"      to 26,
            "atari5200"      to 40,
            "atari7800"      to 41,
            "atarilynx"      to 28,
            "pcengine"       to 31,
            "neogeo"         to 142,
            "ngp"            to 25,
            "mame"           to 75,
            "wonderswan"     to 45,
            "wonderswancolor" to 46,
            "xbox"           to 32,
            "x360"           to 33,
            "c64"            to 66,
            "android"        to 63,
            "windows"        to 138,
        )
    }
}

@Serializable
data class SsUserInfoResponse(val response: SsUserInfoBody? = null)

@Serializable
data class SsUserInfoBody(@SerialName("ssuser") val user: SsUser? = null)
