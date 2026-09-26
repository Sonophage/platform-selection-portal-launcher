package com.psplauncher.core.data.video

object VideoFileFilter {
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "flv",
        "ts", "m2ts", "mts", "mpg", "mpeg", "3gp", "ogv",
    )

    fun isVideo(fileName: String, mimeType: String?): Boolean {
        if (mimeType != null && mimeType != "application/octet-stream") {
            return mimeType.startsWith("video/")
        }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }
}
