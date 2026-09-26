package com.psplauncher.core.ui.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackgroundTaskToastsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `an outcome toasts and progress does not`() = runTest {
        val seen = mutableListOf<SystemToast>()
        val job = launch { SystemToasts.events.toList(seen) }
        runCurrent()

        val notifier = BackgroundTaskNotifier(context)
        notifier.running("scan", "Scanning music", 0.1f)
        notifier.running("scan", "Scanning music", 0.9f)
        notifier.running("scan", "Scanning music", null)
        notifier.complete("scan", "Music scan", "412 tracks")
        notifier.failed("roms", "ROM scan", "Folder not readable")
        runCurrent()
        job.cancel()

        assertEquals("progress must not reach the toast surface", 2, seen.size)
        assertEquals("Music scan", seen[0].title)
        assertEquals("412 tracks", seen[0].message)
        assertEquals(ToastKind.SUCCESS, seen[0].kind)
        assertEquals("ROM scan", seen[1].title)
        assertEquals("Folder not readable", seen[1].message)
        assertEquals(ToastKind.ERROR, seen[1].kind)
    }

    @Test
    fun `two identical results are two toasts`() = runTest {
        val seen = mutableListOf<SystemToast>()
        val job = launch { SystemToasts.events.toList(seen) }
        runCurrent()

        SystemToasts.post("Music scan", "412 tracks", ToastKind.SUCCESS)
        SystemToasts.post("Music scan", "412 tracks", ToastKind.SUCCESS)
        runCurrent()
        job.cancel()

        assertEquals(2, seen.size)
        assertTrue("identical toasts must still be distinguishable", seen[0].id != seen[1].id)
    }

    @Test
    fun `a blank message is no message`() {
        assertNull(SystemToasts.normalise(""))
        assertNull(SystemToasts.normalise("   "))
        assertNull(SystemToasts.normalise(null))
        assertEquals("412 tracks", SystemToasts.normalise("412 tracks"))
    }
}
