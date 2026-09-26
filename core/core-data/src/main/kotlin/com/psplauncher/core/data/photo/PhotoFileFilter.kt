package com.psplauncher.core.data.photo

object PhotoFileFilter {
    val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp",
        "heic", "heif", "tif", "tiff",
    )

    fun isPhoto(fileName: String, mimeType: String?): Boolean {
        if (mimeType != null && mimeType != "application/octet-stream") {
            return mimeType.startsWith("image/")
        }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }
}
