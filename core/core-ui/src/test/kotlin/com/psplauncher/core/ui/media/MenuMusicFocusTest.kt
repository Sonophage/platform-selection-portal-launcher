package com.psplauncher.core.ui.media

import android.media.AudioManager
import com.psplauncher.core.domain.model.UiMediaSlot
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuMusicFocusTest {
    @Test
    fun `every kind of focus loss stops the music, including the one that invites ducking`() {
        listOf(
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
        ).forEach { change ->
            assertTrue(
                "focus change $change must stop the music",
                !MenuMusicPlayer.holdsFocusAfter(change),
            )
        }
    }

    @Test
    fun `only a gain resumes it`() {
        assertTrue(MenuMusicPlayer.holdsFocusAfter(AudioManager.AUDIOFOCUS_GAIN))

        assertTrue(!MenuMusicPlayer.holdsFocusAfter(Int.MIN_VALUE))
    }

    @Test
    fun `the slot ships with no track, which is what makes the switch honest`() {
        assertNull(UiMediaSlot.MENU_MUSIC.bundledDefaultRes())

        assertTrue(
            "a ten-minute menu loop is the canonical case and must fit",
            UiMediaSlot.MENU_MUSIC.limits.hardMaxMs >= 600_000L,
        )
    }
}
