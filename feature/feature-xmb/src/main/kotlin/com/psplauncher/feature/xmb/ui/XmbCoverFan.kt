package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.image.rememberArtworkModel

@Composable
internal fun XmbCoverFan(covers: List<String>, modifier: Modifier = Modifier) {
    if (covers.isEmpty()) return
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight

        val slots = listOf(

            FanSlot(0.2885f, 0.0000f, 0.4231f, 0.8105f, 0f, 1.00f, 25.dp),
            FanSlot(0.0577f, 0.1053f, 0.3846f, 0.7368f, -8f, 0.85f, 20.dp),
            FanSlot(0.5385f, 0.0789f, 0.3846f, 0.7895f, 7f, 0.85f, 20.dp),
        )
        val filled = covers.take(slots.size).mapIndexed { i, uri -> slots[i] to uri }

        filled.reversed().forEach { (slot, uri) ->
            AsyncImage(
                model = rememberArtworkModel(uri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(x = w * slot.x, y = h * slot.y)
                    .size(width = w * slot.width, height = h * slot.height)
                    .graphicsLayer {
                        rotationZ = slot.degrees
                        alpha = slot.alpha
                        shadowElevation = slot.lift.toPx()
                        shape = RoundedCornerShape(size.minDimension * CornerFraction)
                        clip = true
                    },
            )
        }
    }
}

private data class FanSlot(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val degrees: Float,
    val alpha: Float,
    val lift: androidx.compose.ui.unit.Dp,
)

private const val CornerFraction = 12f / 220f

internal object XmbCoverFanPlacement {
    const val WidthFraction = 520f / 1920f
    const val HeightFraction = 380f / 1080f
    const val RightInsetFraction = 120f / 1920f
    const val TopFraction = 470f / 1080f
}
