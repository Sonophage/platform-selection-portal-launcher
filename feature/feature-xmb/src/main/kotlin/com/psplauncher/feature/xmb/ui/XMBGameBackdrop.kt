package com.psplauncher.feature.xmb.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

const val XMB_STILL_SOLID_END = 0.40f

const val XMB_STILL_FADE_END = 0.68f

fun xmbStillOverVideoStops(): Array<Pair<Float, Color>> = arrayOf(
    0f to Color.Black,
    XMB_STILL_SOLID_END to Color.Black,
    XMB_STILL_FADE_END to Color.Transparent,
    1f to Color.Transparent,
)

fun Modifier.xmbStillOverVideo(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(colorStops = xmbStillOverVideoStops()),
            blendMode = BlendMode.DstIn,
        )
    }
