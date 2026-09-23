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

/**
 * The launcher's own background music: one track, looping, for as long as the launcher is the
 * thing on screen and nothing else wants the speaker.
 *
 * Deliberately NOT [UiMediaAudioPlayer]. That one is a one-shot whose [UiMediaAudioPlayer.stop]
 * releases the player, and it is shared by the boot chime and the GameBoot cue — both of which
 * fire while this is supposed to keep going. Two jobs, two players; a single player would have
 * the launch sound killing the music every time you opened something.
 *
 * ── Giving way ────────────────────────────────────────────────────────────────
 *
 * Audio focus is what makes this liveable rather than obnoxious. Spotify, a video, a call, a
 * game with sound: any of them asks the system for focus, and the moment we lose it the music
 * stops. It comes back only when focus returns AND the launcher is still the thing on screen.
 *
 * Every kind of loss stops it, including LOSS_TRANSIENT_CAN_DUCK, where the polite behaviour
 * would be to play on quietly underneath. Ducking is right for a navigation app talking over
 * music; it is wrong for music talking over music, which is what this would be.
 *
 * [setWanted] is the user's switch and the launcher's foreground state combined into one
 * question. It is the only way in: nothing here starts playing because a track was assigned or
 * because focus came back on its own — those only matter when the answer to that question was
 * already yes.
 */
@Singleton
class MenuMusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var player: ExoPlayer? = null

    /** The user's switch AND the launcher being on screen. Nothing plays while this is false. */
    private var wanted: Boolean = false

    /** The track to loop, or null when the user has not assigned one. */
    private var source: String? = null

    /** True while the system has granted us focus. Playback needs [wanted] and this. */
    private var holdsFocus: Boolean = false

    private val audioManager: AudioManager?
        get() = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        holdsFocus = holdsFocusAfter(change)
        // Only resumes something that was already wanted. Focus coming back while the launcher is
        // in the background must not start music behind a game.
        if (holdsFocus) { if (wanted) startOrResume() } else pause()
    }

    /**
     * Sets whether the music should be playing at all, and what it should play.
     *
     * Called with the user's preference AND the launcher's foreground state already combined, so
     * this has one input rather than two flags that can disagree. [track] null means the user has
     * assigned nothing, which is silence however the switch is set.
     */
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
            @Suppress("DEPRECATION") // requestAudioFocus(AudioFocusRequest) is API 26+; minSdk is
            // 29, but the listener form below is what the deprecated overload takes and the
            // modern builder needs the same listener — kept on the simple call until this needs
            // the extra controls (pause-on-duck, delayed focus) that the builder exists for.
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
                // false: focus is handled here, by hand, because the rule is "stop" and not the
                // duck-and-continue that handleAudioFocus implements.
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

    /** Stops, releases the player and hands focus back. */
    private fun release() {
        player?.run {
            removeListener(listener)
            release()
        }
        player = null
        if (holdsFocus) {
            @Suppress("DEPRECATION") // pairs with the request above.
            audioManager?.abandonAudioFocus(focusListener)
            holdsFocus = false
        }
    }

    internal companion object {
        /**
         * Whether we still hold focus after the system reports [change].
         *
         * Pulled out as a pure function because it is the one decision in this class that has
         * nothing to do with players or managers, and the one a later refactor is most likely to
         * soften: LOSS_TRANSIENT_CAN_DUCK is an invitation to keep playing quietly, and the
         * polite answer to it is usually yes. Not here. Ducking is right for a navigation app
         * talking over music; this IS music, and music under music is noise.
         */
        internal fun holdsFocusAfter(change: Int): Boolean = change == AudioManager.AUDIOFOCUS_GAIN
    }

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            // A track that will not decode must not leave us holding focus forever, silently
            // keeping every other app quiet.
            Timber.w(error, "Menu music playback failed — releasing")
            release()
        }
    }
}
