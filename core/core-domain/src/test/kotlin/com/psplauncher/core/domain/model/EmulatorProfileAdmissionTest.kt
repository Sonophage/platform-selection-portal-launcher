package com.psplauncher.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorProfileAdmissionTest {
    private fun profile(
        id: String = "custom.emu",
        packageName: String = "org.example.emu",
        intentType: IntentType = IntentType.COMPONENT,
        customCommand: String? = null,
        intentAction: String? = null,
        intentFlags: List<String> = emptyList(),
        activityClass: String? = "org.example.emu.Main",
    ) = EmulatorProfile(
        id = id,
        name = "Example",
        packageName = packageName,
        activityClass = activityClass,
        intentType = intentType,
        supportedPlatformIds = listOf("psx"),
        customCommand = customCommand,
        intentAction = intentAction,
        intentFlags = intentFlags,
        isCustom = true,
    )

    private fun admit(p: EmulatorProfile) = EmulatorProfileAdmission.admit(listOf(p))

    @Test
    fun `an ordinary component profile is admitted unchanged`() {
        val p = profile()

        val result = admit(p)

        assertEquals(listOf(p), result.admitted)
        assertTrue(result.refused.isEmpty())
    }

    @Test
    fun `an action-view profile with no component is admitted`() {
        val p = profile(intentType = IntentType.ACTION_VIEW, activityClass = null)

        assertEquals(listOf(p), admit(p).admitted)
    }

    @Test
    fun `a custom-command profile is refused`() {
        val p = profile(intentType = IntentType.CUSTOM_COMMAND, customCommand = "su -c rm -rf /")

        val result = admit(p)

        assertTrue(result.admitted.isEmpty())
        assertEquals(1, result.refused.size)
        assertTrue(result.refused.single().reason.contains("custom command", ignoreCase = true))
    }

    @Test
    fun `a custom command on an otherwise ordinary profile is still refused`() {
        val p = profile(intentType = IntentType.COMPONENT, customCommand = "anything")

        assertTrue(admit(p).admitted.isEmpty())
    }

    @Test
    fun `a profile targeting this launcher's own package is refused`() {
        val p = profile(packageName = "com.psplauncher")

        val result = EmulatorProfileAdmission.admit(listOf(p), selfPackage = "com.psplauncher")

        assertTrue(result.admitted.isEmpty())
    }

    @Test
    fun `a malformed package name is refused`() {
        listOf("", "   ", "no-dots", "trailing.", ".leading", "has space.pkg", "a..b").forEach { pkg ->
            assertTrue("expected '$pkg' to be refused", admit(profile(packageName = pkg)).admitted.isEmpty())
        }
    }

    @Test
    fun `an unknown intent flag is refused rather than dropped`() {
        val p = profile(intentFlags = listOf("GRANT_WRITE_URI_PERMISSION"))

        assertTrue(admit(p).admitted.isEmpty())
    }

    @Test
    fun `known intent flags are accepted`() {
        val p = profile(intentFlags = listOf("NEW_TASK", "CLEAR_TOP", "CLEAR_TASK"))

        assertEquals(listOf(p), admit(p).admitted)
    }

    @Test
    fun `a component profile without an activity class is refused`() {
        val p = profile(intentType = IntentType.COMPONENT, activityClass = null)

        assertTrue(admit(p).admitted.isEmpty())
    }

    @Test
    fun `one bad profile does not discard the good ones`() {
        val good = profile(id = "good")
        val bad = profile(id = "bad", customCommand = "x")

        val result = EmulatorProfileAdmission.admit(listOf(good, bad))

        assertEquals(listOf(good), result.admitted)
        assertEquals(listOf("bad"), result.refused.map { it.id })
    }
}
