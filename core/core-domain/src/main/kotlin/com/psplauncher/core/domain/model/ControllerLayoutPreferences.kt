package com.psplauncher.core.domain.model

// ── Confirm / Back button layout ──────────────────────────────────────────────

enum class ConfirmBackLayout {
    /** Default: A / Cross = Confirm,  B / Circle = Back  */
    STANDARD,
    /** Reversed: B / Circle = Confirm, A / Cross = Back */
    REVERSED,
}

fun ConfirmBackLayout.displayLabel(): String = when (this) {
    ConfirmBackLayout.STANDARD -> "Standard (A = Confirm, B = Back)"
    ConfirmBackLayout.REVERSED -> "Reversed (B = Confirm, A = Back)"
}

// ── Secondary button (X / Y) layout ───────────────────────────────────────────

enum class XYLayout {
    /** Default: Y = Options, X = Sort */
    STANDARD,
    /** Swapped: X = Options, Y = Sort */
    SWAPPED,
}

fun XYLayout.displayLabel(): String = when (this) {
    XYLayout.STANDARD -> "Standard (Y = Options, X = Sort)"
    XYLayout.SWAPPED  -> "Swapped (X = Options, Y = Sort)"
}

// ── Controller display / prompt style ─────────────────────────────────────────

enum class ControllerDisplayType {
    XBOX,
    NINTENDO,
    PLAYSTATION,
}

fun ControllerDisplayType.displayLabel(): String = when (this) {
    ControllerDisplayType.XBOX        -> "Xbox"
    ControllerDisplayType.NINTENDO    -> "Nintendo"
    ControllerDisplayType.PLAYSTATION -> "PlayStation"
}

// ── Held-navigation scroll speed ──────────────────────────────────────────────

/** How fast held D-pad/stick navigation repeats. Affects the repeat ramp, not single presses. */
enum class ScrollSpeed {
    RELAXED,
    STANDARD,
    FAST,
}

/**
 * How far the analog stick must move before it navigates, and how far before it means "fast".
 *
 * Both numbers matter and they were both fixed constants. [deadZone] is how far the stick must
 * deflect to register a direction at all. [fullTilt] is the deflection past which a hold is read
 * as an explicit "scroll fast" gesture rather than an ordinary one.
 *
 * The old fixed pair was 0.50 / 0.90, and 0.90 is trivially easy to reach on a handheld thumbstick
 * -- you push it and you are at 1.0. So essentially every stick push was read as full tilt, which
 * on the old repeat loop skipped the acceleration ramp entirely and went straight to the fastest
 * interval. The stick was twice the D-pad's speed from its very first repeat, for the same intent.
 *
 * [STANDARD] is deliberately calmer than that old pair; [HIGH] is roughly what it used to do.
 */
enum class StickSensitivity(val deadZone: Float, val fullTilt: Float) {
    /** Deliberate. Push most of the way before anything happens, full speed only at the stop. */
    LOW(deadZone = 0.70f, fullTilt = 0.99f),
    /** The tuned default. */
    STANDARD(deadZone = 0.58f, fullTilt = 0.95f),
    /** Roughly the old fixed behaviour: light touch, fast to reach full tilt. */
    HIGH(deadZone = 0.45f, fullTilt = 0.88f);

    companion object {
        /** Tolerant parse for the persisted preference; unknown/blank falls back to [STANDARD]. */
        fun fromName(value: String?): StickSensitivity =
            entries.firstOrNull { it.name == value } ?: STANDARD
    }
}

fun StickSensitivity.displayLabel(): String = when (this) {
    StickSensitivity.LOW      -> "Low"
    StickSensitivity.STANDARD -> "Standard"
    StickSensitivity.HIGH     -> "High"
}

fun ScrollSpeed.displayLabel(): String = when (this) {
    ScrollSpeed.RELAXED  -> "Relaxed"
    ScrollSpeed.STANDARD -> "Standard"
    ScrollSpeed.FAST     -> "Fast"
}

// ── Bundled preference snapshot ───────────────────────────────────────────────

data class ControllerLayoutPrefs(
    val confirmBackLayout: ConfirmBackLayout   = ConfirmBackLayout.STANDARD,
    val xyLayout: XYLayout                     = XYLayout.STANDARD,
    val displayType: ControllerDisplayType     = ControllerDisplayType.XBOX,
    val scrollSpeed: ScrollSpeed               = ScrollSpeed.STANDARD,
    val stickSensitivity: StickSensitivity     = StickSensitivity.STANDARD,
    // D-pad LEFT backs out of a flyout, folder or settings screen wherever LEFT is not already
    // doing something on the focused element. Defaults ON: every press it claims is a documented
    // no-op today, and it is what was asked for. Gates the D-pad only — the leftward touch swipe
    // is unconditional, the way the left-edge pull always has been.
    val leftBacksOut: Boolean                  = true,
)
