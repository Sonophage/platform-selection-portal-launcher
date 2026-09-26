package com.psplauncher.themekit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MotionLimitsTest {
    private val ok = MotionLimits.Probe(
        mime = "video/mp4",
        width = 1920,
        height = 1080,
        durationMs = 60_000L,
        bytes = 60L * 1024 * 1024,
    )

    @Test
    fun `a 1080p 60s 60MB mp4 exactly at every cap is accepted`() {
        assertNull(MotionLimits.validate(ok))
    }

    @Test
    fun `each supported mime is accepted`() {
        listOf("video/mp4", "video/webm", "image/gif", "image/webp").forEach { mime ->
            assertNull("expected $mime accepted", MotionLimits.validate(ok.copy(mime = mime)))
        }
    }

    @Test
    fun `unsupported mime names the accepted formats`() {
        val error = MotionLimits.validate(ok.copy(mime = "video/x-msvideo"))
        assertEquals("Unsupported format — use MP4, WebM, or GIF", error)

        assertEquals(
            "Unsupported format — use MP4, WebM, or GIF",
            MotionLimits.validate(ok.copy(mime = "image/png")),
        )
    }

    @Test
    fun `missing mime is rejected`() {
        val error = MotionLimits.validate(ok.copy(mime = null))
        assertEquals("Unsupported format — use MP4, WebM, or GIF", error)
    }

    @Test
    fun `resolution over 1080p is rejected`() {
        assertEquals("Video is too large — 1080p or smaller", MotionLimits.validate(ok.copy(width = 1921, height = 1080)))
        assertEquals("Video is too large — 1080p or smaller", MotionLimits.validate(ok.copy(width = 1920, height = 1081)))

        assertEquals("Video is too large — 1080p or smaller", MotionLimits.validate(ok.copy(width = 3840, height = 2160)))
    }

    @Test
    fun `portrait video checks its long edge against the cap`() {
        assertNull(MotionLimits.validate(ok.copy(width = 1080, height = 1920)))
        assertEquals(
            "Video is too large — 1080p or smaller",
            MotionLimits.validate(ok.copy(width = 1081, height = 1920)),
        )
    }

    @Test
    fun `duration boundary - 60_000ms accepted, 60_001 rejected`() {
        assertNull(MotionLimits.validate(ok.copy(durationMs = 60_000L)))
        assertEquals(
            "Clip is too long — 60 seconds or less",
            MotionLimits.validate(ok.copy(durationMs = 60_001L)),
        )
    }

    @Test
    fun `file size boundary - exactly 60MB accepted, one byte over rejected`() {
        assertNull(MotionLimits.validate(ok.copy(bytes = 60L * 1024 * 1024)))
        assertEquals(
            "File is too large — under 60 MB",
            MotionLimits.validate(ok.copy(bytes = 60L * 1024 * 1024 + 1)),
        )
    }

    @Test
    fun `oversized GIF is rejected with the video-too-large message`() {
        val error = MotionLimits.validate(ok.copy(mime = "image/gif", width = 2560, height = 1440))
        assertEquals("Video is too large — 1080p or smaller", error)
    }

    @Test
    fun `degenerate zero-dimension probe is rejected`() {
        assertEquals(
            "Video is too large — 1080p or smaller",
            MotionLimits.validate(ok.copy(width = 0, height = 1080)),
        )
    }

    @Test
    fun `every known extension maps into the sets the codec and validator actually use`() {
        for (extension in MotionLimits.knownExtensions) {
            assertEquals(
                "mimeForExtension($extension) must land in SUPPORTED_MIME",
                true,
                MotionLimits.mimeForExtension(extension) in MotionLimits.SUPPORTED_MIME,
            )
            val bundleExtension = MotionLimits.bundleExtensionFor(extension)
            if (bundleExtension != null) {
                assertEquals(
                    "bundleExtensionFor($extension) must land in MOTION_EXTENSIONS",
                    true,
                    bundleExtension in PfpThemeCodec.MOTION_EXTENSIONS,
                )
            }
        }

        assertNull(MotionLimits.bundleExtensionFor("webp"))
    }

    @Test
    fun `aliases collapse onto the entry the codec actually stores`() {
        assertEquals("mp4", MotionLimits.bundleExtensionFor("mp4"))
        assertEquals("mp4", MotionLimits.bundleExtensionFor("m4v"))
        assertEquals("webm", MotionLimits.bundleExtensionFor("webm"))
        assertEquals("gif", MotionLimits.bundleExtensionFor("gif"))

        assertNull(MotionLimits.bundleExtensionFor("avi"))
        assertNull(MotionLimits.bundleExtensionFor("mp3"))
    }
}
