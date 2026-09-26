package com.psplauncher.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.sound.LocalLaunchDiscCue
import com.psplauncher.core.ui.image.rememberArtworkModel
import kotlinx.coroutines.delay

@Composable
fun DiscLaunchCeremony(

    art: Any?,

    onHandOff: () -> Unit,

    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handOff by rememberUpdatedState(onHandOff)
    val finished by rememberUpdatedState(onFinished)

    val discCue = LocalLaunchDiscCue.current
    LaunchedEffect(Unit) { discCue() }

    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(DiscCeremony.TotalMs, easing = LinearEasing))
        finished()
    }
    LaunchedEffect(Unit) {
        delay(DiscCeremony.HandOffMs.toLong())
        handOff()
    }

    val model = when (art) {
        is String -> rememberArtworkModel(art)
        else -> art
    }

    val now = t.value

    val caseIn = LinearOutSlowInEasing.transform(phase(now, 0f, DiscCeremony.at(DiscCeremony.CaseInMs)))
    val emerge = FastOutSlowInEasing.transform(
        phase(now, DiscCeremony.at(DiscCeremony.CaseInMs), DiscCeremony.FadeInFraction)
    )
    val caseAlpha = caseIn * (1f - phase(
        now,
        DiscCeremony.at(DiscCeremony.CaseFadeStartMs),
        DiscCeremony.FadeInFraction,
    ))

    val discAlpha = phase(
        now,
        DiscCeremony.at(DiscCeremony.DiscAppearMs),
        DiscCeremony.at(DiscCeremony.DiscOpaqueMs),
    )
    val sink = phase(now, DiscCeremony.FadeInFraction, DiscCeremony.SinkEndFraction)
    val spin = phase(now, DiscCeremony.SinkEndFraction, DiscCeremony.DiscOutStartFraction)

    val outAt = { ms: Int -> DiscCeremony.at(DiscCeremony.DiscOutStartMs + ms) }
    val drop = phase(now, DiscCeremony.DiscOutStartFraction, outAt(DiscCeremony.DropMs))
    val blackout = phase(now, DiscCeremony.DiscOutStartFraction, outAt(DiscCeremony.BlackoutMs))
    val slitOpen = phase(now, outAt(DiscCeremony.SlitOpenStartMs), outAt(DiscCeremony.SlitOpenEndMs))
    val slitClose = FastOutSlowInEasing.transform(
        phase(now, outAt(DiscCeremony.SlitOpenEndMs), outAt(DiscCeremony.SlitCloseEndMs))
    )
    val slitFade = phase(now, outAt(DiscCeremony.SlitFadeStartMs), outAt(DiscCeremony.SlitFadeEndMs))

    val roomLeave = phase(now, DiscCeremony.RoomOpensFraction, 1f)

    val sinkEase = FastOutSlowInEasing.transform(sink)

    val closeEase = FastOutSlowInEasing.transform(phase(now, DiscCeremony.FadeInFraction, DiscCeremony.DiscOutStartFraction))
    val leaveEase = FastOutSlowInEasing.transform(roomLeave)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val discSize = minOf(maxWidth, maxHeight) * DiscCeremony.SizeFraction

        val driftPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            (maxHeight.toPx() * (DiscCeremony.RestHeightFraction - 0.5f))
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    val centre = Offset(size.width / 2f, size.height / 2f + driftPx * sinkEase)

                    val shut = closeEase * (1f - leaveEase)
                    val radius = (DiscCeremony.VignetteOpenRadius -
                        (DiscCeremony.VignetteOpenRadius - DiscCeremony.VignetteClosedRadius) * shut)
                        .coerceAtLeast(0.05f) * size.minDimension

                    val inner = ((closeEase - DiscCeremony.VignetteFillFrom) /
                        (1f - DiscCeremony.VignetteFillFrom)).coerceIn(0f, 1f) * (1f - leaveEase)
                    val dim = DiscCeremony.MaxDim * (1f - leaveEase)
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = inner * dim),
                                0.62f to Color.Black.copy(alpha = maxOf(inner, shut * 0.40f) * dim),
                                1.00f to Color.Black.copy(alpha = shut * dim),
                            ),
                            center = centre,
                            radius = radius,
                        ),
                    )
                }
        )

        if (blackout > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = blackout * (1f - leaveEase) }
                    .background(Color(0xFF050302))
            )
        }

        val dropPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            minOf(maxWidth, maxHeight).toPx() * DiscCeremony.DropFraction
        }
        val arcPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            maxWidth.toPx() * DiscCeremony.DiscArcFraction
        }

        Box(
            modifier = Modifier
                .size(discSize)
                .graphicsLayer {
                    alpha = discAlpha
                    val grow = DiscCeremony.EmergeScale + (1f - DiscCeremony.EmergeScale) * emerge
                    val shrink = 1f - (1f - DiscCeremony.RestScale) * sinkEase
                    scaleX = grow * shrink
                    scaleY = grow * shrink

                    translationX = arcPx * kotlin.math.sin(Math.PI.toFloat() * emerge)

                    rotationZ = emerge * DiscCeremony.EmergeDegrees +
                        sinkEase * DiscCeremony.SinkDegrees +
                        spin * spin * DiscCeremony.SpinUpDegrees +
                        drop * DiscCeremony.DropDegrees

                    translationY = driftPx * sinkEase + dropPx * drop * drop

                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    val r = size.minDimension / 2f

                    drawCircle(
                        color = Color.White.copy(alpha = 0.16f),
                        radius = r * DiscCeremony.HubFraction,
                        style = Stroke(width = r * 0.045f),
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = r * DiscCeremony.HoleFraction,
                        blendMode = BlendMode.Clear,
                    )
                },
        ) {
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF15151C)))
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            0.00f to Color.White.copy(alpha = 0.00f),
                            0.18f to Color.White.copy(alpha = 0.18f),
                            0.32f to Color.White.copy(alpha = 0.00f),
                            0.62f to Color.White.copy(alpha = 0.10f),
                            0.78f to Color.White.copy(alpha = 0.00f),
                            1.00f to Color.White.copy(alpha = 0.00f),
                        )
                    )
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        val r = size.minDimension / 2f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.30f),
                            radius = r - 1f,
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(width = 2f),
                        )
                    }
            )
        }

        val caseSize = minOf(maxWidth, maxHeight) * DiscCeremony.CaseSizeFraction
        val caseSlidePx = with(androidx.compose.ui.platform.LocalDensity.current) {
            maxWidth.toPx() * DiscCeremony.CaseSlideFraction
        }
        if (caseAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(caseSize)
                    .graphicsLayer {
                        alpha = caseAlpha
                        val s = (DiscCeremony.CaseEntryScale +
                            (1f - DiscCeremony.CaseEntryScale) * caseIn) *
                            (1f - DiscCeremony.CaseShrink * emerge)
                        scaleX = s
                        scaleY = s
                        translationX = caseSlidePx * emerge
                        shape = RoundedCornerShape(
                            size.minDimension * DiscCeremony.CaseCornerFraction
                        )
                        clip = true
                        shadowElevation = 30.dp.toPx()
                    },
            ) {
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF15151C)))
                }
            }
        }

        if (slitOpen > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val a = slitOpen * (1f - slitFade)
                val halfW = (size.width * DiscCeremony.SlitWidthFraction / 2f) *
                    slitOpen * (1f - slitClose)
                if (a <= 0f || halfW <= 0f) return@Canvas
                val core = (size.height * DiscCeremony.SlitHeightFraction).coerceAtLeast(2f)
                val y = size.height * DiscCeremony.SlitYFraction
                val glow = core * DiscCeremony.SlitGlowSpread

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFECD6).copy(alpha = 0f),
                            Color(0xFFFFECD6).copy(alpha = 0.55f * a),
                            Color(0xFFFFECD6).copy(alpha = 0f),
                        ),
                        startY = y - glow / 2f,
                        endY = y + glow / 2f,
                    ),
                    topLeft = Offset(size.width / 2f - halfW, y - glow / 2f),
                    size = Size(halfW * 2f, glow),
                )
                drawRect(
                    color = Color.White.copy(alpha = a),
                    topLeft = Offset(size.width / 2f - halfW, y - core / 2f),
                    size = Size(halfW * 2f, core),
                )
            }
        }
    }
}

