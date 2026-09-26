package com.psplauncher.core.ui.wave

enum class WaveStyle {
    ANIMATED,
    REDUCED,
    STATIC,
    REDUCED_STATIC;

    val animated: Boolean get() = this == ANIMATED || this == REDUCED

    val reduced: Boolean get() = this == REDUCED || this == REDUCED_STATIC

    val frozen: WaveStyle
        get() = when (this) {
            ANIMATED, STATIC -> STATIC
            REDUCED, REDUCED_STATIC -> REDUCED_STATIC
        }
}
