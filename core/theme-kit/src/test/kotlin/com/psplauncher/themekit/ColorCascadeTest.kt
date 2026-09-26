package com.psplauncher.themekit

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorCascadeTest {
    private fun anchors(wave: Long) = ColorCascade.lightBackgroundAnchors(wave)

    @Test
    fun `classic blue anchors match the launcher's tuned gradient`() {
        val (top, bottom) = anchors(0xFF0055AAL)
        assertEquals(0xFF003469L, top)
        assertEquals(0xFF4784C1L, bottom)
    }

    @Test
    fun `white and black stay clamped in range`() {
        val (whiteTop, whiteBottom) = anchors(0xFFFFFFFFL)
        assertEquals(0xFF9E9E9EL, whiteTop)
        assertEquals(0xFFFFFFFFL, whiteBottom)

        val (blackTop, blackBottom) = anchors(0xFF000000L)
        assertEquals(0xFF000000L, blackTop)
        assertEquals(0xFF474747L, blackBottom)
    }

    @Test
    fun `alpha is always forced opaque`() {
        val (top, bottom) = anchors(0x000055AAL)
        assertEquals(0xFFL, (top shr 24) and 0xFFL)
        assertEquals(0xFFL, (bottom shr 24) and 0xFFL)
    }
}
