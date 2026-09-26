package com.psplauncher.core.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.IconLegibilityStyle
import com.psplauncher.core.ui.theme.LocalPFPColors

@Composable
fun PortalIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalPFPColors.current.iconColor,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val style = LocalIconLegibility.current
    val matte = matteColorFor(style, tint)
    if (matte == null) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn),
            modifier = modifier,
        )
        return
    }

    val radiusPx = with(LocalDensity.current) {
        (if (style == IconLegibilityStyle.OFFSET_SHADOW) SHADOW_OFFSET_DP else CONTOUR_RADIUS_DP).dp.toPx()
    }
    IconMatteSurface(
        painter = painter,
        contentDescription = contentDescription,
        matteColor = matte,
        glyphColor = tint,
        offsets = matteOffsets(style),
        radiusPx = radiusPx,
        contentScale = contentScale,
        modifier = modifier,
    )
}
