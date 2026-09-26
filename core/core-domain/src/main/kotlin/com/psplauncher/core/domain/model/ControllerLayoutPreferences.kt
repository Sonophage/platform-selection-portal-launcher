package com.psplauncher.core.domain.model

enum class ConfirmBackLayout {
    STANDARD,

    REVERSED,
}

fun ConfirmBackLayout.displayLabel(): String = when (this) {
    ConfirmBackLayout.STANDARD -> "Standard (A = Confirm, B = Back)"
    ConfirmBackLayout.REVERSED -> "Reversed (B = Confirm, A = Back)"
}

enum class XYLayout {
    STANDARD,

    SWAPPED,
}

fun XYLayout.displayLabel(): String = when (this) {
    XYLayout.STANDARD -> "Standard (Y = Options, X = Sort)"
    XYLayout.SWAPPED  -> "Swapped (X = Options, Y = Sort)"
}

enum class ControllerDisplayType {
    XBOX,
    NINTENDO,
    PLAYSTATION,

    KEYBOARD,

    TOUCH,
}

fun ControllerDisplayType.displayLabel(): String = when (this) {
    ControllerDisplayType.XBOX        -> "Xbox"
    ControllerDisplayType.NINTENDO    -> "Nintendo"
    ControllerDisplayType.PLAYSTATION -> "PlayStation"
    ControllerDisplayType.KEYBOARD    -> "Keyboard"
    ControllerDisplayType.TOUCH       -> "Touch"
}

enum class ScrollSpeed {
    RELAXED,
    STANDARD,
    FAST,
}

enum class StickSensitivity(val deadZone: Float, val fullTilt: Float) {
    LOW(deadZone = 0.70f, fullTilt = 0.99f),

    STANDARD(deadZone = 0.58f, fullTilt = 0.95f),

    HIGH(deadZone = 0.45f, fullTilt = 0.88f);

    companion object {
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

data class ControllerLayoutPrefs(
    val confirmBackLayout: ConfirmBackLayout   = ConfirmBackLayout.STANDARD,
    val xyLayout: XYLayout                     = XYLayout.STANDARD,
    val displayType: ControllerDisplayType     = ControllerDisplayType.XBOX,
    val scrollSpeed: ScrollSpeed               = ScrollSpeed.STANDARD,
    val stickSensitivity: StickSensitivity     = StickSensitivity.STANDARD,

    val leftBacksOut: Boolean                  = true,
)
