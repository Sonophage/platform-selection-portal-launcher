package com.psplauncher.feature.artwork

import android.content.Context
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class HalfSavedCredentialTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var keys: MetadataApiKeyProvider

    private val ssUser = stringPreferencesKey("ss_username")
    private val ssPass = stringPreferencesKey("ss_password")
    private val igdbId = stringPreferencesKey("igdb_client_id")
    private val igdbSecret = stringPreferencesKey("igdb_client_secret")

    @Before
    fun setUp() {
        runBlocking { context.pfpDataStore.edit { it.clear() } }
        keys = MetadataApiKeyProvider(context)
    }

    @Test
    fun `a ScreenScraper username with no password is not a usable account`() = runTest {
        context.pfpDataStore.edit { it[ssUser] = "someone" }

        assertFalse(keys.hasSsCredentialsFlow.first())
        assertFalse(keys.hasSsCredentials())
    }

    @Test
    fun `both ScreenScraper halves present is a usable account`() = runTest {
        context.pfpDataStore.edit {
            it[ssUser] = "someone"
            it[ssPass] = "a-password"
        }

        assertTrue(keys.hasSsCredentialsFlow.first())
        assertTrue(keys.hasSsCredentials())
    }

    @Test
    fun `a blank password is not a password`() = runTest {
        context.pfpDataStore.edit {
            it[ssUser] = "someone"
            it[ssPass] = "   "
        }

        assertFalse(keys.hasSsCredentialsFlow.first())
    }

    @Test
    fun `an IGDB client id with no secret is not configured`() = runTest {
        context.pfpDataStore.edit { it[igdbId] = "public-client-id" }

        assertFalse(keys.hasIgdbCredentialsFlow.first())
        assertFalse(keys.hasIgdbCredentials())
    }

    @Test
    fun `both IGDB halves present is configured`() = runTest {
        context.pfpDataStore.edit {
            it[igdbId] = "public-client-id"
            it[igdbSecret] = "the-secret"
        }

        assertTrue(keys.hasIgdbCredentialsFlow.first())
        assertTrue(keys.hasIgdbCredentials())
    }

    @Test
    fun `an empty store is configured for neither`() = runTest {
        assertFalse(keys.hasSsCredentialsFlow.first())
        assertFalse(keys.hasIgdbCredentialsFlow.first())
    }
}
