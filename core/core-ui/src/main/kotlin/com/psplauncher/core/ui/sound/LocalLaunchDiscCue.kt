package com.psplauncher.core.ui.sound

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The sound the launch disc opens on — [com.psplauncher.core.domain.model.UiMediaSlot
 * .LAUNCH_DISC_AUDIO], fired from inside
 * [com.psplauncher.core.ui.components.DiscLaunchCeremony] as its first frame is drawn.
 *
 * Ambient rather than a parameter for the same reason as [LocalMenuSounds]: the ceremony has six
 * entry points (games, films, books, tracks, apps and the settings preview), and a cue wired
 * through the call sites is a cue five of them have and the sixth quietly does not.
 *
 * Not a [MenuSound]: this is seconds of presentation audio played by ExoPlayer, not a SoundPool
 * tick, and it deliberately shares the one-shot [com.psplauncher.core.ui.media.UiMediaAudioPlayer]
 * with the GameBoot cue — which is what makes the GameBoot sound, scheduled against the disc's
 * exit, take over from this one partway through rather than play on top of it.
 *
 * The default is silence, and so is the shipped behaviour: the slot has no bundled sample, so a
 * user who assigns nothing gets the ceremony it has always had.
 */
val LocalLaunchDiscCue: ProvidableCompositionLocal<() -> Unit> =
    staticCompositionLocalOf { {} }
