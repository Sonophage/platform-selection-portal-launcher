package com.psplauncher.core.domain.model

data class GameCollection(
    val id: Long = 0,
    val name: String,
    val categoryId: String = "games",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,

    val gameCount: Int = 0,

    val iconKey: String? = null,
)
