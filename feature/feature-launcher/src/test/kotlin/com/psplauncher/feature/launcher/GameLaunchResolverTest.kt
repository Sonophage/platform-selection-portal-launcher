package com.psplauncher.feature.launcher

import com.psplauncher.core.data.database.dao.PlatformDao
import com.psplauncher.core.data.database.entity.PlatformEntity
import com.psplauncher.core.data.repository.MemoryCardRepository
import com.psplauncher.core.domain.model.EmulatorProfile
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.IntentType
import com.psplauncher.core.domain.model.MemoryCard
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The gathering half of the launch ladder.
 *
 * [EmulatorLaunchResolverTest] pins the precedence itself; this pins that the three STORED rungs
 * actually reach it. They are what a caller assembling the ladder by hand leaves out, and one did:
 * the XMB's direct launch took the first available profile for the platform, which is rung four on
 * its own, so with direct launch on a game pinned through "Change Emulator" ran on something else.
 *
 * Every assertion here fails if a rung stops being read. That is the whole point of the file — the
 * precedence tests above it all pass while the values never arrive.
 */
class GameLaunchResolverTest {

    private fun profile(id: String, packageName: String = id) = EmulatorProfile(
        id                   = id,
        name                 = id,
        packageName          = packageName,
        intentType           = IntentType.ACTION_VIEW,
        supportedPlatformIds = listOf(PLATFORM),
        coreMap              = emptyMap(),
        isAvailable          = true,
    )

    private val pinned   = profile("pinned")
    private val card     = profile("card")
    private val fallback = profile("fallback")

    /** Every profile is installed and supports the platform; only the stored rungs differ. */
    private fun resolver(
        cardEmulatorId: String? = null,
        platformDefault: String? = null,
    ): GameLaunchResolver {
        val all = listOf(fallback, card, pinned)
        val profiles = mockk<EmulatorProfileRepository>()
        every { profiles.getInstalledProfiles() } returns all
        coEvery { profiles.getProfilesForPlatform(PLATFORM) } returns all

        val cards = mockk<MemoryCardRepository>()
        coEvery { cards.getById(PLATFORM) } returns cardEmulatorId?.let {
            MemoryCard(platformId = PLATFORM, displayName = "card", emulatorId = it)
        }

        val platforms = mockk<PlatformDao>()
        coEvery { platforms.getById(PLATFORM) } returns
            platformRow(platformDefault)

        return GameLaunchResolver(profiles, cards, platforms)
    }

    private fun game(override: String? = null) =
        Game(id = 1, title = "A Game", platformId = PLATFORM, romPath = "/roms/a.bin", emulatorPackage = override)

    @Test
    fun `the game's own emulator decides the launch`() = runTest {
        // Both lower rungs are set and point elsewhere, so a pass here cannot come from the
        // resolver simply ignoring everything and landing on the pinned profile by luck.
        val resolved = resolver(cardEmulatorId = "card", platformDefault = "fallback")
            .resolve(game(override = "pinned"))
            .getOrThrow()

        assertEquals("pinned", resolved.profile.id, "the per-game override did not reach the ladder")
        assertEquals(LaunchSource.PER_GAME_OVERRIDE, resolved.source)
    }

    @Test
    fun `the memory card's emulator decides it when the game names none`() = runTest {
        val resolved = resolver(cardEmulatorId = "card", platformDefault = "fallback")
            .resolve(game())
            .getOrThrow()

        assertEquals("card", resolved.profile.id, "the memory card's emulator did not reach the ladder")
        assertEquals(LaunchSource.MEMORY_CARD, resolved.source)
    }

    @Test
    fun `the platform default decides it when neither of those is set`() = runTest {
        val resolved = resolver(platformDefault = "card")
            .resolve(game())
            .getOrThrow()

        assertEquals("card", resolved.profile.id, "the platform default did not reach the ladder")
        assertEquals(LaunchSource.PLATFORM_DEFAULT, resolved.source)
    }

    @Test
    fun `with nothing configured it takes the first of the ordered list`() = runTest {
        val resolved = resolver().resolve(game()).getOrThrow()

        assertEquals("fallback", resolved.profile.id)
        assertEquals(LaunchSource.CATALOG_DEFAULT, resolved.source)
    }

    /**
     * The platform row a caller already holds is read instead of the dao's. Without this, passing
     * one would be a silent no-op and every caller would be resolving off the database anyway.
     */
    @Test
    fun `a platform row passed in is preferred over the stored one`() = runTest {
        val resolved = resolver(platformDefault = "fallback")
            .resolve(game(), platformRow("card"))
            .getOrThrow()

        assertEquals("card", resolved.profile.id, "the caller's platform row was ignored")
    }

    private fun platformRow(preferred: String?) = PlatformEntity(
        id                       = PLATFORM,
        name                     = "PlayStation",
        shortName                = "PSX",
        iconRes                  = null,
        accentColor              = 0L,
        preferredEmulatorPackage = preferred,
    )

    private companion object {
        const val PLATFORM = "psx"
    }
}
