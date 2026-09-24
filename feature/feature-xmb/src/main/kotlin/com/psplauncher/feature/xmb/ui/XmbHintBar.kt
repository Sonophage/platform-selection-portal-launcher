package com.psplauncher.feature.xmb.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.PfpHintBar
import com.psplauncher.feature.xmb.viewmodel.XmbPrompt
import com.psplauncher.feature.xmb.viewmodel.XmbPrompts

// The crossbar's footer is the shared [PfpHintBar] — see core-ui for what the bar is and why it
// lives there. All this does is flatten [XmbPrompts]' three slots into the one list the bar reads,
// because the bar works out the same split from the actions themselves.

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

/**
 * The target rides IN the label — "Open  All Games" — rather than as a second Text beside it. The
 * design draws the row's name louder than the verb, and that is worth less than having one
 * renderer for every prompt in the app.
 */
private fun XmbPrompt.item() =
    ControllerPromptItem(action, target?.takeIf { it.isNotBlank() }?.let { "$verb  $it" } ?: verb)
