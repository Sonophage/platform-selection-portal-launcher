package com.psplauncher.core.common.security

import android.util.Base64
import timber.log.Timber

object KeystoreSecretCipher {
    private val aesGcm = KeystoreAesGcm("pfp_secret_key_v1")

    fun seal(plain: String): SealedSecret = sealWith(plain, aesGcm::seal)

    internal fun sealWith(plain: String, seal: (String) -> String): SealedSecret {
        return try {
            SealedSecret.Sealed(seal(plain))
        } catch (e: Exception) {
            Timber.w(e, "Secret encryption failed; storing as-is")
            SealedSecret.Unprotected(plain, e)
        }
    }

    fun isUsableOnThisDevice(stored: String): Boolean {
        val data = try {
            Base64.decode(stored, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return true
        }
        if (data.size <= KeystoreAesGcm.IV_BYTES) return true
        return runCatching { aesGcm.open(stored) }.isSuccess
    }

    fun decryptOrLegacy(stored: String): String {
        return try {
            aesGcm.open(stored)
        } catch (e: Exception) {
            stored
        }
    }
}
