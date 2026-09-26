package com.psplauncher.feature.artwork.portable

import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArtworkIdentityRecorderTest {
    private val library = mockk<PortableArtworkLibrary>()
    private val tree = mockk<Uri>()
    private lateinit var recorder: ArtworkIdentityRecorder

    private fun entry(
        kind: String = "ICON",
        portableName: String = "Final Fantasy VI",
        ssId: Long? = 1234,
    ) = ArtworkIdentityIndex.Entry(
        platformId = "snes", kind = kind, portableName = portableName,
        romCrc32 = "A1B2C3D4", ssId = ssId, artworkKey = "rom/snes/final-fantasy-vi",
    )

    @Before fun setUp() {
        coEvery { tree.toString() } returns "content://tree/primary%3AArtwork"
        coEvery { library.readIdentityIndex(any()) } returns PortableArtworkLibrary.IdentityIndexRead.Absent
        coEvery { library.writeIdentityIndex(any(), any()) } returns true
        recorder = ArtworkIdentityRecorder(library)
    }

    @Test fun `recording never writes`() = runTest {
        recorder.record(tree, entry())
        recorder.record(tree, entry(kind = "HERO"))
        recorder.record(tree, entry(kind = "LOGO"))

        coVerify(exactly = 0) { library.writeIdentityIndex(any(), any()) }
    }

    @Test fun `flush writes once, carrying everything recorded`() = runTest {
        recorder.record(tree, entry())
        recorder.record(tree, entry(kind = "HERO"))
        val written = slot<ArtworkIdentityIndex>()

        assertTrue(recorder.flush(tree))

        coVerify(exactly = 1) { library.writeIdentityIndex(tree, capture(written)) }
        assertEquals(2, written.captured.entries.size)
        assertEquals(1234L, written.captured.find("snes", "HERO", "Final Fantasy VI")?.ssId)
    }

    @Test fun `flushing with nothing recorded writes nothing`() = runTest {
        assertTrue(recorder.flush(tree))
        coVerify(exactly = 0) { library.writeIdentityIndex(any(), any()) }
    }

    @Test fun `a second flush with no new rows does not rewrite`() = runTest {
        recorder.record(tree, entry())
        recorder.flush(tree)
        recorder.flush(tree)

        coVerify(exactly = 1) { library.writeIdentityIndex(any(), any()) }
    }

    @Test fun `rows already in the folder survive a flush`() = runTest {
        val existing = ArtworkIdentityIndex(
            entries = listOf(
                ArtworkIdentityIndex.Entry(
                    platformId = "psx", kind = "ICON", portableName = "Jak and Daxter", ssId = 77,
                ),
            ),
        )
        coEvery { library.readIdentityIndex(any()) } returns PortableArtworkLibrary.IdentityIndexRead.Loaded(existing)
        val written = slot<ArtworkIdentityIndex>()

        recorder.record(tree, entry())
        recorder.flush(tree)

        coVerify { library.writeIdentityIndex(tree, capture(written)) }
        assertEquals(2, written.captured.entries.size)
        assertEquals(77L, written.captured.find("psx", "ICON", "Jak and Daxter")?.ssId)
    }

    @Test fun `the folder is read once, not once per recorded row`() = runTest {
        recorder.record(tree, entry())
        recorder.record(tree, entry(kind = "HERO"))
        recorder.flush(tree)

        coVerify(exactly = 1) { library.readIdentityIndex(any()) }
    }

    @Test fun `re-recording the same file replaces its row rather than duplicating it`() = runTest {
        val written = slot<ArtworkIdentityIndex>()
        recorder.record(tree, entry(ssId = 1))
        recorder.record(tree, entry(ssId = 2))
        recorder.flush(tree)

        coVerify { library.writeIdentityIndex(tree, capture(written)) }
        assertEquals(1, written.captured.entries.size)
        assertEquals(2L, written.captured.find("snes", "ICON", "Final Fantasy VI")?.ssId)
    }

    @Test fun `a failed write stays dirty so the next flush retries`() = runTest {
        coEvery { library.writeIdentityIndex(any(), any()) } returns false
        recorder.record(tree, entry())

        assertTrue(!recorder.flush(tree))

        coEvery { library.writeIdentityIndex(any(), any()) } returns true
        assertTrue(recorder.flush(tree))
        coVerify(exactly = 2) { library.writeIdentityIndex(any(), any()) }
    }

    @Test fun `an absent folder index is created by flush`() = runTest {
        coEvery { library.readIdentityIndex(any()) } returns PortableArtworkLibrary.IdentityIndexRead.Absent
        recorder.record(tree, entry())

        assertTrue(recorder.flush(tree))
        coVerify(exactly = 1) { library.writeIdentityIndex(any(), any()) }
    }

    @Test fun `an unreadable folder index is not written by flush`() = runTest {
        coEvery { library.readIdentityIndex(any()) } returns
            PortableArtworkLibrary.IdentityIndexRead.Unreadable("IO error")
        recorder.record(tree, entry())

        assertTrue(!recorder.flush(tree))
        coVerify(exactly = 0) { library.writeIdentityIndex(any(), any()) }
    }

    @Test fun `a read that recovers on retry merges buffered rows`() = runTest {
        coEvery { library.readIdentityIndex(any()) } returns
            PortableArtworkLibrary.IdentityIndexRead.Unreadable("IO error")
        recorder.record(tree, entry())
        assertTrue(!recorder.flush(tree))

        val recovered = ArtworkIdentityIndex(
            entries = listOf(
                ArtworkIdentityIndex.Entry(
                    platformId = "psx", kind = "ICON", portableName = "Jak and Daxter", ssId = 77,
                ),
            ),
        )
        coEvery { library.readIdentityIndex(any()) } returns
            PortableArtworkLibrary.IdentityIndexRead.Loaded(recovered)
        val written = slot<ArtworkIdentityIndex>()

        assertTrue(recorder.flush(tree))

        coVerify { library.writeIdentityIndex(tree, capture(written)) }
        assertEquals(2, written.captured.entries.size)
        assertEquals(1234L, written.captured.find("snes", "ICON", "Final Fantasy VI")?.ssId)
        assertEquals(77L, written.captured.find("psx", "ICON", "Jak and Daxter")?.ssId)
    }

    @Test fun `the next ensureLoaded for the tree retries an unreadable index`() = runTest {
        coEvery { library.readIdentityIndex(any()) } returns
            PortableArtworkLibrary.IdentityIndexRead.Unreadable("IO error")

        recorder.record(tree, entry())
        recorder.record(tree, entry(kind = "HERO"))
        recorder.flush(tree)

        coVerify(atLeast = 2) { library.readIdentityIndex(any()) }
    }

    @Test fun `recordAll buffers every row and flush writes them all at once`() = runTest {
        val written = slot<ArtworkIdentityIndex>()

        recorder.recordAll(tree, listOf(entry(), entry(kind = "HERO"), entry(kind = "LOGO")))

        coVerify(exactly = 0) { library.writeIdentityIndex(any(), any()) }
        assertTrue(recorder.flush(tree))
        coVerify(exactly = 1) { library.writeIdentityIndex(tree, capture(written)) }
        assertEquals(3, written.captured.entries.size)
    }

    @Test fun `recordAll with nothing does not even touch the folder`() = runTest {
        recorder.recordAll(tree, emptyList())

        coVerify(exactly = 0) { library.readIdentityIndex(any()) }
        assertTrue(recorder.flush(tree))
        coVerify(exactly = 0) { library.writeIdentityIndex(any(), any()) }
    }

    @Test fun `a recorder loaded before a relink keeps the relink's rows after flush`() = runTest {
        val existing = ArtworkIdentityIndex(
            entries = listOf(
                ArtworkIdentityIndex.Entry(
                    platformId = "psx", kind = "ICON", portableName = "Jak and Daxter", ssId = 77,
                ),
            ),
        )
        coEvery { library.readIdentityIndex(any()) } returns PortableArtworkLibrary.IdentityIndexRead.Loaded(existing)

        val seen = recorder.current(tree)
        assertEquals(1, seen.entries.size)

        val relinkRows = listOf(
            entry(kind = "ICON"),
            ArtworkIdentityIndex.Entry(
                platformId = "psx", kind = "ICON", portableName = "Jak and Daxter", ssId = 77,
            ),
        )
        recorder.recordAll(tree, relinkRows)
        val written = slot<ArtworkIdentityIndex>()

        assertTrue(recorder.flush(tree))

        coVerify { library.writeIdentityIndex(tree, capture(written)) }
        assertEquals(2, written.captured.entries.size)
        assertEquals(1234L, written.captured.find("snes", "ICON", "Final Fantasy VI")?.ssId)
        assertEquals(77L, written.captured.find("psx", "ICON", "Jak and Daxter")?.ssId)
    }
}
