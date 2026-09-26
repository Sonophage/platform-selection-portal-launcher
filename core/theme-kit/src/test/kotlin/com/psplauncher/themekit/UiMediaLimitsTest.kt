package com.psplauncher.themekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiMediaLimitsTest {
    private fun probe(
        mime: String? = "audio/mpeg",
        durationMs: Long? = 100L,
        bytes: Long = 1_000L,
    ) = UiMediaLimits.Probe(mime = mime, durationMs = durationMs, bytes = bytes)

    private fun videoProbe(mime: String = "video/mp4", durationMs: Long? = 100L, bytes: Long = 1_000L) =
        probe(mime = mime, durationMs = durationMs, bytes = bytes)

    @Test fun `sound slots accept exactly at their hard max and reject one ms over`() {
        for (spec in listOf(
            UiMediaLimits.NAVIGATION,
            UiMediaLimits.CONFIRM,
            UiMediaLimits.BACK,
            UiMediaLimits.ERROR,
            UiMediaLimits.NOTIFICATION,
        )) {
            assertNull(UiMediaLimits.validate(spec, probe(durationMs = spec.hardMaxMs)), spec.toString())
            assertNotNull(
                UiMediaLimits.validate(spec, probe(durationMs = spec.hardMaxMs + 1)),
                "duration ${spec.hardMaxMs + 1} must be rejected for $spec",
            )
        }
    }

    @Test fun `launch sound accepts exactly at 3 s and rejects over`() {
        assertNull(UiMediaLimits.validate(UiMediaLimits.LAUNCH, probe(durationMs = 3_000L)))
        assertNotNull(UiMediaLimits.validate(UiMediaLimits.LAUNCH, probe(durationMs = 3_001L)))
    }

    @Test fun `a gameboot clip accepts exactly at 10 s and rejects over`() {
        assertNull(UiMediaLimits.validate(UiMediaLimits.GAMEBOOT_CLIP, videoProbe(durationMs = 8_600L)))
        assertNull(UiMediaLimits.validate(UiMediaLimits.GAMEBOOT_CLIP, videoProbe(durationMs = 10_000L)))
        assertNotNull(UiMediaLimits.validate(UiMediaLimits.GAMEBOOT_CLIP, videoProbe(durationMs = 10_001L)))
    }

    @Test fun `boot media accepts exactly at 10 s and rejects over`() {
        assertNull(UiMediaLimits.validate(UiMediaLimits.BOOT, probe(durationMs = 10_000L)))
        assertNotNull(UiMediaLimits.validate(UiMediaLimits.BOOT, probe(durationMs = 10_001L)))
        assertNull(UiMediaLimits.validate(UiMediaLimits.BOOT_CLIP, videoProbe(durationMs = 10_000L)))
        assertNotNull(UiMediaLimits.validate(UiMediaLimits.BOOT_CLIP, videoProbe(durationMs = 10_001L)))
    }

    @Test fun `recommended range is advisory only - outside it still validates`() {
        assertNull(UiMediaLimits.validate(UiMediaLimits.NAVIGATION, probe(durationMs = 400L)))
    }

    @Test fun `audio staging ceiling boundary - not a user-facing cap`() {
        assertNull(UiMediaLimits.validate(UiMediaLimits.NAVIGATION, probe(bytes = 8L * 1024 * 1024)))

        assertNull(UiMediaLimits.validate(UiMediaLimits.NAVIGATION, probe(bytes = UiMediaLimits.AUDIO_STAGE_MAX_BYTES)))
        assertNotNull(
            UiMediaLimits.validate(UiMediaLimits.NAVIGATION, probe(bytes = UiMediaLimits.AUDIO_STAGE_MAX_BYTES + 1)),
            "a pick bigger than the staging ceiling must be refused",
        )
    }

    @Test fun `video byte cap boundary`() {
        assertNull(UiMediaLimits.validate(UiMediaLimits.BOOT_CLIP, videoProbe(bytes = UiMediaLimits.VIDEO_MAX_BYTES)))
        assertNotNull(UiMediaLimits.validate(UiMediaLimits.BOOT_CLIP, videoProbe(bytes = UiMediaLimits.VIDEO_MAX_BYTES + 1)))
    }

    @Test fun `unknown and null mime are rejected`() {
        assertNotNull(UiMediaLimits.validate(UiMediaLimits.NAVIGATION, probe(mime = "video/mp4")))
        assertNotNull(UiMediaLimits.validate(UiMediaLimits.NAVIGATION, probe(mime = null)))
    }

    @Test fun `video slots accept the video mime set`() {
        assertNull(UiMediaLimits.validate(UiMediaLimits.BOOT_CLIP, videoProbe(mime = "video/mp4")))
        assertNull(UiMediaLimits.validate(UiMediaLimits.GAMEBOOT_CLIP, videoProbe(mime = "video/webm")))

        assertNotNull(UiMediaLimits.validate(UiMediaLimits.BOOT_CLIP, videoProbe(mime = "image/gif")))
    }

    @Test fun `audio slots accept every documented audio mime`() {
        for (mime in UiMediaLimits.AUDIO_MIME) {
            assertNull(UiMediaLimits.validate(UiMediaLimits.NAVIGATION, probe(mime = mime)), mime)
        }
    }

    @Test fun `video mime set excludes the animated-image entries`() {
        assertEquals(setOf("video/mp4", "video/webm"), UiMediaLimits.VIDEO_MIME)
    }

    @Test fun `null duration is a rejection for every kind`() {
        for (spec in listOf(
            UiMediaLimits.NAVIGATION, UiMediaLimits.LAUNCH,
            UiMediaLimits.BOOT, UiMediaLimits.BOOT_CLIP,
            UiMediaLimits.GAMEBOOT_CLIP,
        )) {
            val p = if (spec.kind == UiMediaLimits.Kind.VIDEO) videoProbe(durationMs = null) else probe(durationMs = null)
            val message = assertNotNull(
                UiMediaLimits.validate(spec, p),
                "null duration must be rejected for $spec",
            )
            assertEquals(UiMediaLimits.MSG_NO_DURATION, message)
        }
    }

    @Test fun `rejection messages name the duration cap`() {
        val tooLong = assertNotNull(UiMediaLimits.validate(UiMediaLimits.NAVIGATION, probe(durationMs = 600L)))
        assertTrue(tooLong.contains("0.5 s"), "must name the cap: $tooLong")
    }

    @Test fun `audio has no floor - a zero-duration probe passes the range check`() {
        assertNull(UiMediaLimits.validate(UiMediaLimits.NAVIGATION, probe(durationMs = 0L)))
        for (spec in listOf(
            UiMediaLimits.NAVIGATION, UiMediaLimits.CONFIRM,
            UiMediaLimits.BACK, UiMediaLimits.ERROR, UiMediaLimits.NOTIFICATION, UiMediaLimits.LAUNCH,
        )) {
            assertEquals(0L, spec.recommendedMinMs, "$spec: audio must carry no floor")
        }
    }

    @Test fun `audio byte ceiling far exceeds every duration cap - it is not the real limit`() {
        for (spec in listOf(
            UiMediaLimits.NAVIGATION, UiMediaLimits.CONFIRM,
            UiMediaLimits.BACK, UiMediaLimits.ERROR, UiMediaLimits.NOTIFICATION, UiMediaLimits.LAUNCH,
            UiMediaLimits.BOOT,
        )) {
            assertTrue(
                spec.maxBytes >= UiMediaLimits.AUDIO_STAGE_MAX_BYTES,
                "$spec: audio byte ceiling drifted below the staging constant",
            )
            assertTrue(spec.hardMaxMs > 0, "$spec: the duration cap is the real ceiling")
        }
    }

    @Test fun `extension mapping is closed over the known extensions`() {
        for (ext in UiMediaLimits.knownUiMediaExtensions) {
            assertNotNull(UiMediaLimits.mimeForExtension(ext), ext)
        }
        assertNull(UiMediaLimits.mimeForExtension("exe"))
        assertNull(UiMediaLimits.extensionForMime("application/pdf"))
    }

    @Test fun `stored extension round-trips through its mime`() {
        for (mime in UiMediaLimits.AUDIO_MIME + UiMediaLimits.VIDEO_MIME) {
            assertNotNull(UiMediaLimits.extensionForMime(mime), "no stored extension for $mime")
        }
    }

    @Test fun `every slot spec has sane ranges`() {
        for (spec in listOf(
            UiMediaLimits.NAVIGATION, UiMediaLimits.CONFIRM,
            UiMediaLimits.BACK, UiMediaLimits.ERROR, UiMediaLimits.NOTIFICATION, UiMediaLimits.LAUNCH,
            UiMediaLimits.GAMEBOOT_CLIP, UiMediaLimits.BOOT, UiMediaLimits.BOOT_CLIP,
        )) {
            assertTrue(spec.recommendedMinMs >= 0, "$spec: min must not be negative")
            assertTrue(spec.recommendedMaxMs >= spec.recommendedMinMs, "$spec: range inverted")
            assertTrue(spec.hardMaxMs >= spec.recommendedMaxMs, "$spec: hard cap below the recommended range")
            assertTrue(spec.maxBytes > 0, "$spec: byte cap must be positive")
        }
    }
}
