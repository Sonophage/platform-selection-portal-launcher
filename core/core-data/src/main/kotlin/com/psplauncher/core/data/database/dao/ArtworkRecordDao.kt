package com.psplauncher.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.psplauncher.core.data.database.entity.ArtworkRecordEntity

@Dao
interface ArtworkRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<ArtworkRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ArtworkRecordEntity)

    @Query("SELECT * FROM artwork_records WHERE game_id = :gameId ORDER BY artwork_type, sort_order")
    suspend fun getForGame(gameId: Long): List<ArtworkRecordEntity>

    @Query("""
        SELECT * FROM artwork_records
        WHERE game_id = :gameId AND artwork_type = :type
        ORDER BY sort_order ASC LIMIT 1
    """)
    suspend fun get(gameId: Long, type: String): ArtworkRecordEntity?

    @Query("SELECT * FROM artwork_records WHERE game_id = :gameId AND artwork_type = :type AND sort_order = :sortOrder")
    suspend fun getAt(gameId: Long, type: String, sortOrder: Int): ArtworkRecordEntity?

    @Query("SELECT * FROM artwork_records WHERE game_id = :gameId AND artwork_type = :type ORDER BY sort_order ASC")
    suspend fun findAll(gameId: Long, type: String): List<ArtworkRecordEntity>

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM artwork_records WHERE game_id = :gameId AND artwork_type = :type")
    suspend fun maxSortOrder(gameId: Long, type: String): Int

    @Query("SELECT COUNT(*) FROM artwork_records WHERE game_id = :gameId AND artwork_type = :type")
    suspend fun countFor(gameId: Long, type: String): Int

    @Query("""
        SELECT * FROM artwork_records
        WHERE platform_id = :platformId AND artwork_type = :type
          AND portable_name = :portableName COLLATE NOCASE AND game_id != :gameId
    """)
    suspend fun findNameCollisions(platformId: String, type: String, portableName: String, gameId: Long): List<ArtworkRecordEntity>

    @Query("""
        SELECT * FROM artwork_records
        WHERE game_id = :gameId AND artwork_type = :type AND provider_asset_id = :providerAssetId
    """)
    suspend fun findByProviderAssetId(gameId: Long, type: String, providerAssetId: String): List<ArtworkRecordEntity>

    @Query("SELECT * FROM artwork_records WHERE game_id = :gameId AND artwork_type = :type AND origin_url = :originUrl")
    suspend fun findByOriginUrl(gameId: Long, type: String, originUrl: String): List<ArtworkRecordEntity>

    @Query("SELECT * FROM artwork_records WHERE game_id = :gameId AND artwork_type = :type AND checksum = :checksum")
    suspend fun findByChecksum(gameId: Long, type: String, checksum: String): List<ArtworkRecordEntity>

    @Query("SELECT * FROM artwork_records")
    suspend fun getAll(): List<ArtworkRecordEntity>

    @Query("DELETE FROM artwork_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM artwork_records WHERE game_id = :gameId AND artwork_type = :type AND sort_order = :sortOrder")
    suspend fun deleteAt(gameId: Long, type: String, sortOrder: Int)

    @Query("UPDATE artwork_records SET sort_order = :sortOrder, updated_at = :updatedAt WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int, updatedAt: Long)

    @Query("UPDATE artwork_records SET crop_profile_key = :key, updated_at = :updatedAt WHERE game_id = :gameId AND artwork_type = :type")
    suspend fun setCropProfileKey(gameId: Long, type: String, key: String?, updatedAt: Long)

    @Query("SELECT crop_profile_key FROM artwork_records WHERE game_id = :gameId AND artwork_type = :type AND crop_profile_key IS NOT NULL LIMIT 1")
    suspend fun cropProfileKey(gameId: Long, type: String): String?

    @Transaction
    suspend fun reorder(gameId: Long, type: String, orderedIds: List<Long>) {
        val current = findAll(gameId, type)
        if (current.isEmpty()) return
        val byId = current.associateBy { it.id }
        val listed = orderedIds.distinct().mapNotNull { byId[it] }
        val rest = current.filter { row -> listed.none { it.id == row.id } }
        val target = listed + rest
        if (target.map { it.id } == current.map { it.id }) return

        val now = System.currentTimeMillis()

        target.forEachIndexed { index, row -> setSortOrder(row.id, -(index + 1), now) }
        target.forEachIndexed { index, row -> setSortOrder(row.id, index, now) }
    }

    @Transaction
    suspend fun deleteAtAndCompact(gameId: Long, type: String, sortOrder: Int) {
        deleteAt(gameId, type, sortOrder)
        val now = System.currentTimeMillis()
        val remaining = findAll(gameId, type)
        remaining.forEachIndexed { index, row -> setSortOrder(row.id, -(index + 1), now) }
        remaining.forEachIndexed { index, row -> setSortOrder(row.id, index, now) }
    }

    @Query("SELECT COUNT(*) FROM artwork_records")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM artwork_records")
    suspend fun totalBytes(): Long

    @Query("DELETE FROM artwork_records WHERE game_id = :gameId")
    suspend fun deleteForGame(gameId: Long)

    @Query("DELETE FROM artwork_records")
    suspend fun clear()
}
