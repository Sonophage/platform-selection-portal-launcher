package com.psplauncher.feature.xmb.ui.apppicker

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three-row guarantee, as arithmetic — and where it stops being true.
 *
 * **This exists because the rendered test beside it cannot check this.**
 * `AppPickerThreeRowsTest` composes the real screen and reads `boundsInRoot`, and those bounds are
 * CLIPPED to the viewport: a tile hanging below the grid reports a bottom equal to the grid's, so
 * "nothing extends past the bottom" stays true however tall the tiles get.
 *
 * Demonstrated by forcing a REAL overflow — [MIN_ARTWORK_SIZE] to 120dp and [MAX_ARTWORK_SIZE] to
 * 200dp, which at that test's own viewport floors the artwork at 120dp and needs 572dp for three
 * rows against the 450 it has, a 122dp overflow. The rendered test still passed.
 *
 * (Raising only the CAP does not demonstrate it and was the first thing tried: when the artwork is
 * unclamped the sizing solves to exactly the viewport, so bigger tiles still fit three rows. That
 * is correct behaviour, and the rendered test passing it was correct too.)
 *
 * Same family as the grid tests in this repo that could not fail, and the same answer: what can be
 * wrong is the SUM.
 *
 * What the sum says is that the guarantee is CONDITIONAL, which nothing recorded before. Above a
 * viewport of [THREE_ROW_MIN_VIEWPORT] the sizing solves exactly and three rows fit. Below it the
 * [MIN_ARTWORK_SIZE] floor wins, the tile stops shrinking, and the third row overflows — by 76dp
 * at a 280dp viewport. `pickerAdaptiveArtworkSize` cannot honour three rows on a short panel, and
 * says nothing about it.
 */
class AppPickerRowFitTest {

    /** [pickerAdaptiveArtworkSize]'s own fixed overhead: frame room, spacer, 2-line label, padding. */
    private val tileFixedHeight = FRAME_ROOM + 6.dp + 30.dp + 8.dp
    private val rowGap = 14.dp
    private val verticalPadding = 28.dp

    /** What three rows actually need in a viewport of [v]. */
    private fun neededFor(v: Dp): Dp =
        (pickerAdaptiveArtworkSize(v, rows = 3) + tileFixedHeight) * 3 + rowGap * 2 + verticalPadding

    private fun threeRowsFit(v: Dp) = neededFor(v) <= v

    @Test
    fun `the guarantee holds from the boundary upward`() {
        assertTrue("the boundary itself must fit", threeRowsFit(THREE_ROW_MIN_VIEWPORT))
        for (over in listOf(1, 20, 80, 120, 300, 900)) {
            val h = THREE_ROW_MIN_VIEWPORT + over.dp
            assertTrue("three rows must fit $h", threeRowsFit(h))
        }
    }

    @Test
    fun `the reference handheld is above the boundary, which it was not`() {
        // The Konker Elite's picker grid measures 338dp once the strip, header and footer have
        // taken theirs — AppPickerThreeRowsTest composes the real screen at 821x462dp to get it.
        // At the old 48dp artwork floor the boundary was 356dp, so the third row was clipped by
        // 18dp on the device this app is built for.
        assertTrue(
            "the handheld's 338dp must clear the boundary ($THREE_ROW_MIN_VIEWPORT)",
            threeRowsFit(338.dp),
        )
    }

    @Test
    fun `below the boundary the artwork floor wins and the third row overflows`() {
        // Sampled RELATIVE to the boundary, because the boundary moves with the floor — and it
        // just did, from 356dp to a lower one when MIN_ARTWORK_SIZE went 48 -> 40 to stop the
        // handheld clipping. Hard-coded viewports here would have had to be edited to match, and
        // the edit that gets forgotten is the one that makes the test agree with the bug.
        for (under in listOf(1, 10, 40, 90)) {
            val h = THREE_ROW_MIN_VIEWPORT - under.dp
            assertTrue("$h cannot honour three rows — needed ${neededFor(h)}", !threeRowsFit(h))
        }
    }

    @Test
    fun `the boundary is where the artwork stops shrinking, not an arbitrary number`() {
        // Below it the artwork is pinned at its floor and the need stops falling; at it, the sum
        // is exactly the viewport. That equality is what makes the boundary derivable at all.
        val below = THREE_ROW_MIN_VIEWPORT - 20.dp
        val lower = THREE_ROW_MIN_VIEWPORT - 50.dp
        assertEquals("pinned at the floor below the boundary", MIN_ARTWORK_SIZE, pickerAdaptiveArtworkSize(below, rows = 3))
        assertEquals("and the need is flat there", neededFor(below), neededFor(lower))
        assertEquals("at the boundary the sum is the viewport", THREE_ROW_MIN_VIEWPORT, neededFor(THREE_ROW_MIN_VIEWPORT))
    }

    @Test
    fun `the artwork never escapes its clamps`() {
        for (h in listOf(200, 300, 450, 700, 1600)) {
            val art = pickerAdaptiveArtworkSize(h.dp, rows = 3)
            assertTrue("artwork $art below cap at ${h}dp", art <= MAX_ARTWORK_SIZE)
            assertTrue("artwork $art above floor at ${h}dp", art >= MIN_ARTWORK_SIZE)
        }
    }

    private companion object {
        /**
         * The shortest grid viewport in which three rows of tiles still fit.
         *
         * COMPUTED from the constants, not written down: the point at which the artwork floor
         * stops the tile shrinking. A hard-coded number here would have to be edited every time
         * the floor or the tile overhead moves, and the edit that gets forgotten is the one that
         * makes the test agree with the bug.
         */
        val THREE_ROW_MIN_VIEWPORT =
            (MIN_ARTWORK_SIZE + (FRAME_ROOM + 6.dp + 30.dp + 8.dp)) * 3 + 14.dp * 2 + 28.dp
    }
}
