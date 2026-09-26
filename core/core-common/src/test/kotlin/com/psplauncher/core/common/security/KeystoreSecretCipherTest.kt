package com.psplauncher.core.common.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class KeystoreSecretCipherTest {
    @Test
    fun `successful seal reports the value as protected`() {
        val result = KeystoreSecretCipher.sealWith("hunter2") { "sealed:$it" }

        assertTrue(result is SealedSecret.Sealed)
        assertEquals("sealed:hunter2", result.stored)
        assertTrue(result.isProtected)
    }

    @Test
    fun `keystore failure reports unprotected rather than silently returning plaintext`() {
        val boom = IllegalStateException("keystore unavailable")

        val result = KeystoreSecretCipher.sealWith("hunter2") { throw boom }

        assertTrue(result is SealedSecret.Unprotected)

        assertEquals("hunter2", result.stored)
        assertEquals(false, result.isProtected)
        assertSame(boom, (result as SealedSecret.Unprotected).cause)
    }

    @Test
    fun `unprotected outcome survives being treated as a plain stored value`() {
        val result = KeystoreSecretCipher.sealWith("  spaced  ") { throw RuntimeException() }

        assertEquals("  spaced  ", result.stored)
    }
}
