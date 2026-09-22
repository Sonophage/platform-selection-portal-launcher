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

// ── TMDB, for films ───────────────────────────────────────────────────────────
//
// Every other provider in this app is about games, so a video library had no source of artwork at
// all: what it showed was a frame grabbed out of the file, which for a film is usually a dark
// still of nothing in particular. The owner's copy of Dune showed an unlit face.
//
// The match is on TITLE and YEAR, which is exactly what MovieFileName already pulls out of a
// scene-release filename — "Dune.2021.1080p.BluRay.x264-GalaxyRG.mkv" becomes "Dune" and 2021.
// TMDB's search takes both, and a year turns an ambiguous title into an unambiguous one: there
// are several films called Dune and only one from 2021.

private const val TMDB_SEARCH = "https://api.themoviedb.org/3/search/movie"

/** TMDB serves images from a separate host, sized by a path segment. w500 is a poster at card size. */
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

/** A matched film: what TMDB thinks it is, and where its poster lives. */
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

    /**
     * The best match for [title], preferring one released in [year].
     *
     * Returns null for every failure — no key, no network, no results, a result with no poster —
     * because a film this cannot identify is a film that keeps its frame grab, not an error the
     * user has to dismiss. The log line is there for when someone asks why one did not match.
     *
     * The year is passed to TMDB as a filter AND checked again here. TMDB's `year` parameter is a
     * hint rather than a constraint: it ranks matching years first but still returns others, so a
     * title with no release that year comes back with the wrong film at the top rather than with
     * nothing. Checking it again is what turns that into "no match".
     */
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

        // A year the caller supplied is a fact off the filename, so a candidate that disagrees
        // with it is the wrong film however well its title scores.
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
