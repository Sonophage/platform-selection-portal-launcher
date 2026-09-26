package com.psplauncher.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiMediaSlotTest {
    @Test fun `sound slots are exactly the eight rows in roster order`() {
        assertEquals(
            listOf(

                "sound_scroll" to "Navigation",
                "sound_select" to "Select / Open",
                "sound_system_browse" to "Category Change",
                "sound_back" to "Back / Cancel",
                "sound_confirm" to "Confirm / Apply",
                "sound_error" to "Error / Invalid",
                "sound_launch" to "Launch Sound",
                "sound_notification" to "Notification",
            ),
            UiMediaSlot.ofKind(UiMediaKind.SOUND).map { it.key to it.displayName },
            "the settings screen iterates enum order — this is the Sound screen's row order",
        )
    }

    @Test fun `boot sound is the seventh row and stays an audio track`() {
        assertEquals(UiMediaKind.AUDIO_TRACK, UiMediaSlot.BOOT_AUDIO.kind)
        assertFalse(UiMediaSlot.BOOT_AUDIO.isSound, "boot audio is not a SoundPool menu sound")
        assertEquals("boot_audio", UiMediaSlot.BOOT_AUDIO.key)
        assertEquals("Boot Sound", UiMediaSlot.BOOT_AUDIO.displayName)
    }

    @Test fun `the split restored two slots, and the old misspelt key stays invalid`() {
        assertEquals(UiMediaSlot.SOUND_SELECT, UiMediaSlot.fromKey("sound_select"))
        assertEquals(UiMediaSlot.SOUND_SYSTEM_BROWSE, UiMediaSlot.fromKey("sound_system_browse"))
        assertNull(UiMediaSlot.fromKey("sound_systembrowse"), "the pre-merge spelling is not a slot")
        assertFalse(UiMediaSlot.isValidKey("sound_systembrowse"))
    }

    @Test fun `gameboot is two slots again - the clip, and the sound under the built-in one`() {
        assertEquals(UiMediaSlot.GAMEBOOT_VIDEO, UiMediaSlot.fromKey("gameboot_video"))
        assertEquals(UiMediaKind.VIDEO, UiMediaSlot.GAMEBOOT_VIDEO.kind)
        assertEquals(UiMediaSlot.GAMEBOOT_AUDIO, UiMediaSlot.fromKey("gameboot_audio"))
        assertEquals(UiMediaKind.AUDIO_TRACK, UiMediaSlot.GAMEBOOT_AUDIO.kind)
    }

    @Test fun `the launch disc's opening cue is its own audio slot`() {
        assertEquals(UiMediaSlot.LAUNCH_DISC_AUDIO, UiMediaSlot.fromKey("launch_disc_audio"))
        assertEquals(UiMediaKind.AUDIO_TRACK, UiMediaSlot.LAUNCH_DISC_AUDIO.kind)
    }

    @Test fun `sound_scroll keeps its storage key through the rename to Navigation`() {
        assertEquals(UiMediaSlot.SOUND_SCROLL, UiMediaSlot.fromKey("sound_scroll"))
        assertEquals("sound_scroll", UiMediaSlot.SOUND_SCROLL.key)
    }

    @Test fun `every slot's kind matches its limits spec kind`() {
        for (slot in UiMediaSlot.entries) {
            assertEquals(
                slot.kind.name,
                slot.limits.kind.name,
                "${slot.key}: UiMediaKind and UiMediaLimits.Kind have drifted",
            )
        }
    }

    @Test fun `slot keys stay filename-safe lowercase identifiers`() {
        for (slot in UiMediaSlot.entries) {
            assertTrue(Regex("[a-z0-9_]+").matches(slot.key), "${slot.key} is not filename-safe")
        }
    }

    @Test fun `slot keys are unique`() {
        val keys = UiMediaSlot.entries.map { it.key }
        assertEquals(keys.size, keys.distinct().size, "keys are file names — a collision overwrites")
    }
}
