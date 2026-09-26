package com.psplauncher.feature.launcher

data class LaunchRecoveryRequest(
    val gameId: Long,
    val gameTitle: String,
    val platformId: String?,
    val resolved: ResolvedLaunch?,
    val message: String,
    val historyLine: String?,
    val diagnostic: String,

    val kind: LaunchFailureKind = LaunchFailureKind.UNKNOWN,
)

enum class LaunchFailureKind {
    STORAGE_ACCESS_LOST,

    UNKNOWN,
}

class LaunchBlockedException(
    message: String,
    val kind: LaunchFailureKind,
) : IllegalStateException(message)

enum class LaunchRecoveryAction {
    DISMISS,

    RETRY,

    CHANGE_EMULATOR,

    PER_SYSTEM_DEFAULTS,

    COPY_DIAGNOSTIC,

    OPEN_LIBRARY,
}

fun launchRecoveryActions(request: LaunchRecoveryRequest): List<Pair<LaunchRecoveryAction, String>> =
    buildList {
        if (request.kind == LaunchFailureKind.STORAGE_ACCESS_LOST) {
            add(LaunchRecoveryAction.OPEN_LIBRARY to "Reconnect storage")
        }
        add(LaunchRecoveryAction.RETRY to "Retry")
        add(LaunchRecoveryAction.CHANGE_EMULATOR to "Change emulator")
        if (request.resolved != null) {
            add(LaunchRecoveryAction.PER_SYSTEM_DEFAULTS to "Change per-system default")
        }
        add(LaunchRecoveryAction.COPY_DIAGNOSTIC to "Copy diagnostics")
        add(LaunchRecoveryAction.DISMISS to "Dismiss")
    }
