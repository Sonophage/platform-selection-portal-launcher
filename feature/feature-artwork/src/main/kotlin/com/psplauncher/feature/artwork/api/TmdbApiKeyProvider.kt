package com.psplauncher.feature.artwork.api

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psplauncher.core.common.security.KeystoreSecretCipher
import com.psplauncher.core.common.security.SecretProtection
import com.psplauncher.core.data.datastore.pfpDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")

/**
 * The user's TMDB key, stored the way every other service credential here is.
 *
 * Deliberately the same shape as SgdbApiKeyProvider — sealed through KeystoreSecretCipher, read
 * back with decryptOrLegacy — so there is one way credentials are handled in this app rather than
 * a second one invented for films.
 */
@Singleton
class TmdbApiKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val keyFlow = context.pfpDataStore.data
        .map { prefs -> prefs[KEY_TMDB_API_KEY]?.let { KeystoreSecretCipher.decryptOrLegacy(it) } }

    suspend fun getKey(): String? =
        context.pfpDataStore.data.first()[KEY_TMDB_API_KEY]?.let { KeystoreSecretCipher.decryptOrLegacy(it) }

    suspend fun saveKey(key: String): SecretProtection {
        val sealed = KeystoreSecretCipher.seal(key.trim())
        context.pfpDataStore.edit { it[KEY_TMDB_API_KEY] = sealed.stored }
        return SecretProtection.of(sealed)
    }

    suspend fun clearKey() {
        context.pfpDataStore.edit { it.remove(KEY_TMDB_API_KEY) }
    }

    suspend fun hasKey(): Boolean = !getKey().isNullOrBlank()
}
