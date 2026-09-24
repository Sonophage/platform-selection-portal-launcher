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
import kotlinx.coroutines.delay

/**
 * The wizard's front door: the mark on the wave, and one press to go in.
 *
 * Shown on a first run only — see [com.psplauncher.feature.settings.ui.InitialSetupScreen]. A
 * wizard re-opened from Settings ▸ System is a task, not an arrival, and a ceremony in front of it
 * every time would be something to sit through rather than something to see.
 *
 * Deliberately NOT a [WizardScaffold] page. Every page in that scaffold is a form — a header, a
 * heading, a column of rows and a prompt row — and this is a picture with one affordance. Building
 * it as a page would mean suppressing four pieces of chrome to arrive at a blank one, so it is its
 * own composable and reads the host's action directly.
 */
@Composable
fun WizardSplash(onBegin: () -> Unit) {
    val begin by rememberUpdatedState(onBegin)
    val menuSounds = LocalMenuSounds.current
    val pendingAction = LocalSettingsPendingAction.current
    val onConsumed = LocalSettingsActionConsumed.current

    // Latches: the press starts an animation that ends in navigation, so a second press (a mash,
    // or a tap landing while the controller press is already running) must do nothing at all.
    var entering by remember { mutableStateOf(false) }
    val press = remember { Animatable(0f) }

    val start: () -> Unit = {
        if (!entering) {
            entering = true
            menuSounds(MenuSound.SELECT)
        }
    }

    // BACK is not handled: on a first run there is nowhere behind this. The host's own back
    // handling still applies, which is what keeps the launcher's Home key working.
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

    // The idle shimmer: a band travelling through the mark, slowly, before anything is pressed.
    // It is what makes the screen read as waiting for you rather than as stopped.
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
    // The press: the mark brightens and swells a little, then the whole screen goes to it. The
    // sweep is driven fast through the mark once, over the top of the idle one.
    val glow = FastOutSlowInEasing.transform((t / GlowPeak).coerceIn(0f, 1f))
    val exit = ((t - ExitStart) / (1f - ExitStart)).coerceIn(0f, 1f)
    val sweep = if (entering) -0.4f + t * 1.8f else idleSweep

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Tap anywhere. No ripple and no indication: this is a picture, not a button, and the
            // response to the press is the animation itself.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = start,
            ),
        contentAlignment = Alignment.Center,
    ) {
        WaveLayers(WaveStyle.ANIMATED)

        // A plain Box. This was BoxWithConstraints and never read maxWidth or maxHeight —
        // BoxWithConstraints subcomposes its content to hand it the constraints, so an unused
        // scope buys a second composition pass for nothing, on the first screen a new install
        // ever draws.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(LogoFraction)
                        .graphicsLayer {
                            // Offscreen so the shimmer below can be masked to the mark's own
                            // alpha rather than painted as a rectangle across it.
                            compositingStrategy = CompositingStrategy.Offscreen
                            scaleX = 1f + glow * GlowSwell + exit * ExitSwell
                            scaleY = scaleX
                            alpha = 1f - exit
                        }
                        .drawWithContent {
                            drawContent()
                            // SrcATop, not SrcIn. SrcIn replaces the destination's colour
                            // everywhere, including where the band's gradient is transparent —
                            // so the mark vanished and only the travelling band was visible, a
                            // sliver of logo sliding across an empty screen. SrcAtop keeps the
                            // destination where the source is clear and lightens it where the
                            // band is, which is what a shimmer is. Both are masked to the mark's
                            // own alpha, which is the part that was right: a blurred halo behind
                            // the glyph fogs the wave, and the wave is the other half of this
                            // screen.
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

        // The prompt, below the mark and out of its way. It fades as the press takes over, so the
        // last thing on screen is the mark alone.
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

/** How long the press takes before the wizard's first page is asked for. */
private const val SplashEnterMs = 900

/** Where in that press the glow has fully arrived (a fraction of [SplashEnterMs]). */
private const val GlowPeak = 0.45f

/** Where in the press the mark starts leaving. */
private const val ExitStart = 0.55f

private const val GlowSwell = 0.04f
private const val ExitSwell = 0.22f

/** One idle sweep through the mark. Slow: a shimmer this size reads as a scan if it is quick. */
private const val ShimmerCycleMs = 4_200

/** Half-width of the travelling band, as a fraction of the mark's width. */
private const val ShimmerWidth = 0.45f

/** The mark's share of the SHORT screen edge — the same fraction the boot sequence uses. */
private const val LogoFraction = 0.42f

/** How fast the prompt leaves relative to the press. Gone by roughly the glow's peak. */
private const val PromptFadeRate = 2.4f
