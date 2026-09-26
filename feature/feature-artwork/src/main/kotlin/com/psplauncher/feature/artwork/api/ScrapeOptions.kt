package com.psplauncher.feature.artwork.api

data class ScrapeOptions(
    val preferSteamGridDbHeroes: Boolean = false,
    val downloadClearLogos: Boolean = true,
    val downloadHeroes: Boolean = true,

    val downloadManuals: Boolean = true,
    val downloadVideoSnaps: Boolean = false,

    val bypassSsCache: Boolean = false,

    val metadataOnly: Boolean = false,
)
