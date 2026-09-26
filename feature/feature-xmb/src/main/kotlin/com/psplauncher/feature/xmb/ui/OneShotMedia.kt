package com.psplauncher.feature.xmb.ui

import android.graphics.Matrix
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import timber.log.Timber

@Composable
internal fun OneShotVideoLayer(
    path: String,
    clipEndMs: Long,
    onEnded: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val context = LocalContext.current
    val currentEnded by rememberUpdatedState(onEnded)
    val currentFailed by rememberUpdatedState(onFailed)
    var videoSize by remember(path) { mutableStateOf<VideoSize?>(null) }

    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            if (muted) volume = 0f
            setMediaItem(
                MediaItem.Builder()
                    .setUri(path)
                    .setClippingConfiguration(

                        MediaItem.ClippingConfiguration.Builder()
                            .setEndPositionMs(clipEndMs)
                            .build()
                    )
                    .build()
            )
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) { videoSize = size }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) currentEnded()
            }
            override fun onPlayerError(error: PlaybackException) {
                Timber.w(error, "One-shot video failed: $path")
                currentFailed()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).also { view ->
                player.setVideoTextureView(view)
                view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    applyFullscreenCenterCrop(v as TextureView, videoSize)
                }
            }
        },
        update = { view -> applyFullscreenCenterCrop(view, videoSize) },
        modifier = modifier,
    )
}

@Composable
internal fun OneShotAudioLayer(
    path: String,
    clipEndMs: Long,
    onFinished: () -> Unit = {},
) {
    val context = LocalContext.current
    val currentFinished by rememberUpdatedState(onFinished)

    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.Builder()
                    .setUri(path)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setEndPositionMs(clipEndMs)
                            .build()
                    )
                    .build()
            )
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) currentFinished()
            }
            override fun onPlayerError(error: PlaybackException) {
                Timber.w(error, "One-shot audio failed: $path")
                currentFinished()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
}

private fun applyFullscreenCenterCrop(view: TextureView, size: VideoSize?) {
    val vw = size?.width?.toFloat() ?: return
    val vh = size.height.toFloat()
    if (vw <= 0f || vh <= 0f || view.width == 0 || view.height == 0) return
    val viewW = view.width.toFloat()
    val viewH = view.height.toFloat()
    val scale = maxOf(viewW / vw, viewH / vh)
    view.setTransform(
        Matrix().apply {
            setScale((vw * scale) / viewW, (vh * scale) / viewH, viewW / 2f, viewH / 2f)
        }
    )
}
