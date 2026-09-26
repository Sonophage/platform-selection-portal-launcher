package com.psplauncher.core.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.psplauncher.core.domain.model.UiMediaSlot
import com.psplauncher.core.ui.media.bundledDefaultRes
import com.psplauncher.core.ui.R
import com.psplauncher.core.ui.media.UiMediaPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

enum class MenuSound {
    SCROLL,
    SYSTEM_BROWSE,
    SELECT,

    CONFIRM,
    BACK,
    LAUNCH,

    NOTIFICATION,

    ERROR,
    ;

    val slot: UiMediaSlot
        get() = when (this) {
            SCROLL -> UiMediaSlot.SOUND_SCROLL
            SYSTEM_BROWSE -> UiMediaSlot.SOUND_SYSTEM_BROWSE
            SELECT -> UiMediaSlot.SOUND_SELECT
            CONFIRM -> UiMediaSlot.SOUND_CONFIRM
            BACK -> UiMediaSlot.SOUND_BACK
            LAUNCH -> UiMediaSlot.SOUND_LAUNCH
            NOTIFICATION -> UiMediaSlot.SOUND_NOTIFICATION
            ERROR -> UiMediaSlot.SOUND_ERROR
        }
}

@Singleton
class MenuSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uiMedia: UiMediaPaths,
) {
    @Volatile
    var enabled: Boolean = true
        private set

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()

                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val defaultIds = HashMap<UiMediaSlot, Int>()

    @Volatile
    private var customIds: Map<UiMediaSlot, Int> = emptyMap()

    private val loaded: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.add(sampleId)
            else Timber.w("Menu sound sample $sampleId failed to load (status=$status)")
        }

        for (slot in MenuSound.entries.map { it.slot }.distinct()) {
            val res = slot.bundledDefaultRes() ?: continue
            defaultIds[slot] = pool.load(context, res, 1)
        }

        uiMedia.stamp
            .distinctUntilChanged()
            .onEach { reload() }
            .launchIn(scope)

        uiMedia.menuSoundsEnabled
            .distinctUntilChanged()
            .onEach { enabled = it }
            .launchIn(scope)
    }

    fun play(sound: MenuSound, ignoreMute: Boolean = false) {
        if (!enabled && !ignoreMute) return

        if (sound == MenuSound.NOTIFICATION && !ignoreMute) return
        val id = customIds[sound.slot]?.takeIf { it in loaded } ?: defaultIds[sound.slot] ?: return

        if (id !in loaded) return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    private fun reload() {
        val previous = customIds
        val next = HashMap<UiMediaSlot, Int>()
        for (slot in MenuSound.entries.map { it.slot }.distinct()) {
            val path = runCatching { uiMedia.pathFor(slot) }.getOrNull() ?: continue
            val id = runCatching { pool.load(path, 1) }.getOrNull() ?: continue
            if (id == 0) continue
            next[slot] = id
        }
        customIds = next
        for (id in previous.values) {
            loaded.remove(id)
            runCatching { pool.unload(id) }
        }
    }

    fun refreshCustomSamples() {
        scope.launch { reload() }
    }
}
