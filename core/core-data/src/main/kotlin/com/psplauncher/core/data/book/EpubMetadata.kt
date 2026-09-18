package com.psplauncher.core.data.book

import com.psplauncher.core.archive.BoundedZipReader
import com.psplauncher.core.archive.ZipLimits
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

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
 * Parsing is DOM rather than the [android.util.Xml] pull parser used by `EsDeGamelistParser`, for
 * two reasons: a package document is a few kilobytes where a gamelist is megabytes, and DOM runs in
 * a plain JVM unit test where `android.util.Xml` needs Robolectric. External entities are disabled;
 * the file comes off the user's disk but it is still not ours.
 */
object EpubMetadataReader {

    /**
     * Caps for one EPUB read.
     *
     * Looser than [ZipLimits]'s defaults, which were sized for theme bundles. The reader streams
     * past entries it does not want straight into a null sink, so the total cap is not protecting
     * memory here; it is refusing a file that could not be a book. The per-entry cap is the one
     * that matters, because container.xml, the package document and the cover are read into a
     * ByteArray.
     */
    val EPUB_ZIP_LIMITS = ZipLimits(
        maxEntries = 8192,
        maxEntryBytes = 128L * 1024 * 1024,
        maxTotalBytes = 2L * 1024 * 1024 * 1024,
    )

    private const val CONTAINER_ENTRY = "META-INF/container.xml"
    private const val DC_NAMESPACE = "http://purl.org/dc/elements/1.1/"

    /**
     * Reads [open]'s EPUB metadata, or null when the file is not a readable EPUB. [open] must
     * return a fresh stream each time it is called, because finding an entry is a forward pass.
     *
     * Never throws: a truncated, encrypted or malformed book is a book without metadata, not a
     * failed scan.
     */
    fun read(open: () -> InputStream): EpubMetadata? = runCatching {
        val container = readEntry(open, CONTAINER_ENTRY) ?: return@runCatching null
        val opfPath = parseContainer(container) ?: return@runCatching null
        val opf = readEntry(open, opfPath) ?: return@runCatching null
        parsePackageDocument(opf, opfPath)
    }.getOrNull()

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
    internal fun parseContainer(xml: ByteArray): String? =
        parse(xml)
            ?.elementsNamed("rootfile")
            ?.firstNotNullOfOrNull { it.getAttribute("full-path").takeIf(String::isNotBlank) }

    /** Title, author, series and cover, out of the package document at [opfPath]. */
    internal fun parsePackageDocument(xml: ByteArray, opfPath: String): EpubMetadata? {
        val root = parse(xml) ?: return null
        val metas = root.elementsNamed("meta")

        // Series has two conventions and no winner. Calibre's pair of `name`/`content` metas is
        // EPUB 2 shaped and by far the most common in the wild; EPUB 3 says a collection element
        // refined by its type and position. Read both, prefer Calibre's when a file carries both,
        // because a file that carries both was written by Calibre.
        val calibreSeries = metas.metaContent("calibre:series")
        val calibreIndex = metas.metaContent("calibre:series_index")?.toDoubleOrNull()

        val (epub3Series, epub3Index) = epub3Collection(metas)

        return EpubMetadata(
            title = root.elementsNamedNS(DC_NAMESPACE, "title").firstText(),
            author = root.elementsNamedNS(DC_NAMESPACE, "creator").firstText(),
            series = calibreSeries ?: epub3Series,
            seriesIndex = if (calibreSeries != null) calibreIndex else epub3Index,
            coverEntry = coverEntry(root, metas, opfPath),
        )
    }

    /**
     * EPUB 3 series: `belongs-to-collection` carries the name, and two `refines` metas pointing at
     * its id carry the type and the position. Only a collection that says it is a series counts;
     * `belongs-to-collection` is also how a book declares it is part of a boxed set.
     */
    private fun epub3Collection(metas: List<Element>): Pair<String?, Double?> {
        for (meta in metas) {
            if (meta.getAttribute("property") != "belongs-to-collection") continue
            val id = meta.getAttribute("id").takeIf { it.isNotBlank() }
            val refining = metas.filter { it.getAttribute("refines") == "#$id" }
            val type = refining.firstOrNull { it.getAttribute("property") == "collection-type" }?.textTrimmed()
            // A collection with no declared type is a series by convention; only an explicitly
            // different type (a "set") is skipped.
            if (type != null && type != "series") continue
            val name = meta.textTrimmed() ?: continue
            val position = refining
                .firstOrNull { it.getAttribute("property") == "group-position" }
                ?.textTrimmed()
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
    private fun coverEntry(root: Element, metas: List<Element>, opfPath: String): String? {
        val items = root.elementsNamed("item")

        val byMetaId = metas.metaContent("cover")
            ?.let { id -> items.firstOrNull { it.getAttribute("id") == id } }
        // `properties` is a space-separated token list, so a substring match would also accept
        // something like "not-cover-image".
        val byProperties = items.firstOrNull { item ->
            item.getAttribute("properties").split(' ').any { it == "cover-image" }
        }

        val href = (byMetaId ?: byProperties)?.getAttribute("href")?.takeIf { it.isNotBlank() }
            ?: return null
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

    // ── DOM helpers ───────────────────────────────────────────────────────────

    private fun parse(xml: ByteArray): Element? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // The file is the user's own, but it is still input from outside the app: an EPUB that
            // declares an external entity must not make the scanner fetch it or expand it.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(xml)).documentElement
    }.getOrNull()

    /**
     * Every descendant element with this local name, prefix ignored.
     *
     * The prefix is deliberately not matched. `opf:item` and `item` are the same element, and
     * which one a file uses is the tool's choice, not the spec's.
     */
    private fun Element.elementsNamed(local: String): List<Element> =
        descendants().filter { it.localNameOrTag() == local }

    private fun Element.elementsNamedNS(namespace: String, local: String): List<Element> =
        descendants().filter { it.localNameOrTag() == local && it.namespaceURI == namespace }

    private fun Element.descendants(): List<Element> {
        val out = mutableListOf<Element>()
        val stack = ArrayDeque<Element>().apply { addLast(this@descendants) }
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            out.add(current)
            val children = current.childNodes
            for (i in 0 until children.length) {
                (children.item(i) as? Element)?.let { stack.addLast(it) }
            }
        }
        return out
    }

    private fun Element.localNameOrTag(): String = localName ?: tagName.substringAfterLast(':')

    private fun Element.textTrimmed(): String? = textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun List<Element>.firstText(): String? = firstNotNullOfOrNull { it.textTrimmed() }

    /** The `content` of the `<meta name="...">` with this name. */
    private fun List<Element>.metaContent(name: String): String? =
        firstOrNull { it.getAttribute("name") == name }
            ?.getAttribute("content")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
