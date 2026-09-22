package com.psplauncher.core.data.wallpaper

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

/**
 * The theme's accent colour, and whether it follows the wallpaper.
 *
 * [KEY_ACCENT_OVERRIDE] had three definitions of the same string — PfpThemeStore, PtfThemeImporter
 * and BackupManager each spelled `"theme_accent_override"` out again. It has one now, here, beside
 * the rule that writes it: three copies of a preference key is three chances for one of them to be
 * renamed alone, and the symptom would be a setting that silently stops persisting.
 */
object ThemeAccent {

    /** The custom accent that supersedes the preset colour scheme. Absent = use the scheme. */
    val KEY_ACCENT_OVERRIDE = longPreferencesKey("theme_accent_override")

    /**
     * Settings ▸ Appearance ▸ Theme ▸ "Color from Wallpaper".
     *
     * When on, [KEY_ACCENT_OVERRIDE] is not a value the user picked — it is whatever the current
     * wallpaper yields, re-derived every time the wallpaper changes.
     */
    val KEY_ACCENT_FROM_WALLPAPER = booleanPreferencesKey("theme_accent_from_wallpaper")

    /**
     * Point the theme accent at [accent], but only if the user asked for that.
     *
     * Called from inside the transaction that stores the wallpaper's derived accent, never as a
     * second write afterwards. Two writes could interleave with a wallpaper change and leave the
     * theme wearing the previous picture's colour — the same failure
     * [WallpaperLuminanceProbe.WallpaperSurvey] exists to prevent for the luminance map.
     *
     * A null [accent] (no wallpaper, or one that would not decode) clears the override, so the
     * preset colour scheme comes back rather than the last picture's colour outliving it.
     */
    fun MutablePreferences.followWallpaperAccent(accent: Long?) {
        if (this[KEY_ACCENT_FROM_WALLPAPER] != true) return
        if (accent != null) this[KEY_ACCENT_OVERRIDE] = accent else this.remove(KEY_ACCENT_OVERRIDE)
    }
}
