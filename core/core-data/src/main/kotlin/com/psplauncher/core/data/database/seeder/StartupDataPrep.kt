package com.psplauncher.core.data.database.seeder

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.data.wallpaper.WallpaperLuminanceProbe
import com.psplauncher.core.data.wallpaper.WallpaperLuminanceProbe.clearWallpaperLuma
import com.psplauncher.core.data.wallpaper.WallpaperLuminanceProbe.setWallpaperLuma
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupDataPrep @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
) {
    suspend fun run(currentVersionCode: Int) {
        val prefs = context.pfpDataStore.data.first()
        val alreadyPrepped = prefs[KEY_DATA_PREP_VERSION] == currentVersionCode

        runCatching {
            if (!alreadyPrepped) {
                normalizeGameArtwork()
                normalizeWallpaper()
            }

            healWallpaperSurvey()
        }.onFailure { Timber.e(it, "Startup data prep failed") }

        if (alreadyPrepped) return
        context.pfpDataStore.edit { it[KEY_DATA_PREP_VERSION] = currentVersionCode }
        Timber.i("Startup data prep complete for versionCode=$currentVersionCode")
    }

    private suspend fun normalizeGameArtwork() {
        val filesDir = context.filesDir.absolutePath
        var repaired = 0
        gameDao.getAll().forEach { g ->
            val artwork = resolve(g.artworkUri, filesDir)
            val hero    = resolve(g.heroUri, filesDir)
            val logo    = resolve(g.logoUri, filesDir)
            val icon    = resolve(g.iconUri, filesDir)

            if (artwork != g.artworkUri) gameDao.updateArtwork(g.id, artwork)
            if (hero    != g.heroUri)    gameDao.updateHero(g.id, hero)
            if (logo    != g.logoUri)    gameDao.updateLogo(g.id, logo)
            if (icon    != g.iconUri)    gameDao.updateIconUri(g.id, icon)

            if (artwork != g.artworkUri || hero != g.heroUri || logo != g.logoUri || icon != g.iconUri) {
                repaired++
            }
        }
        if (repaired > 0) Timber.i("Re-homed artwork paths on $repaired game(s)")
    }

    private suspend fun normalizeWallpaper() {
        val filesDir = context.filesDir.absolutePath
        val current = context.pfpDataStore.data.first()[KEY_CUSTOM_WALLPAPER] ?: return
        val resolved = resolve(current, filesDir)
        if (resolved == current) return
        context.pfpDataStore.edit { prefs ->
            if (resolved == null) prefs.remove(KEY_CUSTOM_WALLPAPER)
            else prefs[KEY_CUSTOM_WALLPAPER] = resolved
        }
    }

    private suspend fun healWallpaperSurvey() {
        val prefs = context.pfpDataStore.data.first()
        val wallpaper = prefs[KEY_CUSTOM_WALLPAPER]
        val storedLuma = prefs[WallpaperLuminanceProbe.KEY_WALLPAPER_LUMA]
        val storedAccent = prefs[WallpaperLuminanceProbe.KEY_WALLPAPER_ACCENT]

        if (wallpaper == null) {
            if (storedLuma != null || storedAccent != null) {
                context.pfpDataStore.edit { it.clearWallpaperLuma() }
            }
            return
        }

        if (WallpaperLuminanceProbe.describes(storedLuma, wallpaper) && storedAccent != null) return

        val fresh = WallpaperLuminanceProbe.survey(wallpaper)
        if (fresh?.luma == storedLuma && fresh?.accentArgb == storedAccent) return
        context.pfpDataStore.edit { it.setWallpaperLuma(fresh) }
    }

    private fun resolve(path: String?, filesDirPath: String): String? {
        if (path.isNullOrEmpty()) return path
        val idx = path.indexOf(FILES_MARKER)
        if (idx < 0) return path
        val remapped = filesDirPath.trimEnd('/') + "/" + path.substring(idx + FILES_MARKER.length)
        return if (File(remapped).exists()) remapped else null
    }

    private companion object {
        val KEY_DATA_PREP_VERSION = intPreferencesKey("data_prep_version")
        val KEY_CUSTOM_WALLPAPER  = stringPreferencesKey("display_custom_wallpaper")
        const val FILES_MARKER = "/files/"
    }
}
