package com.psplauncher.feature.launcher

import android.content.Context
import com.psplauncher.core.data.repository.GameBootPreferences
import com.psplauncher.core.data.repository.UiMediaStore
import com.psplauncher.core.domain.model.UiMediaSlot
import com.psplauncher.core.ui.media.UiMediaAudioPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameBootGateTest {
    private class Harness(val scope: TestScope, enabled: Boolean, customVideo: String? = null) {
        val prefs: GameBootPreferences = mockk(relaxed = true) {
            every { gameBootEnabledFlow } returns flowOf(enabled)
        }
        val store: UiMediaStore = mockk(relaxed = true) {
            every { pathFor(any()) } returns null
            every { pathFor(UiMediaSlot.GAMEBOOT_VIDEO) } returns customVideo
        }
        val context: Context = mockk(relaxed = true) {
            every { packageName } returns "com.test"
        }
        val player: UiMediaAudioPlayer = mockk(relaxed = true)

        val gate = GameBootGate(context, prefs, store, player, scope)
    }

    private fun TestScope.eventually(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for: $what")
            }
            Thread.sleep(20)
            testScheduler.runCurrent()
        }
    }

    @Test
    fun `switched off returns immediately and never raises a request or plays`() = runTest {
        val h = Harness(this, enabled = false)

        h.gate.awaitPresentation("Crash Bandicoot")

        assertNull(h.gate.active.value, "A disabled gate must never put a presentation on screen")
        assertFalse(h.gate.isActive)
        verify(exactly = 0) { h.player.play(any<String>(), any(), any()) }
    }

    @Test
    fun `switched on suspends until the overlay reports back and plays the built-in sound`() = runTest {
        val h = Harness(this, enabled = true)

        val awaiting = async { h.gate.awaitPresentation("Crash Bandicoot") }
        eventually("the presentation request is raised") { h.gate.active.value != null }

        val request = assertNotNull(h.gate.active.value, "The overlay should have been asked to present")

        assertTrue(
            request.audioPath?.startsWith("android.resource://com.test/") == true,
            "No custom clip must resolve the built-in audio, got: ${request.audioPath}",
        )

        verify(exactly = 0) { h.player.play(any<String>(), any(), any()) }

        advanceTimeBy(com.psplauncher.core.ui.components.DiscCeremony.DiscOutStartMs.toLong() + 1)
        runCurrent()
        verify(exactly = 1) { h.player.play(any<String>(), any(), any()) }
        assertTrue(awaiting.isActive, "The launch must still be waiting")

        h.gate.onPresentationFinished()
        advanceUntilIdle()

        assertTrue(awaiting.isCompleted, "The launch must be released by onPresentationFinished")
        assertNotNull(
            h.gate.active.value,
            "The overlay must stay up until it says it has left the screen",
        )

        h.gate.onPresentationDismissed()
        assertNull(h.gate.active.value, "The request must be cleared once the overlay is dismissed")
    }

    @Test
    fun `a custom clip replaces the whole presentation and keeps its own track`() = runTest {
        val h = Harness(this, enabled = true, customVideo = "/data/ui-media/gameboot_video.mp4")

        val awaiting = async { h.gate.awaitPresentation("Crash Bandicoot") }
        eventually("the presentation request is raised") { h.gate.active.value != null }

        val request = assertNotNull(h.gate.active.value)
        assertTrue(request.videoPath == "/data/ui-media/gameboot_video.mp4")

        assertNull(request.audioPath, "A custom clip must keep its own audio track")
        verify(exactly = 0) { h.player.play(any<String>(), any(), any()) }

        h.gate.onPresentationFinished()
        advanceUntilIdle()
        assertTrue(awaiting.isCompleted)
    }

    @Test
    fun `a stalled presentation times out and proceeds with the launch`() = runTest {
        val h = Harness(this, enabled = true)

        val awaiting = async { h.gate.awaitPresentation("Crash Bandicoot") }
        eventually("the presentation request is raised") { h.gate.active.value != null }
        assertTrue(awaiting.isActive)

        advanceTimeBy(GameBootGate.TIMEOUT_MS + 1)
        advanceUntilIdle()

        assertTrue(awaiting.isCompleted)
        awaiting.await()
        assertNull(h.gate.active.value)
    }

    @Test
    fun `a second request while presenting is dropped rather than queued`() = runTest {
        val h = Harness(this, enabled = true)

        val first = async { h.gate.awaitPresentation("Crash Bandicoot") }
        eventually("the presentation request is raised") { h.gate.active.value != null }
        val firstRequest = h.gate.active.value

        launch { h.gate.awaitPresentation("Spyro") }

        testScheduler.runCurrent()

        assertTrue(h.gate.active.value === firstRequest, "The on-screen presentation must not change")

        h.gate.onPresentationFinished()
        advanceUntilIdle()
        assertTrue(first.isCompleted)
    }
}
