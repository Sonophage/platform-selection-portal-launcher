package com.psplauncher.feature.xmb.ui.detail

import kotlin.math.floor

enum class StudioTileClass(val aspect: Double, val minTileWidthDp: Double) {
    LANDSCAPE(aspect = 1.5, minTileWidthDp = 112.0),
    PORTRAIT(aspect = 0.7, minTileWidthDp = 80.0),
    SQUARE(aspect = 1.0, minTileWidthDp = 96.0),
    WIDE(aspect = 2.0, minTileWidthDp = 140.0),
}

data class StudioGridCapacity(val columns: Int, val rows: Int) {
    val pageSize: Int get() = columns * rows

    companion object {
        val UNMEASURED = StudioGridCapacity(columns = 4, rows = 5)

        private const val GAP_DP = 8.0
        private const val MIN_COLUMNS = 3
        private const val MAX_COLUMNS = 8
        private const val MIN_ROWS = 1
        private const val MAX_ROWS = 6

        private const val FIT_EPSILON = 1e-6

        fun of(widthDp: Float, heightDp: Float, tileClass: StudioTileClass): StudioGridCapacity {
            val width = widthDp.toDouble()
            val height = heightDp.toDouble()
            val columns = fits(width, tileClass.minTileWidthDp).coerceIn(MIN_COLUMNS, MAX_COLUMNS)

            val tileWidth = ((width - GAP_DP * (columns - 1)) / columns).coerceAtLeast(0.0)
            val tileHeight = tileWidth / tileClass.aspect
            val rows = fits(height, tileHeight).coerceIn(MIN_ROWS, MAX_ROWS)
            return StudioGridCapacity(columns, rows)
        }

        private fun fits(span: Double, tile: Double): Int =
            floor((span + GAP_DP) / (tile + GAP_DP) + FIT_EPSILON).toInt()
    }
}
