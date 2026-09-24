package com.psplauncher.core.ui.wave

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.psplauncher.core.ui.theme.LocalPFPColors
import timber.log.Timber
import kotlin.math.sin

/**
 * The XMB wave — the PlayStation 3 crossbar's background ribbon.
 *
 * It lives in core-ui rather than with the XMB because it is no longer only the XMB's: the setup
 * wizard draws the same wave behind its own full-screen surface, and feature-settings cannot see
 * feature-xmb (feature-xmb depends on feature-settings, so the arrow only goes one way). A second
 * copy would be two shaders to keep in step and only one of them would ever get the next fix —
 * the same argument that already made [WaveLayers] one renderer for two call sites.
 *
 * Attribution and the porting notes are further down, at the AGSL source they belong to.
 */

// Frozen "time" (seconds) used to pose the wave when animation is disabled.
private const val STATIC_TIME = 2.0f
private const val TAU = 6.2831853f


/**
 * The wave itself: the strands and the bloom, with NO background of its own — for drawing over
 * something that is already there (a wallpaper, or the wizard's black).
 *
 * Split out so every place that draws a wave is one renderer with one frame clock. Copies would
 * be several things to keep in step and only one of them would ever get the next fix.
 */
@Composable
fun WaveLayers(
    waveStyle: WaveStyle,
    tint: Color = Color.White,
    /**
     * A multiplier on how fast the surface moves, for callers that want it to react.
     *
     * The crossbar slows it while nothing is happening and quickens it while a game is being
     * launched. It multiplies rather than replaces the style's own speed, so Reduced stays
     * proportionally slower than Animated whatever the caller asks for.
     */
    speedScale: Float = 1f,
) {
    val alphaScale = if (waveStyle.reduced) 0.5f else 1f
    val ampScale   = if (waveStyle.reduced) 0.65f else 1f

    // Continuously-increasing time in seconds since the first frame (so float precision stays sharp),
    // scaled by style speed. Frozen at STATIC_TIME when the wave shouldn't animate — no frame loop,
    // no per-frame recomposition. Only advances while this background is on screen.
    val animated = waveStyle.animated
    val speed = (if (waveStyle.reduced) 0.5f else 1f) * speedScale
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

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ShaderWave(time, alphaScale, ampScale, tint)
    } else {
        FallbackWave(time, alphaScale, ampScale, tint)
    }
    // Soft off-centre light bloom — the same gentle highlight the XMB has near the crossbar.
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = 0.10f), Color.Transparent),
                center = center.copy(x = size.width * 0.48f, y = size.height * 0.30f),
                radius = size.minDimension * 0.62f,
            )
        )
    }
}

