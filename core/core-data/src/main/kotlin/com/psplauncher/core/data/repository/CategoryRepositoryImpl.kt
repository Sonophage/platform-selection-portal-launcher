package com.psplauncher.core.data.repository

import com.psplauncher.core.data.database.dao.CategoryDao
import com.psplauncher.core.data.database.entity.CategoryItemEntity
import com.psplauncher.core.data.database.entity.toDomain
import com.psplauncher.core.data.database.entity.toEntity
import com.psplauncher.core.domain.model.BUILT_IN_CATEGORIES
import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.seededPositions
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
) {
    private fun builtInCategories(): List<Category> = BUILT_IN_CATEGORIES

    fun observeVisible(): Flow<List<Category>> =
        categoryDao.observeVisible().map { categoryEntities ->
            categoryEntities.map { it.toDomain() }
        }

    fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { it.map { entity -> entity.toDomain() } }

    suspend fun upsert(category: Category) =
        categoryDao.upsert(category.toEntity())

    suspend fun delete(id: String) {
        if (id in PROTECTED_BUILTINS) {
            Timber.w("Attempted to delete built-in category '$id' — blocked")
            return
        }
        categoryDao.deleteById(id)
    }

    fun isProtected(id: String): Boolean = id in PROTECTED_BUILTINS

    suspend fun updatePosition(id: String, position: Int) =
        categoryDao.updatePosition(id, position)

    suspend fun setVisible(id: String, visible: Boolean) =
        categoryDao.setVisible(id, visible)

    suspend fun setGamingCategory(id: String, isGaming: Boolean) {
        val existing = categoryDao.getById(id) ?: return
        categoryDao.update(existing.copy(isGamingCategory = isGaming))
    }

    suspend fun rename(id: String, name: String) {
        val existing = categoryDao.getById(id) ?: return
        categoryDao.update(existing.copy(name = name))
    }

    suspend fun setIcon(id: String, iconKey: String) {
        val existing = categoryDao.getById(id) ?: return
        categoryDao.update(existing.copy(iconKey = iconKey))
    }

    suspend fun createCustomCategory(name: String, iconKey: String, isGamingCategory: Boolean = false): String {
        val maxPosition = categoryDao.getAll().maxOfOrNull { it.position } ?: -1
        val id = "custom_" + name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { System.currentTimeMillis().toString() } + "_" + (maxPosition + 1)
        upsert(
            Category(
                id                 = id,
                name               = name.trim(),
                iconKey            = iconKey,
                type               = CategoryType.MANUAL,
                position           = maxPosition + 1,
                isGamingCategory   = isGamingCategory,
            )
        )
        Timber.i("Custom category created: $id ($name, isGaming=$isGamingCategory)")
        return id
    }

    suspend fun move(id: String, up: Boolean): Boolean {
        val ordered = categoryDao.getAll().sortedBy { it.position }
        val index = ordered.indexOfFirst { it.id == id }
        if (index < 0) return false
        val targetIndex = if (up) index - 1 else index + 1
        if (targetIndex !in ordered.indices) return false
        val current = ordered[index]
        val target  = ordered[targetIndex]
        categoryDao.updatePosition(current.id, target.position)
        categoryDao.updatePosition(target.id, current.position)
        return true
    }

    suspend fun addItemToCategory(categoryId: String, itemId: String, itemType: String, order: Int = 0) =
        categoryDao.addItem(CategoryItemEntity(categoryId, itemId, itemType, order))

    suspend fun removeItemFromCategory(categoryId: String, itemId: String) =
        categoryDao.removeItem(categoryId, itemId)

    fun observeCategoryItems(categoryId: String) =
        categoryDao.observeItemsForCategory(categoryId)

    suspend fun seedBuiltInCategories() {
        categoryDao.insertAll(builtInCategories().map { it.toEntity() })
        Timber.i("Built-in categories seeded")
    }

    suspend fun pruneRetiredCategories(): Boolean {
        var removed = false
        for (id in BuiltInCategory.RETIRED_IDS) {
            if (categoryDao.getById(id) == null) continue
            categoryDao.clearCategory(id)
            categoryDao.deleteById(id)
            removed = true
        }
        return removed
    }

    suspend fun placeLastPlayedBeforeGames(): Boolean {
        val recent = categoryDao.getById(BuiltInCategory.RECENTLY_PLAYED) ?: return false
        val games = categoryDao.getById(BuiltInCategory.GAMES) ?: return false
        if (recent.position < games.position) return false
        val target = games.position

        categoryDao.getAll()
            .filter { it.position >= target && it.id != BuiltInCategory.RECENTLY_PLAYED }
            .sortedByDescending { it.position }
            .forEach { categoryDao.updatePosition(it.id, it.position + 1) }
        categoryDao.updatePosition(recent.id, target)
        Timber.i("Last Played moved to position $target, left of Game")
        return true
    }

    suspend fun renameStaleOnlineColumn(): Boolean {
        val network = categoryDao.getById(NETWORK_CATEGORY_ID) ?: return false
        if (network.name != STALE_NETWORK_NAME) return false

        val liveName = builtInCategories().firstOrNull { it.id == NETWORK_CATEGORY_ID }?.name
            ?: return false
        categoryDao.update(network.copy(name = liveName))
        Timber.i("Renamed stale '$STALE_NETWORK_NAME' column to '$liveName'")
        return true
    }

    suspend fun reconcileBuiltInCategories() {
        pruneRetiredCategories()

        val existing = categoryDao.getAll()
        val seeded = seededPositions(
            defaults = builtInCategories(),
            existingIds = existing.map { it.id }.toSet(),
            highestExisting = existing.maxOfOrNull { it.position },
        )
        categoryDao.insertAll(seeded.map { it.toEntity() })
        for (category in builtInCategories()) {
            categoryDao.setGamingFlag(category.id, category.isGamingCategory)
        }
        Timber.i("Built-in category flags reconciled")
    }

    companion object {
        const val NETWORK_CATEGORY_ID = "network"
        const val STALE_NETWORK_NAME = "Online"

        val PROTECTED_BUILTINS = setOf(
            BuiltInCategory.FAVORITES,
            BuiltInCategory.RECENTLY_PLAYED,
            BuiltInCategory.GAMES,
            BuiltInCategory.ANDROID,
            BuiltInCategory.APP_DRAWER,
            BuiltInCategory.SETTINGS,
            "photos", "music", "videos", NETWORK_CATEGORY_ID,
            BuiltInCategory.LIBRARY,
        )
    }
}
