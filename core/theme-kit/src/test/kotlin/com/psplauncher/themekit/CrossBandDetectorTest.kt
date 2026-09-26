package com.psplauncher.themekit

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrossBandDetectorTest {
    private val bright = 0xFFB4B4B4.toInt()
    private val dark = 0xFF1A1A1A.toInt()

    private fun bandImage(
        width: Int = 480,
        height: Int = 272,
        bandRows: IntRange?,
        bandColor: Int = dark,
        background: Int = bright,
    ): BmpImage {
        val argb = IntArray(width * height) { i ->
            val row = i / width
            if (bandRows != null && row in bandRows) bandColor else background
        }
        return BmpImage(width, height, argb)
    }

    @Test
    fun `uniform image yields null`() {
        assertNull(CrossBandDetector.detectBarTopFraction(bandImage(bandRows = null)))
    }

    @Test
    fun `dark band at quarter height is detected near its top edge`() {
        val fraction = assertNotNull(
            CrossBandDetector.detectBarTopFraction(bandImage(bandRows = 68..108)),
        )
        assertTrue(fraction in 0.22f..0.28f, "expected ≈0.25, got $fraction")
    }

    @Test
    fun `band in the lower half is ignored`() {
        assertNull(CrossBandDetector.detectBarTopFraction(bandImage(bandRows = 163..204)))
    }

    @Test
    fun `a thin line is not a band`() {
        assertNull(CrossBandDetector.detectBarTopFraction(bandImage(bandRows = 68..70)))
    }

    @Test
    fun `low-contrast band is rejected`() {
        val slightlyDark = 0xFF9E9E9E.toInt()
        assertNull(
            CrossBandDetector.detectBarTopFraction(
                bandImage(bandRows = 68..108, bandColor = slightlyDark),
            ),
        )
    }

    @Test
    fun `dark-from-the-top image has no top edge and is rejected`() {
        assertNull(CrossBandDetector.detectBarTopFraction(bandImage(bandRows = 0..108)))
    }

    @Test
    fun `tiny images yield null`() {
        assertNull(CrossBandDetector.detectBarTopFraction(bandImage(width = 16, height = 16, bandRows = 4..8)))
    }
}
