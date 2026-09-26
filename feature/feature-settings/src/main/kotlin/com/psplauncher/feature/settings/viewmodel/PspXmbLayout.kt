package com.psplauncher.feature.settings.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psplauncher.themekit.XmbFormFactor
import com.psplauncher.themekit.XmbLayoutAdjust
import com.psplauncher.themekit.XmbLayoutAdjustCodec
import com.psplauncher.themekit.XmbLayoutPreset

internal object PspXmbLayout {
    val KEY = stringPreferencesKey("display_xmb_layout_adjust")

    data class Target(val bucketKey: String, val preset: XmbLayoutAdjust)

    fun forWindow(context: Context): Target {
        val config = context.resources.configuration
        return Target(
            bucketKey = XmbFormFactor.forSmallestWidthDp(config.smallestScreenWidthDp).key,
            preset = XmbLayoutPreset.computeForWindowDp(
                widthDp = config.screenWidthDp.toFloat(),
                heightDp = config.screenHeightDp.toFloat(),
                density = context.resources.displayMetrics.density,
            ),
        )
    }

    fun isApplied(prefs: Preferences, target: Target): Boolean =
        XmbLayoutPreset.matches(XmbLayoutAdjustCodec.decode(prefs[KEY])[target.bucketKey], target.preset)

    fun write(prefs: MutablePreferences, target: Target) {
        val map = XmbLayoutAdjustCodec.decode(prefs[KEY]).toMutableMap()
        map[target.bucketKey] = target.preset
        prefs[KEY] = XmbLayoutAdjustCodec.encode(map)
    }
}
