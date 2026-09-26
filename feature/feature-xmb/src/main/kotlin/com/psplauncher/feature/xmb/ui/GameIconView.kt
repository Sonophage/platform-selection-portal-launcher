package com.psplauncher.feature.xmb.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.aspectRatio
import coil3.compose.AsyncImage
import com.psplauncher.core.ui.image.ArtworkRevisions
import com.psplauncher.core.ui.image.rememberArtworkModel
import com.psplauncher.core.domain.model.IconDisplayMode
import com.psplauncher.core.ui.icons.GameIconStyle
import com.psplauncher.feature.artwork.store.ArtworkDimensions
import com.psplauncher.feature.xmb.R
import com.psplauncher.feature.xmb.viewmodel.XMBItem
import com.psplauncher.feature.xmb.viewmodel.resolveIconDisplay

private val ICON_WIDTH  = 62.dp
private val ICON_HEIGHT = 86.dp

private val PspShape    = RoundedCornerShape(4.dp)
private val SquircleShape = com.psplauncher.core.ui.icons.AppIconContainerShape

private val CartridgeBodyColor      = Color(0xFF1C1C22)
private val CartridgeConnectorColor = Color(0xFF111115)
private val CartridgePinColor       = Color(0xFF2E2E38)
private val IconBorder              = Color(0x55FFFFFF)
private val ShineColor              = Color(0x18FFFFFF)

