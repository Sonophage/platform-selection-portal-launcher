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

/**
 * The launch-disc switch — one boolean for the disc shown between confirming something and it
 * opening.
 *
 * On by default. The disc is not a cosmetic extra: with it off there is no transition at all, and
 * an install that has never seen the setting should get the behaviour the app was built around.
 * It costs a few seconds per launch, which is exactly why the switch exists.
 *
 * **It does not govern games.** Those go through GameBootGate, whose own switch already answers
 * "does a game launch get a presentation", and the disc is what that gate presents. Two switches
 * over one launch is a pair that can contradict itself — turn GameBoot off for games, turn this
 * off for everything else.
 *
 * Lives in core-data beside [MediaLaunchGate], which is the one place that reads it: the gate is
 * the single seam every non-game launch suspends on, so gating there covers books, films, tracks
 * and apps without a fifth call site remembering to ask.
 */
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

        /** The stored boolean, or on when it has never been written. */
        fun resolve(prefs: Preferences): Boolean = prefs[KEY_LAUNCH_DISC_ENABLED] ?: true
    }
}
