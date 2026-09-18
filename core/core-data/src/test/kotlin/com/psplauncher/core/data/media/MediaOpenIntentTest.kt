package com.psplauncher.core.data.media

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The half music, video and books share. Each of these pins something that fails silently: the app
 * opens, nothing crashes, and the user just sees the wrong thing happen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MediaOpenIntentTest {

    private val uri = "content://com.example/tree/doc"

    @Test
    fun `a chosen package is pinned`() {
        val intent = MediaOpenIntent.build(uri, "application/epub+zip", "com.flyersoft.moonreader")

        assertEquals("com.flyersoft.moonreader", intent.`package`)
        assertEquals("application/epub+zip", intent.type)
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }

    @Test
    fun `a null or blank package stays unpinned so the system can choose`() {
        // Blank is the shape a section's own sentinel arrives in once it has been mapped away.
        // Pinning it would aim the intent at a package name that does not exist.
        assertNull(MediaOpenIntent.build(uri, "audio/*", null).`package`)
        assertNull(MediaOpenIntent.build(uri, "audio/*", "").`package`)
        assertNull(MediaOpenIntent.build(uri, "audio/*", "   ").`package`)
    }

    @Test
    fun `the read grant rides along, or the target app resolves the uri and reads nothing`() {
        val intent = MediaOpenIntent.build(uri, "video/*", null)

        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `the chooser drops the pinned package, or it offers the app that just failed`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val pinned = MediaOpenIntent.build(uri, "video/*", "com.example.player")

        val error = MediaOpenIntent.launchChooser(
            context = context,
            intent = pinned,
            chooserTitle = "Play video with…",
            noHandlerMessage = "nothing installed",
            logLabel = "the file",
        )

        assertNull(error, "the chooser started, so there is nothing to report")
        val started = shadowOf(context).nextStartedActivity
        val target = started.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertNull(target?.`package`, "the retry must not be aimed back at the app that failed")
        assertEquals("video/*", target?.type)
    }

    @Test
    fun `the caller's intent is not mutated, so a failed launch can still be inspected`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val pinned = MediaOpenIntent.build(uri, "video/*", "com.example.player")

        MediaOpenIntent.launchChooser(context, pinned, "t", "none", "the file")

        assertEquals("com.example.player", pinned.`package`)
    }
}
