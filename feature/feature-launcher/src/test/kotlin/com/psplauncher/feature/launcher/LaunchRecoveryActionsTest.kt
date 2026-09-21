package com.psplauncher.feature.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the recovery sheet offers, and in what order.
 *
 * The order is the decision. The sheet used to hard-code Retry first and draw it focused whatever
 * had gone wrong, so on a revoked storage grant the lead action was the one the message directly
 * above it had just finished explaining would fail again — and since A meant RETRY outright, three
 * of the five buttons could not be reached by a controller at all, on a controller-first device,
 * at the one moment a controller has to work.
 *
 * So the list is data and the cursor walks it, and these are the properties that must hold however
 * the list is later edited.
 */
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
        // THE REPORTED FAILURE. The message says "reconnect the storage and rescan its library";
        // the first thing under it must be the way to do that, not the way to fail again.
        val actions = launchRecoveryActions(request(LaunchFailureKind.STORAGE_ACCESS_LOST))
        assertEquals(LaunchRecoveryAction.OPEN_LIBRARY, actions.first().first)
    }

    @Test
    fun `an unclassified failure still leads with Retry`() {
        // Retry is a reasonable lead when nothing is known — it just must not be the lead when
        // something IS known and contradicts it.
        val actions = launchRecoveryActions(request(LaunchFailureKind.UNKNOWN))
        assertEquals(LaunchRecoveryAction.RETRY, actions.first().first)
    }

    @Test
    fun `reconnect storage is offered only for the failure it fixes`() {
        // An always-present button that usually does nothing is how a repair surface turns into a
        // menu of guesses.
        val actions = launchRecoveryActions(request(LaunchFailureKind.UNKNOWN))
        assertTrue(actions.none { it.first == LaunchRecoveryAction.OPEN_LIBRARY })
    }

    @Test
    fun `dismiss is always offered and always last`() {
        // Last so the cursor never opens on the way out, and always present so a sheet whose other
        // remedies are all inapplicable still has an exit that is not the Back button alone.
        listOf(LaunchFailureKind.UNKNOWN, LaunchFailureKind.STORAGE_ACCESS_LOST).forEach { kind ->
            val actions = launchRecoveryActions(request(kind))
            assertEquals(LaunchRecoveryAction.DISMISS, actions.last().first, "dismiss not last for $kind")
            assertTrue(actions.count { it.first == LaunchRecoveryAction.DISMISS } == 1)
        }
    }

    @Test
    fun `the per-system default is offered only when an emulator was actually resolved`() {
        // With no resolved launch there is no per-system anything to change, and the screen it
        // opens would have nothing to act on.
        assertTrue(
            launchRecoveryActions(request(resolved = null))
                .none { it.first == LaunchRecoveryAction.PER_SYSTEM_DEFAULTS },
        )
    }

    @Test
    fun `no action is offered twice and every one carries a label`() {
        // The cursor indexes this list, so a duplicate would be two buttons that cannot be told
        // apart, and a blank label would be a button that cannot be read.
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
        // The cursor wraps, so any non-empty list is navigable — but a one-button sheet would mean
        // the only answer is the one the app chose, which is what the old fixed order amounted to.
        listOf(LaunchFailureKind.UNKNOWN, LaunchFailureKind.STORAGE_ACCESS_LOST).forEach { kind ->
            assertTrue(launchRecoveryActions(request(kind)).size >= 2, "too few actions for $kind")
        }
    }
}
