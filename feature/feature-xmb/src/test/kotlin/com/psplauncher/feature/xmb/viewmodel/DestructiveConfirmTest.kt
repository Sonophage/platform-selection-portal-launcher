package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestructiveConfirmTest {
    private val everyMediaMenu = mapOf(
        "video file" to videoFileContextMenuItems(
            isFavorite = false, resumePositionMs = 0, hasWatchStamp = true, inPlaylist = true,
        ),
        "video playlist" to videoPlaylistContextMenuItems(),
        "photo" to photoFileContextMenuItems(),
        "book" to bookContextMenuItems(hasOpenStamp = true),
        "music track" to musicTrackContextMenuItems(playlistId = 7L, hasPlayStamp = true),
        "playlist row" to playlistRowContextMenuItems(),
        "collection row" to collectionRowContextMenuItems(isPinned = false, hasOtherCategory = true),
        "platform" to platformContextMenuItems("ps2", pinned = false, iconDisplayLabel = "Art"),
    )

    @Test
    fun `a confirm opens with the cursor on the harmless answer`() {
        val rows = destructiveConfirmItems("Remove From Library")
        assertFalse("the first row is the destructive one", rows.first().isDestructive)
        assertEquals("the first row is not Cancel", "Cancel", rows.first().label)
    }

    @Test
    fun `a confirm is two rows, and the destructive one repeats the verb`() {
        val rows = destructiveConfirmItems("Delete Playlist")
        assertEquals("a confirm is two rows", 2, rows.size)
        assertEquals("the yes row does not name what it will do", "Delete Playlist", rows.last().label)
        assertTrue("nothing here is marked destructive", rows.last().isDestructive)
    }

    private val gatedByDesign = mapOf(
        "video file" to setOf("video_remove"),
        "video playlist" to setOf("delete_video_playlist"),
        "photo" to setOf("photo_remove"),
        "book" to setOf("book_remove"),
        "music track" to setOf("remove_track"),
        "playlist row" to setOf("delete_playlist"),
        "collection row" to setOf("delete_collection"),
        "platform" to setOf("remove"),
    )

    @Test
    fun `the rows that delete are exactly the rows that ask first`() {
        everyMediaMenu.forEach { (name, rows) ->
            assertEquals(
                "$name: the gated rows are not the ones that destroy something",
                gatedByDesign.getValue(name),
                rows.filter { it.needsConfirm() }.map { it.id }.toSet(),
            )
        }
    }

    @Test
    fun `the exempt ids are real rows, so the exemption cannot rot`() {
        val destructiveIds = everyMediaMenu.values.flatten().filter { it.isDestructive }.map { it.id }.toSet()
        CONFIRM_EXEMPT_IDS.forEach { id ->
            assertTrue(
                "'$id' is exempt from confirming, but no menu offers it any more",
                id in destructiveIds,
            )
        }
    }

    @Test
    fun `unlinking from a playlist does not ask, because it destroys nothing`() {
        val inPlaylist = everyMediaMenu.getValue("music track").first { it.id == "remove_from_playlist" }
        assertTrue("the row should still read as destructive", inPlaylist.isDestructive)
        assertFalse("unlinking a track asked for confirmation", inPlaylist.needsConfirm())
    }

    private fun trackMenu() = XMBContextMenu(
        title = "Blue Monday",
        items = everyMediaMenu.getValue("music track"),
        musicTrackId = "t1",
        playlistId = 7L,
    )

    @Test
    fun `a delete opens a confirm and keeps everything the handler will need`() {
        val confirm = trackMenu().confirmSwapFor("remove_track")

        assertTrue("removing a track fired without asking", confirm != null)
        assertEquals("the cursor is not on Cancel", 0, confirm!!.selectedIndex)
        assertEquals(listOf(CONFIRM_NO_ID, CONFIRM_YES_ID), confirm.items.map { it.id })
        assertEquals("remove_track", confirm.pendingConfirmId)
        assertEquals("the confirm lost the track it was asked about", "t1", confirm.musicTrackId)
        assertEquals("the confirm lost the playlist context", 7L, confirm.playlistId)
        assertTrue("the title does not say what is being asked", confirm.title.contains("Blue Monday"))
    }

    @Test
    fun `answering yes dispatches instead of asking again`() {
        val confirm = trackMenu().confirmSwapFor("remove_track")!!
        assertEquals(
            "the confirmed action would have re-opened its own confirm, so it could never run",
            null,
            confirm.confirmSwapFor("remove_track"),
        )
    }

    @Test
    fun `an unlink and a harmless row both go straight through`() {
        assertEquals(null, trackMenu().confirmSwapFor("remove_from_playlist"))
        assertEquals(null, trackMenu().confirmSwapFor("play"))
    }
}
