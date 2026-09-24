package com.psplauncher.core.data.repository

import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.core.data.database.dao.PlatformDao
import com.psplauncher.core.data.database.dao.PlaySessionDao
import com.psplauncher.core.data.database.entity.GameEntity
import com.psplauncher.core.domain.model.Game
import io.mockk.coEvery
import io.mockk.coVerify
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

    /** What the repository actually handed the DAO. */
    private suspend fun written(block: suspend () -> Unit): GameEntity {
        val slot = slot<GameEntity>()
        coEvery { dao.upsert(capture(slot)) } returns 1L
        block()
        return slot.captured
    }

    @Test
    fun `a game the library has never seen is stamped now`() = runTest {
        coEvery { dao.dateAddedOf(any()) } returns null
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
        coEvery { dao.dateAddedOf(7L) } returns 1_600_000_000_000L
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
        coEvery { dao.dateAddedOf(7L) } returns 0L
        val entity = written { repo.upsert(game(id = 7)) }
        assertEquals(0L, entity.dateAdded)
    }

    @Test
    fun `a caller that carries its own stamp keeps it`() = runTest {
        // A restore hands back rows that already know their dates; it must not be told otherwise,
        // and the database must not even be asked.
        val entity = written { repo.upsert(game(id = 7, dateAdded = 123L)) }
        assertEquals(123L, entity.dateAdded)
        coVerify(exactly = 0) { dao.dateAddedOf(any()) }
    }
}
