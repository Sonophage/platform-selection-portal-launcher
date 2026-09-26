package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ss_media_cache")
data class SsMediaCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "ss_id")
    val ssId: Long,

    @ColumnInfo(name = "medias_json")
    val mediasJson: String,

    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
)
