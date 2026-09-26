package com.psplauncher.feature.settings.viewmodel

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.data.datastore.pfpDataStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.psplauncher.core.data.repository.GameBootPreferences
import com.psplauncher.core.data.repository.UiMediaStore
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DisplaySettingsViewModelWallpaperTest {
    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var vm: DisplaySettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        runBlocking { context.pfpDataStore.edit { it.clear() } }
        File(context.filesDir, "wallpaper").deleteRecursively()
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
        File(context.filesDir, "wallpaper").deleteRecursively()
    }

    @Test
    fun `clearing the wallpaper clears both the poster and motion keys`() = runTest(dispatcher) {
        context.pfpDataStore.edit {
            it[KEY_CUSTOM_WALLPAPER] = "/old/wallpaper.jpg"
            it[KEY_MOTION_WALLPAPER] = "/old/wallpaper.mp4"
        }

        vm.clearWallpaper()

        eventuallyPrefs("reset clears both keys") {
            it[KEY_CUSTOM_WALLPAPER] == null && it[KEY_MOTION_WALLPAPER] == null
        }
    }

    @Test
    fun `clearing the wallpaper deletes the cleared pair's files`() = runTest(dispatcher) {
        val dir = File(context.filesDir, "wallpaper").apply { mkdirs() }
        val pair = File(dir, "wallpaper_123.jpg").apply { writeText("x") }
        val motion = File(dir, "wallpaper_123.mp4").apply { writeText("x") }
        context.pfpDataStore.edit {
            it[KEY_CUSTOM_WALLPAPER] = pair.absolutePath
            it[KEY_MOTION_WALLPAPER] = motion.absolutePath
        }

        vm.clearWallpaper()

        eventually("both files of the cleared pair are removed") {
            !pair.isFile && !motion.isFile
        }
    }

    @Test
    fun `still image import writes the poster pref and keeps working as before`() = runTest(dispatcher) {
        val uri = registerStream(jpegBytes())

        vm.onWallpaperPicked(uri)

        val prefs = eventuallyPrefs("still import sets the poster pref") {
            it[KEY_CUSTOM_WALLPAPER] != null
        }
        val path = prefs[KEY_CUSTOM_WALLPAPER]!!
        assertTrue(File(path).isFile)
        assertTrue("a still import must not set the motion key", prefs[KEY_MOTION_WALLPAPER] == null)
    }

    @Test
    fun `a still import replaces a motion wallpaper wholesale`() = runTest(dispatcher) {
        val dir = File(context.filesDir, "wallpaper").apply { mkdirs() }
        val oldPoster = File(dir, "wallpaper_1.jpg").apply { writeText("old") }
        val oldMotion = File(dir, "wallpaper_1.mp4").apply { writeText("old") }
        context.pfpDataStore.edit {
            it[KEY_CUSTOM_WALLPAPER] = oldPoster.absolutePath
            it[KEY_MOTION_WALLPAPER] = oldMotion.absolutePath
        }

        val uri = registerStream(jpegBytes())
        vm.onWallpaperPicked(uri)

        val prefs = eventuallyPrefs("the still import applies") {
            it[KEY_CUSTOM_WALLPAPER] != null && it[KEY_CUSTOM_WALLPAPER] != oldPoster.absolutePath
        }
        assertTrue(
            "the motion key must clear when a still replaces the pair",
            prefs[KEY_MOTION_WALLPAPER] == null,
        )
        eventually("the old pair's files are pruned") {
            !oldPoster.isFile && !oldMotion.isFile
        }
    }

    private suspend fun TestScope.eventuallyPrefs(
        reason: String,
        predicate: (Preferences) -> Boolean,
    ): Preferences {
        var snapshot: Preferences? = null
        eventually(reason) {
            snapshot = context.pfpDataStore.data.first()
            predicate(snapshot!!)
        }
        return snapshot!!
    }

    private fun registerStream(bytes: ByteArray): Uri {
        val uri = Uri.parse("content://test/${System.nanoTime()}/pick")
        shadowOf(context.contentResolver).registerInputStream(uri, bytes.inputStream())
        return uri
    }

    private fun jpegBytes(width: Int = 64, height: Int = 64): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        return java.io.ByteArrayOutputStream().also {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
        }.toByteArray()
    }

    private companion object {
        val KEY_CUSTOM_WALLPAPER = stringPreferencesKey("display_custom_wallpaper")
        val KEY_MOTION_WALLPAPER = stringPreferencesKey("display_motion_wallpaper")
    }
}
