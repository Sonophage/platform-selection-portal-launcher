package com.psplauncher.core.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import timber.log.Timber

/** An installed app that can handle ACTION_VIEW for some media type. */
data class MediaApp(
    val packageName: String,
    val label: String,
)

/**
 * The half that every "hand a file to an app the user chose" path says the same way.
 *
 * `MusicIntentResolver` and `VideoIntentResolver` had written it out twice already, near enough
 * word for word, and the Library section would have made three. What is genuinely per-type stays
 * with each resolver: the default MIME, the sentinel a section uses for its own in-app player, the
 * wording of an error, and the shape it hands back to its settings screen. What is here is the
 * part that must behave identically everywhere, because it is about Android, not about media:
 * grant the target a read on a SAF uri, pin only to a package the user actually chose, and when
 * that package cannot open the file, offer the chooser rather than failing.
 *
 * **One behaviour was unified rather than preserved.** A pinned player that failed sent Video to
 * the system chooser and Music to a second bare attempt, which let Android silently pick a
 * different app. Neither was pinned by a test, the difference reads as drift rather than intent,
 * and the chooser is the honest one: it asks. Music now does what Video did.
 */
object MediaOpenIntent {

    /**
     * ACTION_VIEW for [uri] as [mimeType], pinned to [pinnedPackage] when the caller supplies a
     * real one. A null or blank package deliberately stays unpinned so the system can choose;
     * callers map their own "use the in-app player" sentinel to null before calling.
     */
    fun build(uri: String, mimeType: String, pinnedPackage: String?): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), mimeType)
            // Without the grant the target app resolves the uri and reads nothing.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!pinnedPackage.isNullOrBlank()) setPackage(pinnedPackage)
        }

    /**
     * Starts [intent]. Returns null on success, or a user-readable message. Never throws.
     *
     * When a pinned package cannot handle the file, this retries once through the system chooser,
     * so a stale player choice costs the user one extra tap instead of an error they cannot act on.
     * [noHandlerMessage] is only reached when nothing on the device can open it at all.
     */
    fun launch(
        context: Context,
        intent: Intent,
        chooserTitle: String,
        noHandlerMessage: String,
        logLabel: String,
    ): String? = try {
        context.startActivity(intent)
        Timber.i("Opened $logLabel (app=${intent.`package` ?: "system"})")
        null
    } catch (e: Exception) {
        if (intent.`package` != null) {
            Timber.w(e, "Pinned app could not open $logLabel, offering the chooser")
            launchChooser(context, intent, chooserTitle, noHandlerMessage, logLabel)
        } else {
            Timber.e(e, "Nothing could open $logLabel")
            noHandlerMessage
        }
    }

    /** Shows the system chooser for [intent], with any pinned package removed. */
    fun launchChooser(
        context: Context,
        intent: Intent,
        chooserTitle: String,
        noHandlerMessage: String,
        logLabel: String,
    ): String? {
        val open = Intent(intent).apply { `package` = null }
        val chooser = Intent.createChooser(open, chooserTitle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            null
        } catch (e: Exception) {
            Timber.e(e, "Nothing could open $logLabel")
            noHandlerMessage
        }
    }

    /**
     * Installed apps that can handle ACTION_VIEW for [probeMimeType], without this app, one entry
     * per package, sorted by label.
     */
    fun handlers(context: Context, probeMimeType: String): List<MediaApp> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://media/x"), probeMimeType)
        }
        return pm.queryIntentActivities(probe, 0)
            .asSequence()
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                MediaApp(
                    packageName = info.packageName,
                    label = runCatching { info.loadLabel(pm).toString() }
                        .getOrDefault(info.packageName),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Display label for an installed package, or null when it is not installed. */
    fun label(context: Context, packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
}
