package com.psplauncher.core.domain.model

data class MusicFolder(
    val id: String,
    val displayName: String,
    val treeUri: String,
    val enabled: Boolean = true,
    val trackCount: Int = 0,
    val lastScannedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
