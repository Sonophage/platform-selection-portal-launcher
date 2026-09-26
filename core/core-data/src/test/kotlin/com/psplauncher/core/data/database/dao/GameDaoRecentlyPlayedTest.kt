package com.psplauncher.core.data.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.data.database.PFPDatabase
import com.psplauncher.core.data.database.entity.GameEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GameDaoRecentlyPlayedTest {
    private lateinit var db: PFPDatabase
    private lateinit var dao: GameDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PFPDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.gameDao()
    }

    @After
    fun tearDown() = db.close()

    private fun game(
        title: String,
        lastPlayedAt: Long?,
        isMissing: Boolean = false,
    ) = GameEntity(
        title = title,
        platformId = "psx",
        romPath = "/roms/psx/${title.replace(" ", "_")}.cue",
        packageName = null,
        emulatorPackage = null,
        artworkUri = null,
        heroUri = null,
        logoUri = null,
        description = null,
        developer = null,
        publisher = null,
        releaseYear = null,
        genre = null,
        steamGridDbId = null,
        lastPlayedAt = lastPlayedAt,
        isMissing = isMissing,
    )

    @Test
    fun `the newest thing played comes first`() = runTest {
        dao.upsert(game("Chrono Trigger", lastPlayedAt = 3_000L))
        dao.upsert(game("Vagrant Story", lastPlayedAt = 1_000L))
        dao.upsert(game("Suikoden II", lastPlayedAt = 2_000L))

        val recent = dao.observeRecentlyPlayed(limit = 10).first()

        assertEquals(
            listOf("Chrono Trigger", "Suikoden II", "Vagrant Story"),
            recent.map { it.title },
        )
    }

    @Test
    fun `the limit caps the shelf at the newest rows, not an arbitrary two`() = runTest {
        dao.upsert(game("Oldest", lastPlayedAt = 1_000L))
        dao.upsert(game("Middle", lastPlayedAt = 2_000L))
        dao.upsert(game("Newest", lastPlayedAt = 3_000L))

        val recent = dao.observeRecentlyPlayed(limit = 2).first()

        assertEquals(listOf("Newest", "Middle"), recent.map { it.title })
    }

    @Test
    fun `a game that has never been played never appears`() = runTest {
        dao.upsert(game("Never Played", lastPlayedAt = null))
        dao.upsert(game("Played Once", lastPlayedAt = 1_000L))

        val recent = dao.observeRecentlyPlayed(limit = 10).first()

        assertEquals("the played game must be on the shelf", listOf("Played Once"), recent.map { it.title })
    }

    @Test
    fun `a played game whose file has gone missing drops off the shelf`() = runTest {
        dao.upsert(game("Gone", lastPlayedAt = 3_000L, isMissing = true))
        dao.upsert(game("Still Here", lastPlayedAt = 1_000L))

        val recent = dao.observeRecentlyPlayed(limit = 10).first()

        assertTrue("a missing game must not be offered for resuming", recent.none { it.title == "Gone" })
        assertEquals("and the present one is still there", listOf("Still Here"), recent.map { it.title })
    }
}
