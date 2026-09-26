package com.psplauncher.core.data.media

import android.content.Context
import android.content.Intent
import com.psplauncher.core.common.launch.LaunchTransition
import com.psplauncher.core.common.launch.LaunchTransition.withoutTransition
import android.net.Uri
import timber.log.Timber

data class MediaApp(
    val packageName: String,
    val label: String,
)

object MediaOpenIntent {
    fun build(uri: String, mimeType: String, pinnedPackage: String?): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), mimeType)

            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            withoutTransition()
            if (!pinnedPackage.isNullOrBlank()) setPackage(pinnedPackage)
        }

    fun launch(
        context: Context,
        intent: Intent,
        chooserTitle: String,
        noHandlerMessage: String,
        logLabel: String,
    ): String? = try {
        context.startActivity(intent, LaunchTransition.options(context))
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

    @Suppress("QueryPermissionsNeeded")
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

    fun label(context: Context, packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
}
