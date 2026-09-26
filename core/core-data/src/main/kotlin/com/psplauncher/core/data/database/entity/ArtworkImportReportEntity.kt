package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artwork_import_reports")
data class ArtworkImportReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val source: String,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "summary_json")
    val summaryJson: String,
)
