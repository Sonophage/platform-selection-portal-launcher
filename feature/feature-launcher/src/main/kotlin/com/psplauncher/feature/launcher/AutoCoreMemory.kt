package com.psplauncher.feature.launcher

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psplauncher.core.data.datastore.pfpDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoCoreMemory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun rememberedProfileId(platformId: String): String? = rememberedIds()[platformId]

    suspend fun rememberedIds(): Map<String, String> = decode(context.pfpDataStore.data.first()[KEY])

    suspend fun remember(platformId: String, profileId: String) {
        runCatching {
            context.pfpDataStore.edit { prefs ->
                prefs[KEY] = json.encodeToString(decode(prefs[KEY]) + (platformId to profileId))
            }
        }.onFailure { Timber.w(it, "Could not remember auto core for platform $platformId") }
    }

    private fun decode(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, String>>(raw)
        }.getOrElse {
            Timber.w(it, "Corrupt auto-core memory ignored (treating as empty)")
            emptyMap()
        }
    }

    companion object {
        private val KEY = stringPreferencesKey("auto_resolved_cores")
    }
}
