package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
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
                rows.filter { it.confirms }.mapNotNull { it.action }.toSet(),
            )
        }
    }

    @Test
    fun `unlinking from a playlist reads as destructive but does not ask`() {
        listOf(
            "music track" to "remove_from_playlist",
            "video file" to "video_remove_playlist",
        ).forEach { (menu, id) ->
            val row = everyMediaMenu.getValue(menu).first { it.action == id }
            assertTrue("$menu: the row should still read as destructive", row.isDestructive)
            assertEquals("$menu: unlinking asked for confirmation", false, row.confirms)
        }
    }

    @Test
    fun `Favorite is never buried in a submenu`() {
        val favorite = everyMediaMenu.getValue("video file").first { it.action == "video_favorite" }
        assertTrue("Favorite would cost two presses", favorite.pinnedToRoot)
    }
}
