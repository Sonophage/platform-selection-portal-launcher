package com.psplauncher.feature.xmb.ui

internal object XmbDim {
    private val Published = floatArrayOf(1f, 0.85f, 0.55f, 0.30f)

    const val PanelScale = 0.68f

    private val Ramp = FloatArray(Published.size) { i ->

        if (i == 0) 1f else Published[i] * PanelScale
    }

    const val LastStep = 3

    fun ranked(distance: Int): Float = Ramp[distance.coerceIn(0, LastStep)]

    fun smoothed(distance: Int, span: Int): Float {
        if (distance <= 0) return 1f

        val steps = span.coerceAtLeast(2)
        val t = ((distance - 1).toFloat() / (steps - 1)).coerceIn(0f, 1f)
        val pos = 1f + t * (LastStep - 1)
        val lo = pos.toInt().coerceIn(1, LastStep - 1)
        val f = pos - lo
        return (Published[lo] + (Published[lo + 1] - Published[lo]) * f) * PanelScale
    }
}
