package com.psplauncher.core.domain.model

data class VideoLibrary(
    val id: String,
    val displayName: String,
    val treeUri: String,

    val artworkUri: String? = null,
    val enabled: Boolean = true,
    val scanRecursively: Boolean = true,
    val videoCount: Int = 0,
    val lastScannedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
