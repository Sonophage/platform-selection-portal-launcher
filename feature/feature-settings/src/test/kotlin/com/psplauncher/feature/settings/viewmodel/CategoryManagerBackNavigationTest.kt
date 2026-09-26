package com.psplauncher.feature.settings.viewmodel

import com.psplauncher.core.data.repository.CategoryRepositoryImpl
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryManagerBackNavigationTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): CategoryManagerViewModel {
        val repo = mockk<CategoryRepositoryImpl>()
        every { repo.observeAll() } returns flowOf(emptyList())
        every { repo.isProtected(any()) } returns false
        return CategoryManagerViewModel(repo)
    }

    @Test
    fun back_at_top_level_is_not_consumed() {
        val vm = viewModel()

        assertFalse(vm.onBack())
    }

    @Test
    fun back_in_submenu_collapses_one_level_then_reaches_top() {
        val vm = viewModel()
        vm.openDetail("videos")
        assertTrue(vm.onBack())
        assertFalse(vm.onBack())
    }

    @Test
    fun back_from_icon_picker_returns_to_list() {
        val vm = viewModel()
        vm.startCreate()
        vm.confirmCreateName("Streaming")
        assertTrue(vm.onBack())
        assertFalse(vm.onBack())
    }
}
