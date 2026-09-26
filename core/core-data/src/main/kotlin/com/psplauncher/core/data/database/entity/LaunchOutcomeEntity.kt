package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "launch_outcomes",
    indices = [
        Index("game_id"),
        Index("platform_id"),
    ],
)
data class LaunchOutcomeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "game_id")
    val gameId: Long,

    @ColumnInfo(name = "game_title")
    val gameTitle: String,

    @ColumnInfo(name = "platform_id")
    val platformId: String?,

    @ColumnInfo(name = "emulator_id")
    val emulatorId: String?,

    @ColumnInfo(name = "emulator_name")
    val emulatorName: String?,

    @ColumnInfo(name = "core_path")
    val corePath: String?,

    @ColumnInfo(name = "core_name")
    val coreName: String?,

    @ColumnInfo(name = "source")
    val source: String?,

    @ColumnInfo(name = "outcome")
    val outcome: String,

    @ColumnInfo(name = "failure_reason")
    val failureReason: String?,

    @ColumnInfo(name = "launched_at_ms")
    val launchedAtMs: Long,

    @ColumnInfo(name = "returned_at_ms")
    val returnedAtMs: Long? = null,
)
