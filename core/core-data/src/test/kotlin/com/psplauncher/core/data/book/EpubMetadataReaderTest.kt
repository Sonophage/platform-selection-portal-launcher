package com.psplauncher.core.data.book

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EpubMetadataReaderTest {
    private fun epub(vararg entries: Pair<String, ByteArray>): () -> InputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        val bytes = out.toByteArray()
        return { ByteArrayInputStream(bytes) }
    }

    private fun container(opfPath: String) = """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/></rootfiles>
        </container>
    """.trimIndent().toByteArray()

    private fun opf(metadata: String, manifest: String = "") = """
        <?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            $metadata
          </metadata>
          <manifest>
            $manifest
          </manifest>
        </package>
    """.trimIndent().toByteArray()

    @Test
    fun `title and author come from the dublin core elements`() {
        val book = epub(
            "mimetype" to "application/epub+zip".toByteArray(),
            "META-INF/container.xml" to container("OEBPS/content.opf"),
            "OEBPS/content.opf" to opf(
                """
                <dc:title>Dune</dc:title>
                <dc:creator>Frank Herbert</dc:creator>
                """
            ),
        )
        val meta = EpubMetadataReader.read(book)
        assertEquals("Dune", meta?.title)
        assertEquals("Frank Herbert", meta?.author)
    }

    @Test
    fun `calibre series and index are read`() {
        val book = epub(
            "META-INF/container.xml" to container("content.opf"),
            "content.opf" to opf(
                """
                <dc:title>Dune Messiah</dc:title>
                <meta name="calibre:series" content="Dune"/>
                <meta name="calibre:series_index" content="2"/>
                """
            ),
        )
        val meta = EpubMetadataReader.read(book)
        assertEquals("Dune", meta?.series)
        assertEquals(2.0, meta?.seriesIndex)
    }

    @Test
    fun `an epub 3 refined collection is read as a series`() {
        val book = epub(
            "META-INF/container.xml" to container("content.opf"),
            "content.opf" to opf(
                """
                <dc:title>The Fall of Hyperion</dc:title>
                <meta property="belongs-to-collection" id="c1">Hyperion Cantos</meta>
                <meta refines="#c1" property="collection-type">series</meta>
                <meta refines="#c1" property="group-position">2</meta>
                """
            ),
        )
        val meta = EpubMetadataReader.read(book)
        assertEquals("Hyperion Cantos", meta?.series)
        assertEquals(2.0, meta?.seriesIndex)
    }

    @Test
    fun `a collection that is explicitly a set is not a series`() {
        val book = epub(
            "META-INF/container.xml" to container("content.opf"),
            "content.opf" to opf(
                """
                <dc:title>Boxed</dc:title>
                <meta property="belongs-to-collection" id="c1">Complete Works</meta>
                <meta refines="#c1" property="collection-type">set</meta>
                """
            ),
        )
        assertNull(EpubMetadataReader.read(book)?.series)
    }

    @Test
    fun `a book with no series at all reports none rather than failing`() {
        val book = epub(
            "META-INF/container.xml" to container("content.opf"),
            "content.opf" to opf("<dc:title>Neuromancer</dc:title>"),
        )
        val meta = EpubMetadataReader.read(book)
        assertEquals("Neuromancer", meta?.title)
        assertNull(meta?.series)
        assertNull(meta?.seriesIndex)
    }

    @Test
    fun `an epub 2 cover meta resolves through the manifest to an entry`() {
        val book = epub(
            "META-INF/container.xml" to container("OEBPS/content.opf"),
            "OEBPS/content.opf" to opf(
                """
                <dc:title>Dune</dc:title>
                <meta name="cover" content="cover-img"/>
                """,
                manifest = """<item id="cover-img" href="images/cover.jpg" media-type="image/jpeg"/>""",
            ),
            "OEBPS/images/cover.jpg" to byteArrayOf(1, 2, 3),
        )
        val meta = EpubMetadataReader.read(book)
        assertEquals("OEBPS/images/cover.jpg", meta?.coverEntry)
        assertContentEquals(byteArrayOf(1, 2, 3), EpubMetadataReader.readEntry(book, meta!!.coverEntry!!))
    }

    @Test
    fun `an epub 3 cover-image property resolves to an entry`() {
        val book = epub(
            "META-INF/container.xml" to container("OEBPS/content.opf"),
            "OEBPS/content.opf" to opf(
                "<dc:title>Hyperion</dc:title>",
                manifest = """<item id="c" href="cover.png" media-type="image/png" properties="cover-image"/>""",
            ),
        )
        assertEquals("OEBPS/cover.png", EpubMetadataReader.read(book)?.coverEntry)
    }

    @Test
    fun `a properties token list is matched whole, not by substring`() {
        val book = epub(
            "META-INF/container.xml" to container("content.opf"),
            "content.opf" to opf(
                "<dc:title>X</dc:title>",
                manifest = """<item id="c" href="not-a-cover.png" properties="not-cover-image"/>""",
            ),
        )
        assertNull(EpubMetadataReader.read(book)?.coverEntry)
    }

    @Test
    fun `a percent-encoded href decodes, and a plus stays a plus`() {
        assertEquals("OEBPS/my cover.jpg", EpubMetadataReader.resolveAgainst("OEBPS/content.opf", "my%20cover.jpg"))

        assertEquals("OEBPS/C++ Primer.jpg", EpubMetadataReader.resolveAgainst("OEBPS/content.opf", "C++%20Primer.jpg"))
    }

    @Test
    fun `a parent-relative href climbs out of the package document's folder`() {
        assertEquals("images/cover.jpg", EpubMetadataReader.resolveAgainst("OEBPS/content.opf", "../images/cover.jpg"))
    }

    @Test
    fun `an href that climbs past the archive root is refused rather than clamped`() {
        assertNull(EpubMetadataReader.resolveAgainst("content.opf", "../../etc/passwd"))
    }

    @Test
    fun `the package document path is read from container xml, not assumed`() {
        val book = epub(
            "META-INF/container.xml" to container("EPUB/package.opf"),
            "EPUB/package.opf" to opf("<dc:title>Elsewhere</dc:title>"),
        )
        assertEquals("Elsewhere", EpubMetadataReader.read(book)?.title)
    }

    @Test
    fun `a package document stored before container xml is still found`() {
        val book = epub(
            "OEBPS/content.opf" to opf("<dc:title>Out Of Order</dc:title>"),
            "META-INF/container.xml" to container("OEBPS/content.opf"),
        )
        assertEquals("Out Of Order", EpubMetadataReader.read(book)?.title)
    }

    @Test
    fun `a zip that is not an epub yields null rather than throwing`() {
        assertNull(EpubMetadataReader.read(epub("readme.txt" to "hello".toByteArray())))
    }

    @Test
    fun `a file that is not a zip at all yields null rather than throwing`() {
        val garbage = "this is not a zip".toByteArray()
        assertNull(EpubMetadataReader.read { ByteArrayInputStream(garbage) })
    }

    @Test
    fun `a container naming a package document that is absent yields null`() {
        val book = epub("META-INF/container.xml" to container("OEBPS/missing.opf"))
        assertNull(EpubMetadataReader.read(book))
    }

    @Test
    fun `an external entity is never resolved into the title`() {
        val hostile = """
            <?xml version="1.0"?>
            <!DOCTYPE package [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <package xmlns="http://www.idpf.org/2007/opf">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>&xxe;</dc:title></metadata>
            </package>
        """.trimIndent().toByteArray()
        val book = epub(
            "META-INF/container.xml" to container("content.opf"),
            "content.opf" to hostile,
        )
        val title = EpubMetadataReader.read(book)?.title.orEmpty()
        assertFalse(title.contains("root:"), "the title must never hold the contents of a file")
        assertFalse(title.contains("/bin/"), "the title must never hold the contents of a file")
    }
}
