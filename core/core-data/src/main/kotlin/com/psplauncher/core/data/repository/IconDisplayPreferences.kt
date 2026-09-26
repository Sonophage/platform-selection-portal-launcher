package com.psplauncher.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.domain.model.IconDisplayMode
import com.psplauncher.core.domain.model.VideoSnapPlacement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IconDisplayPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val modeFlow: Flow<IconDisplayMode> = context.pfpDataStore.data
        .map { IconDisplayMode.fromName(it[KEY_MODE]) ?: IconDisplayMode.DEFAULT }

    suspend fun setMode(mode: IconDisplayMode) =
        context.pfpDataStore.edit { it[KEY_MODE] = mode.name }

    val platformModesFlow: Flow<Map<String, IconDisplayMode>> = context.pfpDataStore.data
        .map { decodePlatformModes(it[KEY_PLATFORM_MODES]) }

    suspend fun setPlatformMode(platformId: String, mode: IconDisplayMode?) =
        context.pfpDataStore.edit { prefs ->
            val updated = withPlatformMode(decodePlatformModes(prefs[KEY_PLATFORM_MODES]), platformId, mode)
            prefs[KEY_PLATFORM_MODES] = encodePlatformModes(updated)
        }

    val animatedIconsFlow: Flow<Boolean> = context.pfpDataStore.data
        .map { it[KEY_ANIMATED_ICONS] ?: true }

    suspend fun setAnimatedIcons(enabled: Boolean) =
        context.pfpDataStore.edit { it[KEY_ANIMATED_ICONS] = enabled }

    val snapPlacementFlow: Flow<VideoSnapPlacement> = context.pfpDataStore.data
        .map { VideoSnapPlacement.fromName(it[KEY_SNAP_PLACEMENT]) ?: VideoSnapPlacement.DEFAULT }

    suspend fun setSnapPlacement(placement: VideoSnapPlacement) =
        context.pfpDataStore.edit { it[KEY_SNAP_PLACEMENT] = placement.name }

    val gameMetadataFlow: Flow<Boolean> = context.pfpDataStore.data
        .map { it[KEY_GAME_METADATA] ?: true }

    suspend fun setGameMetadata(enabled: Boolean) =
        context.pfpDataStore.edit { it[KEY_GAME_METADATA] = enabled }

    val itemBackdropFlow: Flow<Boolean> = context.pfpDataStore.data
        .map { it[KEY_ITEM_BACKDROP] ?: true }

    suspend fun setItemBackdrop(enabled: Boolean) =
        context.pfpDataStore.edit { it[KEY_ITEM_BACKDROP] = enabled }

    val lingerDelaySecondsFlow: Flow<Float> = context.pfpDataStore.data
        .map { (it[KEY_ICON1_LINGER_DELAY_SECONDS] ?: 1.5f).coerceIn(1f, 5f) }

    suspend fun setLingerDelaySeconds(seconds: Float) =
        context.pfpDataStore.edit { it[KEY_ICON1_LINGER_DELAY_SECONDS] = seconds.coerceIn(1f, 5f) }

    companion object {
        private val KEY_MODE = stringPreferencesKey("pref_icon_display_mode")
        private val KEY_PLATFORM_MODES = stringPreferencesKey("pref_icon_display_mode_by_platform")
        private val KEY_ANIMATED_ICONS = androidx.datastore.preferences.core.booleanPreferencesKey("pref_animated_icons")
        private val KEY_ICON1_LINGER_DELAY_SECONDS =
            floatPreferencesKey("pref_icon1_linger_delay_seconds")
        private val KEY_SNAP_PLACEMENT = stringPreferencesKey("pref_video_snap_placement")
        private val KEY_GAME_METADATA =
            androidx.datastore.preferences.core.booleanPreferencesKey("pref_xmb_game_metadata")
        private val KEY_ITEM_BACKDROP =
            androidx.datastore.preferences.core.booleanPreferencesKey("pref_xmb_item_backdrop")

        fun encodePlatformModes(modes: Map<String, IconDisplayMode>): String =
            modes.entries.joinToString("\n") { "${it.key}=${it.value.name}" }

        fun decodePlatformModes(encoded: String?): Map<String, IconDisplayMode> =
            encoded.orEmpty().lineSequence().mapNotNull { line ->
                val platformId = line.substringBefore('=', "").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val mode = IconDisplayMode.fromName(line.substringAfter('=', ""))
                    ?: return@mapNotNull null
                platformId to mode
            }.toMap()

        fun withPlatformMode(
            modes: Map<String, IconDisplayMode>,
            platformId: String,
            mode: IconDisplayMode?,
        ): Map<String, IconDisplayMode> =
            if (mode == null) modes - platformId else modes + (platformId to mode)
    }
}
