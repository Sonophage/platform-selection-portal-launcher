package com.psplauncher.feature.launcher

import android.content.Context
import android.content.pm.PackageManager
import com.psplauncher.core.domain.model.EmulatorProfile
import com.psplauncher.core.domain.model.EmulatorProfileAdmission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProfileIoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object EmulatorProfileModule {
    @Provides
    @ProfileIoDispatcher
    fun provideProfileIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Singleton
class EmulatorProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ProfileIoDispatcher private val io: CoroutineDispatcher,
    private val autoCoreMemory: AutoCoreMemory,
) {
    private val _profiles = MutableStateFlow<List<EmulatorProfile>>(emptyList())
    val profiles: Flow<List<EmulatorProfile>> = _profiles.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun initialize() {
        val bundled  = withContext(io) { loadBundledProfiles() }
        val persisted = withContext(io) { loadPersistedProfiles() }
        _profiles.value = mergeProfiles(bundled, persisted)
        Timber.i("Emulator profiles loaded: ${bundled.size} bundled, ${persisted.size} persisted")
    }

    suspend fun getAllPersistedProfiles(): List<EmulatorProfile> = withContext(io) { loadPersistedProfiles() }

    fun getInstalledProfiles(): List<EmulatorProfile> {
        val pm = context.packageManager
        return _profiles.value.filter { profile ->
            try { pm.getPackageInfo(profile.packageName, 0); true }
            catch (_: PackageManager.NameNotFoundException) { false }
        }
    }

    suspend fun getProfilesForPlatform(platformId: String): List<EmulatorProfile> = withContext(io) {
        getInstalledProfiles()
            .filter { it.isAvailable && it.supportsPlatform(platformId) }
            .byLaunchPreference()
            .stabilizeCore(autoCoreMemory.rememberedProfileId(platformId))
    }

    fun getInstalledVersionCode(packageName: String): Long {
        return try {
            context.packageManager.getPackageInfo(packageName, 0).longVersionCode
        } catch (_: Exception) { -1L }
    }

    suspend fun saveCustomProfile(profile: EmulatorProfile) =
        savePersistedProfile(profile.copy(isCustom = true))

    suspend fun savePersistedProfile(profile: EmulatorProfile) = withContext(io) {
        val current = loadPersistedProfiles().toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) current[idx] = profile else current.add(profile)
        _profiles.value = mergeProfiles(loadBundledProfiles(), current)
        persistProfiles(current)
    }

    suspend fun deleteCustomProfile(id: String) = withContext(io) {
        val current = loadPersistedProfiles().filter { it.id != id }
        _profiles.value = mergeProfiles(loadBundledProfiles(), current)
        persistProfiles(current)
    }

    suspend fun resetPersistedProfiles() = withContext(io) {
        try {
            val file = java.io.File(context.filesDir, "emulator_profiles/custom_profiles.json")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete persisted profiles during reset")
        }
        _profiles.value = loadBundledProfiles()
        Timber.i("Emulator profiles reset to bundled defaults")
    }

    private fun mergeProfiles(
        bundled: List<EmulatorProfile>,
        persisted: List<EmulatorProfile>,
    ): List<EmulatorProfile> {
        val persistedIds = persisted.map { it.id }.toSet()
        return bundled.filter { it.id !in persistedIds } + persisted
    }

    private fun loadBundledProfiles(): List<EmulatorProfile> {
        return try {
            val jsonStr = context.assets
                .open("emulator_profiles/bundled_profiles.json")
                .bufferedReader()
                .readText()
            json.decodeFromString<List<EmulatorProfile>>(jsonStr)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load bundled emulator profiles")
            emptyList()
        }
    }

    private fun loadPersistedProfiles(): List<EmulatorProfile> {
        val parsed = try {
            val file = java.io.File(context.filesDir, "emulator_profiles/custom_profiles.json")
            if (!file.exists()) return emptyList()
            json.decodeFromString<List<EmulatorProfile>>(file.readText())
        } catch (e: Exception) {
            Timber.e(e, "Failed to load persisted emulator profiles")
            return emptyList()
        }

        val admitted = EmulatorProfileAdmission.admit(parsed, selfPackage = context.packageName)
        admitted.refused.forEach {
            Timber.w("Ignoring inadmissible persisted emulator profile %s: %s", it.id, it.reason)
        }
        return admitted.admitted
    }

    private fun persistProfiles(profiles: List<EmulatorProfile>) {
        try {
            val dir  = java.io.File(context.filesDir, "emulator_profiles")
            dir.mkdirs()
            val file = java.io.File(dir, "custom_profiles.json")
            file.writeText(json.encodeToString(ListSerializer(EmulatorProfile.serializer()), profiles))
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist emulator profiles")
        }
    }
}
