package com.psplauncher.core.ui.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Surfaces background work (ROM scans, music scans, artwork fetches, etc.) as Android system
 * notifications. Each task is keyed by its string id so progress updates replace the same
 * notification rather than stacking.
 *
 * Outcomes -- and only outcomes -- also go to [SystemToasts], which draws them as a pill inside
 * the launcher. Progress does not: a scan calls [running] on every file, and a toast per file is
 * not a notification, it is a flicker. This is the single place both surfaces are fed from, so a
 * scan cannot end up telling the system one thing and the screen another.
 *
 * Lives in core-ui so any feature module (xmb, settings, …) can report background progress the
 * same way.
 */
class BackgroundTaskNotifier(private val context: Context) {

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background Tasks",
            NotificationManager.IMPORTANCE_LOW, // quiet — no sound/peek for progress
        ).apply {
            description = "ROM scans, music scans, artwork downloads and other background work"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** Post or update a running task. A null [progress] shows an indeterminate bar. */
    fun running(id: String, label: String, progress: Float?) {
        val builder = base(label)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
        if (progress == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, (progress * 100).toInt().coerceIn(0, 100), false)
        }
        post(id, builder.build())
    }

    /** Mark a task finished — dismissible, no progress bar. */
    fun complete(id: String, label: String, message: String?) {
        // Before the system notification, not after, and deliberately outside canPost(): the
        // in-app pill is the surface the user actually sees while the launcher is in front, and
        // it must not disappear because POST_NOTIFICATIONS was never granted.
        SystemToasts.post(label, message, ToastKind.SUCCESS)
        val builder = base(label)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
        if (!message.isNullOrBlank()) builder.setContentText(message)
        post(id, builder.build())
    }

    /** Mark a task failed — dismissible, shows the error text. */
    fun failed(id: String, label: String, message: String) {
        SystemToasts.post(label, message, ToastKind.ERROR)
        val builder = base(label)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setContentText(message)
        post(id, builder.build())
    }

    fun cancel(id: String) = manager.cancel(id.notificationId())

    private fun base(label: String) = Notification.Builder(context, CHANNEL_ID)
        .setContentTitle(label)
        .setOnlyAlertOnce(true)

    private fun post(id: String, notification: Notification) {
        if (!canPost()) return
        manager.notify(id.notificationId(), notification)
    }

    // POST_NOTIFICATIONS is a runtime permission on API 33+. Without the grant,
    // notify() is silently dropped by the system, so skip rather than risk noise.
    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun String.notificationId(): Int = hashCode()

    companion object {
        private const val CHANNEL_ID = "pfp_background_tasks"
    }
}
