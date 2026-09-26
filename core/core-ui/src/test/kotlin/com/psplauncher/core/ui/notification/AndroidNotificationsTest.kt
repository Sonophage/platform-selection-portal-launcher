package com.psplauncher.core.ui.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
        AndroidNotifications.publish(listOf(notice("a", 100), notice("b", 200)))
        AndroidNotifications.publish(listOf(notice("b", 200)))
        assertEquals(listOf("b"), AndroidNotifications.active.value.map { it.key })
    }

    @Test
    fun `losing the service empties it rather than freezing the last known set`() {
        AndroidNotifications.publish(listOf(notice("a", 100)))
        AndroidNotifications.disconnected()
        assertTrue(AndroidNotifications.active.value.isEmpty())
    }

    @Test
    fun `it starts empty, which is what ungranted access looks like`() {
        assertTrue(AndroidNotifications.active.value.isEmpty())
    }
}
