package com.psplauncher.feature.library.scanner

import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscSetBuilderTest {
    private val builder = DiscSetBuilder()

    private fun game(path: String, platformId: String = "psx"): Game {
        val stem = path.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        return Game(title = stem, platformId = platformId, romPath = path)
    }

    private fun m3u(entries: List<String>): DiscSetBuilder.M3uReader =
        DiscSetBuilder.M3uReader { game ->
            if (game.romPath.orEmpty().endsWith(".m3u", ignoreCase = true)) entries else null
        }

    @Test
    fun `three cue discs in one folder form one set with disc 1 primary`() {
        val games = listOf(
            game("/roms/psx/Final Fantasy VII (Disc 1).cue"),
            game("/roms/psx/Final Fantasy VII (Disc 2).cue"),
            game("/roms/psx/Final Fantasy VII (Disc 3).cue"),
        )

        val assigned = builder.assign(games) { null }

        assertEquals(1, assigned.map { it.discSetKey }.distinct().size)
        assertEquals(listOf(1, 2, 3), assigned.map { it.discNumber })
        assertEquals(1, assigned.count { it.isDiscPrimary })
        assertEquals("/roms/psx/Final Fantasy VII (Disc 1).cue", assigned.single { it.isDiscPrimary }.romPath)
    }

    @Test
    fun `an m3u beside the discs takes over as primary`() {
        val games = listOf(
            game("/roms/psx/Final Fantasy VII (Disc 1).cue"),
            game("/roms/psx/Final Fantasy VII (Disc 2).cue"),
            game("/roms/psx/Final Fantasy VII.m3u"),
        )

        val assigned = builder.assign(games) {
            m3u(listOf("Final Fantasy VII (Disc 1).cue", "Final Fantasy VII (Disc 2).cue")).read(it)
        }

        val primary = assigned.single { it.isDiscPrimary }
        assertEquals("/roms/psx/Final Fantasy VII.m3u", primary.romPath)
        assertNull(primary.discNumber)
        assertTrue(assigned.filter { it.romPath.orEmpty().endsWith(".cue") }.none { it.isDiscPrimary })
        assertEquals(1, assigned.single { it.romPath == "/roms/psx/Final Fantasy VII (Disc 1).cue" }.discNumber)
        assertEquals(2, assigned.single { it.romPath == "/roms/psx/Final Fantasy VII (Disc 2).cue" }.discNumber)
    }

    @Test
    fun `playlist entries with path prefixes still resolve`() {
        val games = listOf(
            game("/roms/psx/Final Fantasy VII (Disc 1).cue"),
            game("/roms/psx/Final Fantasy VII (Disc 2).cue"),
            game("/roms/psx/Final Fantasy VII.m3u"),
        )

        val assigned = builder.assign(games) {
            m3u(listOf("./Final Fantasy VII (Disc 1).cue", "discs/Final Fantasy VII (Disc 2).cue")).read(it)
        }

        assertEquals("/roms/psx/Final Fantasy VII.m3u", assigned.single { it.isDiscPrimary }.romPath)
        assertEquals(2, assigned.count { it.discSetKey != null && !it.isDiscPrimary })
    }

    @Test
    fun `an m3u listing files that were not scanned creates no set`() {
        val games = listOf(game("/roms/psx/Final Fantasy VII.m3u"))

        val assigned = builder.assign(games) {
            m3u(listOf("Some Other Game (Disc 1).cue", "Still Another Game (Disc 2).cue")).read(it)
        }

        assertNull(assigned.single().discSetKey)
        assertFalse(assigned.single().isDiscPrimary)
    }

    @Test
    fun `an unreadable m3u leaves discs with their own identity`() {
        val games = listOf(
            game("/roms/psx/Final Fantasy VII (Disc 1).cue"),
            game("/roms/psx/Final Fantasy VII (Disc 2).cue"),
            game("/roms/psx/Final Fantasy VII.m3u"),
        )

        val assigned = builder.assign(games) { null }

        val discs = assigned.filter { it.romPath.orEmpty().endsWith(".cue") }
        assertEquals(1, discs.map { it.discSetKey }.distinct().size)
        assertNull(assigned.single { it.romPath.orEmpty().endsWith(".m3u") }.discSetKey)
    }

    @Test
    fun `two same-named games in different folders do not merge`() {
        val games = listOf(
            game("/roms/psx/NA/Final Fantasy VII (Disc 1).cue"),
            game("/roms/psx/NA/Final Fantasy VII (Disc 2).cue"),
            game("/roms/psx/EU/Final Fantasy VII (Disc 1).cue"),
            game("/roms/psx/EU/Final Fantasy VII (Disc 2).cue"),
        )

        val assigned = builder.assign(games) { null }

        assertEquals(2, assigned.mapNotNull { it.discSetKey }.distinct().size)
        assertEquals(2, assigned.count { it.isDiscPrimary })
    }

    @Test
    fun `a lone disc-tagged game forms a set of one and is primary`() {
        val games = listOf(game("/roms/dreamcast/Crazy Taxi (Disc 1).gdi", "dreamcast"))

        val assigned = builder.assign(games) { null }

        assertNotNull(assigned.single().discSetKey)
        assertEquals(1, assigned.single().discNumber)
        assertTrue(assigned.single().isDiscPrimary)
    }

    @Test
    fun `a game with no disc tag gets a null set key and is unaffected`() {
        val games = listOf(game("/roms/gba/Pokemon Emerald.gba", "gba"))

        val assigned = builder.assign(games) { null }

        assertNull(assigned.single().discSetKey)
        assertNull(assigned.single().discNumber)
        assertFalse(assigned.single().isDiscPrimary)
    }

    @Test
    fun `disc tag ordering does not change the set key`() {
        val a = builder.assign(listOf(game("/roms/psx/Final Fantasy VII (Disc 1) (USA).cue"))) { null }
        val b = builder.assign(listOf(game("/roms/psx/Final Fantasy VII (USA) (Disc 1).cue"))) { null }

        assertEquals(a.single().discSetKey, b.single().discSetKey)
    }

    @Test
    fun `windows-style paths group discs into one set`() {
        val games = listOf(
            game("D:\\Emulators\\Roms\\psx\\Parasite Eve II (USA) (Disc 1)\\Parasite Eve II (USA) (Disc 1).cue"),
            game("D:\\Emulators\\Roms\\psx\\Parasite Eve II (USA) (Disc 2)\\Parasite Eve II (USA) (Disc 2).cue"),
        )

        val assigned = builder.assign(games) { null }

        assertEquals(1, assigned.mapNotNull { it.discSetKey }.distinct().size)
        assertEquals(1, assigned.count { it.isDiscPrimary })
        assertEquals(listOf(1, 2), assigned.mapNotNull { it.discNumber }.sorted())
        assertEquals(
            "D:\\Emulators\\Roms\\psx\\Parasite Eve II (USA) (Disc 1)\\Parasite Eve II (USA) (Disc 1).cue",
            assigned.single { it.isDiscPrimary }.romPath,
        )
    }

    @Test
    fun `per-disc subfolders with forward slashes group into one set`() {
        val games = listOf(
            game("/roms/psx/Parasite Eve II (USA) (Disc 1)/Parasite Eve II (USA) (Disc 1).cue"),
            game("/roms/psx/Parasite Eve II (USA) (Disc 2)/Parasite Eve II (USA) (Disc 2).cue"),
        )

        val assigned = builder.assign(games) { null }

        assertEquals(1, assigned.mapNotNull { it.discSetKey }.distinct().size)
        assertEquals(1, assigned.count { it.isDiscPrimary })
        assertEquals(listOf(1, 2), assigned.mapNotNull { it.discNumber }.sorted())
        assertEquals(
            "/roms/psx/Parasite Eve II (USA) (Disc 1)/Parasite Eve II (USA) (Disc 1).cue",
            assigned.single { it.isDiscPrimary }.romPath,
        )
    }

    @Test
    fun `region tag on one disc's folder only does not split the set`() {
        val games = listOf(
            game("/storage/408C-3861/Emulation/roms/psx/Parasite Eve II (USA) (Disc 1)/Parasite Eve II (USA) (Disc 1).cue"),
            game("/storage/408C-3861/Emulation/roms/psx/Parasite Eve II (Disc 2)/Parasite Eve II (Disc 2).cue"),
        )

        val assigned = builder.assign(games) { null }

        assertEquals(1, assigned.mapNotNull { it.discSetKey }.distinct().size)
        assertEquals(1, assigned.count { it.isDiscPrimary })
        assertEquals(listOf(1, 2), assigned.mapNotNull { it.discNumber }.sorted())
        assertEquals(
            "/storage/408C-3861/Emulation/roms/psx/Parasite Eve II (USA) (Disc 1)/Parasite Eve II (USA) (Disc 1).cue",
            assigned.single { it.isDiscPrimary }.romPath,
        )
    }

    @Test
    fun `dumps in structurally different folders still do not merge`() {
        val games = listOf(
            game("/roms/psx/NA/Final Fantasy VII (Disc 1)/Final Fantasy VII (Disc 1).cue"),
            game("/roms/psx/NA/Final Fantasy VII (Disc 2)/Final Fantasy VII (Disc 2).cue"),
            game("/roms/psx/EU/Final Fantasy VII (Disc 1)/Final Fantasy VII (Disc 1).cue"),
            game("/roms/psx/EU/Final Fantasy VII (Disc 2)/Final Fantasy VII (Disc 2).cue"),
        )

        val assigned = builder.assign(games) { null }

        assertEquals(2, assigned.mapNotNull { it.discSetKey }.distinct().size)
        assertEquals(2, assigned.count { it.isDiscPrimary })
    }

    @Test
    fun `an m3u beside per-disc subfolders unifies them into one set with the m3u primary`() {
        val games = listOf(
            game("D:\\Emulators\\Roms\\psx\\Parasite Eve II (USA) (Disc 1)\\Parasite Eve II (USA) (Disc 1).cue"),
            game("D:\\Emulators\\Roms\\psx\\Parasite Eve II (USA) (Disc 2)\\Parasite Eve II (USA) (Disc 2).cue"),
            game("D:\\Emulators\\Roms\\psx\\Parasite Eve II (USA).m3u"),
        )

        val assigned = builder.assign(games) {
            m3u(listOf(
                "Parasite Eve II (USA) (Disc 1).cue",
                "Parasite Eve II (USA) (Disc 2).cue",
            )).read(it)
        }

        assertEquals(1, assigned.mapNotNull { it.discSetKey }.distinct().size)
        val primary = assigned.single { it.isDiscPrimary }
        assertEquals("D:\\Emulators\\Roms\\psx\\Parasite Eve II (USA).m3u", primary.romPath)
        assertNull(primary.discNumber)

        assertEquals(1, assigned.single { it.romPath.orEmpty().contains("(Disc 1)") }.discNumber)
        assertEquals(2, assigned.single { it.romPath.orEmpty().contains("(Disc 2)") }.discNumber)
        assertTrue(assigned.filter { it.romPath.orEmpty().endsWith(".cue") }.none { it.isDiscPrimary })
    }

    @Test
    fun `an m3u beside per-disc subfolders unifies them with forward-slash paths too`() {
        val games = listOf(
            game("/roms/psx/Final Fantasy VII (Disc 1)/Final Fantasy VII (Disc 1).cue"),
            game("/roms/psx/Final Fantasy VII (Disc 2)/Final Fantasy VII (Disc 2).cue"),
            game("/roms/psx/Final Fantasy VII.m3u"),
        )

        val assigned = builder.assign(games) {
            m3u(listOf("Final Fantasy VII (Disc 1).cue", "Final Fantasy VII (Disc 2).cue")).read(it)
        }

        assertEquals(1, assigned.mapNotNull { it.discSetKey }.distinct().size)
        assertEquals("/roms/psx/Final Fantasy VII.m3u", assigned.single { it.isDiscPrimary }.romPath)
        assertEquals(2, assigned.count { it.discSetKey != null && !it.isDiscPrimary })
    }

    @Test
    fun `same detected region unifies discs even when folder region tags disagree`() {
        val games = listOf(
            game("/storage/408C-3861/Emulation/roms/psx/Parasite Eve II (USA) (Disc 1)/Parasite Eve II (USA) (Disc 1).cue")
                .copy(region = GameRegion.NTSC_U),
            game("/storage/408C-3861/Emulation/roms/psx/Parasite Eve II (Disc 2)/Parasite Eve II (Disc 2).cue")
                .copy(region = GameRegion.NTSC_U),
        )

        val assigned = builder.assign(games) { null }

        assertEquals(1, assigned.mapNotNull { it.discSetKey }.distinct().size)
        assertEquals(1, assigned.count { it.isDiscPrimary })
        assertEquals(listOf(1, 2), assigned.mapNotNull { it.discNumber }.sorted())
        assertEquals(GameRegion.NTSC_U, assigned.single { it.isDiscPrimary }.region)
    }

    @Test
    fun `detected region from the reader drives unification and is persisted on the rows`() {
        val games = listOf(
            game("/roms/psx/Parasite Eve II (USA) (Disc 1)/Parasite Eve II (USA) (Disc 1).cue"),
            game("/roms/psx/Parasite Eve II (Disc 2)/Parasite Eve II (Disc 2).cue"),
        )

        val assigned = builder.assign(games, { GameRegion.NTSC_U }) { null }

        assertEquals(1, assigned.mapNotNull { it.discSetKey }.distinct().size)
        assertEquals(2, assigned.count { it.region == GameRegion.NTSC_U })
    }

    @Test
    fun `conflicting detected regions split sibling disc folders into two sets`() {
        val games = listOf(
            game("/roms/psx/Final Fantasy VII (USA) (Disc 1)/Final Fantasy VII (Disc 1).cue")
                .copy(region = GameRegion.NTSC_U),
            game("/roms/psx/Final Fantasy VII (USA) (Disc 2)/Final Fantasy VII (Disc 2).cue")
                .copy(region = GameRegion.NTSC_U),
            game("/roms/psx/Final Fantasy VII (Europe) (Disc 1)/Final Fantasy VII (Disc 1).cue")
                .copy(region = GameRegion.PAL),
            game("/roms/psx/Final Fantasy VII (Europe) (Disc 2)/Final Fantasy VII (Disc 2).cue")
                .copy(region = GameRegion.PAL),
        )

        val assigned = builder.assign(games) { null }

        assertEquals(2, assigned.mapNotNull { it.discSetKey }.distinct().size)
        assertEquals(2, assigned.count { it.isDiscPrimary })
    }

    @Test
    fun `a disc with unknown region keeps the group merged`() {
        val games = listOf(
            game("/roms/psx/Final Fantasy VII (Disc 1)/Final Fantasy VII (Disc 1).cue")
                .copy(region = GameRegion.NTSC_U),
            game("/roms/psx/Final Fantasy VII (Disc 2)/Final Fantasy VII (Disc 2).cue"),
        )

        val assigned = builder.assign(games) { null }

        assertEquals(1, assigned.mapNotNull { it.discSetKey }.distinct().size)
        assertEquals(1, assigned.count { it.isDiscPrimary })
    }

    @Test
    fun `reconcile persists a newly detected region`() {
        val disc1 = setGame("/roms/psx/Final Fantasy VII (Disc 1).cue", "psx\u0001/roms/psx\u0001Final Fantasy VII", 1, true)
        val disc2 = setGame("/roms/psx/Final Fantasy VII (Disc 2).cue", "psx\u0001/roms/psx\u0001Final Fantasy VII", 2, false)

        val updated = builder.reconcile(listOf(disc1, disc2), { GameRegion.NTSC_U }) { null }

        assertEquals(2, updated.size)
        assertTrue(updated.all { it.region == GameRegion.NTSC_U })
    }

    private fun setGame(
        path: String,
        key: String?,
        number: Int?,
        primary: Boolean,
        platformId: String = "psx",
    ): Game = game(path, platformId).copy(discSetKey = key, discNumber = number, isDiscPrimary = primary)

    @Test
    fun `reconcile joins a newly added disc into an existing m3u set`() {
        val m3uKey = "psx\u0001/roms/psx\u0001Final Fantasy VII"
        val existing = listOf(
            setGame("/roms/psx/Final Fantasy VII.m3u", m3uKey, null, true),
            setGame("/roms/psx/Final Fantasy VII (Disc 1)/Final Fantasy VII (Disc 1).cue", m3uKey, 1, false),
            setGame("/roms/psx/Final Fantasy VII (Disc 2)/Final Fantasy VII (Disc 2).cue", m3uKey, 2, false),
        )
        val disc3 = setGame(
            "/roms/psx/Final Fantasy VII (Disc 3)/Final Fantasy VII (Disc 3).cue",
            "psx\u0001/roms/psx/Final Fantasy VII (Disc 3)\u0001Final Fantasy VII",
            3,
            true,
        )

        val updated = builder.reconcile(existing + disc3) {
            m3u(listOf(
                "Final Fantasy VII (Disc 1).cue",
                "Final Fantasy VII (Disc 2).cue",
                "Final Fantasy VII (Disc 3).cue",
            )).read(it)
        }

        assertEquals(1, updated.size)
        val joined = updated.single()
        assertEquals(disc3.romPath, joined.romPath)
        assertEquals(m3uKey, joined.discSetKey)
        assertEquals(3, joined.discNumber)
        assertFalse(joined.isDiscPrimary)
    }

    @Test
    fun `reconcile flips the primary to a newly added lower disc`() {
        val key = "psx\u0001/roms/psx\u0001Final Fantasy VII"
        val disc2 = setGame("/roms/psx/Final Fantasy VII (Disc 2).cue", key, 2, true)
        val disc1 = setGame("/roms/psx/Final Fantasy VII (Disc 1).cue", key, 1, true)

        val updated = builder.reconcile(listOf(disc2, disc1)) { null }

        assertEquals(1, updated.size)
        val flipped = updated.single()
        assertEquals(disc2.romPath, flipped.romPath)
        assertFalse(flipped.isDiscPrimary)
    }

    @Test
    fun `reconcile is a no-op on a fully correct batch`() {
        val m3uKey = "psx\u0001/roms/psx\u0001Final Fantasy VII"
        val correct = listOf(
            setGame("/roms/psx/Final Fantasy VII.m3u", m3uKey, null, true),
            setGame("/roms/psx/Final Fantasy VII (Disc 1)/Final Fantasy VII (Disc 1).cue", m3uKey, 1, false),
            setGame("/roms/psx/Final Fantasy VII (Disc 2)/Final Fantasy VII (Disc 2).cue", m3uKey, 2, false),
            setGame("/roms/gba/Pokemon Emerald.gba", null, null, false, "gba"),
        )

        val updated = builder.reconcile(correct) {
            m3u(listOf(
                "Final Fantasy VII (Disc 1).cue",
                "Final Fantasy VII (Disc 2).cue",
            )).read(it)
        }

        assertTrue(updated.isEmpty())
    }

    @Test
    fun `reconcile adopts existing discs into a newly added m3u set`() {
        val m3u = setGame("/roms/psx/Final Fantasy VII.m3u", null, null, false)
        val disc1 = setGame(
            "/roms/psx/Final Fantasy VII (Disc 1)/Final Fantasy VII (Disc 1).cue",
            "psx\u0001/roms/psx/Final Fantasy VII (Disc 1)\u0001Final Fantasy VII",
            1,
            true,
        )
        val disc2 = setGame(
            "/roms/psx/Final Fantasy VII (Disc 2)/Final Fantasy VII (Disc 2).cue",
            "psx\u0001/roms/psx/Final Fantasy VII (Disc 2)\u0001Final Fantasy VII",
            2,
            true,
        )

        val updated = builder.reconcile(listOf(m3u, disc1, disc2)) {
            m3u(listOf("Final Fantasy VII (Disc 1).cue", "Final Fantasy VII (Disc 2).cue")).read(it)
        }

        val m3uKey = "psx\u0001/roms/psx\u0001Final Fantasy VII"
        assertEquals(3, updated.size)
        assertEquals(m3uKey, updated.single { it.romPath.orEmpty().endsWith(".m3u") }.discSetKey)
        assertTrue(updated.single { it.romPath.orEmpty().endsWith(".m3u") }.isDiscPrimary)
        assertEquals(1, updated.single { it.romPath.orEmpty().contains("(Disc 1)") }.discNumber)
        assertEquals(m3uKey, updated.single { it.romPath.orEmpty().contains("(Disc 1)") }.discSetKey)
    }

    @Test
    fun `an unreadable existing playlist clears its stale set assignment`() {
        val key = "psx\u0001/roms/psx\u0001Final Fantasy VII"
        val playlist = setGame("/roms/psx/Final Fantasy VII.m3u", key, null, true)

        val updated = builder.reconcile(listOf(playlist)) { null }

        assertEquals(1, updated.size)
        assertNull(updated.single().discSetKey)
        assertNull(updated.single().discNumber)
        assertFalse(updated.single().isDiscPrimary)
    }

    @Test
    fun `untagged playlist entries take playlist order for disc numbers`() {
        val games = listOf(
            game("/roms/psx/Resident Evil.cue"),
            game("/roms/psx/Resident Evil (Disc 2).cue"),
            game("/roms/psx/Resident Evil.m3u"),
        )

        val assigned = builder.assign(games) {
            m3u(listOf("Resident Evil.cue", "Resident Evil (Disc 2).cue")).read(it)
        }

        assertEquals("/roms/psx/Resident Evil.m3u", assigned.single { it.isDiscPrimary }.romPath)
        assertEquals(1, assigned.single { it.romPath == "/roms/psx/Resident Evil.cue" }.discNumber)
        assertEquals(2, assigned.single { it.romPath == "/roms/psx/Resident Evil (Disc 2).cue" }.discNumber)
        assertEquals(1, assigned.mapNotNull { it.discSetKey }.distinct().size)
    }

    private class CountingRegionReader(
        private val answer: (Game) -> GameRegion? = { null },
    ) : DiscSetBuilder.RegionReader {
        val calls = mutableMapOf<String, Int>()

        override fun read(game: Game): GameRegion? {
            calls.merge(game.romPath.orEmpty(), 1, Int::plus)
            return answer(game)
        }
    }

    @Test
    fun `an undetectable region is read once per path, not once per call site`() {
        val disc1 = "/roms/psx/Parasite Eve II (USA) (Disc 1)/Parasite Eve II (USA) (Disc 1).cue"
        val disc2 = "/roms/psx/Parasite Eve II (Disc 2)/Parasite Eve II (Disc 2).cue"
        val reader = CountingRegionReader { null }

        builder.assign(listOf(game(disc1), game(disc2)), reader) { null }

        assertEquals(mapOf(disc1 to 1, disc2 to 1), reader.calls)
    }

    @Test
    fun `an undetectable region is still read only once when reconciling`() {
        val disc1 = "/roms/psx/Parasite Eve II (USA) (Disc 1)/Parasite Eve II (USA) (Disc 1).cue"
        val disc2 = "/roms/psx/Parasite Eve II (Disc 2)/Parasite Eve II (Disc 2).cue"
        val reader = CountingRegionReader { null }

        builder.reconcile(listOf(game(disc1), game(disc2)), reader) { null }

        assertEquals(mapOf(disc1 to 1, disc2 to 1), reader.calls)
    }

    @Test
    fun `a detected region is read once per path`() {
        val disc1 = "/roms/psx/Parasite Eve II (USA) (Disc 1)/Parasite Eve II (USA) (Disc 1).cue"
        val disc2 = "/roms/psx/Parasite Eve II (Disc 2)/Parasite Eve II (Disc 2).cue"
        val reader = CountingRegionReader { GameRegion.NTSC_U }

        val assigned = builder.assign(listOf(game(disc1), game(disc2)), reader) { null }

        assertEquals(mapOf(disc1 to 1, disc2 to 1), reader.calls)
        assertEquals(2, assigned.count { it.region == GameRegion.NTSC_U })
    }

    @Test
    fun `a region split does not re-read the images it splits on`() {
        val usa1 = "/roms/psx/Final Fantasy VII (USA) (Disc 1)/Final Fantasy VII (Disc 1).cue"
        val usa2 = "/roms/psx/Final Fantasy VII (USA) (Disc 2)/Final Fantasy VII (Disc 2).cue"
        val eu1 = "/roms/psx/Final Fantasy VII (Europe) (Disc 1)/Final Fantasy VII (Disc 1).cue"
        val eu2 = "/roms/psx/Final Fantasy VII (Europe) (Disc 2)/Final Fantasy VII (Disc 2).cue"
        val reader = CountingRegionReader { g ->
            if (g.romPath.orEmpty().contains("(USA)")) GameRegion.NTSC_U else GameRegion.PAL
        }

        val assigned = builder.assign(
            listOf(game(usa1), game(usa2), game(eu1), game(eu2)),
            reader,
        ) { null }

        assertEquals(mapOf(usa1 to 1, usa2 to 1, eu1 to 1, eu2 to 1), reader.calls)
        assertEquals(2, assigned.mapNotNull { it.discSetKey }.distinct().size)
    }

    @Test
    fun `a stored region survives an undetectable read and is not re-read`() {
        val path = "/roms/psx/Final Fantasy VII (Disc 1)/Final Fantasy VII (Disc 1).cue"
        val stored = game(path).copy(region = GameRegion.NTSC_U)
        val reader = CountingRegionReader { null }

        val assigned = builder.assign(listOf(stored), reader) { null }

        assertEquals(mapOf(path to 1), reader.calls)
        assertEquals(GameRegion.NTSC_U, assigned.single().region)
    }
}
