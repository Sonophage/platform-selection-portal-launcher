package com.psplauncher.feature.xmb.ui

/**
 * How far an unselected slot fades, by how many steps it sits from the cursor.
 *
 * ONE definition, read by both the crossbar and the item column, because "fades by distance" is a
 * single rule about the whole screen and two copies of it would be two rules the moment either was
 * tuned. The bar and the column are the two surfaces that carry it: "do the dim numbers from 1c
 * both vertically and horizontally".
 *
 * The shape is 1c's, taken from the prototype's own logic rather than read off a mock —
 * `op: big ? 1 : [1, .85, .55, .3][Math.min(d, 3)]`. THE BUNDLE DISAGREES WITH ITSELF HERE: 1a's
 * static mock steps .9 then .6, with no third stop. The running version won, because it is the one
 * that was actually navigated and because a two-stop ramp cannot say "far away" at all.
 *
 * EVERY UNSELECTED STOP IS THEN TAKEN FURTHER DOWN, by [PanelScale], because the published ramp
 * read too bright in the hand. Multiplied, not subtracted: a flat amount off would take the far
 * stop to almost nothing while changing the near stop by proportionally much less, and the ratios
 * between the stops are what make the ramp read as one gesture rather than three alphas.
 *
 * ONE KNOB, not three edited literals. Tuning this by hand three times is three chances to break
 * the proportions, and a ramp that has lost its shape still animates, still dims, and still looks
 * almost right — the worst kind of wrong. Scaling by construction makes that impossible.
 *
 * The cursor's own stop stays at 1. It is not dimmed at all, so there is nothing to take down, and
 * scaling it would quietly fade the thing the whole cue points at.
 *
 * Distances past the end of the ramp all land on its last stop. That is deliberate: a slot four
 * steps out and a slot nine steps out are both simply "not near", and continuing to fade would
 * reach zero and silently delete rows that are still on screen.
 */
internal object XmbDim {

    /** The ramp as 1c publishes it, before the panel scaling. */
    private val Published = floatArrayOf(1f, 0.85f, 0.55f, 0.30f)

    /**
     * How far the dimming was taken past the published ramp, by eye on a real 6" panel.
     *
     * Two passes, both his: "I think the dimming needs to go down about 20 percent more" took it
     * to 0.80, and "it's not quite there, a little more" took it to 0.68. It is one number so the
     * next pass is one number.
     */
    const val PanelScale = 0.68f

    private val Ramp = FloatArray(Published.size) { i ->
        // Index 0 is the cursor and is exempt by construction, not by a constant that happens to
        // be 1 — so no future scale can dim the selection by accident.
        if (i == 0) 1f else Published[i] * PanelScale
    }

    /** The furthest step the ramp distinguishes. Exposed so a test can walk past it. */
    const val LastStep = 3

    fun ranked(distance: Int): Float = Ramp[distance.coerceIn(0, LastStep)]

    /**
     * The same ramp, stretched to cover a list of [span] steps instead of three.
     *
     * [ranked] is right for the crossbar and the item column, where three steps IS the visible
     * range: the fourth row out is already at the edge of the screen. The right rail can be nine
     * tall, and there the clamp reads as a cliff followed by a flat — rows three through eight all
     * at exactly one alpha, which is what "steppy" looks like.
     *
     * SAME STOPS. This interpolates between the published numbers rather than introducing a second
     * set of them, and it pins both ends: distance 1 lands exactly on the first faded stop and
     * distance [span] lands exactly on the last, so a short rail is indistinguishable from what
     * [ranked] would have drawn and a long one simply gets the in-between values the ramp always
     * implied.
     *
     * The cursor's own stop is still exempt and still returns 1 before any of this runs.
     */
    fun smoothed(distance: Int, span: Int): Float {
        if (distance <= 0) return 1f
        // A one-row list has no gap to interpolate across; anything past the ends clamps.
        val steps = span.coerceAtLeast(2)
        val t = ((distance - 1).toFloat() / (steps - 1)).coerceIn(0f, 1f)
        val pos = 1f + t * (LastStep - 1)
        val lo = pos.toInt().coerceIn(1, LastStep - 1)
        val f = pos - lo
        return (Published[lo] + (Published[lo + 1] - Published[lo]) * f) * PanelScale
    }
}
