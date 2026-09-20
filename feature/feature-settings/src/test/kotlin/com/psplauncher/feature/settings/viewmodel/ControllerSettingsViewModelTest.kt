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

    // uiState is a WhileSubscribed StateFlow and the cycle* methods read uiState.value, so it must be
    // collected before it reflects the repository prefs — mirror the UI by keeping a live collector.
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
        // Picking the ticked option is a normal thing to do in a picker, and it must reach the
        // repository like any other pick rather than being quietly dropped as a no-op: these
        // setters no longer read the current value at all, and this is what pins that.
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
        // The picker indexes ControllerDisplayType.entries, so the list it offers and the values
        // it can set are the same list. Cycling used to define that order; the enum defines it
        // now, and every entry has to be reachable or a picker row would offer a dead option.
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
