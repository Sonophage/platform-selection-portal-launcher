package com.psplauncher.feature.launcher

enum class LaunchOutcomeStatus {
    SUCCEEDED,
    NEVER_FOREGROUNDED,
    INTENT_FAILED,
}

data class LaunchOutcome(
    val gameId: Long,
    val gameTitle: String,
    val platformId: String?,
    val emulatorId: String?,
    val emulatorName: String?,
    val corePath: String?,
    val coreName: String?,

    val source: LaunchSource?,
    val status: LaunchOutcomeStatus,
    val failureReason: String? = null,
    val launchedAtMs: Long,
    val returnedAtMs: Long? = null,
)
