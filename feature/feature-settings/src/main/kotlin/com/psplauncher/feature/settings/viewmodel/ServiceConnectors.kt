package com.psplauncher.feature.settings.viewmodel

import com.psplauncher.feature.artwork.api.IgdbApi
import com.psplauncher.feature.artwork.api.ScreenScraperApi

/**
 * The one place artwork-provider validate flows live, shared by the settings screens and the
 * first-run setup wizard so they can never drift apart. Each function returns the user-facing
 * status message; callers surface it however their screen does.
 */
internal object ServiceConnectors {


    /** Live IGDB check: requests a Twitch token with the entered pair. */
    suspend fun testIgdb(igdbApi: IgdbApi, clientId: String, clientSecret: String): String =
        if (igdbApi.testCredentials(clientId.trim(), clientSecret.trim())) "Valid"
        else "Invalid — check Client ID and Secret"

    /** Live ScreenScraper check: reports the account's thread/quota limits when valid. */
    suspend fun testScreenScraper(
        screenScraperApi: ScreenScraperApi,
        username: String,
        password: String,
    ): String {
        val user = screenScraperApi.fetchUserInfo(username.trim(), password.trim())
        return if (user != null) {
            buildString {
                append("Valid")
                user.maxThreads?.let { t -> append(" — $t thread${if (t == "1") "" else "s"}") }
                user.maxRequestsPerDay?.let { q -> append(", $q requests/day") }
            }
        } else {
            "Invalid — check username and password"
        }
    }
}
