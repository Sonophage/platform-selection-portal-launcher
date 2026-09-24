package com.psplauncher.core.ui.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One notification posted by another app, as the strip and the bar show it. */
data class AndroidNotice(
    /** The listener's own key. Stable across an update of the same notification. */
    val key: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
)

/**
 * What the rest of Android is telling the user, read through a NotificationListenerService.
 *
 * A process-wide object for the same reason [SystemToasts] is one: the service is constructed by
 * the system, not by the graph, and handing it an injected sink means giving the system's own
 * component a Hilt entry point to carry a list of strings.
 *
 * NOTHING IS STORED. The list is the live set of active notifications, replaced wholesale every
 * time the listener is told about a change, and emptied when the service disconnects. A cached
 * copy would outlive the notification it describes and show the user something they have already
 * dismissed somewhere else.
 *
 * The permission behind this cannot be granted by a dialog. [isEnabled] reports whether the user
 * has switched the launcher on in Settings ▸ Notification access, and [settingsIntent] is how to
 * send them there; until they do, the flow stays empty and everything reading it shows nothing,
 * which is the correct behaviour rather than an error state.
 */
object AndroidNotifications {

    private val _active = MutableStateFlow<List<AndroidNotice>>(emptyList())

    /** The live set, newest first. Empty when access has not been granted. */
    val active: StateFlow<List<AndroidNotice>> = _active

    /** Called by the listener service. Replaces the list rather than merging into it. */
    fun publish(notices: List<AndroidNotice>) {
        _active.value = notices.sortedByDescending { it.postedAt }
    }

    /** The service has gone away — nothing is known any more, which is not the same as nothing. */
    fun disconnected() {
        _active.value = emptyList()
    }

    /**
     * Whether the user has granted notification access to [context]'s package.
     *
     * Read from the secure setting rather than kept as a flag: it is changed in Android's own
     * Settings app, outside this process, and a flag would go stale the moment they revoked it.
     */
    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS).orEmpty()
        if (flat.isBlank()) return false
        return flat.split(':').any { entry ->
            ComponentName.unflattenFromString(entry)?.packageName == context.packageName
        }
    }

    /** Where to send the user to grant it. There is no in-app dialog for this permission. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    private const val ENABLED_LISTENERS = "enabled_notification_listeners"
}
