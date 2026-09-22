package com.psplauncher.core.common.launch

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Handing the screen to another app without Android animating the swap.
 *
 * The launch disc is drawn by PSPLauncher, in PSPLauncher's window. Android does not know that,
 * so when the emulator or app starts it runs its ordinary task transition — PFP slides away, the
 * new app slides in — and the disc slides out with it. No amount of z-order inside the Compose
 * tree can fix that: the disc is part of the window being animated.
 *
 * So the transition is switched off instead. The ceremony holds the screen, the new window simply
 * appears underneath it, and the hand-off is the one PFP drew rather than the one the system drew
 * over the top of it.
 *
 * Both halves are needed and they do different things. The zero-length custom animation covers
 * the ENTERING activity, which is what the system would otherwise slide in; the flag covers the
 * ordinary path for activities started without options. Applying one without the other leaves
 * half the swap animated, which looks worse than leaving both on.
 *
 * Deliberately not used for Quick Search, device Settings or a share sheet: those are not
 * hand-offs, the disc never runs for them, and an instant cut with nothing covering it reads as a
 * glitch rather than as a transition.
 */
object LaunchTransition {

    /** The options bundle for [Context.startActivity] and `LauncherApps.startShortcut`. */
    fun options(context: Context): Bundle =
        ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()

    /** The intent flag half. Returns the same intent so it can be chained onto a builder. */
    fun Intent.withoutTransition(): Intent = addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
}
