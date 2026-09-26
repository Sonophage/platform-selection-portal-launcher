package com.psplauncher.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.psplauncher.core.data.repository.RomRootRepository

@Immutable
data class RootFolderRow(
    val treeUri: String,
    val name: String,
    val linked: Boolean,

    val consoles: String? = null,
)

fun rootDisplayName(treeUri: String): String =
    RomRootRepository.rawPathOfTree(treeUri)
        ?: treeUri.substringAfterLast('/').ifBlank { treeUri }
