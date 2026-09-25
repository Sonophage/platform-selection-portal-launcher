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

/**
 * The font-colour pref contract. Skeleton copied from `DisplaySettingsViewModelLegibilityTest`.
 *
 * The interesting cases are the two opt-outs, because they are easy to conflate and mean
 * different things: `exact` stops the *adjustment*, `suppressed` stops only the *notice*.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DisplaySettingsViewModelFontColorTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var vm: DisplaySettingsViewModel

    /** A saturated mid-tone that cannot clear 4.5:1 on the settings backdrop as picked. */
    private val failingColor = 0xFF4A90D9L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        runBlocking { context.pfpDataStore.edit { it.clear() } }
        vm = DisplaySettingsViewModel(
            context,
            UiMediaStore(context),
            GameBootPreferences(context),
            // Real, not a mock: it reads the same DataStore the assertions do, so a test about
            // one toggle cannot pass because the other one was stubbed.
            com.psplauncher.core.data.launch.LaunchDiscPreferences(context),
            io.mockk.mockk(relaxed = true),
            // The layout repo only feeds the media rows' face-button shortcuts. A relaxed mock
            // would hand the combine a flow that never emits, so the state would never build.
            io.mockk.mockk(relaxed = true) {
                io.mockk.every { prefs } returns kotlinx.coroutines.flow.flowOf(
                    com.psplauncher.core.domain.model.ControllerLayoutPrefs()
                )
            },
            // The disk work runs on the TEST scheduler, not a real pool. Without this the
            // ViewModel's luma computation and DataStore reads hop to Dispatchers.IO while this
            // test advances virtual time, and the wait below expires on wall-clock under a
            // loaded full-suite run with the work still queued. Raising the budget cannot fix a
            // race between two clocks; it had already gone 10s -> 60s and still timed out.
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
        // Transient: dismissing must not write anything.
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
        // The distinction that matters: the clamp is NOT switched off, only the telling.
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

    /**
     * Runs [body] with a live collector on `uiState` for the whole test.
     *
     * Without one, every read here is a race the test loses at random. `uiState` is shared with
     * `SharingStarted.WhileSubscribed(5_000)`, and `first()` subscribes then leaves immediately
     * with whatever value is already cached. The upstream it just started hops to
     * `Dispatchers.IO` (a directory listing plus a DataStore read) while the very next
     * `advanceUntilIdle()` advances VIRTUAL time past the five-second stop timeout and cancels
     * it. The real IO lands after the cancellation, the cached value is never replaced, and the
     * wait spins until it times out with the preference already written and the state still null.
     *
     * A subscriber that outlives the polling keeps the upstream alive, so the cached value is
     * genuinely current and `first()` means what it appears to mean. backgroundScope is cancelled
     * when the test ends.
     */
    private fun uiTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        body()
    }

    /** Same wait idiom as the sibling legibility test — see its KDoc. */

    private companion object {
        // Mirrored by their string contract, like the sibling tests — these keys are private to
        // the ViewModel, and the string is the part that must not drift.
        val KEY_TEXT_COLOR = longPreferencesKey("display_text_color")
        val KEY_EXACT = booleanPreferencesKey("display_text_color_exact")
        val KEY_LEGIBILITY = stringPreferencesKey("display_text_legibility")
        val KEY_NOTICE_SUPPRESSED = booleanPreferencesKey("display_text_contrast_notice_suppressed")
    }
}
