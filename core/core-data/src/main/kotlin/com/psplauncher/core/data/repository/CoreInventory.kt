package com.psplauncher.core.data.repository

sealed interface CoreInventory {
    val coreFiles: Set<String>

    val isAuthoritative: Boolean

    data object Unlinked : CoreInventory {
        override val coreFiles: Set<String> = emptySet()
        override val isAuthoritative: Boolean = false
    }

    data class Verified(override val coreFiles: Set<String>) : CoreInventory {
        override val isAuthoritative: Boolean = true
    }

    data object EmptyTree : CoreInventory {
        override val coreFiles: Set<String> = emptySet()
        override val isAuthoritative: Boolean = false
    }

    data class Remembered(override val coreFiles: Set<String>) : CoreInventory {
        override val isAuthoritative: Boolean = false
    }
}
