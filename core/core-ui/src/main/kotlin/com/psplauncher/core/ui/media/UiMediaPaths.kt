package com.psplauncher.core.ui.media

import com.psplauncher.core.domain.model.UiMediaSlot
import kotlinx.coroutines.flow.Flow

interface UiMediaPaths {
    fun pathFor(slot: UiMediaSlot): String?

    val stamp: Flow<Long>

    val menuSoundsEnabled: Flow<Boolean>
}
