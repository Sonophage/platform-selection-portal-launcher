package com.psplauncher.feature.xmb.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.PfpHintBar
import com.psplauncher.feature.xmb.viewmodel.XmbPrompt
import com.psplauncher.feature.xmb.viewmodel.XmbPrompts

@Composable
fun XmbHintBar(
    prompts: XmbPrompts,
    modifier: Modifier = Modifier,
    onAction: ((GamepadAction) -> Unit)? = null,
) {
    PfpHintBar(
        items = buildList {
            add(prompts.back.item())
            prompts.primary?.let { add(it.item()) }
            prompts.right.forEach { add(it.item()) }
        },
        modifier = modifier,
        onAction = onAction,
    )
}

private fun XmbPrompt.item() =
    ControllerPromptItem(action, target?.takeIf { it.isNotBlank() }?.let { "$verb  $it" } ?: verb)
