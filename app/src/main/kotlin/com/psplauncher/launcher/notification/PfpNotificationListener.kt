package com.psplauncher.launcher.notification

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.psplauncher.core.ui.notification.AndroidNotice
import com.psplauncher.core.ui.notification.AndroidNotifications
import timber.log.Timber

/**
 * Reads the device's own notifications so the launcher can show a count and a list.
 *
 * In the app module because the service has to be declared in the manifest with
 * BIND_NOTIFICATION_LISTENER_SERVICE, and because it is the one component the SYSTEM constructs —
 * it has no graph, takes no dependencies, and publishes to a process-wide object.
 *
 * Every callback republishes the WHOLE active set rather than adding or removing one. The listener
 * is told about posts and removals, but it is also disconnected and reconnected by the system at
 * times it does not announce, and a list maintained by deltas across that is a list that drifts —
 * with no way to notice, because a stale notification looks exactly like a real one.
 */
class PfpNotificationListener : NotificationListenerService(), AndroidNotifications.NoticeActions {

    /**
     * The content intent of everything currently listed, by key.
     *
     * Rebuilt whole on every republish, beside the list it belongs to, for the same reason the
     * list is: a map maintained by deltas outlives the notifications in it, and firing a stale
     * PendingIntent opens a screen for something the user dealt with ten minutes ago. It never
     * leaves this class — the UI asks by key.
     */
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
        // A PendingIntent the posting app has cancelled throws rather than returning anything,
        // and the launcher finding out that a notification is stale is not a crash.
        return runCatching { intent.send() }
            .onFailure { Timber.i(it, "Notification content intent could not be sent") }
            .isSuccess
    }

    override fun dismiss(key: String) {
        runCatching { cancelNotification(key) }
            .onFailure { Timber.w(it, "Could not dismiss notification") }
        // The system calls back with the removal, which republishes; this is only the request.
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = republish()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = republish()

    private fun republish() {
        // getActiveNotifications throws if the service is not currently bound, which happens
        // during teardown and after the user revokes access while the launcher is running.
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
        // A notification with neither a title nor a text is a group summary or a bare ongoing
        // marker — there is nothing to draw in a row, so it is not one.
        if (title.isNullOrBlank() && text.isNullOrBlank()) return null
        return AndroidNotice(
            key = key,
            appLabel = appLabelFor(packageName),
            title = title,
            text = text,
            postedAt = postTime,
            canOpen = notification?.contentIntent != null,
            // isClearable is false for ongoing notices — a media session, a foreground service —
            // and calling cancelNotification on one does nothing at all. Saying so on the row
            // beats offering a control that silently declines.
            canDismiss = isClearable,
        )
    }

    private fun appLabelFor(pkg: String): String = runCatching {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(pkg)
}
