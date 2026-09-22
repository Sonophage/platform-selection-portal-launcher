package com.psplauncher.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the WCAG-ish contrast helpers behind [deriveStorefrontColors] — the floor that keeps pale
 * accents (Silver Mono, Golden Amber) from washing white text out. Pure color math, no Compose
 * runtime, so these run as plain JVM unit tests.
 */
class StorefrontColorsTest {

    private fun color(hex: Long) = Color(hex)

    // ── Relative luminance / ratio ────────────────────────────────────────

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
        val a = color(0xFF128BC9)  // classic PSP mid-blue
        val b = color(0xFFE0A32E)  // golden amber
        assertEquals(contrastRatio(a, b), contrastRatio(b, a), 0.0001)
    }

    @Test
    fun `mid blue keeps a modest ratio with white`() {
        // ~3.9:1 — below WCAG AA normal text, which is exactly why derive passes a 3.0 floor
        // rather than flipping the whole classic PSP look to dark text.
        val ratio = contrastRatio(Color.White, color(0xFF128BC9))
        assertTrue("expected mid-blue/white ratio between 3.0 and 4.5, was $ratio", ratio in 3.0..4.5)
    }

    // ── The one accent hue ────────────────────────────────────────────────
    //
    // Settings, the context menu and the App Drawer are supposed to share a tint and did not.
    // menuCursorEdge lerped the raw accentColor toward white; this palette resolves the hue first.
    // These pin the resolution, so the menus and the drawer cannot go back to disagreeing.

    @Test
    fun `a white theme accent falls back to the wave's hue`() {
        // The NORMAL case, not an edge case: XmbColorScheme.resolve sets accentColor = white for
        // every preset. lerp(white, white) is white, which is why every menu cursor outside a game
        // detail page was a plain neutral highlight while the drawer wore the theme's colour.
        val pfp = DefaultPFPColors.copy(accentColor = Color.White, waveColor = color(0xFF0055AA))
        assertEquals(color(0xFF0055AA), storefrontColorsFor(pfp).accentHue)
    }

    @Test
    fun `a vivid accent outranks the wave`() {
        // A game detail page tints accentColor with the artwork (withArtTint). That colour is the
        // point of the page, so it must not be thrown away for the wave behind it.
        val art = color(0xFFE03B4F)
        val pfp = DefaultPFPColors.copy(accentColor = art, waveColor = color(0xFF0055AA))
        assertEquals(art, storefrontColorsFor(pfp).accentHue)
    }

    @Test
    fun `the selection edge is that hue pulled toward white`() {
        // The pair itself: menuCursorEdge now RETURNS tileSelectedEdge, so this is the formula
        // both the menus and the drawer are drawing. If the edge stops being derived from
        // accentHue, the menu cursor silently stops matching the drawer again.
        val pfp = DefaultPFPColors.copy(accentColor = Color.White, waveColor = color(0xFF0055AA))
        val sf = storefrontColorsFor(pfp)
        assertEquals(androidx.compose.ui.graphics.lerp(sf.accentHue, Color.White, 0.55f), sf.tileSelectedEdge)
    }

    // ── ensureReadable ─────────────────────────────────────────────────────

    @Test
    fun `readable foreground passes through unchanged`() {
        assertEquals(Color.White, ensureReadable(Color.White, color(0xFF003369)))
        assertEquals(Color.Black, ensureReadable(Color.Black, color(0xFFE0A32E)))
    }

    @Test
    fun `white washes out on pale silver and flips to black`() {
        // Silver Mono's brighter anchor (wave #B8C4D0 lightened) — white on it is ~1.5:1.
        val pale = color(0xFFCCD5DD)
        assertEquals(Color.Black, ensureReadable(Color.White, pale))
    }

    @Test
    fun `white washes out on golden amber and flips to black`() {
        // Golden Amber's grid midtone (~#E9BD69) — white is ~1.8:1, black is ~10:1.
        assertEquals(Color.Black, ensureReadable(Color.White, color(0xFFE9BD69)))
    }

    @Test
    fun `classic PSP blue keeps white text at the drawer 3-to-1 floor`() {
        // The derive call passes 3.0 so the established white-on-blue identity survives…
        assertEquals(Color.White, ensureReadable(Color.White, color(0xFF128BC9), 3.0f))
        // …while the helper's own WCAG AA default still flags the same pair as unreadable and
        // picks the pole with the better ratio (black beats white there).
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
