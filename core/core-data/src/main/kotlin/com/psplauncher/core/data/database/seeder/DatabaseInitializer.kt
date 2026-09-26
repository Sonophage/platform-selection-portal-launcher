package com.psplauncher.core.data.database.seeder

import com.psplauncher.core.domain.model.PlatformIds.ANDROID as ANDROID_PLATFORM_ID

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.psplauncher.core.data.database.dao.ThemeDao
import com.psplauncher.core.data.database.entity.MemoryCardEntity
import com.psplauncher.core.data.database.entity.ThemeEntity
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.data.repository.CategoryRepositoryImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_DB_SEEDED     = booleanPreferencesKey("db_seeded_v1")
private val KEY_THEMES_SEEDED = booleanPreferencesKey("themes_seeded_v1")

private val KEY_LAST_PLAYED_PLACED = booleanPreferencesKey("last_played_placed_v1")

private val KEY_NETWORK_RENAMED = booleanPreferencesKey("network_renamed_v1")

private val KEY_ANDROID_CARD_SEEDED = booleanPreferencesKey("android_card_seeded_v1")

internal enum class AndroidCardSeed { CREATE_AND_MARK, MARK_ONLY, NOTHING }

internal fun androidCardSeedAction(alreadySeeded: Boolean, cardExists: Boolean): AndroidCardSeed =
    when {
        alreadySeeded -> AndroidCardSeed.NOTHING
        cardExists -> AndroidCardSeed.MARK_ONLY
        else -> AndroidCardSeed.CREATE_AND_MARK
    }

private val BUILTIN_CLASSIC_BLUE = ThemeEntity(
    id               = "builtin_classic_blue",
    name             = "Classic Blue",
    author           = "PSPLauncher",
    version          = "1.0",
    waveColor        = 0xFF0055AAL,
    waveOpacity      = 0.7f,
    waveSpeed        = 1.0f,
    waveAmplitude    = 1.0f,
    accentColor      = 0xFFFFFFFFL,
    textColor        = 0xFFFFFFFFL,
    backgroundUri    = null,
    fontKey          = "system_default",
    hasBootAnimation = false,
    bootAnimationUri = null,
    soundPackUri     = null,
    packagePath      = null,
    isBuiltIn        = true,
    isActive         = true,
)

@Singleton
class DatabaseInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val platformSeeder: PlatformSeeder,
    private val categoryRepository: CategoryRepositoryImpl,
    private val themeDao: ThemeDao,
    private val libraryConsolidation: LibraryConsolidation,
    private val memoryCardDao: com.psplauncher.core.data.database.dao.MemoryCardDao,
) {
    suspend fun initialize() {
        platformSeeder.seed()
        seedMainDb()

        categoryRepository.reconcileBuiltInCategories()
        placeLastPlayed()
        renameNetworkColumn()
        seedAndroidCard()
        seedThemes()

        libraryConsolidation.run()
    }

    private suspend fun seedMainDb() {
        val prefs = context.pfpDataStore.data.first()
        if (prefs[KEY_DB_SEEDED] == true) {
            Timber.d("DB already seeded — skipping")
            return
        }

        Timber.i("First launch — seeding database")

        categoryRepository.seedBuiltInCategories()

        context.pfpDataStore.edit { it[KEY_DB_SEEDED] = true }
        Timber.i("Database seed complete")
    }

    private suspend fun placeLastPlayed() {
        val prefs = context.pfpDataStore.data.first()
        if (prefs[KEY_LAST_PLAYED_PLACED] == true) return
        categoryRepository.placeLastPlayedBeforeGames()
        context.pfpDataStore.edit { it[KEY_LAST_PLAYED_PLACED] = true }
    }

    private suspend fun renameNetworkColumn() {
        val prefs = context.pfpDataStore.data.first()
        if (prefs[KEY_NETWORK_RENAMED] == true) return
        categoryRepository.renameStaleOnlineColumn()
        context.pfpDataStore.edit { it[KEY_NETWORK_RENAMED] = true }
    }

    private suspend fun seedAndroidCard() {
        val prefs = context.pfpDataStore.data.first()
        val action = androidCardSeedAction(
            alreadySeeded = prefs[KEY_ANDROID_CARD_SEEDED] == true,
            cardExists = memoryCardDao.getById(ANDROID_PLATFORM_ID) != null,
        )
        if (action == AndroidCardSeed.NOTHING) return
        if (action == AndroidCardSeed.CREATE_AND_MARK) {
            memoryCardDao.upsert(
                MemoryCardEntity(
                    platformId  = ANDROID_PLATFORM_ID,
                    displayName = "Android Memory Card",
                    enabled     = true,
                    sortOrder   = memoryCardDao.maxSortOrder() + 1,
                    gameCount   = 0,
                )
            )
            Timber.i("Android Memory Card seeded")
        }
        context.pfpDataStore.edit { it[KEY_ANDROID_CARD_SEEDED] = true }
    }

    private suspend fun seedThemes() {
        val prefs = context.pfpDataStore.data.first()
        if (prefs[KEY_THEMES_SEEDED] == true) {
            Timber.d("Themes already seeded — skipping")
            return
        }

        Timber.i("Seeding built-in themes")
        themeDao.insertAll(listOf(BUILTIN_CLASSIC_BLUE))

        context.pfpDataStore.edit { it[KEY_THEMES_SEEDED] = true }
        Timber.i("Theme seed complete")
    }
}
