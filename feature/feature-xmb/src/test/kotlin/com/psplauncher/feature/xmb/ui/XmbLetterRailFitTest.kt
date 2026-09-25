package com.psplauncher.feature.xmb.ui

import androidx.compose.ui.unit.dp
import com.psplauncher.core.ui.components.HintBarHeight
import com.psplauncher.core.ui.components.StatusStripHeight
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rail has to fit between the two chrome bands, at its worst case, on the smallest panel.
 *
 * This is arithmetic rather than a rendered-layout test on purpose. A Compose test that composes
 * the rail and measures it would answer a question nobody is asking: Column lays out whatever it
 * is given and reports the bounds it produced, so it passes whether or not the result fits on
 * screen — the same shape as the grid tests in this repo that could not fail. What can actually
 * be wrong here is the SUM, so the sum is what is asserted.
 *
 * It is also the guard on a pair. [StatusStripHeight] and [HintBarHeight] are core-ui's, the rung
 * height is this file's, and the three of them have to agree for the rail to be reachable. Before
 * this test they did not: the rungs inherited a 24sp line height from the theme, eighteen of them
 * filled a 462dp panel, and the '#' rung was drawn inside the status strip next to the clock. No
 * test noticed, because nothing was adding the numbers up.
 */
class XmbLetterRailFitTest {

    /**
     * The shortest panel this app is actually run on: the Konker Elite handheld, 1080x1920 at
     * density 374, held in landscape, which is 462dp of height. The crossbar's own scale formula
     * (XMBShell) uses a 468dp baseline and clamps at 1.0, so this is the floor in practice.
     */
    private val SHORTEST_PANEL_HEIGHT = 462.dp

    /** '#' plus A to Z. A library can produce every one of them. */
    private val WORST_CASE_RUNGS = 27

    @Test
    fun `the whole alphabet fits between the status strip and the hint bar`() {
        // 1sp == 1dp at fontScale 1.0. A user at a larger font scale gets taller rungs, which is
        // why the headroom below is checked rather than a bare "less than or equal".
        val rungs = RUNG_LINE_HEIGHT.value.dp * WORST_CASE_RUNGS
        val rail = rungs + RAIL_VERTICAL_PADDING * 2
        val available = SHORTEST_PANEL_HEIGHT - StatusStripHeight - HintBarHeight

        assertTrue(
            "a full A-Z rail is ${rail.value}dp and only ${available.value}dp is free between the " +
                "chrome bands — either the rung height or a band height moved and nothing added " +
                "them up",
            rail <= available,
        )
    }

    @Test
    fun `there is headroom for a larger font scale`() {
        // A rung is set in sp, so accessibility text scaling makes the rail taller while the two
        // bands stay fixed in dp. At 1.3x the rail must still fit, or the first thing a user with
        // larger text loses is the control that exists to make a long list reachable.
        val rungs = RUNG_LINE_HEIGHT.value.dp * WORST_CASE_RUNGS * 1.3f
        val rail = rungs + RAIL_VERTICAL_PADDING * 2
        val available = SHORTEST_PANEL_HEIGHT - StatusStripHeight - HintBarHeight

        assertTrue(
            "at 1.3x font scale the rail is ${rail.value}dp against ${available.value}dp free",
            rail <= available,
        )
    }
}
