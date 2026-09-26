package com.psplauncher.feature.settings.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.data.repository.GameBootPreferences
import com.psplauncher.core.data.repository.UiMediaStore
import com.psplauncher.core.domain.model.TextLegibilityStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DisplaySettingsViewModelFontColorTest {
    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var vm: DisplaySettingsViewModel

    private val failingColor = 0xFF4A90D9L

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
    fun `picking persists the long and clearing removes the key`() = uiTest {
        assertNull(vm.uiState.first().textColorArgb)

        vm.setTextColor(failingColor)
        eventually("colour persisted") {
            context.pfpDataStore.data.first()[KEY_TEXT_COLOR] == failingColor
        }
        eventually("colour surfaced") { vm.uiState.first().textColorArgb == failingColor }

        vm.setTextColor(null)
        eventually("key removed, not zeroed") {
            !context.pfpDataStore.data.first().contains(KEY_TEXT_COLOR)
        }
        eventually("cleared colour surfaced") { vm.uiState.first().textColorArgb == null }
    }

    @Test
    fun `a colour that cannot pass raises the notice`() = uiTest {
        vm.setTextColor(failingColor)
        eventually("notice raised") { vm.uiState.first().textContrastNotice != null }
        assertNotNull(vm.uiState.first().textContrastNotice)

        vm.dismissTextContrastNotice()
        eventually("notice dismissed") { vm.uiState.first().textContrastNotice == null }

        assertEquals(false, context.pfpDataStore.data.first()[KEY_NOTICE_SUPPRESSED] ?: false)
    }

    @Test
    fun `white raises no notice at all`() = uiTest {
        vm.setTextColor(0xFFFFFFFFL)
        eventually("colour surfaced") { vm.uiState.first().textColorArgb == 0xFFFFFFFFL }
        assertNull("white already passes on the solved scrim", vm.uiState.first().textContrastNotice)
    }

    @Test
    fun `exact suppresses the adjustment notice and persists`() = uiTest {
        vm.setTextColorExact(true)
        eventually("exact persisted") {
            context.pfpDataStore.data.first()[KEY_EXACT] == true
        }
        eventually("exact surfaced") { vm.uiState.first().textColorExact }

        vm.setTextColor(failingColor)
        eventually("colour surfaced") { vm.uiState.first().textColorArgb == failingColor }
        assertNull(
            "exact means the colour is rendered as picked, so there is no adjustment to report",
            vm.uiState.first().textContrastNotice,
        )
    }

    @Test
    fun `suppressed stops the notice while adjustment continues`() = uiTest {
        vm.suppressTextContrastNotice()
        eventually("suppression persisted") {
            context.pfpDataStore.data.first()[KEY_NOTICE_SUPPRESSED] == true
        }

        vm.setTextColor(failingColor)
        eventually("colour surfaced") { vm.uiState.first().textColorArgb == failingColor }
        assertNull(vm.uiState.first().textContrastNotice)

        assertEquals(false, vm.uiState.first().textColorExact)
    }

    @Test
    fun `an unknown persisted legibility style surfaces as the default`() = uiTest {
        context.pfpDataStore.edit { it[KEY_LEGIBILITY] = "PLATE_HEAVY" }

        eventually("stale value tolerated") {
            vm.uiState.first().textLegibility == TextLegibilityStyle.DEFAULT
        }

        vm.setTextLegibility(TextLegibilityStyle.entries.first { it != TextLegibilityStyle.DEFAULT })
        eventually("a pick works after a stale value") {
            vm.uiState.first().textLegibility != TextLegibilityStyle.DEFAULT
        }
    }

    @Test
    fun `choosing a legibility style persists its enum name`() = uiTest {
        val chosen = TextLegibilityStyle.entries.first { it != TextLegibilityStyle.DEFAULT }

        vm.setTextLegibility(chosen)
        eventually("chosen style persisted") {
            context.pfpDataStore.data.first()[KEY_LEGIBILITY] == chosen.name
        }
    }

    private fun uiTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        body()
    }

    private companion object {
        val KEY_TEXT_COLOR = longPreferencesKey("display_text_color")
        val KEY_EXACT = booleanPreferencesKey("display_text_color_exact")
        val KEY_LEGIBILITY = stringPreferencesKey("display_text_legibility")
        val KEY_NOTICE_SUPPRESSED = booleanPreferencesKey("display_text_contrast_notice_suppressed")
    }
}
