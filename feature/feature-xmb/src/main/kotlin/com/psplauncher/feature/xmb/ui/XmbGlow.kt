package com.psplauncher.feature.xmb.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The warm bloom behind whatever the cursor is on.
 *
 * The XMB has never had one. `XMBCategoryBar` said so outright — "selection is conveyed by size
 * and alpha (no halo)" — and that held while the dim was the only other cue. It is a weaker cue
 * now: [XmbDim] takes the near neighbours to .58, so the gap between "selected" and "one step
 * away" is doing more work than it used to and a second, additive signal earns its place.
 *
 * The colour is the redesign's, in both places it appears: `rgba(255,220,170,...)`, the amber the
 * whole September scheme is drawn in. The two alphas are its own — .55 on the crossbar icon, .45
 * behind an item — and they are different because a caticon is a lone glyph with nothing around it
 * while a row's icon sits beside its own bright label.
 *
 * REACH IS A FRACTION OF THE ICON, NOT A DP. The design states 18px and 36px blurs on a 1920-wide
 * frame, which are only those numbers on a 1920-wide panel; the crossbar's icon size is already
 * themeable through XmbLayoutSpec and Icon Size is a setting the user can change. Pinning the
 * bloom to dp would leave it the same size while the thing it is blooming around grew, which reads
 * as a ring at one size and a smudge at another.
 *
 * It is a radial gradient rather than a true drop-shadow of the glyph's silhouette. A real one
 * needs `Modifier.blur`, which is RenderEffect and so does nothing below API 31 while minSdk here
 * is 29 — a glow that silently is not there on two API levels is worse than one that is slightly
 * rounder than the shape it surrounds.
 */
internal object XmbGlow {

    /** rgba(255,220,170) — 1a's tile bloom and 1c's caticon drop-shadow are the same colour. */
    val Color = Color(0xFFFFDCAA)

    /**
     * 1c: `drop-shadow(0 0 18px rgba(255,220,170,.55))` against a 124px icon.
     *
     * The REACH is wider than the design's 18px because this is a gradient standing in for a
     * gaussian: a blur's visible light ends well before its stated radius, while a gradient stops
     * exactly at its own, so matching the number would produce a tighter glow than the blur it is
     * imitating. Matched by eye on the panel instead, which is the only place the difference shows.
     */
    const val CategoryAlpha = 0.55f
    const val CategoryReach = 0.30f

    /**
     * 1a: a 36px bloom with 6px spread at .45, against a 176px tile.
     *
     * Tighter and stronger than the caticon's, which is the opposite of what the design's raw
     * numbers suggest, for a reason only the panel shows: this is drawn behind the LEADING ICON
     * SLOT, and the slot is wider than most of the glyphs that sit in it. The same reach that
     * bloomed a 124px caticon spread this one over a box half again as wide and left almost
     * nothing visible. The design's tile fills its slot; ours frequently does not.
     */
    const val RowAlpha = 0.58f
    const val RowReach = 0.26f
}

/**
 * Draws [XmbGlow] behind the content, sized from the content itself.
 *
 * [visible] is the animated 0..1 selection, not a boolean, so the bloom arrives and leaves with
 * the same spring as the scale and the alpha rather than popping a frame ahead of them.
 */
internal fun Modifier.xmbFocusGlow(
    visible: Float,
    reach: Float,
    alpha: Float,
): Modifier = drawBehind {
    if (visible <= 0f || alpha <= 0f) return@drawBehind
    // Half the icon, plus the reach on each side: the gradient's edge is where the blur would
    // have faded out, so the bloom grows with whatever it is drawn behind.
    val radius = size.minDimension / 2f * (1f + 2f * reach)
    if (radius <= 0f) return@drawBehind
    val peak = alpha * visible
    drawCircle(
        brush = Brush.radialGradient(
            // Five stops on a roughly gaussian falloff, not three on a straight line.
            //
            // The first attempt held 80% out to mid-radius and then ran to zero over the rest,
            // which on the panel drew a visible DISC behind the icon rather than a bloom around
            // it: the eye finds the edge of a gradient that is still bright when it starts
            // falling. Most of the light now lives in the middle third and the tail is long and
            // faint, so there is no radius at which the glow stops.
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
