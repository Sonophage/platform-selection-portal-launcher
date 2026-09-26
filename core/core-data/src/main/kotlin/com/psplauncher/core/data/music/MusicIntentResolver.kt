package com.psplauncher.core.data.music

import android.content.Context
import android.content.Intent
import com.psplauncher.core.data.media.MediaOpenIntent
import com.psplauncher.core.domain.model.MusicTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class MusicPlayerApp(
    val packageName: String,
    val label: String,
)

@Singleton
class MusicIntentResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun buildIntent(track: MusicTrack, defaultPlayerPackage: String?): Intent =
        MediaOpenIntent.build(
            uri = track.uri,
            mimeType = track.mimeType ?: AUDIO_MIME,

            pinnedPackage = defaultPlayerPackage?.takeIf { it != BUILTIN },
        )

    fun launch(track: MusicTrack, defaultPlayerPackage: String?): String? =
        MediaOpenIntent.launch(
            context = context,
            intent = buildIntent(track, defaultPlayerPackage),
            chooserTitle = "Play music with…",
            noHandlerMessage =
                "No music player could open this track. Install a player or pick one in Settings → Music.",
            logLabel = "music track \"${track.displayTitle}\"",
        )

    fun availablePlayers(): List<MusicPlayerApp> =
        MediaOpenIntent.handlers(context, AUDIO_MIME)
            .map { MusicPlayerApp(packageName = it.packageName, label = it.label) }

    companion object {
        const val BUILTIN = "builtin"

        private const val AUDIO_MIME = "audio/*"
    }
}
