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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PfpMediaCard(
    title: String,

    art: Any?,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,

    subtitle: String? = null,

    initialOnly: Boolean = false,

    onLongClick: (() -> Unit)? = null,
    width: Dp = PfpMediaCardDefaults.Width,

    progress: Float? = null,
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
                Text(

                    text = if (initialOnly) title.trim().firstOrNull()?.uppercase() ?: "?" else title,
                    color = palette.textMuted,
                    fontSize = if (initialOnly) 28.sp else 12.sp,
                    fontWeight = if (initialOnly) FontWeight.Bold else FontWeight.Normal,
                    maxLines = if (initialOnly) 1 else 4,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp),
                )
            }

            progress?.takeIf { it > 0.01f && it < 0.995f }?.let { fraction ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(menuCursorEdge()),
                    )
                }
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
    const val ArtRatio = 2f / 3f

    val Width: Dp = 104.dp
}
