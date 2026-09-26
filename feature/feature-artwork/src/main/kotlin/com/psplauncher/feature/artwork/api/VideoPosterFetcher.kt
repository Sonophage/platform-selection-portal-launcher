package com.psplauncher.feature.artwork.api

import android.content.Context
import com.psplauncher.core.domain.model.MovieFileName
import com.psplauncher.core.domain.repository.VideoRepository
import com.psplauncher.feature.artwork.store.ArtworkKind
import com.psplauncher.feature.artwork.store.ArtworkTempIO
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class VideoPosterResult(val matched: Int, val skipped: Int, val failed: Int) {
    fun message(): String = when {
        matched == 0 && failed == 0 && skipped == 0 -> "No videos to match"
        matched == 0 -> "No posters found for $failed ${if (failed == 1) "film" else "films"}"
        failed == 0 -> "Matched $matched of ${matched + skipped + failed}"
        else -> "Matched $matched, $failed not found"
    }
}

@Singleton
class VideoPosterFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,
    private val tmdb: TmdbApi,
    private val http: HttpClient,
    private val keyProvider: TmdbApiKeyProvider,
) {
    private val posterDir: File get() = File(context.filesDir, "video_posters").apply { mkdirs() }

    suspend fun run(refreshExisting: Boolean = false): VideoPosterResult = withContext(Dispatchers.IO) {
        if (!keyProvider.hasKey()) return@withContext VideoPosterResult(0, 0, 0)

        val videos = videoRepository.getAllVideos()
        var matched = 0
        var skipped = 0
        var failed = 0

        for (video in videos) {
            if (!refreshExisting && !video.posterUri.isNullOrBlank()) {
                skipped++
                continue
            }
            val title = MovieFileName.bareTitleOf(video.displayName)
            val year = MovieFileName.yearOf(video.displayName)

            val movie = tmdb.findMovie(title, year)
            val url = movie?.posterUrl
            if (url == null) {
                failed++
                continue
            }

            val temp = ArtworkTempIO.downloadToTemp(http, context.cacheDir, ArtworkKind.BACKGROUND, url)
            if (temp == null) {
                failed++
                continue
            }
            val dest = File(posterDir, "${video.id}.jpg")
            val moved = runCatching {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
                true
            }.getOrDefault(false)

            if (moved) {
                videoRepository.setPosterUri(video.id, "file://${dest.absolutePath}")
                matched++
                Timber.i("TMDB matched \"${video.displayName}\" to ${movie.title} (${movie.year})")
            } else {
                failed++
            }
        }
        VideoPosterResult(matched, skipped, failed)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        videoRepository.getAllVideos().forEach { video ->
            if (!video.posterUri.isNullOrBlank()) videoRepository.setPosterUri(video.id, null)
        }
        runCatching { posterDir.listFiles()?.forEach { it.delete() } }
        Unit
    }
}
