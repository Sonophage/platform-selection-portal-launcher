package com.psplauncher.studio.io

import com.psplauncher.themekit.MotionLimits
import java.awt.image.BufferedImage
import java.io.File
import org.jcodec.api.FrameGrab
import org.jcodec.common.io.NIOUtils
import org.jcodec.scale.AWTUtil

object VideoCodecs {
    val SUPPORTED_EXTENSIONS = setOf("mp4", "m4v")

    const val MAX_SOURCE_BYTES = MotionLimits.MAX_BYTES

    const val MSG_UNSUPPORTED_SOURCE = "Unsupported video — use an MP4 (H.264)"

    sealed interface Outcome {
        data class Accepted(
            val poster: BufferedImage,
            val probe: MotionLimits.Probe,
            val bundleExtension: String,
        ) : Outcome

        data class Rejected(val message: String) : Outcome
    }

    fun probe(file: File): MotionLimits.Probe? = runCatching {
        NIOUtils.readableChannel(file).use { channel ->
            val meta = FrameGrab.createFrameGrab(channel).videoTrack.meta
            val size = meta.videoCodecMeta.size
            MotionLimits.Probe(
                mime = MotionLimits.mimeForExtension(file.extension),
                width = size.width,
                height = size.height,
                durationMs = (meta.totalDuration * 1000.0).toLong(),
                bytes = file.length(),
            )
        }
    }.getOrNull()

    fun firstFrame(file: File): BufferedImage? = runCatching {
        NIOUtils.readableChannel(file).use { channel ->
            FrameGrab.createFrameGrab(channel).nativeFrame?.let(AWTUtil::toBufferedImage)
        }
    }.getOrNull()

    fun accept(file: File): Outcome {
        val extension = file.extension.lowercase()
        if (extension !in SUPPORTED_EXTENSIONS) return Outcome.Rejected(MSG_UNSUPPORTED_SOURCE)
        if (!file.isFile) return Outcome.Rejected(MotionLimits.MSG_UNDECODABLE)
        if (file.length() > MAX_SOURCE_BYTES) return Outcome.Rejected(MotionLimits.MSG_TOO_LARGE_BYTES)

        val probe = probe(file) ?: return Outcome.Rejected(MotionLimits.MSG_UNDECODABLE)
        MotionLimits.validate(probe)?.let { return Outcome.Rejected(it) }

        val poster = firstFrame(file) ?: return Outcome.Rejected(MotionLimits.MSG_UNDECODABLE)

        val bundleExtension = MotionLimits.bundleExtensionFor(extension)
            ?: return Outcome.Rejected(MSG_UNSUPPORTED_SOURCE)

        return Outcome.Accepted(poster = poster, probe = probe, bundleExtension = bundleExtension)
    }
}
