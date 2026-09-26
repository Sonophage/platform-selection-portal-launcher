package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.core.ui.wave.WaveStyle
import kotlin.math.exp
import kotlin.math.pow

private const val SEQUENCE_MS = 2_000

private const val LEAD_SILENCE_MS = 50f

private val LOUDNESS: List<Pair<Int, Float>> = listOf(
    0 to 0.00f, 50 to 0.11f, 100 to 0.37f, 150 to 0.82f, 200 to 1.00f,
    250 to 0.60f, 300 to 0.37f, 350 to 0.22f, 400 to 0.14f, 450 to 0.06f,
    500 to 0.03f, 650 to 0.01f, 2_000 to 0.00f,
)

private const val FIRST_SWEEP_START_MS = 60
private const val FIRST_SWEEP_END_MS = 320
private const val SECOND_SWEEP_START_MS = 320
private const val SECOND_SWEEP_END_MS = 700

private const val TITLE_FADE_START_MS = 700
private const val TITLE_FADE_MS = 300

private const val DECAY_START_MS = 1_500
private const val DECAY_MS = 500

@Composable
fun GameBootSequence(
    gameTitle: String,
    waveStyle: WaveStyle,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPFPColors.current
    val reduced = !waveStyle.animated || waveStyle.reduced
    var ms by remember { mutableFloatStateOf(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        val startMs = withFrameMillis { it }
        while (true) {
            val elapsed = (withFrameMillis { it } - startMs).toFloat()
            ms = elapsed.coerceAtMost(SEQUENCE_MS.toFloat())
            if (elapsed >= SEQUENCE_MS) break
        }
        currentOnFinished()
    }

    val decay = 1f - ((ms - DECAY_START_MS) / DECAY_MS).coerceIn(0f, 1f)

    val titleAlpha = if (reduced) decay else {
        val fadeIn = ((ms - TITLE_FADE_START_MS) / TITLE_FADE_MS).coerceIn(0f, 1f)
        fadeIn * decay
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to colors.backgroundTop,
                        0.70f to lerp(colors.backgroundTop, colors.backgroundBottom, 0.5f),
                        1.00f to colors.backgroundBottom,
                    )
                )
            )

            if (reduced) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.30f * decay), Color.Transparent),
                        center = center.copy(x = size.width * 0.5f, y = size.height * 0.56f),
                        radius = size.minDimension * 0.55f,
                    )
                )
                if (decay < 1f) drawRect(Color.Black.copy(alpha = 1f - decay))
                return@Canvas
            }

            val loudness = loudnessAt(ms)
            val bloomAlpha = loudness * decay
            if (bloomAlpha > 0.001f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.30f * bloomAlpha), Color.Transparent),
                        center = center.copy(x = size.width * 0.5f, y = size.height * 0.56f),
                        radius = size.minDimension * (0.30f + 0.50f * loudness),
                    )
                )
            }

            sweepProgress(ms, FIRST_SWEEP_START_MS, FIRST_SWEEP_END_MS)?.let {
                drawSweep(size.width, it, intensity = 0.55f * decay)
            }
            sweepProgress(ms, SECOND_SWEEP_START_MS, SECOND_SWEEP_END_MS)?.let {
                drawSweep(size.width, it, intensity = decay)
            }

            val hold = 1f - (ms / LEAD_SILENCE_MS).coerceIn(0f, 1f)
            if (hold > 0f) drawRect(Color.Black.copy(alpha = hold))
            if (decay < 1f) drawRect(Color.Black.copy(alpha = 1f - decay))
        }

        Text(
            text = gameTitle,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 48.dp)
                .alpha(titleAlpha),
        )
    }
}

private fun loudnessAt(ms: Float): Float {
    if (ms <= LOUDNESS.first().first) return LOUDNESS.first().second
    if (ms >= LOUDNESS.last().first) return LOUDNESS.last().second
    for (i in 1 until LOUDNESS.size) {
        val (endMs, endValue) = LOUDNESS[i]
        if (ms > endMs) continue
        val (startMs, startValue) = LOUDNESS[i - 1]
        val t = (ms - startMs) / (endMs - startMs).toFloat()
        return startValue + (endValue - startValue) * t
    }
    return LOUDNESS.last().second
}

private fun sweepProgress(ms: Float, startMs: Int, endMs: Int): Float? =
    if (ms < startMs || ms > endMs) null else (ms - startMs) / (endMs - startMs)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSweep(
    w: Float,
    t: Float,
    intensity: Float,
) {
    if (intensity <= 0.001f) return
    val centre = w * (0.08f + 0.84f * t)
    val bandWidth = w * 0.28f
    val n = 24
    val stops = List(n) { i ->
        val x = i / (n - 1).toFloat() * w
        val u = (x - centre) / bandWidth
        val crest = exp(-(u * 2.6f).pow(2))
        val sheet = smoothstep(-1.4f, 0.55f, u) * 0.45f
        val alpha = ((crest * 0.85f + sheet) * intensity).coerceIn(0f, 0.8f)
        Color.White.copy(alpha = alpha)
    }
    drawRect(brush = Brush.horizontalGradient(stops))
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
