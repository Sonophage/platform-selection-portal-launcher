package com.psplauncher.core.data.wallpaper

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

object ThemeAccent {
    val KEY_ACCENT_OVERRIDE = longPreferencesKey("theme_accent_override")

    val KEY_ACCENT_FROM_WALLPAPER = booleanPreferencesKey("theme_accent_from_wallpaper")

    fun MutablePreferences.followWallpaperAccent(accent: Long?) {
        if (this[KEY_ACCENT_FROM_WALLPAPER] != true) return
        if (accent != null) this[KEY_ACCENT_OVERRIDE] = accent else this.remove(KEY_ACCENT_OVERRIDE)
    }
}
