package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.ui.components.LetterAnchor
import com.psplauncher.core.ui.components.LetterJumpState
import com.psplauncher.core.ui.components.letterAnchors as anchorsOfTitles
import com.psplauncher.core.ui.components.letterJumpFor as jumpForTitles

internal fun letterAnchors(items: List<XMBItem>): List<LetterAnchor>? =
    anchorsOfTitles(items.map { it.title })

internal fun letterJumpFor(items: List<XMBItem>, currentIndex: Int): LetterJumpState? =
    jumpForTitles(items.map { it.title }, currentIndex)
