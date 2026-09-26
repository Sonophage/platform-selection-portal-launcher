package com.psplauncher.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

@Composable
fun menuCursorFill(): Color =
    lerp(deriveStorefrontColors().accentHue, Color.White, 0.20f).copy(alpha = 0.34f)

@Composable
fun menuCursorEdge(): Color = deriveStorefrontColors().tileSelectedEdge.copy(alpha = 0.95f)

@Composable
fun Modifier.menuCursor(selected: Boolean, shape: Shape = RoundedCornerShape(8.dp)): Modifier {
    if (!selected) return this
    return this
        .clip(shape)
        .background(menuCursorFill())
        .border(1.5.dp, menuCursorEdge(), shape)
}
