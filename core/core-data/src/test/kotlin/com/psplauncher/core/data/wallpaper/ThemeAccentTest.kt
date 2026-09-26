package com.psplauncher.core.data.wallpaper

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.psplauncher.core.data.wallpaper.ThemeAccent.KEY_ACCENT_FROM_WALLPAPER
import com.psplauncher.core.data.wallpaper.ThemeAccent.KEY_ACCENT_OVERRIDE
import com.psplauncher.core.data.wallpaper.ThemeAccent.followWallpaperAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeAccentTest {
    @Test
    fun `with the setting off, nothing is touched`() {
        val prefs = mutablePreferencesOf(
            KEY_ACCENT_FROM_WALLPAPER to false,
            KEY_ACCENT_OVERRIDE to 0xFFE0B341L,
        )
        prefs.followWallpaperAccent(0xFF1188CCL)
        assertEquals(0xFFE0B341L, prefs[KEY_ACCENT_OVERRIDE])
    }

    @Test
    fun `an absent setting is off, not on`() {
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
        val prefs = mutablePreferencesOf(
            KEY_ACCENT_FROM_WALLPAPER to true,
            KEY_ACCENT_OVERRIDE to 0xFFE0B341L,
        )
        prefs.followWallpaperAccent(null)
        assertNull(prefs[KEY_ACCENT_OVERRIDE])
    }

    @Test
    fun `turning the setting off is not this function's job`() {
        val prefs = mutablePreferencesOf(
            KEY_ACCENT_FROM_WALLPAPER to false,
            KEY_ACCENT_OVERRIDE to 0xFFE0B341L,
        )
        prefs.followWallpaperAccent(null)
        assertEquals(0xFFE0B341L, prefs[KEY_ACCENT_OVERRIDE])
    }
}
