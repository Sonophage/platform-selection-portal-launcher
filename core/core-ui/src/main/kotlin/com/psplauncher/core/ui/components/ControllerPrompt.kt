package com.psplauncher.core.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.GamepadMappings

@Immutable
data class ControllerPromptStyle(

    val family: ControllerDisplayType = ControllerDisplayType.XBOX,

    val mappings: GamepadMappings = GamepadMappings(),
)

val LocalControllerPromptStyle = compositionLocalOf { ControllerPromptStyle() }

val LocalControllerConnected = compositionLocalOf { true }

@Composable
fun ControllerPrompt(
    action: GamepadAction,
    label: String,
    modifier: Modifier = Modifier,
    style: ControllerPromptStyle = LocalControllerPromptStyle.current,
    labelColor: Color = Color.White.copy(alpha = 0.75f),
    labelStyle: TextStyle = TextStyle.Default,
    glyphSize: Dp = 22.dp,
    spacing: Dp = 4.dp,
) {
    ControllerPrompt(
        actions = listOf(action),
        label = label,
        modifier = modifier,
        style = style,
        labelColor = labelColor,
        labelStyle = labelStyle,
        glyphSize = glyphSize,
        spacing = spacing,
    )
}

@Composable
fun ControllerPrompt(
    actions: List<GamepadAction>,
    label: String,
    modifier: Modifier = Modifier,
    style: ControllerPromptStyle = LocalControllerPromptStyle.current,
    labelColor: Color = Color.White.copy(alpha = 0.75f),
    labelStyle: TextStyle = TextStyle.Default,
    glyphSize: Dp = 22.dp,
    spacing: Dp = 4.dp,

    glyphSpacing: Dp = 2.dp,
) {
    ControllerPromptGlyphs(
        icons = style.mappings.iconsFor(actions),
        label = label,
        modifier = modifier,
        family = style.family,
        labelColor = labelColor,
        labelStyle = labelStyle,
        glyphSize = glyphSize,
        spacing = spacing,
        glyphSpacing = glyphSpacing,
    )
}

@Composable
fun ControllerPromptGlyphs(
    icons: List<ControllerIcon>,
    label: String,
    modifier: Modifier = Modifier,
    family: ControllerDisplayType = LocalControllerPromptStyle.current.family,
    labelColor: Color = Color.White.copy(alpha = 0.75f),
    labelStyle: TextStyle = TextStyle.Default,
    glyphSize: Dp = 22.dp,
    spacing: Dp = 4.dp,
    glyphSpacing: Dp = 2.dp,
) {
    if (icons.isEmpty()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(glyphSpacing),
        ) {
            for (icon in icons) {
                ControllerIconGlyph(icon = icon, family = family, size = glyphSize)
            }
        }
        Text(
            text = label,
            color = labelColor,
            style = labelStyle,
        )
    }
}

@Immutable
data class ControllerPromptItem(
    val actions: List<GamepadAction>,
    val label: String,
    val fixedIcons: List<ControllerIcon>? = null,
) {
    constructor(action: GamepadAction, label: String) : this(listOf(action), label)

    fun tappableAction(): GamepadAction? =
        if (fixedIcons == null && actions.size == 1) actions[0] else null

    companion object {
        fun fixed(icon: ControllerIcon, label: String) =
            ControllerPromptItem(emptyList(), label, listOf(icon))
    }
}

@Composable
internal fun ControllerPromptBar(
    items: List<ControllerPromptItem>,
    modifier: Modifier = Modifier,
    style: ControllerPromptStyle = LocalControllerPromptStyle.current,
    labelColor: Color = Color.White.copy(alpha = 0.75f),
    labelStyle: TextStyle = TextStyle.Default,
    glyphSize: Dp = 22.dp,
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),

    onAction: ((GamepadAction) -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = arrangement,
    ) {
        for (item in items) {
            val tapAction = item.tappableAction()?.takeIf { onAction != null }
            ControllerPromptGlyphs(
                icons = item.fixedIcons ?: style.mappings.iconsFor(item.actions),
                label = item.label,
                family = style.family,
                labelColor = labelColor,
                labelStyle = labelStyle,
                glyphSize = glyphSize,
                modifier = if (tapAction == null) Modifier else Modifier

                    .then(if (LocalControllerConnected.current) Modifier else Modifier.heightIn(min = 48.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = item.label,
                    ) { onAction?.invoke(tapAction) },
            )
        }
    }
}
