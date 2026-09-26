package com.psplauncher.themekit

object MotionLimits {
    val SUPPORTED_MIME = setOf("video/mp4", "video/webm", "image/gif", "image/webp")

    const val MAX_WIDTH = 1920
    const val MAX_HEIGHT = 1080

    const val MAX_DURATION_MS = 60_000L

    const val MAX_BYTES = 60L * 1024 * 1024

    const val ADVISORY_MAX_FPS = 30

    const val MSG_UNSUPPORTED_FORMAT = "Unsupported format — use MP4, WebM, or GIF"
    const val MSG_TOO_LARGE_RESOLUTION = "Video is too large — 1080p or smaller"
    const val MSG_TOO_LONG = "Clip is too long — 60 seconds or less"
    const val MSG_TOO_LARGE_BYTES = "File is too large — under 60 MB"
    const val MSG_UNDECODABLE = "Couldn't read that video — try a different file"

    val knownExtensions = setOf("mp4", "m4v", "webm", "gif", "webp")

    data class Probe(
        val mime: String?,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val bytes: Long,
    )

    fun validate(probe: Probe): String? = when {
        probe.mime == null || probe.mime !in SUPPORTED_MIME -> MSG_UNSUPPORTED_FORMAT
        probe.width <= 0 || probe.height <= 0 ||
            maxOf(probe.width, probe.height) > MAX_WIDTH ||
            minOf(probe.width, probe.height) > MAX_HEIGHT -> MSG_TOO_LARGE_RESOLUTION
        probe.durationMs > MAX_DURATION_MS -> MSG_TOO_LONG
        probe.bytes > MAX_BYTES -> MSG_TOO_LARGE_BYTES
        else -> null
    }

    fun mimeForExtension(extension: String): String? = when (extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> null
    }

    fun bundleExtensionFor(extension: String): String? = when (extension.lowercase()) {
        "mp4", "m4v" -> "mp4"
        "webm" -> "webm"
        "gif" -> "gif"
        else -> null
    }
}
