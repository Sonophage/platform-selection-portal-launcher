package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.image.rememberArtworkModel

/**
 * A fan of the newest covers inside the focused card, on the right of the crossbar.
 *
 * 1a's one job for that half of the screen: "Right side previews the newest covers in the focused
 * item." It answers "what is actually in here" for a card whose row can only say "64 games", and
 * it does it with the one thing a game library has plenty of.
 *
 * ONLY ON GAMES-ROOT CARDS, which is also why it never fights the hover panel for the space. The
 * panel is drawn for a focused real GAME with backdrop art; a card — All Games, Favorites, a
 * console — is not a real game, so the two are never on screen together. Nothing here needs to
 * know that, but it is the reason this can take the same corner unconditionally.
 *
 * EVERY NUMBER IS A FRACTION of the surface it is given, converted from 1a's pixels on its
 * 1920x1080 frame. The design's 520x380 box at right:120, top:470 is the caller's business; what
 * is below is the arrangement inside that box. Fractions rather than dp because the crossbar is
 * themeable and this panel has to keep its proportions on a screen that is not 1920 wide.
 */
@Composable
internal fun XmbCoverFan(covers: List<String>, modifier: Modifier = Modifier) {
    if (covers.isEmpty()) return
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        // Back to front: the two leaning cards, then the upright one over them. Slots are filled
        // in THIS order from the newest cover down, so one cover is the centre card alone rather
        // than a lone card leaning off to one side with nothing to lean against.
        val slots = listOf(
            // x, y, width, height, degrees, alpha, lift — centre first for the fill order
            FanSlot(0.2885f, 0.0000f, 0.4231f, 0.8105f, 0f, 1.00f, 25.dp),
            FanSlot(0.0577f, 0.1053f, 0.3846f, 0.7368f, -8f, 0.85f, 20.dp),
            FanSlot(0.5385f, 0.0789f, 0.3846f, 0.7895f, 7f, 0.85f, 20.dp),
        )
        val filled = covers.take(slots.size).mapIndexed { i, uri -> slots[i] to uri }
        // ...then reversed for DRAWING, so the centre card ends up on top of the leaners it was
        // chosen before. Fill order and paint order are different questions with different answers.
        filled.reversed().forEach { (slot, uri) ->
            AsyncImage(
                model = rememberArtworkModel(uri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(x = w * slot.x, y = h * slot.y)
                    .size(width = w * slot.width, height = h * slot.height)
                    .graphicsLayer {
                        rotationZ = slot.degrees
                        alpha = slot.alpha
                        shadowElevation = slot.lift.toPx()
                        shape = RoundedCornerShape(size.minDimension * CornerFraction)
                        clip = true
                    },
            )
        }
    }
}

/** One card's place in the fan, as fractions of the fan's own box. */
private data class FanSlot(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val degrees: Float,
    val alpha: Float,
    val lift: androidx.compose.ui.unit.Dp,
)

/** 1a rounds the covers by 12px against a 220px card. */
private const val CornerFraction = 12f / 220f

/** Where the fan sits, as fractions of the whole screen — 1a's 520x380 box at right:120, top:470. */
internal object XmbCoverFanPlacement {
    const val WidthFraction = 520f / 1920f
    const val HeightFraction = 380f / 1080f
    const val RightInsetFraction = 120f / 1920f
    const val TopFraction = 470f / 1080f
}
