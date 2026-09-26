package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable
import com.psplauncher.core.domain.model.HiddenPlacement
import com.psplauncher.core.domain.model.HideLocationType

@Serializable
@Entity(
    tableName = "hidden_placements",
    primaryKeys = ["item_key", "location_type", "location_id"],
    indices = [Index("item_key")],
)
data class HiddenPlacementEntity(
    @ColumnInfo(name = "item_key")
    val itemKey: String,

    @ColumnInfo(name = "item_label")
    val itemLabel: String,

    @ColumnInfo(name = "location_type")
    val locationType: String,

    @ColumnInfo(name = "location_id")
    val locationId: String,

    @ColumnInfo(name = "location_label")
    val locationLabel: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

fun HiddenPlacementEntity.toDomain() = HiddenPlacement(
    itemKey = itemKey,
    itemLabel = itemLabel,
    locationType = runCatching { HideLocationType.valueOf(locationType) }.getOrDefault(HideLocationType.GLOBAL),
    locationId = locationId,
    locationLabel = locationLabel,
    createdAt = createdAt,
)

fun HiddenPlacement.toEntity() = HiddenPlacementEntity(
    itemKey = itemKey,
    itemLabel = itemLabel,
    locationType = locationType.name,
    locationId = locationId,
    locationLabel = locationLabel,
    createdAt = createdAt,
)
