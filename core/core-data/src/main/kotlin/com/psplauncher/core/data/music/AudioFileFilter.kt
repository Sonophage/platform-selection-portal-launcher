package com.psplauncher.core.data.music

object AudioFileFilter {
    val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "ogg", "opus", "m4a", "aac", "wav", "wma",
    )

    fun isAudio(fileName: String, mimeType: String?): Boolean {
        if (mimeType != null) return mimeType.startsWith("audio/")
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }
}
