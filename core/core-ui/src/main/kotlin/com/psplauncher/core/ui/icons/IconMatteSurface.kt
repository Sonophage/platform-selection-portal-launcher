package com.psplauncher.core.ui.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.IconLegibilityStyle

@Composable
internal fun IconMatteSurface(
    painter: Painter,
    contentDescription: String?,
    matteColor: Color,
    glyphColor: Color?,
    offsets: List<Offset>,
    radiusPx: Float,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val matteFilter = ColorFilter.tint(matteColor, BlendMode.SrcIn)

    val glyphFilter = glyphColor?.let { ColorFilter.tint(it, BlendMode.SrcIn) }

    Box(
        modifier
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .drawWithCache {
                val factor = contentScale.computeScaleFactor(painter.intrinsicSize, size)
                val dst = Size(
                    painter.intrinsicSize.width * factor.scaleX,
                    painter.intrinsicSize.height * factor.scaleY,
                )
                val origin = Offset((size.width - dst.width) / 2f, (size.height - dst.height) / 2f)
                onDrawBehind {
                    for (o in offsets) {
                        drawTranslated(origin.x + o.x * radiusPx, origin.y + o.y * radiusPx) {
                            with(painter) { draw(dst, colorFilter = matteFilter) }
                        }
                    }
                    drawTranslated(origin.x, origin.y) {
                        with(painter) { draw(dst, colorFilter = glyphFilter) }
                    }
                }
            },
    )
}

private inline fun DrawScope.drawTranslated(dx: Float, dy: Float, block: DrawScope.() -> Unit) {
    translate(dx, dy) { block() }
}

@Composable
internal fun VectorGlyphSurface(
    vector: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val style = LocalIconLegibility.current
    val matte = matteColorFor(style, tint)
    if (matte == null) {
        Icon(vector, contentDescription = contentDescription, tint = tint, modifier = modifier)
        return
    }
    val radiusPx = with(LocalDensity.current) {
        (if (style == IconLegibilityStyle.OFFSET_SHADOW) SHADOW_OFFSET_DP else CONTOUR_RADIUS_DP).dp.toPx()
    }
    IconMatteSurface(
        painter = rememberVectorPainter(vector),
        contentDescription = contentDescription,
        matteColor = matte,
        glyphColor = tint,
        offsets = matteOffsets(style),
        radiusPx = radiusPx,
        modifier = modifier,
    )
}
