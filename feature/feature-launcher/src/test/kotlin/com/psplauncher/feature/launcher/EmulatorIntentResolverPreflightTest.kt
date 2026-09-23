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

/**
 * Plain-JVM preflight tests for the B1 additions to [EmulatorIntentResolver]. The Robolectric suite
 * ([EmulatorIntentResolverTest]) covers intent shape; the revoked-SAF-grant path needs a content
 * resolver that actually throws SecurityException, which Robolectric's ShadowContentResolver cannot
 * produce — so these use a mockk context instead.
 */
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
        // COMPONENT preflight also asks for the pinned activity; any ActivityInfo satisfies it.
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

        // The probe itself is what we're pinning — a reachable URI must NOT be refused by it.
        assertTrue(
            result.exceptionOrNull()?.message?.contains("lost access", ignoreCase = true) != true,
            "reachable grant must not be refused by the probe; failure: ${result.exceptionOrNull()?.message}",
        )
    }

    // ── Raw-path profiles ─────────────────────────────────────────────────────
    //
    // RetroArch's generated profiles carry a single `"ROM" to "{rom_path}"` extra and no
    // attachRomData, so whatever romUri says, what reaches RetroArch is a string built by path
    // arithmetic from the SAF document id. Preflight used to check the URI anyway -- a SAF game
    // carries BOTH handles and the romUri branch came first -- so it opened something the emulator
    // would never receive, blessed the launch, and handed over a path nobody had verified.

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
        // The grant probe would pass here. It is irrelevant: this emulator never sees the URI.
        val context = contextWith(openFd = { /* the URI opens perfectly */ })
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
        // Pokemon Unbound on the SD card. PSPLauncher holds no all-files access, so canRead() is
        // false for everything under /storage/XXXX-XXXX — while RetroArch, targeting SDK 28 with
        // READ_EXTERNAL_STORAGE, opens the same file without trouble. Preflight used to block on
        // our answer and tell the user the emulator needed access it already had, so Play did
        // nothing and said nothing.
        //
        // Robolectric reports no all-files access, which is the case being pinned: unreadable
        // here is not evidence, and the launch must be allowed to reach the emulator.
        val unreadable = File.createTempFile("rom", ".gba").apply {
            deleteOnExit()
            check(setReadable(false, false)) { "could not drop read permission on the fixture" }
        }
        check(!unreadable.canRead()) { "fixture is still readable; the test would prove nothing" }

        val resolver = EmulatorIntentResolver(contextWith(), mockk(relaxed = true))
        val game = Game(title = "Pokemon Unbound", platformId = "psx", romPath = unreadable.absolutePath)

        // No throw: preflight defers to the emulator rather than guessing on our behalf.
        runBlocking { resolver.validateBeforeLaunch(game, rawPathProfile()) }
    }

    @Test
    fun `a URI-launching profile still only checks the grant`() {
        // The control. Without it, the two assertions above would pass against a preflight that
        // simply demanded a readable file path from everything -- which would break every SAF
        // game on every emulator that does take a content URI.
        var probed = false
        val resolver = EmulatorIntentResolver(contextWith(openFd = { probed = true }), mockk(relaxed = true))

        runBlocking { resolver.validateBeforeLaunch(safGame(), componentProfile()) }

        assertTrue(probed, "a URI-launching profile must still probe the grant")
    }
}
