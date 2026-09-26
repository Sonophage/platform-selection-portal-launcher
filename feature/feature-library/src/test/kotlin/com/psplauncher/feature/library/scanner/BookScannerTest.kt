package com.psplauncher.feature.library.scanner

import android.net.Uri
import com.psplauncher.core.data.saf.SafChild
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class BookScannerTest {
    private class Tree(private val dirs: Map<String, List<SafChild>>) {
        val listChildren: (String) -> List<SafChild> = { dirs[it].orEmpty() }
    }

    private fun file(name: String, mime: String? = "application/epub+zip", id: String = name) =
        SafChild(
            documentId = id,

            uri = STUB_URI,
            name = name,
            mime = mime,
            isDirectory = false,
            lastModified = 1L,
            sizeBytes = 2L,
        )

    private fun dir(name: String, id: String = name) = SafChild(
        documentId = id,
        uri = STUB_URI,
        name = name,
        mime = "vnd.android.document/directory",
        isDirectory = true,
        lastModified = null,
        sizeBytes = null,
    )

    private fun collect(tree: Tree, recursive: Boolean = true) =
        collectBookFiles("root", recursive, tree.listChildren)

    @Test
    fun `a nested book is found when recursive, and not when not`() {
        val tree = Tree(
            mapOf(
                "root" to listOf(dir("Fiction")),
                "Fiction" to listOf(dir("SciFi")),
                "SciFi" to listOf(file("Dune.epub")),
            )
        )

        val deep = collect(tree, recursive = true)
        assertEquals(listOf("Dune.epub"), deep.map { it.child.name })
        assertEquals("Fiction/SciFi", deep.single().relativePath, "the path is what the list groups by")

        assertEquals(emptyList(), collect(tree, recursive = false).map { it.child.name })
    }

    @Test
    fun `a nomedia marker skips the folder and everything under it`() {
        val tree = Tree(
            mapOf(
                "root" to listOf(dir("Private"), file("Kept.epub")),
                "Private" to listOf(file(".nomedia", mime = null, id = "nm"), file("Hidden.epub"), dir("Deeper")),
                "Deeper" to listOf(file("AlsoHidden.epub")),
            )
        )

        assertEquals(listOf("Kept.epub"), collect(tree).map { it.child.name })
    }

    @Test
    fun `hidden directories are pruned without being entered`() {
        var entered = false
        val dirs = mapOf(
            "root" to listOf(dir(".thumbnails", id = "thumbs"), file("Kept.epub")),
        )
        val listChildren: (String) -> List<SafChild> = { id ->
            if (id == "thumbs") { entered = true; listOf(file("Cached.epub")) } else dirs[id].orEmpty()
        }

        val found = collectBookFiles("root", scanRecursively = true, listChildren = listChildren)

        assertEquals(listOf("Kept.epub"), found.map { it.child.name })
        assertEquals(false, entered, "a hidden directory should be pruned, not listed then discarded")
    }

    @Test
    fun `files that are not books are ignored, including by extension when the type is unknown`() {
        val tree = Tree(
            mapOf(
                "root" to listOf(
                    file("Dune.epub", mime = "application/octet-stream"),
                    file("cover.jpg", mime = "image/jpeg"),
                    file("notes.txt", mime = "application/octet-stream"),
                    file("Ubik.EPUB", mime = null),
                )
            )
        )

        assertEquals(listOf("Dune.epub", "Ubik.EPUB"), collect(tree).map { it.child.name })
    }

    @Test
    fun `one document listed under two parents produces one book, not two`() {
        val shared = file("Dune.epub", id = "shared")
        val tree = Tree(
            mapOf(
                "root" to listOf(dir("A"), dir("B")),
                "A" to listOf(shared),
                "B" to listOf(shared),
            )
        )

        assertEquals(1, collect(tree).size)
    }

    private companion object {
        val STUB_URI: Uri = mockk(relaxed = true)
    }

    @Test
    fun `a directory that contains itself does not loop forever`() {
        val tree = Tree(
            mapOf(
                "root" to listOf(dir("Loop")),
                "Loop" to listOf(dir("Loop"), file("Dune.epub")),
            )
        )

        assertEquals(listOf("Dune.epub"), collect(tree).map { it.child.name })
    }
}
