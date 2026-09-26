package com.psplauncher.feature.settings.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.withContext

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
