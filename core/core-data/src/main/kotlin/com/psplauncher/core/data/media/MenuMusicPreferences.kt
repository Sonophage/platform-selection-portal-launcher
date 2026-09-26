package com.psplauncher.core.data.media

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
class MenuMusicPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val enabledFlow: Flow<Boolean> = context.pfpDataStore.data.map { resolve(it) }

    suspend fun setEnabled(enabled: Boolean) = context.pfpDataStore.edit {
        it[KEY_MENU_MUSIC_ENABLED] = enabled
    }

    companion object {
        private val KEY_MENU_MUSIC_ENABLED = booleanPreferencesKey("sound_menu_music")

        fun resolve(prefs: Preferences): Boolean = prefs[KEY_MENU_MUSIC_ENABLED] ?: false
    }
}
