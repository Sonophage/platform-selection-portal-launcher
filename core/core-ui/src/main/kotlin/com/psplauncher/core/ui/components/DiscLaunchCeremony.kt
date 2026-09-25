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

/**
 * The disc that plays when a game, film, book or track is starting.
 *
 * The thing you picked turns into a disc — its cover art is the face, with a hole punched through
 * the middle — fades up in the centre of the screen, sinks toward the bottom while it spins up and
 * the room goes dark, and then fades out onto whatever opened.
 *
 * **The hand-off waits for the disc to be gone.** [onHandOff] fires once the disc's exit has
 * finished and the screen is black, so the app's own cold start happens *under* the 900ms hold
 * that follows rather than over a disc that is still visibly leaving. The ceremony costs the user
 * the time up to that point and no more.
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
    /** Start the thing. Fires once, after the disc's exit has finished. */
    onHandOff: () -> Unit,
    /** The overlay has nothing left to draw and should be removed. Fires once. */
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberUpdatedState so a recomposition that swaps the lambdas cannot restart the timeline
    // below — the animation is keyed on Unit deliberately: it must run exactly once per launch.
    val handOff by rememberUpdatedState(onHandOff)
    val finished by rememberUpdatedState(onFinished)

    // The opening cue, fired here rather than at the call site — see [LocalLaunchDiscCue] for
    // why it is ambient. Silent until the user assigns a track to the Launch Disc slot.
    val discCue = LocalLaunchDiscCue.current
    LaunchedEffect(Unit) { discCue() }

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
    // The case arrives, then the disc comes out from behind it. Two movements inside the fade-in,
    // where there used to be one: `caseIn` is the case alone on screen, `emerge` is the exchange.
    val caseIn = LinearOutSlowInEasing.transform(phase(now, 0f, DiscCeremony.at(DiscCeremony.CaseInMs)))
    val emerge = FastOutSlowInEasing.transform(
        phase(now, DiscCeremony.at(DiscCeremony.CaseInMs), DiscCeremony.FadeInFraction)
    )
    val caseAlpha = caseIn * (1f - phase(
        now,
        DiscCeremony.at(DiscCeremony.CaseFadeStartMs),
        DiscCeremony.FadeInFraction,
    ))
    // The disc is hidden BEHIND the case until it clears it, so this is an appearance, not a fade.
    val discAlpha = phase(
        now,
        DiscCeremony.at(DiscCeremony.DiscAppearMs),
        DiscCeremony.at(DiscCeremony.DiscOpaqueMs),
    )
    val sink = phase(now, DiscCeremony.FadeInFraction, DiscCeremony.SinkEndFraction)
    val spin = phase(now, DiscCeremony.SinkEndFraction, DiscCeremony.DiscOutStartFraction)

    // The tail, all measured from the moment the disc starts leaving.
    val outAt = { ms: Int -> DiscCeremony.at(DiscCeremony.DiscOutStartMs + ms) }
    val drop = phase(now, DiscCeremony.DiscOutStartFraction, outAt(DiscCeremony.DropMs))
    val blackout = phase(now, DiscCeremony.DiscOutStartFraction, outAt(DiscCeremony.BlackoutMs))
    val slitOpen = phase(now, outAt(DiscCeremony.SlitOpenStartMs), outAt(DiscCeremony.SlitOpenEndMs))
    val slitClose = FastOutSlowInEasing.transform(
        phase(now, outAt(DiscCeremony.SlitOpenEndMs), outAt(DiscCeremony.SlitCloseEndMs))
    )
    val slitFade = phase(now, outAt(DiscCeremony.SlitFadeStartMs), outAt(DiscCeremony.SlitFadeEndMs))
    // Two movements, not one. The DISC leaves early; the ROOM stays dark until the very end.
    //
    // The whole tail runs after the hand-off, with another app cold-starting under it. Opening the
    // vignette here used to reveal the XMB -- because the thing that was launched has not taken
    // the screen yet -- and then the app cut in over that. Black is what should be under a
    // hand-off, so black is what the tail holds; the reveal only happens if nothing ever arrived,
    // and it is still a slow open rather than a cut so a failed launch does not flash.
    val roomLeave = phase(now, DiscCeremony.RoomOpensFraction, 1f)

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

        // The last few percent to true black, under the disc.
        //
        // The iris stops at MaxDim, which is nearly black and was always enough when the ceremony
        // ended on a disc dissolving. It is not enough now: a slit of LIGHT needs something
        // absolute behind it, and 4% of grey across a whole screen is visible the moment there is
        // a bright thing next to it. Still multiplied by the room's re-open, so a launch that
        // never arrives reveals through this too rather than being held out by it.
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
                    // No fade on the way out. It falls through the floor instead, so the only
                    // thing that ends the disc is the disc leaving the frame.
                    alpha = discAlpha
                    val grow = DiscCeremony.EmergeScale + (1f - DiscCeremony.EmergeScale) * emerge
                    val shrink = 1f - (1f - DiscCeremony.RestScale) * sinkEase
                    scaleX = grow * shrink
                    scaleY = grow * shrink
                    // Out to the right and back to centre: it comes ROUND the case, not past it.
                    translationX = arcPx * kotlin.math.sin(Math.PI.toFloat() * emerge)
                    // Slow to fast, and only once it has been drawn in. Squaring the spin phase is
                    // constant angular ACCELERATION, so the disc is barely turning as it seats and
                    // is going properly by the time it fades -- a disc being spun up, not one that
                    // was already at speed. The sink contributes a token quarter-turn so the pull
                    // downward does not look completely rigid.
                    rotationZ = emerge * DiscCeremony.EmergeDegrees +
                        sinkEase * DiscCeremony.SinkDegrees +
                        spin * spin * DiscCeremony.SpinUpDegrees +
                        drop * DiscCeremony.DropDegrees
                    // Squared, so the fall accelerates the way a dropped thing does.
                    translationY = driftPx * sinkEase + dropPx * drop * drop
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

        // The case, declared AFTER the disc so it draws in FRONT of it.
        //
        // That order is the whole effect and it is the one thing here that cannot be swapped: the
        // disc has to be hidden behind something for "coming out from behind it" to read as
        // anything but a disc sliding sideways.
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

        // The drive slot.
        //
        // It opens where the disc went through the floor and shuts to a point exactly at the
        // hand-off, so the last thing on screen is a slot closing rather than a disc dissolving.
        // Drawn as one full-screen canvas rather than a laid-out Box because everything about it
        // is a fraction of the screen, and a Canvas can say that without a layout pass.
        if (slitOpen > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val a = slitOpen * (1f - slitFade)
                val halfW = (size.width * DiscCeremony.SlitWidthFraction / 2f) *
                    slitOpen * (1f - slitClose)
                if (a <= 0f || halfW <= 0f) return@Canvas
                val core = (size.height * DiscCeremony.SlitHeightFraction).coerceAtLeast(2f)
                val y = size.height * DiscCeremony.SlitYFraction
                val glow = core * DiscCeremony.SlitGlowSpread
                // The bloom first, then the hard line on top of it.
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
    /**
     * The case arrives and the disc comes out of it.
     *
     * Doubled from 850, which was the number the design bundle used and which read as correct on
     * paper and too quick on the panel: "the timing of everything is perfect but the cover coming
     * up and the cd sliding out. elongate the time if needed". Every sub-beat inside it doubled
     * with it, so the shape is unchanged and only the pace moved.
     *
     * The time is ADDED rather than borrowed. Taking it from the spin would have kept the
     * hand-off at six seconds and quietly retuned the one phase that was called perfect.
     */
    const val FadeInMs = 1700

    // The whole thing runs about six seconds to the hand-off now, up from four and a half. The
    // brief when this was written was "three to four seconds"; watching it on the handheld, three
    // was not a ceremony, it was a wipe, and four and a half was still short of what the redesign
    // asked for -- "right now this is set to 4.5 i would make it longer to about 6 seconds". The
    // number that matters is HandOffMs -- what the user actually waits through -- and the tail
    // behind it is unchanged.

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
     *
     * It has now absorbed that time twice. The 1500 ms that took the hand-off from 4500 to 6000
     * went here and nowhere else, for the reason above: every other phase is a movement with a
     * destination, and lengthening one of those makes the disc look slow rather than the ceremony
     * look long.
     */
    const val SpinMs = 3350

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

    /**
     * A millisecond on the timeline, as a fraction of it.
     *
     * The sub-phases below are written in milliseconds like every other number here and converted
     * through this, so none of them can drift out of step with [TotalMs] when a phase is retuned.
     */
    fun at(ms: Int): Float = ms.toFloat() / TotalMs

    // ── Inside FadeInMs: the case, and the disc coming out from behind it ────────────────────
    //
    // The disc used to simply fade up in the middle of an empty screen. It now arrives the way it
    // would out of a shelf: the CASE appears first, then slides left while the disc rolls out from
    // behind it on an arc, and the case is gone before the sink starts. The whole exchange fits
    // inside the fade-in — 1700ms, the same window the fade-in already owned — so nothing
    // downstream moves.

    /** The case fades up on its own, before anything comes out of it. */
    const val CaseInMs = 500

    /** ...and starts going once the disc is clear of it, finishing exactly as the sink begins. */
    const val CaseFadeStartMs = 1240

    /** The disc is behind the case until here, so it appears rather than fades. */
    const val DiscAppearMs = 500
    const val DiscOpaqueMs = 760

    /** Case edge, against the screen's short edge, and the corner it is cut with. */
    const val CaseSizeFraction = 0.648f
    const val CaseCornerFraction = 0.0343f

    /** How far the case travels left, against the screen's WIDTH — it is a sideways move. */
    const val CaseSlideFraction = -0.177f

    /** The case's own arrival scale, and how much it shrinks as it carries the disc out. */
    const val CaseEntryScale = 0.90f
    const val CaseShrink = 0.20f

    /**
     * The disc's detour, against the screen's width.
     *
     * Taken as `sin` across the emergence, so it swings out to the right and comes back to centre
     * rather than sliding across — the disc is coming round the case, not past it.
     */
    const val DiscArcFraction = 0.1875f

    /** Scale the disc comes out at. It is behind the case, so it starts small. */
    const val EmergeScale = 0.62f

    /** The turn it makes on the way out, before the sink's token quarter-turn. */
    const val EmergeDegrees = 160f

    // ── Inside DiscOutMs: the drop, and the slit of light ───────────────────────────────────
    //
    // All measured from [DiscOutStartMs]. The disc no longer fades: it FALLS out of the bottom of
    // the frame, and a slit of light opens where it went and closes to a point exactly at the
    // hand-off. That is what the ceremony ends on now — not a disc dissolving, but a drive slot
    // shutting.

    /** The fall itself, squared, so it accelerates like something dropped. */
    const val DropMs = 350
    const val DropDegrees = 600f

    /** How far it falls, against the short edge. Its centre is already at the bottom edge. */
    const val DropFraction = 0.648f

    /** The room goes the last of the way to true black as the disc leaves, so the slit reads. */
    const val BlackoutMs = 200

    /** The slit: opens as the disc clears the floor, then closes to a point at the hand-off. */
    const val SlitOpenStartMs = 230
    const val SlitOpenEndMs = 350
    const val SlitCloseEndMs = 650

    /** ...and goes out just behind the hand-off, so nothing is left lit under the launched app. */
    const val SlitFadeStartMs = 600
    const val SlitFadeEndMs = 670

    /** Where the slot sits down the screen, how wide it opens, and how thick the light is. */
    const val SlitYFraction = 0.935f
    const val SlitWidthFraction = 0.573f
    const val SlitHeightFraction = 0.0037f

    /** How far past the core bar the glow around the slit reaches. */
    const val SlitGlowSpread = 12f

    /** Disc diameter, against the screen's short edge. */
    const val SizeFraction = 0.72f

    /** The scale it settles to once it has sunk. */
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

    /** The spin-up itself. The turn it keeps making on the way down is [DropDegrees]. */
    const val SpinUpDegrees = 900f

    /** How dark the room gets behind the disc. */
    const val MaxDim = 0.96f

    /** The iris, as a fraction of the screen's short edge: wide open, then closed around the disc. */
    const val VignetteOpenRadius = 1.30f
    const val VignetteClosedRadius = 0.42f

    /** How far into the close the clear centre starts filling in, so it ends on solid black. */
    const val VignetteFillFrom = 0.72f
}
