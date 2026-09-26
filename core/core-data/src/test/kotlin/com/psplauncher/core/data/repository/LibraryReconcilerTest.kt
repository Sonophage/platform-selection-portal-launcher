package com.psplauncher.core.data.repository

import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.repository.GameRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryReconcilerTest {
    private lateinit var gameRepository: GameRepository
    private lateinit var reconciler: LibraryReconciler

    private fun game(id: Long, path: String?) =
        Game(id = id, title = "Game $id", platformId = "psx", romPath = path)

    private val crash = game(1L, "/roms/psx/crash.bin")
    private val spyro = game(2L, "/roms/psx/spyro.bin")

    @Before
    fun setUp() {
        gameRepository = mockk(relaxed = true)
        reconciler = LibraryReconciler(gameRepository)
    }

    @Test
    fun `a game whose file is present is marked seen`() = runTest {
        val result = reconciler.reconcile(
            dbGames = listOf(crash),
            present = setOf("/roms/psx/crash.bin"),
            scanErrored = false,
            now = 1_000L,
        )

        assertEquals(1, result.markedSeen)
        assertEquals(0, result.markedMissing)
        assertFalse(result.skipped)
        coVerify(exactly = 1) { gameRepository.markSeen(listOf("/roms/psx/crash.bin"), 1_000L) }
        coVerify(exactly = 0) { gameRepository.markMissing(any()) }
    }

    @Test
    fun `a game whose file is gone is marked missing, not deleted`() = runTest {
        val result = reconciler.reconcile(
            dbGames = listOf(crash, spyro),

            present = setOf("/roms/psx/spyro.bin"),
            scanErrored = false,
        )

        assertEquals(1, result.markedSeen)
        assertEquals(1, result.markedMissing)
        coVerify(exactly = 1) { gameRepository.markSeen(listOf("/roms/psx/spyro.bin"), any()) }
        coVerify(exactly = 1) { gameRepository.markMissing(listOf("/roms/psx/crash.bin")) }

        coVerify(exactly = 0) { gameRepository.delete(any()) }
    }

    @Test
    fun `re-adding the file marks it seen again, clearing the missing flag`() = runTest {
        val missingCrash = crash.copy(isMissing = true)

        val result = reconciler.reconcile(
            dbGames = listOf(missingCrash),
            present = setOf("/roms/psx/crash.bin"),
            scanErrored = false,
            now = 2_000L,
        )

        assertEquals(1, result.markedSeen)
        assertEquals(0, result.markedMissing)

        coVerify(exactly = 1) { gameRepository.markSeen(listOf("/roms/psx/crash.bin"), 2_000L) }
    }

    @Test
    fun `a newly scanned file that is not yet in the library is left alone`() = runTest {
        val result = reconciler.reconcile(
            dbGames = listOf(crash),
            present = setOf("/roms/psx/crash.bin", "/roms/psx/tekken.bin"),
            scanErrored = false,
        )

        assertEquals(1, result.markedSeen)
        assertEquals(0, result.markedMissing)
        coVerify(exactly = 1) { gameRepository.markSeen(listOf("/roms/psx/crash.bin"), any()) }
    }

    @Test
    fun `games without a rom path are ignored entirely`() = runTest {
        val androidApp = game(3L, null)

        val result = reconciler.reconcile(
            dbGames = listOf(androidApp),
            present = setOf("/roms/psx/crash.bin"),
            scanErrored = false,
        )

        assertEquals(0, result.markedSeen)
        assertEquals(0, result.markedMissing)
        coVerify(exactly = 0) { gameRepository.markMissing(any()) }
    }

    @Test
    fun `a null present-set touches nothing`() = runTest {
        val result = reconciler.reconcile(
            dbGames = listOf(crash, spyro),
            present = null,
            scanErrored = false,
        )

        assertTrue(result.skipped)
        assertEquals(0, result.markedMissing)
        coVerify(exactly = 0) { gameRepository.markMissing(any()) }
        coVerify(exactly = 0) { gameRepository.markSeen(any(), any()) }
    }

    @Test
    fun `an errored scan touches nothing even with a plausible present-set`() = runTest {
        val result = reconciler.reconcile(
            dbGames = listOf(crash, spyro),
            present = setOf("/roms/psx/crash.bin"),
            scanErrored = true,
        )

        assertTrue(result.skipped)
        coVerify(exactly = 0) { gameRepository.markMissing(any()) }
        coVerify(exactly = 0) { gameRepository.markSeen(any(), any()) }
    }

    @Test
    fun `an empty survey against a non-empty library touches nothing`() = runTest {
        val result = reconciler.reconcile(
            dbGames = listOf(crash, spyro),
            present = emptySet(),
            scanErrored = false,
        )

        assertTrue(result.skipped)
        assertEquals(0, result.markedMissing)
        coVerify(exactly = 0) { gameRepository.markMissing(any()) }
    }

    @Test
    fun `an empty survey against an empty library is not an error`() = runTest {
        val result = reconciler.reconcile(
            dbGames = emptyList(),
            present = emptySet(),
            scanErrored = false,
        )

        assertFalse(result.skipped)
        assertEquals(0, result.markedSeen)
        assertEquals(0, result.markedMissing)
    }

    @Test
    fun `a library of only package-backed games survives an empty survey`() = runTest {
        val result = reconciler.reconcile(
            dbGames = listOf(game(3L, null), game(4L, null)),
            present = emptySet(),
            scanErrored = false,
        )

        assertFalse(result.skipped)
        assertEquals(0, result.markedMissing)
        coVerify(exactly = 0) { gameRepository.markMissing(any()) }
    }

    @Test
    fun `every game going missing at once is applied when the survey is trustworthy`() = runTest {
        val result = reconciler.reconcile(
            dbGames = listOf(crash, spyro),
            present = setOf("/roms/psx/tekken.bin"),
            scanErrored = false,
        )

        assertFalse(result.skipped)
        assertEquals(0, result.markedSeen)
        assertEquals(2, result.markedMissing)
        coVerify(exactly = 1) {
            gameRepository.markMissing(listOf("/roms/psx/crash.bin", "/roms/psx/spyro.bin"))
        }
    }
}
