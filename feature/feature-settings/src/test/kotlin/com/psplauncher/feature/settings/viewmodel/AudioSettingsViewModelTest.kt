package com.psplauncher.feature.settings.viewmodel

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.data.repository.ControllerLayoutRepository
import com.psplauncher.core.data.repository.ControllerMappingRepository
import com.psplauncher.core.data.repository.MediaDisplayNames
import com.psplauncher.core.data.repository.UiMediaStore
import com.psplauncher.core.domain.model.UiMediaSlot
import com.psplauncher.core.ui.media.UiMediaAudioPlayer
import com.psplauncher.core.ui.sound.MenuSound
import com.psplauncher.core.ui.sound.MenuSoundPlayer
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AudioSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val mainDispatcher = UnconfinedTestDispatcher(dispatcher.scheduler)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val menuSound: MenuSoundPlayer = mockk(relaxed = true)
    private val bootPreviewer: UiMediaAudioPlayer = mockk(relaxed = true)
    private lateinit var store: UiMediaStore
    private lateinit var vm: AudioSettingsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(mainDispatcher)

        runBlocking { withTimeout(5_000) { context.pfpDataStore.edit { it.clear() } } }
        File(context.filesDir, UiMediaStore.UI_MEDIA_DIR).deleteRecursively()
        MediaDisplayNames.clearCache()
        store = UiMediaStore(context)
        vm = AudioSettingsViewModel(
            context,
            store,
            menuSound,
            com.psplauncher.core.data.media.MenuMusicPreferences(context),
            bootPreviewer,
            ControllerLayoutRepository(context, ControllerMappingRepository(context)),
        )
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun kotlinx.coroutines.test.TestScope.collectUiState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    private fun kotlinx.coroutines.test.TestScope.eventually(
        what: String,
        timeoutMs: Long = 5_000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for: $what")
            }
            Thread.sleep(50)
            testScheduler.advanceUntilIdle()
        }
    }

    private fun seedAssignment(slot: UiMediaSlot, ext: String = "wav") {
        val dir = File(context.filesDir, UiMediaStore.UI_MEDIA_DIR)
        dir.mkdirs()
        File(dir, "${slot.key}.$ext").writeBytes(ByteArray(64))
    }

    private fun seedDisplayName(slot: UiMediaSlot, name: String) {
        runBlocking {
            context.pfpDataStore.edit { it[UiMediaStore.displayNameKey(slot)] = name }
        }
    }

    private fun mediaFile(slot: UiMediaSlot, ext: String) =
        File(File(context.filesDir, UiMediaStore.UI_MEDIA_DIR), "${slot.key}.$ext")

    @Test fun `the Sound screen lists the eight menu sounds then the three presentation tracks`() =
        runTest(dispatcher) {
            assertEquals(
                listOf(
                    "sound_scroll", "sound_select", "sound_system_browse",
                    "sound_back", "sound_confirm", "sound_error",
                    "sound_launch", "sound_notification",
                    "boot_audio", "launch_disc_audio", "gameboot_audio",
                ),
                AudioSettingsViewModel.SOUND_SLOTS.map { it.key },
            )
        }

    @Test fun `an assigned boot audio row gets its name and the use-default affordance`() =
        runTest(dispatcher) {
            seedAssignment(UiMediaSlot.BOOT_AUDIO)
            seedDisplayName(UiMediaSlot.BOOT_AUDIO, "opening.wav")
            collectUiState()

            eventually("the Boot row surfaces its name and Use Default") {
                val state = vm.uiState.value
                state.soundLabels[UiMediaSlot.BOOT_AUDIO] == "opening.wav" &&
                    UiMediaSlot.BOOT_AUDIO in state.assignedSlots
            }
        }

    @Test fun `only rows on this screen can be assigned slots`() = runTest(dispatcher) {
        seedAssignment(UiMediaSlot.BOOT_VIDEO, ext = "mp4")
        seedAssignment(UiMediaSlot.GAMEBOOT_VIDEO, ext = "mp4")
        collectUiState()

        eventually("assignments surface in the ui state") {
            vm.uiState.value.soundLabels.isNotEmpty()
        }

        val state = vm.uiState.value
        assertFalse(UiMediaSlot.BOOT_VIDEO in state.soundLabels.keys, "boot video is not a sound row")
        assertFalse(UiMediaSlot.BOOT_VIDEO in state.assignedSlots)
        assertFalse(UiMediaSlot.GAMEBOOT_VIDEO in state.assignedSlots, "GameBoot has its own screen")
    }

    @Test fun `preview on the boot row plays through the boot previewer, not SoundPool`() =
        runTest(dispatcher) {
            vm.preview(UiMediaSlot.BOOT_AUDIO)
            advanceUntilIdle()

            verify(exactly = 1) { bootPreviewer.play(UiMediaSlot.BOOT_AUDIO, null) }
            verify(exactly = 0) { menuSound.play(any(), any()) }
        }

    @Test fun `preview on the boot row hands the custom assignment to the previewer`() =
        runTest(dispatcher) {
            seedAssignment(UiMediaSlot.BOOT_AUDIO, ext = "mp3")
            collectUiState()
            eventually("the assignment is visible") {
                UiMediaSlot.BOOT_AUDIO in vm.uiState.value.assignedSlots
            }

            vm.preview(UiMediaSlot.BOOT_AUDIO)
            advanceUntilIdle()

            verify(exactly = 1) {
                bootPreviewer.play(UiMediaSlot.BOOT_AUDIO, mediaFile(UiMediaSlot.BOOT_AUDIO, "mp3").absolutePath)
            }
        }

    @Test fun `an unassigned row says Default only when it has one to fall back to`() =
        runTest(dispatcher) {
            collectUiState()
            eventually("the row summaries are built") {
                vm.uiState.value.soundLabels.size == AudioSettingsViewModel.SOUND_SLOTS.size
            }
            val labels = vm.uiState.value.soundLabels

            assertEquals(UI_MEDIA_DEFAULT_LABEL, labels[UiMediaSlot.SOUND_SCROLL])
            assertEquals(UI_MEDIA_DEFAULT_LABEL, labels[UiMediaSlot.BOOT_AUDIO])
            assertEquals(UI_MEDIA_DEFAULT_LABEL, labels[UiMediaSlot.GAMEBOOT_AUDIO])

            assertEquals(NO_SOUND_LABEL, labels[UiMediaSlot.LAUNCH_DISC_AUDIO])
        }

    @Test fun `the ceremony rows preview their own slot, not boot audio`() = runTest(dispatcher) {
        vm.preview(UiMediaSlot.LAUNCH_DISC_AUDIO)
        vm.preview(UiMediaSlot.GAMEBOOT_AUDIO)
        advanceUntilIdle()

        verify(exactly = 1) { bootPreviewer.play(UiMediaSlot.LAUNCH_DISC_AUDIO, null) }
        verify(exactly = 1) { bootPreviewer.play(UiMediaSlot.GAMEBOOT_AUDIO, null) }
        verify(exactly = 0) { bootPreviewer.play(UiMediaSlot.BOOT_AUDIO, any()) }
        verify(exactly = 0) { menuSound.play(any(), any()) }
    }

    @Test fun `menu sound rows still preview through SoundPool, never the boot previewer`() =
        runTest(dispatcher) {
            vm.preview(UiMediaSlot.SOUND_SCROLL)
            advanceUntilIdle()
            verify(exactly = 1) { menuSound.play(any(), any()) }
            verify(exactly = 0) { bootPreviewer.play(UiMediaSlot.BOOT_AUDIO, any()) }
        }

    @Test fun `tearing the screen down stops any running boot preview`() = runTest(dispatcher) {
        vm.stopBootPreview()
        verify(exactly = 1) { bootPreviewer.stop() }
    }

    @Test fun `confirmReset clears the seven sounds including boot audio and never touches video media`() =
        runTest(dispatcher) {
            seedAssignment(UiMediaSlot.SOUND_SCROLL)
            seedAssignment(UiMediaSlot.SOUND_NOTIFICATION)
            seedAssignment(UiMediaSlot.BOOT_AUDIO)
            seedAssignment(UiMediaSlot.BOOT_VIDEO, ext = "mp4")
            seedAssignment(UiMediaSlot.GAMEBOOT_VIDEO, ext = "mp4")
            collectUiState()
            advanceUntilIdle()

            vm.requestReset()
            vm.confirmReset()

            eventually("the seven sounds are cleared") {
                !mediaFile(UiMediaSlot.SOUND_SCROLL, "wav").isFile &&
                    !mediaFile(UiMediaSlot.SOUND_NOTIFICATION, "wav").isFile &&
                    !mediaFile(UiMediaSlot.BOOT_AUDIO, "wav").isFile
            }

            assertTrue(mediaFile(UiMediaSlot.BOOT_VIDEO, "mp4").isFile, "reset must never touch the boot video")
            assertTrue(mediaFile(UiMediaSlot.GAMEBOOT_VIDEO, "mp4").isFile, "reset must never touch GameBoot media")
        }

    @Test fun `useDefault drops the boot audio assignment`() = runTest(dispatcher) {
        seedAssignment(UiMediaSlot.BOOT_AUDIO)

        vm.useDefault(UiMediaSlot.BOOT_AUDIO)

        eventually("the boot audio file is removed") {
            !mediaFile(UiMediaSlot.BOOT_AUDIO, "wav").isFile
        }
    }

    @Test fun `a rejected import surfaces the reason and plays the error sound`() = runTest(dispatcher) {
        val uri = Uri.parse("content://test/rejected.mp4")
        org.robolectric.Shadows.shadowOf(context.contentResolver)
            .registerInputStream(uri, java.io.ByteArrayInputStream(ByteArray(64)))
        vm.onPickerLaunchedFor(UiMediaSlot.SOUND_SCROLL)
        collectUiState()
        vm.onSoundPicked(uri)

        eventually("the rejection dialog surfaces") { vm.uiState.value.message != null }

        assertNotNull(vm.uiState.value.message, "the rejection dialog must surface")
        verify { menuSound.play(MenuSound.ERROR) }
    }

    @Test fun `a confirmed reset plays the confirm sound`() = runTest(dispatcher) {
        vm.confirmReset()
        advanceUntilIdle()
        verify { menuSound.play(MenuSound.CONFIRM) }
    }
}
