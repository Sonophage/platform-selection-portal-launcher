package com.psplauncher.core.ui.media

import android.media.AudioManager
import com.psplauncher.core.domain.model.UiMediaSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two decisions behind menu music that are not about players, managers or files.
 *
 * Both are the kind that a later change softens without noticing: "surely we should duck rather
 * than stop", and "surely background music should be on by default like every other presentation
 * switch". Each is written down here with the reason it is not.
 */
class MenuMusicFocusTest {

    @Test
    fun `every kind of focus loss stops the music, including the one that invites ducking`() {
        // LOSS_TRANSIENT_CAN_DUCK is the system asking us to keep playing quietly underneath.
        // That is right for a navigation app talking over music. This IS music, and music under
        // music is noise — so it is treated as a loss like the rest.
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
        // An unknown future constant is a loss, not a gain. Guessing the other way would leave
        // the music playing over whatever had just taken the speaker.
        assertTrue(!MenuMusicPlayer.holdsFocusAfter(Int.MIN_VALUE))
    }

    @Test
    fun `the slot ships with no track, which is what makes the switch honest`() {
        // Every other AUDIO_TRACK slot has a bundled default so it is audible out of the box.
        // This one deliberately does not: a launcher that starts playing music at a new user is
        // the reason the switch is off by default, and the two facts have to agree.
        assertNull(UiMediaSlot.MENU_MUSIC.bundledDefaultRes())
        // Fifteen minutes, and the number has a reason: the first real file offered to this slot
        // was a ten-minute console menu loop, which the original five-minute guess refused. A
        // ceiling that rejects the canonical case is not a limit, it is a bug.
        assertTrue(
            "a ten-minute menu loop is the canonical case and must fit",
            UiMediaSlot.MENU_MUSIC.limits.hardMaxMs >= 600_000L,
        )
    }
}
