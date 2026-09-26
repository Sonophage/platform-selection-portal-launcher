package com.psplauncher.feature.library.scanner

import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameRegion
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscSetBuilder @Inject constructor() {
    fun interface M3uReader {
        fun read(game: Game): List<String>?
    }

    fun interface RegionReader {
        fun read(game: Game): GameRegion?
    }

    private data class Candidate(
        val game: Game,
        val stem: String,
        val folder: String,
        val ext: String,
        val basename: String,
    )

    private data class Assignment(
        val key: String,
        val discNumber: Int?,
        val viaM3u: Boolean,
    )

    fun assign(
        games: List<Game>,
        regionReader: RegionReader = RegionReader { null },
        m3uReader: M3uReader,
    ): List<Game> = derive(games, m3uReader, regionReader)

    fun reconcile(
        games: List<Game>,
        regionReader: RegionReader = RegionReader { null },
        m3uReader: M3uReader,
    ): List<Game> {
        val derived = derive(games, m3uReader, regionReader)
        return games.zip(derived)
            .filter { (before, after) ->
                before.discSetKey != after.discSetKey ||
                    before.discNumber != after.discNumber ||
                    before.isDiscPrimary != after.isDiscPrimary ||
                    before.region != after.region
            }
            .map { it.second }
    }

    private fun derive(games: List<Game>, m3uReader: M3uReader, regionReader: RegionReader): List<Game> {
        if (games.isEmpty()) return games

        val regionByPath = HashMap<String, GameRegion?>()
        fun regionOf(game: Game): GameRegion? = game.romPath?.let { path ->
            if (regionByPath.containsKey(path)) regionByPath[path]
            else (regionReader.read(game) ?: game.region).also { regionByPath[path] = it }
        }

        val candidates = games.mapNotNull { game -> game.candidate() }
        if (candidates.isEmpty()) return games

        val byPath = candidates.associateBy { it.game.romPath!! }
        val byBasename = candidates.groupBy { it.basename }
        val byFolderBasename = candidates.groupBy { it.folder to it.basename }

        val assignments = HashMap<String, Assignment>()

        val tagged = ArrayList<Triple<Candidate, DiscTag, String>>()
        for (c in candidates) {
            val tag = parseDiscTag(c.stem) ?: continue
            tagged.add(Triple(c, tag, setKey(c, keyTitleFor(c, tag))))
        }
        val regionSplitKey = HashMap<String, String>()
        tagged.groupBy { it.third }.forEach { (baseKey, members) ->
            val regions = members.mapNotNull { regionOf(it.first.game) }
            if (regions.size == members.size && regions.distinct().size > 1) {
                for ((c, _, _) in members) {
                    regionSplitKey[c.game.romPath!!] = "$baseKey\u0001${regionOf(c.game)!!.name}"
                }
            }
        }
        for ((c, tag, baseKey) in tagged) {
            assignments[c.game.romPath!!] = Assignment(
                key = regionSplitKey[c.game.romPath!!] ?: baseKey,
                discNumber = tag.discNumber,
                viaM3u = false,
            )
        }

        for (m3u in candidates.filter { it.ext == "m3u" }) {
            val entries = m3uReader.read(m3u.game) ?: continue
            val resolved = mutableListOf<Candidate>()
            for (entry in entries) {
                val name = playlistEntryName(entry) ?: continue
                val match = (byFolderBasename[m3u.folder to name] ?: byBasename[name])
                    ?.firstOrNull { it !== m3u }
                if (match != null) resolved.add(match)
            }
            if (resolved.isEmpty()) continue

            val m3uKey = setKey(m3u, keyTitleFor(m3u, null))
            assignments[m3u.game.romPath!!] = Assignment(m3uKey, discNumber = null, viaM3u = true)
            resolved.forEachIndexed { index, disc ->
                val tag = parseDiscTag(disc.stem)
                assignments[disc.game.romPath!!] = Assignment(
                    key = m3uKey,
                    discNumber = tag?.discNumber ?: (index + 1),
                    viaM3u = true,
                )
            }
        }

        val primaryByKey = assignments.entries
            .groupBy { it.value.key }
            .mapNotNull { (key, members) ->
                val primary = members.firstOrNull { it.value.viaM3u && it.value.discNumber == null }
                    ?: members.minWithOrNull(
                        compareBy({ it.value.discNumber ?: Int.MAX_VALUE }, { it.key }),
                    )
                primary?.let { key to it.key }
            }
            .toMap()

        return games.map { game ->
            val path = game.romPath
            val assignment = if (path != null) assignments[path] else null
            if (assignment == null) {
                if (game.discSetKey != null || game.discNumber != null || game.isDiscPrimary) {
                    game.copy(region = regionOf(game), discSetKey = null, discNumber = null, isDiscPrimary = false)
                } else {
                    game.copy(region = regionOf(game))
                }
            } else {
                game.copy(
                    region = regionOf(game),
                    discSetKey = assignment.key,
                    discNumber = assignment.discNumber,
                    isDiscPrimary = primaryByKey[assignment.key] == path,
                )
            }
        }
    }

    private fun Game.candidate(): Candidate? {
        val path = romPath ?: return null
        val basename = path.substringAfterLast('/').substringAfterLast('\\')
        if (basename.isBlank() || basename == path) return null
        return Candidate(
            game = this,
            stem = basename.substringBeforeLast('.', basename),
            folder = path.substringBeforeLast('/').substringBeforeLast('\\'),
            ext = basename.substringAfterLast('.', "").lowercase(),
            basename = basename.lowercase(),
        )
    }

    private fun Candidate.discNormalizedFolder(): String {
        val slash = folder.lastIndexOf('/')
        val backslash = folder.lastIndexOf('\\')
        val sep = maxOf(slash, backslash)
        if (sep < 0) return cleanedFolderSegment(folder)
        val parent = folder.substring(0, sep)
        val segment = folder.substring(sep + 1)
        return "$parent${folder[sep]}${cleanedFolderSegment(segment)}"
    }

    private fun cleanedFolderSegment(segment: String): String {
        val discStripped = parseDiscTag(segment)?.strippedTitle ?: segment
        return cleanRomTitle(discStripped).ifBlank { discStripped }
    }

    private fun keyTitleFor(c: Candidate, tag: DiscTag?): String =
        if (tag != null) cleanRomTitle(tag.strippedTitle) else cleanRomTitle(c.stem)

    private fun setKey(c: Candidate, keyTitle: String): String =
        "${c.game.platformId}\u0001${c.discNormalizedFolder()}\u0001$keyTitle"

    private fun playlistEntryName(line: String): String? {
        var entry = line.trim()
        if (entry.isEmpty() || entry.startsWith("#")) return null
        entry = entry.removePrefix("\"").removeSuffix("\"").removePrefix("./")
        if (entry.isBlank()) return null
        val name = entry.substringAfterLast('/').substringAfterLast('\\')
        return name.lowercase().takeIf { it.isNotBlank() }
    }
}
