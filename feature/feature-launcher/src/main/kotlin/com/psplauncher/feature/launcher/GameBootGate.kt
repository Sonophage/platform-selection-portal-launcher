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

data class GameBootRequest(
    val gameTitle: String,
    val videoPath: String? = null,
    val audioPath: String? = null,

    val coverArt: String? = null,
)

@Singleton
class GameBootGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: GameBootPreferences,
    private val uiMedia: UiMediaStore,
    private val audioPlayer: UiMediaAudioPlayer,

    @LaunchDispatcherScope private val scope: CoroutineScope,
) {
    private var pendingSound: Job? = null

    private val _active = MutableStateFlow<GameBootRequest?>(null)

    val active: StateFlow<GameBootRequest?> = _active.asStateFlow()

    @Volatile
    private var completion: CompletableDeferred<Unit>? = null

    val isActive: Boolean get() = _active.value != null

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
                customAudioPath = uiMedia.pathFor(UiMediaSlot.GAMEBOOT_AUDIO),
                defaultUri = gameBootDefaultAudioUri(context),
            )
        }
        _active.value = GameBootRequest(gameTitle = gameTitle, videoPath = video, audioPath = audio, coverArt = coverArt)

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
            Timber.w("GameBoot watchdog fired after ${TIMEOUT_MS}ms — launching anyway")
            clear()
        } finally {
            completion = null
        }
    }

    fun onPresentationFinished() {
        completion?.complete(Unit)
    }

    fun onPresentationDismissed() {
        clear()
    }

    private fun clear() {
        pendingSound?.cancel()
        pendingSound = null
        completion = null
        _active.value = null
    }

    companion object {
        const val TIMEOUT_MS = 13_000L
    }
}
