package com.psplauncher.feature.xmb.ui.detail

import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.ui.components.ControllerPromptGlyphs
import com.psplauncher.core.ui.detail.DetailMediaTileHeight
import com.psplauncher.core.ui.detail.DetailMediaTileWidth
import com.psplauncher.core.ui.detail.PfpDetailMediaTile
import com.psplauncher.core.ui.detail.detailPalette
import androidx.compose.ui.draw.clip
import com.psplauncher.core.ui.image.rememberArtworkModel
import com.psplauncher.feature.xmb.ui.Icon1VideoOverlay
import com.psplauncher.core.ui.theme.LocalPfpTextColors

private fun DetailPanelPage.icon(): ImageVector = when (this) {
    DetailPanelPage.LOGO -> Icons.Filled.PictureInPictureAlt
    DetailPanelPage.BOX_ART -> Icons.Filled.Inventory2
    DetailPanelPage.VIDEO -> Icons.Filled.PlayCircleOutline
    DetailPanelPage.GALLERY -> Icons.Filled.Image
    DetailPanelPage.INFO -> Icons.Filled.Info
}

private val PanelTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.75f),
    offset = Offset(0f, 2f),
    blurRadius = 5f,
)

private val StripTabShape = RoundedCornerShape(percent = 50)
private val StripTabGap: Dp = 6.dp
private val StripShoulderSize: Dp = 13.dp
private val PanelCardShape = RoundedCornerShape(14.dp)

@Composable
fun DetailPanelStrip(
    pages: List<DetailPanelPage>,
    current: DetailPanelPage,
    modifier: Modifier = Modifier,
    onPageTapped: ((DetailPanelPage) -> Unit)? = null,
) {
    if (pages.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(StripTabGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControllerPromptGlyphs(
            icons = listOf(ControllerIcon.BUMPER_LEFT),
            label = "",
            glyphSize = StripShoulderSize,
        )
        pages.forEach { page ->
            val selected = page == current
            Box(
                modifier = Modifier
                    .clip(StripTabShape)

                    .background(
                        if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                        StripTabShape,
                    )
                    .then(
                        if (onPageTapped != null) Modifier.clickable { onPageTapped(page) }
                        else Modifier
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = page.label.uppercase(),
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),

                    fontSize = 8.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
        ControllerPromptGlyphs(
            icons = listOf(ControllerIcon.BUMPER_RIGHT),
            label = "",
            glyphSize = StripShoulderSize,
        )
    }
}

@Composable
fun GameDetailPanel(
    content: DetailPanelContent,
    page: DetailPanelPage,
    modifier: Modifier = Modifier,
    titleFallback: Boolean = true,

    focusedMediaId: String? = null,
    onMediaTapped: ((DetailMedia) -> Unit)? = null,

    halfHeightLogo: Boolean = false,
) {
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (resolvePanelPage(page, content.pages)) {
                DetailPanelPage.LOGO -> LogoPage(content, titleFallback, halfHeightLogo)
                DetailPanelPage.BOX_ART -> BoxArtPage(content)
                DetailPanelPage.VIDEO -> VideoPage(content)
                DetailPanelPage.GALLERY -> GalleryPage(content, focusedMediaId, onMediaTapped)
                DetailPanelPage.INFO -> InfoPage(content)
            }
        }

        content.playTime?.let { played ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "TIME PLAYED: ${played.uppercase()}",

                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(shadow = PanelTextShadow),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun LogoPage(content: DetailPanelContent, titleFallback: Boolean, halfHeight: Boolean = false) {
    val logo = content.logoUri
    if (logo != null) {
        AsyncImage(
            model = rememberArtworkModel(logo),
            contentDescription = content.title,

            contentScale = ContentScale.Fit,

            modifier = Modifier
                .fillMaxWidth()
                .then(if (halfHeight) Modifier.fillMaxHeight(0.5f) else Modifier.fillMaxHeight())
                .padding(8.dp),
        )
    } else if (titleFallback) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = content.title,
                color = LocalPfpTextColors.current.primary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(text = content.platformName, color = LocalPfpTextColors.current.secondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BoxArtPage(content: DetailPanelContent) {
    AsyncImage(
        model = rememberArtworkModel(content.boxArtUri),
        contentDescription = content.title,

        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().padding(8.dp),
    )
}

@Composable
private fun VideoPage(content: DetailPanelContent) {
    val uri = content.videoUri ?: return
    Icon1VideoOverlay(
        videoUri = uri,

        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
    )
}

@Composable
private fun GalleryPage(
    content: DetailPanelContent,
    focusedMediaId: String?,
    onMediaTapped: ((DetailMedia) -> Unit)?,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(content.media, key = { mediaStableId(it) }) { item ->
            PfpDetailMediaTile(
                uri = item.uri,
                isVideo = item.isVideo,

                focused = mediaStableId(item) == focusedMediaId,
                onClick = { onMediaTapped?.invoke(item) },
                posterFallbackUri = content.posterFallbackUri,
                contentDescription = if (item.isVideo) "Video" else "Screenshot",
                modifier = Modifier.width(DetailMediaTileWidth).height(DetailMediaTileHeight),
            )
        }
    }
}

@Composable
private fun InfoPage(content: DetailPanelContent) {
    val palette = detailPalette()
    Column(
        modifier = Modifier

            .fillMaxSize()
            .background(palette.rowFill, PanelCardShape)
            .border(1.dp, palette.rowEdge, PanelCardShape)
            .padding(18.dp),
    ) {
        Text(
            text = content.metaLine ?: content.platformName,
            color = palette.textMuted,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = content.description?.takeIf { it.isNotBlank() } ?: "No description available.",
            color = palette.textPrimary,
            fontSize = 14.sp,
            lineHeight = 21.sp,

            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(12.dp))

        Text(
            text = content.title,
            color = palette.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content.fileName?.let { fileName ->
            Text(
                text = fileName,
                color = palette.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
