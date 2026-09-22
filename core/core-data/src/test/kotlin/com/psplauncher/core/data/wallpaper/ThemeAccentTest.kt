package com.psplauncher.core.data.wallpaper

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.psplauncher.core.data.wallpaper.ThemeAccent.KEY_ACCENT_FROM_WALLPAPER
import com.psplauncher.core.data.wallpaper.ThemeAccent.KEY_ACCENT_OVERRIDE
import com.psplauncher.core.data.wallpaper.ThemeAccent.followWallpaperAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "Color from Wallpaper" — the rule that decides whether the theme accent is a value the user
 * picked or one the wallpaper produced.
 *
 * It is a two-line function guarding a preference that changes what the whole shell looks like,
 * and it runs from two places (the wallpaper writer and the toggle). The cases that matter are
 * the ones where it must NOT write: an accent that overwrote a hand-picked colour because the
 * flag was off would be silent, permanent and unattributable.
 */
class ThemeAccentTest {

    @Test
    fun `with the setting off, nothing is touched`() {
        // The whole point of the guard. A user who picked Gold keeps Gold when the wallpaper
        // changes, and there is no other check between the wallpaper writer and this value.
        val prefs = mutablePreferencesOf(
            KEY_ACCENT_FROM_WALLPAPER to false,
            KEY_ACCENT_OVERRIDE to 0xFFE0B341L,
        )
        prefs.followWallpaperAccent(0xFF1188CCL)
        assertEquals(0xFFE0B341L, prefs[KEY_ACCENT_OVERRIDE])
    }

    @Test
    fun `an absent setting is off, not on`() {
        // Every install that predates the setting has no value for the key. Defaulting the other
        // way would repaint all of them from their wallpaper on first launch.
        val prefs = mutablePreferencesOf()
        prefs.followWallpaperAccent(0xFF1188CCL)
        assertNull(prefs[KEY_ACCENT_OVERRIDE])
    }

    @Test
    fun `with the setting on, the accent follows the wallpaper`() {
        val prefs = mutablePreferencesOf(
            KEY_ACCENT_FROM_WALLPAPER to true,
            KEY_ACCENT_OVERRIDE to 0xFFE0B341L,
        )
        prefs.followWallpaperAccent(0xFF1188CCL)
        assertEquals(0xFF1188CCL, prefs[KEY_ACCENT_OVERRIDE])
    }

    @Test
    fun `no wallpaper to derive from clears the override rather than keeping the last one`() {
        // Reached when the wallpaper is removed or will not decode. Keeping the value would leave
        // the shell wearing the colour of a picture that is no longer there, with a setting that
        // says it is following the wallpaper.
        val prefs = mutablePreferencesOf(
            KEY_ACCENT_FROM_WALLPAPER to true,
            KEY_ACCENT_OVERRIDE to 0xFFE0B341L,
        )
        prefs.followWallpaperAccent(null)
        assertNull(prefs[KEY_ACCENT_OVERRIDE])
    }

    @Test
    fun `turning the setting off is not this function's job`() {
        // followWallpaperAccent only ever runs with the flag ON; the toggle clears the override
        // itself. Pinned because the obvious "simplification" is to make this handle both, which
        // would make every wallpaper write with the flag off clear a hand-picked colour.
        val prefs = mutablePreferencesOf(
            KEY_ACCENT_FROM_WALLPAPER to false,
            KEY_ACCENT_OVERRIDE to 0xFFE0B341L,
        )
        prefs.followWallpaperAccent(null)
        assertEquals(0xFFE0B341L, prefs[KEY_ACCENT_OVERRIDE])
    }
}
