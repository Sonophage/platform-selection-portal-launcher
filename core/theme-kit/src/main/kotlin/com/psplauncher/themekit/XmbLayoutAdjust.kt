package com.psplauncher.themekit

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class XmbLayoutAdjust(
    val scale: Float = 1f,
    val barLeftFraction: Float = 0f,
    val barTopFraction: Float = XmbLayoutSpec.DEFAULT.barTopFraction,
) {
    companion object {
        val DEFAULT = XmbLayoutAdjust()

        const val SCALE_MIN = 0.6f
        const val SCALE_MAX = 1.8f
        const val LEFT_MIN = -0.25f
        const val LEFT_MAX = 0.35f

        const val TOP_MIN = XmbLayoutSpecCodec.BAR_TOP_MIN
        const val TOP_MAX = XmbLayoutSpecCodec.BAR_TOP_MAX
    }
}

enum class XmbFormFactor(val key: String) {
    COMPACT("compact"),
    MEDIUM("medium"),
    EXPANDED("expanded");

    companion object {
        fun forSmallestWidthDp(swDp: Int): XmbFormFactor = when {
            swDp < 600 -> COMPACT
            swDp < 840 -> MEDIUM
            else -> EXPANDED
        }
    }
}

object XmbLayoutAdjustCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mapSerializer = MapSerializer(String.serializer(), XmbLayoutAdjust.serializer())

    fun encode(map: Map<String, XmbLayoutAdjust>): String =
        json.encodeToString(mapSerializer, map.mapValues { sanitize(it.value) })

    fun decode(encoded: String?): Map<String, XmbLayoutAdjust> {
        if (encoded.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(mapSerializer, encoded) }
            .getOrNull()
            ?.mapValues { sanitize(it.value) }
            ?: emptyMap()
    }

    fun sanitize(a: XmbLayoutAdjust): XmbLayoutAdjust = XmbLayoutAdjust(
        scale = a.scale.safe(XmbLayoutAdjust.DEFAULT.scale).coerceIn(XmbLayoutAdjust.SCALE_MIN, XmbLayoutAdjust.SCALE_MAX),
        barLeftFraction = a.barLeftFraction.safe(0f).coerceIn(XmbLayoutAdjust.LEFT_MIN, XmbLayoutAdjust.LEFT_MAX),
        barTopFraction = a.barTopFraction.safe(XmbLayoutAdjust.DEFAULT.barTopFraction)
            .coerceIn(XmbLayoutAdjust.TOP_MIN, XmbLayoutAdjust.TOP_MAX),
    )

    private fun Float.safe(fallback: Float): Float = if (isNaN() || isInfinite()) fallback else this
}
