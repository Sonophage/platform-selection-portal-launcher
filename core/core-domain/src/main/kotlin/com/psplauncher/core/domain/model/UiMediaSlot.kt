package com.psplauncher.core.domain.model

import com.psplauncher.themekit.UiMediaLimits

/**
 * Which media family a slot holds. Drives the accepted MIME set, the probe questions, and which
 * bulk reset touches it ("Reset Audio to Defaults" clears [SOUND] only — boot and GameBoot media
 * are separate screens' concerns).
 */
enum class UiMediaKind { SOUND, VIDEO, AUDIO_TRACK }

/**
 * Every UI-media slot a user can personalize, and the ONE registry for their stable storage keys.
 *
 * A slot's file lives at `filesDir/ui-media/<key>.<ext>` — [key] is used verbatim as the file
 * name, which makes [isValidKey] load-bearing: it is what stops a crafted key escaping the
 * directory (the same guard pattern as theme-kit's `CustomizableIcons.isValidKey`). The enum
 * makes an escape impossible through the API surface today; the guard exists so the invariant
 * is enforced in one place rather than assumed by every caller.
 *
 * [limits] carries this slot's caps from the design doc's table — see [UiMediaLimits].
 *
 * The menu-sound roster is the seven-sound plan's: Navigation covers SCROLL, SELECT and
 * SYSTEM_BROWSE (one sample, three events — see [UiMediaSlot] docs in
 * docs/plans/README.md (C10)), and Boot Sound is the seventh row of the Sound screen
 * while staying an AUDIO_TRACK slot. Slots removed from this enum are swept from user installs
 * and restored backups by `UiMediaStore.pruneOrphans()`; their keys are deliberately NOT reused.
 */
enum class UiMediaSlot(
    val key: String,
    val kind: UiMediaKind,
    val displayName: String,
    val limits: UiMediaLimits.Spec,
) {
    // ── Menu sounds (Interface ▸ Sound) — the plan's seven-sound roster, in row order ──
    SOUND_SCROLL("sound_scroll", UiMediaKind.SOUND, "Navigation", UiMediaLimits.NAVIGATION),
    SOUND_BACK("sound_back", UiMediaKind.SOUND, "Back / Cancel", UiMediaLimits.BACK),
    SOUND_CONFIRM("sound_confirm", UiMediaKind.SOUND, "Confirm / Apply", UiMediaLimits.CONFIRM),
    SOUND_ERROR("sound_error", UiMediaKind.SOUND, "Error / Invalid", UiMediaLimits.ERROR),
    SOUND_LAUNCH("sound_launch", UiMediaKind.SOUND, "Launch Sound", UiMediaLimits.LAUNCH),
    SOUND_NOTIFICATION("sound_notification", UiMediaKind.SOUND, "Notification", UiMediaLimits.NOTIFICATION),

    // ── Boot sequence (Display ▸ Boot Sequence) ──────────────────────────────
    BOOT_VIDEO("boot_video", UiMediaKind.VIDEO, "Boot Animation", UiMediaLimits.BOOT_CLIP),
    BOOT_AUDIO("boot_audio", UiMediaKind.AUDIO_TRACK, "Boot Sound", UiMediaLimits.BOOT),

    // ── The launch ceremony's two cues (Display ▸ Launch Disc / GameBoot) ────
    //
    // The opener plays as the disc rises; the GameBoot cue plays as it leaves. Two moments, so
    // two slots — one sound stretched across both would have to be the length of the ceremony.
    LAUNCH_DISC_AUDIO("launch_disc_audio", UiMediaKind.AUDIO_TRACK, "Launch Disc Sound", UiMediaLimits.LAUNCH_DISC_AUDIO),

    // ── GameBoot (Display ▸ GameBoot) ────────────────────────────────────────
    // The video slot replaces the whole presentation and brings its own audio. The audio slot is
    // for the other case, which is the common one: keep the built-in disc and change only what it
    // sounds like. It was retired once on the theory that GameBoot is a single thing you replace
    // wholesale — true of the clip, and not true of someone who likes the disc and wants their own
    // chime under it.
    GAMEBOOT_AUDIO("gameboot_audio", UiMediaKind.AUDIO_TRACK, "GameBoot Sound", UiMediaLimits.GAMEBOOT_AUDIO),
    GAMEBOOT_VIDEO("gameboot_video", UiMediaKind.VIDEO, "GameBoot Animation", UiMediaLimits.GAMEBOOT_CLIP),

    // ── Menu music (Interface ▸ Sound) ───────────────────────────────────────
    // The only slot that LOOPS, and the only one with no bundled default: background music is
    // something a user opts into with a track of their own, never something a launcher starts
    // playing at you out of the box.
    MENU_MUSIC("menu_music", UiMediaKind.AUDIO_TRACK, "Menu Music", UiMediaLimits.MENU_MUSIC),
    ;

    val isSound: Boolean get() = kind == UiMediaKind.SOUND

    companion object {
        private val byKey: Map<String, UiMediaSlot> = entries.associateBy { it.key }

        fun fromKey(key: String): UiMediaSlot? = byKey[key]

        fun isValidKey(key: String): Boolean = key in byKey

        /** Every slot of one kind — the settings screens iterate these. */
        fun ofKind(kind: UiMediaKind): List<UiMediaSlot> = entries.filter { it.kind == kind }
    }
}
