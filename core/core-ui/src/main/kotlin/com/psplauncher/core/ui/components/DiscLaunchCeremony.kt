package com.psplauncher.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.image.rememberArtworkModel
import kotlinx.coroutines.delay

/**
 * The disc that plays when a game, film, book or track is starting.
 *
 * The thing you picked turns into a disc — its cover art is the face, with a hole punched through
 * the middle — fades up in the centre of the screen, sinks toward the bottom while it spins up and
 * the room goes dark, and then fades out onto whatever opened.
 *
 * **The hand-off is timed to the fade, not to the end.** [onHandOff] fires as the disc starts
 * fading out, so the app's own cold start happens *under* the last half-second of animation
 * instead of after it. The ceremony costs the user the time up to that point and no more.
 *
 * **Draw-only.** It owns no launch and no player: it reports two moments and the caller decides
 * what they mean. That is what lets the same overlay sit in front of four unrelated launch paths,
 * and what keeps a stuck launch from being this file's problem — the gate that waits on
 * [onFinished] has its own watchdog.
 */
@Composable
fun DiscLaunchCeremony(
    /** The cover: a uri for anything on disk, or a Drawable. Null draws the blank disc. */
    art: Any?,
    /** Start the thing. Fires once, as the fade-out begins. */
    onHandOff: () -> Unit,
    /** The overlay has nothing left to draw and should be removed. Fires once. */
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberUpdatedState so a recomposition that swaps the lambdas cannot restart the timeline
    // below — the animation is keyed on Unit deliberately: it must run exactly once per launch.
    val handOff by rememberUpdatedState(onHandOff)
    val finished by rememberUpdatedState(onFinished)

    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // One linear clock, phases derived from it. Three chained animations would each need their
        // own easing and their own completion callback, and the timeline would live in the gaps
        // between them rather than anywhere you could read it.
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
    val rise = phase(now, 0f, DiscCeremony.FadeInFraction)
    val sink = phase(now, DiscCeremony.FadeInFraction, DiscCeremony.HandOffFraction)
    val leave = phase(now, DiscCeremony.HandOffFraction, 1f)

    val riseEase = LinearOutSlowInEasing.transform(rise)
    val sinkEase = FastOutSlowInEasing.transform(sink)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // The room dims as the disc sinks, so the launcher recedes and the disc is the only
            // lit thing by the time it spins.
            .background(Color.Black.copy(alpha = sinkEase * DiscCeremony.MaxDim)),
        contentAlignment = Alignment.Center,
    ) {
        val discSize = minOf(maxWidth, maxHeight) * DiscCeremony.SizeFraction
        // Where the disc ends up: its centre lands at RestHeightFraction down the screen. Measured
        // from the centre, because that is where it starts.
        val driftPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            (maxHeight.toPx() * (DiscCeremony.RestHeightFraction - 0.5f))
        }

        Box(
            modifier = Modifier
                .size(discSize)
                .graphicsLayer {
                    alpha = riseEase * (1f - leave)
                    val grow = DiscCeremony.EntryScale + (1f - DiscCeremony.EntryScale) * riseEase
                    val shrink = 1f - (1f - DiscCeremony.RestScale) * sinkEase
                    scaleX = grow * shrink
                    scaleY = grow * shrink
                    // Spins UP rather than at a constant rate: squaring the sink phase means it is
                    // barely turning as it leaves the centre and moving properly by the time it
                    // settles, which is what reads as a disc being spun up rather than one that
                    // was already going.
                    rotationZ = sink * sink * DiscCeremony.SpinUpDegrees +
                        leave * DiscCeremony.SpinOutDegrees
                    translationY = driftPx * sinkEase
                    // BlendMode.Clear needs its own layer, or it punches through the whole screen
                    // instead of through the disc.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    val r = size.minDimension / 2f
                    // The hub: the clear plastic ring a CD has around its hole. Drawn before the
                    // hole so the hole cuts through it too.
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
            // The iridescent sweep a pressed disc has. Inside the rotating layer on purpose: the
            // sheen belongs to the disc, so it turns with it.
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
            // The outer rim, so the disc keeps an edge against a dark background.
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
    }
}

/** Progress through [from]..[to], clamped, as 0..1. Zero-width spans read as finished. */
private fun phase(now: Float, from: Float, to: Float): Float =
    if (to <= from) 1f else ((now - from) / (to - from)).coerceIn(0f, 1f)

/**
 * The timeline and the shape, in one place so it can be tuned without reading the animation.
 *
 * Milliseconds are the source; the fractions are derived, so moving a phase boundary cannot leave
 * the hand-off pointing at the wrong moment.
 */
object DiscCeremony {
    /** Centre stage: the disc fades up and settles at full size. */
    const val FadeInMs = 950

    /** It sinks toward the bottom, spinning up, while the room darkens. */
    const val SinkMs = 1150

    /** It fades out over the app that is already starting behind it. */
    const val FadeOutMs = 550

    const val TotalMs = FadeInMs + SinkMs + FadeOutMs

    /** The moment the caller should actually start the thing. */
    const val HandOffMs = FadeInMs + SinkMs

    val FadeInFraction = FadeInMs.toFloat() / TotalMs
    val HandOffFraction = HandOffMs.toFloat() / TotalMs

    /** Disc diameter, against the screen's short edge. */
    const val SizeFraction = 0.58f

    /** Scale it fades up from, and the scale it settles to once it has sunk. */
    const val EntryScale = 0.86f
    const val RestScale = 0.88f

    /**
     * Where the disc's CENTRE comes to rest, down the screen. 1.0 is the bottom edge, which means
     * the disc half-sinks out of the screen rather than sitting above it.
     */
    const val RestHeightFraction = 1.0f

    /** Hole and hub, as a fraction of the disc's radius. A CD's hole is ~15mm across a 120mm disc. */
    const val HoleFraction = 0.125f
    const val HubFraction = 0.21f

    const val SpinUpDegrees = 300f
    const val SpinOutDegrees = 420f

    /** How dark the room gets behind the disc. */
    const val MaxDim = 0.90f
}
