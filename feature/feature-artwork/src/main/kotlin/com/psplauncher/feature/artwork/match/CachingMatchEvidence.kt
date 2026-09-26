package com.psplauncher.feature.artwork.match

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class CachingMatchEvidence(
    private val delegate: MatchEvidenceSource,
    private val store: TitleSearchStore,
    private val now: () -> Long = System::currentTimeMillis,
) : MatchEvidenceSource by delegate {
    private val answers = ConcurrentHashMap<SearchKey, List<GameCandidate>>()
    private val inFlight = ConcurrentHashMap<SearchKey, CompletableDeferred<List<GameCandidate>>>()

    override suspend fun searchByTitle(
        provider: MatchProvider,
        query: String,
        platformId: String,
    ): List<GameCandidate> =
        remember(provider, query, scope = platformId) { delegate.searchByTitle(provider, query, platformId) }

    suspend fun remember(
        provider: MatchProvider,
        query: String,
        scope: String,
        search: suspend () -> List<GameCandidate>,
    ): List<GameCandidate> {
        val key = SearchKey.of(provider, query, scope)
        while (true) {
            answers[key]?.let { return it }
            val mine = CompletableDeferred<List<GameCandidate>>()
            val running = inFlight.putIfAbsent(key, mine)
            if (running == null) {
                answers[key]?.let { answer ->
                    mine.complete(answer)
                    inFlight.remove(key, mine)
                    return answer
                }
                return ask(key, mine, search)
            }
            try {
                return running.await()
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                inFlight.remove(key, running)
            }
        }
    }

    fun clear() = answers.clear()

    private suspend fun ask(
        key: SearchKey,
        mine: CompletableDeferred<List<GameCandidate>>,
        search: suspend () -> List<GameCandidate>,
    ): List<GameCandidate> {
        val (found, fromProvider) = try {
            val kept = store.read(key.provider, key.query, key.scope)?.takeIf { it.expiresAtMillis > now() }
            val answer = kept?.candidates ?: search()

            currentCoroutineContext().ensureActive()
            answers[key] = answer
            mine.complete(answer)
            answer to (kept == null)
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            inFlight.remove(key, mine)
        }
        if (fromProvider) keep(key, found)
        return found
    }

    private suspend fun keep(key: SearchKey, found: List<GameCandidate>) {
        val ttl = when {
            found.isNotEmpty() -> HITS_TTL_MS
            key.provider == MatchProvider.SCREENSCRAPER -> SCREENSCRAPER_EMPTY_TTL_MS
            else -> return
        }
        store.write(key.provider, key.query, key.scope, StoredTitleSearch(found, now() + ttl))
    }

    private data class SearchKey(val provider: MatchProvider, val query: String, val scope: String) {
        companion object {
            private val WHITESPACE = Regex("\\s+")

            fun of(provider: MatchProvider, query: String, scope: String) = SearchKey(
                provider = provider,
                query = query.trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT),
                scope = scope,
            )
        }
    }

    companion object {
        const val HITS_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val SCREENSCRAPER_EMPTY_TTL_MS = 24L * 60 * 60 * 1000
    }
}
