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

/**
 * The accent colour of a piece of artwork: the same dominant-hue derivation that turns a photo
 * into a theme, pointed at a game's own art so a detail page can wear the game's colour instead
 * of the user's scheme.
 *
 * Deliberately the SAME [AccentDeriver] the theme path uses, not a second colour algorithm. Two
 * derivations would mean a game's page and a theme made from the same image disagreeing about
 * what colour that image is, which is the kind of difference nobody can see is a bug.
 *
 * Null means "no opinion", not an error: a greyscale box shot has no saturated hue to find, and
 * the caller keeps the user's theme. Failure to read the file lands in the same place, logged.
 */
@Singleton
class ArtworkAccent @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // Decoding is tens of milliseconds and the same art is asked for on every recomposition of
    // every detail page, so the answer is kept. Bounded by the artwork the user actually opens.
    private val cache = ConcurrentHashMap<String, Long>()

    /**
     * The accent for [uri] (a `content://` or `file://` ref, or a bare path), or null when the
     * image has no saturated hue or cannot be read.
     *
     * [candidates] are tried in order and the first that yields a colour wins, so a caller can
     * say "the hero, else the box art, else the icon" without writing the fallback itself.
     */
    suspend fun of(vararg candidates: String?): Long? = withContext(Dispatchers.IO) {
        for (uri in candidates) {
            if (uri.isNullOrBlank()) continue
            cache[uri]?.let { return@withContext it.takeIf { c -> c != NONE } }
            val derived = derive(uri)
            cache[uri] = derived ?: NONE
            if (derived != null) return@withContext derived
        }
        null
    }

    private fun derive(uri: String): Long? {
        // Both failure shapes are logged, because they look identical from the outside: a null
        // decode (unreadable uri, revoked grant, oversize header) throws nothing at all, so
        // without this line an image that simply never opened is indistinguishable from one
        // that opened and turned out to be greyscale.
        val bitmap = runCatching { decode(uri) }.getOrElse {
            Timber.d(it, "ArtworkAccent: read failed for $uri")
            null
        } ?: run {
            Timber.d("ArtworkAccent: nothing decoded from $uri")
            return null
        }
        return try {
            // Small on purpose: the deriver stride-samples ~6000 pixels anyway, so a bigger
            // decode buys nothing but heap.
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
        // A stored artwork ref is a content:// uri for SAF-backed art and a plain path for the
        // internal store, so both shapes have to work here.
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

        // A cached "this image has no accent". Cannot collide with a real result, which is always
        // opaque (0xFF......) because AccentDeriver rebuilds the colour from HSV.
        const val NONE = 0L
    }
}