@Composable
fun WaveBackground(
    waveStyle: WaveStyle,
    modifier: Modifier,
    /**
     * False draws the gradient alone.
     *
     * For the caller that wants the wave ON TOP of something — the crossbar draws the focused
     * item's artwork between the gradient and the wave — and needs the base without it. Drawing
     * the wave here as well would put a second one under the art, and since the art fades out
     * across the middle of the screen the two would both be visible, at different alphas.
     */
    drawWave: Boolean = true,
) {
    val colors = LocalPFPColors.current

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
        if (drawWave) WaveLayers(waveStyle)
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
// Fourteen hairlines bunched into one ribbon, which is what the reference image shows: not a
// stack of shaded sheets but a bundle of fine strands following one long S. Affordable only
// because each strand costs ONE height evaluation and no pow() — see the notes in main(). Every
// change here has been measured with `dumpsys gfxinfo`.
private const val SHEETS = 14
private const val AGSL_WAVE = """
uniform float2 iResolution;
uniform float  iTime;
uniform float  ampScale;
uniform float  alphaScale;
// White over the theme gradient, the wallpaper's accent when drawn over a wallpaper. The wave is
// emitted premultiplied, so this multiplies the colour and leaves the coverage alone.
uniform float3 waveTint;

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
// From their spline pipeline's per-row generation rather than the vertex shader. This is the
// part that makes one strand differ from the next: a phase that depends on z, and two travelling
// waves at DIFFERENT speeds running in OPPOSITE directions, so the pattern shears instead of
// sliding along as one sheet.
const float bandAmplitude     = 0.200;
const float bandSecondaryFreq = 7.0;
const float bandSecondaryAmp  = 0.025;
const float travelSpeed1      = 0.25;
const float travelAmp1        = 0.014;
const float travelSpeed2      = 0.15;
const float travelAmp2        = 0.008;

// Every x frequency, named ONCE. They were literals inside waveHeight and again inside the
// analytic derivative in main(), which is a pair that has to agree and only one of which anyone
// would think to change — the derivative would have gone on shading a wave that was no longer
// the wave being drawn, and it would have looked merely a bit off rather than broken.
//
// Lowered together to stretch the ribbon out: roughly one long S across the screen instead of
// two, which is what the reference shows.
// How far the wave swings, before the middle-third envelope narrows where it may do so.
const float waveGain    = 1.15;
const float mainFreq    = 1.28;
const float bandFreq    = 3.90;
const float band2Freq   = 3.00;
const float travel1Freq = 2.60;
const float travel2Freq = 5.40;

/**
 * How much the wave is allowed to move at this x. Peaks in the middle third, never collapses.
 *
 * The floor and the width both matter, and the first attempt got both wrong: 0.20 + 0.80 with a
 * width of 1.75 left only 5% of the swing at the screen edges, so all fourteen strands converged
 * into one flat pencil line and faded. That reads as the wave STARTING at the left edge and
 * ENDING at the right, when the whole point is that it runs in from off screen and back out.
 *
 * So the taper now happens mostly OUTSIDE the visible range: 0.70 of full swing still left at
 * px = ±1, which keeps the strands fanned and the shape continuing as it leaves. The middle
 * third still moves half as much again as the margins, which is the bias that was asked for —
 * a bias, not a fade to nothing.
 */
float travelEnvelope(float px) {
    float a = px * 0.75;
    return 0.30 + 0.70 * exp(-a * a);   // a*a, not pow(a, 2.0) — this is on the hot path
}

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

    // Their rowPhase: flow * 0.25 + z * 1.7. The z term is why no two strands sit at the same
    // point in the cycle, and the two travel terms below run at 0.25 and 0.15 in opposite
    // directions, which is why the strands drift apart instead of moving as one.
    float flow = t * flowSpeed;
    float rowPhase = flow * 0.25 + z * 1.7;
    y += sin(rowPhase + (x * 0.5 + 0.5) * bandFreq) * bandAmplitude * 0.10;
    y += cos(z * bandSecondaryFreq + (x * 0.5 + 0.5) * band2Freq + flow * 0.09) * bandSecondaryAmp;
    y += sin(((x * 0.5 + 0.5) * travel1Freq + z * 0.8) - flow * travelSpeed1) * travelAmp1 * tension * 12.0;
    y += sin(((x * 0.5 + 0.5) * travel2Freq - z * 1.2) + flow * travelSpeed2) * travelAmp2 * 12.0;

    float base = cos(x * mainFreq - t * 0.5) * waveCosAmp + waveBias;
    base *= (1.0 - damping);
    base += tension * sin(x * splineLength + t * flowSpeed * 0.25);

    float structured = perturbation * perturbationScale * (
        sin((x * splineLength * 6.0 + z * 0.5) * spacing * 0.01 + t * flowSpeed * 0.7) * 0.5 +
        sin((x * splineLength * 10.0 - z * 0.8) * spacing * 0.005 - t * flowSpeed * 0.35) * 0.25
    );

    float total = (base + structured) * waveHeightScale;
    // Their soft clip, which is what stops the crest spiking into a hard fin.
    total = waveSoftClip * softTanh(total / waveSoftClip);
    // waveGain scales the whole displacement. It was cut to 0.62 to "keep the movement shorter",
    // which shortened the wrong thing — the envelope below is what confines the motion to the
    // middle third, and the gain on top of it just flattened the S everywhere. Back above 1.0:
    // full swing where the envelope allows any, still nearly flat at the edges.
    //
    // The envelope is NOT applied here — it depends only on x, so main() computes it once and
    // scales the result, rather than this evaluating it once per strand for the same answer.
    return (y - total) * amp * waveGain;
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
        // Real drift. This was 0.05 cells/sec, which over a 26-cell grid is about a pixel a
        // second — moving, but not observably. Each layer travels at its own rate so they
        // separate instead of sliding as one sheet, and a slow vertical crawl stops the field
        // reading as a rigid grid being panned.
        sp.x += t * (0.34 + fl * 0.21);
        sp.y += t * (0.06 + fl * 0.04);
        float2 cell = floor(sp);
        float2 f = fract(sp) - 0.5;
        float h = hash21(cell + fl * 31.7);
        if (h > 0.90) {
            float2 jitter = float2(hash21(cell + 5.1), hash21(cell + 9.3)) - 0.5;
            float d = length(f - jitter * 0.6);
            // Each one keeps its own rate and phase off its hash, so they do not blink together.
            float twinkle = 0.45 + 0.55 * sin(t * (1.1 + h * 2.4) + h * 40.0);
            // Half the previous radius: these were soft blobs rather than points of light.
            acc += smoothstep(0.085, 0.0, d) * twinkle;
        }
    }
    return acc;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;
    // x in clip space, matching theirs; y measured down the screen as this file's gradient is.
    float px = uv.x * 2.0 - 1.0;
    float t  = iTime;

    // Loop-invariant: the envelope is a function of x alone. Evaluating it inside the loop cost
    // fourteen exp calls per pixel for one value and took legacy jank from 0.4% to 26.7% —
    // measured, not guessed.
    float env = travelEnvelope(px);

    float acc = 0.0;
    float crestY = 0.70;   // replaced by the front sheet's crest below
    for (int i = 0; i < ${SHEETS}; i++) {
        float f = float(i) / ${SHEETS - 1}.0;
        float z = f * 2.0 - 1.0;

        // Bunched, not spread. The reference is one ribbon about a twentieth of the screen deep
        // with every strand inside it; spreading them over a quarter of the screen was what made
        // this read as stacked sheets instead of a bundle of hairs.
        float seat = 0.50 + f * 0.036;
        // ONE clock for every strand. They were each given their own rate, 0.80x to 1.28x, and
        // the bundle came apart — strands overtaking one another reads as interference rather
        // than as a ribbon. They still differ, by the z phase inside waveHeight, which offsets
        // them in space without letting them drift apart in time; that is what makes the bundle
        // travel as one object with depth in it.
        float h = waveHeight(px, z, t, ampScale) * env;
        float sy   = seat + h;

        float d = uv.y - sy;
        // The front sheet is the one the sparkles ride.
        if (i == 0) { crestY = sy; }

        // The slope, analytically, from the two terms that dominate it — rather than a second
        // full waveHeight call. That finite difference cost as much as the strand itself, and
        // paying it fourteen times buys less than spending the same budget on fourteen strands
        // instead of seven. Two trig calls in place of eight.
        float flowS = t * flowSpeed;
        float dMain = -mainFreq * sin(px * mainFreq - t * 0.5) * waveCosAmp * waveHeightScale;
        float dBandT = 0.5 * bandFreq * cos(flowS * 0.25 + z * 1.7 + (px * 0.5 + 0.5) * bandFreq)
                     * bandAmplitude * 0.10;
        // The same gain and envelope the height carries, or the shading would describe a wave
        // steeper or flatter than the one on screen.
        float slope = abs(dMain + dBandT) * ampScale * waveGain * env;
        float edgeOn = slope / sqrt(1.0 + slope * slope);
        // fresnelPower is 4, so pow(edgeOn, 0.25) is a fourth root — two sqrts, and sqrt is a
        // machine instruction where pow is a call.
        float F = fresnelScale * sqrt(sqrt(edgeOn));

        // The sheet's body below its crest, and the bright line riding the crest itself.
        //
        // Both are half as soft as they were: the body's ramp 0.20 -> 0.10, and the crest's
        // falloff 34 -> 68, which is the same halving written the other way round because one is
        // a width and the other is its reciprocal. The wave read as a smear rather than as a
        // surface with an edge.
        // NO body wash. A smoothstep fill under each crest is what turned these into sheets; the
        // reference has none at all, only the lines. Hairline crest: 150 -> 430, about two pixels
        // at 1080p.
        // 430 -> 330: a little blur back into the strands, which were rendering close to
        // aliasing on a 1080p panel.
        float dn = d * 330.0;
        float line = exp(-dn * dn) * (0.16 + 0.30 * F);

        // Strands fade only a little toward the back, so the bundle reads as one object with
        // depth in it rather than as a queue.
        acc += line * (1.0 - f * 0.40);
    }

    // Sparkles ride the front strand's crest so they travel with the ribbon. The reference
    // scatters them around it and well ABOVE it, thinning downward — spray thrown off the crest
    // rather than a symmetric halo — so the falloff is asymmetric: slow up, sharp down.
    float dBand = uv.y - crestY;
    float bu = dBand * 3.4; float bd = dBand * 9.0;
    float band = dBand < 0.0 ? exp(-bu * bu) : exp(-bd * bd);
    acc += sparkles(uv, t) * band * 0.80;

    acc = clamp(acc * alphaScale, 0.0, 0.62);
    return half4(half3(waveTint), 1.0) * acc;   // premultiplied -> SrcOver lightens what is behind
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
private fun ShaderWave(time: Float, alphaScale: Float, ampScale: Float, tint: Color) {
    val shader = rememberWaveShader() ?: return FallbackWave(time, alphaScale, ampScale, tint)
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("iTime", time)   // reading `time` here drives the per-frame redraw
        shader.setFloatUniform("ampScale", ampScale)
        shader.setFloatUniform("alphaScale", alphaScale)
        shader.setFloatUniform("waveTint", tint.red, tint.green, tint.blue)
        drawRect(brush = brush)
    }
}

// ── Canvas fallback (API < 33) ───────────────────────────────────────────────
// Same soft folds approximated with low-alpha white fills (the sheet) + faint crest strokes.
@Composable
private fun FallbackWave(time: Float, alphaScale: Float, ampScale: Float, tint: Color) {
    val amp = 0.05f * ampScale
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawFold(time, base01 = 0.63f, amp01 = amp * 0.9f, freq = 0.80f, phase = 1.7f, drift = -0.38f, sheet = 0.090f * alphaScale, edge = 0.125f * alphaScale, tint = tint)
        drawFold(time, base01 = 0.75f, amp01 = amp * 1.2f, freq = 0.42f, phase = 3.1f, drift = 0.30f,  sheet = 0.105f * alphaScale, edge = 0.145f * alphaScale, tint = tint)
    }
}

private fun DrawScope.drawFold(
    t: Float, base01: Float, amp01: Float, freq: Float, phase: Float, drift: Float,
    sheet: Float, edge: Float, tint: Color,
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
    drawPath(fillPath, color = tint.copy(alpha = sheet))
    drawPath(crestPath, color = tint.copy(alpha = edge * 0.5f), style = Stroke(width = h * 0.022f))
    drawPath(crestPath, color = tint.copy(alpha = edge), style = Stroke(width = h * 0.006f))
}
