package com.psplauncher.core.ui.components

import androidx.compose.ui.unit.dp

// ── The two bands the app's chrome owns ───────────────────────────────────────
//
// The status strip at the top and the hint bar at the bottom are drawn ONCE, by the shell, over
// whatever screen is up. Every screen that keeps them has to leave room for them, and it cannot do
// that with a number of its own: a screen holding its own copy of 34 is a screen that goes on
// reserving 34 after the strip grows.
//
// They live in core-ui rather than beside the strip because the screens that reserve the room are
// in four different modules — feature-appbar cannot see feature-xmb — which is the same reason the
// hint bar itself had to move here.

/** The band the status strip occupies at the top of every screen that keeps it. */
val StatusStripHeight = 34.dp

/** The band the hint bar occupies at the bottom of every screen that draws one. */
val HintBarHeight = 34.dp
