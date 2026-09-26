package com.psplauncher.core.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.domain.model.UiMediaKind
import com.psplauncher.core.domain.model.UiMediaSlot
import com.psplauncher.core.ui.media.UiMediaPaths
import com.psplauncher.themekit.UiMediaLimits
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class UiMediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : UiMediaPaths {
    data class ImportResult(val ok: Boolean, val message: String? = null)

    private val dir = File(context.filesDir, UI_MEDIA_DIR)

    override fun pathFor(slot: UiMediaSlot): String? {
        if (!UiMediaSlot.isValidKey(slot.key)) return null
        return dir.listFiles { f -> f.isFile }
            ?.firstOrNull { it.nameWithoutExtension == slot.key }
            ?.absolutePath
    }

    override val stamp: Flow<Long> = context.pfpDataStore.data.map { prefs ->
        prefs[KEY_UI_MEDIA_STAMP] ?: 0L
    }

    override val menuSoundsEnabled: Flow<Boolean> = context.pfpDataStore.data.map { prefs ->
        prefs[KEY_MENU_SOUNDS_ENABLED] ?: true
    }

    fun assignments(): Map<UiMediaSlot, String> {
        val files = dir.listFiles { f -> f.isFile } ?: return emptyMap()
        val out = HashMap<UiMediaSlot, String>()
        for (file in files) {
            val slot = UiMediaSlot.fromKey(file.nameWithoutExtension) ?: continue
            if (file.extension.lowercase() !in storedExtensions) continue
            out[slot] = file.absolutePath
        }
        return out
    }

    suspend fun import(slot: UiMediaSlot, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        if (!UiMediaSlot.isValidKey(slot.key)) {
            return@withContext ImportResult(false, "Not a customizable media slot")
        }
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val ext = mime?.let { UiMediaLimits.extensionForMime(it) }
        if (mime == null || ext == null) {
            return@withContext ImportResult(
                false,
                if (slot.kind == UiMediaKind.VIDEO) UiMediaLimits.MSG_UNSUPPORTED_FORMAT_VIDEO
                else UiMediaLimits.MSG_UNSUPPORTED_FORMAT_AUDIO,
            )
        }
        val spec = slot.limits

        val knownSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it > 0 }
        if (knownSize != null && knownSize > spec.maxBytes) {
            return@withContext ImportResult(false, UiMediaLimits.tooLarge(spec))
        }

        dir.mkdirs()
        val staged = File(dir, "staging_${System.currentTimeMillis()}.$ext")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->

                staged.outputStream().use { output ->
                    with(SafeMedia) { input.copyCappedTo(output, spec.maxBytes) }
                }
            }
        }.getOrNull()
        if (copied == null) {
            runCatching { staged.delete() }
            return@withContext ImportResult(false, UiMediaLimits.MSG_UNDECODABLE)
        }

        val rejection = validateImported(staged, spec)
        if (rejection != null) {
            runCatching { staged.delete() }
            return@withContext ImportResult(false, rejection)
        }

        for (candidateExt in storedExtensions) {
            if (candidateExt != ext) File(dir, "${slot.key}.$candidateExt").delete()
        }
        val dest = File(dir, "${slot.key}.$ext")
        if (!staged.renameTo(dest)) {
            staged.copyTo(dest, overwrite = true)
            staged.delete()
        }

        recordDisplayName(slot, uri)
        context.pfpDataStore.edit { prefs -> prefs.bumpUiMediaStamp() }
        ImportResult(true)
    }

    suspend fun clear(slot: UiMediaSlot): Boolean = withContext(Dispatchers.IO) {
        if (!UiMediaSlot.isValidKey(slot.key)) return@withContext false
        val removed = storedExtensions.any { ext -> File(dir, "${slot.key}.$ext").delete() }
        if (removed) {
            context.pfpDataStore.edit { prefs ->

                prefs.remove(displayNameKey(slot))
                prefs.bumpUiMediaStamp()
            }
        }
        removed
    }

    suspend fun clearAll(kind: UiMediaKind): Boolean = withContext(Dispatchers.IO) {
        var removedAny = false
        for (slot in UiMediaSlot.ofKind(kind)) {
            if (clear(slot)) removedAny = true
        }
        removedAny
    }

    suspend fun pruneOrphans(): Boolean = withContext(Dispatchers.IO) {
        var removedAny = false

        val files = dir.listFiles { f -> f.isFile }
        for (file in files.orEmpty()) {
            if (!UiMediaSlot.isValidKey(file.nameWithoutExtension) && file.delete()) {
                removedAny = true
            }
        }

        val prefs = context.pfpDataStore.data.first()
        val staleNameKeys = prefs.asMap().keys
            .filterIsInstance<androidx.datastore.preferences.core.Preferences.Key<String>>()
            .filter {
                it.name.startsWith(DISPLAY_NAME_PREFIX) &&
                    !UiMediaSlot.isValidKey(it.name.removePrefix(DISPLAY_NAME_PREFIX))
            }
        if (staleNameKeys.isNotEmpty()) {
            context.pfpDataStore.edit { p -> staleNameKeys.forEach { p.remove(it) } }
            removedAny = true
        }

        if (removedAny) {
            context.pfpDataStore.edit { it.bumpUiMediaStamp() }
        }
        removedAny
    }

    suspend fun displayNameFor(slot: UiMediaSlot): String? {
        val prefs = context.pfpDataStore.data.first()
        return prefs[displayNameKey(slot)]
    }

    suspend fun recordDisplayName(slot: UiMediaSlot, uri: Uri) {
        val fallback = if (slot.kind == UiMediaKind.VIDEO) "Custom video" else "Custom sound"
        val name = MediaDisplayNames.queryDisplayName(context, uri) ?: fallback
        context.pfpDataStore.edit { prefs -> prefs[displayNameKey(slot)] = name }
    }

    private fun validateImported(file: File, spec: UiMediaLimits.Spec): String? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            val durationRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?: mimeGuessFor(file)
            UiMediaLimits.validate(
                spec,
                UiMediaLimits.Probe(
                    mime = mime,
                    durationMs = durationRaw?.toLongOrNull()
                        ?: MediaDurationFallback.durationMs(file, mime),
                    bytes = file.length(),
                ),
            )
        }
    }.getOrDefault(UiMediaLimits.MSG_UNDECODABLE)

    private fun mimeGuessFor(file: File): String? =
        UiMediaLimits.mimeForExtension(file.extension.lowercase())

    private fun MutablePreferences.bumpUiMediaStamp() {
        val previous = this[KEY_UI_MEDIA_STAMP] ?: 0L
        this[KEY_UI_MEDIA_STAMP] = maxOf(previous + 1, System.currentTimeMillis())
    }

    companion object {
        const val UI_MEDIA_DIR = "ui-media"

        val storedExtensions = setOf("mp3", "wav", "ogg", "m4a", "mp4", "webm")

        val KEY_UI_MEDIA_STAMP = longPreferencesKey("ui_media_stamp")

        val KEY_MENU_SOUNDS_ENABLED = booleanPreferencesKey("sound_menu_enabled")

        fun displayNameKey(slot: UiMediaSlot) = stringPreferencesKey("ui_media_name_${slot.key}")

        private const val DISPLAY_NAME_PREFIX = "ui_media_name_"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UiMediaModule {
    @Binds
    @Singleton
    abstract fun bindUiMediaPaths(impl: UiMediaStore): UiMediaPaths
}
