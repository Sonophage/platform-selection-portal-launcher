package com.psplauncher.core.domain.model

import com.psplauncher.themekit.UiMediaLimits

enum class UiMediaKind { SOUND, VIDEO, AUDIO_TRACK }

enum class UiMediaSlot(
    val key: String,
    val kind: UiMediaKind,
    val displayName: String,
    val limits: UiMediaLimits.Spec,
) {
    SOUND_SCROLL("sound_scroll", UiMediaKind.SOUND, "Navigation", UiMediaLimits.NAVIGATION),
    SOUND_SELECT("sound_select", UiMediaKind.SOUND, "Select / Open", UiMediaLimits.NAVIGATION),
    SOUND_SYSTEM_BROWSE("sound_system_browse", UiMediaKind.SOUND, "Category Change", UiMediaLimits.NAVIGATION),
    SOUND_BACK("sound_back", UiMediaKind.SOUND, "Back / Cancel", UiMediaLimits.BACK),
    SOUND_CONFIRM("sound_confirm", UiMediaKind.SOUND, "Confirm / Apply", UiMediaLimits.CONFIRM),
    SOUND_ERROR("sound_error", UiMediaKind.SOUND, "Error / Invalid", UiMediaLimits.ERROR),
    SOUND_LAUNCH("sound_launch", UiMediaKind.SOUND, "Launch Sound", UiMediaLimits.LAUNCH),
    SOUND_NOTIFICATION("sound_notification", UiMediaKind.SOUND, "Notification", UiMediaLimits.NOTIFICATION),

    BOOT_VIDEO("boot_video", UiMediaKind.VIDEO, "Boot Animation", UiMediaLimits.BOOT_CLIP),
    BOOT_AUDIO("boot_audio", UiMediaKind.AUDIO_TRACK, "Boot Sound", UiMediaLimits.BOOT),

    LAUNCH_DISC_AUDIO("launch_disc_audio", UiMediaKind.AUDIO_TRACK, "Launch Disc Sound", UiMediaLimits.LAUNCH_DISC_AUDIO),

    GAMEBOOT_AUDIO("gameboot_audio", UiMediaKind.AUDIO_TRACK, "GameBoot Sound", UiMediaLimits.GAMEBOOT_AUDIO),
    GAMEBOOT_VIDEO("gameboot_video", UiMediaKind.VIDEO, "GameBoot Animation", UiMediaLimits.GAMEBOOT_CLIP),

    MENU_MUSIC("menu_music", UiMediaKind.AUDIO_TRACK, "Menu Music", UiMediaLimits.MENU_MUSIC),
    ;

    val isSound: Boolean get() = kind == UiMediaKind.SOUND

    companion object {
        private val byKey: Map<String, UiMediaSlot> = entries.associateBy { it.key }

        fun fromKey(key: String): UiMediaSlot? = byKey[key]

        fun isValidKey(key: String): Boolean = key in byKey

        fun ofKind(kind: UiMediaKind): List<UiMediaSlot> = entries.filter { it.kind == kind }
    }
}
