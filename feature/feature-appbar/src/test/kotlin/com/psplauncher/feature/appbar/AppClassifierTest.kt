package com.psplauncher.feature.appbar

import android.content.pm.ApplicationInfo
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a freshly installed app lands before the user has said otherwise.
 *
 * The curated list is a pile of string prefixes, and a wrong one is the worst kind of wrong: it
 * classifies nothing, throws nothing, and simply leaves the app in the App Drawer where the user
 * will conclude the feature does not work. Nothing else in the app can notice that. So every
 * package this list claims to know gets named here, once, with what it is.
 *
 * Two of the remote-play ids were read off the test tablet (`adb shell pm list packages`); the
 * rest are each app's published id and have not been seen on a device here. If one of them ever
 * turns out to be wrong, this is the file that says which claim was never verified.
 */
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
        // Network rather than Game, deliberately: they need a connection before they need a
        // controller, and they run nothing on this device.
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
        // Guard on the guard: an empty table would make every assertion below vacuous.
        assertEquals("the remote-play table must cover every curated client", 8, clients.size)
        for ((pkg, name) in clients) {
            assertEquals("$name ($pkg) must classify as Network", setOf(AppCategoryIds.NETWORK), categoryOf(pkg))
        }
    }

    @Test
    fun `GameNative is left alone`() {
        // It is already known to PcLauncherCatalog as a PC launcher, and PC games launch THROUGH
        // it. Curating it as a remote-play app too would put it in Network as itself while it
        // also backs a pile of entries under Game. The richer relationship wins; this one stays
        // unclassified and reachable from the App Drawer.
        assertTrue("GameNative must not be curated", categoryOf("app.gamenative").isEmpty())
    }

    @Test
    fun `the Steam mobile app is not mistaken for Steam Link`() {
        // Installed on the test tablet, and a near-miss for the com.valvesoftware.steamlink
        // prefix. It is a store and social app, not a streaming client, so it stays unclassified
        // rather than being guessed into Network.
        assertTrue(
            "the Steam mobile app must not match the Steam Link prefix",
            categoryOf("com.valvesoftware.android.steam.community").isEmpty(),
        )
    }

    @Test
    fun `an app nobody curated stays unclassified`() {
        // The control. Without it, a classifier that returned Network for everything would pass
        // every assertion above.
        assertTrue(categoryOf("com.example.nothing.in.particular").isEmpty())
    }

    @Test
    fun `the curated list still wins over the system category`() {
        // Resolution order matters: YouTube declares CATEGORY_VIDEO and is also curated, so this
        // only proves anything for a package where the two would disagree.
        assertEquals(
            setOf(AppCategoryIds.NETWORK),
            classifier.defaultCategories(
                app("com.limelight", label = "Moonlight", systemCategory = ApplicationInfo.CATEGORY_VIDEO),
            ),
        )
    }
}
