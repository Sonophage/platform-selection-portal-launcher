package com.psplauncher.core.ui.notification

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What a toast is telling you, which is the only thing its colour and glyph are derived from. */
enum class ToastKind { SUCCESS, ERROR }

/**
 * One finished piece of background work, as the launcher shows it.
 *
 * [id] exists so the host can key an effect on "a different toast" rather than on equal content:
 * two identical scan results in a row are two toasts, and a data-class comparison would collapse
 * them into one that never re-times.
 */
data class SystemToast(
    val id: Long,
    val title: String,
    val message: String?,
    val kind: ToastKind,
)

/**
 * The in-app notification feed: scans, imports and their failures.
 *
 * It used to be a pill that appeared in the top centre and timed itself out, and anything you did
 * not happen to be looking at was gone. It is the status strip's left half now, and what it said
 * is kept in [recent] so the strip can be pulled down into a list of them.
 *
 * A process-wide flow rather than an injected singleton because [BackgroundTaskNotifier] is
 * constructed with `BackgroundTaskNotifier(context)` at a dozen call sites across four modules,
 * none of which has a graph handy -- and making them all injectable to carry a UI event is a lot
 * of plumbing for a one-way string. It stays honest as long as nothing else posts to it: the ONE
 * place a background task reports how it ended is the notifier, and the notifier is what posts
 * here.
 *
 * Emission never suspends and never blocks a worker: a full buffer drops the oldest. A toast is
 * a courtesy, and dropping one is better than stalling a scan behind a UI that may not even be
 * composed.
 */
object SystemToasts {

    private val _events = MutableSharedFlow<SystemToast>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<SystemToast> = _events

    private val _recent = MutableStateFlow<List<SystemToast>>(emptyList())

    /**
     * What has been posted, newest first, capped at [HISTORY].
     *
     * The flow above is an EVENT — it fires once and is gone, which is all a pill that times
     * itself out ever needed. A list you can pull down has to still be there when you pull it, so
     * the two coexist: the strip listens to the event to show the newest for a few seconds, and
     * reads this when opened.
     *
     * In memory only. A notification that survived a restart to tell you about a scan from before
     * it would be reporting on a world that no longer exists — the same reason the pill never
     * persisted either.
     */
    val recent: StateFlow<List<SystemToast>> = _recent

    /** How many are kept. Enough to cover a session's worth of scans without becoming a log. */
    const val HISTORY = 12

    private var nextId = 0L

    @Synchronized
    fun post(title: String, message: String?, kind: ToastKind) {
        val toast = SystemToast(nextId++, title, normalise(message), kind)
        _recent.update { (listOf(toast) + it).take(HISTORY) }
        _events.tryEmit(toast)
    }

    /** Empties the list — the drop-down's own clear. */
    fun clear() = _recent.update { emptyList() }

    /**
     * Blank is absent.
     *
     * Callers of [BackgroundTaskNotifier.complete] pass null, "" and a real summary in roughly
     * equal measure, and the pill draws its second line only when there is one. Resolved here
     * rather than at the drawing end, which has no idea who sent it.
     */
    internal fun normalise(message: String?): String? = message?.takeIf { it.isNotBlank() }
}
