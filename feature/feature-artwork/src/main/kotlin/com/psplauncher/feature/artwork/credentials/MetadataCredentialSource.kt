package com.psplauncher.feature.artwork.credentials

import kotlinx.coroutines.flow.Flow

data class ScreenScraperCredentials(
    val devId: String,
    val devPassword: String,
    val userId: String? = null,
    val userPassword: String? = null,
) {
    companion object {
        fun of(
            devId: String?,
            devPassword: String?,
            userId: String?,
            userPassword: String?,
        ): ScreenScraperCredentials? {
            val id = devId?.trim().orEmpty()
            val password = devPassword?.trim().orEmpty()
            if (id.isEmpty() || password.isEmpty()) return null
            return ScreenScraperCredentials(
                devId        = id,
                devPassword  = password,
                userId       = userId?.trim()?.takeIf { it.isNotEmpty() },
                userPassword = userPassword?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }
}

interface MetadataCredentialSource {
    val screenScraper: Flow<ScreenScraperCredentials?>

    suspend fun screenScraperNow(): ScreenScraperCredentials?
}
