package com.psplauncher.feature.xmb.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal object XmbGlow {
    val Color = Color(0xFFFFDCAA)

    const val CategoryAlpha = 0.55f
    const val CategoryReach = 0.30f

    const val RowAlpha = 0.58f
    const val RowReach = 0.26f
}

internal fun Modifier.xmbFocusGlow(
    visible: Float,
    reach: Float,
    alpha: Float,
): Modifier = drawBehind {
    if (visible <= 0f || alpha <= 0f) return@drawBehind

    val radius = size.minDimension / 2f * (1f + 2f * reach)
    if (radius <= 0f) return@drawBehind
    val peak = alpha * visible
    drawCircle(
        brush = Brush.radialGradient(

            0.00f to XmbGlow.Color.copy(alpha = peak),
            0.30f to XmbGlow.Color.copy(alpha = peak * 0.62f),
            0.52f to XmbGlow.Color.copy(alpha = peak * 0.28f),
            0.74f to XmbGlow.Color.copy(alpha = peak * 0.09f),
            1.00f to XmbGlow.Color.copy(alpha = 0f),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
