package com.psplauncher.feature.settings.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.data.repository.GameBootPreferences
import com.psplauncher.core.data.repository.UiMediaStore
import com.psplauncher.core.domain.model.IconLegibilityStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The icon-legibility pref contract: cycling persists the enum NAME, and a stale/unknown
 * persisted value surfaces as [IconLegibilityStyle.NONE] rather than throwing — the failure
 * mode `cycleWaveStyle` needed a `runCatching` for. [IconLegibilityStyle.fromName] makes that
 * unnecessary; these tests prove it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DisplaySettingsViewModelLegibilityTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var vm: DisplaySettingsViewModel
    private lateinit var collector: Job

    @Before
    fun setUp() {
        // Order matters. The store is prepared BEFORE Main becomes the test dispatcher.
        //
        // DataStore's first touch initialises the file, and on a CLEAN run that is real work. Do
        // it after setMain and that work is dispatched to a scheduler nobody advances inside
        // runBlocking, so it never finishes and every later read returns the ViewModel's initial
        // state instead of the store's. On an incremental run the file already exists, the first
        // touch short-circuits, and the whole class passes -- which is why this only ever failed
        // on --rerun-tasks and never in an ordinary build.
        runBlocking {
            context.pfpDataStore.edit { it.clear() }
            context.pfpDataStore.data.first()
        }
        Dispatchers.setMain(dispatcher)
        vm = DisplaySettingsViewModel(
            context,
            UiMediaStore(context),
            GameBootPreferences(context),
            // Real, not a mock: it reads the same DataStore the assertions do.
            com.psplauncher.core.data.launch.LaunchDiscPreferences(context),
            io.mockk.mockk(relaxed = true),
            // The layout repo only feeds the media rows' face-button shortcuts. A relaxed mock
            // would hand the combine a flow that never emits, so the state would never build.
            io.mockk.mockk(relaxed = true) {
                io.mockk.every { prefs } returns kotlinx.coroutines.flow.flowOf(
                    com.psplauncher.core.domain.model.ControllerLayoutPrefs()
                )
            },
        )
        // ONE subscriber, alive for the whole test, and it is what makes uiState observable here
        // at all.
        //
        // uiState is shared WhileSubscribed(5s). Reading it with first() returns the cached value
        // and cancels immediately, so the upstream restarts and is dropped again before DataStore's
        // real read can land -- and eventually() polls with first(), so it can poll forever and
        // never see a write. Only the FIRST test in the class escaped that, because its very first
        // read started the upstream and the 5 s stop had not yet elapsed on the virtual clock;
        // every test after it inherited a stopped flow. That is why this class passed as a whole
        // and failed whenever a test ran alone, and why renaming a test could move which one broke.
        collector = CoroutineScope(dispatcher).launch { vm.uiState.collect {} }
        // ...and RUN it. launch only queues onto the test scheduler; until the scheduler is
        // pumped the collector exists as a Job but has never subscribed, so the shared flow is
        // still cold when the test body writes to DataStore, and the write it should have
        // observed is the one it misses. isActive is true throughout, which is what made this so
        // hard to see: the Job is alive, it just has not started.
        dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        collector.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `every style persists its enum name and comes back`() = runTest(dispatcher) {
        // Was written against cycling, which walked the list for you. The picker sets a chosen
        // value instead, so each style is now set directly -- which tests the same round trip
        // and no longer depends on the enum's order meaning anything.
        val expected = IconLegibilityStyle.entries
        val seen = mutableListOf<IconLegibilityStyle>()

        expected.forEach { style ->
            vm.setIconLegibility(style)
            eventually("$style persisted") {
                context.pfpDataStore.data.first()[KEY_ICON_LEGIBILITY] == style.name
            }
            eventually("$style visible") {
                // The DataStore-backed state must have settled to the persisted value before we
                // sample it — the initial StateFlow value precedes the first emission.
                vm.uiState.first().iconLegibility == style
            }
            seen += vm.uiState.first().iconLegibility
        }

        assertEquals(expected, seen)
    }

    @Test
    fun `an unknown persisted value surfaces as NONE and still takes a new pick`() = runTest(dispatcher) {
        context.pfpDataStore.edit {
            it[KEY_ICON_LEGIBILITY] = "CONTOUR_MEDIUM" // a style that no longer exists
        }

        eventually("stale value tolerated") {
            vm.uiState.first().iconLegibility == IconLegibilityStyle.NONE
        }

        // The pipeline genuinely works with the stale key present: a pick lands, proving the
        // read neither crashed nor wedged the state flow.
        vm.setIconLegibility(IconLegibilityStyle.OFFSET_SHADOW)
        eventually("a pick works after a stale value") {
            vm.uiState.first().iconLegibility == IconLegibilityStyle.OFFSET_SHADOW
        }
    }

    @Test
    fun `fade by distance defaults on, toggles off, and persists`() = runTest(dispatcher) {
        // Default ON, where the setting it replaced defaulted OFF. The old toggle asked "draw
        // unselected icons at full opacity?" and this one asks "fade them by distance?", so the
        // out-of-the-box answer is the redesign's ramp rather than the flat dim.
        assertEquals(true, vm.uiState.first().fadeByDistance)

        vm.setFadeByDistance(false)
        eventually("fade by distance persisted off") {
            context.pfpDataStore.data.first()[KEY_FADE_BY_DISTANCE] == false
        }
        eventually("fade by distance surfaced off") {
            !vm.uiState.first().fadeByDistance
        }

        vm.setFadeByDistance(true)
        eventually("fade by distance surfaced on again") {
            vm.uiState.first().fadeByDistance
        }
    }

    @Test
    fun `the old solid-unfocused value does not decide the new one`() = runTest(dispatcher) {
        // The keys are deliberately different strings, and this is why. display_solid_unfocused
        // _icons answered "dim at all?"; display_fade_by_distance answers "dim flat, or by
        // distance?". A stored true for the first carries no opinion about the second, so reusing
        // the key would have silently turned "I did not want dimming" into "I want the ramp" on
        // every device that had ever touched the old toggle — a reading nobody chose, arriving
        // with no visible cause.
        context.pfpDataStore.edit { it[KEY_SOLID_UNFOCUSED_ICONS] = false }
        // Wait for the stale write to be READABLE before asserting anything about it. Asserting
        // straight away would pass while the value was still in flight, which is the same green
        // as a correct implementation and tells you nothing.
        eventually("the stale key is in the store") {
            context.pfpDataStore.data.first()[KEY_SOLID_UNFOCUSED_ICONS] == false
        }

        assertEquals(
            "the new setting must not take its value from the old key",
            true,
            vm.uiState.first().fadeByDistance,
        )
    }

    @Test
    fun `text shadow defaults on, toggles off, and persists`() = runTest(dispatcher) {
        // Default ON: helper text over bright wallpapers is the failure mode this setting exists
        // for, so the out-of-the-box experience ships with the shadow enabled.
        assertEquals(true, vm.uiState.first().textShadow)

        vm.setTextShadow(false)
        eventually("text shadow persisted off") {
            context.pfpDataStore.data.first()[KEY_TEXT_SHADOW] == false
        }
        eventually("text shadow surfaced off") {
            !vm.uiState.first().textShadow
        }

        vm.setTextShadow(true)
        eventually("text shadow surfaced on again") {
            vm.uiState.first().textShadow
        }
    }

    /**
     * Waits until [condition] holds. Drives the test scheduler (the VM's coroutines) and, in
     * the same loop, sleeps on a REAL IO thread (never the scheduler thread) so wall-clock
     * work — DataStore writes — gets time to land. Same pattern as the wallpaper test.
     */

    private companion object {
        // Mirror the (private) ViewModel keys by their string contract, like the wallpaper test.
        val KEY_ICON_LEGIBILITY = stringPreferencesKey("display_icon_legibility")
        val KEY_FADE_BY_DISTANCE = booleanPreferencesKey("display_fade_by_distance")

        // Still named here on purpose: the point of the test above is that this key is NOT read.
        val KEY_SOLID_UNFOCUSED_ICONS = booleanPreferencesKey("display_solid_unfocused_icons")
        val KEY_TEXT_SHADOW = booleanPreferencesKey("display_text_shadow")
    }
}
