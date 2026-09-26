package com.psplauncher.launcher.pin

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import timber.log.Timber

object WindowsSetupNotifications {
    fun post(context: Context, gameTitle: String) {
        if (!canPost(context)) {
            Timber.w("Windows setup notification suppressed — notifications not permitted")
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Windows Library", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Finish setting up the Windows Games library" },
        )

        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.let { intent ->
                PendingIntent.getActivity(
                    context,
                    0,
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .setContentTitle("\"$gameTitle\" added to Windows Games")
            .setContentText("Finish setting up your Windows Library to scan its folder")
            .setAutoCancel(true)
            .apply { open?.let { setContentIntent(it) } }
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private const val CHANNEL_ID = "pfp_windows_library_setup"
    private const val NOTIFICATION_ID = 0x57494E
}
