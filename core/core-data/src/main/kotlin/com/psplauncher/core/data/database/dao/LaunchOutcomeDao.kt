package com.psplauncher.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.psplauncher.core.data.database.entity.LaunchOutcomeEntity

@Dao
interface LaunchOutcomeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(outcome: LaunchOutcomeEntity): Long

    @Query(
        "SELECT * FROM launch_outcomes WHERE game_id = :gameId " +
            "ORDER BY launched_at_ms DESC LIMIT :limit"
    )
    suspend fun recentForGame(gameId: Long, limit: Int): List<LaunchOutcomeEntity>

    @Query(
        "SELECT * FROM launch_outcomes WHERE platform_id = :platformId " +
            "ORDER BY launched_at_ms DESC LIMIT :limit"
    )
    suspend fun recentForPlatform(platformId: String, limit: Int): List<LaunchOutcomeEntity>

    @Query(
        "SELECT * FROM launch_outcomes WHERE game_id = :gameId AND outcome != 'SUCCEEDED' " +
            "ORDER BY launched_at_ms DESC LIMIT :limit"
    )
    suspend fun recentFailuresForGame(gameId: Long, limit: Int): List<LaunchOutcomeEntity>
}
