package com.psplauncher.core.ui.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.Modifier

fun Modifier.dragToScroll(state: ScrollableState?): Modifier =
    if (state == null) this
    else scrollable(state = state, orientation = Orientation.Vertical, reverseDirection = true)
