package com.psplauncher.feature.settings.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.data.repository.GameBootPreferences
import com.psplauncher.core.data.repository.UiMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DisplaySettingsViewModelGameBootTest {
    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var vm: DisplaySettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        runBlocking { context.pfpDataStore.edit { it.clear() } }
        vm = DisplaySettingsViewModel(
            context,
            UiMediaStore(context),
            GameBootPreferences(context),

            com.psplauncher.core.data.launch.LaunchDiscPreferences(context),
            io.mockk.mockk(relaxed = true),

            io.mockk.mockk(relaxed = true) {
                io.mockk.every { prefs } returns kotlinx.coroutines.flow.flowOf(
                    com.psplauncher.core.domain.model.ControllerLayoutPrefs()
                )
            },

            io = dispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a fresh install shows the toggle on and turning it off persists`() = runTest(dispatcher) {
        eventually("fresh install defaults on") { vm.uiState.first().gameBootEnabled }

        vm.setGameBootEnabled(false)

        eventually("the row follows the write") { !vm.uiState.first().gameBootEnabled }
        assertEquals(false, context.pfpDataStore.data.first()[KEY_GAMEBOOT_ENABLED])
    }

    @Test
    fun `toggling retires the unreleased mode key`() = runTest(dispatcher) {
        context.pfpDataStore.edit { it[KEY_GAMEBOOT_MODE] = "SOUND_ONLY" }

        eventually("the stale mode reads as on") { vm.uiState.first().gameBootEnabled }

        vm.setGameBootEnabled(false)

        eventually("the toggle wins") { !vm.uiState.first().gameBootEnabled }

        assertNull(
            "the retired mode key must not survive a toggle",
            context.pfpDataStore.data.first()[KEY_GAMEBOOT_MODE],
        )
    }

    @Test
    fun `an established install with the old boolean surfaces off in the row`() = runTest(dispatcher) {
        context.pfpDataStore.edit {
            it[KEY_GAMEBOOT_ENABLED] = false
            it[KEY_SETUP_SEEN] = true
        }

        eventually("boolean false surfaces as off in the row") { !vm.uiState.first().gameBootEnabled }
    }

    private companion object {
        val KEY_GAMEBOOT_ENABLED = booleanPreferencesKey("display_gameboot_enabled")
        val KEY_GAMEBOOT_MODE = stringPreferencesKey("display_gameboot_mode")
        val KEY_SETUP_SEEN = booleanPreferencesKey("initial_setup_seen")
    }
}
