package com.psplauncher.feature.xmb.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
import timber.log.Timber

/**
 * Root background behind the XMB UI. The wallpaper automatically replaces the wave:
 *
 *  - When [motionWallpaperPath] is set, the looping motion wallpaper renders over its poster —
 *    but ONLY while [motionDecision] says PLAY/PLAY_REDUCED; a POSTER decision renders the poster
 *    still alone and never constructs an ExoPlayer. (When the wave would be frozen, the motion
 *    wallpaper is not merely paused — no decoder exists.)
 *  - Otherwise, when [customWallpaperPath] is set, the still wallpaper renders alone — no wave
 *    is drawn or animated, and no wave resources are allocated. A POSTER decision with a motion
 *    file set lands here too: the frozen path is literally the existing still-wallpaper composable.
 *  - When no wallpaper is set, the XMB wave is rendered, honoring [waveStyle].
 *
 * The motion decision itself is computed ONCE by the shell through [MotionWallpaperPolicy] (the
 * single definition of "the device is busy or conserving" that both the shader and the decoder
 * obey) and passed down; callers that render only a wave (boot) can ignore it entirely.
 *
 * The wave itself is the PlayStation 3 XMB wave over a theme-tinted vertical gradient: overlapping
 * sheets of light with a bright edge where the surface turns, and a drifting sparkle layer. On
 * API 33+ it is a single AGSL fragment shader; older devices get a Canvas fallback that keeps the
 * silhouette but has neither the edge highlight nor the sparkles.
 *
 * That last sentence used to promise sparkles on both paths and nothing drew them anywhere, which
 * is the kind of comment that is worse than none: it is authoritative and it is touching the code.
 */
@Composable
fun XmbBackground(
    waveStyle: WaveStyle,
    customWallpaperPath: String? = null,
    motionWallpaperPath: String? = null,
    motionDecision: MotionWallpaperPolicy.Decision = MotionWallpaperPolicy.Decision.PLAY,
    /**
     * Settings ▸ Display ▸ Wave Over Wallpaper. Keeps the wave, drawn on top of the picture,
     * instead of the picture replacing it.
     *
     * Off by default, and the OFF path is unchanged on purpose: it is what every existing install
     * has, and it is the one that allocates no shader, no frame loop and no decoder. Turning this
     * on is choosing to pay for the wave again on top of the wallpaper.
     */
    waveOverWallpaper: Boolean = false,
    /**
     * The accent derived from the wallpaper, used to tint the wave drawn over it.
     *
     * Over the theme gradient the wave is pure white on purpose — the colour comes from the
     * gradient beneath. Over a photograph there is no gradient to colour it, so a white wave sits
     * on the picture looking like it belongs to a different screen. Tinting it with the picture's
     * own accent is what makes it read as part of the wallpaper.
     */
    wallpaperAccent: Long? = null,
    /**
     * Hold the wave back so the caller can draw it over something.
     *
     * The crossbar puts the focused item's artwork between this and the wave — album cover, key
     * art, a film's thumbnail — so the wave reads as part of the screen rather than as a layer the
     * artwork buried. Everything else (boot, the settings preview) leaves this alone and gets the
     * wave where it has always been.
     */
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

        // The wave a second time, over the picture — and ONLY over a picture. With no wallpaper
        // the branch above already drew it, and drawing it twice would double every strand.
        //
        // No gradient underneath it here: that is the wallpaper's job now. WaveBackground paints
        // the theme gradient as its base, which would hide the picture entirely, so this draws
        // the wave alone over whatever is behind it.
        if (hasWallpaper && waveOverWallpaper && !waveDrawnByCaller) {
            WaveOverlay(waveStyle, wallpaperAccent, Modifier.fillMaxSize())
        }
    }
}

/**
 * Lifts a wallpaper accent into a colour the wave can be made of.
 *
 * A saturated accent straight off a photograph turns the wave into a solid block of that colour.
 * The wave is LIGHT: it takes the hue of what it came from without becoming it, so the accent is
 * mixed most of the way to white and only the cast survives.
 */
private fun waveTintFrom(accentArgb: Long): Color =
    lerp(Color(accentArgb or 0xFF000000L), Color.White, 0.62f)

/**
 * The wave alone, tinted, over whatever is already on screen.
 *
 * Public because the crossbar draws it itself — see [waveDrawnByCaller]. No gradient: that is
 * whatever this is being drawn over.
 */
@Composable
fun WaveOverlay(waveStyle: WaveStyle, accentArgb: Long?, modifier: Modifier) {
    Box(modifier) {
        WaveLayers(waveStyle, accentArgb?.let(::waveTintFrom) ?: Color.White)
    }
}

/**
 * Whether the XMB wave animation should be frozen to relieve the device, honoring the two
 * Settings ▸ Display toggles. The wave is a non-essential flourish, so it shouldn't burn power when
 * the system is already conserving it:
 *
 *  - [respectBatterySaver]: true while the OS is in battery-saver mode (live via
 *    `ACTION_POWER_SAVE_MODE_CHANGED`).
 *  - [thermalThrottleAware]: true while the OS reports at least moderate thermal throttling (live via
 *    a thermal-status listener; API 29+ only).
 *
 * Returns false whenever the matching setting is off or the platform can't report the state.
 */
@Composable
fun rememberWavePowerThrottle(
    respectBatterySaver: Boolean,
    thermalThrottleAware: Boolean,
): Boolean {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }

    // Battery-saver: seed from the current value, then track ACTION_POWER_SAVE_MODE_CHANGED.
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

    // Thermal throttling: status listener (available since API 29 == minSdk); freeze at MODERATE+.
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
        // Light scrim so the XMB labels stay readable over any wallpaper.
        Box(Modifier.fillMaxSize().background(Color(0x59000000)))
    }
}

