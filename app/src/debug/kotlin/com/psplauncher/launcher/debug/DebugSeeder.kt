package com.psplauncher.launcher.debug

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.core.data.database.entity.toEntity
import com.psplauncher.core.data.datastore.pfpDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_DEBUG_SEEDED = booleanPreferencesKey("debug_seeded_v1")

@Singleton
class DebugSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val debugController: DebugController,
) {
    suspend fun seedIfNeeded() {
        val prefs = context.pfpDataStore.data.first()
        if (prefs[KEY_DEBUG_SEEDED] == true) {
            Timber.d("Debug data already seeded — skipping")
            return
        }

        val games = DebugGameFactory.gamesForScenario(DebugScenario.FULL_LIBRARY)
        gameDao.insertAll(games.map { it.toEntity() })

        context.pfpDataStore.edit { it[KEY_DEBUG_SEEDED] = true }
        Timber.i("Debug seeder: inserted ${games.size} fake games into Room DB")
    }

    suspend fun reseed(scenario: DebugScenario) {
        Timber.i("Debug reseed: clearing games, seeding scenario=${scenario.name}")

        val games = DebugGameFactory.gamesForScenario(scenario)
        gameDao.insertAll(games.map { it.toEntity() })

        context.pfpDataStore.edit { it[KEY_DEBUG_SEEDED] = true }
        debugController.setScenario(scenario)

        Timber.i("Debug reseed complete: ${games.size} games for scenario ${scenario.name}")
    }
}
