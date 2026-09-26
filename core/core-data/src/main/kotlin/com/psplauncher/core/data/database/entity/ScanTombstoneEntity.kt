package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_tombstones")
data class ScanTombstoneEntity(
    @PrimaryKey
    @ColumnInfo(name = "rom_path")
    val romPath: String,

    @ColumnInfo(name = "platform_id")
    val platformId: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
