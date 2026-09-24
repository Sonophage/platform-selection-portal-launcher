package com.psplauncher.core.data.repository

import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.core.data.database.dao.PlatformDao
import com.psplauncher.core.data.database.dao.PlaySessionDao
import com.psplauncher.core.data.database.entity.GameEntity
import com.psplauncher.core.domain.model.Game
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * When a game says it entered the library.
 *
 * The rule is one sentence and the whole feature depends on it: **the stamp is written once, on
 * the insert that creates the row, and every later write leaves it alone.** Get it wrong in one
 * direction and a rescan restamps the entire library as new; get it wrong in the other and
 * nothing is ever dated at all.
 *
 * The trap is that `GameDao.upsert` is `@Insert(onConflict = REPLACE)`, which SQLite performs as
 * DELETE-then-INSERT. It does not merge: every column the caller does not carry is destroyed. A
 * scanner builds its entity from the filesystem and cannot know when the row was first written,
 * so the repository has to read the old value back before writing — which is what these pin.
 */
class GameDateAddedTest {

    private val dao = mockk<GameDao>(relaxed = true)
    private val repo = GameRepositoryImpl(dao, mockk<PlaySessionDao>(relaxed = true), mockk<PlatformDao>(relaxed = true))

    private fun game(id: Long = 0, dateAdded: Long? = null) = Game(
        id = id,
        title = "Crisis Core",
        platformId = "psp",
        romPath = "/roms/cc.iso",
        dateAdded = dateAdded,
    )

    private fun entityWith(dateAdded: Long? = null, playState: String? = null) = GameEntity(
        id = 7, title = "Crisis Core", platformId = "psp", romPath = "/roms/cc.iso",
        packageName = null, emulatorPackage = null, artworkUri = null, heroUri = null,
        logoUri = null, description = null, developer = null, publisher = null,
        releaseYear = null, genre = null, steamGridDbId = null,
        dateAdded = dateAdded, playState = playState,
    )

    /** What the repository actually handed the DAO. */
    private suspend fun written(block: suspend () -> Unit): GameEntity {
        val slot = slot<GameEntity>()
        coEvery { dao.upsert(capture(slot)) } returns 1L
        block()
        return slot.captured
    }

    @Test
    fun `a game the library has never seen is stamped now`() = runTest {
        coEvery { dao.getById(any()) } returns null
        val before = System.currentTimeMillis()
        val entity = written { repo.upsert(game()) }
        assertTrue(
            (entity.dateAdded ?: 0L) >= before,
            "a new row was written with dateAdded=${entity.dateAdded}",
        )
    }

    /**
     * The one that matters. A rescan writes every game it finds, through this same call, with an
     * entity built from the ROM — so if the old value is not read back first, every scan moves the
     * whole library to the front of "recently added".
     */
    @Test
    fun `a rescan does not restamp a game that was already here`() = runTest {
        coEvery { dao.getById(7L) } returns entityWith(dateAdded = 1_600_000_000_000L)
        val entity = written { repo.upsert(game(id = 7)) }
        assertEquals(1_600_000_000_000L, entity.dateAdded)
    }

    /**
     * A row that predates the column reads 0 — migration 51 to 52 wrote that deliberately rather
     * than the migration's own instant. 0 has to survive the next rescan exactly like a real
     * stamp, or the first scan after an upgrade dates the entire library to that scan.
     */
    @Test
    fun `zero is a real answer and is not mistaken for missing`() = runTest {
        coEvery { dao.getById(7L) } returns entityWith(dateAdded = 0L)
        val entity = written { repo.upsert(game(id = 7)) }
        assertEquals(0L, entity.dateAdded)
    }

    /**
     * The silent one.
     *
     * A scanner's entity carries play_state = null and REPLACE is a delete and an insert, so
     * without the read-back a rescan clears every mark in the library and says nothing — no error,
     * no toast, just a badge that is no longer there.
     *
     * NOT observed in the wild. I reported it as observed and was wrong: the game I had marked
     * read differently an hour later because the owner had re-marked it, not because a scan ate
     * it. The hazard is in the DAO's contract either way, which is what this pins.
     */
    @Test
    fun `a rescan does not clear a mark the user put on a game`() = runTest {
        coEvery { dao.getById(7L) } returns entityWith(dateAdded = 5L, playState = "COMPLETED")
        val entity = written { repo.upsert(game(id = 7)) }
        assertEquals("COMPLETED", entity.playState)
    }

    @Test
    fun `a caller that carries its own stamp keeps it`() = runTest {
        // A restore hands back rows that already know their dates, and must not be told otherwise.
        //
        // This used to also assert the database was never even asked, which was true while the
        // added-date was the only thing being carried forward. It is not any more: play_state has
        // to be read back on the same write, so the row is fetched whatever the caller brought.
        // A saved query is not worth a silently cleared mark.
        coEvery { dao.getById(7L) } returns entityWith(dateAdded = 999L)
        val entity = written { repo.upsert(game(id = 7, dateAdded = 123L)) }
        assertEquals(123L, entity.dateAdded, "the caller's own value lost to the stored one")
    }
}
