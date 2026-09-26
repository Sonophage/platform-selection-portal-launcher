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

class MediaLaunchGateTest {
    private fun gate(enabled: Boolean) = MediaLaunchGate(
        mockk<LaunchDiscPreferences> { every { launchDiscEnabledFlow } returns flowOf(enabled) },
    )

    @Test
    fun `switched off raises nothing and does not suspend`() = runTest {
        val gate = gate(enabled = false)

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

        assertNotNull("the disc must stay up through its fade", gate.active.value)

        gate.onDismissed()
        assertNull("the dismiss takes it down", gate.active.value)
    }

    @Test
    fun `a second request while one is presenting is dropped, not queued`() = runTest {
        val gate = gate(enabled = true)
        val first = async { gate.awaitHandOff("one") }
        runCurrent()

        gate.awaitHandOff("two")

        assertNotNull(gate.active.value)
        assertTrue("the first is still the one holding the screen", first.isActive)
        gate.onHandOff()
        runCurrent()
        gate.onDismissed()
    }
}
