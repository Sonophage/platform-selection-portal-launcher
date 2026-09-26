package com.psplauncher.feature.launcher

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import com.psplauncher.core.domain.model.EmulatorProfile
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.IntentType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EmulatorIntentResolverPreflightTest {
    private fun contextWith(
        packageInstalled: Boolean = true,
        openFd: () -> Unit = {},
    ): Context {
        val pm = mockk<PackageManager>()
        every { pm.getPackageInfo(any<String>(), any<Int>()) } answers {
            if (!packageInstalled) throw PackageManager.NameNotFoundException("gone")
            mockk()
        }

        every { pm.getActivityInfo(any(), any<Int>()) } returns mockk()
        val resolver = mockk<ContentResolver>()
        every { resolver.openFileDescriptor(any(), any()) } answers {
            openFd()
            null
        }
        return mockk<Context>().apply {
            every { this@apply.packageManager } returns pm
            every { this@apply.contentResolver } returns resolver
        }
    }

    private val romUri = "content://com.android.externalstorage.documents/document/roms%2Fgame.bin"

    private fun safGame() = Game(title = "Crash", platformId = "psx", romUri = romUri)

    private fun componentProfile() = EmulatorProfile(
        id = "test",
        name = "Emu",
        packageName = "com.emu",
        activityClass = "com.emu.Activity",
        intentType = IntentType.COMPONENT,
        supportedPlatformIds = listOf("psx"),
    )

    @Test
    fun `saf game whose rom grant is revoked fails with a reconnect hint`() {
        val context = contextWith { throw SecurityException("Permission Denial: opening provider") }
        val resolver = EmulatorIntentResolver(context, mockk(relaxed = true))

        val result = runBlocking { resolver.resolve(safGame(), componentProfile()) }

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()!!.message!!.contains("lost access", ignoreCase = true),
            "Expected a reconnect hint, got: ${result.exceptionOrNull()!!.message}",
        )
    }

    @Test
    fun `saf game whose rom is reachable passes the grant probe`() {
        val context = contextWith(openFd = {})
        val resolver = EmulatorIntentResolver(context, mockk(relaxed = true))

        val result = runBlocking { resolver.resolve(safGame(), componentProfile()) }

        assertTrue(
            result.exceptionOrNull()?.message?.contains("lost access", ignoreCase = true) != true,
            "reachable grant must not be refused by the probe; failure: ${result.exceptionOrNull()?.message}",
        )
    }

    private fun rawPathProfile() = EmulatorProfile(
        id = "retroarch",
        name = "RetroArch",
        packageName = "com.retroarch",
        activityClass = "com.retroarch.browser.retroactivity.RetroActivityFuture",
        intentType = IntentType.COMPONENT,
        supportedPlatformIds = listOf("psx"),
        intentExtras = mapOf("ROM" to "{rom_path}", "LIBRETRO" to "/data/core.so"),
    )

    @Test
    fun `a raw-path profile is refused when the path is missing, however healthy the URI is`() {
        val context = contextWith(openFd = {  })
        val resolver = EmulatorIntentResolver(context, mockk(relaxed = true))

        val game = Game(
            title = "Crash",
            platformId = "psx",
            romUri = romUri,
            romPath = "/storage/1A2B-3C4D/definitely/not/here.bin",
        )

        val failure = assertFailsWith<IllegalStateException> {
            runBlocking { resolver.validateBeforeLaunch(game, rawPathProfile()) }
        }
        assertTrue(
            failure.message.orEmpty().contains("not found"),
            "expected a file-not-found message, got: ${failure.message}",
        )
    }

    @Test
    fun `a raw-path profile with no path at all names the emulator that needs one`() {
        val resolver = EmulatorIntentResolver(contextWith(), mockk(relaxed = true))
        val game = Game(title = "Crash", platformId = "psx", romUri = romUri, romPath = null)

        val failure = assertFailsWith<IllegalStateException> {
            runBlocking { resolver.validateBeforeLaunch(game, rawPathProfile()) }
        }
        assertTrue(
            failure.message.orEmpty().contains("RetroArch"),
            "the message must name the emulator that needs the path, got: ${failure.message}",
        )
    }

    @Test
    fun `a path we cannot read is not a path the EMULATOR cannot read`() {
        val unreadable = File.createTempFile("rom", ".gba").apply {
            deleteOnExit()
            check(setReadable(false, false)) { "could not drop read permission on the fixture" }
        }
        check(!unreadable.canRead()) { "fixture is still readable; the test would prove nothing" }

        val resolver = EmulatorIntentResolver(contextWith(), mockk(relaxed = true))
        val game = Game(title = "Pokemon Unbound", platformId = "psx", romPath = unreadable.absolutePath)

        runBlocking { resolver.validateBeforeLaunch(game, rawPathProfile()) }
    }

    @Test
    fun `a URI-launching profile still only checks the grant`() {
        var probed = false
        val resolver = EmulatorIntentResolver(contextWith(openFd = { probed = true }), mockk(relaxed = true))

        runBlocking { resolver.validateBeforeLaunch(safGame(), componentProfile()) }

        assertTrue(probed, "a URI-launching profile must still probe the grant")
    }
}
