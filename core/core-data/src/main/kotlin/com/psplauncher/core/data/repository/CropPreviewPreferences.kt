package com.psplauncher.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.psplauncher.core.data.datastore.pfpDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class CropPreviewPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val enabledFlow: Flow<Boolean> = context.pfpDataStore.data
        .map { it[KEY_CROP_PREVIEW_ENABLED] ?: DEFAULT_ENABLED }

    suspend fun setEnabled(enabled: Boolean) = context.pfpDataStore.edit {
        it[KEY_CROP_PREVIEW_ENABLED] = enabled
    }

    companion object {
        private val KEY_CROP_PREVIEW_ENABLED = booleanPreferencesKey("artwork_crop_preview_enabled")

        const val DEFAULT_ENABLED = true
    }
}
