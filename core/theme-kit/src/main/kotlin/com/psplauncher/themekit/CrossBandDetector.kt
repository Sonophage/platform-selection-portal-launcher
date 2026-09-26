package com.psplauncher.themekit

import kotlin.math.max
import kotlin.math.sqrt

object CrossBandDetector {
    private const val SEARCH_TOP = 0.03f
    private const val SEARCH_BOTTOM = 0.55f
    private const val FLAT_STDDEV = 0.02f
    private const val MIN_DEPTH = 0.10f
    private const val MIN_EDGE_CONTRAST = 0.10f
    private const val MIN_RUN_FRACTION = 0.06f
    private const val MAX_RUN_FRACTION = 0.35f

    fun detectBarTopFraction(image: BmpImage, maxColumnSamples: Int = 256): Float? {
        val width = image.width
        val height = image.height
        if (width < 32 || height < 32) return null

        val columnStride = max(1, width / maxColumnSamples)
        val profile = FloatArray(height) { row ->
            var sum = 0f
            var count = 0
            var x = 0
            while (x < width) {
                val argb = image.argb[row * width + x]
                sum += luminance(argb)
                count++
                x += columnStride
            }
            sum / count
        }

        val window = max(3, height / 64)
        val smoothed = FloatArray(height) { row ->
            val from = max(0, row - window / 2)
            val to = minOf(height - 1, row + window / 2)
            var sum = 0f
            for (r in from..to) sum += profile[r]
            sum / (to - from + 1)
        }

        val regionStart = (height * SEARCH_TOP).toInt()
        val regionEnd = (height * SEARCH_BOTTOM).toInt()
        if (regionEnd - regionStart < 8) return null

        var mean = 0f
        for (r in regionStart until regionEnd) mean += smoothed[r]
        mean /= (regionEnd - regionStart)
        var variance = 0f
        for (r in regionStart until regionEnd) {
            val d = smoothed[r] - mean
            variance += d * d
        }
        val stddev = sqrt(variance / (regionEnd - regionStart))
        if (stddev < FLAT_STDDEV) return null

        val darkThreshold = mean - max(0.08f, 0.35f * stddev)
        val minRun = (height * MIN_RUN_FRACTION).toInt()
        val maxRun = (height * MAX_RUN_FRACTION).toInt()

        var bestScore = 0f
        var bestStart = -1
        var runStart = -1
        for (r in regionStart..regionEnd) {
            val dark = r < regionEnd && smoothed[r] < darkThreshold
            if (dark && runStart < 0) runStart = r
            if (!dark && runStart >= 0) {
                val runEnd = r
                score(smoothed, runStart, runEnd, mean, minRun, maxRun)?.let { s ->
                    if (s > bestScore) { bestScore = s; bestStart = runStart }
                }
                runStart = -1
            }
        }

        if (bestStart < 0) return null
        return (bestStart.toFloat() / height)
            .coerceIn(XmbLayoutSpecCodec.BAR_TOP_MIN, XmbLayoutSpecCodec.BAR_TOP_MAX)
    }

    private fun score(
        smoothed: FloatArray,
        start: Int,
        end: Int,
        regionMean: Float,
        minRun: Int,
        maxRun: Int,
    ): Float? {
        val length = end - start
        if (length < minRun || length > maxRun) return null

        var inside = 0f
        for (r in start until end) inside += smoothed[r]
        inside /= length
        val depth = regionMean - inside
        if (depth < MIN_DEPTH) return null

        val edgeRows = max(3, length / 2)
        val edgeFrom = start - edgeRows
        if (edgeFrom < 0) return null
        var above = 0f
        for (r in edgeFrom until start) above += smoothed[r]
        above /= edgeRows
        if (above - inside < MIN_EDGE_CONTRAST) return null

        return depth * sqrt(length.toFloat())
    }

    internal fun luminance(argb: Int): Float {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
