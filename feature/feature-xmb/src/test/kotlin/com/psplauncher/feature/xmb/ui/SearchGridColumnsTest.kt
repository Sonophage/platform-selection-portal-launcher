package com.psplauncher.feature.xmb.ui

import com.psplauncher.feature.xmb.viewmodel.SEARCH_GRID_COLUMNS
import com.psplauncher.feature.xmb.viewmodel.SEARCH_GRID_MAX_COLUMNS
import com.psplauncher.feature.xmb.viewmodel.SEARCH_GRID_MIN_COLUMNS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How many result cards fit, against the panels this app is actually run on.
 *
 * Arithmetic rather than a rendered test, for the same reason as [XmbCanvasScaleTest] and
 * [XmbLetterRailFitTest]: a Compose test that composes the grid reports whatever the layout
 * produced, so it passes at any column count. What can be wrong here is the NUMBER.
 *
 * The search card is a fixed 2:3 with an unspecified width, so the column count is also the
 * card's size. A fixed seven therefore drew bigger cards on a bigger screen rather than more of
 * them — the reading of "responsive" this app does not want, because the card's size belongs to
 * the user through the scale slider.
 *
 * The property that makes the change safe is the first test: **the reference handheld must keep
 * drawing seven**, because the target width was derived from what seven produced there.
 */
class SearchGridColumnsTest {

    /** SearchScreen's constants. Private there, restated here — and pinned by the first test. */
    private val TARGET = 93f
    private val GAP = 14f

    /** The screen's own gutters, one on each side. */
    private val GUTTER = 40f

    /** SearchScreen's arithmetic, on a panel [widthDp] wide. */
    private fun columnsFor(widthDp: Float): Int {
        val available = widthDp - GUTTER * 2
        return ((available + GAP) / (TARGET + GAP)).toInt()
            .coerceIn(SEARCH_GRID_MIN_COLUMNS, SEARCH_GRID_MAX_COLUMNS)
    }

    @Test
    fun `the reference handheld still draws exactly seven`() {
        // Konker Elite, 821dp of landscape width. Seven is what the old constant drew, the target
        // card width was derived from it, and if this ever stops being seven the tuning that the
        // SEARCH_GRID_COLUMNS comment describes — cards short enough for a two-line title and a
        // subtitle — has been silently undone on the device this app is built for.
        assertEquals("the handheld's grid must not change", 7, columnsFor(821f))
    }

    @Test
    fun `a wider panel gets MORE cards, not bigger ones`() {
        // The whole point. NP05J tablet, 1067dp.
        val tablet = columnsFor(1067f)
        assertTrue("a 1067dp panel should fit more than the handheld's seven; got $tablet", tablet > 7)
        // And the card stays the size it was: every column is one target width plus its gap, so
        // "more columns" and "same card" are the same statement.
        val available = 1067f - GUTTER * 2
        val cardWidth = (available - GAP * (tablet - 1)) / tablet
        assertTrue(
            "the card must stay near its target width, not grow; got $cardWidth against $TARGET",
            cardWidth in (TARGET - 12f)..(TARGET + 12f),
        )
    }

    @Test
    fun `a narrow panel gets FEWER cards rather than seven cramped ones`() {
        // Unihertz Titan Elite, 638dp. Seven fixed columns here squeezed the card below anything
        // a 2:3 cover reads at; the count is allowed to fall instead.
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
        // The two must agree, or the frame before the first measurement lands re-flows the grid.
        assertEquals(
            "SEARCH_GRID_COLUMNS is the pre-measurement value and should be what the reference panel measures",
            columnsFor(821f),
            SEARCH_GRID_COLUMNS,
        )
    }
}
