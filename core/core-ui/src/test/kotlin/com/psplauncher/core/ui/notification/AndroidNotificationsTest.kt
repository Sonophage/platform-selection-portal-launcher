package com.psplauncher.core.ui.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The live set of the device's own notifications.
 *
 * The rule that matters is that it is LIVE. Every listener callback republishes the whole set,
 * because the system connects and disconnects the service at times it does not announce, and a
 * list maintained by deltas across that drifts with nothing to notice it — a stale notification
 * looks exactly like a real one.
 */
class AndroidNotificationsTest {

    @Before
    fun reset() = AndroidNotifications.disconnected()

    private fun notice(key: String, at: Long) =
        AndroidNotice(key = key, appLabel = "App", title = "T", text = null, postedAt = at)

    @Test
    fun `newest first, whatever order the system hands them over in`() {
        AndroidNotifications.publish(listOf(notice("a", 100), notice("b", 300), notice("c", 200)))
        assertEquals(listOf("b", "c", "a"), AndroidNotifications.active.value.map { it.key })
    }

    @Test
    fun `publishing REPLACES, so a dismissed notification actually goes`() {
        // The whole reason the service republishes everything instead of removing one: if this
        // merged, a notification the user swiped away somewhere else would stay in the bar.
        AndroidNotifications.publish(listOf(notice("a", 100), notice("b", 200)))
        AndroidNotifications.publish(listOf(notice("b", 200)))
        assertEquals(listOf("b"), AndroidNotifications.active.value.map { it.key })
    }

    @Test
    fun `losing the service empties it rather than freezing the last known set`() {
        // Access revoked, or the system tore the listener down. Nothing is known any more, and
        // that is not the same as nothing having changed — a frozen list is a list of claims the
        // launcher can no longer stand behind.
        AndroidNotifications.publish(listOf(notice("a", 100)))
        AndroidNotifications.disconnected()
        assertTrue(AndroidNotifications.active.value.isEmpty())
    }

    @Test
    fun `it starts empty, which is what ungranted access looks like`() {
        // No permission means the service is never constructed and nothing ever publishes. The
        // empty list is the correct state, not an error one.
        assertTrue(AndroidNotifications.active.value.isEmpty())
    }
}
