package com.psplauncher.feature.xmb.ui

import androidx.compose.ui.unit.dp
import com.psplauncher.core.ui.components.HintBarHeight
import com.psplauncher.core.ui.components.StatusStripHeight
import org.junit.Assert.assertTrue
import org.junit.Test

class XmbLetterRailFitTest {
    private val SHORTEST_PANEL_HEIGHT = 462.dp

    private val WORST_CASE_RUNGS = 27

    @Test
    fun `the whole alphabet fits between the status strip and the hint bar`() {
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
        val rungs = RUNG_LINE_HEIGHT.value.dp * WORST_CASE_RUNGS * 1.3f
        val rail = rungs + RAIL_VERTICAL_PADDING * 2
        val available = SHORTEST_PANEL_HEIGHT - StatusStripHeight - HintBarHeight

        assertTrue(
            "at 1.3x font scale the rail is ${rail.value}dp against ${available.value}dp free",
            rail <= available,
        )
    }
}
