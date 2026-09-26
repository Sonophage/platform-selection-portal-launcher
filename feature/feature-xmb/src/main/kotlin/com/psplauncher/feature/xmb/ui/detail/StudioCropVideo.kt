package com.psplauncher.feature.xmb.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs

@Composable
fun rememberCropClipPlayer(path: String): androidx.media3.exoplayer.ExoPlayer? {
    val context = LocalContext.current
    var failed by remember(path) { mutableStateOf(false) }
    val player = remember(path) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, true)
                .build()
            volume = 0f
            setMediaItem(
                androidx.media3.common.MediaItem.fromUri(
                    android.net.Uri.fromFile(java.io.File(path))
                )
            )
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                timber.log.Timber.w(error, "Crop editor clip playback failed")
                failed = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    return player.takeIf { !failed }
}

@Composable
fun SyncClipTo(
    leader: androidx.media3.exoplayer.ExoPlayer?,
    follower: androidx.media3.exoplayer.ExoPlayer?,
) {
    if (leader == null || follower == null) return
    LaunchedEffect(leader, follower) {
        while (true) {
            kotlinx.coroutines.delay(SYNC_TICK_MS)
            val drift = leader.currentPosition - follower.currentPosition
            if (abs(drift) > DRIFT_TOLERANCE_MS) follower.seekTo(leader.currentPosition)
        }
    }
}

private const val SYNC_TICK_MS = 500L
private const val DRIFT_TOLERANCE_MS = 150L

@Composable
fun CropVideoSurface(
    player: androidx.media3.exoplayer.ExoPlayer,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx -> android.view.TextureView(ctx).also(player::setVideoTextureView) },
        update = { view -> player.setVideoTextureView(view) },
        modifier = modifier,
    )
}