@Composable
fun GameIcon(
    item: XMBItem,
    iconStyle: GameIconStyle,
    modifier: Modifier = Modifier,
) {
    when {
        item.isAndroidApp && !item.isRealGame && item.iconUri != null -> PspIcon0Icon(
            artworkUri  = item.iconUri,
            accentColor = item.accentColor?.let { Color(it) },
            title       = item.title,
            modifier    = modifier,
        )
        item.isAndroidApp && !item.isRealGame -> AndroidAppIcon(
            packageName = item.packageName,
            title       = item.title,
            modifier    = modifier,
        )

        iconStyle == GameIconStyle.CARTRIDGE -> NaturalArtSlot(modifier) { artModifier ->
            PhysicalMediaIcon(
                platformId  = item.platformId,
                accentColor = item.accentColor?.let { Color(it) },
                title       = item.title,
                modifier    = artModifier,
            )
        }

        else -> {
            val resolved = resolveIconDisplay(
                item,
                LocalIconDisplayMode.current,
                LocalIconDisplayModeByPlatform.current,
            )
            when {
                resolved.mode == IconDisplayMode.PHYSICAL_MEDIA && resolved.uri == null ->
                    NaturalArtSlot(modifier) { artModifier ->
                        PhysicalMediaIcon(
                            platformId  = item.platformId,
                            accentColor = item.accentColor?.let { Color(it) },
                            title       = item.title,
                            modifier    = artModifier,
                        )
                    }

                resolved.uri == null &&
                    (resolved.mode == IconDisplayMode.BOX_ART || resolved.mode == IconDisplayMode.BOX_3D) ->
                    NaturalArtSlot(modifier) { artModifier ->
                        BoxArtPlaceholderIcon(
                            platformId  = item.platformId,
                            accentColor = item.accentColor?.let { Color(it) },
                            title       = item.title,
                            modifier    = artModifier,
                        )
                    }

                resolved.naturalAspect -> NaturalArtSlot(modifier) { artModifier ->
                    NaturalAspectArtIcon(
                        artworkUri  = resolved.uri!!,

                        framed      = resolved.uri == item.boxArtUri,
                        accentColor = item.accentColor?.let { Color(it) },
                        title       = item.title,
                        modifier    = artModifier,
                    )
                }

                else -> {
                    val panelShowingVideo = LocalPanelShowingVideo.current
                    val video = LocalFocusedGameVideo.current?.takeIf {
                        it.gameId == item.gameId &&
                            snapSiteFor(it.placement, resolved.mode, panelShowingVideo) == SnapSite.TILE
                    }
                    Box(modifier = modifier) {
                        PspIcon0Icon(
                            artworkUri  = resolved.uri,
                            accentColor = item.accentColor?.let { Color(it) },
                            title       = item.title,
                            modifier    = Modifier.fillMaxSize(),
                        )
                        if (video != null) {
                            Icon1VideoOverlay(
                                videoUri = video.uri,
                                modifier = Modifier.fillMaxSize().clip(PspShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val NATURAL_ART_HEIGHT = 84.dp

@Composable
private fun NaturalArtSlot(
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        content(Modifier.fillMaxWidth().requiredHeight(NATURAL_ART_HEIGHT))
    }
}

fun boxArtAspectFor(platformId: String?): Float = ArtworkDimensions.boxArt(platformId).aspectRatio

@Composable
private fun BoxArtPlaceholderIcon(
    platformId: String?,
    accentColor: Color?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val accent = accentColor ?: Color(0xFF4A9EFF)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .aspectRatio(boxArtAspectFor(platformId))
                .clip(PspShape)
                .background(
                    Brush.verticalGradient(listOf(accent.copy(alpha = 0.6f), Color(0xFF0A0A0F)))
                )
                .border(1.dp, IconBorder, PspShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun NaturalAspectArtIcon(
    artworkUri: String,
    framed: Boolean,
    accentColor: Color?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val painter = coil3.compose.rememberAsyncImagePainter(
        model = coil3.request.ImageRequest.Builder(LocalContext.current)
            .data(artworkUri)
            .size(coil3.size.Size.ORIGINAL)

            .memoryCacheKey(ArtworkRevisions.cacheKey(artworkUri))
            .build()
    )

    val state by painter.state.collectAsState()
    val ratio = (state as? coil3.compose.AsyncImagePainter.State.Success)
        ?.painter?.intrinsicSize
        ?.takeIf { it.width > 0f && it.height > 0f }
        ?.let { it.width / it.height }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            ratio != null -> Box(
                modifier = Modifier
                    .aspectRatio(ratio)
                    .then(
                        if (framed) Modifier
                            .clip(PspShape)
                            .background(Color(0xFF0A0A0F))
                            .border(1.dp, IconBorder, PspShape)
                        else Modifier
                    ),
            ) {
                Image(
                    painter            = painter,
                    contentDescription = null,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize(),
                )
            }
            state is coil3.compose.AsyncImagePainter.State.Error -> PspIcon0Icon(
                artworkUri  = null,
                accentColor = accentColor,
                title       = title,
                modifier    = Modifier.fillMaxSize(),
            )
            else -> Unit
        }
    }
}

@Composable
fun PspIcon0Icon(
    artworkUri: String?,
    accentColor: Color?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val accent = accentColor ?: Color(0xFF4A9EFF)

    Box(
        modifier = modifier
            .clip(PspShape)
            .background(Color(0xFF0A0A0F))
            .border(1.dp, IconBorder, PspShape),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model              = rememberArtworkModel(artworkUri),
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(accent.copy(alpha = 0.6f), Color(0xFF0A0A0F)))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    fontSize   = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White.copy(alpha = 0.85f),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.3f)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(ShineColor, Color.Transparent)))
        )
    }
}

private const val ASSET_BASE = "file:///android_asset/systems/physical-media"

@Composable
fun PhysicalMediaIcon(
    platformId: String?,
    accentColor: Color?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val assetName   = physicalMediaAssetName(platformId)
    val fallbackRes = physicalMediaIconRes(platformId) ?: R.drawable.media_cartridge
    var assetFailed by remember(assetName) { mutableStateOf(false) }

    Box(
        modifier         = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (assetName != null && !assetFailed) {
            AsyncImage(
                model              = "$ASSET_BASE/$assetName.png",
                contentDescription = null,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize(),
                onError            = { assetFailed = true },
            )
        } else {
            Image(
                painter            = painterResource(id = fallbackRes),
                contentDescription = null,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun AndroidAppIcon(
    packageName: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val imageBitmap = remember(packageName) {
        if (packageName == null) return@remember null
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bmp = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(96),
                drawable.intrinsicHeight.coerceAtLeast(96),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        }.getOrNull()
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(SquircleShape)
            .background(Color(0xFF1A1A20)),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                painter            = BitmapPainter(imageBitmap),
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text       = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White.copy(alpha = 0.7f),
            )
        }
    }
    }
}
