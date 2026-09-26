package com.psplauncher.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.data.wallpaper.ThemeAccent.KEY_ACCENT_OVERRIDE
import com.psplauncher.themekit.AccentDeriver
import com.psplauncher.themekit.PtfParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class PtfThemeImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PfpThemeStore,
) {
    sealed interface Result {
        data class Success(val themeName: String, val accentArgb: Long?) : Result

        data object CxmbNotSupported : Result

        data class Failed(val reason: String) : Result
    }

    suspend fun import(uri: Uri): Result = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { with(SafeMedia) { it.readCapped() } }
        }.getOrNull() ?: return@withContext Result.Failed("Could not read the file (or it is too large)")

        when (PtfParser.detect(bytes)) {
            PtfParser.Kind.NOT_PTF -> return@withContext Result.Failed("Not a PSP theme (.ptf) file")
            PtfParser.Kind.CXMB -> return@withContext Result.CxmbNotSupported
            PtfParser.Kind.OFFICIAL_PTF -> Unit
        }

        val theme = runCatching { PtfParser.parse(bytes) }
            .onFailure { Timber.w(it, "PTF parse threw on a malformed theme") }
            .getOrNull()
            ?: return@withContext Result.Failed("The theme file could not be parsed")
        val wallpaper = theme.wallpaper ?: return@withContext Result.Failed(
            when (theme.wallpaperStatus) {
                PtfParser.WallpaperStatus.MISSING -> "The theme has no wallpaper image"
                PtfParser.WallpaperStatus.UNSUPPORTED_COMPRESSION ->
                    "This theme compresses its wallpaper with a method that isn't supported"
                else -> "The theme's wallpaper is damaged and could not be decoded"
            },
        )

        val accent = AccentDeriver.deriveAccent(wallpaper)?.toUInt()?.toLong()
        val name = theme.name.ifBlank { "Imported PSP theme" }

        val saved = store.createFromPtf(
            name = name,
            wallpaper = wallpaper,
            accentArgb = accent,
            sourceFile = uri.lastPathSegment,
            firmware = theme.firmware.ifBlank { null },
        ) ?: return@withContext Result.Failed("Could not save the theme")

        if (!store.apply(saved.id)) {
            Timber.w("PTF import: saved but apply failed for %s", saved.id)
        }
        Result.Success(themeName = name, accentArgb = accent)
    }

    suspend fun clearAccentOverride() {
        context.pfpDataStore.edit { it.remove(KEY_ACCENT_OVERRIDE) }
    }

    private companion object {
    }
}
