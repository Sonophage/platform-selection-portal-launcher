package com.psplauncher.core.ui.sound

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Playing a menu sound from a composable that has no ViewModel of its own.
 *
 * Every screen that makes noise today reaches [MenuSoundPlayer] through its ViewModel, which works
 * right up until the composable that owns the navigation is shared chrome — [SettingsScaffold] is
 * one dispatch for every settings screen, and threading a player down through a dozen signatures
 * to reach it would put the wiring in twelve places to serve one.
 *
 * A lambda rather than the player itself: core-ui components can make a sound without taking a
 * dependency on how sound is produced, and a preview or a test simply gets the default.
 *
 * The default is silence, and that is the one hazard here — a missing provider mutes rather than
 * crashes. It is provided once, at the top of the activity's content, for exactly that reason:
 * one place to get right, and no screen that can quietly fall outside it.
 */
val LocalMenuSounds: ProvidableCompositionLocal<(MenuSound) -> Unit> =
    staticCompositionLocalOf { { _: MenuSound -> } }
