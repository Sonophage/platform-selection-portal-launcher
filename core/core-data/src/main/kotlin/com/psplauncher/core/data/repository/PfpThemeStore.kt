package com.psplauncher.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.data.wallpaper.WallpaperLuminanceProbe
import com.psplauncher.core.data.wallpaper.WallpaperLuminanceProbe.clearWallpaperLuma
import com.psplauncher.core.data.wallpaper.ThemeAccent
import com.psplauncher.core.data.wallpaper.ThemeAccent.KEY_ACCENT_OVERRIDE
import com.psplauncher.core.data.wallpaper.WallpaperLuminanceProbe.setWallpaperLuma
import com.psplauncher.themekit.AccentDeriver
import com.psplauncher.themekit.BmpImage
import com.psplauncher.themekit.CustomizableIcons
import com.psplauncher.themekit.PfpThemeBundle
import com.psplauncher.themekit.PfpThemeCodec
import com.psplauncher.themekit.PfpThemeManifest
import com.psplauncher.themekit.PfpThemeSource
import com.psplauncher.themekit.ThemeImage
import com.psplauncher.themekit.ThemeMotion
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class PfpThemeStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class SavedTheme(
        val id: String,
        val name: String,
        val accentArgb: Long?,

        val previewPath: String?,
    )

    private val dir = File(context.filesDir, "pfpthemes")

    private val _themes = MutableStateFlow(scan())
    val themes: StateFlow<List<SavedTheme>> = _themes.asStateFlow()

    suspend fun createFromImage(uri: Uri, name: String? = null): SavedTheme? = withContext(Dispatchers.IO) {
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)
                ?.use { with(SafeMedia) { it.readCapped() } }
                ?.let { SafeMedia.decodeBitmapCapped(it) }
        }.getOrNull() ?: return@withContext null

        val scaled = downscale(bitmap, maxEdge = 1920)
        val accent = AccentDeriver.deriveAccent(scaled.toBmpImage())?.toUInt()?.toLong()
        val themeName = name ?: nextDefaultName()
        save(
            name = themeName,
            wallpaper = scaled,
            accentArgb = accent,
            source = PfpThemeSource(type = PfpThemeSource.TYPE_USER_CREATED),
        ).also { if (scaled !== bitmap) bitmap.recycle() }
    }

    suspend fun createFromPtf(name: String, wallpaper: BmpImage, accentArgb: Long?, sourceFile: String?, firmware: String?): SavedTheme? =
        withContext(Dispatchers.IO) {
            val bitmap = Bitmap.createBitmap(wallpaper.width, wallpaper.height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(wallpaper.argb, 0, wallpaper.width, 0, 0, wallpaper.width, wallpaper.height)
            save(
                name = name,
                wallpaper = bitmap,
                accentArgb = accentArgb,
                source = PfpThemeSource(type = PfpThemeSource.TYPE_PTF_IMPORT, file = sourceFile, firmware = firmware),
            )
        }

    suspend fun apply(id: String): Boolean = withContext(Dispatchers.IO) {
        val wallpaperSidecar = File(dir, "$id.wallpaper.jpg")

        val bundle = runCatching { PfpThemeCodec.read(File(dir, "$id.pfptheme")) }.getOrNull()
            ?: return@withContext false

        val destDir = File(context.filesDir, "wallpaper").apply { mkdirs() }
        val dest = File(destDir, "wallpaper_theme_${System.currentTimeMillis()}.jpg")
        val wallpaperOk = wallpaperSidecar.isFile && runCatching { wallpaperSidecar.copyTo(dest, overwrite = true) }.isSuccess

        val iconsDir = File(context.filesDir, THEME_ICONS_DIR)
        iconsDir.deleteRecursively()
        val iconEntries: Map<String, com.psplauncher.themekit.ThemeImage> = buildMap {
            putAll(bundle.icons)
            for ((platformId, image) in bundle.sysicons) put("sysicon_$platformId", image)
        }
        if (iconEntries.isNotEmpty()) {
            iconsDir.mkdirs()

            for ((key, image) in iconEntries) {
                File(iconsDir, "$key.${image.extension.lowercase()}").writeBytes(image.bytes)
            }
        }

        val motionDest = bundle.motion?.let { motion ->
            runCatching {
                val motionDir = File(context.filesDir, "wallpaper").apply { mkdirs() }
                val dest = File(motionDir, "wallpaper_theme_${System.currentTimeMillis()}.${motion.extension.lowercase()}")

                FileOutputStream(dest).use { out -> motion.copyTo(out) }
                dest
            }.onFailure {
                Timber.w(it, "PfpThemeStore: could not extract the motion wallpaper")
            }.getOrNull()
        }

        val accent = bundle.manifest.accentColor.toAccentArgbOrNull()

        val iconColor = bundle.manifest.iconColor
            .takeIf { it != PfpThemeManifest.ICON_COLOR_AUTO }
            ?.toAccentArgbOrNull()

        val textColor = bundle.manifest.textColor
            .takeIf { it != PfpThemeManifest.ICON_COLOR_AUTO }
            ?.toAccentArgbOrNull()

        val layoutJson = bundle.manifest.layout
            ?.let(com.psplauncher.themekit.XmbLayoutSpecCodec::sanitize)
            ?.takeUnless { it == com.psplauncher.themekit.XmbLayoutSpec.DEFAULT }
            ?.let(com.psplauncher.themekit.XmbLayoutSpecCodec::encode)

        val waveStyle = when (bundle.manifest.waveStyle) {
            PfpThemeManifest.WAVE_STATIC -> WAVE_STYLE_STATIC
            PfpThemeManifest.WAVE_REDUCED -> WAVE_STYLE_REDUCED
            else -> WAVE_STYLE_ANIMATED
        }
        val appliedName = _themes.value.firstOrNull { it.id == id }?.name ?: "Custom Theme"

        val luma = if (wallpaperOk) WallpaperLuminanceProbe.survey(dest.absolutePath) else null
        context.pfpDataStore.edit { prefs ->
            prefs[KEY_APPLIED_THEME_NAME] = appliedName
            prefs[KEY_WAVE_STYLE] = waveStyle

            if (motionDest != null) prefs[KEY_MOTION_WALLPAPER] = motionDest.absolutePath else prefs.remove(KEY_MOTION_WALLPAPER)
            if (wallpaperOk) prefs[KEY_CUSTOM_WALLPAPER] = dest.absolutePath else prefs.remove(KEY_CUSTOM_WALLPAPER)
            prefs.setWallpaperLuma(luma)

            if (accent != null) prefs[KEY_ACCENT_OVERRIDE] = accent else prefs.remove(KEY_ACCENT_OVERRIDE)
            if (iconColor != null) prefs[KEY_ICON_COLOR] = iconColor else prefs.remove(KEY_ICON_COLOR)
            if (textColor != null) prefs[KEY_TEXT_COLOR] = textColor else prefs.remove(KEY_TEXT_COLOR)
            if (layoutJson != null) prefs[KEY_THEME_LAYOUT] = layoutJson else prefs.remove(KEY_THEME_LAYOUT)
            if (iconEntries.isNotEmpty()) {
                prefs[KEY_THEME_ICONS_STAMP] = System.currentTimeMillis()
            } else {
                prefs.remove(KEY_THEME_ICONS_STAMP)
            }
        }
        true
    }

    suspend fun resetApplied(): Unit = withContext(Dispatchers.IO) {
        context.pfpDataStore.edit { prefs ->
            prefs.remove(KEY_CUSTOM_WALLPAPER)
            prefs.remove(KEY_MOTION_WALLPAPER)
            prefs.clearWallpaperLuma()
            prefs.remove(KEY_ACCENT_OVERRIDE)

            prefs.remove(ThemeAccent.KEY_ACCENT_FROM_WALLPAPER)
            prefs.remove(KEY_ICON_COLOR)
            prefs.remove(KEY_TEXT_COLOR)
            prefs.remove(KEY_WAVE_STYLE)
            prefs.remove(KEY_THEME_LAYOUT)
            prefs.remove(KEY_THEME_ICONS_STAMP)
            prefs.remove(KEY_APPLIED_THEME_NAME)
        }

        File(context.filesDir, THEME_ICONS_DIR).deleteRecursively()
        File(context.filesDir, "wallpaper").listFiles()?.forEach { it.delete() }
    }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        listOf("$id.pfptheme", "$id.preview.jpg", "$id.wallpaper.jpg")
            .forEach { File(dir, it).delete() }
        _themes.value = scan()
    }

    suspend fun exportForShare(id: String): File? = withContext(Dispatchers.IO) {
        val src = File(dir, "$id.pfptheme")
        if (!src.isFile) return@withContext null
        val name = _themes.value.firstOrNull { it.id == id }?.name ?: id
        val safe = name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { id }.replace(' ', '_')
        runCatching {
            val out = File(File(context.cacheDir, "shared_themes").apply { mkdirs() }, "$safe.pfptheme")
            src.copyTo(out, overwrite = true)
            out
        }.onFailure { Timber.w(it, "PfpThemeStore: export failed") }.getOrNull()
    }

    sealed interface ImportResult {
        data class Success(val theme: SavedTheme) : ImportResult

        data class Unreadable(val cause: Throwable?) : ImportResult

        data object TooLarge : ImportResult

        data object OutOfMemory : ImportResult

        data object NotABundle : ImportResult

        data object DamagedWallpaper : ImportResult

        data class NotSaved(val cause: Throwable?) : ImportResult
    }

    suspend fun importBundle(uri: Uri): SavedTheme? =
        (importBundleDetailed(uri) as? ImportResult.Success)?.theme

    suspend fun importBundleDetailed(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val staging = File(dir, "import_${System.currentTimeMillis()}.tmp")
        val copied = try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                Timber.w("PfpThemeStore: no stream for %s", uri)
                return@withContext ImportResult.Unreadable(null)
            }
            stream.use { input ->
                FileOutputStream(staging).use { out -> with(SafeMedia) { input.copyCappedTo(out) } }
            }
        } catch (e: Exception) {
            staging.delete()
            Timber.w(e, "PfpThemeStore: could not read the bundle")
            return@withContext ImportResult.Unreadable(e)
        }
        if (copied == null) {
            staging.delete()
            Timber.w(
                "PfpThemeStore: bundle exceeds the %d-byte read cap",
                SafeMedia.MAX_THEME_FILE_BYTES,
            )
            return@withContext ImportResult.TooLarge
        }

        val parsed = try {
            PfpThemeCodec.read(staging)
        } catch (e: OutOfMemoryError) {
            staging.delete()
            Timber.w(e, "PfpThemeStore: out of heap parsing a %d-byte bundle", copied)
            return@withContext ImportResult.OutOfMemory
        }
        val bundle = parsed ?: run {
            staging.delete()
            Timber.w("PfpThemeStore: %d bytes are not a .pfptheme bundle", copied)
            return@withContext ImportResult.NotABundle
        }

        val wallpaper = bundle.wallpaper?.let {
            SafeMedia.decodeBitmapCapped(it) ?: run {
                staging.delete()

                Timber.w("PfpThemeStore: wallpaper is %d bytes but would not decode", it.size)
                return@withContext ImportResult.DamagedWallpaper
            }
        }

        val saved = runCatching {
            val id = "pfp_${System.currentTimeMillis()}"
            val name = bundle.manifest.name.ifBlank { nextDefaultName() }

            val stored = File(dir, "$id.pfptheme")
            if (!staging.renameTo(stored)) {
                staging.copyTo(stored, overwrite = true)
                staging.delete()
            }

            wallpaper?.let { wp ->
                FileOutputStream(File(dir, "$id.wallpaper.jpg")).use { wp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            }

            val preview = bundle.preview?.let { SafeMedia.decodeBitmapCapped(it) }
                ?: wallpaper?.let { downscale(it, maxEdge = 480) }
            preview?.let { p ->
                try {
                    FileOutputStream(File(dir, "$id.preview.jpg")).use { p.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                } finally {
                    if (p !== wallpaper) p.recycle()
                }
            }
            _themes.value = scan()
            SavedTheme(
                id,
                name,
                bundle.manifest.accentColor.toAccentArgbOrNull(),
                File(dir, "$id.preview.jpg").takeIf { it.isFile }?.absolutePath,
            )
        }
        wallpaper?.recycle()

        if (staging.exists()) staging.delete()
        saved.fold(
            onSuccess = { ImportResult.Success(it) },
            onFailure = { cause ->
                Timber.w(cause, "PfpThemeStore: bundle parsed but could not be saved")
                if (cause is OutOfMemoryError) ImportResult.OutOfMemory
                else ImportResult.NotSaved(cause)
            },
        )
    }

    suspend fun saveCurrentLook(name: String): SavedTheme? = withContext(Dispatchers.IO) {
        val prefs = context.pfpDataStore.data.first()

        val customDir = File(context.filesDir, CustomIconStore.CUSTOM_ICONS_DIR)
        val themeIconsDir = File(context.filesDir, THEME_ICONS_DIR)
        val icons = mutableMapOf<String, ThemeImage>()
        val sysicons = mutableMapOf<String, ThemeImage>()
        for (slot in CustomizableIcons.ALL) {
            val source = findIconFile(customDir, slot.key) ?: findIconFile(themeIconsDir, slot.key) ?: continue
            if (source.extension.equals("gif", ignoreCase = true)) {
                iconsOrSysicons(slot.key, ThemeImage(source.readBytes(), "gif"), icons, sysicons)
            } else {
                val bitmap = SafeMedia.decodeFileCapped(source.absolutePath, maxDimension = 512)
                    ?: continue
                val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                bitmap.recycle()
                iconsOrSysicons(slot.key, ThemeImage(png, "png"), icons, sysicons)
            }
        }

        val wallpaperBitmap = prefs[KEY_CUSTOM_WALLPAPER]
            ?.let { runCatching { SafeMedia.decodeFileCapped(it, maxDimension = 1920) }.getOrNull() }
        val wallpaperPng = wallpaperBitmap?.let {
            ByteArrayOutputStream().also { out -> it.compress(Bitmap.CompressFormat.PNG, 100, out) }.toByteArray()
        }

        val motion = prefs[KEY_MOTION_WALLPAPER]
            ?.let { path ->
                val file = File(path)
                val ext = file.extension.lowercase()
                if (file.isFile && ext in setOf("mp4", "webm", "gif")) ThemeMotion.ofFile(file, ext) else null
            }

        val manifest = PfpThemeManifest(
            name = name.ifBlank { nextDefaultName() },
            accentColor = prefs[KEY_ACCENT_OVERRIDE]?.let { "#%06X".format(it and 0xFFFFFF) } ?: "",
            iconColor = prefs[KEY_ICON_COLOR]?.let { "#%06X".format(it and 0xFFFFFF) }
                ?: PfpThemeManifest.ICON_COLOR_AUTO,
            textColor = prefs[KEY_TEXT_COLOR]?.let { "#%06X".format(it and 0xFFFFFF) }
                ?: PfpThemeManifest.ICON_COLOR_AUTO,
            waveStyle = when (prefs[KEY_WAVE_STYLE]) {
                WAVE_STYLE_STATIC -> PfpThemeManifest.WAVE_STATIC
                WAVE_STYLE_REDUCED -> PfpThemeManifest.WAVE_REDUCED
                else -> PfpThemeManifest.WAVE_ANIMATED
            },
            layout = prefs[KEY_THEME_LAYOUT]
                ?.let { com.psplauncher.themekit.XmbLayoutSpecCodec.decode(it) }
                ?.let(com.psplauncher.themekit.XmbLayoutSpecCodec::sanitize)
                ?.takeUnless { it == com.psplauncher.themekit.XmbLayoutSpec.DEFAULT },
            source = PfpThemeSource(type = PfpThemeSource.TYPE_USER_CREATED),
            created = LocalDate.now().toString(),
        )

        val preview = wallpaperBitmap?.let { downscale(it, maxEdge = 480) }
        val previewBytes = preview?.let {
            ByteArrayOutputStream().also { out -> it.compress(Bitmap.CompressFormat.PNG, 90, out) }.toByteArray()
        }

        return@withContext runCatching {
            dir.mkdirs()
            val id = "pfp_${System.currentTimeMillis()}"

            FileOutputStream(File(dir, "$id.pfptheme")).use { out ->
                PfpThemeCodec.write(
                    PfpThemeBundle(
                        manifest = manifest,
                        wallpaper = wallpaperPng,
                        preview = previewBytes,
                        icons = icons,
                        sysicons = sysicons,
                        motion = motion,
                    ),
                    out,
                )
            }
            wallpaperBitmap?.let {
                FileOutputStream(File(dir, "$id.wallpaper.jpg")).use { out -> it.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            }
            preview?.let {
                FileOutputStream(File(dir, "$id.preview.jpg")).use { out -> it.compress(Bitmap.CompressFormat.JPEG, 88, out) }
                if (it !== wallpaperBitmap) it.recycle()
            }
            wallpaperBitmap?.recycle()

            _themes.value = scan()
            SavedTheme(
                id,
                manifest.name,
                manifest.accentColor.toAccentArgbOrNull(),
                File(dir, "$id.preview.jpg").takeIf { f -> f.isFile }?.absolutePath,
            )
        }.onFailure { Timber.w(it, "PfpThemeStore: saveCurrentLook failed") }.getOrNull()
    }

    private fun iconsOrSysicons(
        slotKey: String,
        image: ThemeImage,
        icons: MutableMap<String, ThemeImage>,
        sysicons: MutableMap<String, ThemeImage>,
    ) {
        if (slotKey.startsWith("sysicon_")) sysicons[slotKey.removePrefix("sysicon_")] = image
        else icons[slotKey] = image
    }

    private fun findIconFile(dir: File, slotKey: String): File? =
        setOf("png", "jpg", "webp", "bmp", "heif", "gif")
            .asSequence()
            .map { File(dir, "$slotKey.$it") }
            .firstOrNull { it.isFile }

    private fun save(name: String, wallpaper: Bitmap, accentArgb: Long?, source: PfpThemeSource): SavedTheme? {
        return runCatching {
            dir.mkdirs()
            val id = "pfp_${System.currentTimeMillis()}"

            val manifest = PfpThemeManifest(
                name = name,
                accentColor = accentArgb?.let { "#%06X".format(it and 0xFFFFFF) } ?: "",
                source = source,
                created = LocalDate.now().toString(),
            )
            val wallpaperPng = ByteArrayOutputStream()
                .also { wallpaper.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

            val preview = downscale(wallpaper, maxEdge = 480)
            val previewBytes = ByteArrayOutputStream()
                .also { preview.compress(Bitmap.CompressFormat.PNG, 90, it) }.toByteArray()

            FileOutputStream(File(dir, "$id.pfptheme")).use { out ->
                PfpThemeCodec.write(PfpThemeBundle(manifest, wallpaperPng, previewBytes), out)
            }
            FileOutputStream(File(dir, "$id.wallpaper.jpg")).use { wallpaper.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            FileOutputStream(File(dir, "$id.preview.jpg")).use { preview.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (preview !== wallpaper) preview.recycle()

            _themes.value = scan()
            SavedTheme(id, name, accentArgb, File(dir, "$id.preview.jpg").absolutePath)
        }.onFailure { Timber.w(it, "PfpThemeStore: save failed") }.getOrNull()
    }

    private fun scan(): List<SavedTheme> =
        dir.listFiles { f -> f.name.endsWith(".pfptheme") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .mapNotNull { file ->
                val id = file.name.removeSuffix(".pfptheme")

                val manifest = runCatching { PfpThemeCodec.readManifest(file) }
                    .onFailure { Timber.w(it, "PfpThemeStore: could not read %s", file.name) }
                    .getOrNull() ?: return@mapNotNull null
                SavedTheme(
                    id = id,
                    name = manifest.name,
                    accentArgb = manifest.accentColor.toAccentArgbOrNull(),
                    previewPath = File(dir, "$id.preview.jpg").takeIf { it.isFile }?.absolutePath,
                )
            }

    private fun nextDefaultName(): String {
        val existing = _themes.value.map { it.name }.toSet()
        var n = 1
        while ("Custom Theme $n" in existing) n++
        return "Custom Theme $n"
    }

    private fun downscale(src: Bitmap, maxEdge: Int): Bitmap {
        val edge = maxOf(src.width, src.height)
        if (edge <= maxEdge) return src
        val scale = maxEdge.toFloat() / edge
        return Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun Bitmap.toBmpImage(): BmpImage {
        val px = IntArray(width * height)
        getPixels(px, 0, width, 0, 0, width, height)
        return BmpImage(width, height, px)
    }

    private fun String.toAccentArgbOrNull(): Long? {
        val hex = removePrefix("#")
        if (hex.length != 6) return null
        return hex.toLongOrNull(16)?.let { 0xFF000000L or it }
    }

    companion object {
        private val KEY_CUSTOM_WALLPAPER = stringPreferencesKey("display_custom_wallpaper")

        private val KEY_MOTION_WALLPAPER = stringPreferencesKey("display_motion_wallpaper")
        private val KEY_WAVE_STYLE = stringPreferencesKey("display_wave_style")
        private val KEY_ICON_COLOR = longPreferencesKey("theme_icon_color")

        private val KEY_TEXT_COLOR = longPreferencesKey("display_text_color")

        private const val WAVE_STYLE_ANIMATED = "ANIMATED"
        private const val WAVE_STYLE_REDUCED = "REDUCED"
        private const val WAVE_STYLE_STATIC = "STATIC"

        const val THEME_ICONS_DIR = "theme-icons"

        val KEY_THEME_ICONS_STAMP = longPreferencesKey("theme_icons_stamp")

        val KEY_THEME_LAYOUT = stringPreferencesKey("theme_layout_spec")

        val KEY_APPLIED_THEME_NAME = stringPreferencesKey("theme_applied_name")
    }
}
