package com.psplauncher.feature.xmb.ui

import com.psplauncher.feature.xmb.viewmodel.SEARCH_GRID_COLUMNS
import com.psplauncher.feature.xmb.viewmodel.SEARCH_GRID_MAX_COLUMNS
import com.psplauncher.feature.xmb.viewmodel.SEARCH_GRID_MIN_COLUMNS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchGridColumnsTest {
    private val TARGET = 93f
    private val GAP = 14f

    private val GUTTER = 40f

    private fun columnsFor(widthDp: Float): Int {
        val available = widthDp - GUTTER * 2
        return ((available + GAP) / (TARGET + GAP)).toInt()
            .coerceIn(SEARCH_GRID_MIN_COLUMNS, SEARCH_GRID_MAX_COLUMNS)
    }

    @Test
    fun `the reference handheld still draws exactly seven`() {
        assertEquals("the handheld's grid must not change", 7, columnsFor(821f))
    }

    @Test
    fun `a wider panel gets MORE cards, not bigger ones`() {
        val tablet = columnsFor(1067f)
        assertTrue("a 1067dp panel should fit more than the handheld's seven; got $tablet", tablet > 7)

        val available = 1067f - GUTTER * 2
        val cardWidth = (available - GAP * (tablet - 1)) / tablet
        assertTrue(
            "the card must stay near its target width, not grow; got $cardWidth against $TARGET",
            cardWidth in (TARGET - 12f)..(TARGET + 12f),
        )
    }

    @Test
    fun `a narrow panel gets FEWER cards rather than seven cramped ones`() {
        val titan = columnsFor(638f)
        assertTrue("a 638dp panel should fit fewer than seven; got $titan", titan < 7)
        assertTrue("and not fewer than the floor", titan >= SEARCH_GRID_MIN_COLUMNS)
    }

    @Test
    fun `the clamps hold at both ends`() {
        assertEquals("an absurdly narrow panel stops at the floor", SEARCH_GRID_MIN_COLUMNS, columnsFor(50f))
        assertEquals("an absurdly wide one stops at the ceiling", SEARCH_GRID_MAX_COLUMNS, columnsFor(9000f))
    }

    @Test
    fun `the fallback constant is what the handheld measures`() {
        assertEquals(
            "SEARCH_GRID_COLUMNS is the pre-measurement value and should be what the reference panel measures",
            columnsFor(821f),
            SEARCH_GRID_COLUMNS,
        )
    }
}
