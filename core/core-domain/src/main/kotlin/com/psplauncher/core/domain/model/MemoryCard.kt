package com.psplauncher.core.domain.model

data class MemoryCard(
    val platformId: String,
    val displayName: String,
    val enabled: Boolean = true,
    val pinned: Boolean = false,
    val sortOrder: Int = 0,

    val treeUri: String? = null,
    val romDirectory: String? = null,
    val supportedExtensions: List<String> = emptyList(),
    val emulatorId: String? = null,
    val scanRecursively: Boolean = true,
    val lastScannedAt: Long? = null,
    val gameCount: Int = 0,
)
