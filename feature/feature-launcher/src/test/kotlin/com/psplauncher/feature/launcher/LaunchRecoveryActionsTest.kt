package com.psplauncher.feature.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LaunchRecoveryActionsTest {
    private fun request(
        kind: LaunchFailureKind = LaunchFailureKind.UNKNOWN,
        resolved: ResolvedLaunch? = null,
    ) = LaunchRecoveryRequest(
        gameId = 1L,
        gameTitle = "Final Fantasy",
        platformId = "psp",
        resolved = resolved,
        message = "…",
        historyLine = null,
        diagnostic = "…",
        kind = kind,
    )

    @Test
    fun `a lost storage grant leads with reconnecting the storage`() {
        val actions = launchRecoveryActions(request(LaunchFailureKind.STORAGE_ACCESS_LOST))
        assertEquals(LaunchRecoveryAction.OPEN_LIBRARY, actions.first().first)
    }

    @Test
    fun `an unclassified failure still leads with Retry`() {
        val actions = launchRecoveryActions(request(LaunchFailureKind.UNKNOWN))
        assertEquals(LaunchRecoveryAction.RETRY, actions.first().first)
    }

    @Test
    fun `reconnect storage is offered only for the failure it fixes`() {
        val actions = launchRecoveryActions(request(LaunchFailureKind.UNKNOWN))
        assertTrue(actions.none { it.first == LaunchRecoveryAction.OPEN_LIBRARY })
    }

    @Test
    fun `dismiss is always offered and always last`() {
        listOf(LaunchFailureKind.UNKNOWN, LaunchFailureKind.STORAGE_ACCESS_LOST).forEach { kind ->
            val actions = launchRecoveryActions(request(kind))
            assertEquals(LaunchRecoveryAction.DISMISS, actions.last().first, "dismiss not last for $kind")
            assertTrue(actions.count { it.first == LaunchRecoveryAction.DISMISS } == 1)
        }
    }

    @Test
    fun `the per-system default is offered only when an emulator was actually resolved`() {
        assertTrue(
            launchRecoveryActions(request(resolved = null))
                .none { it.first == LaunchRecoveryAction.PER_SYSTEM_DEFAULTS },
        )
    }

    @Test
    fun `no action is offered twice and every one carries a label`() {
        listOf(LaunchFailureKind.UNKNOWN, LaunchFailureKind.STORAGE_ACCESS_LOST).forEach { kind ->
            val actions = launchRecoveryActions(request(kind))
            assertEquals(actions.size, actions.map { it.first }.toSet().size, "duplicate action for $kind")
            actions.forEach { (action, label) ->
                assertTrue(label.isNotBlank(), "blank label for $action")
            }
        }
    }

    @Test
    fun `every sheet has at least two ways out, so a pad is never stuck`() {
        listOf(LaunchFailureKind.UNKNOWN, LaunchFailureKind.STORAGE_ACCESS_LOST).forEach { kind ->
            assertTrue(launchRecoveryActions(request(kind)).size >= 2, "too few actions for $kind")
        }
    }
}
