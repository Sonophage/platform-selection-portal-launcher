package com.psplauncher.core.ui.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The retained feed behind the status strip.
 *
 * The pill only ever needed an event — it fired once and was gone, and anything you were not
 * looking at you never saw. A list you can pull down has to still be there when you pull it, and
 * that is the whole reason this exists.
 */
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
        // Uncapped it is a log that grows for as long as the launcher is up. Dropping the newest
        // instead would keep the list full of things that have stopped being true.
        repeat(SystemToasts.HISTORY + 5) { SystemToasts.post("Scan $it", null, ToastKind.SUCCESS) }
        val titles = SystemToasts.recent.value.map { it.title }
        assertEquals(SystemToasts.HISTORY, titles.size)
        assertEquals("Scan ${SystemToasts.HISTORY + 4}", titles.first())
        assertTrue("the oldest must be gone", "Scan 0" !in titles)
    }

    @Test
    fun `two identical reports are two notifications`() {
        // Same title, same message, one after the other — a scan that found nothing twice. They
        // are distinct ids so the strip re-flashes the second rather than treating it as the one
        // already on screen.
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
