package com.psplauncher.core.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.psplauncher.themekit.ColorCascade

data class PFPColors(
    val waveColor: Color,
    val accentColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val backgroundOverlay: Color,
    val selectedItem: Color,
    val categoryBar: Color,

    val backgroundTop: Color = Color(0xFF26106C),
    val backgroundBottom: Color = Color(0xFF3B148C),

    val iconColor: Color = Color.White,
)

fun PFPColors.withWaveTint(wave: Color): PFPColors {
    val argb = wave.toArgb().toLong() and 0xFFFFFFFFL
    val anchors = ColorCascade.lightBackgroundAnchors(argb)
    return copy(
        waveColor        = wave,
        backgroundTop    = Color(anchors.first),
        backgroundBottom = Color(anchors.second),
    )
}

fun PFPColors.withArtTint(art: Color): PFPColors {
    val argb = art.toArgb().toLong() and 0xFFFFFFFFL
    return copy(
        accentColor = art,
        waveColor = art,
        backgroundTop = Color(ColorCascade.darken(argb, 0.16f)),
        backgroundBottom = Color(ColorCascade.darken(argb, 0.30f)),
    )
}

val LocalPFPColors = staticCompositionLocalOf {
    DefaultPFPColors
}

val DefaultPFPColors = PFPColors(
    waveColor         = Color(0xFF0055AA),
    accentColor       = Color(0xFFFFFFFF),
    textPrimary       = Color(0xFFFFFFFF),
    textSecondary     = Color(0xFFCCDDFF),
    backgroundOverlay = Color(0x88000000),
    selectedItem      = Color(0xFFFFFFFF),
    categoryBar       = Color(0x00000000),

    backgroundTop     = Color(0xFF0743A2),
    backgroundBottom  = Color(0xFF128BC9),
)

object PfpPalette {
    val Accent         = Color(0xFF4A90D9)
    val Subtext        = Color(0xFFAAAAAA)
    val Divider        = Color(0xFF2A2A2A)
    val SurfaceDim     = Color(0xFF10141C)
    val Surface        = Color(0xFF141A24)
    val SurfaceMid     = Color(0xFF181F2B)
    val SurfaceHigh    = Color(0xFF1B2230)
    val SurfaceHighest = Color(0xFF202838)
    val Outline        = Color(0xFF3A4356)
}

private val PfpDarkColorScheme = darkColorScheme(
    primary              = PfpPalette.Accent,
    onPrimary            = Color.White,
    secondary            = PfpPalette.Accent,
    onSecondary          = Color.White,
    background           = PfpPalette.SurfaceDim,
    onBackground         = Color.White,
    surface              = PfpPalette.Surface,
    onSurface            = Color.White,
    surfaceVariant       = PfpPalette.SurfaceHigh,
    onSurfaceVariant     = PfpPalette.Subtext,

    surfaceContainerLowest  = PfpPalette.SurfaceDim,
    surfaceContainerLow     = PfpPalette.Surface,
    surfaceContainer        = PfpPalette.SurfaceMid,
    surfaceContainerHigh    = PfpPalette.SurfaceHigh,
    surfaceContainerHighest = PfpPalette.SurfaceHighest,
    outline              = PfpPalette.Outline,
    outlineVariant       = PfpPalette.Divider,
)

@Composable
fun PFPTheme(
    colors: PFPColors = DefaultPFPColors,
    content: @Composable () -> Unit,
) {
    val textColors = remember(colors.textPrimary, colors.backgroundTop, colors.backgroundBottom) {
        resolveTextColors(colors.textPrimary, colors.backgroundTop, colors.backgroundBottom)
    }

    CompositionLocalProvider(
        LocalPFPColors provides colors,
        LocalPfpTextColors provides textColors,

        LocalContentColor provides textColors.primary,
    ) {
        MaterialTheme(
            colorScheme = PfpDarkColorScheme,
            typography = pfpTypography(),
            content = content,
        )
    }
}

