package com.psplauncher.feature.settings.ui.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.components.ControllerHintStyle
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.PfpControllerHints
import com.psplauncher.core.ui.sound.LocalMenuSounds
import com.psplauncher.core.ui.sound.MenuSound
import com.psplauncher.core.ui.theme.menuCursorEdge
import com.psplauncher.core.ui.wave.WaveLayers
import com.psplauncher.core.ui.wave.WaveStyle
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.feature.settings.ui.LocalSettingsPromptAction
import com.psplauncher.feature.settings.ui.LocalSettingsScrollStateRegistrar
import com.psplauncher.feature.settings.ui.SettingsScaffold

private val WizardProgressTrack = Color.White.copy(alpha = 0.16f)

internal val WizardAmber = Color(0xFFFFC857)

@Composable
fun WizardScaffold(

    stepNumber: Int?,

    stepCount: Int = 0,
    title: String,

    heading: String,

    hint: String? = null,
    onBack: () -> Unit,

    backEnabled: Boolean = true,

    onSkip: (() -> Unit)? = null,

    message: String? = null,
    onDismissMessage: (() -> Unit)? = null,

    footerNote: String? = null,

    contentKey: Any? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val menuSounds = LocalMenuSounds.current
    val skip by rememberUpdatedState(onSkip)
    SettingsScaffold(
        title = title,
        subtitle = "",
        onBack = onBack,
        modifier = modifier,

        backdrop = { WizardBackdrop() },

        showRail = false,
        header = { WizardHeader(stepNumber, stepCount, title) },
        footer = { WizardFooter(backEnabled, onSkip != null, footerNote) },

        onInterceptAction = { action ->
            if (action == GamepadAction.OPEN_CONTEXT_MENU && skip != null) {
                menuSounds(MenuSound.BACK)
                skip?.invoke()
                true
            } else {
                false
            }
        },
        contentKey = contentKey,
    ) {
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)

        LaunchedEffect(contentKey) { scrollState.scrollTo(0) }

        var pagesSeen by remember { mutableIntStateOf(0) }
        LaunchedEffect(contentKey) {
            if (pagesSeen > 0) menuSounds(MenuSound.SYSTEM_BROWSE)
            pagesSeen++
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = WizardEdgeInset)
                .verticalScroll(scrollState),
        ) {
            WizardHeading(heading, hint)
            if (message != null && onDismissMessage != null) {
                WizardMessageRow(message, onDismissMessage)
            }
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WizardBackdrop() {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        WaveLayers(WaveStyle.ANIMATED)
    }
}

@Composable
private fun WizardHeader(stepNumber: Int?, stepCount: Int, title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()

            .focusProperties { canFocus = false }
            .padding(start = 48.dp, end = 48.dp, top = 18.dp, bottom = 8.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 30.sp,

            fontWeight = FontWeight.Light,
        )
        if (stepNumber != null && stepCount > 0) {
            Spacer(Modifier.height(10.dp))
            WizardProgress(stepNumber, stepCount)
        }
    }
}

@Composable
private fun WizardProgress(stepNumber: Int, stepCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "STEP $stepNumber OF $stepCount",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .width(WizardProgressWidth)
                .height(2.dp)
                .clip(CircleShape)
                .background(WizardProgressTrack),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(stepNumber.toFloat() / stepCount.toFloat())
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(menuCursorEdge()),
            )
        }
    }
}

@Composable
private fun WizardHeading(heading: String, hint: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = WizardGutter, end = WizardGutter, top = 14.dp, bottom = 10.dp),
    ) {
        Text(
            text = heading,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Start,
        )
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = hint,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 13.sp,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun WizardFooter(backEnabled: Boolean, skippable: Boolean, note: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (note != null) {
            Text(
                text = note,
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(6.dp))
        }
        PfpControllerHints(
            items = listOfNotNull(
                ControllerPromptItem(GamepadAction.SELECT, "Enter"),
                ControllerPromptItem(GamepadAction.BACK, "Back").takeIf { backEnabled },
                ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Skip Setup")
                    .takeIf { skippable },
            ),
            style = ControllerHintStyle.INLINE,

            onAction = LocalSettingsPromptAction.current,
        )
    }
}

private val WizardEdgeInset = 16.dp

private val WizardGutter = 48.dp - WizardEdgeInset

internal val WizardRowGutter = 48.dp - WizardEdgeInset

private val WizardProgressWidth = 160.dp
