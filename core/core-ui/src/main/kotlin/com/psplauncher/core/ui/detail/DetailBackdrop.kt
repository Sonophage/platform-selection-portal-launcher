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

// ── Full-page art backdrop ────────────────────────────────────────────────────
//
// The entry's own artwork behind the WHOLE page, not a banner card at the top of it. A card put a
// frame around the art and then asked the page to live below the frame; a backdrop makes the art
// the page, and everything on it is text and buttons floating over the game's own picture.
//
// The tint over it is a vertical gradient that is nearly clear at the top and solid at the bottom.
// That is the entire legibility strategy: the top of the page is where the art is worth seeing
// (the logo sits there, and a logo is designed to be read over its own key art), and the bottom is
// where the overview, the buttons and the information band need a surface. Between them the art
// dissolves into the page's own colour rather than into black, so the game's palette carries all
// the way down.

/**
 * The backdrop tint's gradient stops, given the page colour to dissolve into.
 *
 * A pure function so the one thing that can silently ruin this screen is testable: if these stops
 * ever run the other way the art is covered at the top and exposed under the body text, which is
 * both halves of the design inverted at once and still renders without error.
 */
fun detailBackdropStops(pageTone: Color): Array<Pair<Float, Color>> = arrayOf(
    0f to pageTone.copy(alpha = 0.10f),
    0.32f to pageTone.copy(alpha = 0.46f),
    0.64f to pageTone.copy(alpha = 0.84f),
    1f to pageTone.copy(alpha = 0.97f),
)

/**
 * Draws [artworkUri] full-bleed behind the page with the tint above it. Nothing at all when there
 * is no artwork, so a game without art falls back to the plain page gradient underneath.
 *
 * Deliberately fixed to the viewport rather than scrolled with the body: the art is the page's
 * surface, and a surface that slides away under the text reads as a very tall image instead.
 */
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
