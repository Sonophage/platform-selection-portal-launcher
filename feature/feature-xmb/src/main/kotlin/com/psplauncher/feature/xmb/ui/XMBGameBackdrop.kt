package com.psplauncher.feature.xmb.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

// ── The crossbar's per-game backdrop: still art over the video snap ───────────
//
// A background snap used to cover the still art completely, so the whole screen went from a
// photograph to a clip the instant the cursor landed on a game with one. The still now stays on
// the LEFT, at full strength, and fades out across the middle of the screen: the crossbar and its
// labels sit on solid artwork, and the open right-hand side is where the clip plays. One screen
// that reads solid-to-motion, left to right.
//
// The left is the half that has to be solid. That is where the category bar, the item list, the
// focused game's name and its metadata line all live, and a clip changes the luminance under every
// one of them on every frame.

/** The still is at full strength out to here. */
const val XMB_STILL_SOLID_END = 0.40f

/** ...and completely gone by here, leaving the clip. */
const val XMB_STILL_FADE_END = 0.68f

/**
 * The alpha mask applied to the still artwork when a snap is playing behind it.
 *
 * A pure function because it is the whole contract: solid on the left, clear on the right, and one
 * transition between them. Reversed, it would hide the clip behind the labels and play it under
 * nothing — which renders perfectly and is the design backwards.
 */
fun xmbStillOverVideoStops(): Array<Pair<Float, Color>> = arrayOf(
    0f to Color.Black,
    XMB_STILL_SOLID_END to Color.Black,
    XMB_STILL_FADE_END to Color.Transparent,
    1f to Color.Transparent,
)

/**
 * Fades this layer out from left to right, revealing whatever is drawn beneath it.
 *
 * DstIn against a black-to-transparent gradient: black keeps the pixel, transparent drops it. The
 * offscreen compositing strategy is required, not a tuning knob — without it the blend applies to
 * the whole window rather than to this layer, and the gradient erases the screen.
 */
fun Modifier.xmbStillOverVideo(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(colorStops = xmbStillOverVideoStops()),
            blendMode = BlendMode.DstIn,
        )
    }
