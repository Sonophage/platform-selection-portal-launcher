package com.psplauncher.feature.settings.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.psplauncher.core.data.repository.MediaRootKind
import com.psplauncher.core.data.repository.MediaRootRepository
import com.psplauncher.core.data.repository.CoreInventory
import com.psplauncher.core.data.repository.RetroArchLink
import com.psplauncher.core.data.repository.RomRootRepository
import com.psplauncher.core.data.repository.Vita3KLibrary
import com.psplauncher.feature.artwork.MetadataApiKeyProvider
import com.psplauncher.feature.artwork.api.ArtworkImportManager
import com.psplauncher.feature.artwork.api.IgdbApi
import com.psplauncher.feature.artwork.api.ScreenScraperApi
import com.psplauncher.feature.artwork.api.SgdbApiKeyProvider
import com.psplauncher.feature.artwork.importer.DetectedImportSource
import com.psplauncher.feature.artwork.importer.ImportPlan
import com.psplauncher.feature.artwork.portable.PortableArtworkLibrary
import com.psplauncher.feature.launcher.EmulatorAutoConfigService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
class InitialSetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val context = mockk<Context>(relaxed = true)
    private val packageManager = mockk<PackageManager>(relaxed = true)
    private val romRoots = mockk<RomRootRepository>(relaxed = true)
    private val mediaRoots = mockk<MediaRootRepository>(relaxed = true)
    private val artworkImport = mockk<ArtworkImportManager>(relaxed = true)
    private val retroArchLink = mockk<RetroArchLink>(relaxed = true)
    private val vita3KLibrary = mockk<Vita3KLibrary>(relaxed = true)
    private val autoConfig = mockk<EmulatorAutoConfigService>(relaxed = true)
    private val sgdbKeys = mockk<SgdbApiKeyProvider>(relaxed = true)
    private val metadataKeys = mockk<MetadataApiKeyProvider>(relaxed = true)
    private val igdbApi = mockk<IgdbApi>()
    private val screenScraperApi = mockk<ScreenScraperApi>()
    private val scanRunner = mockk<com.psplauncher.feature.settings.media.WizardMediaScanRunner>(relaxed = true)
    private val romRootScanRunner = mockk<RomRootScanRunner>(relaxed = true)
    private val installedApps =
        mockk<com.psplauncher.feature.appbar.InstalledAppRepository>(relaxed = true)
    private val launcherShortcuts =
        mockk<com.psplauncher.feature.appbar.LauncherShortcutRepository>(relaxed = true)
    private lateinit var vm: InitialSetupViewModel

    private fun buildVm() = InitialSetupViewModel(
        context, romRoots, mediaRoots, artworkImport, retroArchLink, vita3KLibrary, autoConfig,
        sgdbKeys, metadataKeys, igdbApi, screenScraperApi,
        scanRunner, romRootScanRunner,
        mockk(relaxed = true), // romScanner (B3 create-standard-folders)
        mockk(relaxed = true), // folderHintResolver
        mockk(relaxed = true), // memoryCardRepository
        installedApps,
        launcherShortcuts,
    )

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { context.packageManager } returns packageManager
        // Default: RetroArch NOT installed (the tests below override to enable its page).
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } throws
            PackageManager.NameNotFoundException()
        every { romRoots.roots } returns flowOf(emptyList())
        every { mediaRoots.roots(any()) } returns flowOf(emptyList())
        every { artworkImport.folderTreeUri } returns flowOf(null)
        every { vita3KLibrary.ux0TreeUriFlow } returns flowOf(null)
        every { sgdbKeys.apiKeyFlow } returns flowOf(null)
        every { metadataKeys.igdbClientIdFlow } returns flowOf(null)
        every { metadataKeys.ssUsernameFlow } returns flowOf(null)
        coEvery { screenScraperApi.isEnabled() } returns true
        coEvery { retroArchLink.inventory() } returns CoreInventory.Unlinked
        vm = buildVm()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    // uiState is WhileSubscribed — tests that assert on it need an active collector.
    private fun TestScope.collectState() = launch { vm.uiState.collect {} }

    // ── Book roots ──────────────────────────────────────────────────────────────

    @Test fun `book roots reach the Books page and nothing else`() = runTest(dispatcher) {
        // The one thing that can silently go wrong here: BOOK is joined by a SECOND combine on
        // top of the inner five (combine's typed overload tops out at five), so a mis-wire lands
        // the books in another section's list or drops them entirely — and either reads as an
        // empty page rather than as an error.
        every { mediaRoots.roots(MediaRootKind.BOOK) } returns
            flowOf(listOf("content://tree/primary%3ABooks"))
        vm = buildVm()

        val job = collectState()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.bookRoots.size)
        assertTrue(state.musicRoots.isEmpty())
        assertTrue(state.videoRoots.isEmpty())
        assertTrue(state.photoRoots.isEmpty())
        assertTrue(state.romRoots.isEmpty())
        job.cancel()
    }

    // ── Permission grants ───────────────────────────────────────────────────────

    @Test fun `grants are re-read on demand, not cached from construction`() = runTest(dispatcher) {
        // None of the three grants has a change broadcast, so the page re-reads them on every
        // resume. If refreshGrants ever went back to a one-shot read, a user who granted usage
        // access and came back would still be looking at "Grant…".
        every { installedApps.hasUsageAccess() } returns false
        every { launcherShortcuts.isDefaultLauncher() } returns false
        vm = buildVm()
        val job = collectState()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasUsageAccess)
        assertFalse(vm.uiState.value.isHomeLauncher)

        // The user leaves, grants both, comes back.
        every { installedApps.hasUsageAccess() } returns true
        every { launcherShortcuts.isDefaultLauncher() } returns true
        vm.refreshGrants()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hasUsageAccess)
        assertTrue(vm.uiState.value.isHomeLauncher)
        job.cancel()
    }

    // ── Step navigation ─────────────────────────────────────────────────────────

    @Test fun `steps advance through every page, skipping conditional emulator pages when not installed`() =
        runTest(dispatcher) {
            val job = collectState()
            advanceUntilIdle()
            val expected = listOf(
                SetupStep.WELCOME, SetupStep.PERMISSIONS, SetupStep.ROM_ROOTS, SetupStep.MUSIC,
                SetupStep.VIDEO, SetupStep.PHOTO, SetupStep.BOOKS, SetupStep.ARTWORK,
                SetupStep.SERVICES, SetupStep.PERSONALIZE, SetupStep.FINISH,
            )
            expected.forEachIndexed { index, step ->
                assertEquals("landing on step $index", step, vm.uiState.value.step)
                if (index < expected.lastIndex) {
                    vm.nextStep()
                    advanceUntilIdle()
                }
            }
            assertEquals(SetupStep.FINISH, vm.uiState.value.step)
            job.cancel()
        }

    @Test fun `steps retreat in order and back from welcome exits`() = runTest(dispatcher) {
        val job = collectState()
        advanceUntilIdle()
        assertEquals(SetupStep.WELCOME, vm.uiState.value.step)

        vm.nextStep()
        advanceUntilIdle()
        assertEquals(SetupStep.PERMISSIONS, vm.uiState.value.step)

        assertTrue(vm.previousStep())
        advanceUntilIdle()
        assertEquals(SetupStep.WELCOME, vm.uiState.value.step)

        assertFalse("back on the first page means exit", vm.previousStep())
        job.cancel()
    }

    @Test fun `RetroArch and Vita3K pages are included when both apps are installed`() =
        runTest(dispatcher) {
            // Both RetroArch and Vita3K installed for this run (any getPackageInfo call succeeds).
            every { packageManager.getPackageInfo(any<String>(), any<Int>()) } returns mockk()
            vm = buildVm()

            val job = collectState()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.retroArchInstalled)
            assertTrue(vm.uiState.value.vita3KInstalled)

            listOf(
                SetupStep.PERMISSIONS, SetupStep.ROM_ROOTS, SetupStep.MUSIC, SetupStep.VIDEO,
                SetupStep.PHOTO, SetupStep.BOOKS, SetupStep.ARTWORK, SetupStep.SERVICES,
                SetupStep.VITA, SetupStep.RETROARCH, SetupStep.PERSONALIZE, SetupStep.FINISH,
            ).forEach { step ->
                vm.nextStep()
                advanceUntilIdle()
                assertEquals(step, vm.uiState.value.step)
            }
            job.cancel()
        }

    @Test fun `Vita page is gated on Vita3K installed but included even when RetroArch is not`() =
        runTest(dispatcher) {
            // Only Vita3K installed: getPackageInfo succeeds for the vita package, throws otherwise.
            every { packageManager.getPackageInfo(any<String>(), any<Int>()) } answers {
                if (firstArg<String>().startsWith("org.vita3k")) mockk<android.content.pm.PackageInfo>()
                else throw PackageManager.NameNotFoundException()
            }
            vm = buildVm()

            val job = collectState()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.vita3KInstalled)
            assertFalse(vm.uiState.value.retroArchInstalled)

            listOf(
                SetupStep.PERMISSIONS, SetupStep.ROM_ROOTS, SetupStep.MUSIC, SetupStep.VIDEO,
                SetupStep.PHOTO, SetupStep.BOOKS, SetupStep.ARTWORK, SetupStep.SERVICES,
                SetupStep.VITA, SetupStep.PERSONALIZE, SetupStep.FINISH,
            ).forEach { step ->
                vm.nextStep()
                advanceUntilIdle()
                assertEquals(step, vm.uiState.value.step)
            }
            // The list above ends at FINISH without RETROARCH — landing here proves the RetroArch
            // page stayed hidden (it would have been reached before FINISH if it were present).
            job.cancel()
        }

    @Test fun `parking survives exactly one reset, so an excursion keeps your place`() =
        runTest(dispatcher) {
            // Make It Yours opens a real settings screen, which disposes the wizard overlay and
            // fires its onDispose reset. Without the park, opening the theme picker on step 10
            // would put you back on page one.
            val job = collectState()
            repeat(3) { vm.nextStep() }
            advanceUntilIdle()
            val where = vm.uiState.value.step

            vm.parkForExcursion()
            vm.resetWizard()           // the overlay leaving for the excursion
            advanceUntilIdle()
            assertEquals("the parked reset must be a no-op", where, vm.uiState.value.step)

            // And only one: the wizard genuinely closed still starts over next time.
            vm.resetWizard()
            advanceUntilIdle()
            assertEquals(SetupStep.WELCOME, vm.uiState.value.step)
            job.cancel()
        }

    @Test fun `resetWizard returns to the welcome page for the next run`() = runTest(dispatcher) {
        val job = collectState()
        vm.nextStep()
        vm.nextStep()
        advanceUntilIdle()
        assertEquals(SetupStep.ROM_ROOTS, vm.uiState.value.step)

        vm.resetWizard()
        advanceUntilIdle()
        assertEquals(SetupStep.WELCOME, vm.uiState.value.step)
        job.cancel()
    }

    // ── Multi-root folders ──────────────────────────────────────────────────────

    @Test fun `rom root pick persists the grant and kicks off the scan`() = runTest(dispatcher) {
        val uri = mockk<Uri> { every { this@mockk.toString() } returns "content://tree/primary%3ARoms" }

        vm.addRomRoot(uri)
        advanceUntilIdle()

        coVerify { romRoots.persist(uri, writable = true) }
        coVerify { romRoots.add("content://tree/primary%3ARoms") }
        io.mockk.verify { romRootScanRunner.kickoff() }
    }

    @Test fun `addMediaRoot persists, adds, and starts the per-kind scan`() = runTest(dispatcher) {
        val uri = mockk<Uri> { every { this@mockk.toString() } returns "content://tree/primary%3AMusic" }

        vm.addMediaRoot(MediaRootKind.MUSIC, uri)
        advanceUntilIdle()

        coVerify { mediaRoots.persist(uri) }
        coVerify { mediaRoots.add(MediaRootKind.MUSIC, "content://tree/primary%3AMusic") }
        io.mockk.verify { scanRunner.kickoff(MediaRootKind.MUSIC) }
    }

    @Test fun `removeMediaRoot removes and rescans to reconcile library rows`() =
        runTest(dispatcher) {
            vm.removeMediaRoot(MediaRootKind.PHOTO, "content://tree/primary%3APhotos")
            advanceUntilIdle()

            coVerify { mediaRoots.remove(MediaRootKind.PHOTO, "content://tree/primary%3APhotos") }
            io.mockk.verify { scanRunner.kickoff(MediaRootKind.PHOTO) }
        }

    @Test fun `relinkMediaRoot replaces the root uri and rescans`() = runTest(dispatcher) {
        val old = "content://tree/primary%3AMusic"
        val newUri = mockk<Uri> { every { this@mockk.toString() } returns "content://tree/1A2B-3C4D%3AMusic" }

        vm.relinkMediaRoot(MediaRootKind.MUSIC, old, newUri)
        advanceUntilIdle()

        coVerify { mediaRoots.persist(newUri) }
        coVerify { mediaRoots.replace(MediaRootKind.MUSIC, old, "content://tree/1A2B-3C4D%3AMusic") }
        io.mockk.verify { scanRunner.kickoff(MediaRootKind.MUSIC) }
    }

    // ── Artwork ─────────────────────────────────────────────────────────────────

    @Test fun `artwork folder link success surfaces sources for the import offer`() =
        runTest(dispatcher) {
            val uri = mockk<Uri>()
            val source = mockk<DetectedImportSource>()
            every { source.label } returns "16-bit Collection"
            every { source.systems } returns emptyList()
            coEvery { artworkImport.folderTreeUri } returns flowOf("content://tree/primary%3AArtwork")
            coEvery { artworkImport.linkFolder(uri) } returns ArtworkImportManager.LinkResult(
                manifest = mockk(), existingLibrary = false,
            )
            coEvery { artworkImport.detectSources() } returns listOf(source)
            // Rebuild a fresh VM so its rootLists combine subscribes to the re-stubbed folder flow
            // (the setUp VM is already collecting the old null folderTreeUri).
            vm = buildVm()
            val job = collectState()
            advanceUntilIdle()

            vm.onArtworkFolderPicked(uri)
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.artworkFolderName)
            assertEquals(listOf(ArtworkSourceUi("16-bit Collection", 0)), vm.uiState.value.artworkSources)
            assertNotNull(vm.uiState.value.message)
            job.cancel()
        }

    @Test fun `artwork folder link failure surfaces a message`() = runTest(dispatcher) {
        val uri = mockk<Uri>()
        coEvery { artworkImport.linkFolder(uri) } returns null
        val job = collectState()

        vm.onArtworkFolderPicked(uri)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.message)
        job.cancel()
    }

    @Test fun `importArtworkNow copies the first detected source`() = runTest(dispatcher) {
        val uri = mockk<Uri>()
        val source = mockk<DetectedImportSource>()
        val plan = mockk<ImportPlan>()
        every { source.label } returns "Xbox Library"
        every { source.systems } returns emptyList()
        every { plan.itemCount } returns 3
        every { plan.sourceLabel } returns "Xbox Library"
        coEvery { artworkImport.linkFolder(uri) } returns ArtworkImportManager.LinkResult(
            manifest = mockk(), existingLibrary = false,
        )
        coEvery { artworkImport.detectSources() } returns listOf(source)
        coEvery { artworkImport.buildPlan(source) } returns plan
        val job = collectState()

        vm.onArtworkFolderPicked(uri)
        advanceUntilIdle()
        vm.importArtworkNow()
        advanceUntilIdle()

        coVerify { artworkImport.startImport(plan, PortableArtworkLibrary.Transfer.COPY) }
        assertTrue(vm.uiState.value.message.orEmpty().contains("Importing"))
        job.cancel()
    }

    @Test fun `importArtworkNow with no source explains instead of importing`() =
        runTest(dispatcher) {
            val job = collectState()
            advanceUntilIdle()

            vm.importArtworkNow()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.message.orEmpty().contains("Nothing to import"))
            coVerify(exactly = 0) { artworkImport.startImport(any(), any()) }
            job.cancel()
        }

    // ── RetroArch ───────────────────────────────────────────────────────────────

    @Test fun `linkRetroArch saves the tree and reports installed cores`() = runTest(dispatcher) {
        coEvery { retroArchLink.inventory() } returns CoreInventory.Verified(
            setOf("snes9x_libretro_android.so", "mgba_libretro_android.so")
        )
        val uri = mockk<Uri>()
        val job = collectState()

        vm.linkRetroArch(uri)
        advanceUntilIdle()

        coVerify { retroArchLink.save(uri) }
        coVerify { autoConfig.runOnStartup() }
        assertTrue(vm.uiState.value.retroArchLinked)
        assertEquals(2, vm.uiState.value.retroArchCoreCount)
        job.cancel()
    }

    @Test fun `unlinkRetroArch clears the link and the detected count`() = runTest(dispatcher) {
        val job = collectState()
        advanceUntilIdle()

        vm.unlinkRetroArch()
        advanceUntilIdle()

        coVerify { retroArchLink.clear() }
        assertFalse(vm.uiState.value.retroArchLinked)
        assertNull(vm.uiState.value.retroArchCoreCount)
        job.cancel()
    }

    // ── Vita3K data folder ─────────────────────────────────────────────────────

    @Test fun `linkVitaFolder grants the ux0 folder and reports it`() = runTest(dispatcher) {
        val uri = mockk<Uri> {
            every { this@mockk.toString() } returns "content://tree/primary%3ARoms%2Fvita%2Fux0"
        }
        val job = collectState()

        vm.linkVitaFolder(uri)
        advanceUntilIdle()

        coVerify { vita3KLibrary.setUx0Folder(uri) }
        assertNotNull(vm.uiState.value.message)
        job.cancel()
    }

    @Test fun `forgetVitaFolder clears the ux0 grant without touching files`() = runTest(dispatcher) {
        val job = collectState()
        advanceUntilIdle()

        vm.forgetVitaFolder()
        advanceUntilIdle()

        coVerify { vita3KLibrary.clear() }
        job.cancel()
    }

    // ── Services (unchanged behavior) ───────────────────────────────────────────

    @Test fun `testIgdbCredentials reports valid and invalid`() = runTest(dispatcher) {
        coEvery { igdbApi.testCredentials("id", "secret") } returns true
        val job = collectState()

        vm.testIgdbCredentials("id", "secret")
        advanceUntilIdle()
        assertEquals("Valid", vm.uiState.value.igdbStatus)

        coEvery { igdbApi.testCredentials("id", "wrong") } returns false
        vm.testIgdbCredentials("id", "wrong")
        advanceUntilIdle()
        assertEquals("Invalid — check Client ID and Secret", vm.uiState.value.igdbStatus)
        job.cancel()
    }

    @Test fun `navigating steps clears stale test statuses`() = runTest(dispatcher) {
        coEvery { igdbApi.testCredentials("id", "secret") } returns true
        val job = collectState()

        vm.nextStep()
        vm.nextStep()
        vm.nextStep()
        vm.testIgdbCredentials("id", "secret")
        advanceUntilIdle()
        assertEquals("Valid", vm.uiState.value.igdbStatus)

        vm.nextStep()
        advanceUntilIdle()
        assertNull("leaving the page must drop the orphaned status", vm.uiState.value.igdbStatus)
        job.cancel()
    }

    @Test fun `blank credentials are ignored`() = runTest(dispatcher) {
        vm.connectSgdb("  ")
        vm.connectIgdb("client-id", "")
        advanceUntilIdle()

        coVerify(exactly = 0) { sgdbKeys.saveKey(any()) }
        coVerify(exactly = 0) { metadataKeys.saveIgdbCredentials(any(), any()) }
    }
}
