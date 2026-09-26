package com.psplauncher.feature.xmb.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.psplauncher.core.ui.R as CoreUiR
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.icons.PortalIcon
import com.psplauncher.themekit.UiMediaLimits
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val FADE_IN_MS  = 800
private const val HOLD_MS     = 1_400L
private const val FADE_OUT_MS = 600

private const val HARD_CAP_MS = 12_000L

@Composable
fun BootSequenceOverlay(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    bootVideoPath: String? = null,
    bootAudioPath: String? = null,
) {
    val logoAlpha    = remember { Animatable(0f) }
    val logoScale    = remember { Animatable(0.92f) }
    val overlayAlpha = remember { Animatable(1f) }

    val currentComplete by rememberUpdatedState(onComplete)

    val completed = remember { AtomicBoolean(false) }

    var presentationDone by remember { mutableStateOf(false) }

    var useLogoAnimation by remember(bootVideoPath) { mutableStateOf(bootVideoPath == null) }

    LaunchedEffect(Unit) {
        val endedNaturally = withTimeoutOrNull(HARD_CAP_MS) {
            snapshotFlow { presentationDone }.first { it }
        } != null
        if (endedNaturally) {
            overlayAlpha.animateTo(0f, animationSpec = tween(FADE_OUT_MS))
        } else {
            Timber.w("Boot watchdog fired after ${HARD_CAP_MS}ms — completing boot regardless")
        }
        if (completed.compareAndSet(false, true)) currentComplete()
    }

    LaunchedEffect(useLogoAnimation) {
        if (!useLogoAnimation) return@LaunchedEffect
        logoScale.animateTo(1f, animationSpec = tween(FADE_IN_MS))
        logoAlpha.animateTo(1f, animationSpec = tween(FADE_IN_MS))
        delay(HOLD_MS)
        presentationDone = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(overlayAlpha.value),
        contentAlignment = Alignment.Center,
    ) {
        if (bootVideoPath != null && !useLogoAnimation) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                OneShotVideoLayer(
                    path = bootVideoPath,
                    clipEndMs = UiMediaLimits.BOOT_MAX_MS,
                    onEnded = { presentationDone = true },
                    onFailed = {
                        useLogoAnimation = true
                    },

                    muted = bootAudioPath != null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))

            PortalIcon(
                painter = painterResource(CoreUiR.drawable.psp_logo),
                contentDescription = "PSPLauncher",
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(0.42f)
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    },
            )

            Text(
                text = "PSPLauncher",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .alpha(logoAlpha.value),
            )
        }

        if (bootAudioPath != null) {
            OneShotAudioLayer(path = bootAudioPath, clipEndMs = UiMediaLimits.BOOT_MAX_MS)
        }
    }
}
