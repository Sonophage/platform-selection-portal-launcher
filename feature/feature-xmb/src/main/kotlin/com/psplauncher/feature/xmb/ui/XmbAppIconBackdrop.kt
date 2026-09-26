package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.psplauncher.core.ui.icons.appIconBitmap

sealed interface XmbBackdrop {
    data class Art(val uri: String) : XmbBackdrop

    data class AppIcon(val packageName: String) : XmbBackdrop
}

private const val SOURCE_PX = 24

private const val BACKDROP_ALPHA = 0.55f

@Composable
fun XmbAppIconBackdrop(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val icon = remember(packageName) { context.appIconBitmap(packageName, sizePx = SOURCE_PX) } ?: return

    Image(
        bitmap = icon,
        contentDescription = null,
        contentScale = ContentScale.Crop,

        filterQuality = FilterQuality.High,
        modifier = modifier.fillMaxSize().alpha(BACKDROP_ALPHA),
    )
}
