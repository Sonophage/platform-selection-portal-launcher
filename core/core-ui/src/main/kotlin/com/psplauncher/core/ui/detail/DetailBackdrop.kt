package com.psplauncher.core.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.image.rememberArtworkModel

fun detailBackdropStops(pageTone: Color): Array<Pair<Float, Color>> = arrayOf(
    0f to pageTone.copy(alpha = 0.10f),
    0.32f to pageTone.copy(alpha = 0.46f),
    0.64f to pageTone.copy(alpha = 0.84f),
    1f to pageTone.copy(alpha = 0.97f),
)

@Composable
fun BoxScope.PfpDetailArtBackdrop(artworkUri: String?) {
    if (artworkUri.isNullOrBlank()) return
    val pageTone = detailSurfaceBottom()
    Box(Modifier.matchParentSize()) {
        AsyncImage(
            model = rememberArtworkModel(artworkUri),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colorStops = detailBackdropStops(pageTone))),
        )
    }
}
