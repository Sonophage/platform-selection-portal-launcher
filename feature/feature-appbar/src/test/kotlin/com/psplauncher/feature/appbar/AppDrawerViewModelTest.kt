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
            mockk(relaxed = true),   // menuSound
            mockk(relaxed = true),   // gameRepository
            mockk(relaxed = true),   // memoryCardRepository
            // The launch disc's gate. Relaxed, so awaitHandOff returns at once: these tests are
            // about which app is launched, not about the ceremony in front of it.
            mockk(relaxed = true),   // mediaLaunchGate
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Filter logic ──────────────────────────────────────────────────────

    @Test
    fun `the drawer opens on Recently Used, not on the full alphabetical list`() = runTest {
        // All Apps is 45 icons in alphabetical order on the owner's device — a list you read
        // rather than recognise. Opening on what you were last using is the point of the section.
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
        viewModel.setFilter(AppFilter.ALL)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(fakeApps().size, state.visibleApps.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `APPS shows neither emulators nor games, and an app that is both is in both`() = runTest {
        // The owner's rule. An emulator he has also marked as a game belongs under Emulators AND
        // under Games — hiding it from one would make that section a lie — but it is not an
        // "app", which is the distinction Apps exists to draw and All Apps cannot.
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setFilter(AppFilter.APPS)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val apps = awaitItem().visibleApps
            assertTrue("Apps must contain no emulators", apps.none { it.isEmulator })
            assertTrue("Apps must contain no games", apps.none { it.isGame })
            cancelAndIgnoreRemainingEvents()
        }

        // No `if` around this. Guarding it on the fixture happening to contain such an app is how
        // an assertion ends up passing because it never ran.
        val both = fakeApps().filter { it.isEmulator && it.isGame }.map { it.label }
        assertTrue("the fixture must contain an app that is both, or this proves nothing", both.isNotEmpty())

        viewModel.setFilter(AppFilter.EMULATORS)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            assertTrue(awaitItem().visibleApps.map { it.label }.containsAll(both))
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.setFilter(AppFilter.GAMES)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            assertTrue(
                "an app that is both an emulator and a game must appear under Games too",
                awaitItem().visibleApps.map { it.label }.containsAll(both),
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
            assertTrue(state.visibleApps.all { it.isEmulator })
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
            assertTrue(state.visibleApps.all { it.isGame })
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
            assertEquals(listOf("Minecraft", "Browser"), state.visibleApps.map { it.label })
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

    // ── Search logic ──────────────────────────────────────────────────────

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
        // These are about the menu / search / uninstall paths, not about which section opens, so
        // the full list is selected explicitly. They used to rely on the drawer defaulting to
        // All Apps, which made them fail the moment the default became Recently Used for reasons
        // that had nothing to do with what they were testing.
        viewModel.setFilter(AppFilter.ALL)
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

    // ── Selection ─────────────────────────────────────────────────────────

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

    // ── Options menu / BACK semantics ─────────────────────────────────────

    @Test
    fun `back on the open options menu closes just the menu`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        // These are about the menu / search / uninstall paths, not about which section opens, so
        // the full list is selected explicitly. They used to rely on the drawer defaulting to
        // All Apps, which made them fail the moment the default became Recently Used for reasons
        // that had nothing to do with what they were testing.
        viewModel.setFilter(AppFilter.ALL)
        testDispatcher.scheduler.advanceUntilIdle()

        // Controller Y opens the focused app's options module (grid focus is on index 0).
        viewModel.handleGamepadAction(GamepadAction.OPEN_CONTEXT_MENU)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("PPSSPP", state.menuApp?.label)
            cancelAndIgnoreRemainingEvents()
        }

        // BACK pops the menu; the drawer's grid state (which lives beside menuApp in this VM and
        // is what the shell needs to keep the drawer open) is untouched.
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
        // A user who hasn't granted usage access: no app carries a lastUsedAt, so the RECENT
        // filter is empty. L1/R1 must still cycle out of it (it previously stranded the cursor -
        // the empty-grid guard swallowed PREV/NEXT_CATEGORY along with grid navigation).
        coEvery { repository.getInstalledApps() } returns fakeApps().map { it.copy(lastUsedAt = 0L) }
        viewModel = AppDrawerViewModel(
            repository,
            mockk(relaxed = true),   // menuSound
            mockk(relaxed = true),   // gameRepository
            mockk(relaxed = true),   // memoryCardRepository
            // The launch disc's gate. Relaxed, so awaitHandOff returns at once: these tests are
            // about which app is launched, not about the ceremony in front of it.
            mockk(relaxed = true),   // mediaLaunchGate
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setFilter(AppFilter.RECENT)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(AppFilter.RECENT, state.activeFilter)
            assertTrue(state.visibleApps.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }

        // R1 leaves the empty RECENT section. RECENT is FIRST in the enum now, so the escape is
        // NEXT rather than PREV — the destination was always an artefact of the order, the thing
        // being proven is that an empty section is never a dead end.
        viewModel.handleGamepadAction(GamepadAction.NEXT_CATEGORY)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            assertEquals(AppFilter.APPS, awaitItem().activeFilter)
            cancelAndIgnoreRemainingEvents()
        }

        // L1 walks back into the empty section, and R1 gets out again.
        viewModel.handleGamepadAction(GamepadAction.PREV_CATEGORY)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(AppFilter.RECENT, state.activeFilter)
            assertTrue(state.visibleApps.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }

        // RECENT is the first filter — PREV clamps (no wrap), staying on the still-empty section
        // as a harmless no-op rather than a crash. Clamping is only safe BECAUSE the other
        // direction always works; that is the pair, and both halves are asserted here.
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
        // These are about the menu / search / uninstall paths, not about which section opens, so
        // the full list is selected explicitly. They used to rely on the drawer defaulting to
        // All Apps, which made them fail the moment the default became Recently Used for reasons
        // that had nothing to do with what they were testing.
        viewModel.setFilter(AppFilter.ALL)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleGamepadAction(GamepadAction.OPEN_CONTEXT_MENU)
        testDispatcher.scheduler.advanceUntilIdle()
        // Walk to the Uninstall row and select it (fake apps have no isSystemApp flag, so the
        // Uninstall action is present for every row).
        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_DOWN)
        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_DOWN)
        viewModel.handleGamepadAction(GamepadAction.SELECT)
        testDispatcher.scheduler.advanceUntilIdle()
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

    // ── Uninstall prompt cursor ──────────────────────────────────────────
    //
    // The prompt used to have no cursor at all: it was drawn with Cancel and Uninstall side by
    // side, nothing focused, and the ViewModel read "SELECT confirms, anything else cancels".
    // That was coherent only because nothing on screen said otherwise. Now that it is the shared
    // stacked overlay with a visible cursor, SELECT has to mean "the button you are on", and the
    // button you start on has to be the harmless one.

    /** Opens the uninstall prompt for PPSSPP, leaving the cursor wherever it opens. */
    private fun openUninstallPrompt() {
        viewModel.handleGamepadAction(GamepadAction.OPEN_CONTEXT_MENU)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_DOWN)
        viewModel.handleGamepadAction(GamepadAction.NAVIGATE_DOWN)
        viewModel.handleGamepadAction(GamepadAction.SELECT)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `the uninstall prompt opens with the cursor on Cancel`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        // These are about the menu / search / uninstall paths, not about which section opens, so
        // the full list is selected explicitly. They used to rely on the drawer defaulting to
        // All Apps, which made them fail the moment the default became Recently Used for reasons
        // that had nothing to do with what they were testing.
        viewModel.setFilter(AppFilter.ALL)
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
        // The reason the cursor starts on Cancel. Uninstall is reached from a menu whose last
        // press was also SELECT, so a second one arrives easily and by reflex.
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
        // The other direction. If this passed while the test above also passed by the prompt simply
        // never confirming, the guard rail would be a wall.
        testDispatcher.scheduler.advanceUntilIdle()
        // These are about the menu / search / uninstall paths, not about which section opens, so
        // the full list is selected explicitly. They used to rely on the drawer defaulting to
        // All Apps, which made them fail the moment the default became Recently Used for reasons
        // that had nothing to do with what they were testing.
        viewModel.setFilter(AppFilter.ALL)
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
        // State that survived the close would put the cursor on Uninstall for the NEXT app, which
        // is the worst possible place for it to be remembered.
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

    // ── isLoading ────────────────────────────────────────────────────────

    @Test
    fun `isLoading is false after initial load completes`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private val fakeDrawable: Drawable = mockk(relaxed = true)

    private fun fakeApps() = listOf(
        InstalledApp(packageName = "org.ppsspp.ppsspp",           label = "PPSSPP",    icon = fakeDrawable, isEmulator = true,  isGame = false),
        InstalledApp(packageName = "com.retroarch",                label = "RetroArch", icon = fakeDrawable, isEmulator = true,  isGame = false),
        // An emulator the user has also marked as a game. Not a curiosity: on the owner's device
        // 8 of 8 emulators are tagged this way, and it is the case the Apps/Games/Emulators rule
        // turns on. Without it in the fixture the "in both" assertion below has nothing to find
        // and passes by doing nothing.
        InstalledApp(packageName = "org.dolphinemu.dolphinemu",    label = "Dolphin",   icon = fakeDrawable, isEmulator = true,  isGame = true),
        InstalledApp(packageName = "com.mojang.minecraftpe",       label = "Minecraft", icon = fakeDrawable, isEmulator = false, isGame = true, lastUsedAt = 2_000L),
        InstalledApp(packageName = "com.psplauncher.launcher", label = "PFP",       icon = fakeDrawable, isEmulator = false, isGame = false),
        InstalledApp(packageName = "com.example.browser",          label = "Browser",   icon = fakeDrawable, isEmulator = false, isGame = false, lastUsedAt = 1_000L),
    )
}
