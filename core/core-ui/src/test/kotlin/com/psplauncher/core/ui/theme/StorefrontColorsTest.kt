package com.psplauncher.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorefrontColorsTest {
    private fun color(hex: Long) = Color(hex)

    @Test
    fun `black and white anchor the luminance range`() {
        assertEquals(0.0, relativeLuminance(Color.Black), 0.0001)
        assertEquals(1.0, relativeLuminance(Color.White), 0.0001)
    }

    @Test
    fun `white on black is the maximum contrast ratio`() {
        assertEquals(21.0, contrastRatio(Color.White, Color.Black), 0.01)
    }

    @Test
    fun `contrast ratio is symmetric`() {
        val a = color(0xFF128BC9)
        val b = color(0xFFE0A32E)
        assertEquals(contrastRatio(a, b), contrastRatio(b, a), 0.0001)
    }

    @Test
    fun `mid blue keeps a modest ratio with white`() {
        val ratio = contrastRatio(Color.White, color(0xFF128BC9))
        assertTrue("expected mid-blue/white ratio between 3.0 and 4.5, was $ratio", ratio in 3.0..4.5)
    }

    @Test
    fun `a white theme accent falls back to the wave's hue`() {
        val pfp = DefaultPFPColors.copy(accentColor = Color.White, waveColor = color(0xFF0055AA))
        assertEquals(color(0xFF0055AA), storefrontColorsFor(pfp).accentHue)
    }

    @Test
    fun `a vivid accent outranks the wave`() {
        val art = color(0xFFE03B4F)
        val pfp = DefaultPFPColors.copy(accentColor = art, waveColor = color(0xFF0055AA))
        assertEquals(art, storefrontColorsFor(pfp).accentHue)
    }

    @Test
    fun `the selection edge is that hue pulled toward white`() {
        val pfp = DefaultPFPColors.copy(accentColor = Color.White, waveColor = color(0xFF0055AA))
        val sf = storefrontColorsFor(pfp)
        assertEquals(androidx.compose.ui.graphics.lerp(sf.accentHue, Color.White, 0.55f), sf.tileSelectedEdge)
    }

    @Test
    fun `readable foreground passes through unchanged`() {
        assertEquals(Color.White, ensureReadable(Color.White, color(0xFF003369)))
        assertEquals(Color.Black, ensureReadable(Color.Black, color(0xFFE0A32E)))
    }

    @Test
    fun `white washes out on pale silver and flips to black`() {
        val pale = color(0xFFCCD5DD)
        assertEquals(Color.Black, ensureReadable(Color.White, pale))
    }

    @Test
    fun `white washes out on golden amber and flips to black`() {
        assertEquals(Color.Black, ensureReadable(Color.White, color(0xFFE9BD69)))
    }

    @Test
    fun `classic PSP blue keeps white text at the drawer 3-to-1 floor`() {
        assertEquals(Color.White, ensureReadable(Color.White, color(0xFF128BC9), 3.0f))

        assertEquals(Color.Black, ensureReadable(Color.White, color(0xFF128BC9)))
    }

    @Test
    fun `flipped text still clears the floor it was chosen against`() {
        for (bgHex in listOf(0xFFCCD5DDL, 0xFFE9BD69L, 0xFF128BC9L)) {
            val bg = color(bgHex)
            val fg = ensureReadable(Color.White, bg, 3.0f)
            assertTrue("$fg on ${bgHex.toString(16)} must clear 3.0:1",
                contrastRatio(fg, bg) >= 3.0)
        }
    }
}
