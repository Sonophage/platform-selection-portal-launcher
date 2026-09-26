package com.psplauncher.feature.settings.ui.wizard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.R as CoreUiR
import com.psplauncher.core.ui.components.ControllerHintStyle
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.PfpControllerHints
import com.psplauncher.core.ui.icons.PortalIcon
import com.psplauncher.core.ui.sound.LocalMenuSounds
import com.psplauncher.core.ui.sound.MenuSound
import com.psplauncher.core.ui.wave.WaveLayers
import com.psplauncher.core.ui.wave.WaveStyle
import com.psplauncher.feature.settings.ui.LocalSettingsActionConsumed
import com.psplauncher.feature.settings.ui.LocalSettingsPendingAction

@Composable
fun WizardSplash(onBegin: () -> Unit) {
    val begin by rememberUpdatedState(onBegin)
    val menuSounds = LocalMenuSounds.current
    val pendingAction = LocalSettingsPendingAction.current
    val onConsumed = LocalSettingsActionConsumed.current

    var entering by remember { mutableStateOf(false) }
    val press = remember { Animatable(0f) }

    val start: () -> Unit = {
        if (!entering) {
            entering = true
            menuSounds(MenuSound.SELECT)
        }
    }

    LaunchedEffect(pendingAction) {
        if (pendingAction == GamepadAction.SELECT) {
            onConsumed()
            start()
        }
    }

    LaunchedEffect(entering) {
        if (!entering) return@LaunchedEffect
        press.animateTo(1f, tween(SplashEnterMs, easing = FastOutSlowInEasing))
        begin()
    }

    val idle = rememberInfiniteTransition(label = "splash-shimmer")
    val idleSweep by idle.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(ShimmerCycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "splash-shimmer-sweep",
    )

    val t = press.value

    val glow = FastOutSlowInEasing.transform((t / GlowPeak).coerceIn(0f, 1f))
    val exit = ((t - ExitStart) / (1f - ExitStart)).coerceIn(0f, 1f)
    val sweep = if (entering) -0.4f + t * 1.8f else idleSweep

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)

            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = start,
            ),
        contentAlignment = Alignment.Center,
    ) {
        WaveLayers(WaveStyle.ANIMATED)

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(LogoFraction)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            scaleX = 1f + glow * GlowSwell + exit * ExitSwell
                            scaleY = scaleX
                            alpha = 1f - exit
                        }
                        .drawWithContent {
                            drawContent()

                            val span = size.width * ShimmerWidth
                            val head = size.width * sweep
                            drawRect(
                                brush = Brush.linearGradient(
                                    0f to Color.Transparent,
                                    0.5f to Color.White.copy(alpha = if (entering) 0.95f else 0.55f),
                                    1f to Color.Transparent,
                                    start = Offset(head - span, 0f),
                                    end = Offset(head + span, size.height),
                                ),
                                blendMode = BlendMode.SrcAtop,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PortalIcon(
                        painter = painterResource(CoreUiR.drawable.psp_logo),
                        contentDescription = "PSPLauncher",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = (1f - t * PromptFadeRate).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Let's set up your launcher",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(10.dp))
            PfpControllerHints(
                items = listOf(ControllerPromptItem(GamepadAction.SELECT, "Begin")),
                style = ControllerHintStyle.INLINE,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

private const val SplashEnterMs = 900

private const val GlowPeak = 0.45f

private const val ExitStart = 0.55f

private const val GlowSwell = 0.04f
private const val ExitSwell = 0.22f

private const val ShimmerCycleMs = 4_200

private const val ShimmerWidth = 0.45f

private const val LogoFraction = 0.42f

private const val PromptFadeRate = 2.4f
