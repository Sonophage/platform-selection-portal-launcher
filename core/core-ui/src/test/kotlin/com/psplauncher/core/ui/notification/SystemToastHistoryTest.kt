package com.psplauncher.core.ui.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SystemToastHistoryTest {
    @Before
    fun clear() = SystemToasts.clear()

    @Test
    fun `the newest is first, because that is the one the strip shows`() {
        SystemToasts.post("First", null, ToastKind.SUCCESS)
        SystemToasts.post("Second", null, ToastKind.SUCCESS)
        assertEquals(listOf("Second", "First"), SystemToasts.recent.value.map { it.title })
    }

    @Test
    fun `the history is capped, and it is the OLDEST that falls off`() {
        repeat(SystemToasts.HISTORY + 5) { SystemToasts.post("Scan $it", null, ToastKind.SUCCESS) }
        val titles = SystemToasts.recent.value.map { it.title }
        assertEquals(SystemToasts.HISTORY, titles.size)
        assertEquals("Scan ${SystemToasts.HISTORY + 4}", titles.first())
        assertTrue("the oldest must be gone", "Scan 0" !in titles)
    }

    @Test
    fun `two identical reports are two notifications`() {
        SystemToasts.post("Scan complete", "0 new", ToastKind.SUCCESS)
        SystemToasts.post("Scan complete", "0 new", ToastKind.SUCCESS)
        val ids = SystemToasts.recent.value.map { it.id }
        assertEquals(2, ids.size)
        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun `clearing empties it`() {
        SystemToasts.post("Anything", null, ToastKind.SUCCESS)
        SystemToasts.clear()
        assertTrue(SystemToasts.recent.value.isEmpty())
    }
}
