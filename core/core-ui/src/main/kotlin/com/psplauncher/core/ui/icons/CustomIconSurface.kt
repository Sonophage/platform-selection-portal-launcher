package com.psplauncher.core.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable
fun CustomIconSurface(
    icon: CustomIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (icon is CustomIcon.Animated && LocalIconAnimating.current) {
        AsyncImage(
            model = icon.path,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        OverrideGlyphSurface(
            bitmap = icon.firstFrame,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}
