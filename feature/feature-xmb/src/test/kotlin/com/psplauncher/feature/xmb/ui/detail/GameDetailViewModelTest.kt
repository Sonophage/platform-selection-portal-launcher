package com.psplauncher.feature.xmb.ui.detail

import android.content.Context
import app.cash.turbine.test
import com.psplauncher.core.data.database.dao.PlatformDao
import com.psplauncher.core.data.database.entity.PlatformEntity
import com.psplauncher.core.data.repository.MemoryCardRepository
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.MemoryCard
import com.psplauncher.core.domain.repository.GameRepository
import com.psplauncher.feature.artwork.api.ArtworkFetchResult
import com.psplauncher.feature.artwork.api.ArtworkRepository
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.feature.artwork.match.MatchProvider
import com.psplauncher.feature.artwork.match.MetadataApplyPolicy
import com.psplauncher.feature.artwork.match.MetadataField
import com.psplauncher.feature.artwork.match.MetadataPreset
import com.psplauncher.feature.artwork.match.MetadataPreview
import com.psplauncher.feature.artwork.store.ArtworkStore
import com.psplauncher.feature.launcher.EmulatorIntentResolver
import com.psplauncher.feature.launcher.EmulatorProfileRepository
import com.psplauncher.feature.launcher.byLaunchPreference
import com.psplauncher.feature.launcher.supportsPlatform
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var gameRepository: GameRepository
    private lateinit var platformDao: PlatformDao
    private lateinit var memoryCardRepository: MemoryCardRepository
    private lateinit var profileRepository: EmulatorProfileRepository
    private lateinit var intentResolver: EmulatorIntentResolver
    private lateinit var artworkRepository: ArtworkRepository
    private lateinit var artworkAccent: com.psplauncher.core.data.repository.ArtworkAccent
    private lateinit var artworkStore: ArtworkStore
    private lateinit var artworkRecordDao: com.psplauncher.core.data.database.dao.ArtworkRecordDao
    private lateinit var launchDispatcher: com.psplauncher.feature.launcher.LaunchDispatcher
    private lateinit var menuSound: com.psplauncher.core.ui.sound.MenuSoundPlayer
    private lateinit var pcGameExporter: com.psplauncher.feature.settings.pc.PcGameExporter
    private lateinit var viewModel: GameDetailViewModel

    private val fakeGame = Game(
        id                  = 1L,
        title               = "Crash Bandicoot",
        platformId          = "psx",
        romPath             = "/roms/psx/crash.bin",
        packageName         = null,
        emulatorPackage     = null,
        artworkUri          = null,
        heroUri             = null,
        logoUri             = null,
        description         = "A classic platformer.",
        developer           = "Naughty Dog",
        publisher           = "Sony",
        releaseYear         = 1996,
        genre               = "Platformer",
        steamGridDbId       = null,
        totalPlayTimeMillis = 7_200_000L,
        lastPlayedAt        = null,
        userNote            = null,
    )

    private val fakePlatform = PlatformEntity(
        id                       = "psx",
        name                     = "PlayStation",
        shortName                = "PS1",
        iconRes                  = null,
        accentColor              = 0xFF0070D1L,
        isPinnedToBar            = false,
        barPosition              = -1,
        preferredEmulatorPackage = null,
        romExtensions            = ".bin,.cue",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context           = mockk(relaxed = true)
        gameRepository    = mockk(relaxed = true)
        platformDao       = mockk(relaxed = true)
        memoryCardRepository = mockk(relaxed = true)
        profileRepository = mockk(relaxed = true)
        intentResolver    = mockk(relaxed = true)
        artworkRepository = mockk(relaxed = true)
        artworkAccent     = mockk(relaxed = true)

        coEvery { artworkAccent.of(*anyVararg()) } returns null
        artworkStore      = mockk(relaxed = true)
        artworkRecordDao  = mockk(relaxed = true)

        coEvery { artworkRecordDao.get(any(), any()) } returns null
        launchDispatcher  = mockk(relaxed = true)
        menuSound         = mockk(relaxed = true)
        pcGameExporter    = mockk(relaxed = true)

        coEvery { launchDispatcher.launch(any(), any(), any()) } returns
            com.psplauncher.feature.launcher.LaunchDispatchResult.Accepted

        coEvery { gameRepository.getById(1L) }    returns fakeGame
        coEvery { platformDao.getById("psx") }    returns fakePlatform
        coEvery { memoryCardRepository.getById("psx") } returns null
        every { profileRepository.getInstalledProfiles() }         returns emptyList()

        coEvery { profileRepository.getProfilesForPlatform(any()) } answers {
            val platformId = firstArg<String>()
            profileRepository.getInstalledProfiles()
                .filter { it.isAvailable && it.supportsPlatform(platformId) }
                .byLaunchPreference()
        }

        viewModel = newViewModel()
    }

    private fun newViewModel() = GameDetailViewModel(
            context           = context,
            gameRepository    = gameRepository,
            platformDao       = platformDao,
            collectionRepository = mockk(relaxed = true),
            profileRepository = profileRepository,
            intentResolver    = intentResolver,
            artworkRepository = artworkRepository,
            artworkAccent     = artworkAccent,
            artworkStore      = artworkStore,
            artworkRecordDao  = artworkRecordDao,
            menuSound         = menuSound,
            launcherShortcutRepository = mockk(relaxed = true),
            launchDispatcher  = launchDispatcher,

            launchResolver    = com.psplauncher.feature.launcher.GameLaunchResolver(
                profileRepository, memoryCardRepository, platformDao,
            ),
            pcGameExporter    = pcGameExporter,
        )

    @Test
    fun `the game's art accent reaches the state`() = runTest {
        coEvery { artworkAccent.of(*anyVararg()) } returns 0xFF1455D9L

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0xFF1455D9L, viewModel.uiState.value.artAccentArgb)
    }

    @Test
    fun `art with no hue leaves the page on the user's theme`() = runTest {
        coEvery { artworkAccent.of(*anyVararg()) } returns null

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.artAccentArgb)
    }

    @Test
    fun `opening another game never shows the previous game's colour`() = runTest {
        coEvery { artworkAccent.of(*anyVararg()) } returns 0xFF1455D9L
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0xFF1455D9L, viewModel.uiState.value.artAccentArgb)

        coEvery { gameRepository.getById(2L) } returns windowsGame
        coEvery { platformDao.getById("windows") } returns null
        coEvery { artworkAccent.of(*anyVararg()) } coAnswers {
            assertNull(
                "the outgoing game's accent was still on the page",
                viewModel.uiState.value.artAccentArgb,
            )
            0xFFE03B4FL
        }

        viewModel.loadGame(2L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0xFFE03B4FL, viewModel.uiState.value.artAccentArgb)
    }

    private val windowsGame = Game(
        id              = 2L,
        title           = "Portal 2",
        platformId      = "windows",
        packageName     = "banner.hub",
        launchIntentUri = "intent:#Intent;action=banner.hub.LAUNCH_GAME;S.localGameId=local_1f2e;end",
    )

    @Test
    fun `Export Game is offered for a Windows game, and not for a ROM game or an Android game`() = runTest {
        coEvery { gameRepository.getById(2L) } returns windowsGame
        coEvery { gameRepository.getById(3L) } returns
            Game(id = 3L, title = "Alto's Odyssey", platformId = "android", packageName = "com.noodlecake.altosodyssey")
        coEvery { platformDao.getById("windows") } returns null
        coEvery { platformDao.getById("android") } returns null

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(DetailAction.EXPORT in viewModel.uiState.value.visibleActions)

        viewModel.loadGame(3L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(DetailAction.EXPORT in viewModel.uiState.value.visibleActions)

        viewModel.loadGame(2L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(DetailAction.EXPORT in viewModel.uiState.value.visibleActions)
    }

    @Test
    fun `Export Game exports this game and shows what happened`() = runTest {
        coEvery { gameRepository.getById(2L) } returns windowsGame
        coEvery { platformDao.getById("windows") } returns null
        coEvery { pcGameExporter.exportGame(2L) } returns com.psplauncher.feature.settings.pc.PcGameExportReport(
            written = 1, skipped = 0, failed = 0, message = "Exported Portal 2 to windows/import as Portal 2.pfpgame.",
        )
        viewModel.loadGame(2L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.activateAction(DetailAction.EXPORT)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { pcGameExporter.exportGame(2L) }
        assertEquals("Exported Portal 2 to windows/import as Portal 2.pfpgame.", viewModel.uiState.value.actionMessage)
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun fakeLaunchIntent(): android.content.Intent = mockk(relaxed = true) {
        every { toUri(any()) } returns "intent://fake"
    }

    @Test
    fun `loadGame populates game and platform in state`() = runTest {
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals("Crash Bandicoot", state.game?.title)
            assertEquals("PlayStation",     state.platform?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadGame sets isLoading false when game not found`() = runTest {
        coEvery { gameRepository.getById(99L) } returns null
        viewModel.loadGame(99L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertNull(state.game)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadGame shows every stored video and screenshot in the strip`() = runTest {
        coEvery { artworkStore.findAll(1L, com.psplauncher.feature.artwork.store.ArtworkKind.VIDEO) } returns
            listOf("vid0", "vid1")
        coEvery { artworkStore.findAll(1L, com.psplauncher.feature.artwork.store.ArtworkKind.SCREENSHOT) } returns
            listOf("shot0", "shot1", "shot2")
        coEvery { artworkStore.find(1L, com.psplauncher.feature.artwork.store.ArtworkKind.TITLESCREEN, any()) } returns "title"
        coEvery { artworkStore.find(1L, com.psplauncher.feature.artwork.store.ArtworkKind.ICON1, any()) } returns null

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(
                listOf("vid0", "vid1", "shot0", "shot1", "shot2", "title"),
                state.detailMedia.map { it.uri },
            )
            assertEquals(listOf(true, true, false, false, false, false), state.detailMedia.map { it.isVideo })

            assertEquals("vid0", state.videoUri)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadGame falls back to the icon snap when the game has no full video`() = runTest {
        coEvery { artworkStore.findAll(1L, com.psplauncher.feature.artwork.store.ArtworkKind.VIDEO) } returns emptyList()
        coEvery { artworkStore.find(1L, com.psplauncher.feature.artwork.store.ArtworkKind.ICON1, any()) } returns "snap"
        coEvery { artworkStore.findAll(1L, com.psplauncher.feature.artwork.store.ArtworkKind.SCREENSHOT) } returns listOf("shot0")
        coEvery { artworkStore.find(1L, com.psplauncher.feature.artwork.store.ArtworkKind.TITLESCREEN, any()) } returns null

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(listOf("snap", "shot0"), state.detailMedia.map { it.uri })
            assertEquals(listOf(true, false), state.detailMedia.map { it.isVideo })
            assertEquals("snap", state.videoUri)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadGame exposes disc members and selects the primary by default`() = runTest {
        val setKey = "psx\u0001/roms/psx\u0001Final Fantasy VII"
        val primary = fakeGame.copy(
            id = 1L,
            title = "Final Fantasy VII",
            romPath = "/roms/psx/Final Fantasy VII.m3u",
            discSetKey = setKey,
            discNumber = null,
            isDiscPrimary = true,
        )
        val disc2 = fakeGame.copy(
            id = 2L,
            title = "Final Fantasy VII",
            romPath = "/roms/psx/Final Fantasy VII (Disc 2).cue",
            discSetKey = setKey,
            discNumber = 2,
            isDiscPrimary = false,
        )
        coEvery { gameRepository.getById(1L) } returns primary
        coEvery { gameRepository.getDiscSetMembers(setKey) } returns listOf(primary, disc2)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(listOf(2L, 1L), state.discMembers.map { it.id })
            assertEquals(1L, state.selectedDiscId)
            assertEquals(1L, state.selectedDisc?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadGame orders discs numerically while selecting requested disc`() = runTest {
        val setKey = "psx\u0001/roms/psx\u0001Final Fantasy VII"
        val primary = fakeGame.copy(id = 1L, discSetKey = setKey, isDiscPrimary = true, discNumber = 1)
        val disc2 = fakeGame.copy(id = 2L, discSetKey = setKey, discNumber = 2, isDiscPrimary = false)
        coEvery { gameRepository.getById(1L) } returns primary
        val disc10 = fakeGame.copy(id = 10L, discSetKey = setKey, discNumber = 10, isDiscPrimary = false)
        coEvery { gameRepository.getDiscSetMembers(setKey) } returns listOf(disc10, disc2, primary)

        viewModel.loadGame(1L, requestedDiscId = 2L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(listOf(1L, 2L, 10L), state.discMembers.map { it.id })
            assertEquals(2L, state.selectedDiscId)
            assertEquals(2L, state.selectedDisc?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadGame with an unknown requested disc falls back to the primary`() = runTest {
        val setKey = "psx\u0001/roms/psx\u0001Final Fantasy VII"
        val primary = fakeGame.copy(id = 1L, discSetKey = setKey, isDiscPrimary = true, discNumber = 1)
        val disc2 = fakeGame.copy(id = 2L, discSetKey = setKey, discNumber = 2, isDiscPrimary = false)
        coEvery { gameRepository.getById(1L) } returns primary
        coEvery { gameRepository.getDiscSetMembers(setKey) } returns listOf(primary, disc2)

        viewModel.loadGame(1L, requestedDiscId = 999L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1L, state.selectedDiscId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadGame with a requested disc launches that member directly`() = runTest {
        val setKey = "psx\u0001/roms/psx\u0001Final Fantasy VII"
        val primary = fakeGame.copy(id = 1L, discSetKey = setKey, isDiscPrimary = true, discNumber = 1)
        val disc2 = fakeGame.copy(id = 2L, discSetKey = setKey, discNumber = 2, isDiscPrimary = false)
        val fakeProfile = com.psplauncher.core.domain.model.EmulatorProfile(
            id = "duckstation",
            name = "DuckStation",
            packageName = "com.github.stenzek.duckstation",
            intentType = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("psx"),
        )
        coEvery { gameRepository.getById(1L) } returns primary
        coEvery { gameRepository.getById(2L) } returns disc2
        coEvery { gameRepository.getDiscSetMembers(setKey) } returns listOf(primary, disc2)
        every { profileRepository.getInstalledProfiles() } returns listOf(fakeProfile)
        coEvery { intentResolver.resolve(any(), any()) } returns Result.success(fakeLaunchIntent())

        viewModel.loadGame(1L, requestedDiscId = 2L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.launch()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { intentResolver.resolve(disc2, match { it.id == "duckstation" }) }
    }

    @Test
    fun `selecting a disc persists the preferred disc`() = runTest {
        val setKey = "psx\u0001/roms/psx\u0001Final Fantasy VII"
        val primary = fakeGame.copy(id = 1L, discSetKey = setKey, discNumber = 1, isDiscPrimary = true)
        val disc2 = fakeGame.copy(id = 2L, discSetKey = setKey, discNumber = 2, isDiscPrimary = false)
        coEvery { gameRepository.getById(1L) } returns primary
        coEvery { gameRepository.getDiscSetMembers(setKey) } returns listOf(primary, disc2)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.selectDisc(2L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { gameRepository.setPreferredDisc(1L, 2L) }
        assertEquals(2L, viewModel.uiState.value.selectedDiscId)
    }

    @Test
    fun `selecting a non-primary disc launches that member`() = runTest {
        val setKey = "psx\u0001/roms/psx\u0001Final Fantasy VII"
        val primary = fakeGame.copy(id = 1L, discSetKey = setKey, isDiscPrimary = true, discNumber = 1)
        val disc2 = fakeGame.copy(id = 2L, discSetKey = setKey, discNumber = 2, isDiscPrimary = false)
        val fakeProfile = com.psplauncher.core.domain.model.EmulatorProfile(
            id = "duckstation",
            name = "DuckStation",
            packageName = "com.github.stenzek.duckstation",
            intentType = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("psx"),
        )
        coEvery { gameRepository.getById(1L) } returns primary
        coEvery { gameRepository.getById(2L) } returns disc2
        coEvery { gameRepository.getDiscSetMembers(setKey) } returns listOf(primary, disc2)
        every { profileRepository.getInstalledProfiles() } returns listOf(fakeProfile)
        coEvery { intentResolver.resolve(any(), any()) } returns Result.success(fakeLaunchIntent())

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.selectDisc(2L)
        viewModel.launch()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { intentResolver.resolve(disc2, match { it.id == "duckstation" }) }
    }

    @Test
    fun `loadGame reports the catalog source when nothing is configured`() = runTest {
        val duckstation = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "duckstation",
            name                 = "DuckStation",
            packageName          = "com.github.stenzek.duckstation",
            intentType           = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("ps1"),
        )
        every { profileRepository.getInstalledProfiles() } returns listOf(duckstation)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("duckstation", state.resolvedLaunch?.profile?.id)
        assertEquals(
            com.psplauncher.feature.launcher.LaunchSource.CATALOG_DEFAULT,
            state.resolvedLaunch?.source,
        )
    }

    @Test
    fun `loadGame attributes a platform default when one is configured`() = runTest {
        val duckstation = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "duckstation",
            name                 = "DuckStation",
            packageName          = "com.github.stenzek.duckstation",
            intentType           = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("ps1"),
        )
        val retroarch = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "retroarch_aarch64",
            name                 = "RetroArch (64-bit)",
            packageName          = "com.retroarch.aarch64",
            intentType           = com.psplauncher.core.domain.model.IntentType.COMPONENT,
            supportedPlatformIds = listOf("psx"),
        )
        coEvery { platformDao.getById("psx") } returns
            fakePlatform.copy(preferredEmulatorPackage = "duckstation")
        every { profileRepository.getInstalledProfiles() } returns listOf(retroarch, duckstation)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("duckstation", state.resolvedLaunch?.profile?.id)
        assertEquals(
            com.psplauncher.feature.launcher.LaunchSource.PLATFORM_DEFAULT,
            state.resolvedLaunch?.source,
        )
    }

    @Test
    fun `confirming an emulator pick re-attributes the launch to a per-game override`() = runTest {
        val duckstation = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "duckstation",
            name                 = "DuckStation",
            packageName          = "com.github.stenzek.duckstation",
            intentType           = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("ps1"),
        )
        every { profileRepository.getInstalledProfiles() } returns listOf(duckstation)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { gameRepository.getById(1L) } returns fakeGame.copy(emulatorPackage = "duckstation")
        viewModel.confirmEmulatorPick("duckstation")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("duckstation", state.resolvedLaunch?.profile?.id)
        assertEquals(
            com.psplauncher.feature.launcher.LaunchSource.PER_GAME_OVERRIDE,
            state.resolvedLaunch?.source,
        )
        assertTrue(state.actionMessage!!.contains("Emulator set to DuckStation"))
    }

    @Test
    fun `toggleFavorite calls repository and flips isFavorite in state`() = runTest {
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleFavorite()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { gameRepository.setFavorite(1L, true) }

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.game?.isFavorite == true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startEditNote sets isEditingNote true`() = runTest {
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.startEditNote()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            assertTrue(awaitItem().isEditingNote)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveNote persists and clears editing state`() = runTest {
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.startEditNote()
        viewModel.onNoteChanged("Great game!")
        viewModel.saveNote()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { gameRepository.updateNote(1L, "Great game!") }

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isEditingNote)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveNote with blank text persists null`() = runTest {
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.startEditNote()
        viewModel.onNoteChanged("   ")
        viewModel.saveNote()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { gameRepository.updateNote(1L, null) }
    }

    @Test
    fun `cancelNote clears isEditingNote without saving`() = runTest {
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.startEditNote()
        viewModel.onNoteChanged("unsaved change")
        viewModel.cancelNote()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { gameRepository.updateNote(any(), any()) }

        viewModel.uiState.test {
            assertFalse(awaitItem().isEditingNote)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `launch sets launchError when no emulator is installed`() = runTest {
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.launch()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            assertNotNull(awaitItem().launchError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `launch emits intent when profile and resolver both succeed`() = runTest {
        val fakeProfile = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "ppsspp",
            name                 = "PPSSPP",
            packageName          = "org.ppsspp.ppsspp",
            intentType           = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("psx"),
        )
        val fakeIntent = fakeLaunchIntent()
        every { profileRepository.getInstalledProfiles() }        returns listOf(fakeProfile)
        coEvery { profileRepository.getProfilesForPlatform("psx") } returns listOf(fakeProfile)
        coEvery { intentResolver.resolve(any(), any()) }            returns Result.success(fakeIntent)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.launch()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { launchDispatcher.launch(any(), any(), fakeIntent) }
    }

    @Test
    fun `launch refuses a missing game even when an emulator is available`() = runTest {
        val missingGame = fakeGame.copy(isMissing = true)
        val fakeProfile = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "ppsspp",
            name                 = "PPSSPP",
            packageName          = "org.ppsspp.ppsspp",
            intentType           = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("psx"),
        )
        coEvery { gameRepository.getById(1L) }                    returns missingGame
        every { profileRepository.getInstalledProfiles() }        returns listOf(fakeProfile)
        coEvery { profileRepository.getProfilesForPlatform("psx") } returns listOf(fakeProfile)
        coEvery { intentResolver.resolve(any(), any()) }            returns Result.success(fakeLaunchIntent())

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.launch()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { intentResolver.resolve(any(), any()) }
        coVerify(exactly = 0) { launchDispatcher.launch(any(), any(), any()) }

        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull(state.launchError)
            assertNull(state.actionMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `launch never plays the menu launch chime even with GameBoot off`() = runTest {
        val fakeProfile = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "ppsspp",
            name                 = "PPSSPP",
            packageName          = "org.ppsspp.ppsspp",
            intentType           = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("psx"),
        )
        every { profileRepository.getInstalledProfiles() }         returns listOf(fakeProfile)
        coEvery { profileRepository.getProfilesForPlatform("psx") } returns listOf(fakeProfile)
        coEvery { intentResolver.resolve(any(), any()) }            returns Result.success(fakeLaunchIntent())

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.launch(playSound = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { launchDispatcher.launch(any(), any(), any()) }
        verify(exactly = 0) { menuSound.play(com.psplauncher.core.ui.sound.MenuSound.LAUNCH, any()) }
        verify(exactly = 1) { menuSound.play(com.psplauncher.core.ui.sound.MenuSound.SELECT, any()) }
    }

    @Test
    fun `direct-launch auto-fire plays no menu sound`() = runTest {
        val fakeProfile = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "ppsspp",
            name                 = "PPSSPP",
            packageName          = "org.ppsspp.ppsspp",
            intentType           = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("psx"),
        )
        every { profileRepository.getInstalledProfiles() }         returns listOf(fakeProfile)
        coEvery { profileRepository.getProfilesForPlatform("psx") } returns listOf(fakeProfile)
        coEvery { intentResolver.resolve(any(), any()) }            returns Result.success(fakeLaunchIntent())

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.launch(playSound = false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { launchDispatcher.launch(any(), any(), any()) }
        verify(exactly = 0) { menuSound.play(any(), any()) }
    }

    @Test
    fun `launch succeeds once a previously missing game is seen again`() = runTest {
        val fakeProfile = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "ppsspp",
            name                 = "PPSSPP",
            packageName          = "org.ppsspp.ppsspp",
            intentType           = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("psx"),
        )
        val fakeIntent = fakeLaunchIntent()

        coEvery { gameRepository.getById(1L) }                    returns fakeGame.copy(isMissing = false)
        every { profileRepository.getInstalledProfiles() }        returns listOf(fakeProfile)
        coEvery { profileRepository.getProfilesForPlatform("psx") } returns listOf(fakeProfile)
        coEvery { intentResolver.resolve(any(), any()) }            returns Result.success(fakeIntent)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.launch()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { launchDispatcher.launch(any(), any(), fakeIntent) }
    }

    @Test
    fun `launch uses per-game emulator override before platform default`() = runTest {
        val overrideGame = fakeGame.copy(emulatorPackage = "duckstation")
        val platformDefault = fakePlatform.copy(preferredEmulatorPackage = "retroarch_aarch64")
        val duckstation = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "duckstation",
            name                 = "DuckStation",
            packageName          = "com.github.stenzek.duckstation",
            intentType           = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("ps1"),
        )
        val retroarch = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "retroarch_aarch64",
            name                 = "RetroArch (64-bit)",
            packageName          = "com.retroarch.aarch64",
            intentType           = com.psplauncher.core.domain.model.IntentType.COMPONENT,
            supportedPlatformIds = listOf("psx"),
        )
        val fakeIntent = fakeLaunchIntent()
        coEvery { gameRepository.getById(1L) } returns overrideGame
        coEvery { platformDao.getById("psx") } returns platformDefault
        every { profileRepository.getInstalledProfiles() } returns listOf(retroarch, duckstation)
        coEvery { profileRepository.getProfilesForPlatform("psx") } returns listOf(retroarch, duckstation)
        coEvery { intentResolver.resolve(any(), any()) } returns Result.success(fakeIntent)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.launch()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { intentResolver.resolve(overrideGame, match { it.id == "duckstation" }) }
    }

    @Test
    fun `launch uses memory card emulator when game has no emulator override`() = runTest {
        val platformDefault = fakePlatform.copy(preferredEmulatorPackage = "retroarch_aarch64")
        val memoryCard = MemoryCard(
            platformId = "psx",
            displayName = "PlayStation Memory Card",
            emulatorId = "duckstation",
        )
        val duckstation = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "duckstation",
            name                 = "DuckStation",
            packageName          = "com.github.stenzek.duckstation",
            intentType           = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("ps1"),
        )
        val retroarch = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "retroarch_aarch64",
            name                 = "RetroArch (64-bit)",
            packageName          = "com.retroarch.aarch64",
            intentType           = com.psplauncher.core.domain.model.IntentType.COMPONENT,
            supportedPlatformIds = listOf("psx"),
        )
        val fakeIntent = fakeLaunchIntent()
        coEvery { platformDao.getById("psx") } returns platformDefault
        coEvery { memoryCardRepository.getById("psx") } returns memoryCard
        every { profileRepository.getInstalledProfiles() } returns listOf(retroarch, duckstation)
        coEvery { profileRepository.getProfilesForPlatform("psx") } returns listOf(retroarch, duckstation)
        coEvery { intentResolver.resolve(any(), any()) } returns Result.success(fakeIntent)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.launch()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { intentResolver.resolve(fakeGame, match { it.id == "duckstation" }) }
    }

    @Test
    fun `launch uses platform default when game has no emulator override`() = runTest {
        val platformDefault = fakePlatform.copy(preferredEmulatorPackage = "duckstation")
        val duckstation = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "duckstation",
            name                 = "DuckStation",
            packageName          = "com.github.stenzek.duckstation",
            intentType           = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("ps1"),
        )
        val retroarch = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "retroarch_aarch64",
            name                 = "RetroArch (64-bit)",
            packageName          = "com.retroarch.aarch64",
            intentType           = com.psplauncher.core.domain.model.IntentType.COMPONENT,
            supportedPlatformIds = listOf("psx"),
        )
        val fakeIntent = fakeLaunchIntent()
        coEvery { platformDao.getById("psx") } returns platformDefault
        every { profileRepository.getInstalledProfiles() } returns listOf(retroarch, duckstation)
        coEvery { profileRepository.getProfilesForPlatform("psx") } returns listOf(retroarch, duckstation)
        coEvery { intentResolver.resolve(any(), any()) } returns Result.success(fakeIntent)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.launch()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { intentResolver.resolve(fakeGame, match { it.id == "duckstation" }) }
    }

    @Test
    fun `launch sets launchError when resolver returns failure for missing ROM`() = runTest {
        val fakeProfile = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "ppsspp",
            name                 = "PPSSPP",
            packageName          = "org.ppsspp.ppsspp",
            intentType           = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
            supportedPlatformIds = listOf("psx"),
        )
        every { profileRepository.getInstalledProfiles() }        returns listOf(fakeProfile)
        coEvery { profileRepository.getProfilesForPlatform("psx") } returns listOf(fakeProfile)
        coEvery { intentResolver.resolve(any(), any()) }            returns Result.failure(
            IllegalStateException("ROM file not found: /roms/psx/crash.bin")
        )

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.launch()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull(state.launchError)
            assertTrue(state.launchError!!.contains("ROM file not found"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `launch sets launchError when resolver returns failure for uninstalled emulator`() = runTest {
        val fakeProfile = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "retroarch_aarch64",
            name                 = "RetroArch (64-bit)",
            packageName          = "com.retroarch.aarch64",
            intentType           = com.psplauncher.core.domain.model.IntentType.COMPONENT,
            activityClass        = "com.retroarch.browser.retroactivity.RetroActivityFuture",
            supportedPlatformIds = listOf("psx"),
        )
        every { profileRepository.getInstalledProfiles() }        returns listOf(fakeProfile)
        coEvery { profileRepository.getProfilesForPlatform("psx") } returns listOf(fakeProfile)
        coEvery { intentResolver.resolve(any(), any()) }            returns Result.failure(
            IllegalStateException("Emulator not installed: RetroArch (64-bit) (com.retroarch.aarch64)")
        )

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.launch()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull(state.launchError)
            assertTrue(state.launchError!!.contains("Emulator not installed"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `launch sets launchError when resolver returns failure for missing RetroArch core`() = runTest {
        val fakeProfile = com.psplauncher.core.domain.model.EmulatorProfile(
            id                   = "retroarch_aarch64",
            name                 = "RetroArch (64-bit)",
            packageName          = "com.retroarch.aarch64",
            intentType           = com.psplauncher.core.domain.model.IntentType.COMPONENT,
            activityClass        = "com.retroarch.browser.retroactivity.RetroActivityFuture",
            supportedPlatformIds = listOf("psx"),
            coreMap              = mapOf("psx" to "/data/data/com.retroarch.aarch64/cores/pcsx_rearmed.so"),
        )
        every { profileRepository.getInstalledProfiles() }        returns listOf(fakeProfile)
        coEvery { profileRepository.getProfilesForPlatform("psx") } returns listOf(fakeProfile)
        coEvery { intentResolver.resolve(any(), any()) }            returns Result.failure(
            IllegalStateException("RetroArch core not found: /data/data/com.retroarch.aarch64/cores/pcsx_rearmed.so")
        )

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.launch()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull(state.launchError)
            assertTrue(state.launchError!!.contains("RetroArch core not found"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onLaunchFailed sets launchError in state`() = runTest {
        viewModel.onLaunchFailed("Emulator not found. Is it installed?")

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Emulator not found. Is it installed?", state.launchError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissLaunchError clears launchError`() = runTest {
        viewModel.onLaunchFailed("some error")
        viewModel.dismissLaunchError()

        viewModel.uiState.test {
            assertNull(awaitItem().launchError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requestLaunchHelp raises the recovery sheet through the dispatcher`() = runTest {
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onLaunchFailed("Emulator not found. Is it installed?")

        viewModel.requestLaunchHelp()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            launchDispatcher.requestRecovery(
                match { it.id == 1L },
                any(),
                eq("Emulator not found. Is it installed?"),
            )
        }
    }

    @Test
    fun `requestLaunchHelp is a no-op when there is no launch error`() = runTest {
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestLaunchHelp()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { launchDispatcher.requestRecovery(any(), any(), any()) }
    }

    @Test
    fun `prepareForOpen clears stale closed state before reopening same game`() = runTest {
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleGamepadAction(com.psplauncher.core.domain.model.GamepadAction.BACK)
        assertTrue(viewModel.uiState.value.closed)

        viewModel.prepareForOpen()
        assertFalse(viewModel.uiState.value.closed)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.closed)
            assertEquals("Crash Bandicoot", state.game?.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fetchArtwork evicts only this game's refs and never wipes the library`() = runTest {
        coEvery { artworkRepository.fetchArtworkForGame(any(), any()) } returns
            ArtworkFetchResult(gameId = 1L, title = "Crash Bandicoot", success = true)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.fetchArtwork()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { artworkRepository.evictFromImageCache(any()) }
        coVerify(exactly = 0) { artworkRepository.clearCache() }

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isFetchingArtwork)
            assertEquals("Artwork updated", state.artworkMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissArtworkMessage clears artworkMessage`() = runTest {
        coEvery { artworkRepository.fetchArtworkForGame(any(), any()) } returns
            ArtworkFetchResult(1L, "Crash", success = true)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.fetchArtwork()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dismissArtworkMessage()

        viewModel.uiState.test {
            assertNull(awaitItem().artworkMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val metadataCurrent = mapOf<MetadataField, Any?>(
        MetadataField.DESCRIPTION to "A classic platformer.",
        MetadataField.DEVELOPER to null,
    )
    private val ssPreset = MetadataPreset(
        provider = MatchProvider.SCREENSCRAPER,
        description = "Bandicoot jumps.",
        developer = "Naughty Dog",
    )
    private val secondPreset = MetadataPreset(provider = MatchProvider.IGDB, description = "IGDB text")

    private fun openLoadedPreview(presets: List<MetadataPreset> = listOf(ssPreset, secondPreset)) {
        coEvery { artworkRepository.fetchMetadataPreview(1L) } returns MetadataPreview(metadataCurrent, presets)
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPageLaidOut()
        viewModel.activateAction(DetailAction.METADATA)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `Update Metadata previews Current vs Incoming and writes nothing until applied`() = runTest {
        openLoadedPreview()

        val preview = viewModel.uiState.value.metadataPreview!!
        assertFalse(preview.loading)
        assertEquals(MatchProvider.SCREENSCRAPER, preview.preset?.provider)

        assertEquals(MetadataApplyPolicy.FILL_MISSING_ONLY, preview.policy)
        assertEquals(setOf(MetadataField.DEVELOPER), preview.willWrite)
        assertEquals(preview.applyIndex, preview.focus)
        coVerify(exactly = 0) { artworkRepository.applyMetadata(any(), any(), any(), any()) }
    }

    @Test
    fun `Back closes the metadata preview, not the page, and writes nothing`() = runTest {
        openLoadedPreview()

        viewModel.handleGamepadAction(GamepadAction.BACK)

        assertNull(viewModel.uiState.value.metadataPreview)
        assertFalse(viewModel.uiState.value.closed)
        coVerify(exactly = 0) { artworkRepository.applyMetadata(any(), any(), any(), any()) }
    }

    @Test
    fun `a metadata preview closed while loading is not reopened by the late answer`() = runTest {
        coEvery { artworkRepository.fetchMetadataPreview(1L) } returns MetadataPreview(metadataCurrent, listOf(ssPreset))
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openMetadataPreview()
        viewModel.closeMetadataPreview()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.metadataPreview)
    }

    @Test
    fun `no provider metadata keeps the preview open and explains it until dismissed`() = runTest {
        openLoadedPreview(presets = emptyList())

        val preview = viewModel.uiState.value.metadataPreview!!
        assertFalse(preview.loading)
        assertTrue(preview.nothingFound)
        assertFalse(preview.failed)
        assertNull(viewModel.uiState.value.actionMessage)

        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(MetadataApplyPolicy.FILL_MISSING_ONLY, viewModel.uiState.value.metadataPreview?.policy)
        viewModel.handleGamepadAction(GamepadAction.SELECT)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.metadataPreview)
        assertFalse(viewModel.uiState.value.closed)
        coVerify(exactly = 0) { artworkRepository.applyMetadata(any(), any(), any(), any()) }
    }

    @Test
    fun `a failed metadata retrieval stays open and is told apart from nothing found`() = runTest {
        coEvery { artworkRepository.fetchMetadataPreview(1L) } throws java.io.IOException("offline")
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.activateAction(DetailAction.METADATA)
        testDispatcher.scheduler.advanceUntilIdle()

        val preview = viewModel.uiState.value.metadataPreview!!
        assertFalse(preview.loading)
        assertTrue(preview.nothingFound)
        assertTrue(preview.failed)

        viewModel.handleGamepadAction(GamepadAction.BACK)

        assertNull(viewModel.uiState.value.metadataPreview)
        assertFalse(viewModel.uiState.value.closed)
    }

    @Test
    fun `tapping the empty preview's button closes it without a write`() = runTest {
        openLoadedPreview(presets = emptyList())

        viewModel.applyMetadataPreview()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.metadataPreview)
        coVerify(exactly = 0) { artworkRepository.applyMetadata(any(), any(), any(), any()) }
    }

    @Test
    fun `Apply writes under the selected policy and reloads the game`() = runTest {
        openLoadedPreview()
        coEvery { artworkRepository.applyMetadata(any(), any(), any(), any()) } returns
            setOf(MetadataField.DESCRIPTION, MetadataField.DEVELOPER)

        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(MetadataApplyPolicy.REPLACE_ALL, viewModel.uiState.value.metadataPreview?.policy)
        viewModel.handleGamepadAction(GamepadAction.SELECT)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            artworkRepository.applyMetadata(
                1L, ssPreset, MetadataApplyPolicy.REPLACE_ALL,
                setOf(MetadataField.DESCRIPTION, MetadataField.DEVELOPER),
            )
        }
        coVerify(atLeast = 2) { gameRepository.getById(1L) }
        assertNull(viewModel.uiState.value.metadataPreview)
        assertEquals("Updated 2 fields from ScreenScraper", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun `Keep Current closes without calling the writer`() = runTest {
        openLoadedPreview()

        viewModel.selectMetadataPolicy(MetadataApplyPolicy.KEEP_CURRENT)
        viewModel.applyMetadataPreview()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.metadataPreview)
        assertEquals("Kept current metadata", viewModel.uiState.value.actionMessage)
        coVerify(exactly = 0) { artworkRepository.applyMetadata(any(), any(), any(), any()) }
    }

    @Test
    fun `the metadata overlay takes controller input once its rows arrive`() = runTest {
        openLoadedPreview()

        assertTrue(GameDetailKeys.METADATA_APPLY in viewModel.focusableNodeKeys())
        assertEquals(GameDetailKeys.METADATA_APPLY, viewModel.uiState.value.navFocusKey)

        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_UP)

        assertEquals(
            GameDetailKeys.metadataField(MetadataField.DEVELOPER.name),
            viewModel.uiState.value.navFocusKey,
        )
    }

    @Test
    fun `switching source re-ticks its changes and toggling a row chooses fields`() = runTest {
        openLoadedPreview()

        viewModel.handleGamepadAction(GamepadAction.NEXT_CATEGORY)
        var preview = viewModel.uiState.value.metadataPreview!!
        assertEquals(MatchProvider.IGDB, preview.preset?.provider)
        assertEquals(setOf(MetadataField.DESCRIPTION), preview.chosen)
        assertEquals(preview.applyIndex, preview.focus)

        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_UP)
        viewModel.handleGamepadAction(GamepadAction.SELECT)

        preview = viewModel.uiState.value.metadataPreview!!
        assertEquals(MetadataApplyPolicy.CHOOSE_FIELDS, preview.policy)
        assertTrue(preview.chosen.isEmpty())
        assertTrue(preview.willWrite.isEmpty())
    }

    private fun loadedAndLaidOut() {
        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onPageLaidOut()
    }

    @Test
    fun `input before the game loads is dropped and never replayed`() = runTest {
        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_DOWN)
        assertNull(viewModel.uiState.value.navFocusKey)

        viewModel.loadGame(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(GameDetailKeys.LAUNCH, viewModel.uiState.value.navFocusKey)
        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(GameDetailKeys.OPTIONS, viewModel.uiState.value.navFocusKey)
    }

    @Test
    fun `Options owns every input while open and hands the page its cursor back`() = runTest {
        loadedAndLaidOut()
        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(GameDetailKeys.OPTIONS, viewModel.uiState.value.navFocusKey)

        viewModel.handleGamepadAction(GamepadAction.OPEN_CONTEXT_MENU)
        assertTrue(viewModel.uiState.value.showOptions)
        assertEquals(
            GameDetailKeys.option(DetailAction.FAVORITE.name),
            viewModel.uiState.value.navFocusKey,
        )

        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals(
            GameDetailKeys.option(DetailAction.COLLECTIONS.name),
            viewModel.uiState.value.navFocusKey,
        )

        viewModel.handleGamepadAction(GamepadAction.BACK)
        assertFalse(viewModel.uiState.value.showOptions)
        assertFalse(viewModel.uiState.value.closed)

        assertEquals(GameDetailKeys.OPTIONS, viewModel.uiState.value.navFocusKey)

        viewModel.handleGamepadAction(GamepadAction.BACK)
        assertTrue(viewModel.uiState.value.closed)
    }

    @Test
    fun `a page action cannot fire through the Options overlay`() = runTest {
        loadedAndLaidOut()

        assertEquals(GameDetailKeys.LAUNCH, viewModel.uiState.value.navFocusKey)

        viewModel.handleGamepadAction(GamepadAction.OPEN_CONTEXT_MENU)
        viewModel.handleGamepadAction(GamepadAction.SELECT)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.game?.isFavorite == true)
        coVerify { gameRepository.setFavorite(1L, true) }
        assertNull(viewModel.uiState.value.launchError)
    }

    @Test
    fun `a multi-disc set exposes one node per disc and confirming one keeps the cursor there`() = runTest {
        val setKey = "psx\u0001/roms/psx\u0001Final Fantasy VII"
        val primary = fakeGame.copy(id = 1L, discSetKey = setKey, discNumber = 1, isDiscPrimary = true)
        val disc2 = fakeGame.copy(id = 2L, discSetKey = setKey, discNumber = 2, isDiscPrimary = false)
        coEvery { gameRepository.getById(1L) } returns primary
        coEvery { gameRepository.getDiscSetMembers(setKey) } returns listOf(primary, disc2)

        loadedAndLaidOut()
        assertTrue(GameDetailKeys.disc(1L) in viewModel.focusableNodeKeys())
        assertTrue(GameDetailKeys.disc(2L) in viewModel.focusableNodeKeys())
        assertEquals(GameDetailKeys.LAUNCH, viewModel.uiState.value.navFocusKey)

        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals(GameDetailKeys.disc(2L), viewModel.uiState.value.navFocusKey)

        viewModel.handleGamepadAction(GamepadAction.SELECT)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2L, viewModel.uiState.value.selectedDiscId)

        assertEquals(GameDetailKeys.disc(2L), viewModel.uiState.value.navFocusKey)
    }

    @Test
    fun `a package-backed game exposes no emulator nodes`() = runTest {
        coEvery { gameRepository.getById(3L) } returns
            Game(id = 3L, title = "Alto's Odyssey", platformId = "android", packageName = "com.noodlecake.altosodyssey")
        coEvery { platformDao.getById("android") } returns null

        viewModel.loadGame(3L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onPageLaidOut()

        val keys = viewModel.focusableNodeKeys()
        assertTrue(GameDetailKeys.LAUNCH in keys)
        assertTrue(GameDetailKeys.OPTIONS in keys)
        assertTrue(GameDetailKeys.FAVORITE in keys)
    }

    @Test
    fun `a missing manual leaves no focusable ghost node`() = runTest {
        coEvery { artworkStore.find(1L, com.psplauncher.feature.artwork.store.ArtworkKind.MANUAL) } returns null

        loadedAndLaidOut()

        assertFalse(DetailQuickAction.MANUAL in viewModel.uiState.value.visibleDetailRows)
        assertFalse(viewModel.uiState.value.hasManual)

        viewModel.openDetailsMenu()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showDetailsMenu)
        assertFalse(
            GameDetailKeys.detailsRow(DetailQuickAction.MANUAL.name) in viewModel.focusableNodeKeys(),
        )
        assertNull(viewModel.uiState.value.manualViewerUri)
    }

    @Test
    fun `a manual only in the portable library is enabled and opens`() = runTest {
        coEvery { artworkStore.find(1L, com.psplauncher.feature.artwork.store.ArtworkKind.MANUAL) } returns null
        coEvery {
            artworkRecordDao.get(1L, com.psplauncher.feature.artwork.store.ArtworkKind.MANUAL.name)
        } returns mockk { every { documentUri } returns "content://library/psx/manuals/crash.pdf" }

        loadedAndLaidOut()

        assertTrue(viewModel.uiState.value.hasManual)
        assertTrue(DetailQuickAction.MANUAL in viewModel.uiState.value.visibleDetailRows)

        viewModel.onDetailsRowTapped(DetailQuickAction.MANUAL)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("content://library/psx/manuals/crash.pdf", viewModel.uiState.value.manualViewerUri)
    }

    @Test
    fun `Details opens its dropdown, and its Options row opens the full menu`() = runTest {
        loadedAndLaidOut()

        viewModel.openDetailsMenu()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showDetailsMenu)
        assertEquals(
            GameDetailKeys.detailsRow(DetailQuickAction.FAVORITE.name),
            viewModel.uiState.value.navFocusKey,
        )

        viewModel.onDetailsRowTapped(DetailQuickAction.OPTIONS)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showDetailsMenu)
        assertTrue(viewModel.uiState.value.showOptions)
        assertEquals(
            GameDetailKeys.option(DetailAction.FAVORITE.name),
            viewModel.uiState.value.navFocusKey,
        )
    }

    @Test
    fun `Back closes the Details dropdown before it closes the page`() = runTest {
        loadedAndLaidOut()
        viewModel.openDetailsMenu()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showDetailsMenu)

        viewModel.handleGamepadAction(GamepadAction.BACK)
        assertFalse(viewModel.uiState.value.showDetailsMenu)
        assertFalse(viewModel.uiState.value.closed)

        assertEquals(GameDetailKeys.LAUNCH, viewModel.uiState.value.navFocusKey)

        viewModel.handleGamepadAction(GamepadAction.BACK)
        assertTrue(viewModel.uiState.value.closed)
    }

    @Test
    fun `a tap and a Cross press on the same node do the same thing`() = runTest {
        loadedAndLaidOut()

        viewModel.onNodeTapped(GameDetailKeys.FAVORITE)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(GameDetailKeys.FAVORITE, viewModel.uiState.value.navFocusKey)

        assertFalse(viewModel.uiState.value.cursorVisible)
    }

    private fun installPsxProfile() = com.psplauncher.core.domain.model.EmulatorProfile(
        id = "duckstation",
        name = "DuckStation",
        packageName = "com.github.stenzek.duckstation",
        intentType = com.psplauncher.core.domain.model.IntentType.ACTION_VIEW,
        supportedPlatformIds = listOf("psx"),
    ).also { every { profileRepository.getInstalledProfiles() } returns listOf(it) }

    @Test
    fun `the emulator picker leads with a row that clears the override`() = runTest {
        installPsxProfile()
        loadedAndLaidOut()

        viewModel.requestChangeEmulator()
        testDispatcher.scheduler.advanceUntilIdle()

        val options = viewModel.uiState.value.emulatorPickerOptions
        assertEquals(DEFAULT_EMULATOR_SENTINEL, options.first().id)
        assertEquals("Use system default", options.first().name)

        assertTrue(options.any { it.id == "duckstation" })
    }

    @Test
    fun `choosing that row writes null, not a profile id`() = runTest {
        installPsxProfile()
        loadedAndLaidOut()
        viewModel.requestChangeEmulator()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmEmulatorPick(DEFAULT_EMULATOR_SENTINEL)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { gameRepository.setPreferredEmulator(1L, null) }
        coVerify(exactly = 0) { gameRepository.setPreferredEmulator(1L, DEFAULT_EMULATOR_SENTINEL) }
    }

    @Test
    fun `choosing a real emulator still sets the override`() = runTest {
        installPsxProfile()
        loadedAndLaidOut()
        viewModel.requestChangeEmulator()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmEmulatorPick("duckstation")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { gameRepository.setPreferredEmulator(1L, "duckstation") }
    }

    @Test
    fun `with no override the cursor starts on the clear row, which is the truth`() = runTest {
        installPsxProfile()
        loadedAndLaidOut()

        viewModel.requestChangeEmulator()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.emulatorPickerIndex)
    }

    @Test
    fun `the remove prompt opens on Cancel, not on Remove`() = runTest {
        loadedAndLaidOut()
        viewModel.activateAction(DetailAction.REMOVE)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.confirmRemove)

        assertEquals(GameDetailKeys.CONFIRM_CANCEL, viewModel.uiState.value.navFocusKey)
    }

    @Test
    fun `the prompt is navigable, so Remove has to be chosen deliberately`() = runTest {
        loadedAndLaidOut()
        viewModel.activateAction(DetailAction.REMOVE)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals(GameDetailKeys.CONFIRM_REMOVE, viewModel.uiState.value.navFocusKey)

        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_UP)
        assertEquals(GameDetailKeys.CONFIRM_CANCEL, viewModel.uiState.value.navFocusKey)
    }

    @Test
    fun `Confirm on the freshly opened prompt cancels rather than removing`() = runTest {
        loadedAndLaidOut()
        viewModel.activateAction(DetailAction.REMOVE)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleGamepadAction(GamepadAction.SELECT)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.confirmRemove)
        coVerify(exactly = 0) { gameRepository.delete(any()) }
    }

    @Test
    fun `Confirm on Remove does remove`() = runTest {
        loadedAndLaidOut()
        viewModel.activateAction(DetailAction.REMOVE)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_DOWN)
        viewModel.handleGamepadAction(GamepadAction.SELECT)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { gameRepository.delete(1L) }
    }

    @Test
    fun `Back closes the prompt without removing`() = runTest {
        loadedAndLaidOut()
        viewModel.activateAction(DetailAction.REMOVE)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleGamepadAction(GamepadAction.BACK)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.confirmRemove)
        assertFalse(viewModel.uiState.value.closed)
        coVerify(exactly = 0) { gameRepository.delete(any()) }
    }
}
