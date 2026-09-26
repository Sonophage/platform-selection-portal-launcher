package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.psplauncher.core.domain.model.GameCollection

@Serializable
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "category_id")
    val categoryId: String = "games",

    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "icon_key")
    val iconKey: String? = null,
)

fun CollectionEntity.toDomain(gameCount: Int = 0) = GameCollection(
    id        = id,
    name      = name,
    categoryId = categoryId,
    isPinned  = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sortOrder = sortOrder,
    gameCount = gameCount,
    iconKey   = iconKey,
)

fun GameCollection.toEntity() = CollectionEntity(
    id        = id,
    name      = name,
    categoryId = categoryId,
    isPinned  = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sortOrder = sortOrder,
    iconKey   = iconKey,
)
