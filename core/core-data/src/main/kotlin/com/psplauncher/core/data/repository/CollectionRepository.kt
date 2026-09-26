package com.psplauncher.core.data.repository

import com.psplauncher.core.data.database.dao.CollectionDao
import com.psplauncher.core.data.database.entity.CollectionEntity
import com.psplauncher.core.data.database.entity.CollectionGameEntity
import com.psplauncher.core.data.database.entity.toDomain
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionRepository @Inject constructor(
    private val collectionDao: CollectionDao,
) {
    fun observeCollections(): Flow<List<GameCollection>> =
        collectionDao.observeAllWithCounts().map { rows ->
            rows.map { it.collection.toDomain(gameCount = it.game_count) }
        }

    fun observeGames(collectionId: Long): Flow<List<Game>> =
        collectionDao.observeGames(collectionId).map { list -> list.map { it.toDomain() } }

    fun observeCollectionIdsForGame(gameId: Long): Flow<List<Long>> =
        collectionDao.observeCollectionIdsForGame(gameId)

    suspend fun getAll(): List<GameCollection> =
        collectionDao.getAllWithCounts().map { it.collection.toDomain(gameCount = it.game_count) }

    suspend fun getCollectionIdsForGame(gameId: Long): List<Long> =
        collectionDao.getCollectionIdsForGame(gameId)

    suspend fun create(name: String, categoryId: String = "games"): Long {
        val now = System.currentTimeMillis()
        val id = collectionDao.insert(
            CollectionEntity(
                name      = name.trim().ifBlank { "Untitled Collection" },
                categoryId = categoryId,
                createdAt = now,
                updatedAt = now,
                sortOrder = collectionDao.maxSortOrder() + 1,
            )
        )
        Timber.i("Collection created: id=$id name=$name categoryId=$categoryId")
        return id
    }

    suspend fun rename(id: Long, name: String) =
        collectionDao.rename(id, name.trim().ifBlank { "Untitled Collection" }, System.currentTimeMillis())

    suspend fun setCategory(id: Long, categoryId: String) {
        collectionDao.setCategory(id, categoryId, System.currentTimeMillis())
        Timber.i("Collection $id moved to category $categoryId")
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        collectionDao.setPinned(id, pinned, System.currentTimeMillis())
        Timber.i("Collection $id pinned=$pinned")
    }

    suspend fun setIcon(id: Long, iconKey: String?) {
        collectionDao.setIcon(id, iconKey, System.currentTimeMillis())
        Timber.i("Collection $id icon=$iconKey")
    }

    suspend fun delete(id: Long) {
        collectionDao.delete(id)
        Timber.i("Collection deleted: id=$id")
    }

    suspend fun addGame(collectionId: Long, gameId: Long) {
        collectionDao.addGame(CollectionGameEntity(collectionId, gameId))
        collectionDao.touch(collectionId, System.currentTimeMillis())
    }

    suspend fun removeGame(collectionId: Long, gameId: Long) {
        collectionDao.removeGame(collectionId, gameId)
        collectionDao.touch(collectionId, System.currentTimeMillis())
    }

    suspend fun toggleGame(collectionId: Long, gameId: Long): Boolean {
        return if (collectionDao.isGameInCollection(collectionId, gameId) > 0) {
            removeGame(collectionId, gameId)
            false
        } else {
            addGame(collectionId, gameId)
            true
        }
    }

    suspend fun move(id: Long, up: Boolean): Boolean {
        val ordered = collectionDao.getAll()
        val index = ordered.indexOfFirst { it.id == id }
        if (index < 0) return false
        val targetIndex = if (up) index - 1 else index + 1
        if (targetIndex !in ordered.indices) return false

        val current = ordered[index]
        val target  = ordered[targetIndex]
        collectionDao.setSortOrder(current.id, target.sortOrder)
        collectionDao.setSortOrder(target.id, current.sortOrder)
        return true
    }
}
