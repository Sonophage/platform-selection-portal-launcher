package com.psplauncher.feature.library.scanner

import com.psplauncher.core.domain.model.Video
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The video quick-scan reuse rule, which is two decisions and not one.
 *
 * A book's cover comes out of the same parse that reads its title, so [canReuse] can answer both
 * with a single boolean. A video's do not: [VideoScanner] reads metadata with one
 * MediaMetadataRetriever pass and generates the thumbnail with a second, and either can fail while
 * the other succeeds. Deciding them together is what broke: a row that was probed but whose frame
 * grab came back null was reused wholesale by every later quick scan, so its thumbnail could never
 * appear again no matter how many times the user pressed Rescan. Only a Deep Rescan recovered it,
 * and nothing on screen said so.
 *
 * So metadata is carried over when the file is unchanged (that pass is the expensive one), and the
 * thumbnail is carried over only when the file it names is actually on disk. The failure modes run
 * in both directions and both are quiet: reuse too eagerly and a library stays blank forever, drop
 * the metadata too readily and every quick scan is secretly a deep one.
 *
 * The fourth test is the one that is easy to get wrong, and it is inherited from
 * [BookQuickScanTest]: "has a thumbnail" cannot stand in for "has been probed", because a video
 * whose frame grab genuinely fails carries exactly the same null as a row written before the
 * metadata pass existed.
 */
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

    // ── Carrying the metadata over ────────────────────────────────────────────

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
        // A row carrying nothing but a file name and a timestamp. Without this rule an existing
        // library would keep showing bare file names until the user found Deep Rescan.
        val unprobed = video(durationMs = null, width = null, height = null, codec = null, thumbnailUri = null)
        assertFalse(canReuseVideoMetadata(unprobed, lastModified = 1000L))
    }

    @Test
    fun `a probed video with no thumbnail still keeps its metadata`() {
        // The "too strict" direction. A video whose frame grab cannot be taken (DRM, an exotic
        // codec) must not re-run the whole MediaMetadataRetriever probe on every single scan just
        // because its thumbnail slot is empty. Only the thumbnail is retried; see below.
        assertTrue(canReuseVideoMetadata(video(thumbnailUri = null), lastModified = 1000L))
    }

    @Test
    fun `a video known only by its duration counts as probed`() {
        // A container may report a duration and nothing else. Reading four fields rather than one
        // keeps that video out of the reprobe-forever loop.
        val durationOnly = video(width = null, height = null, codec = null, thumbnailUri = null)
        assertTrue(canReuseVideoMetadata(durationOnly, lastModified = 1000L))
    }

    // ── Carrying the thumbnail over ───────────────────────────────────────────

    @Test
    fun `a thumbnail whose file is on disk is carried over`() {
        assertEquals(
            ThumbAction.Carry("file:///data/files/video_thumbs/abc.jpg"),
            thumbActionFor(video(), thumbPresent),
        )
    }

    @Test
    fun `a probed video with no thumbnail is told to generate one`() {
        // THE REPORTED BUG. Every video in the library showed no thumbnail and no amount of
        // rescanning changed it, because the quick scan returned the row whole with its null
        // intact. Generate is what sends the scanner back to generateThumbnail on a quick pass.
        assertEquals(ThumbAction.Generate, thumbActionFor(video(thumbnailUri = null), thumbPresent))
    }

    @Test
    fun `clearing the thumbnail cache makes the next scan regenerate thumbnails`() {
        // The row is unchanged and probed, so only the missing file can force the regenerate. If
        // this said Carry, Clear Thumbnail Cache then Rescan would report success and leave every
        // row blank.
        assertEquals(ThumbAction.Generate, thumbActionFor(video(), thumbGone))
    }

    @Test
    fun `a blank thumbnail uri is treated as absent rather than carried`() {
        // An empty string is not a path. Carrying it would hand Coil something unloadable and the
        // row would render its placeholder forever without ever retrying.
        assertEquals(ThumbAction.Generate, thumbActionFor(video(thumbnailUri = "   "), thumbPresent))
    }

    @Test
    fun `a video the scanner has never seen is told to generate`() {
        assertEquals(ThumbAction.Generate, thumbActionFor(null, thumbPresent))
    }

    @Test
    fun `Carry never names a file that is not there`() {
        // A guard on the guard. Carry exists to mean "this frame is on disk and needs no work";
        // if it could ever be returned for an absent file the type would be lying, and every
        // caller that trusts it would render a broken image instead of regenerating.
        listOf(video(), video(thumbnailUri = null), video(thumbnailUri = "  ")).forEach { v ->
            val action = thumbActionFor(v, thumbGone)
            assertFalse(action is ThumbAction.Carry, "Carry returned for a missing file: $v")
        }
    }
}
