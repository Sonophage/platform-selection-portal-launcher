package com.psplauncher.feature.settings.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.withContext

/**
 * Waits for [condition], driving both clocks, because these tests have two.
 *
 * The ViewModel runs on a [kotlinx.coroutines.test.StandardTestDispatcher] and its state is only
 * built when virtual time is advanced — but the thing it is waiting FOR is a real DataStore write
 * to a real file on a real thread, which no amount of `advanceUntilIdle` will hurry. So the loop
 * alternates: advance the virtual clock, then give the real one a moment.
 *
 * ONE copy. There were four, byte for byte, one per DisplaySettingsViewModel test — and the number
 * below is exactly the kind of thing that gets raised in one of four files and leaves the other
 * three still flaky.
 *
 * THE BUDGET IS REAL TIME AND IS NOT A GUESS ABOUT SPEED. It was 10 seconds, which is ample for a
 * DataStore write on an idle machine and a coin flip when `./gradlew test` is running thirteen
 * modules' JVMs at once: one of these failed exactly once in a full parallel sweep on 2026-09-24
 * and passed three times out of three alone, and at HEAD. Nothing waits longer on success — the
 * loop leaves the moment the condition holds — so a bigger number costs a passing run nothing and
 * only changes how long a genuine hang takes to report.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestScope.eventually(reason: String, condition: suspend () -> Boolean) {
    val deadline = System.currentTimeMillis() + EVENTUALLY_TIMEOUT_MS
    while (!condition()) {
        if (System.currentTimeMillis() > deadline) {
            throw AssertionError("condition not met within ${EVENTUALLY_TIMEOUT_MS / 1000}s: $reason")
        }
        advanceUntilIdle()
        withContext(Dispatchers.IO) { Thread.sleep(25) }
    }
    advanceUntilIdle()
}

private const val EVENTUALLY_TIMEOUT_MS = 60_000L
