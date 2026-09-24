package com.psplauncher.feature.xmb.ui

/**
 * How far an unselected slot fades, by how many steps it sits from the cursor.
 *
 * ONE definition, read by both the crossbar and the item column, because "fades by distance" is a
 * single rule about the whole screen and two copies of it would be two rules the moment either was
 * tuned. The bar and the column are the two surfaces that carry it: "do the dim numbers from 1c
 * both vertically and horizontally".
 *
 * The numbers are 1c's, taken from the prototype's own logic rather than read off a mock —
 * `op: big ? 1 : [1, .85, .55, .3][Math.min(d, 3)]`. THE BUNDLE DISAGREES WITH ITSELF HERE: 1a's
 * static mock steps .9 then .6, with no third stop. The running version wins, because it is the
 * one that was actually navigated and because a two-stop ramp cannot say "far away" at all.
 *
 * Distances past the end of the ramp all land on its last stop. That is deliberate: a slot four
 * steps out and a slot nine steps out are both simply "not near", and continuing to fade would
 * reach zero and silently delete rows that are still on screen.
 */
internal object XmbDim {

    private val Ramp = floatArrayOf(1f, 0.85f, 0.55f, 0.30f)

    /** The furthest step the ramp distinguishes. Exposed so a test can walk past it. */
    const val LastStep = 3

    fun ranked(distance: Int): Float = Ramp[distance.coerceIn(0, LastStep)]
}
