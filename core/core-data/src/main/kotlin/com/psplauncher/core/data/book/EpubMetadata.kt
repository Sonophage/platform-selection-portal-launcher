package com.psplauncher.core.data.book

import android.util.Xml
import com.psplauncher.core.archive.BoundedZipReader
import com.psplauncher.core.archive.ZipLimits
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * What one EPUB's package document says about itself. Every field is nullable: an EPUB is only
 * obliged to carry a title, and plenty in the wild carry less than that.
 *
 * [coverEntry] is a ZIP entry name already resolved against the package document's folder, so it
 * can be handed straight back to [EpubMetadataReader.readEntry].
 */
data class EpubMetadata(
    val title: String? = null,
    val author: String? = null,
    val series: String? = null,
    val seriesIndex: Double? = null,
    val coverEntry: String? = null,
)

/**
 * Reads title, author, series and cover art out of an EPUB.
 *
 * An EPUB is a ZIP, so this goes through [BoundedZipReader] rather than opening a second
 * `ZipInputStream` of its own: the bomb and entry-count policy is not this file's to choose. Each
 * entry it wants costs one pass over the archive, stopped the moment the entry is found. Three
 * passes is the worst case (container, package document, cover) and it only happens for a file the
 * scanner has decided is new or changed, which is why [BookScanner] does this once rather than on
 * every rescan.
 *
 * Parsing uses [Xml.newPullParser], the same parser `EsDeGamelistParser` uses, and it is the second
 * attempt. The first used `DocumentBuilderFactory` because DOM runs in a plain JVM unit test where
 * `android.util.Xml` needs Robolectric. That passed every test on the host and returned null for
 * every book on the device: Android's `DocumentBuilderFactory` throws
 * `ParserConfigurationException` for any feature except `FEATURE_SECURE_PROCESSING`, so the
 * `disallow-doctype-decl` call that made the host's XXE test pass was the thing breaking the parse
 * on the only platform that ships. Test ergonomics are not worth a parser that behaves differently
 * where it runs.
 *
 * The pull parser also removes the whole question: it resolves no external entities, so there is no
 * feature to negotiate and nothing to get wrong per platform.
 */
object EpubMetadataReader {

    /**
     * Caps for one EPUB read.
     *
     * Looser than [ZipLimits]'s defaults, which were sized for theme bundles and refuse a normal
     * book at 512 entries. The reader streams past entries it does not want straight into a null
     * sink, so the total cap is not protecting memory here; it is refusing a file that could not be
     * a book. The per-entry cap is the one that matters, because container.xml, the package
     * document and the cover are read into a ByteArray.
     */
    val EPUB_ZIP_LIMITS = ZipLimits(
        maxEntries = 8192,
        maxEntryBytes = 128L * 1024 * 1024,
        maxTotalBytes = 2L * 1024 * 1024 * 1024,
    )

    private const val CONTAINER_ENTRY = "META-INF/container.xml"

    /**
     * Reads [open]'s EPUB metadata, or null when the file is not a readable EPUB. [open] must
     * return a fresh stream each time it is called, because finding an entry is a forward pass.
     *
     * Never throws: a truncated, encrypted or malformed book is a book without metadata, not a
     * failed scan. It does log, which the first version did not, and that silence is exactly why a
     * parser that failed on every single book still reported a clean scan of 80 of them.
     */
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

    /**
     * Returns the bytes of one entry, or null when the archive does not hold it. Stops the read at
     * the match, so an entry near the front costs nothing like the whole file.
     */
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

    /**
     * The package document's path, from `META-INF/container.xml`.
     *
     * The path is NOT assumed to be `OEBPS/content.opf`. That is what most tools emit and what a
     * hardcoded guess would work against for a long time before meeting a book from a tool that
     * does something else, which is exactly the failure worth avoiding: the scan would report zero
     * metadata and look like a parser bug rather than a wrong constant.
     */
    internal fun parseContainer(xml: ByteArray): String? {
        forEachStartTag(xml) { parser, local ->
            if (local == "rootfile") {
                parser.attr("full-path")?.let { return it }
            }
        }
        return null
    }

