package com.psplauncher.themekit

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object WallpaperMetrics {
    const val BUSY_THRESHOLD = 0.055f

    const val DARK_ICON_LUMINANCE = 0.35f

    fun busyness(image: BmpImage, maxSamples: Int = 6000): Float {
        val width = image.width
        val height = image.height
        if (width < 4 || height < 4) return 0f

        val x0 = (width * 0.10f).toInt()
        val x1 = (width * 0.60f).toInt().coerceAtMost(width - 2)
        val y0 = (height * 0.30f).toInt()
        val y1 = (height * 0.85f).toInt().coerceAtMost(height - 2)
        if (x1 <= x0 || y1 <= y0) return 0f

        val regionPixels = (x1 - x0) * (y1 - y0)
        val stride = max(1, (sqrt(regionPixels / maxSamples.toFloat())).toInt())

        var sum = 0f
        var count = 0
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val here = luminance(image.argb[y * width + x])
                val right = luminance(image.argb[y * width + x + 1])
                val below = luminance(image.argb[(y + 1) * width + x])
                sum += (abs(here - right) + abs(here - below)) / 2f
                count++
                x += stride
            }
            y += stride
        }
        return if (count == 0) 0f else sum / count
    }

    fun isBusy(image: BmpImage): Boolean = busyness(image) > BUSY_THRESHOLD

    fun luminance(argb: Int): Float = CrossBandDetector.luminance(argb)
}
