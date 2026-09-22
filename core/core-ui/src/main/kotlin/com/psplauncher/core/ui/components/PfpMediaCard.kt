package com.psplauncher.core.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.detail.detailPalette
import com.psplauncher.core.ui.image.rememberArtworkModel
import com.psplauncher.core.ui.theme.LocalPfpTextColors
import com.psplauncher.core.ui.theme.menuCursorEdge

/**
 * One thing in the library, drawn as a portrait card: its art, its name, and what it is.
 *
 * ONE component for every surface that shows a grid or rail of library items — the Last Played
 * shelf, search results, and the app menu. Each of those had grown its own card, which is three
 * renderers for one idea: three focus treatments to keep matching, three fallbacks for missing
 * art, and a change that lands in whichever one the author happened to be looking at.
 *
 * Portrait throughout, including for the square things. Box art, film posters and book covers are
 * all roughly 2:3, and app icons and album art are square; a grid that changed shape per row
 * would read as broken alignment rather than as variety.
 *
 * The art is FITTED, never cropped. A 2:3 cover fills a 2:3 tile either way, so cropping would
 * only ever damage the odd ones out — a square app icon would lose its edges and a 16:9 video
 * still would lose most of its frame.
 *
 * What fitting costs is the tile around those odd ones out, and left flat that cost was too high:
 * a square app icon and a landscape GBA cover both sat as a band in a black void, which reads as
 * a failed image. So the same art is ALSO drawn cropped-to-fill and blurred underneath, and the
 * empty tile takes the colour of the thing it belongs to.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PfpMediaCard(
    title: String,
    /**
     * The art: a `String` uri for anything on disk, or a `Drawable` for an installed app's icon,
     * which comes from PackageManager and has no uri at all.
     *
     * Typed as Any? because that is what Coil takes. A uri goes through rememberArtworkModel so
     * it picks up the cache key that makes a re-scraped picture actually refresh; anything else
     * is handed over as-is.
     */
    art: Any?,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** What it is — "Game · Game Boy Advance", an artist, a year. Null draws no second line. */
    subtitle: String? = null,
    /** Y / long-press. Null leaves the card with no menu, which is not the same as an empty one. */
    onLongClick: (() -> Unit)? = null,
    width: Dp = PfpMediaCardDefaults.Width,
) {
    val palette = detailPalette()
    val shape = RoundedCornerShape(10.dp)

    val model = when (art) {
        is String -> rememberArtworkModel(art)
        else -> art
    }

    Column(
        modifier
            .let { if (width == Dp.Unspecified) it else it.width(width) }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PfpMediaCardDefaults.ArtRatio)
                .clip(shape)
                .background(palette.rowFill)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) menuCursorEdge() else palette.rowEdge,
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (model != null && art != "") {
                // The same art, cropped to fill and blurred, UNDER the fitted copy — see the
                // class comment for why the tile can't just be left black.
                //
                // Android 12+ only. Modifier.blur is a silent no-op below it, which would leave a
                // sharp zoomed crop behind the art: worse than the black it replaces.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.55f,
                        modifier = Modifier.fillMaxSize().blur(18.dp),
                    )
                }
                AsyncImage(
                    model = model,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                )
            } else {
                // The name, in the tile. A blank tile with the title underneath reads as a failed
                // image; a tile that says what it is reads as a thing without a picture.
                Text(
                    text = title,
                    color = palette.textMuted,
                    fontSize = 12.sp,
                    maxLines = 4,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = LocalPfpTextColors.current.primary,
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = LocalPfpTextColors.current.secondary,
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

object PfpMediaCardDefaults {
    /** 2:3, the shape box art, film posters and book covers already are. */
    const val ArtRatio = 2f / 3f

    /** The rail's width. Grids pass their own so a row divides the screen evenly. */
    val Width: Dp = 104.dp
}
