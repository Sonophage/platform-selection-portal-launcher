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

/**
 * "Is this provider configured?" for the two credentials that come in a public half and a secret
 * half, when only the public half is there.
 *
 * This is not a hypothetical state. `BackupManager` backs up `ss_username` and `igdb_client_id` as
 * ordinary strings, and lists `ss_password` and `igdb_client_secret` in ENCRYPTED_CREDENTIAL_KEYS
 * — dropped on restore when they cannot be decrypted on the receiving device. So a restore from
 * another device lands exactly here, and it was found on the owner's device: `ss_username` stored,
 * `ss_password` gone, the Artwork settings screen reporting the account as saved, and every scrape
 * running at anonymous rate limits because the scraper asked a different question and got a
 * different answer.
 *
 * `saveSsCredentials` writes both halves in one `edit`, which is why the half state can only
 * arrive via a restore — and why it was never noticed.
 *
 * The rule under test: a provider is configured only when BOTH halves are present, and there is
 * one definition of that which the screen and the scrape path both read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class HalfSavedCredentialTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var keys: MetadataApiKeyProvider

    // Written straight into the store rather than through saveSsCredentials, because that is what
    // a restore does — and because the Android Keystore the real save path seals with does not
    // exist under Robolectric. Plaintext is read back by decryptOrLegacy's legacy branch.
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
