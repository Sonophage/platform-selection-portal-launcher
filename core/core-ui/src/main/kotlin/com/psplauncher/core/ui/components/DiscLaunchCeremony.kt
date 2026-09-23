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
import com.psplauncher.core.ui.sound.MenuSound
import com.psplauncher.core.ui.sound.LocalMenuSounds
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

    // The opening cue, fired here rather than at the call site.
    //
    // The ceremony is what it announces, and it has six entry points — games, films, books,
    // tracks, apps and the settings preview. A sound wired into the callers is a sound five of
    // them have and the sixth quietly does not.
    //
    // SYSTEM_BROWSE is the crossbar's own "you have moved to another thing" cue, which is what
    // this is: the screen is about to become somewhere else.
    val menuSounds = LocalMenuSounds.current
    LaunchedEffect(Unit) { menuSounds(MenuSound.SYSTEM_BROWSE) }

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
    val sink = phase(now, DiscCeremony.FadeInFraction, DiscCeremony.SinkEndFraction)
    val spin = phase(now, DiscCeremony.SinkEndFraction, DiscCeremony.DiscOutStartFraction)
    // Two movements, not one. The DISC leaves early; the ROOM stays dark until the very end.
    //
    // The whole tail runs after the hand-off, with another app cold-starting under it. Opening the
    // vignette here used to reveal the XMB -- because the thing that was launched has not taken
    // the screen yet -- and then the app cut in over that. Black is what should be under a
    // hand-off, so black is what the tail holds; the reveal only happens if nothing ever arrived,
    // and it is still a slow open rather than a cut so a failed launch does not flash.
    val discLeave = phase(now, DiscCeremony.DiscOutStartFraction, DiscCeremony.DiscGoneFraction)
    val roomLeave = phase(now, DiscCeremony.RoomOpensFraction, 1f)

    val riseEase = LinearOutSlowInEasing.transform(rise)
    val sinkEase = FastOutSlowInEasing.transform(sink)
    // The vignette runs across the sink AND the spin as one movement, so the room keeps closing in
    // the whole time the disc is seated rather than stopping the moment it lands.
    val closeEase = FastOutSlowInEasing.transform(phase(now, DiscCeremony.FadeInFraction, DiscCeremony.DiscOutStartFraction))
    val leaveEase = FastOutSlowInEasing.transform(roomLeave)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val discSize = minOf(maxWidth, maxHeight) * DiscCeremony.SizeFraction
        // Where the disc ends up: its centre lands at RestHeightFraction down the screen. Measured
        // from the centre, because that is where it starts.
        val driftPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            (maxHeight.toPx() * (DiscCeremony.RestHeightFraction - 0.5f))
        }

        // The room closes in like an iris rather than dimming flat: a black ring whose clear
        // centre shrinks around the disc and follows it down.
        //
        // And then OPENS again, outward from the disc, over the whole fade. It used to be held
        // black until the overlay was simply taken away, which is a cut rather than a transition
        // and read as a flicker. Retreating from the disc to the edges gives the last beat
        // somewhere to go: the black is already thin and nearly clear by the time the composition
        // leaves, so there is no frame where a full-black screen becomes something else at once.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    val centre = Offset(size.width / 2f, size.height / 2f + driftPx * sinkEase)
                    // closeEase shuts the iris, leaveEase re-opens it — one expression, so the
                    // radius it opens from is exactly the radius it closed to.
                    val shut = closeEase * (1f - leaveEase)
                    val radius = (DiscCeremony.VignetteOpenRadius -
                        (DiscCeremony.VignetteOpenRadius - DiscCeremony.VignetteClosedRadius) * shut)
                        .coerceAtLeast(0.05f) * size.minDimension
                    // The centre only fills in at the very end of the close, so the disc spins in
                    // clear air, and it empties again first on the way out.
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

        Box(
            modifier = Modifier
                .size(discSize)
                .graphicsLayer {
                    alpha = riseEase * (1f - discLeave)
                    val grow = DiscCeremony.EntryScale + (1f - DiscCeremony.EntryScale) * riseEase
                    val shrink = 1f - (1f - DiscCeremony.RestScale) * sinkEase
                    scaleX = grow * shrink
                    scaleY = grow * shrink
                    // Slow to fast, and only once it has been drawn in. Squaring the spin phase is
                    // constant angular ACCELERATION, so the disc is barely turning as it seats and
                    // is going properly by the time it fades -- a disc being spun up, not one that
                    // was already at speed. The sink contributes a token quarter-turn so the pull
                    // downward does not look completely rigid.
                    rotationZ = sinkEase * DiscCeremony.SinkDegrees +
                        spin * spin * DiscCeremony.SpinUpDegrees +
                        discLeave * DiscCeremony.SpinOutDegrees
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
    const val FadeInMs = 850

    // The whole thing runs about four and a half seconds to the hand-off now, up from under
    // three. The brief when this was written was "three to four seconds"; watching it on the
    // handheld, three was not a ceremony, it was a wipe. The number that matters is HandOffMs --
    // what the user actually waits through -- and the tail behind it is unchanged.

    /** It is drawn downward until its centre reaches the bottom edge — half in, half out. */
    const val SinkMs = 1150

    /**
     * Seated at the bottom, spinning up slow to fast, in a room that is still closing in.
     *
     * This is where the extra time goes when the ceremony is asked to be longer, and it is the
     * only phase that can absorb it. The fade-in is the disc arriving and the sink is it being
     * drawn under; both are movements with an end, and stretching either just makes the disc
     * slow. The spin is the part with no destination — it can run as long as the ceremony wants
     * and still read as a machine getting up to speed.
     */
    const val SpinMs = 1850

    /**
     * The disc's own departure — it shrinks away and the iris closes behind it.
     *
     * BEFORE the hand-off, and that is the whole point of splitting the old single "fade out"
     * into two. The disc used to start leaving at the same instant the caller was released, on
     * the theory that the launched app needed a moment to appear and the fade was the overlap to
     * spend. It does not need a moment: with the window transition suppressed the app's window
     * simply appears, often inside a few hundred milliseconds, and it appeared ON TOP of a disc
     * that was still visibly leaving. That is the "it gets cut off" -- not the ceremony being
     * short, the last thing in it being interrupted.
     *
     * Nothing can now take the screen until the disc has finished going.
     */
    const val DiscOutMs = 650

    /**
     * Black, after the hand-off, while the launched app cold-starts underneath.
     *
     * The only part that is usually invisible, and the only part that may be. It has to stay long
     * enough that a launch which never arrives reveals rather than snaps: at 900 in the old
     * single-phase shape the room came back in about a third of a second and read as a cut.
     */
    const val HoldMs = 900

    const val TotalMs = FadeInMs + SinkMs + SpinMs + DiscOutMs + HoldMs

    /**
     * The moment the caller should actually start the thing — once the disc has GONE.
     *
     * It has moved twice, each time because an activity's window takes the screen the instant it
     * is ready and nothing drawn by the launcher survives that. First it fired at the top of the
     * spin, and the spin was never seen. Then at the top of the fade, and the disc's exit was cut
     * in half. It now fires when there is nothing left on screen to interrupt: the disc is gone,
     * the room is black, and the hold behind this is what the cold start happens under.
     */
    const val HandOffMs = FadeInMs + SinkMs + SpinMs + DiscOutMs

    /** Start of the spin: the disc has arrived and is about to be spun up. */
    val SinkEndFraction = (FadeInMs + SinkMs).toFloat() / TotalMs

    /**
     * The disc starts leaving. The spin is over; the caller is still waiting.
     *
     * Published in milliseconds as well as a fraction because GameBootGate schedules its sound
     * against this moment, and a second copy of the sum living over there is exactly the pair
     * that drifts the first time a phase is retuned.
     */
    const val DiscOutStartMs = FadeInMs + SinkMs + SpinMs
    val DiscOutStartFraction = DiscOutStartMs.toFloat() / TotalMs

    /** The disc is gone, and the caller is released. One instant, by construction. */
    val DiscGoneFraction = HandOffMs.toFloat() / TotalMs
    val HandOffFraction = DiscGoneFraction

    /**
     * When the room starts opening again, part-way through the hold.
     *
     * Only a launch that never arrives gets this far — anything that did arrive is already
     * covering the screen. It is late in the hold on purpose, so the reveal reads as a slow open
     * rather than a snap back to the XMB.
     */
    private const val RoomOpensShare = 0.30f
    val RoomOpensFraction = HandOffFraction + (1f - HandOffFraction) * RoomOpensShare

    val FadeInFraction = FadeInMs.toFloat() / TotalMs

    /** Disc diameter, against the screen's short edge. */
    const val SizeFraction = 0.72f

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

    /** A token turn while it is being drawn in, so the pull down is not rigid. */
    const val SinkDegrees = 70f

    /** The spin-up itself, and the turn it keeps making while it fades. */
    const val SpinUpDegrees = 900f
    const val SpinOutDegrees = 780f

    /** How dark the room gets behind the disc. */
    const val MaxDim = 0.96f

    /** The iris, as a fraction of the screen's short edge: wide open, then closed around the disc. */
    const val VignetteOpenRadius = 1.30f
    const val VignetteClosedRadius = 0.42f

    /** How far into the close the clear centre starts filling in, so it ends on solid black. */
    const val VignetteFillFrom = 0.72f
}
