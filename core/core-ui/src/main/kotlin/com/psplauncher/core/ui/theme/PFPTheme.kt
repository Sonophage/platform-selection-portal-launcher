package com.psplauncher.core.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
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
    // Background gradient anchors behind the wave (top → bottom).
    val backgroundTop: Color = Color(0xFF26106C),
    val backgroundBottom: Color = Color(0xFF3B148C),
    // Unified tint for the XMB's silhouette icon art (catbar_*/sysicon_*), applied via
    // PortalIcon. White = the icons' native color, i.e. visually a no-op default.
    // See docs/icon-system-plan.md.
    val iconColor: Color = Color.White,
)

/**
 * Re-tints a palette to [wave] and re-derives the background gradient from it, so the gradient
 * always matches whatever hue the wave is.
 *
 * ONE definition, because three callers must agree on what "the theme, but this colour" means:
 * the XMB when a category tints the wave, the detail pages when they take their colour from a
 * game's artwork instead of the user's scheme, and the desktop Theme Studio through the same
 * ColorCascade math underneath. A second copy would let one surface drift a shade from another
 * with nothing to catch it.
 */
fun PFPColors.withWaveTint(wave: Color): PFPColors {
    val argb = wave.toArgb().toLong() and 0xFFFFFFFFL
    val anchors = ColorCascade.lightBackgroundAnchors(argb)
    return copy(
        waveColor        = wave,
        backgroundTop    = Color(anchors.first),
        backgroundBottom = Color(anchors.second),
    )
}

/**
 * Re-tints a palette to a colour taken from ARTWORK, for a page that is mostly artwork.
 *
 * Not [withWaveTint]. That one builds the XMB's gradient, which lightens toward the wave by 0.28
 * so the wave has something bright to sit against -- and a saturated artwork colour through it
 * produced a page that was a sheet of that colour at full strength, with the artwork's own hues
 * lost in it and muted text nearly gone. A detail page wants the opposite: a DEEP version of the
 * hue, so the page reads as dark and the colour belongs to the art and the cursor.
 *
 * [accentColor] does take the colour at full strength: it is the one thing on the page that is
 * supposed to be the game's colour outright.
 */
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
    // Classic PSP "Original" blue gradient, sampled from the real XMB wave video: a saturated azure
    // top easing to a brighter cyan-blue near the wave (not a washed-out sky-blue).
    backgroundTop     = Color(0xFF0743A2),
    backgroundBottom  = Color(0xFF128BC9),
)

/**
 * The single source for the app's flat UI palette. The Material scheme below and the settings
 * screens (SettingsScaffold's SettingsAccent/SettingsSubtext/SettingsDivider) both draw from
 * here, so an accent or surface change propagates everywhere at once.
 */
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

// Dark Material scheme derived from the app's palette. Any stock M3 component that doesn't set
// explicit colors (AlertDialogs, TextButtons, text fields inside dialogs) inherits this, so
// system prompts match the XMB theme instead of falling back to Material's light purple.
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
    // AlertDialog containers draw from the surfaceContainer roles.
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
    // The theme's text roles, resolved against the theme's own backdrop, once per theme change.
    //
    // This used to pass [colors.textPrimary] straight through and keep `secondary` pinned to
    // PfpPalette.Subtext, with a note that deriving it was a later phase's job "where it is a
    // deliberate decision rather than a side effect". This is that decision, and the device made
    // it: on the pale Silver scheme white text on a near-white wallpaper left the whole unselected
    // tier at roughly 1.3:1, and BOTH candidate sublabel colours — the XMB's cool 0xAAC8DAF2 and
    // PfpPalette.Subtext's neutral #AAAAAA — are light. Picking between them could not have
    // worked, because the problem was never the hue.
    //
    // So the pole is measured rather than assumed. [ensureReadable] flips primary to black when
    // white cannot clear 3:1 on the theme's own mid-tone, and when it does flip, secondary and
    // inactive follow it into the dark family instead of staying light against a light page. The
    // 3.0 floor is the App Drawer's, for its stated reason: classic PSP blue sits at ~3.9:1 and
    // only genuinely pale washes should flip.
    //
    // NOT the measured-backdrop clamp: [TextProtection] still resolves to Shadow for everyone,
    // because nothing reads it yet and a plate engine is a feature rather than a colour fix.
    val textColors = remember(colors.textPrimary, colors.backgroundTop, colors.backgroundBottom) {
        resolveTextColors(colors.textPrimary, colors.backgroundTop, colors.backgroundBottom)
    }

    CompositionLocalProvider(
        LocalPFPColors provides colors,
        LocalPfpTextColors provides textColors,
        // Every Text() that does not pass an explicit color= reads LocalContentColor, so providing
        // it here is the one line that reaches the long tail of call sites without touching them.
        LocalContentColor provides textColors.primary,
    ) {
        MaterialTheme(colorScheme = PfpDarkColorScheme, content = content)
    }
}

object PFPThemeTokens {
    val colors: PFPColors
        @Composable get() = LocalPFPColors.current
}
