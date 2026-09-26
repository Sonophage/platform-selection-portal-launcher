package com.psplauncher.core.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.IconLegibilityStyle

@Composable
fun CategoryIconGlyph(
    iconKey: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    val slotKey = catbarSlotKeyFor(iconKey)
    if (slotKey == null) {
        val platformId = consolePlatformIdFor(iconKey)
        if (platformId != null) {
            ConsoleIcon(
                platformId = platformId,
                contentDescription = contentDescription,
                modifier = modifier,
            )
            return
        }
    }
    val icon = LocalCustomIcons.current[slotKey] ?: LocalXmbIconOverrides.current[slotKey]
    if (icon != null) {
        CustomIconSurface(icon, contentDescription, modifier)
        return
    }
    PortalIcon(
        painter = painterResource(categoryIconFor(iconKey).resId),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

@Composable
fun OverrideGlyphSurface(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val style = LocalIconLegibility.current
    val matte = matteColorFor(style, DEFAULT_OVERRIDE_GLYPH_COLOR)
    if (matte == null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
        )
        return
    }
    val radiusPx = with(LocalDensity.current) {
        (if (style == IconLegibilityStyle.OFFSET_SHADOW) SHADOW_OFFSET_DP else CONTOUR_RADIUS_DP).dp.toPx()
    }
    IconMatteSurface(
        painter = BitmapPainter(bitmap),
        contentDescription = contentDescription,
        matteColor = matte,
        glyphColor = null,
        offsets = matteOffsets(style),
        radiusPx = radiusPx,
        modifier = modifier,
    )
}

private val DEFAULT_OVERRIDE_GLYPH_COLOR = Color(0xFF9E9E9E)
