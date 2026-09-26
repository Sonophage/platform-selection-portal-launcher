package com.psplauncher.themekit

object UiMediaLimits {
    enum class Kind { SOUND, VIDEO, AUDIO_TRACK }

    data class Spec(
        val kind: Kind,
        val recommendedMinMs: Long,
        val recommendedMaxMs: Long,
        val hardMaxMs: Long,
        val maxBytes: Long,
    )

    const val SOUND_MAX_MS          = 500L
    const val CONFIRM_MAX_MS        = 1_000L
    const val LAUNCH_MAX_MS         = 3_000L

    const val NOTIFICATION_MAX_MS   = 2_000L

    const val GAMEBOOT_SEQUENCE_MS  = 2_000L

    const val GAMEBOOT_CLIP_MAX_MS  = 10_000L
    const val BOOT_MAX_MS           = 10_000L

    const val MENU_MUSIC_MAX_MS     = 900_000L

    const val AUDIO_STAGE_MAX_BYTES = 128L * 1024 * 1024
    const val VIDEO_MAX_BYTES = 25L * 1024 * 1024

    val NAVIGATION   = Spec(Kind.SOUND,       0L,    250L,   SOUND_MAX_MS,   AUDIO_STAGE_MAX_BYTES)
    val CONFIRM      = Spec(Kind.SOUND,       0L,   500L,   CONFIRM_MAX_MS, AUDIO_STAGE_MAX_BYTES)
    val BACK         = Spec(Kind.SOUND,       0L,   500L,   CONFIRM_MAX_MS, AUDIO_STAGE_MAX_BYTES)
    val LAUNCH       = Spec(Kind.SOUND,       0L, 2_000L,   LAUNCH_MAX_MS,  AUDIO_STAGE_MAX_BYTES)
    val NOTIFICATION = Spec(Kind.SOUND,       0L, 1_500L,   NOTIFICATION_MAX_MS, AUDIO_STAGE_MAX_BYTES)
    val ERROR        = Spec(Kind.SOUND,       0L,   500L,   CONFIRM_MAX_MS, AUDIO_STAGE_MAX_BYTES)

    val GAMEBOOT_CLIP = Spec(Kind.VIDEO,      1_000L, 8_000L, GAMEBOOT_CLIP_MAX_MS, VIDEO_MAX_BYTES)
    val BOOT         = Spec(Kind.AUDIO_TRACK, 0L, 8_000L, BOOT_MAX_MS,    AUDIO_STAGE_MAX_BYTES)
    val MENU_MUSIC   = Spec(Kind.AUDIO_TRACK, 0L, 8_000L, MENU_MUSIC_MAX_MS, AUDIO_STAGE_MAX_BYTES)

    val LAUNCH_DISC_AUDIO = Spec(Kind.AUDIO_TRACK, 0L, 8_000L, BOOT_MAX_MS, AUDIO_STAGE_MAX_BYTES)
    val GAMEBOOT_AUDIO    = Spec(Kind.AUDIO_TRACK, 0L, 5_000L, BOOT_MAX_MS, AUDIO_STAGE_MAX_BYTES)
    val BOOT_CLIP    = Spec(Kind.VIDEO,       1_000L, 8_000L, BOOT_MAX_MS,    VIDEO_MAX_BYTES)

    val AUDIO_MIME = setOf(
        "audio/mpeg", "audio/wav", "audio/x-wav", "audio/ogg", "audio/mp4", "audio/mp4a-latm",
    )

    val VIDEO_MIME = setOf("video/mp4", "video/webm")

    const val MSG_UNSUPPORTED_FORMAT_AUDIO = "Unsupported format — use MP3, WAV, OGG, or M4A audio"
    const val MSG_UNSUPPORTED_FORMAT_VIDEO = "Unsupported format — use MP4 or WebM video"
    const val MSG_TOO_LARGE_BYTES_SOUND = "File is too large"
    const val MSG_TOO_LARGE_BYTES_VIDEO = "File is too large — videos must be under 25 MB"
    const val MSG_UNDECODABLE = "Couldn't read that file — try a different one"

    const val MSG_NO_DURATION =
        "Couldn't read that file's length — try a different file, or convert it to MP3/WAV/OGG/M4A"

    data class Probe(
        val mime: String?,
        val durationMs: Long?,
        val bytes: Long,
    )

    fun validate(spec: Spec, probe: Probe): String? = when {
        probe.mime == null || probe.mime !in mimesFor(spec.kind) ->
            if (spec.kind == Kind.VIDEO) MSG_UNSUPPORTED_FORMAT_VIDEO else MSG_UNSUPPORTED_FORMAT_AUDIO
        probe.durationMs == null -> MSG_NO_DURATION
        probe.durationMs > spec.hardMaxMs -> tooLong(spec)
        probe.bytes > spec.maxBytes -> tooLarge(spec)
        else -> null
    }

    fun tooLong(spec: Spec): String = "That clip is too long — ${formatSeconds(spec.hardMaxMs)} or less"

    fun tooLarge(spec: Spec): String =
        if (spec.kind == Kind.VIDEO) MSG_TOO_LARGE_BYTES_VIDEO else MSG_TOO_LARGE_BYTES_SOUND

    fun mimeForExtension(extension: String): String? = when (extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg", "oga" -> "audio/ogg"
        "m4a", "m4b", "mp4a" -> "audio/mp4"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        else -> null
    }

    val knownUiMediaExtensions = setOf("mp3", "wav", "ogg", "oga", "m4a", "m4b", "mp4a", "mp4", "m4v", "webm")

    fun extensionForMime(mime: String): String? = when (mime.lowercase()) {
        "audio/mpeg" -> "mp3"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/ogg" -> "ogg"
        "audio/mp4", "audio/mp4a-latm" -> "m4a"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        else -> null
    }

    private fun mimesFor(kind: Kind): Set<String> =
        if (kind == Kind.VIDEO) VIDEO_MIME else AUDIO_MIME

    private fun formatSeconds(ms: Long): String {
        val whole = ms / 1000
        val frac = (ms % 1000) / 100
        return if (frac == 0L) "$whole s" else "$whole.$frac s"
    }
}
