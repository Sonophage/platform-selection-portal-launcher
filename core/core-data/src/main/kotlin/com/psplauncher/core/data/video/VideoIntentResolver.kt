package com.psplauncher.core.data.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.psplauncher.core.data.media.MediaOpenIntent
import com.psplauncher.core.domain.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** An installed app able to handle ACTION_VIEW for video — a candidate external video player. */
data class VideoPlayerApp(
    val packageName: String,
    val label: String,
)

/**
 * Builds and launches the external-player intent for a video.
 *
 * The Android half lives in [MediaOpenIntent], shared with music and books: the read grant, the
 * pin-only-when-chosen rule, and the chooser retry. What stays here is what is about video — the
 * generic video type, the error wording, and [validate], the pre-launch check that has no
 * equivalent on the other paths.
 */
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

    /**
     * Launches [video] in an external player, optionally pinned to [playerPackage]. Returns a
     * user-readable error on failure, or null on success. A pinned player that can't handle it
     * retries once via the chooser.
     */
    fun launch(video: Video, playerPackage: String?): String? =
        MediaOpenIntent.launch(
            context = context,
            intent = buildViewIntent(video, playerPackage),
            chooserTitle = CHOOSER_TITLE,
            noHandlerMessage =
                "No video player could open this file. Install a player or pick one in Settings ▸ Video.",
            logLabel = "video \"${video.displayTitle}\"",
        )

    /** Shows the system chooser for [video] ("Ask Every Time"). */
    fun launchChooser(video: Video): String? =
        MediaOpenIntent.launchChooser(
            context = context,
            intent = buildViewIntent(video, null),
            chooserTitle = CHOOSER_TITLE,
            noHandlerMessage = "No video player is installed.",
            logLabel = "video \"${video.displayTitle}\"",
        )

    /**
     * Pre-launch safety check. Returns a user-readable error when the video can't be handed off, or
     * null when it's safe to launch: the uri parses, the file still exists, and an activity resolves
     * for the chosen player (or any player when [playerPackage] is null). Never throws.
     */
    fun validate(video: Video, playerPackage: String?): String? {
        val uri = runCatching { Uri.parse(video.uri) }.getOrNull()
            ?: return "This video's location is invalid."
        // Existence/accessibility proxy: a live SAF document reports a MIME type; a deleted file or
        // revoked grant yields null. Only block on a definite null (never on a query error).
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

    /** Display label for an installed package, or null if not installed. */
    fun playerLabel(packageName: String): String? = MediaOpenIntent.label(context, packageName)

    /** Installed apps that can handle ACTION_VIEW for video, de-duplicated by package and sorted. */
    fun availablePlayers(): List<VideoPlayerApp> =
        MediaOpenIntent.handlers(context, VIDEO_MIME)
            .map { VideoPlayerApp(packageName = it.packageName, label = it.label) }

    private companion object {
        const val VIDEO_MIME = "video/*"
        const val CHOOSER_TITLE = "Play video with…"
    }
}
