package com.psplauncher.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.theme.LocalPfpTextColors

val ControllerHintEdgeGap = 5.dp

@Composable
fun ControllerHintBar(
    items: List<ControllerPromptItem>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    background: Color = Color.Black.copy(alpha = 0.5f),
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(9.dp),

    onAction: ((GamepadAction) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    ControllerPromptBar(
        items = items,
        onAction = onAction,
        modifier = modifier
            .background(
                color = background,
                shape = shape,
            )

            .padding(horizontal = 5.dp, vertical = 2.dp),
        labelColor = Color.White,
        labelStyle = TextStyle(
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.75f),
                offset = Offset(0f, 2f),
                blurRadius = 4f,
            ),
        ),
        glyphSize = 11.dp,
        arrangement = arrangement,
    )
}

enum class ControllerHintStyle {
    PILL,

    INLINE,

    OVERLAY,
}

private val OverlayLabel = Color.White.copy(alpha = 0.72f)

private val inlineLabelColor: Color
    @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.secondary

@Composable
fun PfpControllerHints(
    items: List<ControllerPromptItem>,
    style: ControllerHintStyle,
    modifier: Modifier = Modifier,
    onAction: ((GamepadAction) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    when (style) {
        ControllerHintStyle.PILL -> ControllerHintBar(items, modifier, onAction = onAction)
        ControllerHintStyle.INLINE, ControllerHintStyle.OVERLAY -> ControllerPromptBar(
            items = items,
            onAction = onAction,
            modifier = modifier,
            labelColor = if (style == ControllerHintStyle.OVERLAY) OverlayLabel else inlineLabelColor,
            labelStyle = TextStyle(fontSize = 12.sp),
            glyphSize = 16.dp,

            arrangement = Arrangement.spacedBy(18.dp, androidx.compose.ui.Alignment.CenterHorizontally),
        )
    }
}
