package com.psplauncher.core.data.music

import android.content.Context
import android.content.Intent
import com.psplauncher.core.data.media.MediaOpenIntent
import com.psplauncher.core.domain.model.MusicTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** An installed app able to handle ACTION_VIEW for audio — a candidate default music player. */
data class MusicPlayerApp(
    val packageName: String,
    val label: String,
)

/**
 * Builds and launches the external-player intent for a music track. Playback is owned by the
 * chosen external app so audio keeps playing after the user leaves PSPLauncher — it never decodes
 * audio itself. [buildIntent] is pure (no side effects) so it can be unit-tested.
 *
 * The Android half lives in [MediaOpenIntent], shared with video and books. What stays here is
 * what is actually about music: the generic audio type, the [BUILTIN] sentinel, and the wording
 * the user reads when nothing can play the track.
 */
@Singleton
class MusicIntentResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * ACTION_VIEW intent for [track], optionally pinned to [defaultPlayerPackage]. Always grants
     * the target temporary read access to the SAF uri and launches into its own task.
     */
    fun buildIntent(track: MusicTrack, defaultPlayerPackage: String?): Intent =
        MediaOpenIntent.build(
            uri = track.uri,
            mimeType = track.mimeType ?: AUDIO_MIME,
            // BUILTIN is not a real package, so it must not reach setPackage.
            pinnedPackage = defaultPlayerPackage?.takeIf { it != BUILTIN },
        )

    /**
     * Launches [track] in the external player. Returns a user-readable error message on failure
     * (no player installed, revoked uri, etc.), or null on success. Never throws.
     */
    fun launch(track: MusicTrack, defaultPlayerPackage: String?): String? =
        MediaOpenIntent.launch(
            context = context,
            intent = buildIntent(track, defaultPlayerPackage),
            chooserTitle = "Play music with…",
            noHandlerMessage =
                "No music player could open this track. Install a player or pick one in Settings ▸ Music.",
            logLabel = "music track \"${track.displayTitle}\"",
        )

    /** Installed apps that can handle ACTION_VIEW for audio, de-duplicated by package and sorted. */
    fun availablePlayers(): List<MusicPlayerApp> =
        MediaOpenIntent.handlers(context, AUDIO_MIME)
            .map { MusicPlayerApp(packageName = it.packageName, label = it.label) }

    companion object {
        /** Sentinel default meaning "PSPLauncher" (in-app player) rather than a real package. */
        const val BUILTIN = "builtin"

        private const val AUDIO_MIME = "audio/*"
    }
}
