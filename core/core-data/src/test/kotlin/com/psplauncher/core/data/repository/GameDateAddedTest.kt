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

    @Test
    fun `a rescan does not restamp a game that was already here`() = runTest {
        coEvery { dao.getById(7L) } returns entityWith(dateAdded = 1_600_000_000_000L)
        val entity = written { repo.upsert(game(id = 7)) }
        assertEquals(1_600_000_000_000L, entity.dateAdded)
    }

    @Test
    fun `zero is a real answer and is not mistaken for missing`() = runTest {
        coEvery { dao.getById(7L) } returns entityWith(dateAdded = 0L)
        val entity = written { repo.upsert(game(id = 7)) }
        assertEquals(0L, entity.dateAdded)
    }

    @Test
    fun `a rescan does not clear a mark the user put on a game`() = runTest {
        coEvery { dao.getById(7L) } returns entityWith(dateAdded = 5L, playState = "COMPLETED")
        val entity = written { repo.upsert(game(id = 7)) }
        assertEquals("COMPLETED", entity.playState)
    }

    @Test
    fun `a caller that carries its own stamp keeps it`() = runTest {
        coEvery { dao.getById(7L) } returns entityWith(dateAdded = 999L)
        val entity = written { repo.upsert(game(id = 7, dateAdded = 123L)) }
        assertEquals(123L, entity.dateAdded, "the caller's own value lost to the stored one")
    }
}
