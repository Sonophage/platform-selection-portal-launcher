package com.psplauncher.feature.xmb.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.psplauncher.core.domain.model.MusicTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class MusicPlaybackState(
    val track: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val index: Int = 0,
    val queueSize: Int = 0,
    val isPrepared: Boolean = false,
)

@Singleton
class MusicPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var queue: List<MusicTrack> = emptyList()
    private var index = 0
    private var tickJob: Job? = null

    private val _state = MutableStateFlow(MusicPlaybackState())
    val state: StateFlow<MusicPlaybackState> = _state

    var onTrackStarted: ((MusicTrack) -> Unit)? = null

    fun setQueue(tracks: List<MusicTrack>, startIndex: Int) {
        queue = tracks
        index = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        playCurrent()
    }

    fun playPause() {
        val p = player ?: return
        runCatching { if (p.isPlaying) p.pause() else p.start() }

        if (p.isPlaying) startTicker() else { tickJob?.cancel(); tickJob = null }
        emit()
    }

    fun next() {
        if (index < queue.lastIndex) { index++; playCurrent() } else seekTo(0)
    }

    fun prev() {
        if ((player?.currentPosition ?: 0) > 3000 || index == 0) seekTo(0)
        else { index--; playCurrent() }
    }

    fun seekTo(ms: Int) {
        val p = player ?: return
        runCatching { p.seekTo(ms.coerceIn(0, p.duration.coerceAtLeast(0))) }
        emit()
    }

    fun seekBy(deltaMs: Int) {
        val p = player ?: return
        seekTo((p.currentPosition + deltaMs))
    }

    fun stop() {
        tickJob?.cancel(); tickJob = null
        releasePlayer()
        queue = emptyList(); index = 0
        _state.value = MusicPlaybackState()
    }

    fun currentTrack(): MusicTrack? = queue.getOrNull(index)

    private fun playCurrent() {
        val track = queue.getOrNull(index) ?: return stop()
        releasePlayer()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnCompletionListener { next() }
            setOnErrorListener { _, what, extra ->
                Timber.w("MediaPlayer error what=$what extra=$extra for ${track.displayTitle}")
                next(); true
            }
            setOnPreparedListener { mp ->
                runCatching { mp.start() }
                startTicker()
                emit()
                onTrackStarted?.invoke(track)
            }
            runCatching {
                setDataSource(context, Uri.parse(track.uri))
                prepareAsync()
            }.onFailure {
                Timber.w(it, "Failed to load ${track.displayTitle}; skipping")
                next()
            }
        }

        _state.value = MusicPlaybackState(
            track = track, isPlaying = false, positionMs = 0, durationMs = 0,
            index = index, queueSize = queue.size, isPrepared = false,
        )
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                emit()
                delay(500)
            }
        }
    }

    private fun emit() {
        val p = player
        val track = queue.getOrNull(index)
        _state.value = MusicPlaybackState(
            track = track,
            isPlaying = runCatching { p?.isPlaying == true }.getOrDefault(false),
            positionMs = runCatching { p?.currentPosition ?: 0 }.getOrDefault(0),
            durationMs = runCatching { p?.duration?.coerceAtLeast(0) ?: 0 }.getOrDefault(0),
            index = index,
            queueSize = queue.size,
            isPrepared = p != null,
        )
    }

    private fun releasePlayer() {
        player?.let { p -> runCatching { p.reset(); p.release() } }
        player = null
    }
}