private fun phase(now: Float, from: Float, to: Float): Float =
    if (to <= from) 1f else ((now - from) / (to - from)).coerceIn(0f, 1f)

object DiscCeremony {
    const val FadeInMs = 1700

    const val SinkMs = 1150

    const val SpinMs = 3350

    const val DiscOutMs = 650

    const val HoldMs = 900

    const val TotalMs = FadeInMs + SinkMs + SpinMs + DiscOutMs + HoldMs

    const val HandOffMs = FadeInMs + SinkMs + SpinMs + DiscOutMs

    val SinkEndFraction = (FadeInMs + SinkMs).toFloat() / TotalMs

    const val DiscOutStartMs = FadeInMs + SinkMs + SpinMs
    val DiscOutStartFraction = DiscOutStartMs.toFloat() / TotalMs

    val DiscGoneFraction = HandOffMs.toFloat() / TotalMs
    val HandOffFraction = DiscGoneFraction

    private const val RoomOpensShare = 0.30f
    val RoomOpensFraction = HandOffFraction + (1f - HandOffFraction) * RoomOpensShare

    val FadeInFraction = FadeInMs.toFloat() / TotalMs

    fun at(ms: Int): Float = ms.toFloat() / TotalMs

    const val CaseInMs = 500

    const val CaseFadeStartMs = 1240

    const val DiscAppearMs = 500
    const val DiscOpaqueMs = 760

    const val CaseSizeFraction = 0.648f
    const val CaseCornerFraction = 0.0343f

    const val CaseSlideFraction = -0.177f

    const val CaseEntryScale = 0.90f
    const val CaseShrink = 0.20f

    const val DiscArcFraction = 0.1875f

    const val EmergeScale = 0.62f

    const val EmergeDegrees = 160f

    const val DropMs = 350
    const val DropDegrees = 600f

    const val DropFraction = 0.648f

    const val BlackoutMs = 200

    const val SlitOpenStartMs = 230
    const val SlitOpenEndMs = 350
    const val SlitCloseEndMs = 650

    const val SlitFadeStartMs = 600
    const val SlitFadeEndMs = 670

    const val SlitYFraction = 0.935f
    const val SlitWidthFraction = 0.573f
    const val SlitHeightFraction = 0.0037f

    const val SlitGlowSpread = 12f

    const val SizeFraction = 0.72f

    const val RestScale = 0.88f

    const val RestHeightFraction = 1.0f

    const val HoleFraction = 0.125f
    const val HubFraction = 0.21f

    const val SinkDegrees = 70f

    const val SpinUpDegrees = 900f

    const val MaxDim = 0.96f

    const val VignetteOpenRadius = 1.30f
    const val VignetteClosedRadius = 0.42f

    const val VignetteFillFrom = 0.72f
}
