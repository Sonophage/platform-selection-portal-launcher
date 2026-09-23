package com.psplauncher.feature.launcher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.psplauncher.core.data.repository.CoreInventory
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a detected RetroArch core profile puts in its intent.
 *
 * The bug this pins: PSPLauncher sent ROM and LIBRETRO and nothing else, and RetroArch came up
 * with no config file and no idea where its own folders were — a black screen for a game that
 * launched perfectly from inside RetroArch. Its log is what proved it; ours stopped after the
 * libretro path and the auto-start line, while the same intent carrying the set below went on to
 * report its config file, app dir, and default savefile, savestate, system and screenshot folders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class EmulatorDetectorRetroArchExtrasTest {

    private val retroArchPackage = "com.retroarch.aarch64"
    private val dataDir = "/data/data/$retroArchPackage"
    private val apk = "/data/app/~~abc==/$retroArchPackage-xyz==/base.apk"

    private fun detectorWithRetroArch(): EmulatorDetector {
        val pm = mockk<PackageManager>()
        // Only RetroArch is installed, so the catalog sweep finds nothing and the result is the
        // core profiles alone.
        every { pm.getPackageInfo(any<String>(), any<Int>()) } answers {
            if (firstArg<String>() == retroArchPackage) mockk()
            else throw PackageManager.NameNotFoundException()
        }
        every { pm.getApplicationInfo(retroArchPackage, any<Int>()) } returns
            ApplicationInfo().apply {
                packageName = retroArchPackage
                this.dataDir = this@EmulatorDetectorRetroArchExtrasTest.dataDir
                sourceDir = apk
            }
        val context = mockk<Context>()
        every { context.packageManager } returns pm
        return EmulatorDetector(context)
    }

    private fun mgbaProfile() = detectorWithRetroArch()
        .detect(CoreInventory.Verified(setOf("mgba_libretro_android.so")))
        .firstOrNull { it.autoSource == "retroarch-core" }

    @Test
    fun `a core profile carries the environment RetroArch's own launcher sends`() {
        val profile = assertNotNull(mgbaProfile(), "no RetroArch core profile was produced")
        val extras = profile.intentExtras

        // The two that name the content. These were always right.
        assertEquals("{rom_path}", extras["ROM"])
        assertTrue(extras["LIBRETRO"].orEmpty().endsWith("mgba_libretro_android.so"))

        // The five that were missing, and without which RetroArch has no config and no folders.
        assertEquals("$dataDir/retroarch.cfg", extras["CONFIGFILE"])
        assertEquals(dataDir, extras["DATADIR"])
        assertEquals(apk, extras["APK"])
        assertNotNull(extras["SDCARD"])
        assertTrue(
            extras["EXTERNAL"].orEmpty().endsWith("/Android/data/$retroArchPackage/files"),
            "EXTERNAL must point at RetroArch's own external files dir, got: ${extras["EXTERNAL"]}",
        )
    }

    @Test
    fun `paths are read off the installed package, never assumed`() {
        // RetroArch ships one package per ABI, so every path derived from it moves with the
        // package. A hardcoded /data/data/com.retroarch would be wrong on the build almost
        // everybody has.
        val profile = assertNotNull(mgbaProfile())
        profile.intentExtras.forEach { (key, value) ->
            if (key == "ROM") return@forEach
            assertTrue(
                !value.contains("com.retroarch") || value.contains(retroArchPackage),
                "$key names a RetroArch package that is not the installed one: $value",
            )
        }
    }
}
