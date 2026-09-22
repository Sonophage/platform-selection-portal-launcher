package com.psplauncher.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.domain.repository.BookRepository
import com.psplauncher.core.domain.repository.GameRepository
import com.psplauncher.core.domain.repository.MusicRepository
import com.psplauncher.core.domain.repository.VideoRepository
import com.psplauncher.feature.artwork.api.ArtworkRepository
import com.psplauncher.feature.artwork.api.ArtworkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * What the launcher currently holds, all of it derived.
 *
 * Nothing here is a stored summary: every number is produced by asking the thing that owns it,
 * each time the screen opens. A counts table would be one more pair to keep in step, and it would
 * be wrong the moment a scan ran.
 */
data class OverviewUiState(
    val games: Int = 0,
    val tracks: Int = 0,
    val books: Int = 0,
    val videos: Int = 0,
    val artwork: ArtworkStatus = ArtworkStatus(),
    /** Null until the size has been measured — it walks the cache directory, so it is not instant. */
    val artworkCacheBytes: Long? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class OverviewSettingsViewModel @Inject constructor(
    gameRepository: GameRepository,
    musicRepository: MusicRepository,
    bookRepository: BookRepository,
    videoRepository: VideoRepository,
    private val artworkRepository: ArtworkRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OverviewUiState())
    val state: StateFlow<OverviewUiState> = _state

    init {
        // The four library counts come off flows, so the page stays right while a scan finishes
        // behind it rather than showing whatever was true when it opened.
        viewModelScope.launch {
            combine(
                gameRepository.observeAll(),
                musicRepository.observeAllTracks(),
                bookRepository.observeAllBooks(),
                videoRepository.observeAllVideos(),
            ) { games, tracks, books, videos ->
                listOf(games.size, tracks.size, books.size, videos.size)
            }.collect { (games, tracks, books, videos) ->
                _state.update {
                    it.copy(games = games, tracks = tracks, books = books, videos = videos, loading = false)
                }
            }
        }
        // Artwork status and cache size both walk the disk, so they are one-shot rather than
        // flows, and each is allowed to fail without taking the rest of the page with it.
        viewModelScope.launch {
            runCatching { artworkRepository.computeStatus() }
                .onSuccess { status -> _state.update { it.copy(artwork = status) } }
                .onFailure { Timber.w(it, "Overview could not read the artwork status") }
            runCatching { artworkRepository.cacheSizeBytes() }
                .onSuccess { bytes -> _state.update { it.copy(artworkCacheBytes = bytes) } }
                .onFailure { Timber.w(it, "Overview could not measure the artwork cache") }
        }
    }
}
