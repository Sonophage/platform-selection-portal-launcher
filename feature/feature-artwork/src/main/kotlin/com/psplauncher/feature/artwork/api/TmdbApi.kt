package com.psplauncher.feature.artwork.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TMDB_SEARCH = "https://api.themoviedb.org/3/search/movie"

private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

@Serializable
private data class TmdbSearchResponse(val results: List<TmdbResult> = emptyList())

@Serializable
private data class TmdbResult(
    val id: Int = 0,
    val title: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
)

data class TmdbMovie(
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
)

@Singleton
class TmdbApi @Inject constructor(
    private val http: HttpClient,
    private val keyProvider: TmdbApiKeyProvider,
) {
    suspend fun findMovie(title: String, year: Int?): TmdbMovie? {
        val key = keyProvider.getKey()?.takeIf { it.isNotBlank() } ?: return null
        val response = runCatching {
            http.get(TMDB_SEARCH) {
                parameter("api_key", key)
                parameter("query", title)
                if (year != null) parameter("year", year)
                parameter("include_adult", "false")
            }.body<TmdbSearchResponse>()
        }.onFailure { Timber.w(it, "TMDB search failed for \"$title\"") }.getOrNull() ?: return null

        val candidates = response.results
        if (candidates.isEmpty()) {
            Timber.i("TMDB had nothing for \"$title\" ($year)")
            return null
        }

        val exact = if (year == null) candidates.first()
        else candidates.firstOrNull { it.releaseDate?.take(4)?.toIntOrNull() == year }

        if (exact == null) {
            Timber.i("TMDB found \"$title\" but nothing released in $year — left unmatched")
            return null
        }

        return TmdbMovie(
            tmdbId = exact.id,
            title = exact.title,
            year = exact.releaseDate?.take(4)?.toIntOrNull(),
            posterUrl = exact.posterPath?.let { "$TMDB_IMAGE_BASE$it" },
        )
    }
}
