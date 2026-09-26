package com.psplauncher.core.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.data.database.entity.CollectionEntity
import com.psplauncher.core.data.database.entity.CollectionGameEntity
import com.psplauncher.core.data.database.entity.GameEntity
import com.psplauncher.core.data.database.entity.PlaySessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GameUpsertCascadeTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        PFPDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val gameDao = db.gameDao()
    private val playSessionDao = db.playSessionDao()
    private val collectionDao = db.collectionDao()

    @After fun tearDown() = db.close()

    private fun game(title: String) = GameEntity(
        title = title,
        platformId = "windows",
        romPath = null,
        packageName = "app.gamenative",
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
    )

    @Test
    fun `upsert onto an existing game id cascade-deletes its play sessions and collection membership`() = runTest {
        val gameId = gameDao.upsert(game("Portal 2"))
        playSessionDao.insert(PlaySessionEntity(gameId = gameId, platformId = "windows", launchedAt = 1L))
        val collectionId = collectionDao.insert(CollectionEntity(name = "Favorites"))
        collectionDao.addGame(CollectionGameEntity(collectionId, gameId))

        gameDao.upsert(game("Portal 2").copy(id = gameId, title = "Portal 2 (rescraped)"))

        assertEquals(0, playSessionDao.getAll().size)
        assertEquals(0, collectionDao.getGameIdsInCollection(collectionId).size)
    }

    @Test
    fun `attaching a launcher handle keeps the game's play sessions and collections`() = runTest {
        val gameId = gameDao.upsert(game("Portal 2"))
        playSessionDao.insert(PlaySessionEntity(gameId = gameId, platformId = "windows", launchedAt = 1L))
        val collectionId = collectionDao.insert(CollectionEntity(name = "Favorites"))
        collectionDao.addGame(CollectionGameEntity(collectionId, gameId))

        gameDao.attachLauncherHandle(
            id = gameId,
            packageName = "com.winlator",
            shortcutId = "shortcut-42",
            launchIntentUri = null,
        )

        assertEquals(1, playSessionDao.getAll().size)
        assertEquals(1, collectionDao.getGameIdsInCollection(collectionId).size)

        val after = gameDao.getById(gameId)!!
        assertEquals("com.winlator", after.packageName)
        assertEquals("shortcut-42", after.launchShortcutId)
    }
}
