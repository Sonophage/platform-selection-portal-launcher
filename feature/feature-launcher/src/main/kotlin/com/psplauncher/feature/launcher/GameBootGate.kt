package com.psplauncher.feature.launcher

import android.content.Context
import com.psplauncher.core.data.repository.GameBootPreferences
import com.psplauncher.core.data.repository.UiMediaStore
import com.psplauncher.core.domain.model.UiMediaSlot
import com.psplauncher.core.ui.media.UiMediaAudioPlayer
import com.psplauncher.core.ui.media.gameBootDefaultAudioUri
import com.psplauncher.core.ui.media.resolveGameBootAudio
import com.psplauncher.themekit.UiMediaLimits
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/** What the shell needs to draw one GameBoot presentation. */
data class GameBootRequest(
    val gameTitle: String,
    val videoPath: String? = null,
    val audioPath: String? = null,
    /**
     * The game's cover, for the built-in presentation to print on the disc.
     *
     * Only read when [videoPath] is null. A user who supplied their own clip gets their clip —
     * the built-in sequence is what the disc replaced, not their asset.
     */
    val coverArt: String? = null,
)

/**
 * The seam between "the launch is going to happen" and "the emulator has the screen".
 *
 * [LaunchDispatcher.launch] awaits this immediately before `startActivity`, so the presentation
 * only ever plays for a launch that has already passed every preflight — a game that cannot start
 * never shows a GameBoot. The XMB shell observes [active] and composes the overlay; when the
 * overlay finishes (or is skipped) it calls [onPresentationFinished] and the launch proceeds.
 *
 * **The user can never be trapped here.** [awaitPresentation] is bounded by [TIMEOUT_MS] and a
 * timeout PROCEEDS with the launch rather than throwing — a stuck presentation costs the user
 * eight seconds, not their game.
 *
 * **One GameBoot, one switch, one asset.** GameBoot is either on or off ([GameBootPreferences]),
 * and when it is on the user either keeps the built-in five-second sequence or replaces the whole
 * thing with a clip of their own — there is no separate sound to assign, which is why
 * [resolveGameBootAudio] has only two branches: the built-in sound plays under the built-in
 * sequence, and a custom clip is left to its own track.
 *
 * **The launch waits for the whole presentation.** [awaitPresentation] does not return until the
 * overlay reports back, and the built-in sequence runs its full five seconds in every case — the
 * motion budget drops its motion, not its length — so the emulator never takes the screen partway
 * through. The audio is started here rather than by the overlay only so it begins before the first
 * frame is drawn and stays in sync with a timeline that was measured against it; the overlay is
 * draw-only and can never release the player mid-clip.
 */
