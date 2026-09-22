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

/**
 * What the in-app toast pill is fed, which is not the same set of events the system notification
 * gets.
 *
 * [BackgroundTaskNotifier] is the ONE funnel both surfaces hang off, so the interesting failure
 * is not "does a toast appear" -- it is the pair getting out of step. Two ways that happens:
 *
 *  - Someone adds a [SystemToasts] post to `running`, which a music scan calls once per FILE.
 *    Nothing breaks, nothing throws, and the screen becomes a strobe. Nothing but this test can
 *    see it, because the system notification it was modelled on is explicitly OnlyAlertOnce and
 *    replaces itself in place.
 *
 *  - The toast gets moved behind `canPost()`, which gates the SYSTEM notification on the
 *    POST_NOTIFICATIONS grant. That grant has nothing to do with drawing inside our own window,
 *    and the in-app pill would then silently stop appearing on a device where the user declined
 *    a permission for a surface they are not even looking at. Robolectric grants nothing by
 *    default, so this whole test runs in exactly that ungranted state.
 */
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
        // The pill is timed by an effect keyed on the toast's id. Rescanning an unchanged folder
        // twice produces byte-identical text, and if these shared an id the second one would key
        // nothing new: the pill would never re-appear and the user would have no sign the second
        // scan ran at all.
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
        // complete() is called with a null message by some callers and an empty string by others.
        // The pill draws its second line only when there is one, so an empty string has to become
        // null HERE rather than at the drawing end, which cannot see who sent it.
        assertNull(SystemToasts.normalise(""))
        assertNull(SystemToasts.normalise("   "))
        assertNull(SystemToasts.normalise(null))
        assertEquals("412 tracks", SystemToasts.normalise("412 tracks"))
    }
}
