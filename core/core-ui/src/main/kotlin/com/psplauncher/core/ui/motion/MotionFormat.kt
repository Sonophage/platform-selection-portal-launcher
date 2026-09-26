package com.psplauncher.core.ui.motion

enum class MotionFormat { VIDEO, ANIMATED_IMAGE }

fun formatOf(path: String): MotionFormat =
    if (path.endsWith(".gif", ignoreCase = true) || path.endsWith(".webp", ignoreCase = true)) {
        MotionFormat.ANIMATED_IMAGE
    } else {
        MotionFormat.VIDEO
    }
