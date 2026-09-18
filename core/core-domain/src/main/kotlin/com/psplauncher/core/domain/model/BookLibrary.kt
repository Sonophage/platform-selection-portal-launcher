package com.psplauncher.core.domain.model

/**
 * A user-added book source: a SAF document-tree URI the user granted read access to. Mirrors
 * [PhotoLibrary] (SAF-only, manual scan). Books are discovered by a manual scan and stored as
 * [Book] rows.
 */
data class BookLibrary(
    val id: String,
    val displayName: String,
    val treeUri: String,
    val enabled: Boolean = true,
    val scanRecursively: Boolean = true,
    val bookCount: Int = 0,
    val lastScannedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
