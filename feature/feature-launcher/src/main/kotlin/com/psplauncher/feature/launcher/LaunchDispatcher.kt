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

/** Monotonic elapsed-realtime source so verification windows never depend on wall-clock changes. */
fun interface LaunchClock {
    fun now(): Long
}

/** A game launch hand-off request, snapshot at dispatch time so the outcome row is self-describing. */
data class PendingLaunch(
    val game: Game,
    val resolved: ResolvedLaunch?,
    val intentSummary: String,
    /** Elapsed realtime. For measuring how long the launch lasted, and nothing else. */
    val dispatchedAtMs: Long,
    /** Wall clock. The instant that gets written down. See [LaunchWallClock]. */
    val dispatchedAtWallMs: Long,
)

/** Result of handing an intent to the system. */
sealed interface LaunchDispatchResult {
    /** startActivity threw — the intent never reached an emulator. */
    data class Rejected(val message: String) : LaunchDispatchResult
    /** startActivity succeeded; the launch is now pending foreground verification. */
    data object Accepted : LaunchDispatchResult
}

/**
 * The single funnel every game-path launch intent goes through (B1 — launch reliability).
 *
 * One dispatcher owns three things that used to be scattered across call sites:
 *  1. **startActivity with named failures** — every game launch lands here, so an
 *     [ActivityNotFoundException] / SecurityException can never be silently swallowed (the old
 *     XMB direct-launch path logged and vanished).
 *  2. **Outcome recording** — each settled launch writes a [LaunchOutcome] via [LaunchOutcomeRecorder]:
 *     `INTENT_FAILED` immediately when startActivity throws, and `SUCCEEDED` / `NEVER_FOREGROUNDED`
 *     when the host lifecycle classifies the pending hand-off.
 *  3. **Post-launch verification** — PFP is a HOME launcher, so no usage-stats permission is needed:
 *     a successful game dispatch backgrounds it. The verdict is lifecycle-driven, not timer-driven:
 *     [onHostStopped] proves the emulator actually covered the launcher, which records the session
 *     as success no matter how short it was (closing the emulator right after it opens is the
 *     user's choice, not a failure). If [onHostStopped] never arrives inside the stop window, the
 *     launch is treated as never-foregrounded.
 *
 * Failures emit a [LaunchRecoveryRequest] (via [recoveryRequests]) so the shell can offer force-stop,
 * a different emulator/core, and a copyable diagnostic instead of a dead end.
 */
