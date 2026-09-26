package com.psplauncher.feature.xmb.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.motion.MotionWallpaperBackground
import com.psplauncher.core.ui.motion.MotionWallpaperPolicy
import com.psplauncher.core.ui.wave.WaveBackground
import com.psplauncher.core.ui.wave.WaveLayers
import com.psplauncher.core.ui.wave.WaveStyle

@Composable
fun XmbBackground(
    waveStyle: WaveStyle,
    customWallpaperPath: String? = null,
    motionWallpaperPath: String? = null,
    motionDecision: MotionWallpaperPolicy.Decision = MotionWallpaperPolicy.Decision.PLAY,

    waveOverWallpaper: Boolean = false,

    wallpaperAccent: Long? = null,

    waveDrawnByCaller: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val hasWallpaper = customWallpaperPath != null
    val motionPlaying = hasWallpaper && motionWallpaperPath != null &&
        motionDecision != MotionWallpaperPolicy.Decision.POSTER

    Box(modifier.fillMaxSize()) {
        when {
            motionPlaying -> MotionWallpaperBackground(
                posterPath = customWallpaperPath,
                motionPath = motionWallpaperPath,
                decision = motionDecision,
                modifier = Modifier.fillMaxSize(),
            )
            hasWallpaper -> WallpaperBackground(customWallpaperPath, Modifier.fillMaxSize())
            else -> WaveBackground(waveStyle, Modifier.fillMaxSize(), drawWave = !waveDrawnByCaller)
        }

        if (hasWallpaper && waveOverWallpaper && !waveDrawnByCaller) {
            WaveOverlay(waveStyle, wallpaperAccent, Modifier.fillMaxSize())
        }
    }
}

private fun waveTintFrom(accentArgb: Long): Color =
    lerp(Color(accentArgb or 0xFF000000L), Color.White, 0.62f)

@Composable
fun WaveOverlay(
    waveStyle: WaveStyle,
    accentArgb: Long?,
    modifier: Modifier,
    speedScale: Float = 1f,
    glowScale: Float = 1f,
) {
    Box(modifier) {
        WaveLayers(waveStyle, accentArgb?.let(::waveTintFrom) ?: Color.White, speedScale, glowScale)
    }
}

@Composable
fun rememberWavePowerThrottle(
    respectBatterySaver: Boolean,
    thermalThrottleAware: Boolean,
): Boolean {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }

    var powerSave by remember { mutableStateOf(false) }
    DisposableEffect(powerManager, respectBatterySaver) {
        if (powerManager == null || !respectBatterySaver) {
            powerSave = false
            return@DisposableEffect onDispose {}
        }
        powerSave = powerManager.isPowerSaveMode
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                powerSave = powerManager.isPowerSaveMode
            }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    var thermalThrottling by remember { mutableStateOf(false) }
    DisposableEffect(powerManager, thermalThrottleAware) {
        if (powerManager == null || !thermalThrottleAware) {
            thermalThrottling = false
            return@DisposableEffect onDispose {}
        }
        thermalThrottling = powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            thermalThrottling = status >= PowerManager.THERMAL_STATUS_MODERATE
        }
        powerManager.addThermalStatusListener(listener)
        onDispose { powerManager.removeThermalStatusListener(listener) }
    }

    return powerSave || thermalThrottling
}

@Composable
private fun WallpaperBackground(
    customWallpaperPath: String,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model              = customWallpaperPath,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )

        Box(Modifier.fillMaxSize().background(Color(0x59000000)))
    }
}

