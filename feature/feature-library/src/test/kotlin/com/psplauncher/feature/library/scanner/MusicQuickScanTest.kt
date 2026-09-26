package com.psplauncher.feature.library.scanner

import com.psplauncher.core.domain.model.MusicTrack
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        val foreign = "file:///data/user/0/com.playfieldportal.launcher.lite/files/music_art/abc.img"
        assertFalse(musicArtStillOnDisk(foreign, gone))
    }

    @Test
    fun `a track that simply has no art is still reused`() {
        assertTrue(musicArtStillOnDisk(null, gone))
        assertTrue(musicArtStillOnDisk("", gone))
        assertTrue(musicArtStillOnDisk("   ", gone))
    }

    @Test
    fun `a stored value that is not a usable path forces a reparse`() {
        assertFalse(musicArtStillOnDisk("://not a uri", gone))
    }

    private fun track(
        lastModified: Long? = 1000L,
        artUri: String? = null,
        albumArtist: String? = "",
    ) = MusicTrack(
        id = "t1", folderId = "f1", uri = "content://t1", displayName = "t1.mp3",
        lastModified = lastModified, artUri = artUri, albumArtist = albumArtist,
    )

    @Test
    fun `an unchanged, already-read track is reused`() {
        assertTrue(canReuseMusicMetadata(track(), 1000L, present))
    }

    @Test
    fun `a changed file is reparsed`() {
        assertFalse(canReuseMusicMetadata(track(lastModified = 1000L), 2000L, present))
    }

    @Test
    fun `a track never seen before is parsed`() {
        assertFalse(canReuseMusicMetadata(null, 1000L, present))
    }

    @Test
    fun `a row written before album_artist existed is reparsed, once`() {
        assertFalse(canReuseMusicMetadata(track(albumArtist = null), 1000L, present))
    }

    @Test
    fun `a file that genuinely has no album artist is reused after one read`() {
        assertTrue(canReuseMusicMetadata(track(albumArtist = ""), 1000L, present))
    }

    @Test
    fun `the art rule still applies to a row that has been read`() {
        assertFalse(canReuseMusicMetadata(track(artUri = "file:///gone.img"), 1000L, gone))
    }
}
