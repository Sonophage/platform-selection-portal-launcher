package com.psplauncher.core.domain.model

object ControllerHintPolicy {
    const val DEFAULT_ENABLED = true

    const val DEFAULT_DELAY_SECONDS = 0f

    const val MIN_DELAY_SECONDS = 0f
    const val MAX_DELAY_SECONDS = 5f

    val DELAY_RANGE: ClosedFloatingPointRange<Float> = MIN_DELAY_SECONDS..MAX_DELAY_SECONDS

    fun clampDelay(seconds: Float): Float = seconds.coerceIn(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS)

    val DELAY_STEPS: Int = ((MAX_DELAY_SECONDS - MIN_DELAY_SECONDS) / 0.5f).toInt() - 1
}
