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

/**
 * Whether the launcher loops its own music while it is on screen.
 *
 * OFF by default, unlike every other presentation switch in the app, and for a different reason
 * than caution: there is no bundled track for [com.psplauncher.core.domain.model.UiMediaSlot
 * .MENU_MUSIC], so on by default would be a switch that does nothing until a file is assigned —
 * a control that lies about its own state. Off says what is true.
 */
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

        /** The stored boolean, or off when it has never been written. */
        fun resolve(prefs: Preferences): Boolean = prefs[KEY_MENU_MUSIC_ENABLED] ?: false
    }
}
