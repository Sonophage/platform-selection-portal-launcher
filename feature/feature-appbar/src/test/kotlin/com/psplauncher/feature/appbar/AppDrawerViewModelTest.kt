package com.psplauncher.feature.appbar

import android.graphics.drawable.Drawable
import app.cash.turbine.test
import com.psplauncher.core.domain.model.GamepadAction
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppDrawerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: InstalledAppRepository
    private lateinit var viewModel: AppDrawerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        coEvery { repository.getInstalledApps() } returns fakeApps()
        every { repository.hasUsageAccess() } returns true
        viewModel = AppDrawerViewModel(
            repository,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),

            mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the drawer opens on Recently Used, not on the full alphabetical list`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(AppFilter.RECENT, state.activeFilter)
            assertTrue(state.searchQuery.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ALL filter shows all apps`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setFilter(AppFilter.EMULATORS)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(fakeApps().size, state.visibleApps.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `APPS shows neither emulators nor games, and an app that is both is in both`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setFilter(AppFilter.APPS)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val apps = awaitItem().sectionApps
            assertTrue("Apps must contain no emulators", apps.none { it.isEmulator })
            assertTrue("Apps must contain no games", apps.none { it.isGame })
            cancelAndIgnoreRemainingEvents()
        }

        val both = fakeApps().filter { it.isEmulator && it.isGame }.map { it.label }
        assertTrue("the fixture must contain an app that is both, or this proves nothing", both.isNotEmpty())

        viewModel.setFilter(AppFilter.EMULATORS)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            assertTrue(awaitItem().sectionApps.map { it.label }.containsAll(both))
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.setFilter(AppFilter.GAMES)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            assertTrue(
                "an app that is both an emulator and a game must appear under Games too",
                awaitItem().sectionApps.map { it.label }.containsAll(both),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `EMULATORS filter shows only emulator-tagged apps`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setFilter(AppFilter.EMULATORS)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue("the row holds only emulators", state.sectionApps.all { it.isEmulator })

            assertTrue("the list below holds no emulators", state.otherApps.none { it.isEmulator })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `GAMES filter shows only game-tagged apps`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setFilter(AppFilter.GAMES)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue("the row holds only games", state.sectionApps.all { it.isGame })
            assertTrue("the list below holds no games", state.otherApps.none { it.isGame })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `RECENT filter shows timestamped apps newest first`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setFilter(AppFilter.RECENT)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(listOf("Minecraft", "Browser"), state.sectionApps.map { it.label })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `usage access state reflects repository result`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.hasUsageAccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search query filters by app label case-insensitively`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setSearchQuery("PPSSPP")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.visibleApps.all { it.label.contains("PPSSPP", ignoreCase = true) })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing search query restores full list`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFilter(AppFilter.EMULATORS)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setSearchQuery("PPSSPP")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setSearchQuery("")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(fakeApps().size, state.visibleApps.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search with no matches results in empty visible list`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setSearchQuery("zzzznotfound")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.visibleApps.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onAppSelected updates selectedIndex in state`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onAppSelected(3)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(3, state.selectedIndex)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `openUsageAccessSettings delegates to repository`() = runTest {
        viewModel.openUsageAccessSettings()
        verify { repository.openUsageAccessSettings() }
    }

    @Test
    fun `back on the open options menu closes just the menu`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFilter(AppFilter.EMULATORS)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleGamepadAction(GamepadAction.OPEN_CONTEXT_MENU)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("PPSSPP", state.menuApp?.label)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.handleGamepadAction(GamepadAction.BACK)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(null, state.menuApp)
            assertEquals(null, state.confirmUninstall)
            assertEquals(0, state.selectedIndex)
            assertEquals(fakeApps().size, state.visibleApps.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `category cycling works out of an empty recently-used filter`() = runTest {
        coEvery { repository.getInstalledApps() } returns fakeApps().map { it.copy(lastUsedAt = 0L) }
        viewModel = AppDrawerViewModel(
            repository,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),

            mockk(relaxed = true),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setFilter(AppFilter.RECENT)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(AppFilter.RECENT, state.activeFilter)
            assertTrue("the section itself is empty", state.sectionApps.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.handleGamepadAction(GamepadAction.NEXT_CATEGORY)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            assertEquals(AppFilter.APPS, awaitItem().activeFilter)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.handleGamepadAction(GamepadAction.PREV_CATEGORY)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(AppFilter.RECENT, state.activeFilter)
            assertTrue("the section itself is empty", state.sectionApps.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.handleGamepadAction(GamepadAction.PREV_CATEGORY)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            assertEquals(AppFilter.RECENT, awaitItem().activeFilter)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.handleGamepadAction(GamepadAction.NEXT_CATEGORY)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            assertEquals(AppFilter.APPS, awaitItem().activeFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `back after opening uninstall guard rail closes the dialog not the drawer`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFilter(AppFilter.EMULATORS)
        testDispatcher.scheduler.advanceUntilIdle()

        openUninstallPrompt()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("PPSSPP", state.confirmUninstall?.label)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.handleGamepadAction(GamepadAction.BACK)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(null, state.confirmUninstall)
            assertEquals(null, state.menuApp)
            assertEquals(fakeApps().size, state.visibleApps.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun openUninstallPrompt() {
        viewModel.handleGamepadAction(GamepadAction.OPEN_CONTEXT_MENU)
        testDispatcher.scheduler.advanceUntilIdle()
        val actions = viewModel.uiState.value.menuActions
        val target = actions.indexOf(AppMenuAction.UNINSTALL)
        assertTrue("the menu offered no Uninstall row: $actions", target >= 0)
        repeat(target) { viewModel.handleGamepadAction(GamepadAction.NAVIGATE_DOWN) }
        viewModel.handleGamepadAction(GamepadAction.SELECT)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `the uninstall prompt opens with the cursor on Cancel`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFilter(AppFilter.EMULATORS)
        testDispatcher.scheduler.advanceUntilIdle()
        openUninstallPrompt()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("PPSSPP", state.confirmUninstall?.label)
            assertFalse("prompt opened on the destructive button", state.uninstallConfirmFocused)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pressing confirm the instant the prompt opens does not uninstall anything`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        openUninstallPrompt()
        viewModel.handleGamepadAction(GamepadAction.SELECT)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 0) { repository.uninstallApp(any()) }
        viewModel.uiState.test {
            assertEquals(null, awaitItem().confirmUninstall)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moving to Uninstall and confirming does uninstall`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFilter(AppFilter.EMULATORS)
        testDispatcher.scheduler.advanceUntilIdle()
        openUninstallPrompt()
        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_DOWN)
        viewModel.handleGamepadAction(GamepadAction.SELECT)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 1) { repository.uninstallApp("org.ppsspp.ppsspp") }
    }

    @Test
    fun `the cursor moves back off the destructive button`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        openUninstallPrompt()
        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_DOWN)
        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_UP)
        viewModel.handleGamepadAction(GamepadAction.SELECT)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 0) { repository.uninstallApp(any()) }
    }

    @Test
    fun `a reopened prompt starts on Cancel again, whatever the last answer was`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        openUninstallPrompt()
        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_DOWN)
        viewModel.handleGamepadAction(GamepadAction.BACK)
        testDispatcher.scheduler.advanceUntilIdle()
        openUninstallPrompt()
        viewModel.uiState.test {
            assertFalse("cursor was remembered across prompts", awaitItem().uninstallConfirmFocused)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLoading is false after initial load completes`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val fakeDrawable: Drawable = mockk(relaxed = true)

    private fun fakeApps() = listOf(
        InstalledApp(packageName = "org.ppsspp.ppsspp",           label = "PPSSPP",    icon = fakeDrawable, isEmulator = true,  isGame = false),
        InstalledApp(packageName = "com.retroarch",                label = "RetroArch", icon = fakeDrawable, isEmulator = true,  isGame = false),

        InstalledApp(packageName = "org.dolphinemu.dolphinemu",    label = "Dolphin",   icon = fakeDrawable, isEmulator = true,  isGame = true),
        InstalledApp(packageName = "com.mojang.minecraftpe",       label = "Minecraft", icon = fakeDrawable, isEmulator = false, isGame = true, lastUsedAt = 2_000L),
        InstalledApp(packageName = "com.psplauncher.launcher", label = "PFP",       icon = fakeDrawable, isEmulator = false, isGame = false),
        InstalledApp(packageName = "com.example.browser",          label = "Browser",   icon = fakeDrawable, isEmulator = false, isGame = false, lastUsedAt = 1_000L),
    )

    @Test
    fun `under a tab the two halves partition every app, with nothing lost or doubled`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        listOf(AppFilter.APPS, AppFilter.EMULATORS, AppFilter.GAMES).forEach { filter ->
            viewModel.setFilter(filter)
            testDispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value
            val row = state.sectionApps.map { it.packageName }
            val rest = state.otherApps.map { it.packageName }
            assertEquals("$filter: an app is in both halves", emptyList<String>(), row.intersect(rest.toSet()).toList())
            assertEquals(
                "$filter: the two halves are not the whole drawer",
                fakeApps().map { it.packageName }.sorted(),
                (row + rest).sorted(),
            )
        }
    }
}
