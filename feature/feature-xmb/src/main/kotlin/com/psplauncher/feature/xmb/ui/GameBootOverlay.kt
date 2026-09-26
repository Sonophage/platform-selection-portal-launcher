package com.psplauncher.feature.xmb.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.psplauncher.core.ui.wave.WaveStyle
import com.psplauncher.themekit.UiMediaLimits
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val TITLE_FADE_MS = 500

@Composable
fun GameBootOverlay(
    gameTitle: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    videoPath: String? = null,
    audioPath: String? = null,
    waveStyle: WaveStyle = WaveStyle.ANIMATED,
) {
    val currentComplete by rememberUpdatedState(onComplete)
    val completed = remember { AtomicBoolean(false) }
    val overlayAlpha = remember { Animatable(1f) }

    var presentationDone by remember { mutableStateOf(false) }
    var useTitleCard by remember(videoPath) { mutableStateOf(videoPath == null) }

    val hardCapMs = if (videoPath != null) VIDEO_HARD_CAP_MS else SEQUENCE_HARD_CAP_MS

    LaunchedEffect(Unit) {
        val endedNaturally = withTimeoutOrNull(hardCapMs) {
            snapshotFlow { presentationDone }.first { it }
        } != null
        if (endedNaturally) {
            overlayAlpha.animateTo(0f, animationSpec = tween(TITLE_FADE_MS))
        } else {
            Timber.w("GameBoot watchdog fired after ${hardCapMs}ms — continuing to the emulator")
        }
        if (completed.compareAndSet(false, true)) currentComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(overlayAlpha.value),
    ) {
        if (videoPath != null && !useTitleCard) {
            OneShotVideoLayer(
                path = videoPath,
                clipEndMs = UiMediaLimits.GAMEBOOT_CLIP_MAX_MS,
                onEnded = { presentationDone = true },

                onFailed = { useTitleCard = true },
                muted = audioPath != null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GameBootSequence(
                gameTitle = gameTitle,
                waveStyle = waveStyle,
                onFinished = { presentationDone = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val SEQUENCE_HARD_CAP_MS = 6_500L

private const val VIDEO_HARD_CAP_MS = 11_500L
