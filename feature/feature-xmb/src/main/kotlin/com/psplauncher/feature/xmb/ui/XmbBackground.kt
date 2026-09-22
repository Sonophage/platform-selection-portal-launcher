package com.psplauncher.feature.xmb.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.RuntimeShader
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.motion.MotionWallpaperBackground
import com.psplauncher.core.ui.motion.MotionWallpaperPolicy
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.core.ui.wave.WaveStyle
import timber.log.Timber
import kotlin.math.sin

// Frozen "time" (seconds) used to pose the wave when animation is disabled.
private const val STATIC_TIME = 2.0f
private const val TAU = 6.2831853f

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
    modifier: Modifier = Modifier,
) {
    when {
        customWallpaperPath != null && motionWallpaperPath != null && motionDecision != MotionWallpaperPolicy.Decision.POSTER ->
            MotionWallpaperBackground(
                posterPath = customWallpaperPath,
                motionPath = motionWallpaperPath,
                decision = motionDecision,
                modifier = modifier,
            )
        customWallpaperPath != null -> WallpaperBackground(customWallpaperPath, modifier)
        else -> WaveBackground(waveStyle, modifier)
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

@Composable
private fun WaveBackground(
    waveStyle: WaveStyle,
    modifier: Modifier,
) {
    val colors = LocalPFPColors.current
    val alphaScale = if (waveStyle.reduced) 0.5f else 1f
    val ampScale   = if (waveStyle.reduced) 0.65f else 1f

    // Continuously-increasing time in seconds since the first frame (so float precision stays sharp),
    // scaled by style speed. Frozen at STATIC_TIME when the wave shouldn't animate — no frame loop,
    // no per-frame recomposition. Only advances while this background is on screen.
    val animated = waveStyle.animated
    val speed = if (waveStyle.reduced) 0.5f else 1f
    val time by produceState(STATIC_TIME, animated, speed) {
        if (!animated) {
            value = STATIC_TIME
            return@produceState
        }
        var startMs = -1L
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                if (startMs < 0L) startMs = frameMs
                value = (frameMs - startMs) / 1000f * speed
            }
        }
    }

    // Monthly-tinted vertical gradient: deep top (keeps the status strip legible) easing to the pale
    // bottom the wave sits against.
    val gradient = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to colors.backgroundTop,
            0.30f to colors.backgroundTop,
            0.70f to lerp(colors.backgroundTop, colors.backgroundBottom, 0.5f),
            1.00f to colors.backgroundBottom,
        )
    )

    Box(modifier = modifier.fillMaxSize().background(gradient)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ShaderWave(time, alphaScale, ampScale)
        } else {
            FallbackWave(time, alphaScale, ampScale)
        }
        // Soft off-centre light bloom — the same gentle highlight the XMB has near the crossbar.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    center = center.copy(x = size.width * 0.48f, y = size.height * 0.30f),
                    radius = size.minDimension * 0.62f,
                )
            )
        }
    }
}

