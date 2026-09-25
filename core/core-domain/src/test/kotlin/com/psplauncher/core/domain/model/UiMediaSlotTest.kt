package com.psplauncher.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the seven-sound roster (docs/plans/README.md (C10)): six SOUND-kind rows in the
 * plan table's order, Boot Sound as the seventh row on the Sound screen, and the storage-key
 * decisions that keep old installs and backups from breaking — `sound_scroll` survives its
 * rename to "Navigation" with no migration, and the collapsed slots' keys (the two merged menu
 * sounds, and GameBoot's retired audio slot) become invalid so `pruneOrphans` can sweep their
 * leftovers.
 */
class UiMediaSlotTest {

    // ── the roster ───────────────────────────────────────────────────────────

    @Test fun `sound slots are exactly the eight rows in roster order`() {
        assertEquals(
            listOf(
                // Three movement events, three rows. They shared one slot until the split; the
                // XMB distinguishes the cursor stepping, something opening and a category
                // changing, and a single sample for all three is what made every movement sound
                // alike. They still share a bundled default — see UiMediaDefaultsTest.
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

    // ── the collapse: removed slots ──────────────────────────────────────────

    @Test fun `the split restored two slots, and the old misspelt key stays invalid`() {
        // These two were merged into Navigation and then split back out. `sound_select` returns
        // under its original key; the other comes back as `sound_system_browse`, with the
        // underscore the rest of the roster uses — the pre-merge key was `sound_systembrowse`
        // and it is deliberately NOT revived. Nothing is stranded by that: pruneOrphans deletes
        // files whose name is not a live slot key, so any sample stored under the old spelling
        // went when the merge landed.
        assertEquals(UiMediaSlot.SOUND_SELECT, UiMediaSlot.fromKey("sound_select"))
        assertEquals(UiMediaSlot.SOUND_SYSTEM_BROWSE, UiMediaSlot.fromKey("sound_system_browse"))
        assertNull(UiMediaSlot.fromKey("sound_systembrowse"), "the pre-merge spelling is not a slot")
        assertFalse(UiMediaSlot.isValidKey("sound_systembrowse"))
    }

    @Test fun `gameboot is two slots again - the clip, and the sound under the built-in one`() {
        // This reverses an earlier decision, deliberately. GAMEBOOT_AUDIO was retired on the
        // reasoning that GameBoot is ONE thing you replace wholesale; the case that brought it
        // back is the opposite one — keep the built-in disc, change only what it sounds like.
        //
        // The wholesale rule survives inside resolveGameBootAudio: a custom VIDEO still silences
        // this slot, because a clip brings its own track.
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
        // No migration: a user who customized the old Navigation row keeps their assignment.
        assertEquals(UiMediaSlot.SOUND_SCROLL, UiMediaSlot.fromKey("sound_scroll"))
        assertEquals("sound_scroll", UiMediaSlot.SOUND_SCROLL.key)
    }

    // ── drift pins ───────────────────────────────────────────────────────────

    @Test fun `every slot's kind matches its limits spec kind`() {
        // UiMediaSlot carries the spec directly, so a slot can never LACK one — the drift risk is
        // the two kind enums disagreeing (e.g. a SOUND slot holding an AUDIO_TRACK spec would
        // accept the wrong MIME set at import).
        for (slot in UiMediaSlot.entries) {
            assertEquals(
                slot.kind.name,
                slot.limits.kind.name,
                "${slot.key}: UiMediaKind and UiMediaLimits.Kind have drifted",
            )
        }
    }

    @Test fun `slot keys stay filename-safe lowercase identifiers`() {
        // The keys are used verbatim as file names under filesDir/ui-media — see the path-escape
        // guard's own tests in UiMediaStoreTest.
        for (slot in UiMediaSlot.entries) {
            assertTrue(Regex("[a-z0-9_]+").matches(slot.key), "${slot.key} is not filename-safe")
        }
    }

    @Test fun `slot keys are unique`() {
        val keys = UiMediaSlot.entries.map { it.key }
        assertEquals(keys.size, keys.distinct().size, "keys are file names — a collision overwrites")
    }
}