    /** Title, author, series and cover, out of the package document at [opfPath]. */
    internal fun parsePackageDocument(xml: ByteArray, opfPath: String): EpubMetadata? {
        var title: String? = null
        var author: String? = null
        val metas = mutableListOf<Meta>()
        val items = mutableListOf<Item>()
        // `title` and `creator` are read only inside <metadata>. A <guide> reference and a spine
        // item can both carry a title, and neither is the book's.
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
                            // Read last: it advances the parser past this element.
                            text = parser.textOrNull(),
                        )
                        "item" -> items += Item(
                            id = parser.attr("id"),
                            href = parser.attr("href"),
                            properties = parser.attr("properties"),
                        )
                        // <manifest> closing <metadata> implicitly guards a document whose
                        // metadata element is never explicitly closed.
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

        // Series has two conventions and no winner. Calibre's pair of `name`/`content` metas is
        // EPUB 2 shaped and by far the most common in the wild; EPUB 3 says a collection element
        // refined by its type and position. Read both, prefer Calibre's when a file carries both,
        // because a file that carries both was written by Calibre.
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

    /**
     * EPUB 3 series: `belongs-to-collection` carries the name, and two `refines` metas pointing at
     * its id carry the type and the position. Only a collection that says it is a series counts;
     * `belongs-to-collection` is also how a book declares it is part of a boxed set.
     */
    private fun epub3Collection(metas: List<Meta>): Pair<String?, Double?> {
        for (meta in metas) {
            if (meta.property != "belongs-to-collection") continue
            val refining = metas.filter { it.refines == "#${meta.id}" }
            val type = refining.firstOrNull { it.property == "collection-type" }?.text
            // A collection with no declared type is a series by convention; only an explicitly
            // different type (a "set") is skipped.
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

    /**
     * The cover image's ZIP entry, by either of the two ways an EPUB names one: an EPUB 2 `meta`
     * pointing at a manifest item id, or an EPUB 3 manifest item declaring `cover-image` in its
     * properties.
     */
    private fun coverEntry(metas: List<Meta>, items: List<Item>, opfPath: String): String? {
        val byMetaId = metas.content("cover")?.let { id -> items.firstOrNull { it.id == id } }
        // `properties` is a space-separated token list, so a substring match would also accept
        // something like "not-cover-image".
        val byProperties = items.firstOrNull { item ->
            item.properties.orEmpty().split(' ').any { it == "cover-image" }
        }
        val href = (byMetaId ?: byProperties)?.href?.takeIf { it.isNotBlank() } ?: return null
        return resolveAgainst(opfPath, href)
    }

    /**
     * A manifest href is relative to the package document, not to the archive root, and is
     * percent-encoded. Returns a ZIP entry name.
     */
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

    /**
     * Percent-decoding only. `URLDecoder` is not usable here: it also turns `+` into a space, which
     * is correct for a query string and wrong for a file called `C++ Primer.jpg`.
     */
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
        return out.toString(Charsets.UTF_8)
    }

    // ── Pull-parser helpers ───────────────────────────────────────────────────

    private fun newParser(xml: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        // Namespace processing stays OFF and prefixes are stripped by hand instead. `opf:item` and
        // `item` are the same element and which one a file uses is the tool's choice, so matching
        // on the local name accepts both without the parser needing every namespace declared.
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

    /**
     * The text of the element the parser is sitting on, or null when it is empty.
     *
     * `nextText` is safe on a self-closing element: it returns "" and leaves the parser on the
     * END_TAG, which is where the caller's loop expects to continue from.
     */
    private fun XmlPullParser.textOrNull(): String? =
        runCatching { nextText() }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private fun List<Meta>.content(name: String): String? =
        firstOrNull { it.name == name }?.content
}
