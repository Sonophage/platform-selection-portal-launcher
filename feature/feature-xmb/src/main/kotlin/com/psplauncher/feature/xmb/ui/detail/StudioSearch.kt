package com.psplauncher.feature.xmb.ui.detail

import com.psplauncher.feature.artwork.match.TitleKey
import com.psplauncher.feature.artwork.store.ArtworkKind

object StudioQuery {
    fun normalize(raw: String): String = TitleKey.of(raw)

    fun sameQuery(a: String, b: String): Boolean = TitleKey.same(a, b)
}

data class StudioRequestKey(
    val normalizedQuery: String,
    val source: StudioSource,
    val kind: ArtworkKind,
    val includeNsfw: Boolean = false,
    val matchId: String? = null,
) {
    companion object {
        fun of(
            query: String,
            source: StudioSource,
            kind: ArtworkKind,
            includeNsfw: Boolean,
            matchId: String? = null,
        ) = StudioRequestKey(
            normalizedQuery = StudioQuery.normalize(query),
            source = source,
            kind = kind,

            includeNsfw = includeNsfw && source == StudioSource.STEAMGRIDDB,
            matchId = matchId,
        )
    }
}

data class StudioArtKey(val kind: ArtworkKind, val provider: String, val asset: String) {
    companion object {
        fun of(kind: ArtworkKind, art: StudioArt) = StudioArtKey(kind, art.provider, art.providerAssetId ?: art.url)
    }
}

object ScreenScraperAssetId {
    fun of(url: String?): String? {
        val query = url?.substringAfter('?', missingDelimiterValue = "")?.takeIf { it.isNotEmpty() } ?: return null
        val params = query.split('&').mapNotNull { pair ->
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', missingDelimiterValue = "")
            if (name.isEmpty() || value.isEmpty()) return@mapNotNull null
            runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrNull()?.let { name.lowercase() to it }
        }.toMap()
        val gameId = params["jeuid"] ?: return null
        val media = params["media"] ?: return null
        return "$gameId:$media"
    }
}

internal fun screenScraperTiles(
    kind: ArtworkKind,
    types: List<String>,
    medias: List<com.psplauncher.feature.artwork.api.SsCachedMedia>,
): List<StudioArt> {
    val served = types.flatMap { type ->
        medias.mapNotNull { media -> media.url?.takeIf { media.type == type }?.let { url -> media to url } }
    }
    return served
        .groupBy { (_, url) -> ScreenScraperAssetId.of(url) ?: url }
        .values
        .map { copies ->
            val (media, url) = copies.first()
            val regions = copies.mapNotNull { (copy, _) -> copy.region?.uppercase() }.distinct()
            StudioArt(
                url = url,
                thumb = null,
                provider = "ScreenScraper",
                label = listOfNotNull(media.type, regions.joinToString("/").ifEmpty { null }).joinToString(" · "),
                isVideo = kind == ArtworkKind.VIDEO || kind == ArtworkKind.ICON1,
                providerAssetId = ScreenScraperAssetId.of(url),
            )
        }
}

data class StudioLibraryAssets(
    val kind: ArtworkKind? = null,
    val slots: List<com.psplauncher.feature.artwork.store.StudioArtworkSlot> = emptyList(),
) {
    fun holds(kind: ArtworkKind, art: StudioArt): Boolean = kind == this.kind && slots.any { it.holds(art) }

    fun sortOrdersHolding(art: StudioArt): List<Int> = slots.filter { it.holds(art) }.map { it.sortOrder }

    companion object {
        fun of(kind: ArtworkKind, slots: List<com.psplauncher.feature.artwork.store.StudioArtworkSlot>) =
            StudioLibraryAssets(kind, slots)

        private fun com.psplauncher.feature.artwork.store.StudioArtworkSlot.holds(art: StudioArt): Boolean =
            (providerAssetId != null && providerAssetId == art.providerAssetId) ||
                originUrl?.let(::originOf) == originOf(art.url)

        private fun originOf(url: String): String = ScreenScraperAssetId.of(url) ?: url
    }
}

class StudioResultCache(private val maxEntries: Int = MAX_ENTRIES) {
    private val entries = LinkedHashMap<StudioRequestKey, List<StudioArt>>(16, 0.75f, true)

    operator fun get(key: StudioRequestKey): List<StudioArt>? = synchronized(entries) { entries[key] }

    operator fun set(key: StudioRequestKey, results: List<StudioArt>) = synchronized(entries) {
        entries[key] = results
        while (entries.size > maxEntries) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }

    fun contains(key: StudioRequestKey): Boolean = synchronized(entries) { entries.containsKey(key) }

    fun evictSource(source: StudioSource) = synchronized(entries) {
        entries.keys.filter { it.source == source }.forEach { entries.remove(it) }
    }

    fun clear() = synchronized(entries) { entries.clear() }

    val size: Int get() = synchronized(entries) { entries.size }

    private companion object {
        const val MAX_ENTRIES = 24
    }
}

data class StudioPage(
    val items: List<StudioArt>,
    val pageIndex: Int,
    val pageCount: Int,

    val rangeStart: Int,
    val rangeEnd: Int,
    val totalResults: Int,
) {
    val hasPrevious: Boolean get() = pageIndex > 0
    val hasNext: Boolean get() = pageIndex < pageCount - 1

    companion object {
        fun of(all: List<StudioArt>, pageIndex: Int, pageSize: Int): StudioPage {
            require(pageSize > 0) { "pageSize must be positive" }
            val pageCount = if (all.isEmpty()) 0 else (all.size + pageSize - 1) / pageSize
            val clamped = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            val from = clamped * pageSize
            val items = if (all.isEmpty()) emptyList() else all.drop(from).take(pageSize)
            return StudioPage(
                items = items,
                pageIndex = clamped,
                pageCount = pageCount,
                rangeStart = if (items.isEmpty()) 0 else from + 1,
                rangeEnd = if (items.isEmpty()) 0 else from + items.size,
                totalResults = all.size,
            )
        }
    }
}
