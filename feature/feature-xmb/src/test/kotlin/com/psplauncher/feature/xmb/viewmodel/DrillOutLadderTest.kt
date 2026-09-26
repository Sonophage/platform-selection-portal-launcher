package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillOutLadderTest {
    private val root = XMBUiState()

    @Test fun `the category root has no level to leave`() {
        assertNull(root.drillOutStep)
        assertFalse(root.isInSubItem)
    }

    @Test fun `isInSubItem is exactly 'there is a rung to climb'`() {
        val drilled = listOf(
            root.copy(musicNav = MusicNav.AllMusic),
            root.copy(videoNav = VideoNav.Library("lib", "Movies")),
            root.copy(videoNav = VideoNav.Playlist(1L, "Mix")),
            root.copy(videoNav = VideoNav.Favorites),
            root.copy(videoNav = VideoNav.AllVideos),
            root.copy(photoNav = PhotoNav.Library("alb", "Trip")),
            root.copy(photoNav = PhotoNav.AllPhotos),
            root.copy(selectedPlatformId = "psp"),
            root.copy(selectedCollectionId = 7L),
        )
        drilled.forEach { state ->
            assertTrue("$state should be a sub-item", state.isInSubItem)
            assertTrue("$state should have a rung", state.drillOutStep != null)
        }
    }

    @Test fun `a video library backs out to the libraries list, not out of video`() {
        assertEquals(
            DrillOutStep.VIDEO_LIBRARY,
            root.copy(videoNav = VideoNav.Library("lib", "Movies")).drillOutStep,
        )
        assertEquals(
            DrillOutStep.VIDEO_PLAYLIST,
            root.copy(videoNav = VideoNav.Playlist(1L, "Mix")).drillOutStep,
        )

        assertEquals(
            DrillOutStep.VIDEO_COLLECTION_CHILD,
            root.copy(videoNav = VideoNav.RecentlyWatched).drillOutStep,
        )
        assertEquals(DrillOutStep.VIDEO, root.copy(videoNav = VideoNav.Collections).drillOutStep)
    }

    @Test fun `a photo album backs out to the albums list first`() {
        assertEquals(
            DrillOutStep.PHOTO_LIBRARY,
            root.copy(photoNav = PhotoNav.Library("alb", "Trip")).drillOutStep,
        )
        assertEquals(DrillOutStep.PHOTO, root.copy(photoNav = PhotoNav.Albums).drillOutStep)
    }

    @Test fun `a media sub-view outranks a games folder left open behind it`() {
        val state = root.copy(musicNav = MusicNav.AllMusic, selectedPlatformId = "psp")
        assertEquals(DrillOutStep.MUSIC, state.drillOutStep)
    }

    @Test fun `a games folder and a collection share the last rung`() {
        assertEquals(DrillOutStep.PLATFORM_FOLDER, root.copy(selectedPlatformId = "psp").drillOutStep)
        assertEquals(DrillOutStep.PLATFORM_FOLDER, root.copy(selectedCollectionId = 7L).drillOutStep)
    }
}
