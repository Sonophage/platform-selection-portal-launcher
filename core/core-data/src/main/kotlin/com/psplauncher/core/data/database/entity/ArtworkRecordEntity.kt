package com.psplauncher.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artwork_records",
    indices = [

        Index("game_id", "artwork_type", "sort_order", unique = true),

        Index("platform_id", "artwork_type", "portable_name"),
    ],
)
data class ArtworkRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "game_id")
    val gameId: Long,

    @ColumnInfo(name = "platform_id")
    val platformId: String,

    @ColumnInfo(name = "artwork_type")
    val artworkType: String,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "portable_name")
    val portableName: String,

    @ColumnInfo(name = "relative_path")
    val relativePath: String,

    @ColumnInfo(name = "document_uri")
    val documentUri: String,

    val source: String,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long = 0,

    val width: Int? = null,
    val height: Int? = null,
    val checksum: String? = null,

    @ColumnInfo(name = "user_assigned")
    val userAssigned: Boolean = false,

    val locked: Boolean = false,

    @ColumnInfo(name = "origin_url")
    val originUrl: String? = null,

    @ColumnInfo(name = "provider")
    val provider: String? = null,

    @ColumnInfo(name = "provider_asset_id")
    val providerAssetId: String? = null,

    @ColumnInfo(name = "prev_document_uri")
    val prevDocumentUri: String? = null,

    @ColumnInfo(name = "prev_relative_path")
    val prevRelativePath: String? = null,

    @ColumnInfo(name = "prev_size_bytes")
    val prevSizeBytes: Long = 0,

    @ColumnInfo(name = "crop_rect")
    val cropRect: String? = null,

    @ColumnInfo(name = "has_original")
    val hasOriginal: Boolean = false,

    @ColumnInfo(name = "crop_profile_key")
    val cropProfileKey: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
