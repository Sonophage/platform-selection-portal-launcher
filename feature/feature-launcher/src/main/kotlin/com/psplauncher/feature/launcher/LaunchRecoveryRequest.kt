package com.psplauncher.feature.launcher

/**
 * Why a launch failed + what the user can do about it, produced by [LaunchDispatcher] whenever a
 * game-path launch needs a repair surface instead of a dead end (B1 — launch reliability).
 *
 * The recovery sheet renders from this: the failure message, a history line when the same game has
 * failed recently ("2 of the last 3 launches failed"), and a copyable diagnostic. The emulator
 * identity ([resolved]) is nullable — package/shortcut/native launches have no emulator ladder.
 */
data class LaunchRecoveryRequest(
    val gameId: Long,
    val gameTitle: String,
    val platformId: String?,
    val resolved: ResolvedLaunch?,
    val message: String,
    val historyLine: String?,
    val diagnostic: String,
    /**
     * What kind of failure this was, which decides which remedy the sheet leads with.
     *
     * Structural rather than matched out of [message]: the sheet used to lead with Retry whatever
     * had happened, so on a revoked storage grant the first and only pad-reachable action was the
     * one the message had just finished explaining would fail again.
     */
    val kind: LaunchFailureKind = LaunchFailureKind.UNKNOWN,
)

/** Why a launch could not proceed, as far as the resolver could tell. */
enum class LaunchFailureKind {
    /** The ROM's storage grant is gone: the file is there, this app may no longer open it. */
    STORAGE_ACCESS_LOST,

    /** Anything the resolver could not classify. Retry is a reasonable lead for these. */
    UNKNOWN,
}

/**
 * A launch refused for a reason worth telling the user about, carrying [kind] so the recovery
 * sheet does not have to read the sentence to decide what to offer.
 */
class LaunchBlockedException(
    message: String,
    val kind: LaunchFailureKind,
) : IllegalStateException(message)

/** Buttons the recovery sheet offers, mapped by the host shell to its own navigation. */
enum class LaunchRecoveryAction {
    /** Close the sheet (BACK / dismiss button). */
    DISMISS,
    /** Try the same game again. */
    RETRY,
    /** Open the per-game emulator picker for the failed game. */
    CHANGE_EMULATOR,
    /** Open the per-system defaults screen (per-platform fix). */
    PER_SYSTEM_DEFAULTS,
    /** Copy the diagnostic text to the clipboard. */
    COPY_DIAGNOSTIC,

    /** Open Settings ▸ Library, where a lost storage grant is re-granted. */
    OPEN_LIBRARY,
}

/**
 * The buttons a recovery sheet offers, in the order it offers them (pure — unit-tested).
 *
 * An ordered list rather than a hand-written column, because the order is the decision and it has
 * to be checkable. The sheet used to hard-code Retry first and draw it focused whatever had gone
 * wrong — so on a revoked storage grant, the lead action was the one the message directly above it
 * had just explained would fail again. Only two of the five were reachable by a pad at all.
 *
 * Dismiss is always last and never first: the cursor should not open on the way out.
 */
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
