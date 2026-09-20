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
    suspend fun of(vararg candidates: String?): Long? =
        resolve(*candidates)?.accent

    /**
     * The first candidate that actually decodes, with its colour.
     *
     * The URI matters as much as the colour. On this library 125 of 147 games carry an
     * artwork_uri pointing into the app's internal artwork store, and that store is empty -- the
     * art lives in the ES-DE tree those games' heroUri points at. Anything that reads the first
     * NAMED candidate gets a path to nothing; anything that reads the first READABLE one gets the
     * picture. Both the colour and the backdrop have to make that choice the same way, so they
     * make it here, once.
     *
     * A readable image with no dominant hue returns with a null [accent]: it is still the right
     * image to show, it just has no colour to offer.
     */
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

    /** A readable image and, if it had one, its colour. */
    data class Resolved(val uri: String, val accent: Long?)

    /** The first candidate that decodes, without caring what colour it is. */
    suspend fun firstReadable(vararg candidates: String?): String? = resolve(*candidates)?.uri

    /** Whether this one image decodes. Shares the same cache as [resolve], so it is free twice. */
    suspend fun isReadable(uri: String): Boolean = resolve(uri) != null

    private fun accentOf(bitmap: Bitmap, uri: String): Long? {
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

        // Two different cached negatives, because they mean opposite things to a caller looking
        // for a backdrop: NO_HUE is a picture worth showing that happens to be greyscale,
        // UNREADABLE is a path with nothing behind it. Neither can collide with a real result,
        // which is always opaque (0xFF......) because AccentDeriver rebuilds the colour from HSV.
        const val NO_HUE = 0L
        const val UNREADABLE = 1L
    }
}
