package com.psplauncher.themekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WallpaperLuminanceMapTest {
    private fun image(width: Int = 240, height: Int = 240, argbAt: (x: Int, y: Int) -> Int) =
        BmpImage(width, height, IntArray(width * height) { i -> argbAt(i % width, i / width) })

    private fun gray(v: Int) = 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v

    private val black = gray(0)
    private val white = gray(255)

    @Test
    fun `relative luminance is not Rec 601 luma`() {
        val midGreen = 0xFF00A000.toInt()
        assertNotEquals(
            WallpaperMetrics.luminance(midGreen),
            WallpaperLuminanceMap.relativeLuminance(midGreen),
            "WallpaperMetrics.luminance is Rec.601 and does not compose with contrastRatio",
        )
        assertTrue(
            WallpaperLuminanceMap.relativeLuminance(midGreen) <
                WallpaperMetrics.luminance(midGreen),
            "sRGB gamma pulls mid-tones down; a swap would silently over-report brightness",
        )
    }

    @Test
    fun `relative luminance endpoints and green weighting`() {
        assertEquals(0f, WallpaperLuminanceMap.relativeLuminance(black), 0.0001f)
        assertEquals(1f, WallpaperLuminanceMap.relativeLuminance(white), 0.0001f)

        assertEquals(0.2126f, WallpaperLuminanceMap.relativeLuminance(0xFFFF0000.toInt()), 0.001f)
        assertEquals(0.7152f, WallpaperLuminanceMap.relativeLuminance(0xFF00FF00.toInt()), 0.001f)
        assertEquals(0.0722f, WallpaperLuminanceMap.relativeLuminance(0xFF0000FF.toInt()), 0.001f)
    }

    @Test
    fun `a flat image reports the same luminance in every band`() {
        val map = WallpaperLuminanceMap.compute(image { _, _ -> gray(128) }, "/w.png")
        val expected = WallpaperLuminanceMap.relativeLuminance(gray(128))

        assertEquals(WallpaperLuminanceMap.ROWS * WallpaperLuminanceMap.ZONES, map.bands.size)
        map.bands.forEach { band ->
            assertEquals(expected, band.mean, 0.005f)
            assertEquals(expected, band.p90, 0.005f)
        }
    }

    @Test
    fun `bands track a top-to-bottom sweep`() {
        val map = WallpaperLuminanceMap.compute(
            image { _, y -> gray((y * 255 / 239).coerceIn(0, 255)) },
            "/sweep.png",
        )
        val top = map.bandAt(0.5f, 0f)
        val bottom = map.bandAt(0.5f, 0.99f)

        assertTrue(top.mean < 0.05f, "top band should be near-black, was ${top.mean}")
        assertTrue(bottom.mean > 0.85f, "bottom band should be near-white, was ${bottom.mean}")
    }

    @Test
    fun `zones are independent across the width`() {
        val map = WallpaperLuminanceMap.compute(
            image { x, _ -> if (x < 80) black else white },
            "/split.png",
        )
        assertTrue(map.bandAt(0.1f, 0.5f).mean < 0.05f)
        assertTrue(map.bandAt(0.9f, 0.5f).mean > 0.95f)
    }

    @Test
    fun `p90 catches a bright cloud the mean hides`() {
        val map = WallpaperLuminanceMap.compute(
            image { _, y -> if (y % 20 >= 16) white else black },
            "/cloud.png",
        )
        val band = map.bandAt(0.5f, 0f)

        assertEquals(0.2f, band.mean, 0.02f, "the mean reads this band as mostly dark")
        assertTrue(band.p90 > 0.95f, "but p90 must see the bright fifth, was ${band.p90}")
    }

    @Test
    fun `bandAt clamps out-of-range fractions instead of throwing`() {
        val map = WallpaperLuminanceMap.compute(image { _, _ -> gray(64) }, "/w.png")
        assertEquals(map.bandAt(0f, 0f), map.bandAt(-1f, -1f))
        assertEquals(map.bandAt(1f, 1f), map.bandAt(2f, 2f))
    }

    @Test
    fun `the wave boost applies only to the bottom region`() {
        val map = WallpaperLuminanceMap.compute(image { _, _ -> gray(100) }, "/w.png")
        val plain = map.bandAt(0.5f, 0.5f)

        assertEquals(plain.mean, map.effectiveBandAt(0.5f, 0.5f).mean, 0.0001f)
        assertEquals(
            plain.mean + WallpaperLuminanceMap.WAVE_LUMINANCE_BOOST,
            map.effectiveBandAt(0.5f, 0.9f).mean,
            0.0001f,
        )
    }

    @Test
    fun `the wave boost never pushes past white`() {
        val map = WallpaperLuminanceMap.compute(image { _, _ -> white }, "/w.png")
        assertEquals(1f, map.effectiveBandAt(0.5f, 1f).mean, 0.0001f)
        assertEquals(1f, map.effectiveBandAt(0.5f, 1f).p90, 0.0001f)
    }

    @Test
    fun `json round-trips`() {
        val original = WallpaperLuminanceMap.compute(
            image { x, y -> gray(((x + y) % 256)) },
            "/files/wallpapers/w-1234.png",
        )
        val restored = WallpaperLuminanceMap.fromJson(original.toJson(), "/files/wallpapers/w-1234.png")
        assertEquals(original, restored)
    }

    @Test
    fun `a map for a different wallpaper is discarded`() {
        val map = WallpaperLuminanceMap.compute(image { _, _ -> white }, "/files/w-1111.png")
        assertNull(WallpaperLuminanceMap.fromJson(map.toJson(), "/files/w-2222.png"))
    }

    @Test
    fun `unreadable json is discarded rather than thrown`() {
        assertNull(WallpaperLuminanceMap.fromJson("", "/w.png"))
        assertNull(WallpaperLuminanceMap.fromJson("{\"source\":\"/w.png\"", "/w.png"))

        assertNull(WallpaperLuminanceMap.fromJson("{\"source\":\"/w.png\",\"bands\":[]}", "/w.png"))
    }
}