// ── AGSL wave (API 33+) ──────────────────────────────────────────────────────
//
// The PlayStation 3 XMB wave, ported from linkev/PlayStation-3-XMB (MIT, (c) 2025 Mart), whose
// author reverse-engineered it from the PS3's own spline.elf. Their permission notice is kept in
// LICENSES/PlayStation-3-XMB-MIT.txt.
//
// What is ported is the MOTION — the height field below is their vertex shader's arithmetic, with
// their reverse-engineered constants — and the shading idea: a Fresnel term that lights the parts
// of the surface turning edge-on, drawn as white with alpha. That last part is why this fits here
// at all. Their fragment shader ends in `vec4(vec3(1.0), F * opacity * brightness)`: white over a
// coloured gradient, which is exactly how this file already composites, so the wave stays a
// lightening pass and the COLOUR still comes entirely from the theme. The monthly hue, a category
// tinting the wave, a user's chosen scheme — all of it behaves as before.
//
// What is NOT ported is the rendering model, and it could not be. Theirs displaces a 100x100 grid
// mesh in WebGL2 from a spline texture the CPU regenerates each frame, and reads its normal from
// screen-space derivatives. This is one fullscreen AGSL pass with no mesh and no texture, so the
// surface is evaluated analytically per pixel and the overlapping sheets the mesh gets for free —
// its far edge folding over its near edge, which is most of the PS3 look — are summed explicitly
// as SHEETS slices through z. Four, because each slice costs six sines per pixel and this draws
// behind the entire UI on a handheld.
//
// The slope stands in for their normal: a surface turning edge-on to the viewer is a surface whose
// height is changing fastest, so |dh/dx| drives the same highlight their dot(view, N) does.
private const val SHEETS = 4
private const val AGSL_WAVE = """
uniform float2 iResolution;
uniform float  iTime;
uniform float  ampScale;
uniform float  alphaScale;

// Reverse-engineered defaults from the source project's spline-settings.js. Named as they are
// there so the two can be compared without translating.
const float flowSpeed         = 0.18;
const float tension           = 0.12;
const float damping           = 0.0001;
const float splineLength      = 0.306001;
const float spacing           = 407.658;
const float perturbation      = 0.0998587;
const float perturbationScale = 0.07;
const float waveCosAmp        = 0.09;
const float waveBias          = -0.1;
const float waveHeightScale   = 0.5;
const float waveSoftClip      = 0.22;
const float ffdYAmp           = 0.03;
const float fresnelPower      = 4.0;
const float fresnelScale      = 0.5;

// AGSL is not GLSL: SkSL has no tanh, and asking for one fails at RuntimeShader construction
// rather than at build time. exp is there, so this is the identity written out, with the argument
// clamped because exp(2x) overflows long before the curve stops being flat.
float softTanh(float x) {
    float e = exp(2.0 * clamp(x, -8.0, 8.0));
    return (e - 1.0) / (e + 1.0);
}

// Their vertex shader's displacement, flattened to a function of (x, z, t). The spline texture
// lookup they start from is a low-frequency band, so it is folded into the FFD sine here rather
// than carried as a texture.
float waveHeight(float x, float z, float t, float amp) {
    float y = sin(x * 3.1 + z * 0.7 + t * flowSpeed) * ffdYAmp;

    float base = cos(x * 2.0 - t * 0.5) * waveCosAmp + waveBias;
    base *= (1.0 - damping);
    base += tension * sin(x * splineLength + t * flowSpeed * 0.25);

    float structured = perturbation * perturbationScale * (
        sin((x * splineLength * 6.0 + z * 0.5) * spacing * 0.01 + t * flowSpeed * 0.7) * 0.5 +
        sin((x * splineLength * 10.0 - z * 0.8) * spacing * 0.005 - t * flowSpeed * 0.35) * 0.25
    );

    float total = (base + structured) * waveHeightScale;
    // Their soft clip, which is what stops the crest spiking into a hard fin.
    total = waveSoftClip * softTanh(total / waveSoftClip);
    return (y - total) * amp;
}

// A cheap stable hash. Two layers of these are the PS3's additive point-sprite sparkles; this
// file's header has claimed "a scatter of sparkles" for some time while nothing drew any, which
// is why they were impossible to see.
float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float sparkles(float2 uv, float t) {
    float acc = 0.0;
    // Two scales, so they do not read as one grid. Each drifts at its own rate along the wave.
    for (int layer = 0; layer < 2; layer++) {
        float fl = float(layer);
        float scale = 26.0 + fl * 17.0;
        float2 sp = uv * float2(scale, scale * 0.45);
        sp.x += t * (0.05 + fl * 0.03);
        float2 cell = floor(sp);
        float2 f = fract(sp) - 0.5;
        float h = hash21(cell + fl * 31.7);
        if (h > 0.90) {
            float2 jitter = float2(hash21(cell + 5.1), hash21(cell + 9.3)) - 0.5;
            float d = length(f - jitter * 0.6);
            // Each one keeps its own rate and phase off its hash, so they do not blink together.
            float twinkle = 0.45 + 0.55 * sin(t * (1.1 + h * 2.4) + h * 40.0);
            acc += smoothstep(0.17, 0.0, d) * twinkle;
        }
    }
    return acc;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;
    // x in clip space, matching theirs; y measured down the screen as this file's gradient is.
    float px = uv.x * 2.0 - 1.0;
    float t  = iTime;

    float acc = 0.0;
    for (int i = 0; i < 4; i++) {
        float f = float(i) / 3.0;
        float z = f * 2.0 - 1.0;

        // Each sheet sits a little lower and is a little fainter than the one in front of it,
        // which is what the mesh's own depth does for them.
        float seat = 0.60 + f * 0.13;
        float h    = waveHeight(px, z, t, ampScale);
        float sy   = seat + h;

        float d = uv.y - sy;

        // Slope as a stand-in for the surface normal turning edge-on.
        float e  = 0.02;
        float dh = waveHeight(px + e, z, t, ampScale) - h;
        float slope = abs(dh) / e;
        float edgeOn = slope / sqrt(1.0 + slope * slope);
        float F = fresnelScale * pow(edgeOn, 1.0 / fresnelPower);

        // The sheet's body below its crest, and the bright line riding the crest itself.
        float body = smoothstep(0.0, 0.20, d) * 0.055;
        float line = exp(-pow(d * 34.0, 2.0)) * (0.10 + 0.22 * F);

        acc += (body + line) * (1.0 - f * 0.35);
    }

    // Sparkles ride the wave rather than the whole screen: a band around where the sheets sit,
    // or they read as dust on the glass instead of as part of the wave.
    float band = exp(-pow((uv.y - 0.70) * 2.4, 2.0));
    acc += sparkles(uv, t) * band * 0.42;

    acc = clamp(acc * alphaScale, 0.0, 0.62);
    return half4(1.0, 1.0, 1.0, 1.0) * acc;   // premultiplied white -> SrcOver lightens the gradient
}
"""

