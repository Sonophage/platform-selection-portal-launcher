package com.psplauncher.launcher.notification

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.psplauncher.core.ui.notification.AndroidNotice
import com.psplauncher.core.ui.notification.AndroidNotifications
import timber.log.Timber

class PfpNotificationListener : NotificationListenerService(), AndroidNotifications.NoticeActions {
    private var intents: Map<String, PendingIntent> = emptyMap()

    override fun onListenerConnected() {
        Timber.i("Notification listener connected")
        AndroidNotifications.attach(this)
        republish()
    }

    override fun onListenerDisconnected() {
        Timber.i("Notification listener disconnected")
        intents = emptyMap()
        AndroidNotifications.disconnected()
    }

    override fun open(key: String): Boolean {
        val intent = intents[key] ?: return false

        return runCatching { intent.send(this, 0, null, null, null, null, balOptions()) }
            .onFailure { Timber.i(it, "Notification content intent could not be sent") }
            .isSuccess
    }

    private fun balOptions(): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
                .toBundle()
        } else {
            null
        }

    override fun dismiss(key: String) {
        runCatching { cancelNotification(key) }
            .onFailure { Timber.w(it, "Could not dismiss notification") }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = republish()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = republish()

    private fun republish() {
        val active = runCatching { activeNotifications }.getOrNull() ?: run {
            AndroidNotifications.disconnected()
            return
        }
        val drawable = active.orEmpty().filter { it.toNotice() != null }
        intents = drawable.mapNotNull { sbn ->
            sbn.notification?.contentIntent?.let { sbn.key to it }
        }.toMap()
        val notices = drawable.mapNotNull { it.toNotice() }
        Timber.i("Notification listener: ${active.orEmpty().size} active, ${notices.size} drawable, ${intents.size} openable")
        AndroidNotifications.publish(notices)
    }

    private fun StatusBarNotification.toNotice(): AndroidNotice? {
        val extras = notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        if (title.isNullOrBlank() && text.isNullOrBlank()) return null
        return AndroidNotice(
            key = key,
            appLabel = appLabelFor(packageName),
            title = title,
            text = text,
            postedAt = postTime,
            canOpen = notification?.contentIntent != null,

            canDismiss = isClearable,
        )
    }

    private fun appLabelFor(pkg: String): String = runCatching {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(pkg)
}
