package com.psplauncher.themekit

object ColorCascade {
    fun lightBackgroundAnchors(waveArgb: Long): Pair<Long, Long> =
        darken(waveArgb, 0.62f) to lighten(waveArgb, 0.28f)

    fun lighten(argb: Long, t: Float): Long {
        val r = (argb shr 16) and 0xFFL
        val g = (argb shr 8) and 0xFFL
        val b = argb and 0xFFL
        val nr = (r + (255 - r) * t).toLong().coerceIn(0L, 255L)
        val ng = (g + (255 - g) * t).toLong().coerceIn(0L, 255L)
        val nb = (b + (255 - b) * t).toLong().coerceIn(0L, 255L)
        return (0xFFL shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    fun darken(argb: Long, factor: Float): Long {
        val r = (((argb shr 16) and 0xFFL) * factor).toLong().coerceIn(0L, 255L)
        val g = (((argb shr 8) and 0xFFL) * factor).toLong().coerceIn(0L, 255L)
        val b = ((argb and 0xFFL) * factor).toLong().coerceIn(0L, 255L)
        return (0xFFL shl 24) or (r shl 16) or (g shl 8) or b
    }
}
