package com.psplauncher.core.ui.media

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class MenuMusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var player: ExoPlayer? = null

    private var wanted: Boolean = false

    private var source: String? = null

    private var holdsFocus: Boolean = false

    private val audioManager: AudioManager?
        get() = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        holdsFocus = holdsFocusAfter(change)

        if (holdsFocus) { if (wanted) startOrResume() } else pause()
    }

    fun setWanted(wanted: Boolean, track: String?) {
        val trackChanged = track != source
        source = track
        this.wanted = wanted && track != null
        when {
            !this.wanted -> release()
            trackChanged -> restart()
            else -> requestFocusAndPlay()
        }
    }

    private fun restart() {
        release()
        requestFocusAndPlay()
    }

    private fun requestFocusAndPlay() {
        val manager = audioManager ?: return
        if (!holdsFocus) {
            @Suppress("DEPRECATION")

            val granted = manager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
            holdsFocus = granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!holdsFocus) {
                Timber.d("Menu music: audio focus refused — something else is playing")
                return
            }
        }
        startOrResume()
    }

    private fun startOrResume() {
        val uri = source ?: return
        if (!wanted || !holdsFocus) return
        val existing = player
        if (existing != null) {
            existing.playWhenReady = true
            return
        }
        player = ExoPlayer.Builder(context).build().apply {
            addListener(listener)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),

                false,
            )
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(MediaItem.fromUri(uri))
            playWhenReady = true
            prepare()
        }
        Timber.d("Menu music playing: $uri")
    }

    private fun pause() {
        player?.playWhenReady = false
    }

    private fun release() {
        player?.run {
            removeListener(listener)
            release()
        }
        player = null
        if (holdsFocus) {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(focusListener)
            holdsFocus = false
        }
    }

    internal companion object {
        internal fun holdsFocusAfter(change: Int): Boolean = change == AudioManager.AUDIOFOCUS_GAIN
    }

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.w(error, "Menu music playback failed — releasing")
            release()
        }
    }
}
