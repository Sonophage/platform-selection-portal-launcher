package com.psplauncher.core.data.book

import android.util.Xml
import com.psplauncher.core.archive.BoundedZipReader
import com.psplauncher.core.archive.ZipLimits
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream

data class EpubMetadata(
    val title: String? = null,
    val author: String? = null,
    val series: String? = null,
    val seriesIndex: Double? = null,
    val coverEntry: String? = null,
)

object EpubMetadataReader {
    val EPUB_ZIP_LIMITS = ZipLimits(
        maxEntries = 8192,
        maxEntryBytes = 128L * 1024 * 1024,
        maxTotalBytes = 2L * 1024 * 1024 * 1024,
    )

    private const val CONTAINER_ENTRY = "META-INF/container.xml"

    fun read(open: () -> InputStream): EpubMetadata? = try {
        val container = readEntry(open, CONTAINER_ENTRY)
        if (container == null) {
            Timber.d("No $CONTAINER_ENTRY: not an EPUB")
            null
        } else {
            val opfPath = parseContainer(container)
            if (opfPath == null) {
                Timber.w("$CONTAINER_ENTRY names no rootfile")
                null
            } else {
                val opf = readEntry(open, opfPath)
                if (opf == null) {
                    Timber.w("Package document \"$opfPath\" is named but absent")
                    null
                } else {
                    parsePackageDocument(opf, opfPath)
                }
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "EPUB metadata read failed")
        null
    }

    fun readEntry(open: () -> InputStream, name: String): ByteArray? {
        var bytes: ByteArray? = null
        open().use { input ->
            BoundedZipReader.read(input, EPUB_ZIP_LIMITS) { entry ->
                if (!entry.isDirectory && entry.name == name) {
                    bytes = entry.readBytes()
                    entry.stop()
                }
            }
        }
        return bytes
    }

    internal fun parseContainer(xml: ByteArray): String? {
        forEachStartTag(xml) { parser, local ->
            if (local == "rootfile") {
                parser.attr("full-path")?.let { return it }
            }
        }
        return null
    }

    internal fun parsePackageDocument(xml: ByteArray, opfPath: String): EpubMetadata? {
        var title: String? = null
        var author: String? = null
        val metas = mutableListOf<Meta>()
        val items = mutableListOf<Item>()

        var inMetadata = false

        val parsed = runCatching {
            val parser = newParser(xml)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (val local = parser.localName()) {
                        "metadata" -> inMetadata = true
                        "title" -> if (inMetadata && title == null) title = parser.textOrNull()
                        "creator" -> if (inMetadata && author == null) author = parser.textOrNull()
                        "meta" -> metas += Meta(
                            name = parser.attr("name"),
                            content = parser.attr("content"),
                            property = parser.attr("property"),
                            id = parser.attr("id"),
                            refines = parser.attr("refines"),

                            text = parser.textOrNull(),
                        )
                        "item" -> items += Item(
                            id = parser.attr("id"),
                            href = parser.attr("href"),
                            properties = parser.attr("properties"),
                        )

                        "manifest" -> inMetadata = false
                    }
                } else if (event == XmlPullParser.END_TAG && parser.localName() == "metadata") {
                    inMetadata = false
                }
                event = parser.next()
            }
            true
        }.getOrElse { Timber.w(it, "Package document parse failed"); false }
        if (!parsed) return null

        val calibreSeries = metas.content("calibre:series")
        val calibreIndex = metas.content("calibre:series_index")?.toDoubleOrNull()
        val (epub3Series, epub3Index) = epub3Collection(metas)

        return EpubMetadata(
            title = title,
            author = author,
            series = calibreSeries ?: epub3Series,
            seriesIndex = if (calibreSeries != null) calibreIndex else epub3Index,
            coverEntry = coverEntry(metas, items, opfPath),
        )
    }

    private data class Meta(
        val name: String?,
        val content: String?,
        val property: String?,
        val id: String?,
        val refines: String?,
        val text: String?,
    )

    private data class Item(val id: String?, val href: String?, val properties: String?)

    private fun epub3Collection(metas: List<Meta>): Pair<String?, Double?> {
        for (meta in metas) {
            if (meta.property != "belongs-to-collection") continue
            val refining = metas.filter { it.refines == "#${meta.id}" }
            val type = refining.firstOrNull { it.property == "collection-type" }?.text

            if (type != null && type != "series") continue
            val name = meta.text ?: continue
            val position = refining
                .firstOrNull { it.property == "group-position" }
                ?.text
                ?.toDoubleOrNull()
            return name to position
        }
        return null to null
    }

    private fun coverEntry(metas: List<Meta>, items: List<Item>, opfPath: String): String? {
        val byMetaId = metas.content("cover")?.let { id -> items.firstOrNull { it.id == id } }

        val byProperties = items.firstOrNull { item ->
            item.properties.orEmpty().split(' ').any { it == "cover-image" }
        }
        val href = (byMetaId ?: byProperties)?.href?.takeIf { it.isNotBlank() } ?: return null
        return resolveAgainst(opfPath, href)
    }

    internal fun resolveAgainst(opfPath: String, href: String): String? {
        val decoded = percentDecode(href)
        if (decoded.isBlank()) return null

        val base = opfPath.substringBeforeLast('/', "")
        val segments = ArrayDeque<String>()
        if (base.isNotEmpty()) base.split('/').forEach { segments.addLast(it) }
        for (part in decoded.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeLast()
                else -> segments.addLast(part)
            }
        }
        return segments.joinToString("/").takeIf { it.isNotBlank() }
    }

    private fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val out = java.io.ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            val hex = if (c == '%' && i + 2 < value.length) {
                value.substring(i + 1, i + 3).toIntOrNull(16)
            } else {
                null
            }
            if (hex != null) {
                out.write(hex)
                i += 3
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }

        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun newParser(xml: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(ByteArrayInputStream(xml), null)
    }

    private inline fun forEachStartTag(xml: ByteArray, body: (XmlPullParser, String) -> Unit) {
        runCatching {
            val parser = newParser(xml)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) body(parser, parser.localName())
                event = parser.next()
            }
        }.onFailure { Timber.w(it, "XML parse failed") }
    }

    private fun XmlPullParser.localName(): String = name?.substringAfterLast(':').orEmpty()

    private fun XmlPullParser.attr(name: String): String? =
        getAttributeValue(null, name)?.trim()?.takeIf { it.isNotEmpty() }

    private fun XmlPullParser.textOrNull(): String? =
        runCatching { nextText() }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private fun List<Meta>.content(name: String): String? =
        firstOrNull { it.name == name }?.content
}
