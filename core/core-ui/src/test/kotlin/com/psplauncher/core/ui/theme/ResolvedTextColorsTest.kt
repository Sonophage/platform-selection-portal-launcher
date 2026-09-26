package com.psplauncher.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

class ResolvedTextColorsTest {
    private fun resolve(requested: Color, top: Color, bottom: Color) = resolveTextColors(requested, top, bottom)

    private val silverTop = Color(0xFFBFC3C7)
    private val silverBottom = Color(0xFFE6E9EC)
    private val classicBlueTop = Color(0xFF0743A2)
    private val classicBlueBottom = Color(0xFF128BC9)

    @Test
    fun `classic blue keeps white text and is not marked adjusted`() {
        val c = resolve(Color.White, classicBlueTop, classicBlueBottom)
        assertTrue(c.primary == Color.White, "classic blue flipped away from white")
        assertTrue(!c.adjusted, "classic blue reported as adjusted")
        assertTrue(c.secondary == DefaultPfpTextColors.secondary, "classic blue changed its secondary")
    }

    @Test
    fun `a pale wallpaper flips the text to the dark family`() {
        val c = resolve(Color.White, silverTop, silverBottom)
        assertTrue(c.primary.luminance() < 0.5f, "primary stayed light on a pale wallpaper")
        assertTrue(c.adjusted, "a flip was not reported as an adjustment")
    }

    @Test
    fun `when primary flips, secondary and inactive flip with it`() {
        val c = resolve(Color.White, silverTop, silverBottom)
        assertTrue(c.secondary.luminance() < 0.5f, "secondary stayed light under a dark primary")
        assertTrue(c.inactive.luminance() < 0.5f, "inactive stayed light under a dark primary")
    }

    @Test
    fun `the three roles stay distinguishable from each other after a flip`() {
        val c = resolve(Color.White, silverTop, silverBottom)
        val p = c.primary.luminance()
        val s = c.secondary.luminance()
        val i = c.inactive.luminance()
        assertTrue(p < s, "primary was not darker than secondary ($p vs $s)")
        assertTrue(s < i, "secondary was not darker than inactive ($s vs $i)")
    }

    @Test
    fun `the flipped text actually clears the bar it was flipped for`() {
        val c = resolve(Color.White, silverTop, silverBottom)
        val backdrop = lerp(silverTop, silverBottom, 0.5f)
        assertTrue(
            contrastRatio(c.primary, backdrop) >= 3.0,
            "flipped primary still under 3:1 (${contrastRatio(c.primary, backdrop)})",
        )
        assertTrue(
            contrastRatio(c.secondary, backdrop) >= 3.0,
            "flipped secondary still under 3:1 (${contrastRatio(c.secondary, backdrop)})",
        )
    }

    @Test
    fun `the two themes sit on opposite sides of the floor by a wide margin`() {
        val blueMid = lerp(classicBlueTop, classicBlueBottom, 0.5f)
        val silverMid = lerp(silverTop, silverBottom, 0.5f)
        val whiteOnBlue = contrastRatio(Color.White, blueMid)
        val whiteOnSilver = contrastRatio(Color.White, silverMid)
        assertTrue(whiteOnBlue > 4.5, "classic blue is now borderline at $whiteOnBlue")
        assertTrue(whiteOnSilver < 2.0, "silver is no longer clearly failing at $whiteOnSilver")
        assertTrue(resolve(Color.White, classicBlueTop, classicBlueBottom).primary == Color.White)
    }
}
