package com.psplauncher.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationEngineReadinessTest {
    private fun node(key: String) = NavigationNode(key = key, onSelect = { })

    @Test
    fun `input ignored and not buffered before ready`() {
        val engine = NavigationEngine()
        engine.replaceNodes(listOf(node("a"), node("b")))
        assertEquals("a", engine.focusedKey)

        assertEquals(null, engine.dispatch(NavigationCommand.Direction(NavigationDirection.DOWN)))
        assertEquals("input must not move focus before ready", "a", engine.focusedKey)

        assertEquals(null, engine.dispatch(NavigationCommand.Confirm))
    }

    @Test
    fun `initial focus appears after ready`() {
        val engine = NavigationEngine()
        engine.replaceNodes(listOf(node("a"), node("b")))
        engine.markReady()
        assertEquals("a", engine.focusedKey)
        assertEquals("b", engine.dispatch(NavigationCommand.Direction(NavigationDirection.DOWN)))
    }

    @Test
    fun `missing readiness signal recovers and logs warning`() {
        val warnings = mutableListOf<String>()
        val engine = NavigationEngine(logger = NavigationLogger { warnings.add(it) })
        engine.replaceNodes(listOf(node("a"), node("b")))

        engine.recoverReadiness()
        assertEquals(1, warnings.size)
        assertTrue(warnings[0].contains("readiness", ignoreCase = true))

        assertEquals("b", engine.dispatch(NavigationCommand.Direction(NavigationDirection.DOWN)))
    }

    @Test
    fun `recovery lock drops inputs instead of queueing`() {
        val engine = NavigationEngine()
        engine.replaceNodes(listOf(node("a"), node("b"), node("c")))
        engine.markReady()

        engine.beginRecoveryLock()
        assertNull(engine.dispatch(NavigationCommand.Direction(NavigationDirection.DOWN)))
        assertNull(engine.dispatch(NavigationCommand.Direction(NavigationDirection.DOWN)))
        engine.endRecoveryLock()

        assertEquals("a", engine.focusedKey)
        assertEquals("b", engine.dispatch(NavigationCommand.Direction(NavigationDirection.DOWN)))
    }
}
