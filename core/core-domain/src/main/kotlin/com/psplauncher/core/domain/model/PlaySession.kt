package com.psplauncher.core.domain.model

data class PlaySession(
    val id: Long = 0,
    val gameId: Long,
    val platformId: String,
    val launchedAt: Long,
    val durationMillis: Long = 0,
)

data class RecentPlatform(
    val platform: Platform,
    val lastPlayedAt: Long,
    val recentGames: List<Game>,
)
