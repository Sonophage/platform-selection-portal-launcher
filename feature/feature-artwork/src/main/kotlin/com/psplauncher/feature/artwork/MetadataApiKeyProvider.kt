package com.psplauncher.feature.artwork

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psplauncher.core.common.security.KeystoreSecretCipher
import com.psplauncher.core.common.security.SecretProtection
import com.psplauncher.core.data.datastore.pfpDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataApiKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Secrets (TGDB key, IGDB client secret) are encrypted at rest via the Keystore-backed cipher;
    // the IGDB client id is a public identifier and stays plaintext. decryptOrLegacy keeps any
    // pre-encryption values working until they're re-saved.
    // ── TheGamesDB ────────────────────────────────────────────────────────────
    val tgdbKeyFlow: Flow<String?> = context.pfpDataStore.data
        .map { prefs -> prefs[KEY_TGDB_API_KEY]?.let { KeystoreSecretCipher.decryptOrLegacy(it) } }

    suspend fun getTgdbKey(): String? = tgdbKeyFlow.first()

    suspend fun saveTgdbKey(key: String): SecretProtection {
        val sealed = KeystoreSecretCipher.seal(key.trim())
        context.pfpDataStore.edit { it[KEY_TGDB_API_KEY] = sealed.stored }
        return SecretProtection.of(sealed)
    }

    suspend fun clearTgdbKey() {
        context.pfpDataStore.edit { it.remove(KEY_TGDB_API_KEY) }
    }

    // ── IGDB ──────────────────────────────────────────────────────────────────
    val igdbClientIdFlow: Flow<String?> = context.pfpDataStore.data.map { it[KEY_IGDB_CLIENT_ID] }

    suspend fun getIgdbClientId(): String? = igdbClientIdFlow.first()
    suspend fun getIgdbClientSecret(): String? =
        context.pfpDataStore.data.first()[KEY_IGDB_CLIENT_SECRET]?.let { KeystoreSecretCipher.decryptOrLegacy(it) }

    suspend fun saveIgdbCredentials(clientId: String, clientSecret: String): SecretProtection {
        val sealed = KeystoreSecretCipher.seal(clientSecret.trim())
        context.pfpDataStore.edit {
            it[KEY_IGDB_CLIENT_ID]     = clientId.trim()
            it[KEY_IGDB_CLIENT_SECRET] = sealed.stored
        }
        return SecretProtection.of(sealed)
    }

    suspend fun clearIgdbCredentials() {
        context.pfpDataStore.edit {
            it.remove(KEY_IGDB_CLIENT_ID)
            it.remove(KEY_IGDB_CLIENT_SECRET)
        }
    }

    suspend fun hasTgdbKey(): Boolean = getTgdbKey()?.isNotBlank() == true

    /**
     * "IGDB is configured" as a flow, so a screen cannot answer it differently from the scraper.
     *
     * These pairs are a public half and a secret half, and the secret half can go missing on its
     * own: a restore carries `igdb_client_id` but DROPS `igdb_client_secret` when the archive came
     * from another device, because it cannot be decrypted here (BackupManager's
     * ENCRYPTED_CREDENTIAL_KEYS). A predicate that only checks the public half then reports a
     * configured provider that cannot authenticate, which is the most expensive way to be wrong
     * about a credential: everything looks right and nothing works.
     */
    val hasIgdbCredentialsFlow: Flow<Boolean> = context.pfpDataStore.data.map { prefs ->
        !prefs[KEY_IGDB_CLIENT_ID].isNullOrBlank() &&
            !prefs[KEY_IGDB_CLIENT_SECRET]?.let { KeystoreSecretCipher.decryptOrLegacy(it) }.isNullOrBlank()
    }

    suspend fun hasIgdbCredentials(): Boolean = hasIgdbCredentialsFlow.first()

    // ── ScreenScraper (user account — raises thread count & daily quota) ──────
    // The username is a public handle (plaintext); the password is encrypted at rest like the
    // other scraper secrets and dropped on cross-device restore by BackupManager.
    val ssUsernameFlow: Flow<String?> = context.pfpDataStore.data.map { it[KEY_SS_USERNAME] }

    suspend fun getSsUsername(): String? = ssUsernameFlow.first()
    suspend fun getSsPassword(): String? =
        context.pfpDataStore.data.first()[KEY_SS_PASSWORD]?.let { KeystoreSecretCipher.decryptOrLegacy(it) }

    suspend fun saveSsCredentials(username: String, password: String): SecretProtection {
        val sealed = KeystoreSecretCipher.seal(password.trim())
        context.pfpDataStore.edit {
            it[KEY_SS_USERNAME] = username.trim()
            it[KEY_SS_PASSWORD] = sealed.stored
        }
        return SecretProtection.of(sealed)
    }

    suspend fun clearSsCredentials() {
        context.pfpDataStore.edit {
            it.remove(KEY_SS_USERNAME)
            it.remove(KEY_SS_PASSWORD)
        }
    }

    /** "The ScreenScraper account is usable" — one definition, for the same reason as IGDB above. */
    val hasSsCredentialsFlow: Flow<Boolean> = context.pfpDataStore.data.map { prefs ->
        !prefs[KEY_SS_USERNAME].isNullOrBlank() &&
            !prefs[KEY_SS_PASSWORD]?.let { KeystoreSecretCipher.decryptOrLegacy(it) }.isNullOrBlank()
    }

    suspend fun hasSsCredentials(): Boolean = hasSsCredentialsFlow.first()

    // Note on the ScreenScraper developer pair: it is required for the API to answer at all, but
    // it is not user-entered — it ships obfuscated inside the APK (see the buildConfigField byte
    // arrays in feature-artwork/build.gradle.kts and credentials/DevPairDecoder). Only the
    // optional user account above is stored here.

    companion object {
        private val KEY_TGDB_API_KEY       = stringPreferencesKey("tgdb_api_key")
        private val KEY_IGDB_CLIENT_ID     = stringPreferencesKey("igdb_client_id")
        private val KEY_IGDB_CLIENT_SECRET = stringPreferencesKey("igdb_client_secret")
        private val KEY_SS_USERNAME        = stringPreferencesKey("ss_username")
        private val KEY_SS_PASSWORD        = stringPreferencesKey("ss_password")
    }
}
