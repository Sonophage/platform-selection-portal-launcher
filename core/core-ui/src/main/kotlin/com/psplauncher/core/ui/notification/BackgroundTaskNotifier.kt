package com.psplauncher.core.ui.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class BackgroundTaskNotifier(private val context: Context) {
    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background Tasks",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "ROM scans, music scans, artwork downloads and other background work"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

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

    fun complete(id: String, label: String, message: String?) {
        SystemToasts.post(label, message, ToastKind.SUCCESS)
        val builder = base(label)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
        if (!message.isNullOrBlank()) builder.setContentText(message)
        post(id, builder.build())
    }

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

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun String.notificationId(): Int = hashCode()

    companion object {
        private const val CHANNEL_ID = "pfp_background_tasks"
    }
}
