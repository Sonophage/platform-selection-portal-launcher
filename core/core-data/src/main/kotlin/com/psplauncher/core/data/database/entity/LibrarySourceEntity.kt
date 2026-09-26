package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_sources")
data class LibrarySourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val path: String,

    val label: String,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    @ColumnInfo(name = "last_scanned_at")
    val lastScannedAt: Long? = null,

    @ColumnInfo(name = "game_count")
    val gameCount: Int = 0,

    @ColumnInfo(name = "platform_id")
    val platformId: String? = null,
)
