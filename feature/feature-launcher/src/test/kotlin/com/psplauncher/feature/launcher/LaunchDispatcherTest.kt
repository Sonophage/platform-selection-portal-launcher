package com.psplauncher.feature.launcher

import android.content.Context
import android.content.Intent
import com.psplauncher.core.domain.model.EmulatorProfile
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.IntentType
import com.psplauncher.core.domain.model.PlaySession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchDispatcherTest {
    private val game = Game(
        id = 7L,
        title = "Crash Bandicoot",
        platformId = "psx",
        romPath = "/roms/psx/crash.bin",
    )

    private val resolved = ResolvedLaunch(
        profile = EmulatorProfile(
            id = "duckstation",
            name = "DuckStation",
            packageName = "com.github.stenzek.duckstation",
            intentType = IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("ps1"),
        ),
        source = LaunchSource.PLATFORM_DEFAULT,
    )

    private class Harness(val scope: TestScope) {
        val context: Context = mockk(relaxed = true)
        val recorder: LaunchOutcomeRecorder = mockk(relaxed = true)
        val intent: Intent = mockk(relaxed = true)
        var now = 0L

        var wallNow = 1_790_000_000_000L

        val gameBootPreferences: com.psplauncher.core.data.repository.GameBootPreferences =
            mockk(relaxed = true) {
                every { gameBootEnabledFlow } returns kotlinx.coroutines.flow.flowOf(false)
            }
        val uiMediaStore: com.psplauncher.core.data.repository.UiMediaStore = mockk(relaxed = true)
        val gameBootAudioPlayer: com.psplauncher.core.ui.media.UiMediaAudioPlayer = mockk(relaxed = true)
        val gameBootGate = GameBootGate(context, gameBootPreferences, uiMediaStore, gameBootAudioPlayer, scope)
        val menuSound: com.psplauncher.core.ui.sound.MenuSoundPlayer = mockk(relaxed = true)
        val autoCoreMemory: AutoCoreMemory = mockk(relaxed = true)
        val gameRepository: com.psplauncher.core.domain.repository.GameRepository = mockk(relaxed = true)

        val dispatcher = LaunchDispatcher(
            context = context,
            outcomeRecorder = recorder,
            scope = scope,
            clock = LaunchClock { now },
            wallClock = LaunchClock { wallNow },
            gameBootGate = gameBootGate,
            menuSound = menuSound,
            autoCoreMemory = autoCoreMemory,
            gameRepository = gameRepository,
        )
    }

    private fun TestScope.harness() = Harness(this)

    private suspend fun Harness.launchAccepted() {
        coEvery { recorder.record(any()) } returns Unit
        val result = dispatcher.launch(game, resolved, intent)
        assertIs<LaunchDispatchResult.Accepted>(result)
    }

    private val retroarchResolved = ResolvedLaunch(
        profile = EmulatorProfile(
            id = "auto_retroarch_gambatte_libretro_android",
            name = "RetroArch · Gambatte (GB/GBC)",
            packageName = "com.retroarch",
            intentType = IntentType.COMPONENT,
            supportedPlatformIds = listOf("gb", "gbc"),
            autoSource = "retroarch-core",
        ),
        source = LaunchSource.CATALOG_DEFAULT,
    )

    @Test
    fun `accepted retroarch core launch remembers the console's core`() = runTest {
        val h = harness()
        coEvery { h.recorder.record(any()) } returns Unit

        h.dispatcher.launch(game, retroarchResolved, h.intent)

        coVerify(exactly = 1) {
            h.autoCoreMemory.remember("psx", "auto_retroarch_gambatte_libretro_android")
        }
    }

    @Test
    fun `a dispatched launch stamps the shelf before the emulator has even opened`() = runTest {
        val h = harness()
        coEvery { h.recorder.record(any()) } returns Unit
        h.now = 1_234L
        h.wallNow = 1_790_000_001_000L

        h.dispatcher.launch(game, resolved, h.intent)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.gameRepository.markOpened(7L, 1_790_000_001_000L) }
        coVerify(exactly = 0) { h.gameRepository.markOpened(7L, 1_234L) }
    }

    @Test
    fun `a launch the process never returns from is still on the shelf`() = runTest {
        val h = harness()
        coEvery { h.recorder.record(any()) } returns Unit
        h.now = 5_000L
        h.wallNow = 1_790_000_005_000L

        h.dispatcher.launch(game, resolved, h.intent)
        h.dispatcher.onHostStopped()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.gameRepository.markOpened(7L, 1_790_000_005_000L) }
        coVerify(exactly = 0) { h.gameRepository.recordPlaySession(any()) }
    }

    @Test
    fun `a launch that never reached startActivity does not stamp`() = runTest {
        val h = harness()
        coEvery { h.recorder.record(any()) } returns Unit
        every { h.context.startActivity(any(), any()) } throws
            android.content.ActivityNotFoundException("no such activity")

        h.dispatcher.launch(game, resolved, h.intent)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.gameRepository.markOpened(any(), any()) }
    }

    @Test
    fun `standalone launch does not write core memory`() = runTest {
        val h = harness()
        coEvery { h.recorder.record(any()) } returns Unit

        h.dispatcher.launch(game, resolved, h.intent)

        coVerify(exactly = 0) { h.autoCoreMemory.remember(any(), any()) }
    }

    @Test
    fun `intent failure records INTENT_FAILED and offers recovery`() = runTest {
        val h = harness()
        every { h.context.startActivity(any(), any()) } throws android.content.ActivityNotFoundException("nope")

        val result = h.dispatcher.launch(game, resolved, h.intent)

        assertIs<LaunchDispatchResult.Rejected>(result)
        assertEquals("Emulator not found. Is it installed?", (result as LaunchDispatchResult.Rejected).message)
        coVerify { h.recorder.record(match {
            it.status == LaunchOutcomeStatus.INTENT_FAILED &&
                it.emulatorId == "duckstation" &&
                it.failureReason == "Emulator not found. Is it installed?"
        }) }
        assertIs<LaunchRecoveryRequest>(h.dispatcher.recoveryRequests.value)
    }

    @Test
    fun `a failed dispatch plays the error sound and offers recovery`() = runTest {
        val h = harness()
        every { h.context.startActivity(any(), any()) } throws android.content.ActivityNotFoundException("nope")

        val result = h.dispatcher.launch(game, resolved, h.intent)

        assertIs<LaunchDispatchResult.Rejected>(result)
        verify { h.menuSound.play(com.psplauncher.core.ui.sound.MenuSound.ERROR) }
        assertIs<LaunchRecoveryRequest>(h.dispatcher.recoveryRequests.value)
    }

    @Test
    fun `an accepted dispatch does not play the error sound`() = runTest {
        val h = harness()
        h.launchAccepted()
        verify(exactly = 0) { h.menuSound.play(any(), any()) }
    }

    @Test
    fun `successful dispatch leaves recovery clear and arms verification`() = runTest {
        val h = harness()
        h.launchAccepted()
        assertNull(h.dispatcher.recoveryRequests.value)

        coVerify(exactly = 0) { h.recorder.record(any()) }
    }

    @Test
    fun `host never stops inside the window records never-foregrounded and offers recovery`() = runTest {
        val h = harness()
        h.launchAccepted()

        h.now = LaunchDispatcher.STOP_WINDOW_MS + 1
        advanceTimeBy(LaunchDispatcher.STOP_WINDOW_MS + 1)
        advanceUntilIdle()

        coVerify { h.recorder.record(match {
            it.status == LaunchOutcomeStatus.NEVER_FOREGROUNDED &&
                it.failureReason!!.contains("never came to the foreground")
        }) }
        assertIs<LaunchRecoveryRequest>(h.dispatcher.recoveryRequests.value)
    }

    @Test
    fun `returning instantly after the emulator covered the launcher records SUCCEEDED`() = runTest {
        val h = harness()
        h.launchAccepted()

        h.dispatcher.onHostStopped()
        h.now = 1_000
        h.dispatcher.onHostResumed()
        advanceUntilIdle()

        coVerify { h.recorder.record(match {
            it.status == LaunchOutcomeStatus.SUCCEEDED && it.failureReason == null
        }) }
        assertNull(h.dispatcher.recoveryRequests.value, "an instant deliberate close is a success")
    }

    @Test
    fun `returning before the emulator covered the launcher records never-foregrounded`() = runTest {
        val h = harness()
        h.launchAccepted()

        h.now = 1_000
        h.dispatcher.onHostResumed()
        advanceUntilIdle()

        coVerify { h.recorder.record(match {
            it.status == LaunchOutcomeStatus.NEVER_FOREGROUNDED &&
                it.failureReason!!.contains("never came to the foreground")
        }) }
        assertIs<LaunchRecoveryRequest>(h.dispatcher.recoveryRequests.value)
    }

    @Test
    fun `a real session records SUCCEEDED silently`() = runTest {
        val h = harness()
        h.launchAccepted()

        h.dispatcher.onHostStopped()
        h.now = 60_000
        h.dispatcher.onHostResumed()
        advanceUntilIdle()

        coVerify { h.recorder.record(match {
            it.status == LaunchOutcomeStatus.SUCCEEDED && it.failureReason == null
        }) }
        assertNull(h.dispatcher.recoveryRequests.value, "success must never pop recovery UI")
    }

    @Test
    fun `host stop inside the window cancels the watchdog and leaves the launch pending`() = runTest {
        val h = harness()
        h.launchAccepted()

        h.now = 1_000
        h.dispatcher.onHostStopped()
        advanceTimeBy(LaunchDispatcher.STOP_WINDOW_MS + 1_000)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.recorder.record(any()) }
        assertNull(h.dispatcher.recoveryRequests.value)

        h.now = 60_000
        h.dispatcher.onHostResumed()
        advanceUntilIdle()
        coVerify { h.recorder.record(match { it.status == LaunchOutcomeStatus.SUCCEEDED }) }
    }

    @Test
    fun `resume with no pending launch is ignored`() = runTest {
        val h = harness()
        h.dispatcher.onHostStopped()
        h.dispatcher.onHostResumed()
        advanceUntilIdle()
        coVerify(exactly = 0) { h.recorder.record(any()) }
        assertNull(h.dispatcher.recoveryRequests.value)
    }

    @Test
    fun `recovery history line counts recent failures for the game`() = runTest {
        val h = harness()
        coEvery { h.recorder.record(any()) } returns Unit
        coEvery { h.recorder.recentForGame(7L, 5) } returns listOf(
            outcome(LaunchOutcomeStatus.INTENT_FAILED),
            outcome(LaunchOutcomeStatus.SUCCEEDED),
            outcome(LaunchOutcomeStatus.NEVER_FOREGROUNDED),
        )
        every { h.context.startActivity(any(), any()) } throws android.content.ActivityNotFoundException("x")

        h.dispatcher.launch(game, resolved, h.intent)

        val request = h.dispatcher.recoveryRequests.value
        assertIs<LaunchRecoveryRequest>(request)
        assertTrue(
            request.historyLine!!.contains("2 of the last 3"),
            "expected failure summary, got: ${request.historyLine}",
        )
        assertTrue(request.diagnostic.contains("DuckStation"), "diagnostic must name the emulator")
        assertTrue(request.diagnostic.contains("crash.bin"), "diagnostic must name the ROM")
    }

    @Test
    fun `preflight failure records and offers recovery without an intent`() = runTest {
        val h = harness()
        h.dispatcher.recordPreflightFailure(game, resolved, "ROM file not found")
        advanceUntilIdle()

        coVerify { h.recorder.record(match {
            it.status == LaunchOutcomeStatus.INTENT_FAILED && it.failureReason == "ROM file not found"
        }) }
        assertIs<LaunchRecoveryRequest>(h.dispatcher.recoveryRequests.value)
    }

    @Test
    fun `dismiss clears the recovery request`() = runTest {
        val h = harness()
        every { h.context.startActivity(any(), any()) } throws android.content.ActivityNotFoundException("x")
        coEvery { h.recorder.record(any()) } returns Unit

        h.dispatcher.launch(game, resolved, h.intent)
        assertIs<LaunchRecoveryRequest>(h.dispatcher.recoveryRequests.value)

        h.dispatcher.dismissRecovery()
        assertNull(h.dispatcher.recoveryRequests.value)
    }

    private fun outcome(status: LaunchOutcomeStatus) = LaunchOutcome(
        gameId = 7L,
        gameTitle = "Crash Bandicoot",
        platformId = "psx",
        emulatorId = "duckstation",
        emulatorName = "DuckStation",
        corePath = null,
        coreName = null,
        source = LaunchSource.PLATFORM_DEFAULT,
        status = status,
        failureReason = null,
        launchedAtMs = 1L,
    )

    @Test
    fun `a verified session records the real duration and stamps last played`() = runTest {
        val h = Harness(this)
        coEvery { h.recorder.record(any()) } returns Unit
        assertIs<LaunchDispatchResult.Accepted>(h.dispatcher.launch(game, null, h.intent))

        h.dispatcher.onHostStopped()
        h.now = 1_800_000L
        h.dispatcher.onHostResumed()
        advanceUntilIdle()

        val session = slot<PlaySession>()
        coVerify(exactly = 1) { h.gameRepository.recordPlaySession(capture(session)) }
        assertEquals(7L, session.captured.gameId)
        assertEquals("psx", session.captured.platformId)

        assertEquals(1_790_000_000_000L, session.captured.launchedAt)
        assertEquals(1_800_000L, session.captured.durationMillis)
    }

    @Test
    fun `a launch the emulator never foregrounded is not play time`() = runTest {
        val h = Harness(this)
        coEvery { h.recorder.record(any()) } returns Unit
        assertIs<LaunchDispatchResult.Accepted>(h.dispatcher.launch(game, null, h.intent))

        h.now = 900L
        h.dispatcher.onHostResumed()
        advanceUntilIdle()

        coVerify(exactly = 0) { h.gameRepository.recordPlaySession(any()) }
    }

    @Test
    fun `a failed startActivity is not play time`() = runTest {
        val h = Harness(this)
        every { h.context.startActivity(any(), any()) } throws android.content.ActivityNotFoundException()
        coEvery { h.recorder.record(any()) } returns Unit

        h.dispatcher.launch(game, null, h.intent)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.gameRepository.recordPlaySession(any()) }
    }

    @Test
    fun `a repository failure never takes the launcher down with it`() = runTest {
        val h = Harness(this)
        coEvery { h.recorder.record(any()) } returns Unit
        coEvery { h.gameRepository.recordPlaySession(any()) } throws IllegalStateException("db closed")
        assertIs<LaunchDispatchResult.Accepted>(h.dispatcher.launch(game, null, h.intent))

        h.dispatcher.onHostStopped()
        h.now = 60_000L
        h.dispatcher.onHostResumed()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.recorder.record(any()) }
    }
}