/**
 * The AGSL wave, or null when this device's SkSL will not compile it.
 *
 * RuntimeShader validates at CONSTRUCTION and throws IllegalArgumentException, so an unsupported
 * builtin is not a build error, it is a crash — and this draws behind the launcher's home screen,
 * so the crash is at boot, every boot. That is not hypothetical: this shader called tanh, which
 * GLSL has and SkSL does not, and the launcher died on launch until it was written out by hand.
 * SkSL is not uniform across vendors and Android versions, so the next one will be found the same
 * way. Falling back to the Canvas wave loses the Fresnel edge and keeps the launcher.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun rememberWaveShader(): RuntimeShader? = remember {
    runCatching { RuntimeShader(AGSL_WAVE) }
        .onFailure { Timber.e(it, "XMB wave shader did not compile; falling back to the Canvas wave") }
        .getOrNull()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderWave(time: Float, alphaScale: Float, ampScale: Float) {
    val shader = rememberWaveShader() ?: return FallbackWave(time, alphaScale, ampScale)
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("iTime", time)   // reading `time` here drives the per-frame redraw
        shader.setFloatUniform("ampScale", ampScale)
        shader.setFloatUniform("alphaScale", alphaScale)
        drawRect(brush = brush)
    }
}

// ── Canvas fallback (API < 33) ───────────────────────────────────────────────
// Same soft folds approximated with low-alpha white fills (the sheet) + faint crest strokes.
@Composable
private fun FallbackWave(time: Float, alphaScale: Float, ampScale: Float) {
    val amp = 0.05f * ampScale
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawFold(time, base01 = 0.63f, amp01 = amp * 0.9f, freq = 0.80f, phase = 1.7f, drift = -0.38f, sheet = 0.090f * alphaScale, edge = 0.125f * alphaScale)
        drawFold(time, base01 = 0.75f, amp01 = amp * 1.2f, freq = 0.42f, phase = 3.1f, drift = 0.30f,  sheet = 0.105f * alphaScale, edge = 0.145f * alphaScale)
    }
}

private fun DrawScope.drawFold(
    t: Float, base01: Float, amp01: Float, freq: Float, phase: Float, drift: Float,
    sheet: Float, edge: Float,
) {
    val w = size.width
    val h = size.height
    val n = 48
    val crestPath = Path()
    val fillPath = Path()
    fillPath.moveTo(0f, h)
    for (i in 0..n) {
        val xx = i / n.toFloat()
        val y = (base01 + amp01 * sin(xx * TAU * freq + t * drift + phase)) * h
        val x = xx * w
        if (i == 0) { crestPath.moveTo(x, y); fillPath.lineTo(x, y) }
        else { crestPath.lineTo(x, y); fillPath.lineTo(x, y) }
    }
    fillPath.lineTo(w, h)
    fillPath.close()

    // Sheet: a flat, faint white wash from the crest down — stacking the folds brightens the lower
    // screen like the reference. Crest: two soft white strokes for the gentle fold highlight.
    drawPath(fillPath, color = Color.White.copy(alpha = sheet))
    drawPath(crestPath, color = Color.White.copy(alpha = edge * 0.5f), style = Stroke(width = h * 0.022f))
    drawPath(crestPath, color = Color.White.copy(alpha = edge), style = Stroke(width = h * 0.006f))
}
