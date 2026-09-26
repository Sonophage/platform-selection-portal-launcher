package com.psplauncher.feature.xmb.ui.apppicker

import com.psplauncher.feature.xmb.viewmodel.PICKER_GRID_COLUMNS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPickerGridColumnsTest {
    private val TARGET = 99f
    private val GAP = 10f
    private val SIDE = 32f
    private val MIN = 3
    private val MAX = 12

    private fun columnsFor(widthDp: Float): Int {
        val grid = widthDp - SIDE * 2
        return ((grid + GAP) / (TARGET + GAP)).toInt().coerceIn(MIN, MAX)
    }

    @Test
    fun `the reference handheld still draws exactly seven`() {
        assertEquals("the handheld's picker must not change", 7, columnsFor(821f))
    }

    @Test
    fun `99 and not 100 — the truncation boundary is a whole column`() {
        fun at(target: Float) = ((821f - SIDE * 2 + GAP) / (target + GAP)).toInt()
        assertEquals("99dp keeps seven", 7, at(99f))
        assertEquals("100dp silently drops to six", 6, at(100f))
    }

    @Test
    fun `a wider panel gets MORE tiles at the same size, not bigger ones`() {
        val tablet = columnsFor(1067f)
        assertTrue("a 1067dp panel should fit more than seven; got $tablet", tablet > 7)
        val grid = 1067f - SIDE * 2
        val tile = (grid - GAP * (tablet - 1)) / tablet
        assertTrue(
            "the tile must stay near its target, not grow toward the old 134.7dp; got $tile",
            tile in (TARGET - 12f)..(TARGET + 12f),
        )
    }

    @Test
    fun `a narrow panel gets FEWER tiles rather than seven cramped ones`() {
        val titan = columnsFor(638f)
        assertTrue("a 638dp panel should fit fewer than seven; got $titan", titan < 7)
        assertTrue("and not fewer than the floor", titan >= MIN)
    }

    @Test
    fun `the fallback constant is what the handheld measures`() {
        assertEquals(
            "PICKER_GRID_COLUMNS is the pre-measurement value and should match the reference panel",
            columnsFor(821f),
            PICKER_GRID_COLUMNS,
        )
    }
}
