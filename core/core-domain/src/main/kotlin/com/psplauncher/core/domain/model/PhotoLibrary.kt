package com.psplauncher.core.domain.model

data class PhotoLibrary(
    val id: String,
    val displayName: String,
    val treeUri: String,
    val enabled: Boolean = true,
    val scanRecursively: Boolean = true,
    val photoCount: Int = 0,
    val lastScannedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
