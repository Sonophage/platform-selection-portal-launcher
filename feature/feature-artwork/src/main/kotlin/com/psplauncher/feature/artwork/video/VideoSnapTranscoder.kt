package com.psplauncher.feature.artwork.video

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton

@Suppress("UnsafeOptInUsageError")
class VideoSnapTranscoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun transcode(input: File, output: File): Boolean =
        suspendCancellableCoroutine { cont ->
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(input))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setEndPositionMs(MAX_SNAP_MS)
                            .build()
                    )
                    .build()
                val edited = EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(true)
                    .setEffects(
                        Effects(emptyList(), listOf(Presentation.createForHeight(SNAP_HEIGHT_PX)))
                    )
                    .build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            Timber.d("Video snap transcoded: ${output.length() / 1024} KB")
                            if (cont.isActive) cont.resume(true) { _, _, _ -> }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            Timber.w(exportException, "Video snap transcode failed")
                            output.delete()
                            if (cont.isActive) cont.resume(false) { _, _, _ -> }
                        }
                    })
                    .build()
                transformer.start(edited, output.absolutePath)
                cont.invokeOnCancellation { mainHandler.post { transformer.cancel() } }
            }
        }

    suspend fun transcodeCropped(
        input: File, output: File, l: Float, t: Float, r: Float, b: Float,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(input))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setEndPositionMs(MAX_SNAP_MS).build()
                )
                .build()
            val crop = androidx.media3.effect.Crop(2f * l - 1f, 2f * r - 1f, 1f - 2f * b, 1f - 2f * t)
            val edited = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(true)
                .setEffects(Effects(emptyList(), listOf(crop, Presentation.createForHeight(SNAP_HEIGHT_PX))))
                .build()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Timber.d("Cropped icon snap transcoded: ${output.length() / 1024} KB")
                        if (cont.isActive) cont.resume(true) { _, _, _ -> }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        Timber.w(exportException, "Cropped icon snap transcode failed")
                        output.delete()
                        if (cont.isActive) cont.resume(false) { _, _, _ -> }
                    }
                })
                .build()
            transformer.start(edited, output.absolutePath)
            cont.invokeOnCancellation { mainHandler.post { transformer.cancel() } }
        }
    }

    companion object {
        const val MAX_SNAP_MS = 60_000L
        const val SNAP_HEIGHT_PX = 160
    }
}
