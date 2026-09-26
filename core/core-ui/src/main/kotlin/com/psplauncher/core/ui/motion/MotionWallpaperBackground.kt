package com.psplauncher.core.ui.motion

import android.graphics.Matrix
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import coil3.gif.MovieDrawable
import coil3.gif.repeatCount
import coil3.request.ImageRequest
import timber.log.Timber

@Composable
fun MotionWallpaperBackground(
    posterPath: String,
    motionPath: String,
    decision: MotionWallpaperPolicy.Decision,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(posterPath)
                .repeatCount(1)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        when (formatOf(motionPath)) {
            MotionFormat.ANIMATED_IMAGE -> AnimatedImageSurface(motionPath)
            MotionFormat.VIDEO -> MotionVideoSurface(motionPath, decision)
        }
    }
}

@Composable
private fun MotionVideoSurface(motionPath: String, decision: MotionWallpaperPolicy.Decision) {
    val context = LocalContext.current
    var firstFrameRendered by remember(motionPath) { mutableStateOf(false) }
    var videoSize by remember(motionPath) { mutableStateOf<VideoSize?>(null) }

    val player = remember(motionPath) {
        ExoPlayer.Builder(context).build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            volume = 0f
            setMediaItem(MediaItem.fromUri(motionPath))

            repeatMode = Player.REPEAT_MODE_ALL

            setPlaybackSpeed(if (decision == MotionWallpaperPolicy.Decision.PLAY_REDUCED) 0.5f else 1f)
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
                Timber.tag(TAG).d("motion first frame rendered (%s)", motionPath)
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (firstFrameRendered) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "motionWallpaperFade",
    )

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).also { view ->
                player.setVideoTextureView(view)
                view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    applyCenterCrop(v as TextureView, videoSize)
                }
            }
        },
        update = { view -> applyCenterCrop(view, videoSize) },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    )
}

@Composable
private fun AnimatedImageSurface(motionPath: String) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(motionPath)
            .repeatCount(MovieDrawable.REPEAT_INFINITE)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

private const val TAG = "MotionWallpaper"

private fun applyCenterCrop(view: TextureView, size: VideoSize?) {
    val vw = size?.width?.toFloat() ?: return
    val vh = size.height.toFloat()
    if (vw <= 0f || vh <= 0f || view.width == 0 || view.height == 0) return
    val viewW = view.width.toFloat()
    val viewH = view.height.toFloat()
    val scale = maxOf(viewW / vw, viewH / vh)
    val matrix = Matrix().apply {
        setScale((vw * scale) / viewW, (vh * scale) / viewH, viewW / 2f, viewH / 2f)
    }
    view.setTransform(matrix)
}

@Composable
fun rememberAppVisible(): Boolean {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var appVisible by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> appVisible = true
                Lifecycle.Event.ON_STOP -> appVisible = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return appVisible
}
