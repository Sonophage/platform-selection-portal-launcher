package com.psplauncher.feature.launcher

import android.content.Context
import android.content.Intent
import com.psplauncher.core.common.launch.LaunchTransition
import com.psplauncher.core.common.launch.LaunchTransition.withoutTransition
import com.psplauncher.core.domain.model.Game
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

fun interface LaunchClock {
    fun now(): Long
}

data class PendingLaunch(
    val game: Game,
    val resolved: ResolvedLaunch?,
    val intentSummary: String,

    val dispatchedAtMs: Long,

    val dispatchedAtWallMs: Long,
)

sealed interface LaunchDispatchResult {
    data class Rejected(val message: String) : LaunchDispatchResult

    data object Accepted : LaunchDispatchResult
}

@Singleton
class LaunchDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outcomeRecorder: LaunchOutcomeRecorder,
    @LaunchDispatcherScope private val scope: CoroutineScope,
    @LaunchRealtimeClock private val clock: LaunchClock,
    @LaunchWallClock private val wallClock: LaunchClock,
    private val gameBootGate: GameBootGate,

    private val menuSound: com.psplauncher.core.ui.sound.MenuSoundPlayer,
    private val autoCoreMemory: AutoCoreMemory,
    private val gameRepository: com.psplauncher.core.domain.repository.GameRepository,
) {
    private val _recoveryRequests = MutableStateFlow<LaunchRecoveryRequest?>(null)

    val recoveryRequests: StateFlow<LaunchRecoveryRequest?> = _recoveryRequests.asStateFlow()

    private var pending: PendingLaunch? = null
    private var hostStopped = false
    private var watchdog: Job? = null

    suspend fun launch(game: Game, resolved: ResolvedLaunch?, intent: Intent): LaunchDispatchResult {
        gameBootGate.awaitPresentation(game.displayTitle, game.discFaceUri)
        return try {
            context.startActivity(
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).withoutTransition(),
                LaunchTransition.options(context),
            )

            resolved?.profile?.takeIf { it.isRetroArchProfile() }?.let { profile ->
                autoCoreMemory.remember(game.platformId, profile.id)
            }
            val dispatchedAt = clock.now()
            val dispatchedAtWall = wallClock.now()

            scope.launch {
                runCatching { gameRepository.markOpened(game.id, dispatchedAtWall) }
                    .onFailure { Timber.w(it, "Could not stamp gameId=${game.id} on the Last Played shelf") }
            }
            acceptPending(
                PendingLaunch(
                    game         = game,
                    resolved     = resolved,
                    intentSummary = intent.toUri(Intent.URI_INTENT_SCHEME),
                    dispatchedAtMs = dispatchedAt,
                    dispatchedAtWallMs = dispatchedAtWall,
                )
            )
            LaunchDispatchResult.Accepted
        } catch (e: android.content.ActivityNotFoundException) {
            Timber.w(e, "Launch startActivity failed: emulator activity not found (gameId=${game.id})")
            settleImmediateFailure(game, resolved, "Emulator not found. Is it installed?")
        } catch (e: SecurityException) {
            Timber.w(e, "Launch startActivity failed: permission denied (gameId=${game.id})")
            settleImmediateFailure(game, resolved, "Permission denied launching emulator")
        } catch (e: Exception) {
            Timber.w(e, "Launch startActivity failed (gameId=${game.id})")
            settleImmediateFailure(game, resolved, "Could not open emulator: ${e.message}")
        }
    }

    suspend fun recordPreflightFailure(
        game: Game,
        resolved: ResolvedLaunch?,
        reason: String,
        offerRecovery: Boolean = true,
        kind: LaunchFailureKind = LaunchFailureKind.UNKNOWN,
    ) {
        Timber.w("Launch blocked by preflight: gameId=${game.id}, reason=$reason")
        outcomeRecorder.record(
            outcomeFor(game, resolved, LaunchOutcomeStatus.INTENT_FAILED, reason)
        )
        menuSound.play(com.psplauncher.core.ui.sound.MenuSound.ERROR)
        if (offerRecovery) emitRecovery(game, resolved, reason, kind)
    }

    suspend fun requestRecovery(
        game: Game,
        resolved: ResolvedLaunch?,
        message: String,
        kind: LaunchFailureKind = LaunchFailureKind.UNKNOWN,
    ) {
        emitRecovery(game, resolved, message, kind)
    }

    fun dismissRecovery() {
        _recoveryRequests.value = null
    }

    fun onHostStopped() {
        if (pending == null) return
        hostStopped = true
        watchdog?.cancel()
        watchdog = null
    }

    fun onHostResumed() {
        val p = pending ?: return
        pending = null
        val emulatorTookForeground = hostStopped
        hostStopped = false
        watchdog?.cancel()
        watchdog = null

        if (emulatorTookForeground) {
            val playedMs = clock.now() - p.dispatchedAtMs
            Timber.i("Launch session ${playedMs}ms — emulator covered the launcher — recording success")
            scope.launch {
                outcomeRecorder.record(
                    outcomeFor(
                        p.game, p.resolved, LaunchOutcomeStatus.SUCCEEDED, reason = null,
                    ).copy(returnedAtMs = wallClock.now())
                )

                runCatching {
                    gameRepository.recordPlaySession(
                        com.psplauncher.core.domain.model.PlaySession(
                            gameId         = p.game.id,
                            platformId     = p.game.platformId,

                            launchedAt     = p.dispatchedAtWallMs,
                            durationMillis = playedMs,
                        )
                    )
                }.onFailure { Timber.w(it, "Could not record play session for gameId=${p.game.id}") }
            }
        } else {
            Timber.w("Launch returned without the emulator covering the launcher (${clock.now() - p.dispatchedAtMs}ms)")
            scope.launch {
                outcomeRecorder.record(
                    outcomeFor(
                        p.game, p.resolved, LaunchOutcomeStatus.NEVER_FOREGROUNDED,
                        "The emulator never came to the foreground after launch.",
                    )
                )
                emitRecovery(
                    p.game, p.resolved,
                    "The emulator never appeared. Check that it is installed and up to date, " +
                        "then try launching again.",
                )
            }
        }
    }

    private fun acceptPending(p: PendingLaunch) {
        pending = p
        hostStopped = false

        watchdog = scope.launch {
            delay(STOP_WINDOW_MS)
            val stillPending = pending?.game?.id == p.game.id
            if (stillPending && !hostStopped) {
                pending = null
                Timber.w("No activity covered the launcher within ${STOP_WINDOW_MS}ms of dispatch")
                outcomeRecorder.record(
                    outcomeFor(
                        p.game, p.resolved, LaunchOutcomeStatus.NEVER_FOREGROUNDED,
                        "The emulator never came to the foreground after launch.",
                    )
                )
                emitRecovery(
                    p.game, p.resolved,
                    "The emulator never appeared. Check that it is installed and up to date, " +
                        "then try launching again.",
                )
            }
        }
    }

    private suspend fun settleImmediateFailure(
        game: Game,
        resolved: ResolvedLaunch?,
        message: String,
    ): LaunchDispatchResult {
        outcomeRecorder.record(
            outcomeFor(game, resolved, LaunchOutcomeStatus.INTENT_FAILED, message)
        )
        menuSound.play(com.psplauncher.core.ui.sound.MenuSound.ERROR)
        emitRecovery(game, resolved, message)
        return LaunchDispatchResult.Rejected(message)
    }

    private suspend fun emitRecovery(
        game: Game,
        resolved: ResolvedLaunch?,
        message: String,
        kind: LaunchFailureKind = LaunchFailureKind.UNKNOWN,
    ) {
        val recent = runCatching { outcomeRecorder.recentForGame(game.id, RECENT_LIMIT) }
            .getOrDefault(emptyList())
        val recentFailures = recent.count { it.status != LaunchOutcomeStatus.SUCCEEDED }
        val historyLine = when {
            recent.isEmpty() -> null
            recentFailures == 0 -> null
            else -> "$recentFailures of the last ${recent.size} launches for this game failed."
        }
        _recoveryRequests.value = LaunchRecoveryRequest(
            gameId          = game.id,
            gameTitle       = game.displayTitle,
            platformId      = game.platformId,
            resolved        = resolved,
            message         = message,
            historyLine     = historyLine,
            diagnostic      = buildDiagnostic(game, resolved, recent.firstOrNull()),
            kind            = kind,
        )
    }

    private fun outcomeFor(
        game: Game,
        resolved: ResolvedLaunch?,
        status: LaunchOutcomeStatus,
        reason: String?,
    ) = LaunchOutcome(
        gameId        = game.id,
        gameTitle     = game.displayTitle,
        platformId    = game.platformId,
        emulatorId    = resolved?.profile?.id,
        emulatorName  = resolved?.profile?.name,
        corePath      = resolved?.corePath,
        coreName      = resolved?.coreName,
        source        = resolved?.source,
        status        = status,
        failureReason = reason,
        launchedAtMs  = System.currentTimeMillis(),
    )

    private fun buildDiagnostic(
        game: Game,
        resolved: ResolvedLaunch?,
        last: LaunchOutcome?,
    ): String = buildString {
        appendLine("PSPLauncher — launch diagnostic")
        appendLine("Game: ${game.title} (id ${game.id})")
        appendLine("Platform: ${game.platformId}")
        appendLine("Emulator: ${resolved?.profile?.name ?: last?.emulatorName ?: "unknown"}")
        if (resolved?.coreName != null) appendLine("Core: ${resolved.coreName}")
        appendLine("Source: ${resolved?.source?.name ?: last?.source?.name ?: "n/a"}")
        appendLine("ROM: ${game.romPath ?: game.romUri ?: game.packageName ?: game.launchToken ?: "n/a"}")
        if (game.isMissing) appendLine("Missing: yes (file not found on last scan)")
        if (last?.failureReason != null) appendLine("Last failure: ${last.failureReason}")
    }

    companion object {
        const val STOP_WINDOW_MS = 6_000L
        private const val RECENT_LIMIT = 5
    }
}
