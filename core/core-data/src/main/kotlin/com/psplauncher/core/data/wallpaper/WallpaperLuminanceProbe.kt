package com.psplauncher.core.data.wallpaper

import android.graphics.BitmapFactory
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psplauncher.core.data.wallpaper.ThemeAccent.followWallpaperAccent
import com.psplauncher.themekit.AccentDeriver
import com.psplauncher.themekit.BmpImage
import com.psplauncher.themekit.WallpaperLuminanceMap
import java.io.File
import kotlin.math.max

object WallpaperLuminanceProbe {
    val KEY_WALLPAPER_LUMA = stringPreferencesKey("display_wallpaper_luma")

    val KEY_WALLPAPER_ACCENT = longPreferencesKey("wallpaper_accent")

    const val MAX_EDGE = 256

    fun survey(path: String): WallpaperSurvey? = runCatching {
        if (!File(path).isFile) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = max(1, max(bounds.outWidth, bounds.outHeight) / MAX_EDGE)
            },
        ) ?: return null

        val width = decoded.width
        val height = decoded.height
        val pixels = IntArray(width * height)
        decoded.getPixels(pixels, 0, width, 0, 0, width, height)
        decoded.recycle()
        if (width <= 0 || height <= 0) return null

        val image = BmpImage(width, height, pixels)
        WallpaperSurvey(
            luma = WallpaperLuminanceMap.compute(image, path).toJson(),

            accentArgb = AccentDeriver.deriveAccent(image)?.toUInt()?.toLong(),
        )
    }.getOrNull()

    data class WallpaperSurvey(val luma: String?, val accentArgb: Long?)

    fun describes(json: String?, path: String): Boolean =
        json != null && WallpaperLuminanceMap.fromJson(json, path) != null

    fun MutablePreferences.setWallpaperLuma(survey: WallpaperSurvey?) {
        val json = survey?.luma
        if (json != null) this[KEY_WALLPAPER_LUMA] = json else this.remove(KEY_WALLPAPER_LUMA)
        val accent = survey?.accentArgb
        if (accent != null) this[KEY_WALLPAPER_ACCENT] = accent else this.remove(KEY_WALLPAPER_ACCENT)

        followWallpaperAccent(accent)
    }

    fun MutablePreferences.clearWallpaperLuma() {
        this.remove(KEY_WALLPAPER_LUMA)
        this.remove(KEY_WALLPAPER_ACCENT)

        followWallpaperAccent(null)
    }
}
