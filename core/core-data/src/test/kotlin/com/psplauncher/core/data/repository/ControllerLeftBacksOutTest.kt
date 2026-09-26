package com.psplauncher.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.data.datastore.pfpDataStore
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ControllerLeftBacksOutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val key = booleanPreferencesKey("controller_left_backs_out")
    private lateinit var repository: ControllerLayoutRepository

    @Before
    fun setUp() {
        runBlocking { context.pfpDataStore.edit { it.clear() } }
        repository = ControllerLayoutRepository(context, mockk(relaxed = true))
    }

    @Test
    fun `an absent key reads as on, with nothing written`() = runTest {
        assertTrue(repository.prefs.first().leftBacksOut)
        assertNull(context.pfpDataStore.data.first()[key])
    }

    @Test
    fun `it round-trips off and back on`() = runTest {
        repository.setLeftBacksOut(false)
        assertFalse(repository.prefs.first().leftBacksOut)
        assertEquals(false, context.pfpDataStore.data.first()[key])

        repository.setLeftBacksOut(true)
        assertTrue(repository.prefs.first().leftBacksOut)
    }

    @Test
    fun `reset returns it to on`() = runTest {
        repository.setLeftBacksOut(false)
        repository.resetAllPrefs()

        assertTrue(repository.prefs.first().leftBacksOut)
        assertNull(context.pfpDataStore.data.first()[key], "reset must remove the key, not write true over it")
    }
}
