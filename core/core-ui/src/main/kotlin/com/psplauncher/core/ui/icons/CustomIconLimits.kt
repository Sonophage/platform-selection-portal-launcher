package com.psplauncher.core.ui.icons

object CustomIconLimits {
    val SUPPORTED_MIME = setOf(
        "image/png",
        "image/jpeg",
        "image/webp",
        "image/bmp",
        "image/heif",
        "image/gif",
    )

    const val MIME_GIF = "image/gif"

    const val MAX_BYTES = 8L * 1024 * 1024

    const val MAX_DIMENSION = 512

    const val MAX_FRAMES = 120

    const val MAX_DURATION_MS = 10_000L

    const val MSG_UNSUPPORTED_FORMAT = "Unsupported format — use PNG, JPG, WEBP, BMP, HEIC, or GIF"
    const val MSG_TOO_LARGE_RESOLUTION = "Image is too large — 512px or smaller for a GIF"
    const val MSG_TOO_MANY_FRAMES = "GIF has too many frames — 120 or fewer"
    const val MSG_TOO_LONG = "GIF is too long — 10 seconds or less"
    const val MSG_TOO_LARGE_BYTES = "File is too large — under 8 MB"
    const val MSG_UNDECODABLE = "Couldn't read that image — try a different file"

    data class Probe(
        val mime: String?,
        val width: Int,
        val height: Int,
        val frameCount: Int?,
        val durationMs: Long?,
        val bytes: Long,
    )

    fun validate(probe: Probe): String? = when {
        probe.mime == null || probe.mime !in SUPPORTED_MIME -> MSG_UNSUPPORTED_FORMAT
        probe.width <= 0 || probe.height <= 0 -> MSG_UNDECODABLE
        probe.bytes > MAX_BYTES -> MSG_TOO_LARGE_BYTES
        probe.mime == MIME_GIF -> when {
            probe.width > MAX_DIMENSION || probe.height > MAX_DIMENSION -> MSG_TOO_LARGE_RESOLUTION
            probe.frameCount != null && probe.frameCount > MAX_FRAMES -> MSG_TOO_MANY_FRAMES
            probe.durationMs != null && probe.durationMs > MAX_DURATION_MS -> MSG_TOO_LONG
            else -> null
        }
        else -> null
    }
}
