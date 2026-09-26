package com.psplauncher.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.psplauncher.core.data.database.entity.SsMediaCacheEntity

@Dao
interface SsMediaCacheDao {
    @Query("SELECT * FROM ss_media_cache WHERE ss_id = :ssId")
    suspend fun get(ssId: Long): SsMediaCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SsMediaCacheEntity)

    @Query("DELETE FROM ss_media_cache WHERE ss_id = :ssId")
    suspend fun delete(ssId: Long)

    @Query("DELETE FROM ss_media_cache")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM ss_media_cache")
    suspend fun count(): Int
}
