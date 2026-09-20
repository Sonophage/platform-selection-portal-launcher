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

/**
 * The query behind the Last Played section.
 *
 * It had no coverage at all and no callers either, right up until the section was built on it, so
 * every clause in it was a claim nobody had checked. Each one is a way the shelf could be quietly
 * wrong rather than visibly broken:
 *
 *  - the ORDER BY is the section's entire meaning; reversed, it is a list of what you played
 *    longest ago, and it still looks like a working list;
 *  - the null filter is what keeps a fresh library's shelf empty instead of full of games in
 *    arbitrary id order, which would read as "you played all of these";
 *  - the is_missing filter keeps a game whose file has gone away off a shelf whose whole promise
 *    is that pressing A resumes what you were doing.
 */
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

        // Guard on the guard: the control row has to be there, or "Never Played is absent" is
        // also true of a query that returns nothing at all.
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
