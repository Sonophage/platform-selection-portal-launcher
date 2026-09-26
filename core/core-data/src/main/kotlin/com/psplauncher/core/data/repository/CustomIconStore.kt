package com.psplauncher.core.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.ui.icons.CustomIcon
import com.psplauncher.core.ui.icons.CustomIconLimits
import com.psplauncher.core.ui.icons.GifFrameProbe
import com.psplauncher.themekit.CustomizableIcons
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.asImageBitmap

@Singleton
class CustomIconStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheEvictor: CustomIconCacheEvictor,
) {
    data class ImportResult(val ok: Boolean, val message: String? = null)

    private val dir = File(context.filesDir, CUSTOM_ICONS_DIR)

    private val mimeForExtension = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "heif" to "image/heif",
        "gif" to "image/gif",
    )

    suspend fun import(slotKey: String, uri: Uri, mime: String?): ImportResult = withContext(Dispatchers.IO) {
        if (!CustomizableIcons.isValidKey(slotKey)) {
            return@withContext ImportResult(false, "Not a customizable icon slot")
        }
        val ext = mimeToExtension(mime)
        if (ext == null) {
            return@withContext ImportResult(false, CustomIconLimits.MSG_UNSUPPORTED_FORMAT)
        }

        val knownSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it > 0 }
        if (knownSize != null && knownSize > CustomIconLimits.MAX_BYTES) {
            return@withContext ImportResult(false, CustomIconLimits.MSG_TOO_LARGE_BYTES)
        }

        dir.mkdirs()
        val staged = File(dir, "staging_${System.currentTimeMillis()}.$ext")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->

                val bytes = with(SafeMedia) { input.readCapped(CustomIconLimits.MAX_BYTES) }
                if (bytes == null) {
                    null
                } else {
                    staged.writeBytes(bytes)
                    bytes.size.toLong()
                }
            }
        }.getOrNull()
        if (copied == null) {
            runCatching { staged.delete() }
            return@withContext ImportResult(false, CustomIconLimits.MSG_UNDECODABLE)
        }

        val rejection = validateImported(staged, ext, copied)
        if (rejection != null) {
            runCatching { staged.delete() }
            return@withContext ImportResult(false, rejection)
        }

        for ((candidateExt, _) in mimeForExtension) {
            if (candidateExt != ext) File(dir, "$slotKey.$candidateExt").delete()
        }
        val dest = File(dir, "$slotKey.$ext")
        if (!staged.renameTo(dest)) {
            staged.copyTo(dest, overwrite = true)
            staged.delete()
        }
        cacheEvictor.evict(dest.absolutePath)
        context.pfpDataStore.edit { prefs -> prefs[KEY_CUSTOM_ICONS_STAMP] = System.currentTimeMillis() }
        ImportResult(true)
    }

    suspend fun clear(slotKey: String): Boolean = withContext(Dispatchers.IO) {
        if (!CustomizableIcons.isValidKey(slotKey)) return@withContext false
        val removed = mimeForExtension.keys.any { ext -> File(dir, "$slotKey.$ext").delete() }
        if (removed) {
            context.pfpDataStore.edit { prefs -> prefs[KEY_CUSTOM_ICONS_STAMP] = System.currentTimeMillis() }
        }
        removed
    }

    suspend fun clearAll(): Boolean = withContext(Dispatchers.IO) {
        val had = dir.listFiles { f -> f.isFile }.orEmpty().isNotEmpty()
        context.pfpDataStore.edit { prefs -> prefs.remove(KEY_CUSTOM_ICONS_STAMP) }
        dir.deleteRecursively()
        had
    }

    suspend fun load(): Map<String, CustomIcon> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.isFile }.orEmpty().mapNotNull { file ->
            val key = file.nameWithoutExtension
            if (!CustomizableIcons.isValidKey(key)) return@mapNotNull null
            val ext = file.extension.lowercase()
            if (ext !in mimeForExtension) return@mapNotNull null

            val bitmap = SafeMedia.decodeFileCapped(
                file.absolutePath,
                maxDimension = DECODE_MAX_DIMENSION,
                targetDimension = DECODE_TARGET_DIMENSION,
            ) ?: return@mapNotNull null
            val firstFrame = bitmap.asImageBitmap()

            val icon = if (ext == "gif" && GifFrameProbe.countFrames(file) > 1) {
                CustomIcon.Animated(path = file.absolutePath, firstFrame = firstFrame)
            } else {
                CustomIcon.Still(firstFrame)
            }
            key to icon
        }.toMap()
    }

    private fun validateImported(file: File, ext: String, bytes: Long): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val probe = CustomIconLimits.Probe(
            mime = mimeForExtension[ext],
            width = bounds.outWidth,
            height = bounds.outHeight,

            frameCount = null,
            durationMs = null,
            bytes = bytes,
        )
        return CustomIconLimits.validate(probe)
    }

    private fun mimeToExtension(mime: String?): String? = when (mime?.lowercase()) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "image/bmp" -> "bmp"
        "image/heif" -> "heif"
        "image/gif" -> "gif"
        else -> null
    }

    companion object {
        const val CUSTOM_ICONS_DIR = "custom-icons"

        val KEY_CUSTOM_ICONS_STAMP = longPreferencesKey("custom_icons_stamp")

        private const val DECODE_MAX_DIMENSION = 8192
        private const val DECODE_TARGET_DIMENSION = 512
    }
}
