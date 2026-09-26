package com.psplauncher.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp

@Immutable
sealed interface TextProtection {
    data object None : TextProtection

    data object Shadow : TextProtection

    @Immutable
    data class Plate(val color: Color, val alpha: Float) : TextProtection

    @Immutable
    data class Outline(val color: Color) : TextProtection
}

@Immutable
data class PfpTextColors(

    val primary: Color,

    val secondary: Color,

    val inactive: Color,

    val destructive: Color,

    val requested: Color,

    val adjusted: Boolean,

    val achievedRatio: Float,

    val protection: TextProtection,
)

val DefaultPfpTextColors = PfpTextColors(
    primary = Color.White,
    secondary = PfpPalette.Subtext,
    inactive = Color(0xCCD8E6FF),
    destructive = Color(0xFFFF6B6B),
    requested = Color.White,
    adjusted = false,
    achievedRatio = 21f,
    protection = TextProtection.Shadow,
)

val LocalPfpTextColors = staticCompositionLocalOf { DefaultPfpTextColors }

fun resolveTextColors(
    requested: Color,
    backgroundTop: Color,
    backgroundBottom: Color,
    minContrast: Float = 3.0f,
): PfpTextColors {
    val backdrop = lerp(backgroundTop, backgroundBottom, 0.5f)
    val primary = ensureReadable(requested, backdrop, minContrast)
    val darkFamily = primary.luminance() < 0.5f
    return DefaultPfpTextColors.copy(
        primary = primary,
        requested = requested,
        adjusted = primary != requested,
        achievedRatio = contrastRatio(primary, backdrop).toFloat(),
        secondary = if (darkFamily) lerp(Color.Black, Color.White, 0.28f) else DefaultPfpTextColors.secondary,
        inactive = if (darkFamily) lerp(Color.Black, Color.White, 0.46f) else DefaultPfpTextColors.inactive,
    )
}
