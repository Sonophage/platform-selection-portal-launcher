package com.psplauncher.feature.settings.viewmodel

import com.psplauncher.core.data.repository.ControllerLayoutRepository
import com.psplauncher.core.data.repository.ControllerMappingRepository
import com.psplauncher.core.domain.model.ConfirmBackLayout
import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.ControllerLayoutPrefs
import com.psplauncher.core.domain.model.XYLayout
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControllerSettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mappingRepository: ControllerMappingRepository
    private lateinit var layoutRepository: ControllerLayoutRepository
    private lateinit var viewModel: ControllerSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mappingRepository = mockk(relaxed = true)
        layoutRepository = mockk(relaxed = true)
        coEvery { layoutRepository.prefs } returns flowOf(ControllerLayoutPrefs())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.buildActive(): ControllerSettingsViewModel {
        val vm = ControllerSettingsViewModel(mappingRepository, layoutRepository)
        backgroundScope.launch { vm.uiState.collect { } }
        return vm
    }

    @Test
    fun `layout prefs are exposed in uiState`() = runTest(testDispatcher) {
        val prefs = ControllerLayoutPrefs(
            confirmBackLayout = ConfirmBackLayout.REVERSED,
            xyLayout = XYLayout.SWAPPED,
            displayType = ControllerDisplayType.PLAYSTATION,
        )
        coEvery { layoutRepository.prefs } returns flowOf(prefs)
        viewModel = buildActive()
        advanceUntilIdle()

        assertEquals(prefs, viewModel.uiState.value.layoutPrefs)
    }

    @Test
    fun `setConfirmBackLayout persists the chosen layout`() = runTest(testDispatcher) {
        viewModel = buildActive()
        advanceUntilIdle()

        viewModel.setConfirmBackLayout(ConfirmBackLayout.REVERSED)
        advanceUntilIdle()

        coVerify { layoutRepository.setConfirmBackLayout(ConfirmBackLayout.REVERSED) }
    }

    @Test
    fun `setConfirmBackLayout persists a value that is already current`() = runTest(testDispatcher) {
        coEvery { layoutRepository.prefs } returns flowOf(
            ControllerLayoutPrefs(confirmBackLayout = ConfirmBackLayout.REVERSED)
        )
        viewModel = buildActive()
        advanceUntilIdle()

        viewModel.setConfirmBackLayout(ConfirmBackLayout.REVERSED)
        advanceUntilIdle()

        coVerify { layoutRepository.setConfirmBackLayout(ConfirmBackLayout.REVERSED) }
    }

    @Test
    fun `setXYLayout persists the chosen layout`() = runTest(testDispatcher) {
        viewModel = buildActive()
        advanceUntilIdle()

        viewModel.setXYLayout(XYLayout.SWAPPED)
        advanceUntilIdle()

        coVerify { layoutRepository.setXYLayout(XYLayout.SWAPPED) }
    }

    @Test
    fun `every controller type can be chosen directly`() = runTest(testDispatcher) {
        viewModel = buildActive()
        advanceUntilIdle()

        ControllerDisplayType.entries.forEach { type ->
            viewModel.setDisplayType(type)
            advanceUntilIdle()
            coVerify { layoutRepository.setDisplayType(type) }
        }
    }

    @Test
    fun `resetToDefaults resets mappings and layout prefs`() = runTest(testDispatcher) {
        viewModel = buildActive()
        advanceUntilIdle()
        viewModel.resetToDefaults()
        advanceUntilIdle()

        coVerify { mappingRepository.resetToDefaults() }
        coVerify { layoutRepository.resetAllPrefs() }
    }
}
