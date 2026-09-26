package com.psplauncher.core.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.image.rememberArtworkModel

val DetailMediaTileWidth: Dp = 214.dp
val DetailMediaTileHeight: Dp = 120.dp

@Composable
fun PfpDetailMediaTile(
    uri: String?,
    isVideo: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    posterFallbackUri: String? = null,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    val poster = if (isVideo) (uri ?: posterFallbackUri) else uri
    Box(
        modifier = modifier
            .width(DetailMediaTileWidth)
            .height(DetailMediaTileHeight)
            .clip(shape)
            .background(DetailRowFill)
            .detailFocusRing(
                focused = focused,
                edge = DetailFocusEdge,
                fill = DetailFocusEdge.copy(alpha = 0.12f),
                shape = shape,
                strong = true,
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (poster != null) {
            AsyncImage(
                model = rememberArtworkModel(poster),
                contentDescription = if (isVideo) null else contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isVideo) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
            PfpPlayMark(color = Color.White.copy(alpha = if (focused) 1f else 0.9f), size = 40.dp)

            Text(
                text = "VIDEO",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
