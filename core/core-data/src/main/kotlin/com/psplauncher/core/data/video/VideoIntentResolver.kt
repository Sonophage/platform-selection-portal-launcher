package com.psplauncher.core.data.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.psplauncher.core.data.media.MediaOpenIntent
import com.psplauncher.core.domain.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class VideoPlayerApp(
    val packageName: String,
    val label: String,
)

@Singleton
class VideoIntentResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun buildViewIntent(video: Video, playerPackage: String?): Intent =
        MediaOpenIntent.build(
            uri = video.uri,
            mimeType = video.mimeType ?: VIDEO_MIME,
            pinnedPackage = playerPackage,
        )

    fun launch(video: Video, playerPackage: String?): String? =
        MediaOpenIntent.launch(
            context = context,
            intent = buildViewIntent(video, playerPackage),
            chooserTitle = CHOOSER_TITLE,
            noHandlerMessage =
                "No video player could open this file. Install a player or pick one in Settings → Video.",
            logLabel = "video \"${video.displayTitle}\"",
        )

    fun launchChooser(video: Video): String? =
        MediaOpenIntent.launchChooser(
            context = context,
            intent = buildViewIntent(video, null),
            chooserTitle = CHOOSER_TITLE,
            noHandlerMessage = "No video player is installed.",
            logLabel = "video \"${video.displayTitle}\"",
        )

    fun validate(video: Video, playerPackage: String?): String? {
        val uri = runCatching { Uri.parse(video.uri) }.getOrNull()
            ?: return "This video's location is invalid."

        val reachable = runCatching { context.contentResolver.getType(uri) }
        if (reachable.isSuccess && reachable.getOrNull() == null) {
            return "This video file could not be found or access was lost. Try re-scanning the library."
        }
        val resolved = runCatching {
            context.packageManager.resolveActivity(buildViewIntent(video, playerPackage), 0)
        }.getOrNull()
        if (resolved == null) {
            return if (playerPackage != null) "${playerLabel(playerPackage) ?: "That player"} can't open this video, or isn't installed."
            else "No video player is installed to open this file."
        }
        return null
    }

    fun playerLabel(packageName: String): String? = MediaOpenIntent.label(context, packageName)

    fun availablePlayers(): List<VideoPlayerApp> =
        MediaOpenIntent.handlers(context, VIDEO_MIME)
            .map { VideoPlayerApp(packageName = it.packageName, label = it.label) }

    private companion object {
        const val VIDEO_MIME = "video/*"
        const val CHOOSER_TITLE = "Play video with…"
    }
}
