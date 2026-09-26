package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "category_items",
    primaryKeys = ["category_id", "item_id"],
    foreignKeys = [
        ForeignKey(
            entity        = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns  = ["category_id"],
            onDelete      = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("category_id"), Index("item_id")],
)
data class CategoryItemEntity(
    @ColumnInfo(name = "category_id")
    val categoryId: String,

    @ColumnInfo(name = "item_id")
    val itemId: String,

    @ColumnInfo(name = "item_type")
    val itemType: String,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    val pinned: Boolean = false,
)
