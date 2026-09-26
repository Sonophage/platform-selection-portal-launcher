package com.psplauncher.core.ui.notification

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class ToastKind { SUCCESS, ERROR }

data class SystemToast(
    val id: Long,
    val title: String,
    val message: String?,
    val kind: ToastKind,
)

object SystemToasts {
    private val _events = MutableSharedFlow<SystemToast>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<SystemToast> = _events

    private val _recent = MutableStateFlow<List<SystemToast>>(emptyList())

    val recent: StateFlow<List<SystemToast>> = _recent

    const val HISTORY = 12

    private var nextId = 0L

    @Synchronized
    fun post(title: String, message: String?, kind: ToastKind) {
        val toast = SystemToast(nextId++, title, normalise(message), kind)
        _recent.update { (listOf(toast) + it).take(HISTORY) }
        _events.tryEmit(toast)
    }

    fun clear() = _recent.update { emptyList() }

    internal fun normalise(message: String?): String? = message?.takeIf { it.isNotBlank() }
}
