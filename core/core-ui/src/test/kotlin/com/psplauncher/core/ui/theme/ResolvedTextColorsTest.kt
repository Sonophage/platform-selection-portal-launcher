package com.psplauncher.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The rule that the app's text colour is decided by the wallpaper, not by a constant.
 *
 * This replaces a question that had no answer. Every sublabel colour in the app was light — the
 * XMB's cool `0xAAC8DAF2`, the detail pages' neutral `0xAAEEEEEE`, the music screens' lavender
 * `0xFFC9C7E8`, `PfpPalette.Subtext`'s `#AAAAAA` — because all of them were chosen against dark
 * themes. On the pale Silver scheme the whole unselected tier vanished into the wallpaper, and
 * picking a winner between two light colours could not have fixed it.
 *
 * So the poles are measured. These tests pin the two halves that must agree: primary flips when a
 * light wallpaper makes white unreadable, and when it flips, the other two roles flip with it.
 * A secondary left in the light family under a dark primary is the original bug wearing the new
 * machinery, and it would look exactly like the old one on screen.
 *
 * The maths lives in [ensureReadable] and is tested separately; what is asserted here is the
 * *policy* PFPTheme applies on top of it.
 */
class ResolvedTextColorsTest {

    // Calls the production resolver. Deliberately NOT a copy of it: the first draft of this
    // test re-implemented the policy locally, and breaking the real code left it green — it could
    // only ever have tested itself.
    private fun resolve(requested: Color, top: Color, bottom: Color) = resolveTextColors(requested, top, bottom)

    private val silverTop = Color(0xFFBFC3C7)
    private val silverBottom = Color(0xFFE6E9EC)
    private val classicBlueTop = Color(0xFF0743A2)
    private val classicBlueBottom = Color(0xFF128BC9)

    @Test
    fun `classic blue keeps white text and is not marked adjusted`() {
        // The default theme must not change. A resolution pass that repainted the app people
        // already use would be a regression dressed as a fix.
        val c = resolve(Color.White, classicBlueTop, classicBlueBottom)
        assertTrue(c.primary == Color.White, "classic blue flipped away from white")
        assertTrue(!c.adjusted, "classic blue reported as adjusted")
        assertTrue(c.secondary == DefaultPfpTextColors.secondary, "classic blue changed its secondary")
    }

    @Test
    fun `a pale wallpaper flips the text to the dark family`() {
        // THE REPORTED FAILURE. On Silver, white text sat on a near-white wallpaper.
        val c = resolve(Color.White, silverTop, silverBottom)
        assertTrue(c.primary.luminance() < 0.5f, "primary stayed light on a pale wallpaper")
        assertTrue(c.adjusted, "a flip was not reported as an adjustment")
    }

    @Test
    fun `when primary flips, secondary and inactive flip with it`() {
        // The guard on the pair. Half a flip is the same bug: a dark title over light sublabels on
        // a light page reads worse than the all-light version it replaced, because the page now
        // looks deliberate.
        val c = resolve(Color.White, silverTop, silverBottom)
        assertTrue(c.secondary.luminance() < 0.5f, "secondary stayed light under a dark primary")
        assertTrue(c.inactive.luminance() < 0.5f, "inactive stayed light under a dark primary")
    }

    @Test
    fun `the three roles stay distinguishable from each other after a flip`() {
        // Collapsing all three onto pure black would make the flip legible and the hierarchy gone.
        // Primary is the darkest, inactive the lightest, and none of them are the same.
        val c = resolve(Color.White, silverTop, silverBottom)
        val p = c.primary.luminance()
        val s = c.secondary.luminance()
        val i = c.inactive.luminance()
        assertTrue(p < s, "primary was not darker than secondary ($p vs $s)")
        assertTrue(s < i, "secondary was not darker than inactive ($s vs $i)")
    }

    @Test
    fun `the flipped text actually clears the bar it was flipped for`() {
        // A flip that lands on a colour which still fails is worse than no flip: it spends the
        // visual change and buys nothing.
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
        // Measured, because the numbers are the whole argument and one of them was folklore.
        // TextLegibility's comment quotes "white on the classic PSP blue is ~3.9:1", which is true
        // of the darker top anchor; the mid-tone the gradient actually presents is 5.78:1. Either
        // way it clears 3.0 comfortably, so the signature theme cannot flip — and Silver's 1.46:1
        // cannot fail to. Nothing here is borderline, which is what makes a single floor workable.
        val blueMid = lerp(classicBlueTop, classicBlueBottom, 0.5f)
        val silverMid = lerp(silverTop, silverBottom, 0.5f)
        val whiteOnBlue = contrastRatio(Color.White, blueMid)
        val whiteOnSilver = contrastRatio(Color.White, silverMid)
        assertTrue(whiteOnBlue > 4.5, "classic blue is now borderline at $whiteOnBlue")
        assertTrue(whiteOnSilver < 2.0, "silver is no longer clearly failing at $whiteOnSilver")
        assertTrue(resolve(Color.White, classicBlueTop, classicBlueBottom).primary == Color.White)
    }
}
