package com.psplauncher.feature.library.scanner

import com.psplauncher.core.domain.model.Video
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoQuickScanTest {
    private fun video(
        lastModified: Long? = 1000L,
        durationMs: Long? = 7_200_000L,
        width: Int? = 1920,
        height: Int? = 1080,
        codec: String? = "HEVC",
        thumbnailUri: String? = "file:///data/files/video_thumbs/abc.jpg",
    ) = Video(
        id = "v1",
        libraryId = "l1",
        uri = "content://doc/v1",
        displayName = "Arrival.mkv",
        durationMs = durationMs,
        width = width,
        height = height,
        codec = codec,
        thumbnailUri = thumbnailUri,
        lastModified = lastModified,
    )

    private val thumbPresent: (String) -> Boolean = { true }
    private val thumbGone: (String) -> Boolean = { false }

    @Test
    fun `an unchanged probed video keeps its metadata`() {
        assertTrue(canReuseVideoMetadata(video(), lastModified = 1000L))
    }

    @Test
    fun `a video whose file changed is probed again`() {
        assertFalse(canReuseVideoMetadata(video(lastModified = 1000L), lastModified = 2000L))
    }

    @Test
    fun `a video the scanner has never seen is probed`() {
        assertFalse(canReuseVideoMetadata(null, lastModified = 1000L))
    }

    @Test
    fun `a row from before the metadata pass is probed again even though its file is unchanged`() {
        val unprobed = video(durationMs = null, width = null, height = null, codec = null, thumbnailUri = null)
        assertFalse(canReuseVideoMetadata(unprobed, lastModified = 1000L))
    }

    @Test
    fun `a probed video with no thumbnail still keeps its metadata`() {
        assertTrue(canReuseVideoMetadata(video(thumbnailUri = null), lastModified = 1000L))
    }

    @Test
    fun `a video known only by its duration counts as probed`() {
        val durationOnly = video(width = null, height = null, codec = null, thumbnailUri = null)
        assertTrue(canReuseVideoMetadata(durationOnly, lastModified = 1000L))
    }

    @Test
    fun `a thumbnail whose file is on disk is carried over`() {
        assertEquals(
            ThumbAction.Carry("file:///data/files/video_thumbs/abc.jpg"),
            thumbActionFor(video(), thumbPresent),
        )
    }

    @Test
    fun `a probed video with no thumbnail is told to generate one`() {
        assertEquals(ThumbAction.Generate, thumbActionFor(video(thumbnailUri = null), thumbPresent))
    }

    @Test
    fun `clearing the thumbnail cache makes the next scan regenerate thumbnails`() {
        assertEquals(ThumbAction.Generate, thumbActionFor(video(), thumbGone))
    }

    @Test
    fun `a blank thumbnail uri is treated as absent rather than carried`() {
        assertEquals(ThumbAction.Generate, thumbActionFor(video(thumbnailUri = "   "), thumbPresent))
    }

    @Test
    fun `a video the scanner has never seen is told to generate`() {
        assertEquals(ThumbAction.Generate, thumbActionFor(null, thumbPresent))
    }

    @Test
    fun `Carry never names a file that is not there`() {
        listOf(video(), video(thumbnailUri = null), video(thumbnailUri = "  ")).forEach { v ->
            val action = thumbActionFor(v, thumbGone)
            assertFalse(action is ThumbAction.Carry, "Carry returned for a missing file: $v")
        }
    }
}
