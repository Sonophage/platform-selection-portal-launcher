package com.psplauncher.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import com.psplauncher.themekit.AccentDeriver
import com.psplauncher.themekit.BmpImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkAccent @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = ConcurrentHashMap<String, Long>()

    suspend fun of(vararg candidates: String?): Long? =
        resolve(*candidates)?.accent

    suspend fun resolve(vararg candidates: String?): Resolved? = withContext(Dispatchers.IO) {
        for (uri in candidates) {
            if (uri.isNullOrBlank()) continue
            cache[uri]?.let { cached ->
                if (cached == UNREADABLE) continue
                return@withContext Resolved(uri, cached.takeIf { it != NO_HUE })
            }
            val bitmap = runCatching { decode(uri) }.getOrElse {
                Timber.d(it, "ArtworkAccent: read failed for $uri")
                null
            }
            if (bitmap == null) {
                Timber.d("ArtworkAccent: nothing decoded from $uri")
                cache[uri] = UNREADABLE
                continue
            }
            val accent = accentOf(bitmap, uri)
            cache[uri] = accent ?: NO_HUE
            return@withContext Resolved(uri, accent)
        }
        null
    }

    data class Resolved(val uri: String, val accent: Long?)

    suspend fun firstReadable(vararg candidates: String?): String? = resolve(*candidates)?.uri

    suspend fun isReadable(uri: String): Boolean = resolve(uri) != null

    private fun accentOf(bitmap: Bitmap, uri: String): Long? {
        return try {
            val scaled = downscale(bitmap, MAX_EDGE)
            AccentDeriver.deriveAccent(scaled.toBmpImage())
                ?.toUInt()?.toLong()
                .also {
                    if (scaled !== bitmap) scaled.recycle()
                    Timber.d("ArtworkAccent: %s -> %s", uri, it?.let { a -> "#%06X".format(a and 0xFFFFFF) } ?: "no hue")
                }
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(uri: String): Bitmap? {
        if (!uri.contains("://")) {
            val file = File(uri)
            return if (file.isFile) BitmapFactory.decodeFile(file.path) else null
        }
        return context.contentResolver.openInputStream(uri.toUri())
            ?.use { with(SafeMedia) { it.readCapped() } }
            ?.let { SafeMedia.decodeBitmapCapped(it) }
    }

    private fun downscale(src: Bitmap, maxEdge: Int): Bitmap {
        val edge = maxOf(src.width, src.height)
        if (edge <= maxEdge) return src
        val scale = maxEdge.toFloat() / edge
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun Bitmap.toBmpImage(): BmpImage {
        val px = IntArray(width * height)
        getPixels(px, 0, width, 0, 0, width, height)
        return BmpImage(width, height, px)
    }

    private companion object {
        const val MAX_EDGE = 512

        const val NO_HUE = 0L
        const val UNREADABLE = 1L
    }
}
