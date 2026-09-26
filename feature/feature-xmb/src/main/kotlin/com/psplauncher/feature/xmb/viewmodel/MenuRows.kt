package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.ui.components.MenuState
import com.psplauncher.core.ui.components.rowsShown

internal fun XMBUiState.menuWithPills(): MenuState<String>? =
    activeContextMenu?.state?.copy(withheld = focusedPills().map { it.id }.toSet())

fun XMBUiState.menuRows(): List<XMBContextMenuItem> = menuWithPills()?.rowsShown().orEmpty()
