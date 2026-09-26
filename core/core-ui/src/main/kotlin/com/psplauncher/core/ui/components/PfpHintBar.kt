package com.psplauncher.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.GamepadAction

@Composable
fun PfpHintBar(
    items: List<ControllerPromptItem>,
    modifier: Modifier = Modifier,
    onAction: ((GamepadAction) -> Unit)? = null,

    centre: (@Composable () -> Unit)? = null,
) {
    if (items.isEmpty() && centre == null) return
    val (back, primary, right) = hintBarGroups(items)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)

            .background(Brush.verticalGradient(0f to Color.Transparent, 1f to BarScrim))
            .padding(horizontal = EdgeGap),
    ) {
        back?.let { Group(listOf(it), onAction) }
        primary?.let {
            if (back != null) Divider()
            Group(listOf(it), onAction)
        }

        Box(
            modifier = Modifier.weight(1f).padding(horizontal = GroupGap),
            contentAlignment = Alignment.Center,
        ) { centre?.invoke() }

        Group(right, onAction)
    }
}

@Composable
private fun Group(items: List<ControllerPromptItem>, onAction: ((GamepadAction) -> Unit)?) {
    if (items.isEmpty()) return
    PfpControllerHints(items = items, style = ControllerHintStyle.OVERLAY, onAction = onAction)
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(horizontal = GroupGap)
            .width(1.dp)
            .height(DividerHeight)
            .background(Color.White.copy(alpha = 0.22f)),
    )
}

internal data class HintBarGroups(
    val back: ControllerPromptItem?,
    val primary: ControllerPromptItem?,
    val right: List<ControllerPromptItem>,
)

internal fun hintBarGroups(items: List<ControllerPromptItem>): HintBarGroups {
    val back = items.firstOrNull { it.tappableAction() == GamepadAction.BACK }
    val primary = items.firstOrNull { it.tappableAction() == GamepadAction.SELECT }
    return HintBarGroups(back, primary, items.filter { it !== back && it !== primary })
}

private val BarHeight = HintBarHeight
private val EdgeGap = 22.dp
private val GroupGap = 16.dp
private val DividerHeight = 15.dp
private val BarScrim = Color(0xB3060200)
