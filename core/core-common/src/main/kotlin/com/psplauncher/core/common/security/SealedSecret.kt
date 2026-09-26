package com.psplauncher.core.common.security

sealed interface SealedSecret {
    val stored: String

    val isProtected: Boolean

    data class Sealed(override val stored: String) : SealedSecret {
        override val isProtected: Boolean get() = true
    }

    data class Unprotected(override val stored: String, val cause: Throwable) : SealedSecret {
        override val isProtected: Boolean get() = false
    }
}

enum class SecretProtection {
    PROTECTED,

    UNPROTECTED,
    ;

    companion object {
        fun of(vararg secrets: SealedSecret): SecretProtection =
            if (secrets.all { it.isProtected }) PROTECTED else UNPROTECTED
    }
}
