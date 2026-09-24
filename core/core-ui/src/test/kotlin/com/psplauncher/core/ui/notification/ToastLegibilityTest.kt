package com.psplauncher.core.ui.notification

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What survives of the toast now that the pill is gone.
 *
 * The legibility floor moved with the text it governed — it is asserted against the notification
 * bar in feature-xmb, which is what a user now reads those sentences in. What is left here is the
 * feed itself: a message that is blank is absent, and the history is a history.
 */
class ToastLegibilityTest {

    @Test
    fun `a blank message is absent, not empty`() {
        // Callers of BackgroundTaskNotifier.complete pass null, "" and a real summary in roughly
        // equal measure, and the row draws its second line only when there is one.
        assertNull(SystemToasts.normalise(null))
        assertNull(SystemToasts.normalise(""))
        assertNull(SystemToasts.normalise("   "))
        assertNotNull(SystemToasts.normalise("12 new covers"))
    }
}
