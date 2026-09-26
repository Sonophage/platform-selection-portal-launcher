package com.psplauncher.feature.artwork.importer

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.InputStream

object EsDeGamelistParser {
    data class Entry(
        val romFileName: String,
        val name: String?,
        val description: String?,
        val developer: String?,
        val publisher: String?,
        val releaseYear: Int?,
        val genre: String?,
    )

    private const val MAX_ENTRIES = 50_000
    private const val MAX_FIELD_CHARS = 8_000
    private val YEAR = Regex("""^(\d{4})""")

    fun parse(input: InputStream): List<Entry> {
        val out = mutableListOf<Entry>()
        runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && out.size < MAX_ENTRIES) {
                if (event == XmlPullParser.START_TAG && parser.name.equals("game", ignoreCase = true)) {
                    readGame(parser)?.let { out += it }
                }
                event = parser.next()
            }
        }.onFailure { Timber.w(it, "gamelist.xml parse stopped after ${out.size} entries") }
        return out
    }

    fun yearOf(releaseDate: String?): Int? =
        releaseDate?.trim()?.let { YEAR.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?.takeIf { it in 1950..2100 }

    private fun readGame(parser: XmlPullParser): Entry? {
        var path: String? = null
        var name: String? = null
        var desc: String? = null
        var developer: String? = null
        var publisher: String? = null
        var releaseDate: String? = null
        var genre: String? = null

        val startDepth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.depth == startDepth) break
            if (event != XmlPullParser.START_TAG) continue
            when (parser.name.lowercase()) {
                "path"        -> path = text(parser)
                "name"        -> name = text(parser)
                "desc"        -> desc = text(parser)
                "developer"   -> developer = text(parser)
                "publisher"   -> publisher = text(parser)
                "releasedate" -> releaseDate = text(parser)
                "genre"       -> genre = text(parser)
            }
        }

        val fileName = path?.replace('\\', '/')?.substringAfterLast('/')?.trim()
        if (fileName.isNullOrBlank()) return null
        return Entry(
            romFileName = fileName,
            name = name,
            description = desc,
            developer = developer,
            publisher = publisher,
            releaseYear = yearOf(releaseDate),
            genre = genre,
        )
    }

    private fun text(parser: XmlPullParser): String? = runCatching {
        val value = parser.nextText()
        value.trim().take(MAX_FIELD_CHARS).ifBlank { null }
    }.getOrNull()
}