@Singleton
class LaunchDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outcomeRecorder: LaunchOutcomeRecorder,
    @LaunchDispatcherScope private val scope: CoroutineScope,
    @LaunchRealtimeClock private val clock: LaunchClock,
    @LaunchWallClock private val wallClock: LaunchClock,
    private val gameBootGate: GameBootGate,
    // A refused launch is the ERROR event's flagship home — the custom-vs-default decision
    // lives in MenuSoundPlayer; the dispatcher only says "this launch did not happen".
    private val menuSound: com.psplauncher.core.ui.sound.MenuSoundPlayer,
    private val autoCoreMemory: AutoCoreMemory,
    private val gameRepository: com.psplauncher.core.domain.repository.GameRepository,
) {
    private val _recoveryRequests = MutableStateFlow<LaunchRecoveryRequest?>(null)
    /** Non-null while a recovery sheet should be shown; cleared by [dismissRecovery]. */
    val recoveryRequests: StateFlow<LaunchRecoveryRequest?> = _recoveryRequests.asStateFlow()

    // ── Pending hand-off state (guarded by the scope's single-thread confinement) ───────────
    private var pending: PendingLaunch? = null
    private var hostStopped = false
    private var watchdog: Job? = null

    /**
     * Hands [intent] to the system for [game] and records what happens. [resolved] is the B4 ladder
     * result when this was an emulator launch (null for package/shortcut/native launches).
     *
     * Failure messages mirror the historic Game Detail copy so screens can keep their wording.
     */
    suspend fun launch(game: Game, resolved: ResolvedLaunch?, intent: Intent): LaunchDispatchResult {
        // The ONE GameBoot seam. Here, and not at the confirm moment, because everything above
        // this line is preflight: a game that cannot launch must never show a presentation. Both
        // game-launch call sites (Game Detail and the XMB's direct launch) get it for free.
        // No-op when GameBoot is disabled, and bounded by its own watchdog — see GameBootGate.
        gameBootGate.awaitPresentation(game.displayTitle, game.discFaceUri)
        return try {
            // Every dispatcher launch comes from an app-graph context (ViewModel/Activity via the
            // shared singleton), so NEW_TASK is required to start outside our own task. Idempotent.
            // No window transition: the launch disc is holding the screen and Android would
            // otherwise slide it away with PFP's window. See LaunchTransition.
            context.startActivity(
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).withoutTransition(),
                LaunchTransition.options(context),
            )
            // A RetroArch core launch pins the console to that core (see AutoCoreMemory), so the
            // console's automatic pick — and its RetroArch configs — stay stable across detection
            // passes. Written only once the intent actually reached the emulator, so a launch that
            // never happened can never pin a broken core. Every game-launch call site (Game Detail
            // and the XMB's direct launch) funnels through here, so one write covers both.
            resolved?.profile?.takeIf { it.isRetroArchProfile() }?.let { profile ->
                autoCoreMemory.remember(game.platformId, profile.id)
            }
            val dispatchedAt = clock.now()
            val dispatchedAtWall = wallClock.now()
            // The Last Played stamp, written NOW rather than on the way back.
            //
            // It used to ride along with the play session, which is only recorded if the launcher
            // is still in memory when the user returns — `pending` and `hostStopped` are plain
            // fields on this singleton. A big game is exactly what evicts the launcher: Skyrim
            // through GameNative takes the device, Android reclaims PFP, and the return is a cold
            // start with no pending launch to classify. The session is genuinely lost at that
            // point, and so, until now, was the one fact the shelf needs.
            //
            // Same rule as the AutoCoreMemory write above: after startActivity, so a launch that
            // never happened cannot stamp. A launch that opened and then failed still counts —
            // the shelf asks what you last opened, not what went well.
            //
            // recordPlaySession stamps the same instant again on a clean return (it passes
            // session.launchedAt, which is this value), so the two cannot disagree.
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

    /**
     * A launch was refused before any intent existed (preflight) — recorded so the game's history
     * line stays honest. [offerRecovery] raises the recovery sheet; screens that already render an
     * inline error for the same failure (Game Detail) pass false to avoid double-surfacing it,
     * while the XMB's silent direct-launch path (no inline UI at all) always wants the sheet.
     */
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

    /** Manual recovery request from a screen (e.g. Game Detail's help affordance). */
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

    /** MainActivity reports PFP left the foreground (an activity covered the launcher). */
    fun onHostStopped() {
        if (pending == null) return
        hostStopped = true
        watchdog?.cancel()
        watchdog = null
    }

    /** MainActivity reports PFP is foreground again — classify the pending hand-off. */
    fun onHostResumed() {
        val p = pending ?: return
        pending = null
        val emulatorTookForeground = hostStopped
        hostStopped = false
        watchdog?.cancel()
        watchdog = null

        if (emulatorTookForeground) {
            // The emulator demonstrably covered the launcher, so the user was inside it. Coming back
            // — even instantly — is a choice, not a failure: closing the emulator right after it
            // opens is a legitimate session and must never pop recovery UI. Duration is irrelevant.
            val playedMs = clock.now() - p.dispatchedAtMs
            Timber.i("Launch session ${playedMs}ms — emulator covered the launcher — recording success")
            scope.launch {
                outcomeRecorder.record(
                    outcomeFor(
                        p.game, p.resolved, LaunchOutcomeStatus.SUCCEEDED, reason = null,
                    ).copy(returnedAtMs = wallClock.now())
                )
                // The play session, recorded HERE and nowhere else.
                //
                // This branch is the only place the app knows a game was actually played: the
                // emulator demonstrably covered the launcher, and the user has come back. Stamping
                // at launch instead would count a launch that never foregrounded as a session, and
                // recording in the watchdog would count one that may still be running.
                //
                // The duration was already being computed for the log line above and thrown away.
                // Until now nothing called recordPlaySession at all, so `last_played_at` and
                // `total_play_time_millis` were never written: the Recently Played sort ordered
                // every game by zero, and Game Detail's "Last played" and "Play time" rows are
                // null-guarded and so never rendered.
                runCatching {
                    gameRepository.recordPlaySession(
                        com.psplauncher.core.domain.model.PlaySession(
                            gameId         = p.game.id,
                            platformId     = p.game.platformId,
                            // The wall-clock instant, not the monotonic one. This becomes
                            // games.last_played_at, which the Last Played shelf sorts on against
                            // rows stamped with currentTimeMillis by music, books and video.
                            launchedAt     = p.dispatchedAtWallMs,
                            durationMillis = playedMs,
                        )
                    )
                }.onFailure { Timber.w(it, "Could not record play session for gameId=${p.game.id}") }
            }
        } else {
            // The launcher was never covered, so the emulator never demonstrably took the foreground
            // even though startActivity succeeded (the user is back before the stop window without
            // having seen the emulator). Same verdict the watchdog would reach — never-foregrounded.
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

    // ── Internals ─────────────────────────────────────────────────────────────────────────

    private fun acceptPending(p: PendingLaunch) {
        pending = p
        hostStopped = false
        // If the emulator never covers the launcher inside the window, startActivity "succeeded"
        // but nothing came to front. Treat that as a failure rather than waiting forever.
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

    // All pending-state reads/writes above run on the caller thread (main in production) or on the
    // injected scope's dispatcher. The scope is provided as Main.immediate (see
    // LaunchDispatcherModule) so acceptPending (ViewModel launch site), onHostStopped/onHostResumed
    // (MainActivity lifecycle) and the watchdog (scope) are confined to one thread by construction.

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
        /** How long a successfully dispatched launch may take to cover the launcher. */
        const val STOP_WINDOW_MS = 6_000L
        private const val RECENT_LIMIT = 5
    }
}
