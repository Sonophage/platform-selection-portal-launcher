package com.psplauncher.feature.appbar

import android.content.pm.ApplicationInfo
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppClassifierTest {
    private val classifier = AppClassifier()

    private fun app(
        packageName: String,
        label: String = packageName,
        systemCategory: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    ) = InstalledApp(
        packageName = packageName,
        label = label,
        icon = mockk(relaxed = true),
        isGame = false,
        isEmulator = false,
        systemCategory = systemCategory,
    )

    private fun categoryOf(packageName: String): Set<String> = classifier.defaultCategories(app(packageName))

    @Test
    fun `remote play clients land in Network`() {
        val clients = mapOf(
            "com.limelight" to "Moonlight",
            "com.limelight.noir" to "Moonlight Noir (matched by the com.limelight prefix)",
            "com.boosteroid.streaming" to "Boosteroid",
            "com.metallic.chiaki" to "Chiaki",
            "com.valvesoftware.steamlink" to "Steam Link",
            "com.nvidia.geforcenow" to "GeForce NOW",
            "com.microsoft.xcloud" to "Xbox Cloud Gaming",
            "com.parsecgaming.parsec" to "Parsec",
        )

        assertEquals("the remote-play table must cover every curated client", 8, clients.size)
        for ((pkg, name) in clients) {
            assertEquals("$name ($pkg) must classify as Network", setOf(AppCategoryIds.NETWORK), categoryOf(pkg))
        }
    }

    @Test
    fun `GameNative is left alone`() {
        assertTrue("GameNative must not be curated", categoryOf("app.gamenative").isEmpty())
    }

    @Test
    fun `the Steam mobile app is not mistaken for Steam Link`() {
        assertTrue(
            "the Steam mobile app must not match the Steam Link prefix",
            categoryOf("com.valvesoftware.android.steam.community").isEmpty(),
        )
    }

    @Test
    fun `an app nobody curated stays unclassified`() {
        assertTrue(categoryOf("com.example.nothing.in.particular").isEmpty())
    }

    @Test
    fun `the curated list still wins over the system category`() {
        assertEquals(
            setOf(AppCategoryIds.NETWORK),
            classifier.defaultCategories(
                app("com.limelight", label = "Moonlight", systemCategory = ApplicationInfo.CATEGORY_VIDEO),
            ),
        )
    }
}
