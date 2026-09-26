package com.psplauncher.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

enum class TextContrastRole(val threshold: Float) {
    BODY(4.5f),
    LARGE(3.0f),
}

fun relativeLuminance(c: Color): Double {
    fun linearize(channel: Float): Double {
        val v = channel.toDouble()
        return if (v <= 0.04045) v / 12.92
        else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * linearize(c.red) + 0.7152 * linearize(c.green) + 0.0722 * linearize(c.blue)
}

fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

fun ensureReadable(fg: Color, bg: Color, minContrast: Float = 4.5f): Color {
    if (contrastRatio(fg, bg) >= minContrast) return fg
    return bestPolarity(bg)
}

fun bestPolarity(bg: Color): Color =
    if (contrastRatio(Color.Black, bg) >= contrastRatio(Color.White, bg)) Color.Black else Color.White

fun composite(top: Color, bottom: Color): Color {
    val a = top.alpha
    return Color(
        red = top.red * a + bottom.red * (1f - a),
        green = top.green * a + bottom.green * (1f - a),
        blue = top.blue * a + bottom.blue * (1f - a),
    )
}

fun scrimAlphaFor(
    scrim: Color,
    background: Color,
    text: Color,
    target: Float = TextContrastRole.BODY.threshold,
): Float {
    if (contrastRatio(text, background) >= target) return 0f

    for (step in 1..256) {
        val a = step / 256f
        if (contrastRatio(text, composite(scrim.copy(alpha = a), background)) >= target) return a
    }
    return 1f
}

fun solveScrimColor(
    base: Color,
    alpha: Float,
    target: Float = TextContrastRole.BODY.threshold,
    text: Color = Color.White,
    worstCase: Color = Color.White,
): Color {
    fun passes(t: Float): Boolean =
        contrastRatio(
            text,
            composite(lerp(base, Color.Black, t).copy(alpha = alpha), worstCase),
        ) >= target

    if (passes(0f)) return base
    if (!passes(1f)) return Color.Black
    var lo = 0f
    var hi = 1f
    repeat(12) {
        val mid = (lo + hi) / 2f
        if (passes(mid)) hi = mid else lo = mid
    }
    return lerp(base, Color.Black, hi)
}

fun xmbScrimAnchors(backgroundTop: Color, backgroundBottom: Color): Pair<Color, Color> =
    solveScrimColor(backgroundTop, alpha = XMB_SCRIM_TOP_ALPHA).copy(alpha = XMB_SCRIM_TOP_ALPHA) to
        solveScrimColor(backgroundBottom, alpha = XMB_SCRIM_BOTTOM_ALPHA).copy(alpha = XMB_SCRIM_BOTTOM_ALPHA)

const val XMB_SCRIM_TOP_ALPHA = 0.72f
const val XMB_SCRIM_BOTTOM_ALPHA = 0.90f

@Immutable
data class ResolvedTextColor(

    val color: Color,

    val requested: Color,

    val adjusted: Boolean,

    val achievedRatio: Float,

    val meetsTarget: Boolean,
)

fun clampLightnessForContrast(
    fg: Color,
    bg: Color,
    target: Float = TextContrastRole.BODY.threshold,
    maxShift: Float = 0.35f,
): ResolvedTextColor {
    val plain = contrastRatio(fg, bg).toFloat()
    if (plain >= target) {
        return ResolvedTextColor(fg, fg, adjusted = false, achievedRatio = plain, meetsTarget = true)
    }

    val (h, s, l0) = fg.toHsl()
    fun at(l: Float) = hslToColor(h, s, l)
    fun passes(l: Float) = contrastRatio(at(l), bg) >= target

    fun bisect(pole: Float): Float? {
        if (!passes(pole)) return null
        var near = l0
        var far = pole
        repeat(8) {
            val mid = (near + far) / 2f
            if (passes(mid)) far = mid else near = mid
        }
        return far
    }

    val down = bisect(0f)
    val up = bisect(1f)
    val best = when {
        down != null && up != null -> if (l0 - down <= up - l0) down else up
        else -> down ?: up
    }

    if (best == null || kotlin.math.abs(best - l0) > maxShift) {
        return ResolvedTextColor(
            color = fg,
            requested = fg,
            adjusted = false,
            achievedRatio = plain,
            meetsTarget = false,
        )
    }

    val out = at(best)
    return ResolvedTextColor(
        color = out,
        requested = fg,
        adjusted = out != fg,
        achievedRatio = contrastRatio(out, bg).toFloat(),
        meetsTarget = true,
    )
}

internal fun Color.toHsl(): Triple<Float, Float, Float> {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val l = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        red -> (green - blue) / d + (if (green < blue) 6f else 0f)
        green -> (blue - red) / d + 2f
        else -> (red - green) / d + 4f
    } * 60f
    return Triple(h, s, l)
}

internal fun hslToColor(h: Float, s: Float, l: Float): Color {
    if (s == 0f) return Color(l, l, l)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun channel(tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    val hk = h / 360f
    return Color(
        channel(hk + 1f / 3f).coerceIn(0f, 1f),
        channel(hk).coerceIn(0f, 1f),
        channel(hk - 1f / 3f).coerceIn(0f, 1f),
    )
}
