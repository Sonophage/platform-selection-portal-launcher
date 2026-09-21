package com.psplauncher.core.domain.model

/**
 * When the on-screen button hints are shown, and for how long a pause — defined ONCE.
 *
 * This policy was spelled out in seven places: a default on [XMBUiState]-side state, another on
 * the Display settings state, a `?: 2.5f).coerceIn(1f, 5f)` in each of the two modules that read
 * the preference, the slider's own `valueRange`, the sublabel's "(1–5 seconds)" prose, and an
 * `IDLE_HINT_DELAY_MS = 2_500L` constant that no longer fed the gate at all. The preference KEY
 * carried a comment saying the two modules must match; the default and the clamp either side of
 * it carried nothing, so the pair that could actually drift was the unguarded one.
 *
 * Both readers and the slider now take their numbers from here, so there is nothing left to keep
 * in step.
 */
object ControllerHintPolicy {

    /** Display ▸ Context Menu Hint. On by default: the hints are how the buttons are discovered. */
    const val DEFAULT_ENABLED = true

    /**
     * Zero, meaning the hints stay up rather than waiting for a pause.
     *
     * They used to fade in after 2.5s of inactivity, which made them a reward for hesitating —
     * and they are also tappable now, so the bar is a control and not only a legend. A control
     * that appears once you have stopped trying to use it is worse than no control. The delay
     * survives as a preference for anyone who wants the old auto-hide back.
     */
    const val DEFAULT_DELAY_SECONDS = 0f

    const val MIN_DELAY_SECONDS = 0f
    const val MAX_DELAY_SECONDS = 5f

    /** The slider's range, and the bounds [clampDelay] enforces on a stored value. */
    val DELAY_RANGE: ClosedFloatingPointRange<Float> = MIN_DELAY_SECONDS..MAX_DELAY_SECONDS

    /**
     * A stored delay, brought inside the range.
     *
     * Needed because the range used to start at 1s: an established install can hold a value the
     * slider can still show, but a future narrowing would silently place the thumb off its track.
     */
    fun clampDelay(seconds: Float): Float = seconds.coerceIn(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS)

    /** Half-second steps across [DELAY_RANGE], as the slider counts them (gaps, not stops). */
    val DELAY_STEPS: Int = ((MAX_DELAY_SECONDS - MIN_DELAY_SECONDS) / 0.5f).toInt() - 1
}
