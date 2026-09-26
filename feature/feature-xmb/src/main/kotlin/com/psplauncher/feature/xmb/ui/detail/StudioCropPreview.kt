package com.psplauncher.feature.xmb.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.feature.artwork.store.ArtworkKind
import kotlin.math.roundToInt

enum class CropPreviewChrome { PSP_TILE, FRAMELESS }

fun cropPreviewChromeFor(kind: ArtworkKind): CropPreviewChrome? = when (kind) {
    ArtworkKind.ICON, ArtworkKind.BOX_ART, ArtworkKind.ICON1 -> CropPreviewChrome.PSP_TILE

    ArtworkKind.BOX_3D, ArtworkKind.PHYSICAL_MEDIA, ArtworkKind.VIDEO -> CropPreviewChrome.FRAMELESS

    ArtworkKind.HERO, ArtworkKind.BACKGROUND, ArtworkKind.LOGO,
    ArtworkKind.SCREENSHOT, ArtworkKind.TITLESCREEN, ArtworkKind.MANUAL -> null
}

fun cropPreviewCaptionFor(kind: ArtworkKind): String? = when (kind) {
    ArtworkKind.ICON -> "XMB tile"
    ArtworkKind.ICON1 -> "XMB icon animation"
    ArtworkKind.BOX_ART -> "Box Art tile"
    ArtworkKind.BOX_3D -> "3D Box tile"
    ArtworkKind.PHYSICAL_MEDIA -> "Phys. Media tile"
    ArtworkKind.VIDEO -> "Media strip"
    else -> null
}

private val PreviewShape = RoundedCornerShape(4.dp)
private val PreviewBacking = Color(0xFF0A0A0F)
private val PreviewBorder = Color(0x55FFFFFF)
private val PreviewShine = Color(0x18FFFFFF)

private val PreviewWidth = 132.dp

@Composable
private fun CropPreviewFrame(
    aspect: Float,
    chrome: CropPreviewChrome,
    caption: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier = modifier.width(PreviewWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect.coerceAtLeast(0.0001f))
                .then(
                    if (chrome == CropPreviewChrome.PSP_TILE) {
                        Modifier
                            .clip(PreviewShape)
                            .background(PreviewBacking)
                            .border(1.dp, PreviewBorder, PreviewShape)
                    } else {
                        Modifier
                    }
                ),
        ) {
            content()
            if (chrome == CropPreviewChrome.PSP_TILE) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.3f)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(PreviewShine, Color.Transparent)))
                )
            }
        }
        Text(
            caption,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun StudioCropPreviewTile(
    image: ImageBitmap,
    aspect: Float,
    cropL: Float, cropT: Float, cropR: Float, cropB: Float,
    chrome: CropPreviewChrome,
    caption: String,
    modifier: Modifier = Modifier,
) {
    CropPreviewFrame(aspect, chrome, caption, modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val srcX = (cropL * image.width).roundToInt().coerceIn(0, image.width)
            val srcY = (cropT * image.height).roundToInt().coerceIn(0, image.height)
            val srcW = ((cropR - cropL) * image.width).roundToInt()
                .coerceIn(1, image.width - srcX)
            val srcH = ((cropB - cropT) * image.height).roundToInt()
                .coerceIn(1, image.height - srcY)
            drawImage(
                image = image,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(srcW, srcH),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            )
        }
    }
}

@Composable
fun StudioCropPreviewVideoTile(
    player: androidx.media3.exoplayer.ExoPlayer,
    aspect: Float,
    cropL: Float, cropT: Float, cropR: Float, cropB: Float,
    chrome: CropPreviewChrome,
    caption: String,
    modifier: Modifier = Modifier,
) {
    CropPreviewFrame(aspect, chrome, caption, modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
            val density = LocalDensity.current
            val tileW = with(density) { maxWidth.toPx() }
            val tileH = with(density) { maxHeight.toPx() }
            val dispW = tileW / (cropR - cropL).coerceAtLeast(0.0001f)
            val dispH = tileH / (cropB - cropT).coerceAtLeast(0.0001f)
            CropVideoSurface(
                player = player,
                modifier = Modifier
                    .offset {
                        IntOffset((-cropL * dispW).roundToInt(), (-cropT * dispH).roundToInt())
                    }
                    .size(with(density) { dispW.toDp() }, with(density) { dispH.toDp() }),
            )
        }
    }
}
