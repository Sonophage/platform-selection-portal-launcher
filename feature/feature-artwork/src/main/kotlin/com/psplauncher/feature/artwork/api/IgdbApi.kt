package com.psplauncher.feature.artwork.api

import com.psplauncher.feature.artwork.MetadataApiKeyProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Singleton

@Serializable
data class IgdbTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in")   val expiresIn: Long,
    @SerialName("token_type")   val tokenType: String = "bearer",
)

@Serializable
data class IgdbGame(
    val id: Long,
    val name: String? = null,
    val cover: IgdbImage? = null,
    val artworks: List<IgdbImage> = emptyList(),

    @SerialName("first_release_date") val firstReleaseDate: Long? = null,
)

@Serializable
data class IgdbImage(
    val id: Long = 0,
    @SerialName("image_id") val imageId: String? = null,
)

data class IgdbGameInfo(
    val artworkUrl: String?,
    val heroUrl: String?,
    val logoUrl: String?,
)

private data class IgdbToken(val accessToken: String, val expiresAtMs: Long)

@Singleton
class IgdbApi @Inject constructor(
    private val httpClient: HttpClient,
    private val keyProvider: MetadataApiKeyProvider,
) {
    private var cachedToken: IgdbToken? = null

    suspend fun hasCredentials(): Boolean = keyProvider.hasIgdbCredentials()

    suspend fun fetchGameInfo(platformId: String, title: String): IgdbGameInfo? =
        query(bestMatchBody(title), "'$title'")?.firstOrNull()?.toInfo()

    suspend fun searchGames(title: String, limit: Int = SEARCH_LIMIT): List<IgdbGame> =
        query(searchBody(title, limit), "search '$title'").orEmpty()

    suspend fun fetchGameInfoById(igdbId: Long): IgdbGameInfo? =
        query(byIdBody(igdbId), "id $igdbId")?.firstOrNull()?.toInfo()

    private suspend fun query(body: String, what: String): List<IgdbGame>? {
        val clientId = keyProvider.getIgdbClientId() ?: return null
        val clientSecret = keyProvider.getIgdbClientSecret() ?: return null
        val token = obtainToken(clientId, clientSecret) ?: return null

        return try {
            httpClient.post("$BASE/games") {
                header("Client-ID", clientId)
                header("Authorization", "Bearer ${token.accessToken}")
                contentType(ContentType.Text.Plain)
                setBody(body)
            }.body<List<IgdbGame>>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "IGDB request failed for $what")
            null
        }
    }

    private fun IgdbGame.toInfo() = IgdbGameInfo(
        artworkUrl = cover?.imageId?.let { coverImageUrl(it) },
        heroUrl    = artworks.firstOrNull()?.imageId?.let { artworkImageUrl(it) },
        logoUrl    = null,
    )

    suspend fun testCredentials(clientId: String, clientSecret: String): Boolean = try {
        val http = httpClient.submitForm(
            url = "$AUTH_BASE/token",
            formParameters = parameters {
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("grant_type", "client_credentials")
            },
        )
        if (!http.status.isSuccess()) {
            Timber.w("IGDB token request refused: " + http.status + " " + http.bodyAsText())
            false
        } else {
            http.body<IgdbTokenResponse>().accessToken.isNotBlank()
        }
    } catch (e: Exception) {
        Timber.w(e, "IGDB credential test failed")
        false
    }

    private suspend fun obtainToken(clientId: String, clientSecret: String): IgdbToken? {
        val cached = cachedToken
        if (cached != null && cached.expiresAtMs > System.currentTimeMillis()) return cached

        return try {
            val response: IgdbTokenResponse =
            httpClient.submitForm(
                url = "$AUTH_BASE/token",
                formParameters = parameters {
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("grant_type", "client_credentials")
                },
            ).body()
            val expiresAt = System.currentTimeMillis() + (response.expiresIn - 60L) * 1_000L
            IgdbToken(response.accessToken, expiresAt).also { cachedToken = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "IGDB token fetch failed")
            null
        }
    }

    companion object {
        private const val BASE      = "https://api.igdb.com/v4"
        private const val AUTH_BASE = "https://id.twitch.tv/oauth2"
        private const val SEARCH_LIMIT = 10

        private fun quoted(title: String) = "\"" + title.replace("\"", "'") + "\""

        internal fun bestMatchBody(title: String) =
            "search ${quoted(title)}; fields name,cover.image_id,artworks.image_id; limit 1;"

        internal fun searchBody(title: String, limit: Int) =
            "search ${quoted(title)}; fields name,first_release_date,cover.image_id; limit $limit;"

        internal fun byIdBody(igdbId: Long) =
            "fields name,cover.image_id,artworks.image_id; where id = $igdbId;"

        fun coverThumbUrl(imageId: String)   = "https://images.igdb.com/igdb/image/upload/t_cover_small/$imageId.jpg"
        fun coverImageUrl(imageId: String)   = "https://images.igdb.com/igdb/image/upload/t_cover_big/$imageId.jpg"
        fun artworkImageUrl(imageId: String) = "https://images.igdb.com/igdb/image/upload/t_screenshot_big/$imageId.jpg"
    }
}
