package com.psplauncher.themekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XmbLayoutPresetTest {
    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.002f) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "expected $actual to be within $tolerance of $expected",
        )
    }

    @Test
    fun `Thor reference window reproduces the hand-tuned compact values`() {
        val raw = XmbLayoutPreset.computeRawForWindow(
            widthPx = 1920f, heightPx = 1080f, densityDpi = 369f,
        )

        assertClose(1.32f, raw.scale)
        assertClose(0.13f, raw.barTopFraction)
        assertClose(-0.05f, raw.barLeftFraction)

        assertClose(354.55f, raw.canvasH, tolerance = 0.05f)
        assertClose(630.30f, raw.canvasW, tolerance = 0.05f)
    }

    @Test
    fun `Thor values survive the codec sanitize gate unchanged`() {
        val preset = XmbLayoutPreset.computeForWindow(
            widthPx = 1920f, heightPx = 1080f, densityDpi = 369f,
        )
        val sanitized = XmbLayoutAdjustCodec.sanitize(preset)

        assertEquals(preset, sanitized)
    }

    @Test
    fun `any 16x9 panel lands on the identical reference canvas`() {
        val odin = XmbLayoutPreset.computeRawForWindow(
            widthPx = 1920f, heightPx = 1080f, densityDpi = 480f,
        )

        assertClose(354.55f, odin.canvasH, tolerance = 0.05f)
        assertClose(630.30f, odin.canvasW, tolerance = 0.05f)
        assertClose(0.13f, odin.barTopFraction)
        assertClose(-0.05f, odin.barLeftFraction)
    }

    @Test
    fun `taller-aspect phone keeps the canvas height and widens the canvas`() {
        val phone = XmbLayoutPreset.computeRawForWindow(
            widthPx = 2400f, heightPx = 1080f, densityDpi = 440f,
        )

        assertClose(354.55f, phone.canvasH, tolerance = 0.05f)
        assertClose(787.85f, phone.canvasW, tolerance = 0.5f)
        assertClose(-0.04f, phone.barLeftFraction)
    }

    @Test
    fun `narrower 16x10 tablet drifts barLeftFraction more negative`() {
        val tablet = XmbLayoutPreset.computeRawForWindow(
            widthPx = 2560f, heightPx = 1600f, densityDpi = 320f,
        )

        assertClose(354.55f, tablet.canvasH, tolerance = 0.05f)
        assertClose(567.2f, tablet.canvasW, tolerance = 0.5f)
        assertClose(-0.0556f, tablet.barLeftFraction)
    }

    @Test
    fun `near-square foldable clamps at SCALE_MAX and degrades gracefully`() {
        val fold = XmbLayoutPreset.computeRawForWindow(
            widthPx = 2176f, heightPx = 1812f, densityDpi = 373f,
        )

        assertEquals(XmbLayoutAdjust.SCALE_MAX, fold.scale)
        assertClose(0.1469f, fold.barTopFraction)
        assertClose(-0.0682f, fold.barLeftFraction)
    }

    @Test
    fun `dp entry point matches the px entry point for the Thor window`() {
        val fromPx = XmbLayoutPreset.computeForWindow(
            widthPx = 1920f, heightPx = 1080f, densityDpi = 369f,
        )
        val fromDp = XmbLayoutPreset.computeForWindowDp(
            widthDp = 832.5f, heightDp = 468.3f, density = 369f / 160f,
        )

        assertClose(fromPx.scale, fromDp.scale)
        assertClose(fromPx.barLeftFraction, fromDp.barLeftFraction)
        assertClose(fromPx.barTopFraction, fromDp.barTopFraction)
    }

    @Test
    fun `computeForWindowDp normalizes portrait input to landscape`() {
        val landscape = XmbLayoutPreset.computeForWindowDp(832.5f, 468.3f, 369f / 160f)
        val portrait = XmbLayoutPreset.computeForWindowDp(468.3f, 832.5f, 369f / 160f)

        assertEquals(landscape, portrait)
    }

    @Test
    fun `a saved preset still matches after the prefs round trip`() {
        val preset = XmbLayoutPreset.computeForWindow(widthPx = 1920f, heightPx = 1080f, densityDpi = 369f)
        val saved = XmbLayoutAdjustCodec.decode(XmbLayoutAdjustCodec.encode(mapOf("compact" to preset)))["compact"]

        assertTrue(XmbLayoutPreset.matches(saved, preset))
    }

    @Test
    fun `one editor step, a reset to default or no saved layout is not the preset`() {
        val preset = XmbLayoutPreset.computeForWindow(widthPx = 1920f, heightPx = 1080f, densityDpi = 369f)

        assertFalse(XmbLayoutPreset.matches(preset.copy(barLeftFraction = preset.barLeftFraction + 0.01f), preset))
        assertFalse(XmbLayoutPreset.matches(preset.copy(barTopFraction = preset.barTopFraction - 0.01f), preset))
        assertFalse(XmbLayoutPreset.matches(preset.copy(scale = preset.scale + 0.02f), preset))
        assertFalse(XmbLayoutPreset.matches(XmbLayoutAdjust.DEFAULT, preset))
        assertFalse(XmbLayoutPreset.matches(null, preset))
    }
}
