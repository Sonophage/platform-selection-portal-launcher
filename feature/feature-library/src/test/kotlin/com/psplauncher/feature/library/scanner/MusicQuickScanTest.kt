package com.psplauncher.feature.library.scanner

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a quick scan may carry a track's album art over.
 *
 * This was found on the device rather than reasoned about, and the number is the reason it is
 * worth a test: after the package rename, 3,965 of 3,966 tracks carried an `art_uri` pointing into
 * `/data/user/0/<old package>/files/music_art/`, which this app cannot read. The rows were not
 * null and their files were not deleted — they belonged to somebody else. The quick scan reused
 * them wholesale on every pass, so every album cover in the library stayed blank and no amount of
 * rescanning could change it.
 *
 * Album art is not separable from the metadata pass the way a video thumbnail is, so a missing file
 * has to force a full reparse of that track. That makes the opposite mistake expensive in a way
 * nobody would notice: if a track with no art at all also reparsed, every quick scan would secretly
 * be a deep scan for the whole no-art half of a library.
 */
class MusicQuickScanTest {

    private val present: (String) -> Boolean = { true }
    private val gone: (String) -> Boolean = { false }

    @Test
    fun `art whose file is still there is carried over`() {
        assertTrue(musicArtStillOnDisk("file:///data/user/0/app/files/music_art/abc.img", present))
    }

    @Test
    fun `art whose file has gone forces a reparse`() {
        assertFalse(musicArtStillOnDisk("file:///data/user/0/app/files/music_art/abc.img", gone))
    }

    @Test
    fun `art belonging to a package we cannot read forces a reparse`() {
        // THE REPORTED SHAPE. The path is well formed and the file may even exist on disk; it is
        // simply not ours. exists() answers false for it because the directory is unreadable, and
        // that is the whole signal available.
        val foreign = "file:///data/user/0/com.playfieldportal.launcher.lite/files/music_art/abc.img"
        assertFalse(musicArtStillOnDisk(foreign, gone))
    }

    @Test
    fun `a track that simply has no art is still reused`() {
        // The expensive mistake. A library is full of tracks with no embedded art, and reparsing
        // them on every scan is invisible except as a scan that never gets faster.
        assertTrue(musicArtStillOnDisk(null, gone))
        assertTrue(musicArtStillOnDisk("", gone))
        assertTrue(musicArtStillOnDisk("   ", gone))
    }

    @Test
    fun `a stored value that is not a usable path forces a reparse`() {
        // Better to reparse one track than to keep a row pointing at something unreadable forever.
        assertFalse(musicArtStillOnDisk("://not a uri", gone))
    }
}
