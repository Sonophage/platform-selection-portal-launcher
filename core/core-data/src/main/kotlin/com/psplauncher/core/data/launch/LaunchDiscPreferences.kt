package com.psplauncher.core.data.launch

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.psplauncher.core.data.datastore.pfpDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class LaunchDiscPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val launchDiscEnabledFlow: Flow<Boolean> = context.pfpDataStore.data.map { resolve(it) }

    suspend fun setLaunchDiscEnabled(enabled: Boolean) = context.pfpDataStore.edit {
        it[KEY_LAUNCH_DISC_ENABLED] = enabled
    }

    companion object {
        private val KEY_LAUNCH_DISC_ENABLED = booleanPreferencesKey("display_launch_disc")

        fun resolve(prefs: Preferences): Boolean = prefs[KEY_LAUNCH_DISC_ENABLED] ?: true
    }
}
