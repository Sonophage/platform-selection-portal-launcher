package com.psplauncher.core.data.launch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A launch disc that should be on screen, and the art printed on its face.
 *
 * [art] is Any? because that is what Coil takes and what the callers actually hold: a uri string
 * for a cover on disk, or a Drawable for an installed app's icon, which comes from PackageManager
 * and has no uri at all. Held only for the length of one ceremony.
 */
data class MediaLaunchRequest(val art: Any?)

/**
 * The seam between "this media is going to open" and "it has the screen" — the disc's gate.
 *
 * A singleton for the same reason GameBootGate is one: the callers are in different ViewModels
 * (the XMB opens books, tracks and apps, Video Detail opens films, the App Drawer opens apps)
 * while a single renderer at the top of the shell draws for all of them. It began as a private
 * deferred inside XMBViewModel, which was right while every caller was a method in that class and
 * wrong the moment anything else needed it — a second copy of a gate is two watchdogs to keep in
 * step.
 *
 * In core-data, not feature-launcher, for the reason GameBootPreferences gives for living here:
 * several feature modules depend on it and must never disagree, and a feature-to-feature edge to
 * arrange that is worse than the shared module that already exists.
 *
 * Games do NOT come through here. They have GameBootGate, whose switch decides whether a game gets
 * a presentation at all; the disc is what that gate now presents.
 *
 * **The caller can never be trapped.** [awaitHandOff] is bounded and a timeout PROCEEDS with the
 * launch: a stuck overlay costs a moment, never the thing that was asked for.
 */
@Singleton
class MediaLaunchGate @Inject constructor(
    private val preferences: LaunchDiscPreferences,
) {

    private val _active = MutableStateFlow<MediaLaunchRequest?>(null)

    /** Non-null while a disc should be on screen. */
    val active: StateFlow<MediaLaunchRequest?> = _active.asStateFlow()

    @Volatile
    private var handOff: CompletableDeferred<Unit>? = null

    /**
     * Raises the disc and suspends until it is ready to hand over — which is the instant it begins
     * fading out, NOT when it disappears. The caller opens the thing at that point, so the app's
     * cold start happens under the fade.
     *
     * Returns immediately when a disc is already up: a second request is dropped, not queued, so a
     * double-press cannot stack two ceremonies.
     */
    suspend fun awaitHandOff(art: Any?) {
        // The switch, asked HERE rather than at each caller: this is the one seam every non-game
        // launch already suspends on, so a fifth call site cannot forget to check it. Off means the
        // launch simply happens, with no transition and no delay.
        if (!preferences.launchDiscEnabledFlow.first()) return
        if (_active.value != null) {
            Timber.d("Launch disc already presenting — ignoring a second request")
            return
        }
        val done = CompletableDeferred<Unit>()
        handOff = done
        _active.value = MediaLaunchRequest(art)
        try {
            withTimeout(TIMEOUT_MS) { done.await() }
        } catch (_: TimeoutCancellationException) {
            // Swallowed on purpose: the launch continues. Never trap the user behind a transition.
            Timber.w("Launch disc watchdog fired after ${TIMEOUT_MS}ms — opening anyway")
            _active.value = null
        } finally {
            handOff = null
        }
    }

    /** The disc has begun fading: whatever was waiting on it may open now. */
    fun onHandOff() {
        handOff?.complete(Unit)
    }

    /**
     * The disc has finished fading and can come off the screen.
     *
     * Separate from [onHandOff] for the reason [GameBootGate] needed the same split: the disc
     * releases the launch as it starts fading and stays up for the rest of that fade, so clearing
     * at the hand-off would pull it off mid-animation.
     */
    fun onDismissed() {
        _active.value = null
    }

    companion object {
        /**
         * Watchdog, generous against the disc's own ~3.7s timeline because it is not a schedule.
         * It exists for an overlay that never reports back at all — a composition torn down
         * mid-animation — not to police a slow frame.
         */
        const val TIMEOUT_MS = 9_000L
    }
}
