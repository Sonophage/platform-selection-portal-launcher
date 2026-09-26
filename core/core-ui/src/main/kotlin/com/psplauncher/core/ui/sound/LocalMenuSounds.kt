package com.psplauncher.core.ui.sound

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

val LocalMenuSounds: ProvidableCompositionLocal<(MenuSound) -> Unit> =
    staticCompositionLocalOf { { _: MenuSound -> } }
