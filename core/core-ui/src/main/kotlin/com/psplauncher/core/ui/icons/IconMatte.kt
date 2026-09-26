package com.psplauncher.core.ui.icons

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.psplauncher.core.domain.model.IconLegibilityStyle
import kotlin.math.sqrt

internal val CONTOUR_RADIUS_DP = 1.75f
internal val SHADOW_OFFSET_DP = 1.25f

internal const val CONTOUR_MATTE_ALPHA = 0.95f
internal const val SHADOW_MATTE_ALPHA = 0.85f

val MatteLight = Color(0xFFEBF5FF)
val MatteDark = Color(0xFF000A12)

fun matteOffsets(style: IconLegibilityStyle): List<Offset> = when (style) {
    IconLegibilityStyle.NONE -> emptyList()
    IconLegibilityStyle.OFFSET_SHADOW -> listOf(Offset(1f, 1f))
    IconLegibilityStyle.CONTOUR_DARK,
    IconLegibilityStyle.CONTOUR_LIGHT,
    IconLegibilityStyle.CONTOUR_AUTO -> CONTOUR_OFFSETS
}

private val CONTOUR_OFFSETS: List<Offset> = run {
    val diagonal = 1f / sqrt(2f)
    listOf(
        Offset(0f, -1f),
        Offset(diagonal, -diagonal),
        Offset(1f, 0f),
        Offset(diagonal, diagonal),
        Offset(0f, 1f),
        Offset(-diagonal, diagonal),
        Offset(-1f, 0f),
        Offset(-diagonal, -diagonal),
    )
}

fun matteColorFor(style: IconLegibilityStyle, glyphColor: Color): Color? = when (style) {
    IconLegibilityStyle.NONE -> null
    IconLegibilityStyle.OFFSET_SHADOW -> MatteDark.copy(alpha = SHADOW_MATTE_ALPHA)
    IconLegibilityStyle.CONTOUR_DARK -> MatteDark.copy(alpha = CONTOUR_MATTE_ALPHA)
    IconLegibilityStyle.CONTOUR_LIGHT -> MatteLight.copy(alpha = CONTOUR_MATTE_ALPHA)
    IconLegibilityStyle.CONTOUR_AUTO ->
        if (glyphColor.luminance() < AUTO_LUMINANCE_THRESHOLD) {
            MatteLight.copy(alpha = CONTOUR_MATTE_ALPHA)
        } else {
            MatteDark.copy(alpha = CONTOUR_MATTE_ALPHA)
        }
}

private const val AUTO_LUMINANCE_THRESHOLD = 0.5f

val LocalIconLegibility = staticCompositionLocalOf { IconLegibilityStyle.DEFAULT }
