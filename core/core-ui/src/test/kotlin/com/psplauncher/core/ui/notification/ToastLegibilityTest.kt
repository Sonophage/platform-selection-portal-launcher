package com.psplauncher.core.ui.notification

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToastLegibilityTest {
    @Test
    fun `a blank message is absent, not empty`() {
        assertNull(SystemToasts.normalise(null))
        assertNull(SystemToasts.normalise(""))
        assertNull(SystemToasts.normalise("   "))
        assertNotNull(SystemToasts.normalise("12 new covers"))
    }
}