@Singleton
class GameBootGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: GameBootPreferences,
    private val uiMedia: UiMediaStore,
    private val audioPlayer: UiMediaAudioPlayer,
    // The same scope LaunchDispatcher takes, and injected for the same reason: building one here
    // touches Dispatchers.Main at construction, which a plain JVM unit test has no answer for.
    // Hilt provides Main.immediate; a test provides its own, and the delayed cue then runs on the
    // test's virtual clock instead of a real 4.5 seconds.
    @LaunchDispatcherScope private val scope: CoroutineScope,
) {
    // The GameBoot sound is scheduled against the disc's exit and must not hold up the launch it
    // belongs to, so it runs here rather than inline in awaitPresentation — which is suspended on
    // the presentation's own completion at that point.
    //
    // Cancelled with the presentation: a cue for a ceremony that has been skipped or torn down by
    // the watchdog would arrive over whatever came next.
    private var pendingSound: Job? = null

    private val _active = MutableStateFlow<GameBootRequest?>(null)

    /** Non-null while a presentation should be on screen. */
    val active: StateFlow<GameBootRequest?> = _active.asStateFlow()

    // Completed by the overlay (natural end, skip, or its own error). Replaced per presentation.
    @Volatile
    private var completion: CompletableDeferred<Unit>? = null

    /** True while a presentation is running — used to drop a duplicate launch request. */
    val isActive: Boolean get() = _active.value != null

    /**
     * Runs the GameBoot presentation when it is switched on, and returns immediately when it is
     * not — in which case nothing plays and the launch is silent by decision. A game boot is
     * never scored by the menu's Launch Sound, which stays the app-launch sound and is suppressed
     * at the confirm sites for games in both states; with GameBoot off, off means off.
     *
     * When on, this suspends until the presentation finishes, is skipped, or times out. A second
     * request while one is already on screen is dropped, not queued.
     */
    suspend fun awaitPresentation(gameTitle: String, coverArt: String? = null) {
        if (!preferences.gameBootEnabledFlow.first()) return
        if (isActive) {
            Timber.d("GameBoot already presenting — ignoring a second request for $gameTitle")
            return
        }
        val done = CompletableDeferred<Unit>()
        completion = done
        val (video, audio) = withContext(Dispatchers.IO) {
            val customVideo = uiMedia.pathFor(UiMediaSlot.GAMEBOOT_VIDEO)
            customVideo to resolveGameBootAudio(
                customVideoPath = customVideo,
                defaultUri = gameBootDefaultAudioUri(context),
            )
        }
        _active.value = GameBootRequest(gameTitle = gameTitle, videoPath = video, audioPath = audio, coverArt = coverArt)
        // WHEN the GameBoot sound plays depends on what is presenting it.
        //
        // A custom clip carries its own audio and its own timing, so it starts with the first
        // frame and `audio` is null for it anyway.
        //
        // The built-in disc does not. Its sound used to start with the first frame too, which put
        // the whole cue under the fade-in and the spin and left the moment that actually matters
        // -- the disc going, the screen handing over -- in silence. It is scheduled against the
        // disc's exit instead: the ceremony opens on the crossbar's browse cue and this lands as
        // the disc leaves.
        //
        // The delay is read from DiscCeremony rather than written out here. It is the one place
        // the ceremony's phases are defined, and a second copy of that sum over here is the pair
        // that drifts the first time a phase is retuned.
        audio?.let { track ->
            if (video != null) {
                audioPlayer.play(uri = track, clipEndMs = UiMediaLimits.GAMEBOOT_SEQUENCE_MS, label = "gameboot")
            } else {
                pendingSound = scope.launch {
                    delay(com.psplauncher.core.ui.components.DiscCeremony.DiscOutStartMs.toLong())
                    audioPlayer.play(uri = track, clipEndMs = UiMediaLimits.GAMEBOOT_SEQUENCE_MS, label = "gameboot")
                }
            }
        }
        try {
            withTimeout(TIMEOUT_MS) { done.await() }
        } catch (_: TimeoutCancellationException) {
            // Deliberately swallowed: the launch continues. Rule 13 — never trap the user on the
            // transition screen because a player stalled. The watchdog tears the overlay down
            // itself, because by definition nothing else is going to.
            Timber.w("GameBoot watchdog fired after ${TIMEOUT_MS}ms — launching anyway")
            clear()
        } finally {
            // The deferred is spent either way. The REQUEST is not: the disc presentation releases
            // the launch as it starts spinning and stays on screen for another second while the
            // emulator loads under it, so clearing here would pull the overlay off mid-animation.
            // [onPresentationDismissed] is what takes it down.
            completion = null
        }
    }

    /**
     * The overlay reports the LAUNCH may proceed (ended, skipped, failed, or — for the disc — spun
     * up far enough that the game should now start behind it).
     */
    fun onPresentationFinished() {
        completion?.complete(Unit)
    }

    /** The overlay has left the screen and the request can be dropped. */
    fun onPresentationDismissed() {
        clear()
    }

    private fun clear() {
        // Before the state, so a cue scheduled for a presentation that is being torn down cannot
        // arrive over whatever takes the screen next.
        pendingSound?.cancel()
        pendingSound = null
        completion = null
        _active.value = null
    }

    companion object {
        /**
         * Hard cap on the whole presentation, sized from the LONGEST one a user can produce: a
         * 10 s custom clip ([UiMediaLimits.GAMEBOOT_CLIP_MAX_MS]) plus the overlay's fade and a
         * slow first frame. Deliberately longer than either of GameBootOverlay's caps so the
         * overlay normally resolves itself and this watchdog stays the last resort.
         *
         * The built-in sequence is bounded far more tightly by the overlay's own sequence cap;
         * this number exists for the clip path, which is the only one that can genuinely stall.
         */
        const val TIMEOUT_MS = 13_000L
    }
}
