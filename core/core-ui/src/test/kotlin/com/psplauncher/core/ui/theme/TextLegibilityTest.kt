package com.psplauncher.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.psplauncher.themekit.ColorCascade
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLegibilityTest {
    private val classicBlueWave = 0xFF0055AAL

    private fun argb(v: Long) = Color(v.toInt())

    private fun greyAtLuminance(target: Double): Color {
        var lo = 0f
        var hi = 1f
        repeat(24) {
            val mid = (lo + hi) / 2f
            if (relativeLuminance(Color(mid, mid, mid)) < target) lo = mid else hi = mid
        }
        return Color(hi, hi, hi)
    }

    @Test
    fun `the raw bottom gradient anchor cannot carry white text`() {
        val bottom = argb(ColorCascade.lightBackgroundAnchors(classicBlueWave).second)
        assertTrue(
            "lighten(wave, 0.28) is meant to be a bright anchor; if this ever passes 4.5:1 on its " +
                "own the scrim solve below has stopped being load-bearing and should be revisited",
            contrastRatio(Color.White, bottom) < 4.5,
        )
    }

    @Test
    fun `solved scrim anchors clear AA over a worst-case white wallpaper`() {
        val (top, bottom) = ColorCascade.lightBackgroundAnchors(classicBlueWave)
        val (solvedTop, solvedBottom) = xmbScrimAnchors(argb(top), argb(bottom))

        val topBg = composite(solvedTop, Color.White)
        val bottomBg = composite(solvedBottom, Color.White)

        assertTrue(contrastRatio(Color.White, topBg) >= 4.5)
        assertTrue(contrastRatio(Color.White, bottomBg) >= 4.5)
    }

    @Test
    fun `the scrim solve keeps the theme hue instead of collapsing to neutral`() {
        val bottom = argb(ColorCascade.lightBackgroundAnchors(classicBlueWave).second)
        val solved = solveScrimColor(bottom, alpha = 0.90f)

        assertTrue(solved.blue > solved.green)
        assertTrue(solved.green > solved.red)
        assertTrue("should not have bottomed out at black", solved.blue > 0.15f)
    }

    @Test
    fun `an already dark anchor is returned untouched`() {
        val deep = Color(0xFF0A0F1A)
        assertEquals(deep, solveScrimColor(deep, alpha = 0.90f))
    }

    @Test
    fun `composite blends in sRGB channel space`() {
        val half = composite(Color.Black.copy(alpha = 0.5f), Color.White)
        assertEquals(0.5f, half.red, 1f / 255f)
        assertEquals(0.5f, half.green, 1f / 255f)
        assertEquals(0.5f, half.blue, 1f / 255f)
    }

    @Test
    fun `a full-screen scrim over the measured bright band buries the wallpaper`() {
        val band = greyAtLuminance(0.539)
        assertTrue(contrastRatio(Color.White, band) < 2.0)

        val alpha = scrimAlphaFor(Color.Black, band, Color.White)

        assertTrue("expected ~0.40, was $alpha", alpha in 0.35f..0.45f)
    }

    @Test
    fun `no scrim is needed where the backdrop is already dark`() {
        assertEquals(0f, scrimAlphaFor(Color.Black, Color(0xFF101010), Color.White), 0f)
    }

    @Test
    fun `the clamp preserves hue and saturation`() {
        val backgrounds = listOf(Color.White, Color.Black, greyAtLuminance(0.30), Color(0xFF128BC9))
        val foregrounds = listOf(
            Color(0xFF4A90D9), Color(0xFFFF6B6B), Color(0xFF3DDC84),
            Color(0xFFFFC107), Color(0xFF9C27B0), Color(0xFF00BCD4),
        )
        for (bg in backgrounds) for (fg in foregrounds) {
            val out = clampLightnessForContrast(fg, bg, maxShift = 1f)
            if (!out.adjusted) continue
            val (h0, s0, _) = fg.toHsl()
            val (h1, s1, _) = out.color.toHsl()

            assertEquals("hue drifted for $fg on $bg", h0, h1, 1f)

            assertEquals("saturation drifted for $fg on $bg", s0, s1, 0.02f)
        }
    }

    @Test
    fun `the clamp never reports a passing colour that does not pass`() {
        val backgrounds = (0..10).map { greyAtLuminance(it / 10.0) }
        val foregrounds = listOf(Color(0xFF4A90D9), Color(0xFFFFC107), Color(0xFF9C27B0))
        for (bg in backgrounds) for (fg in foregrounds) {
            val out = clampLightnessForContrast(fg, bg, maxShift = 1f)
            if (out.meetsTarget) {
                assertTrue(
                    "claimed to pass at ${out.achievedRatio} on $bg",
                    out.achievedRatio >= 4.5f - 0.01f,
                )
            }
        }
    }

    @Test
    fun `an unadjusted result is the requested colour`() {
        val out = clampLightnessForContrast(Color.White, Color.Black)
        assertFalse(out.adjusted)
        assertEquals(Color.White, out.color)
        assertTrue(out.meetsTarget)
    }

    @Test
    fun `the clamp picks the nearer direction rather than the darker one`() {
        val out = clampLightnessForContrast(Color(0xFF4A90D9), greyAtLuminance(0.10), maxShift = 1f)
        val (_, _, l0) = Color(0xFF4A90D9).toHsl()
        val (_, _, l1) = out.color.toHsl()
        assertTrue("expected a lift, got $l1 from $l0", l1 > l0)
    }

    @Test
    fun `the accent on the measured band is rescued by the clamp, not by a plate`() {
        val accent = Color(0xFF4A90D9)
        val band = greyAtLuminance(0.539)
        assertTrue(contrastRatio(accent, band) < 2.0)

        val out = clampLightnessForContrast(accent, band)
        assertTrue("this case is reachable and must be reached", out.meetsTarget)
        assertTrue("reaching it changes the colour, so the notice must fire", out.adjusted)
        assertTrue(out.achievedRatio >= 4.5f)

        val (h0, s0, l0) = accent.toHsl()
        val (h1, s1, l1) = out.color.toHsl()
        assertEquals(h0, h1, 0.5f)
        assertEquals(s0, s1, 0.02f)
        assertTrue("should have darkened, not brightened", l1 < l0)
    }

    @Test
    fun `a colour too far from any passing lightness escalates instead of being overridden`() {
        val pale = Color(0xFFFFF3B0)
        val band = greyAtLuminance(0.539)

        val out = clampLightnessForContrast(pale, band)
        assertFalse("must not silently repaint the user's choice", out.adjusted)
        assertFalse(out.meetsTarget)
        assertEquals(pale, out.color)

        val (_, _, l0) = pale.toHsl()
        val (_, _, l1) = clampLightnessForContrast(pale, band, maxShift = 1f).color.toHsl()
        assertTrue("the unbounded move is what maxShift exists to refuse", abs(l1 - l0) > 0.35f)
    }

    @Test
    fun `hsl round trips`() {
        val colors = listOf(
            Color(0xFF4A90D9), Color(0xFFFF6B6B), Color(0xFF3DDC84),
            Color.White, Color.Black, Color(0xFF808080), Color(0xFFFFC107),
        )
        for (c in colors) {
            val (h, s, l) = c.toHsl()
            val back = hslToColor(h, s, l)
            assertEquals(c.red, back.red, 0.002f)
            assertEquals(c.green, back.green, 0.002f)
            assertEquals(c.blue, back.blue, 0.002f)
        }
    }

    @Test
    fun `body text is held to AA and only large text drops to three`() {
        assertEquals(4.5f, TextContrastRole.BODY.threshold, 0f)
        assertEquals(3.0f, TextContrastRole.LARGE.threshold, 0f)
    }

    @Test
    fun `bestPolarity flips at the crossover`() {
        assertEquals(Color.White, bestPolarity(Color(0xFF101010)))
        assertEquals(Color.Black, bestPolarity(Color(0xFFF0F0F0)))
    }
}
