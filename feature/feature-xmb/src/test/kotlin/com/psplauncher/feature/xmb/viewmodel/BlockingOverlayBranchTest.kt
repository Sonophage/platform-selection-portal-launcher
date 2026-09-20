package com.psplauncher.feature.xmb.viewmodel

import org.junit.Test
import kotlin.test.assertTrue

/**
 * The overlay list and the dispatcher's branches are a pair, and only one of them can be tested.
 *
 * `handleGamepadAction` ends with `if (state.hasBlockingOverlay) return` — a defensive net whose
 * own comment says it "guards against a future overlay being added without its own branch". It had
 * been catching one, silently, for as long as the Save-as-Theme dialog existed: that dialog is in
 * `hasBlockingOverlay` and had no branch, so every press after it opened was dropped and the
 * controller went dead until the user touched Cancel. The net now logs.
 *
 * **What this test cannot do, stated plainly:** it cannot assert that each blocking field has a
 * dispatcher branch, because `XMBViewModel` takes 42 constructor dependencies and no test in this
 * repository constructs one. That is task 7.1 in the remediation plan, and until it lands this
 * half of the pair is covered by the log line rather than by an assertion.
 *
 * What it does pin is the other half: a dialog that can be opened must be one the XMB agrees is
 * blocking. Drop `saveThemeNameDialog` out of `hasBlockingOverlay` and the crossbar navigates
 * underneath an open dialog instead.
 */
class BlockingOverlayBranchTest {

    /**
     * A crossbar with nothing open.
     *
     * NOT `XMBUiState()` — `showBootSequence` defaults to true, because the boot animation is up
     * before anything else is. The first draft of this test used the bare default and every
     * assertion passed for the wrong reason; the control case below is what caught it.
     */
    private fun idle() = XMBUiState(showBootSequence = false)

    @Test
    fun `an idle crossbar blocks nothing`() {
        // The control. Without it, the assertions below would pass against a property that
        // simply always answered true.
        assertTrue(idle().hasBlockingOverlay.not())
    }

    @Test
    fun `the save-as-theme dialog blocks the crossbar`() {
        val open = idle().copy(
            saveThemeNameDialog = PlaylistNameDialogState(title = "Save Current Look as Theme"),
        )
        assertTrue(open.hasBlockingOverlay, "the crossbar would navigate behind an open dialog")
    }

    @Test
    fun `the boot sequence blocks the crossbar, which is why the default state does`() {
        assertTrue(XMBUiState().hasBlockingOverlay)
        assertTrue(idle().copy(showBootSequence = true).hasBlockingOverlay)
    }
}
