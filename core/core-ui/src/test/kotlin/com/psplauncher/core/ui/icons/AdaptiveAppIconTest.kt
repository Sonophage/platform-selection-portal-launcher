package com.psplauncher.core.ui.icons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The safe-zone arithmetic behind drawing an app icon without its background tile.
 *
 * Getting this wrong is invisible in code review and obvious on screen, in one of two directions.
 * Too small an inset and every app icon in the crossbar renders at 2/3 the size of the silhouettes
 * beside it, floating in transparent margin. Too large and the art is cropped into its bleed, which
 * clips the corners off round logos.
 *
 * The number is not free-floating: an adaptive icon is authored on a 108-unit canvas whose central
 * 72 units are the only part guaranteed to survive the system mask, so the scale is exactly
 * 108/72 = 1.5 and the inset is the quarter that hangs off each edge.
 */
class AdaptiveAppIconTest {

    @Test
    fun `the foreground is drawn at one and a half times the target square`() {
        // 192 + 2*48 = 288 = 192 * 1.5. If this drifts, every app icon changes size at once.
        val size = 192
        val inset = adaptiveForegroundInset(size)
        assertEquals(48, inset)
        assertEquals(size * 3 / 2, size + 2 * inset)
    }

    @Test
    fun `the ratio holds at every size the app asks for`() {
        // Row icons, grid tiles and the detail page each pick their own pixel size. The scale is a
        // property of the icon format, not of the slot, so it cannot depend on which one called.
        listOf(48, 96, 128, 144, 192, 256, 512).forEach { size ->
            val drawn = size + 2 * adaptiveForegroundInset(size)
            val ratio = drawn.toDouble() / size
            assertTrue(
                ratio > 1.49 && ratio < 1.52,
                "scale was $ratio at size $size",
            )
        }
    }

    @Test
    fun `the inset is never negative, so the art is never scaled down`() {
        // A negative inset would shrink the foreground inside its own safe zone and put a margin
        // where the silhouettes have none. Integer division at tiny sizes is the way that happens.
        listOf(1, 2, 3, 7, 8, 16, 24).forEach { size ->
            assertTrue(adaptiveForegroundInset(size) >= 0, "negative inset at size $size")
        }
    }

    @Test
    fun `the safe zone fills the square rather than sitting inside it`() {
        // The property that actually matters, stated the other way round: the central 72/108 of
        // what gets drawn must cover the whole target square. This is what makes an app icon the
        // same visual weight as the themed glyph in the row above it.
        val size = 144
        val drawn = size + 2 * adaptiveForegroundInset(size)
        val safeZone = drawn * 72 / 108
        assertTrue(safeZone >= size - 1, "safe zone $safeZone did not cover $size")
    }
}
