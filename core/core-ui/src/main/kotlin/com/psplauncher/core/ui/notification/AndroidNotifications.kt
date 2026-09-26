package com.psplauncher.core.ui.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AndroidNotice(

    val key: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,

    val canOpen: Boolean = false,

    val canDismiss: Boolean = false,
)

object AndroidNotifications {
    private val _active = MutableStateFlow<List<AndroidNotice>>(emptyList())

    val active: StateFlow<List<AndroidNotice>> = _active

    interface NoticeActions {
        fun open(key: String): Boolean

        fun dismiss(key: String)
    }

    private var actions: NoticeActions? = null

    fun attach(actions: NoticeActions) {
        this.actions = actions
    }

    fun publish(notices: List<AndroidNotice>) {
        _active.value = notices.sortedByDescending { it.postedAt }
    }

    fun disconnected() {
        _active.value = emptyList()
        actions = null
    }

    fun open(key: String): Boolean = actions?.open(key) ?: false

    fun dismiss(key: String) {
        actions?.dismiss(key)
    }

    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS).orEmpty()
        if (flat.isBlank()) return false
        return flat.split(':').any { entry ->
            ComponentName.unflattenFromString(entry)?.packageName == context.packageName
        }
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    private const val ENABLED_LISTENERS = "enabled_notification_listeners"
}
