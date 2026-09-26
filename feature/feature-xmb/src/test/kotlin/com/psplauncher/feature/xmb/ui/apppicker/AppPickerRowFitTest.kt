package com.psplauncher.feature.xmb.ui.apppicker

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPickerRowFitTest {
    private val tileFixedHeight = FRAME_ROOM + 6.dp + 30.dp + 8.dp
    private val rowGap = 14.dp
    private val verticalPadding = 28.dp

    private fun neededFor(v: Dp): Dp =
        (pickerAdaptiveArtworkSize(v, rows = 3) + tileFixedHeight) * 3 + rowGap * 2 + verticalPadding

    private fun threeRowsFit(v: Dp) = neededFor(v) <= v

    @Test
    fun `the guarantee holds from 356dp of grid viewport upward`() {
        assertTrue("356dp is the boundary and must fit", threeRowsFit(THREE_ROW_MIN_VIEWPORT))
        for (h in listOf(360, 400, 428, 450, 640, 1200)) {
            assertTrue("three rows must fit a ${h}dp viewport", threeRowsFit(h.dp))
        }
    }

    @Test
    fun `below the boundary the artwork floor wins and the third row overflows`() {
        for (h in listOf(280, 300, 320, 340)) {
            assertTrue(
                "a ${h}dp viewport cannot honour three rows — needed ${neededFor(h.dp)}",
                !threeRowsFit(h.dp),
            )
        }
    }

    @Test
    fun `the boundary is where the artwork stops shrinking, not an arbitrary number`() {
        assertEquals("pinned at the floor below the boundary", MIN_ARTWORK_SIZE, pickerAdaptiveArtworkSize(300.dp, rows = 3))
        assertEquals("and the need is flat there", neededFor(300.dp), neededFor(340.dp))
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
        val THREE_ROW_MIN_VIEWPORT = 356.dp
    }
}
