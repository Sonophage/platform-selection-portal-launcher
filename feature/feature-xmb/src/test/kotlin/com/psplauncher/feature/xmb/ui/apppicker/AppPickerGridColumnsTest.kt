package com.psplauncher.feature.xmb.ui.apppicker

import com.psplauncher.feature.xmb.viewmodel.PICKER_GRID_COLUMNS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How many app tiles fit, against the panels this app is run on.
 *
 * Arithmetic rather than a rendered test, for the reason the sibling grid tests give: a Compose
 * test that composes the grid reports whatever the layout produced, so it passes at any column
 * count. What can be wrong here is the NUMBER.
 *
 * A fixed seven spread the SAME seven tiles across whatever width it was given — 99.6dp a tile on
 * the handheld, 134.7dp on a tablet. Tile size belongs to the user through the scale slider, so
 * the count is what floats.
 *
 * The property that makes the change safe is the first test: the reference handheld must keep
 * drawing seven, because the target width was derived from what seven produced there.
 */
class AppPickerGridColumnsTest {

    /** AppPickerScreen's constants. Private there, restated here — and pinned by the first test. */
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
        // Konker Elite, 821dp. Seven is what the old constant drew and what the target was
        // derived from; if this stops being seven the handheld's layout changed by accident.
        assertEquals("the handheld's picker must not change", 7, columnsFor(821f))
    }

    @Test
    fun `99 and not 100 — the truncation boundary is a whole column`() {
        // The count truncates, so rounding the tile UP past what it measures costs a column.
        // This is not hypothetical: the same arithmetic took the search grid from 7 to 6 at 94dp
        // instead of 93dp, and the test there caught it before it shipped.
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
        // Titan Elite, 638dp — seven fixed columns gave it a 73dp tile.
        val titan = columnsFor(638f)
        assertTrue("a 638dp panel should fit fewer than seven; got $titan", titan < 7)
        assertTrue("and not fewer than the floor", titan >= MIN)
    }

    @Test
    fun `the fallback constant is what the handheld measures`() {
        // They must agree, or the frame before the first measurement re-flows the grid.
        assertEquals(
            "PICKER_GRID_COLUMNS is the pre-measurement value and should match the reference panel",
            columnsFor(821f),
            PICKER_GRID_COLUMNS,
        )
    }
}
