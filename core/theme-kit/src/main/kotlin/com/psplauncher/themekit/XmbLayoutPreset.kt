package com.psplauncher.themekit

import kotlin.math.abs

object XmbLayoutPreset {
    const val PSP_CANVAS_HEIGHT_DP = 354.546f

    const val PSP_CATICON_CENTER_FRACTION = 0.34436f

    const val PSP_CROSS_ANCHOR_X_DP = 105.49f

    private const val BAR_TOP_SUB_DP = 76.0f

    private const val COLUMN_BASE_INSET_DP = 137.0f

    private const val XMB_BASELINE_HEIGHT_DP = 468f
    private const val XMB_BASELINE_WIDTH_DP = 832f
    private const val XMB_MAX_SCALE = 2.5f

    fun computeForWindowDp(widthDp: Float, heightDp: Float, density: Float): XmbLayoutAdjust =
        computeForWindow(
            widthPx = maxOf(widthDp, heightDp) * density,
            heightPx = minOf(widthDp, heightDp) * density,
            densityDpi = density * 160f,
        )

    fun computeForWindow(widthPx: Float, heightPx: Float, densityDpi: Float): XmbLayoutAdjust {
        val d = densityDpi / 160f
        val W_dp = widthPx / d
        val H_dp = heightPx / d

        val uiScale = minOf(H_dp / XMB_BASELINE_HEIGHT_DP, W_dp / XMB_BASELINE_WIDTH_DP)
            .coerceIn(1f, XMB_MAX_SCALE)

        val rawScale = (H_dp / PSP_CANVAS_HEIGHT_DP) / uiScale
        val scale = rawScale.coerceIn(XmbLayoutAdjust.SCALE_MIN, XmbLayoutAdjust.SCALE_MAX)

        val totalScale = uiScale * scale
        val canvasH = H_dp / totalScale
        val canvasW = W_dp / totalScale

        val barTopFraction = (PSP_CATICON_CENTER_FRACTION - BAR_TOP_SUB_DP / canvasH)
            .coerceIn(XmbLayoutAdjust.TOP_MIN, XmbLayoutAdjust.TOP_MAX)

        val barLeftFraction = ((PSP_CROSS_ANCHOR_X_DP - COLUMN_BASE_INSET_DP) / canvasW)
            .coerceIn(XmbLayoutAdjust.LEFT_MIN, XmbLayoutAdjust.LEFT_MAX)

        return XmbLayoutAdjust(
            scale = scale,
            barLeftFraction = barLeftFraction,
            barTopFraction = barTopFraction,
        )
    }

    fun computeRawForWindow(widthPx: Float, heightPx: Float, densityDpi: Float): AutoFitValues {
        val d = densityDpi / 160f
        val W_dp = widthPx / d
        val H_dp = heightPx / d

        val uiScale = minOf(H_dp / XMB_BASELINE_HEIGHT_DP, W_dp / XMB_BASELINE_WIDTH_DP)
            .coerceIn(1f, XMB_MAX_SCALE)

        val rawScale = (H_dp / PSP_CANVAS_HEIGHT_DP) / uiScale
        val scale = rawScale.coerceIn(XmbLayoutAdjust.SCALE_MIN, XmbLayoutAdjust.SCALE_MAX)

        val totalScale = uiScale * scale
        val canvasH = H_dp / totalScale
        val canvasW = W_dp / totalScale

        val barTopFraction = (PSP_CATICON_CENTER_FRACTION - BAR_TOP_SUB_DP / canvasH)
            .coerceIn(XmbLayoutAdjust.TOP_MIN, XmbLayoutAdjust.TOP_MAX)

        val barLeftFraction = ((PSP_CROSS_ANCHOR_X_DP - COLUMN_BASE_INSET_DP) / canvasW)
            .coerceIn(XmbLayoutAdjust.LEFT_MIN, XmbLayoutAdjust.LEFT_MAX)

        return AutoFitValues(
            uiScale = uiScale,
            scale = scale,
            canvasH = canvasH,
            canvasW = canvasW,
            barTopFraction = barTopFraction,
            barLeftFraction = barLeftFraction,
        )
    }

    fun matches(saved: XmbLayoutAdjust?, preset: XmbLayoutAdjust): Boolean =
        saved != null &&
            abs(saved.scale - preset.scale) <= MATCH_TOLERANCE &&
            abs(saved.barLeftFraction - preset.barLeftFraction) <= MATCH_TOLERANCE &&
            abs(saved.barTopFraction - preset.barTopFraction) <= MATCH_TOLERANCE

    private const val MATCH_TOLERANCE = 0.001f

    data class AutoFitValues(
        val uiScale: Float,
        val scale: Float,
        val canvasH: Float,
        val canvasW: Float,
        val barTopFraction: Float,
        val barLeftFraction: Float,
    )
}
