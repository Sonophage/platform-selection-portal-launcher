package com.psplauncher.core.data.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.data.database.PFPDatabase
import com.psplauncher.core.data.database.entity.GameEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GameDaoMarkOpenedTest {
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

    private fun appRow(
        title: String,
        pkg: String,
        playedAt: Long?,
        playTime: Long = 0L,
    ) = GameEntity(
        title = title,
        platformId = "android",
        romPath = null,
        packageName = pkg,
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
        contentType = "ANDROID_APP",
        lastPlayedAt = playedAt,
        totalPlayTimeMillis = playTime,
    )

    private suspend fun app(title: String, pkg: String, playedAt: Long?): Long =
        dao.upsert(appRow(title, pkg, playedAt))

    @Test
    fun `an app that is opened again goes to the front`() = runTest {
        app("Spotify", "com.spotify.music", playedAt = 3_000L)
        app("Moon+ Reader", "com.flyersoft.moonreader", playedAt = 2_000L)
        val gameNative = app("GameNative", "app.gamenative", playedAt = 1_000L)

        assertEquals(
            listOf("Spotify", "Moon+ Reader", "GameNative"),
            dao.observeRecentlyPlayed(10).first().map { it.title },
        )

        dao.markOpened(gameNative, playedAt = 9_000L)

        assertEquals(
            "re-opening must reorder the shelf, which is the whole point of it",
            listOf("GameNative", "Spotify", "Moon+ Reader"),
            dao.observeRecentlyPlayed(10).first().map { it.title },
        )
    }

    @Test
    fun `the stamp does not touch the play counter`() = runTest {
        val id = dao.upsert(
            appRow("GameNative", "app.gamenative", playedAt = null, playTime = 7_200_000L)
        )

        dao.markOpened(id, playedAt = 9_000L)

        val row = dao.getById(id)!!
        assertEquals(9_000L, row.lastPlayedAt)
        assertEquals(7_200_000L, row.totalPlayTimeMillis)
    }

    @Test
    fun `an app the shelf has never seen appears once it is opened`() = runTest {
        val id = app("Opera", "com.opera.browser", playedAt = null)
        assertEquals(emptyList<String>(), dao.observeRecentlyPlayed(10).first().map { it.title })

        dao.markOpened(id, playedAt = 5_000L)

        assertEquals(listOf("Opera"), dao.observeRecentlyPlayed(10).first().map { it.title })
    }
}
