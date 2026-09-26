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

data class MediaLaunchRequest(val art: Any?)

@Singleton
class MediaLaunchGate @Inject constructor(
    private val preferences: LaunchDiscPreferences,
) {
    private val _active = MutableStateFlow<MediaLaunchRequest?>(null)

    val active: StateFlow<MediaLaunchRequest?> = _active.asStateFlow()

    @Volatile
    private var handOff: CompletableDeferred<Unit>? = null

    suspend fun awaitHandOff(art: Any?) {
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
            Timber.w("Launch disc watchdog fired after ${TIMEOUT_MS}ms — opening anyway")
            _active.value = null
        } finally {
            handOff = null
        }
    }

    fun onHandOff() {
        handOff?.complete(Unit)
    }

    fun onDismissed() {
        _active.value = null
    }

    companion object {
        const val TIMEOUT_MS = 9_000L
    }
}
