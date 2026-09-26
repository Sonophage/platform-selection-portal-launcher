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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class EmulatorDetectorRetroArchExtrasTest {
    private val retroArchPackage = "com.retroarch.aarch64"
    private val dataDir = "/data/data/$retroArchPackage"
    private val apk = "/data/app/~~abc==/$retroArchPackage-xyz==/base.apk"

    private fun detectorWithRetroArch(): EmulatorDetector {
        val pm = mockk<PackageManager>()

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

        assertEquals("{rom_path}", extras["ROM"])
        assertTrue(extras["LIBRETRO"].orEmpty().endsWith("mgba_libretro_android.so"))

        val external = "${extras["SDCARD"]}/Android/data/$retroArchPackage/files"
        assertEquals("$external/retroarch.cfg", extras["CONFIGFILE"])
        assertEquals(external, extras["EXTERNAL"])
        assertEquals(dataDir, extras["DATADIR"])
        assertEquals(apk, extras["APK"])
        assertNotNull(extras["SDCARD"])
    }

    @Test
    fun `the config path never points inside the private data dir`() {
        val extras = assertNotNull(mgbaProfile()).intentExtras
        val config = assertNotNull(extras["CONFIGFILE"])
        assertTrue(
            !config.startsWith("$dataDir/"),
            "CONFIGFILE must not be under the private data dir, got: $config",
        )
        assertTrue(config.endsWith("/Android/data/$retroArchPackage/files/retroarch.cfg"))
    }

    @Test
    fun `paths are read off the installed package, never assumed`() {
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
