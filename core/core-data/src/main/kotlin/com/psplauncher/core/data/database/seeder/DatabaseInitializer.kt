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
// One-shot: Last Played shipped appended to the end of the bar and was given its home left of
// Game hours later. Flag-guarded rather than condition-guarded so a user who afterwards moves
// it somewhere else keeps that choice.
private val KEY_LAST_PLAYED_PLACED = booleanPreferencesKey("last_played_placed_v1")
// One-shot: the Network column was seeded as "Online" on older installs, and reconciliation never
// updates a name because a name is user-editable. Flag-guarded so that renaming it yourself
// afterwards — back to "Online" or to anything else — sticks.
private val KEY_NETWORK_RENAMED = booleanPreferencesKey("network_renamed_v1")
// One-shot: the Android library is app-based, so no scan or import ever creates its Memory Card
// and it has to be seeded. Flag-guarded for the same reason as the rename above -- DELETING it
// yourself afterwards has to stick. It did not: the card was created from LibraryManagerViewModel's
// init block, so removing it and reopening Library Manager brought it straight back, and the
// comment on startAddConsole ("the only way back when the auto-created card is removed") described
// a removal that was never possible.
private val KEY_ANDROID_CARD_SEEDED = booleanPreferencesKey("android_card_seeded_v1")

/** The Android library's platform id, and the card seeded for it exactly once. */

/** What [DatabaseInitializer.seedAndroidCard] should do on this launch. */
internal enum class AndroidCardSeed { CREATE_AND_MARK, MARK_ONLY, NOTHING }

/**
 * The seeding decision, as a pure function, because the subtle half is easy to get wrong and
 * impossible to see: an install that already HAS the card from the old always-create behaviour
 * must still get the flag written, or the one-shot never retires and the card comes back the
 * first time the user deletes it. That is the reported bug, one step removed.
 */
internal fun androidCardSeedAction(alreadySeeded: Boolean, cardExists: Boolean): AndroidCardSeed =
    when {
        alreadySeeded -> AndroidCardSeed.NOTHING
        cardExists -> AndroidCardSeed.MARK_ONLY
        else -> AndroidCardSeed.CREATE_AND_MARK
    }

/** Built-in theme seeded separately from the main DB seed so it can be added to existing installs. */
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
    // Called once from PFPApplication after DI is ready.
    // Safe to call multiple times — guarded by DataStore flags and INSERT OR IGNORE.
    suspend fun initialize() {
        // Runs every launch (INSERT OR IGNORE): built-in platforms added in an app update reach
        // databases seeded by older builds too. Only inserts missing IDs — never overwrites a
        // user's platform customizations (pinned/preferred-emulator/etc.).
        platformSeeder.seed()
        seedMainDb()
        // Runs every launch (not gated by DB_SEEDED): corrects system-defined flags on
        // built-in categories so definition changes reach databases seeded by older builds.
        categoryRepository.reconcileBuiltInCategories()
        placeLastPlayed()
        renameNetworkColumn()
        seedAndroidCard()
        seedThemes()
        // One-shot v22 follow-up (flag-guarded): the Windows-card consolidation steps that
        // need app logic — spoof-package label checks, duplicate merge, card creation.
        libraryConsolidation.run()
    }

    private suspend fun seedMainDb() {
        val prefs = context.pfpDataStore.data.first()
        if (prefs[KEY_DB_SEEDED] == true) {
            Timber.d("DB already seeded — skipping")
            return
        }

        Timber.i("First launch — seeding database")
        // Platforms are seeded every launch in initialize(); here we only do the one-shot
        // first-launch category seed and set the DB_SEEDED flag.
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

    /**
     * Creates the Android Memory Card once, ever.
     *
     * Once, not "when missing": the card is the user's to delete. The flag is what makes a
     * delete stick, exactly as KEY_NETWORK_RENAMED makes a rename stick. Library Manager's Add
     * Console list keeps Android selectable, which is the way back.
     *
     * The existence check is still here for the install that already has one from the old
     * behaviour — it would otherwise get a duplicate-key upsert on the next launch.
     */
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
