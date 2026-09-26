package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.psplauncher.core.domain.model.MemoryCard

@Serializable
@Entity(tableName = "memory_cards")
data class MemoryCardEntity(
    @PrimaryKey
    @ColumnInfo(name = "platform_id")
    val platformId: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    val enabled: Boolean = true,

    val pinned: Boolean = false,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "tree_uri")
    val treeUri: String? = null,

    @ColumnInfo(name = "rom_directory")
    val romDirectory: String? = null,

    @ColumnInfo(name = "supported_extensions")
    val supportedExtensions: String = "",

    @ColumnInfo(name = "emulator_id")
    val emulatorId: String? = null,

    @ColumnInfo(name = "scan_recursively")
    val scanRecursively: Boolean = true,

    @ColumnInfo(name = "last_scanned_at")
    val lastScannedAt: Long? = null,

    @ColumnInfo(name = "game_count")
    val gameCount: Int = 0,
)

fun MemoryCardEntity.toDomain() = MemoryCard(
    platformId          = platformId,
    displayName         = displayName,
    enabled             = enabled,
    pinned              = pinned,
    sortOrder           = sortOrder,
    treeUri             = treeUri,
    romDirectory        = romDirectory,
    supportedExtensions = supportedExtensions.split(",").map { it.trim() }.filter { it.isNotBlank() },
    emulatorId          = emulatorId,
    scanRecursively     = scanRecursively,
    lastScannedAt       = lastScannedAt,
    gameCount           = gameCount,
)

fun MemoryCard.toEntity() = MemoryCardEntity(
    platformId          = platformId,
    displayName         = displayName,
    enabled             = enabled,
    pinned              = pinned,
    sortOrder           = sortOrder,
    treeUri             = treeUri,
    romDirectory        = romDirectory,
    supportedExtensions = supportedExtensions.joinToString(","),
    emulatorId          = emulatorId,
    scanRecursively     = scanRecursively,
    lastScannedAt       = lastScannedAt,
    gameCount           = gameCount,
)
