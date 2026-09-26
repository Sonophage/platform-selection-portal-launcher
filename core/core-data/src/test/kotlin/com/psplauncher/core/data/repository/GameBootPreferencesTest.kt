package com.psplauncher.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.data.datastore.pfpDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GameBootPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var prefs: GameBootPreferences

    @Before
    fun setUp() {
        runBlocking { context.pfpDataStore.edit { it.clear() } }
        prefs = GameBootPreferences(context)
    }

    @Test
    fun `the retired mode key wins over the boolean and the first-run marker`() = runTest {
        context.pfpDataStore.edit {
            it[KEY_MODE] = "FULL"
            it[KEY_ENABLED] = false
            it[KEY_SETUP_SEEN] = true
        }

        assertTrue(prefs.gameBootEnabledFlow.first())
    }

    @Test
    fun `the retired sound-only mode reads as on`() = runTest {
        context.pfpDataStore.edit { it[KEY_MODE] = "SOUND_ONLY" }

        assertTrue(prefs.gameBootEnabledFlow.first())
    }

    @Test
    fun `the retired off mode reads as off`() = runTest {
        context.pfpDataStore.edit { it[KEY_MODE] = "OFF" }

        assertEquals(false, prefs.gameBootEnabledFlow.first())
    }

    @Test
    fun `the boolean is honoured verbatim when no mode key remains`() = runTest {
        context.pfpDataStore.edit { it[KEY_ENABLED] = true }
        assertTrue(prefs.gameBootEnabledFlow.first())

        context.pfpDataStore.edit { it[KEY_ENABLED] = false }
        assertEquals(false, prefs.gameBootEnabledFlow.first())
    }

    @Test
    fun `no keys with the first-run marker resolves off - established install`() = runTest {
        context.pfpDataStore.edit { it[KEY_SETUP_SEEN] = true }

        assertEquals(false, prefs.gameBootEnabledFlow.first())
    }

    @Test
    fun `no keys and no marker resolves on - fresh install`() = runTest {
        assertTrue(prefs.gameBootEnabledFlow.first())
    }

    @Test
    fun `toggling persists the boolean and retires the stale mode key`() = runTest {
        context.pfpDataStore.edit { it[KEY_MODE] = "FULL" }

        prefs.setGameBootEnabled(false)

        assertEquals(false, prefs.gameBootEnabledFlow.first())
        val stored = context.pfpDataStore.data.first()
        assertEquals(false, stored[KEY_ENABLED])
        assertNull(stored[KEY_MODE], "The retired mode key must not survive a toggle")
    }

    private companion object {
        val KEY_MODE = stringPreferencesKey("display_gameboot_mode")
        val KEY_ENABLED = booleanPreferencesKey("display_gameboot_enabled")
        val KEY_SETUP_SEEN = booleanPreferencesKey("initial_setup_seen")
    }
}
