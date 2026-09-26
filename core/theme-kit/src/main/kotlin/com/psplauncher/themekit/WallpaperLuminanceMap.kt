package com.psplauncher.themekit

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.sqrt

@Serializable
data class LuminanceBand(

    val mean: Float,

    val p90: Float,
)

@Serializable
data class WallpaperLuminanceMap(
    val source: String,
    val bands: List<LuminanceBand>,
) {
    init {
        require(bands.size == ROWS * ZONES) { "expected ${ROWS * ZONES} bands, got ${bands.size}" }
    }

    fun bandAt(xFraction: Float, yFraction: Float): LuminanceBand {
        val zone = (xFraction.coerceIn(0f, 1f) * ZONES).toInt().coerceAtMost(ZONES - 1)
        val row = (yFraction.coerceIn(0f, 1f) * ROWS).toInt().coerceAtMost(ROWS - 1)
        return bands[row * ZONES + zone]
    }

    fun effectiveBandAt(xFraction: Float, yFraction: Float): LuminanceBand {
        val band = bandAt(xFraction, yFraction)
        if (yFraction < 1f - WAVE_REGION) return band
        return LuminanceBand(
            mean = (band.mean + WAVE_LUMINANCE_BOOST).coerceAtMost(1f),
            p90 = (band.p90 + WAVE_LUMINANCE_BOOST).coerceAtMost(1f),
        )
    }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        const val ROWS = 12

        const val ZONES = 3

        const val WAVE_REGION = 0.35f

        const val WAVE_LUMINANCE_BOOST = 0.06f

        private const val MAX_SAMPLES = 12_000

        private const val HISTOGRAM_BINS = 256

        private val JSON = Json { ignoreUnknownKeys = true }

        fun relativeLuminance(argb: Int): Float {
            fun linearize(channel: Int): Double {
                val v = channel / 255.0
                return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            }
            val r = linearize(argb shr 16 and 0xFF)
            val g = linearize(argb shr 8 and 0xFF)
            val b = linearize(argb and 0xFF)
            return (0.2126 * r + 0.7152 * g + 0.0722 * b).toFloat()
        }

        fun compute(image: BmpImage, source: String): WallpaperLuminanceMap {
            val cells = ROWS * ZONES
            val sums = DoubleArray(cells)
            val counts = IntArray(cells)
            val histograms = Array(cells) { IntArray(HISTOGRAM_BINS) }

            val stride = max(
                1,
                sqrt(image.width.toFloat() * image.height / MAX_SAMPLES).toInt(),
            )

            var y = 0
            while (y < image.height) {
                val row = (y.toLong() * ROWS / image.height).toInt().coerceAtMost(ROWS - 1)
                var x = 0
                while (x < image.width) {
                    val zone = (x.toLong() * ZONES / image.width).toInt().coerceAtMost(ZONES - 1)
                    val cell = row * ZONES + zone
                    val luminance = relativeLuminance(image.argb[y * image.width + x])
                    sums[cell] += luminance
                    counts[cell]++
                    val bin = (luminance * (HISTOGRAM_BINS - 1)).toInt().coerceIn(0, HISTOGRAM_BINS - 1)
                    histograms[cell][bin]++
                    x += stride
                }
                y += stride
            }

            val bands = List(cells) { cell ->
                val count = counts[cell]

                if (count == 0) LuminanceBand(0f, 0f)
                else LuminanceBand(
                    mean = (sums[cell] / count).toFloat(),
                    p90 = percentile(histograms[cell], count, 0.90f),
                )
            }
            return WallpaperLuminanceMap(source, bands)
        }

        fun fromJson(json: String, expectedSource: String): WallpaperLuminanceMap? {
            val map = try {
                JSON.decodeFromString(serializer(), json)
            } catch (_: Exception) {
                return null
            }
            return map.takeIf { it.source == expectedSource }
        }

        private fun percentile(histogram: IntArray, count: Int, fraction: Float): Float {
            val target = (count * fraction).toInt().coerceAtMost(count - 1)
            var seen = 0
            for (bin in histogram.indices) {
                seen += histogram[bin]
                if (seen > target) return bin.toFloat() / (HISTOGRAM_BINS - 1)
            }
            return 1f
        }
    }
}
