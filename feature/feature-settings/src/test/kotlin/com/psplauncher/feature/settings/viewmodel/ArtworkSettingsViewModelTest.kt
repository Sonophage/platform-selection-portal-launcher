package com.psplauncher.feature.settings.viewmodel

import com.psplauncher.core.domain.model.VideoSnapPlacement
import com.psplauncher.feature.artwork.MetadataApiKeyProvider
import com.psplauncher.feature.artwork.api.ArtworkRepository
import com.psplauncher.feature.artwork.api.ArtworkScrapePreferences
import com.psplauncher.feature.artwork.api.ArtworkStatus
import com.psplauncher.feature.artwork.api.IgdbApi
import com.psplauncher.feature.artwork.api.ScrapeOptions
import com.psplauncher.feature.artwork.api.MetadataScrapeWorker
import com.psplauncher.feature.artwork.api.SgdbApiKeyProvider
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkSettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var sgdbKeyProvider: SgdbApiKeyProvider
    private lateinit var metadataKeyProvider: MetadataApiKeyProvider
    private lateinit var artworkRepository: ArtworkRepository
    private lateinit var scrapePreferences: ArtworkScrapePreferences
    private lateinit var igdbApi: IgdbApi
    private lateinit var screenScraperApi: com.psplauncher.feature.artwork.api.ScreenScraperApi
    private lateinit var iconDisplayPreferences: com.psplauncher.core.data.repository.IconDisplayPreferences
    private lateinit var cropPreviewPreferences: com.psplauncher.core.data.repository.CropPreviewPreferences
    private lateinit var debugCredentialsLoader: com.psplauncher.feature.settings.debug.DebugCredentialsLoader
    private lateinit var viewModel: ArtworkSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        sgdbKeyProvider     = mockk(relaxed = true)
        metadataKeyProvider = mockk(relaxed = true)
        artworkRepository   = mockk(relaxed = true)
        scrapePreferences   = mockk(relaxed = true)
        igdbApi             = mockk(relaxed = true)
        screenScraperApi    = mockk(relaxed = true)
        debugCredentialsLoader = mockk(relaxed = true)

        every { sgdbKeyProvider.apiKeyFlow }             returns flowOf(null)
        every { metadataKeyProvider.igdbClientIdFlow }   returns flowOf(null)
        every { metadataKeyProvider.ssUsernameFlow }     returns flowOf(null)

        every { metadataKeyProvider.hasIgdbCredentialsFlow } returns flowOf(false)
        every { metadataKeyProvider.hasSsCredentialsFlow }   returns flowOf(false)

        every { screenScraperApi.isEnabledFlow }         returns flowOf(false)
        every { scrapePreferences.preferSteamGridDbHeroesFlow } returns flowOf(false)
        cropPreviewPreferences = mockk(relaxed = true) {
            every { enabledFlow } returns flowOf(true)
        }
        iconDisplayPreferences = mockk(relaxed = true) {
            every { modeFlow } returns flowOf(com.psplauncher.core.domain.model.IconDisplayMode.DEFAULT)
            every { animatedIconsFlow } returns flowOf(true)
            every { snapPlacementFlow } returns flowOf(VideoSnapPlacement.ICON)
            every { gameMetadataFlow } returns flowOf(true)
            every { lingerDelaySecondsFlow } returns flowOf(1.5f)
        }
        coEvery { artworkRepository.computeStatus() }    returns ArtworkStatus(total = 10, complete = 8, missing = 2)
        coEvery { scrapePreferences.getOptions() }       returns ScrapeOptions()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = ArtworkSettingsViewModel(

        context             = mockk(relaxed = true),
        sgdbKeyProvider     = sgdbKeyProvider,
        metadataKeyProvider = metadataKeyProvider,
        artworkLinkRepair   = mockk(relaxed = true),
        artworkRepository   = artworkRepository,
        scrapePreferences   = scrapePreferences,
        igdbApi             = igdbApi,
        screenScraperApi    = screenScraperApi,

        artworkFolderRepository = mockk(relaxed = true) {
            coEvery { getTreeUri() } returns null
        },
        iconDisplayPreferences = iconDisplayPreferences,
        cropPreviewPreferences = cropPreviewPreferences,
        debugCredentialsLoader = debugCredentialsLoader,
    )

    private fun TestScope.activeViewModel(): ArtworkSettingsViewModel {
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        return vm
    }

    @Test
    fun `hasApiKey is false when sgdb key flow emits null`() = runTest(testDispatcher) {
        viewModel = activeViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasApiKey)
    }

    @Test
    fun `hasApiKey is true when sgdb key flow emits a key`() = runTest(testDispatcher) {
        every { sgdbKeyProvider.apiKeyFlow } returns flowOf("abc123")
        viewModel = activeViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasApiKey)
    }

    @Test
    fun `hasIgdbCredentials follows the provider, not the client id on its own`() = runTest(testDispatcher) {
        every { metadataKeyProvider.igdbClientIdFlow } returns flowOf("my-client")
        every { metadataKeyProvider.hasIgdbCredentialsFlow } returns flowOf(false)
        viewModel = activeViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasIgdbCredentials)

        assertEquals("my-client", viewModel.uiState.value.igdbClientId)
    }

    @Test
    fun `hasIgdbCredentials is true once the provider says both halves are present`() = runTest(testDispatcher) {
        every { metadataKeyProvider.igdbClientIdFlow } returns flowOf("my-client")
        every { metadataKeyProvider.hasIgdbCredentialsFlow } returns flowOf(true)
        viewModel = activeViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasIgdbCredentials)
    }

    @Test
    fun `a saved ScreenScraper username alone does not report a usable account`() = runTest(testDispatcher) {
        every { metadataKeyProvider.ssUsernameFlow }       returns flowOf("someone")
        every { metadataKeyProvider.hasSsCredentialsFlow } returns flowOf(false)
        viewModel = activeViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasSsCredentials)
        assertEquals("someone", viewModel.uiState.value.ssUsername)
    }

    @Test
    fun `scrapeMissingOnly enqueues the missing-mode worker`() = runTest(testDispatcher) {
        io.mockk.mockkObject(MetadataScrapeWorker.Companion)
        every { MetadataScrapeWorker.enqueue(any(), any()) } returns java.util.UUID.randomUUID()
        try {
            viewModel = activeViewModel()
            advanceUntilIdle()

            viewModel.scrapeMissingOnly()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isScraping)
            io.mockk.verify { MetadataScrapeWorker.enqueue(any(), MetadataScrapeWorker.MODE_MISSING) }
        } finally {
            io.mockk.unmockkObject(MetadataScrapeWorker.Companion)
        }
    }

    @Test
    fun `confirmRescrapeAll enqueues the all-mode worker`() = runTest(testDispatcher) {
        io.mockk.mockkObject(MetadataScrapeWorker.Companion)
        every { MetadataScrapeWorker.enqueue(any(), any()) } returns java.util.UUID.randomUUID()
        try {
            viewModel = activeViewModel()
            advanceUntilIdle()

            viewModel.requestRescrapeAll()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.confirmRescrapeAll)

            viewModel.confirmRescrapeAll()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.confirmRescrapeAll)
            assertTrue(viewModel.uiState.value.isScraping)
            io.mockk.verify { MetadataScrapeWorker.enqueue(any(), MetadataScrapeWorker.MODE_ALL) }
        } finally {
            io.mockk.unmockkObject(MetadataScrapeWorker.Companion)
        }
    }

    @Test
    fun `cancelScrape delegates to the worker cancel`() = runTest(testDispatcher) {
        io.mockk.mockkObject(MetadataScrapeWorker.Companion)
        every { MetadataScrapeWorker.cancel(any()) } returns mockk(relaxed = true)
        try {
            viewModel = activeViewModel()
            advanceUntilIdle()

            viewModel.cancelScrape()
            io.mockk.verify { MetadataScrapeWorker.cancel(any()) }
        } finally {
            io.mockk.unmockkObject(MetadataScrapeWorker.Companion)
        }
    }

    @Test
    fun `cancelRescrapeAll dismisses confirmation dialog`() = runTest(testDispatcher) {
        viewModel = activeViewModel()
        advanceUntilIdle()

        viewModel.requestRescrapeAll()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.confirmRescrapeAll)

        viewModel.cancelRescrapeAll()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.confirmRescrapeAll)
    }

    @Test
    fun `setPreferSteamGridDbHeroes persists to scrapePreferences`() = runTest(testDispatcher) {
        viewModel = activeViewModel()
        advanceUntilIdle()

        viewModel.setPreferSteamGridDbHeroes(true)
        advanceUntilIdle()

        coVerify { scrapePreferences.setPreferSteamGridDbHeroes(true) }
    }

    @Test
    fun `setDownloadLogos updates state and persists as clear logos`() = runTest(testDispatcher) {
        viewModel = activeViewModel()
        advanceUntilIdle()

        viewModel.setDownloadLogos(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.downloadLogos)
        coVerify { scrapePreferences.setDownloadClearLogos(false) }
    }

    @Test
    fun `icon1 linger delay defaults to 1 point 5 seconds`() = runTest(testDispatcher) {
        viewModel = activeViewModel()
        advanceUntilIdle()

        assertEquals(1.5f, viewModel.uiState.value.icon1LingerDelaySeconds)
    }

    @Test
    fun `setIcon1LingerDelaySeconds updates state and persists`() = runTest(testDispatcher) {
        viewModel = activeViewModel()
        advanceUntilIdle()

        viewModel.setIcon1LingerDelaySeconds(3.5f)
        advanceUntilIdle()

        assertEquals(3.5f, viewModel.uiState.value.icon1LingerDelaySeconds)
        coVerify { iconDisplayPreferences.setLingerDelaySeconds(3.5f) }
    }

    @Test
    fun `testIgdbCredentials shows Valid on success`() = runTest(testDispatcher) {
        coEvery { igdbApi.testCredentials("id", "secret") } returns true
        viewModel = activeViewModel()
        advanceUntilIdle()

        viewModel.testIgdbCredentials("id", "secret")
        advanceUntilIdle()

        assertEquals("Valid", viewModel.uiState.value.igdbCredentialStatus)
    }

    @Test
    fun `testIgdbCredentials shows failure message on invalid credentials`() = runTest(testDispatcher) {
        coEvery { igdbApi.testCredentials("bad", "creds") } returns false
        viewModel = activeViewModel()
        advanceUntilIdle()

        viewModel.testIgdbCredentials("bad", "creds")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.igdbCredentialStatus?.contains("Invalid") == true)
    }

    @Test
    fun `dismissSummary clears summary`() = runTest(testDispatcher) {
        viewModel = activeViewModel()
        advanceUntilIdle()

        viewModel.dismissSummary()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.summary)
    }

    @Test
    fun `dismissCredentialStatus clears igdb status`() = runTest(testDispatcher) {
        coEvery { igdbApi.testCredentials(any(), any()) } returns true
        viewModel = activeViewModel()
        advanceUntilIdle()

        viewModel.testIgdbCredentials("id", "secret")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.igdbCredentialStatus.isNullOrBlank())

        viewModel.dismissCredentialStatus()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.igdbCredentialStatus)
    }

    @Test
    fun `the credentials file row is offered in debug builds`() = runTest(testDispatcher) {
        viewModel = activeViewModel()
        advanceUntilIdle()

        assertEquals(
            com.psplauncher.feature.settings.BuildConfig.DEBUG,
            viewModel.uiState.value.debugCredentialsAvailable,
        )
    }
}
