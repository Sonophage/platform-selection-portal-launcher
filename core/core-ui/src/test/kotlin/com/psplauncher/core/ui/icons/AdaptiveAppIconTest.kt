package com.psplauncher.core.ui.icons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdaptiveAppIconTest {
    @Test
    fun `the foreground is drawn at one and a half times the target square`() {
        val size = 192
        val inset = adaptiveForegroundInset(size)
        assertEquals(48, inset)
        assertEquals(size * 3 / 2, size + 2 * inset)
    }

    @Test
    fun `the ratio holds at every size the app asks for`() {
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
        listOf(1, 2, 3, 7, 8, 16, 24).forEach { size ->
            assertTrue(adaptiveForegroundInset(size) >= 0, "negative inset at size $size")
        }
    }

    @Test
    fun `the safe zone fills the square rather than sitting inside it`() {
        val size = 144
        val drawn = size + 2 * adaptiveForegroundInset(size)
        val safeZone = drawn * 72 / 108
        assertTrue(safeZone >= size - 1, "safe zone $safeZone did not cover $size")
    }
}
