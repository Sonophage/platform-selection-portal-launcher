package com.psplauncher.core.data.launch

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [MediaLaunchGate]: the switch, and the two-signal split that keeps the disc on screen
 * through its own fade.
 *
 * runCurrent, never advanceUntilIdle: the gate's watchdog is a withTimeout on the same virtual
 * clock, so advancing until idle sails past [MediaLaunchGate.TIMEOUT_MS] and tears the request
 * down before the assertion reads it — a test that would have reported the disc never appearing
 * when the only thing wrong was the clock.
 */
class MediaLaunchGateTest {

    private fun gate(enabled: Boolean) = MediaLaunchGate(
        mockk<LaunchDiscPreferences> { every { launchDiscEnabledFlow } returns flowOf(enabled) },
    )

    @Test
    fun `switched off raises nothing and does not suspend`() = runTest {
        val gate = gate(enabled = false)

        // No one is going to call onHandOff. Reaching the next line at all is the assertion: if the
        // switch were checked after the request were raised, this would hang until the watchdog and
        // every launch in the app would sit behind a disc nobody asked for.
        gate.awaitHandOff("art")

        assertNull("a disabled disc must never go on screen", gate.active.value)
    }

    @Test
    fun `switched on holds the launch until the hand-off, and the disc until the dismiss`() = runTest {
        val gate = gate(enabled = true)

        val awaiting = async { gate.awaitHandOff("art") }
        runCurrent()
        assertNotNull("the disc should be up", gate.active.value)
        assertTrue("the launch must still be waiting", awaiting.isActive)

        gate.onHandOff()
        runCurrent()
        assertTrue("the hand-off releases the launch", awaiting.isCompleted)
        // The half that a single signal would get wrong: the disc has another beat of fading to do
        // and the app is only just starting behind it.
        assertNotNull("the disc must stay up through its fade", gate.active.value)

        gate.onDismissed()
        assertNull("the dismiss takes it down", gate.active.value)
    }

    @Test
    fun `a second request while one is presenting is dropped, not queued`() = runTest {
        val gate = gate(enabled = true)
        val first = async { gate.awaitHandOff("one") }
        runCurrent()

        // A double-press must not stack two ceremonies, and must not deadlock the second caller
        // behind a hand-off that belongs to the first.
        gate.awaitHandOff("two")

        assertNotNull(gate.active.value)
        assertTrue("the first is still the one holding the screen", first.isActive)
        gate.onHandOff()
        runCurrent()
        gate.onDismissed()
    }
}
