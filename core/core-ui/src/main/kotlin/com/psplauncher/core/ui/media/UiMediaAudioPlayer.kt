package com.psplauncher.core.ui.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.psplauncher.core.domain.model.UiMediaSlot
import com.psplauncher.core.ui.sound.MenuSoundPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

@Singleton
class UiMediaAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var player: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)

    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun play(slot: UiMediaSlot = UiMediaSlot.BOOT_AUDIO, customPath: String?) {
        val source = customPath
            ?: slot.bundledDefaultUri(context.packageName)
            ?: return
        play(uri = source, clipEndMs = slot.limits.hardMaxMs, label = slot.key)
    }

    fun play(uri: String, clipEndMs: Long, label: String) {
        stop()
        (player ?: ExoPlayer.Builder(context).build().also { player = it }).apply {
            addListener(listener)
            setMediaItem(
                MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setEndPositionMs(clipEndMs)
                            .build()
                    )
                    .build()
            )
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
        _isPlaying.value = true
        Timber.d("$label audio playing: $uri")
    }

    fun stop() {
        _isPlaying.value = false
        player?.run {
            removeListener(listener)
            release()
        }
        player = null
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                _isPlaying.value = false
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.w(error, "UI-media audio playback failed")
            _isPlaying.value = false
        }
    }
}
