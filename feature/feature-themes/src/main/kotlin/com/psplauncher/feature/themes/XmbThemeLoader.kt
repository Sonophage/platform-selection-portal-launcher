package com.psplauncher.feature.themes

import android.content.Context
import android.net.Uri
import com.psplauncher.core.archive.BoundedZipReader
import com.psplauncher.core.archive.ZipLimits
import com.psplauncher.core.data.database.dao.ThemeDao
import com.psplauncher.core.data.database.entity.ThemeEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

private val SAFE_THEME_ID = Regex("[A-Za-z0-9._-]{1,64}")

private val THEME_ZIP_LIMITS = ZipLimits(
    maxEntries    = 512,
    maxEntryBytes = 64L * 1024 * 1024,
    maxTotalBytes = 128L * 1024 * 1024,
)

sealed class ThemeLoadResult {
    data class Success(val themeId: String) : ThemeLoadResult()
    data class InvalidFormat(val reason: String) : ThemeLoadResult()
    data class UnsupportedVersion(val found: Int, val supported: Int) : ThemeLoadResult()
    data class IoError(val cause: Throwable) : ThemeLoadResult()
}

@Singleton
class XmbThemeLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themeDao: ThemeDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadFromUri(uri: Uri): ThemeLoadResult = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ThemeLoadResult.IoError(IOException("Cannot open URI: $uri"))
            loadFromStream(stream)
        } catch (e: Exception) {
            Timber.w(e, "IoError opening theme URI: $uri")
            ThemeLoadResult.IoError(e)
        }
    }

    suspend fun loadFromStream(stream: InputStream): ThemeLoadResult = withContext(Dispatchers.IO) {
        try {
            val entries = readZipEntries(stream)

            val manifestBytes = entries["theme.json"]
                ?: return@withContext ThemeLoadResult.InvalidFormat("Missing required theme.json in archive")

            val manifest = try {
                json.decodeFromString(XmbThemeManifest.serializer(), manifestBytes.decodeToString())
            } catch (e: Exception) {
                return@withContext ThemeLoadResult.InvalidFormat("Invalid theme.json: ${e.message}")
            }

            if (manifest.formatVersion > THEME_FORMAT_VERSION) {
                return@withContext ThemeLoadResult.UnsupportedVersion(
                    found     = manifest.formatVersion,
                    supported = THEME_FORMAT_VERSION,
                )
            }

            if (!SAFE_THEME_ID.matches(manifest.id)) {
                return@withContext ThemeLoadResult.InvalidFormat(
                    "Invalid theme id '${manifest.id}' — use letters, numbers, '.', '_' or '-' (max 64)"
                )
            }

            val waveColor = parseHexColor(manifest.waveColor)
                ?: return@withContext ThemeLoadResult.InvalidFormat(
                    "Invalid wave_color '${manifest.waveColor}' — expected #RRGGBB or #AARRGGBB"
                )
            val accentColor = parseHexColor(manifest.accentColor)
                ?: return@withContext ThemeLoadResult.InvalidFormat(
                    "Invalid accent_color '${manifest.accentColor}' — expected #RRGGBB or #AARRGGBB"
                )
            val textColor = parseHexColor(manifest.textColor)
                ?: return@withContext ThemeLoadResult.InvalidFormat(
                    "Invalid text_color '${manifest.textColor}' — expected #RRGGBB or #AARRGGBB"
                )

            val themeDir = File(context.filesDir, "themes/${manifest.id}")
            themeDir.mkdirs()

            val backgroundUri    = extractAsset(entries, "background.jpg",    themeDir, manifest.hasBackground)
            val bootAnimationUri = extractAsset(entries, "boot_animation.mp4", themeDir, manifest.hasBootAnimation)
            val soundPackUri     = extractSoundPack(entries, themeDir, manifest.hasSoundPack)

            val entity = ThemeEntity(
                id               = manifest.id,
                name             = manifest.name,
                author           = manifest.author,
                version          = manifest.version,
                waveColor        = waveColor,
                waveOpacity      = manifest.waveOpacity,
                waveSpeed        = manifest.waveSpeed,
                waveAmplitude    = manifest.waveAmplitude,
                accentColor      = accentColor,
                textColor        = textColor,
                backgroundUri    = backgroundUri,
                fontKey          = manifest.fontKey,
                hasBootAnimation = manifest.hasBootAnimation,
                bootAnimationUri = bootAnimationUri,
                soundPackUri     = soundPackUri,
                packagePath      = null,
                isBuiltIn        = false,
            )

            themeDao.upsert(entity)
            Timber.i("Theme installed: ${manifest.id} (${manifest.name})")
            ThemeLoadResult.Success(manifest.id)
        } catch (e: Exception) {
            Timber.w(e, "Unexpected error loading theme")
            ThemeLoadResult.IoError(e)
        }
    }

    private fun extractAsset(
        entries: Map<String, ByteArray>,
        fileName: String,
        themeDir: File,
        shouldExtract: Boolean,
    ): String? {
        if (!shouldExtract) return null
        val bytes = entries[fileName] ?: return null
        val dest = safeChild(themeDir, fileName) ?: return null
        dest.writeBytes(bytes)
        return dest.absolutePath
    }

    private fun extractSoundPack(
        entries: Map<String, ByteArray>,
        themeDir: File,
        shouldExtract: Boolean,
    ): String? {
        if (!shouldExtract) return null
        val soundEntries = entries.filterKeys { it.startsWith("sounds/") }
        if (soundEntries.isEmpty()) return null

        val soundsDir = File(themeDir, "sounds")
        soundsDir.mkdirs()
        soundEntries.forEach { (name, bytes) ->

            val dest = safeChild(themeDir, name) ?: return@forEach
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
        }
        return soundsDir.absolutePath
    }

    private fun safeChild(baseDir: File, relativePath: String): File? {
        val base = baseDir.canonicalFile
        val target = File(base, relativePath).canonicalFile
        val basePrefix = base.path + File.separator
        return if (target.path == base.path || target.path.startsWith(basePrefix)) {
            target
        } else {
            Timber.w("Rejected unsafe theme entry path: $relativePath")
            null
        }
    }

    private fun readZipEntries(stream: InputStream): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        BoundedZipReader.read(stream, THEME_ZIP_LIMITS) { entry ->
            if (!entry.isDirectory) map[entry.name] = entry.readBytes()
        }
        return map
    }
}
