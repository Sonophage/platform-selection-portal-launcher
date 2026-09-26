package com.psplauncher.core.ui.icons

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

sealed interface CustomIcon {
    val firstFrame: ImageBitmap

    data class Still(override val firstFrame: ImageBitmap) : CustomIcon

    data class Animated(val path: String, override val firstFrame: ImageBitmap) : CustomIcon
}

val LocalCustomIcons = staticCompositionLocalOf<Map<String, CustomIcon>> { emptyMap() }

val LocalIconAnimating = staticCompositionLocalOf { false }
