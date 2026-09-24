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

/**
 * CRUD and ordering for XMB categories (the horizontal bar). Seeds the built-in categories on first
 * run and reconciles their system-defined flags every launch so definition changes reach databases
 * seeded by older builds. Custom categories are fully editable; built-ins are protected from
 * deletion.
 */
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
        categoryDao.deleteById(id)   // category_items rows cascade-delete
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

    // Creates a user category appended after the existing ones. Returns its generated id.
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

    // Swaps position with the adjacent category in the given direction. Returns true on move.
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

    // Seeds built-in categories on first launch — idempotent (INSERT OR IGNORE).
    suspend fun seedBuiltInCategories() {
        categoryDao.insertAll(builtInCategories().map { it.toEntity() })
        Timber.i("Built-in categories seeded")
    }

    // Corrects system-defined flags on built-in rows that already exist. Runs on every
    // launch so changes to built-in definitions (e.g. marking Games as a gaming category)
    // propagate to databases seeded by older builds — without wiping user data. Only the
    // gaming flag is reconciled; user-editable fields (name, position, visibility, icon)
    // are deliberately left alone.
    /**
     * Drops rows for built-ins this build retired, and the category items that hung off them.
     * Returns true when something was removed, so a caller can log or re-read.
     */
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

    /**
     * Puts Last Played immediately left of Game on a database that already has it elsewhere.
     *
     * Reconciliation deliberately never touches a category's position, because position is
     * user-editable and stomping it would undo a reorder on every launch. This is the one
     * exception, and it is a ONE-SHOT: the caller guards it with a DataStore flag so it runs
     * once per install and a later reorder is permanent.
     *
     * It shifts, rather than assigning a fixed number, so it preserves whatever order the user
     * already has: everything at or past Game moves up one, and Last Played takes Game's old
     * slot. Returns false when there is nothing to do.
     */
    suspend fun placeLastPlayedBeforeGames(): Boolean {
        val recent = categoryDao.getById(BuiltInCategory.RECENTLY_PLAYED) ?: return false
        val games = categoryDao.getById(BuiltInCategory.GAMES) ?: return false
        if (recent.position < games.position) return false
        val target = games.position
        // Descending, so no two rows hold the same position even momentarily.
        categoryDao.getAll()
            .filter { it.position >= target && it.id != BuiltInCategory.RECENTLY_PLAYED }
            .sortedByDescending { it.position }
            .forEach { categoryDao.updatePosition(it.id, it.position + 1) }
        categoryDao.updatePosition(recent.id, target)
        Timber.i("Last Played moved to position $target, left of Game")
        return true
    }

    /**
     * Renames the Network column on a database that was seeded while it was still called "Online".
     *
     * [BUILT_IN_CATEGORIES] has said "Network" for some time, but reconciliation adds built-ins
     * with INSERT OR IGNORE and deliberately never touches a name, because a name is user-editable
     * — this database also has Game renamed to "Emulation", and a blanket name sync would stomp it.
     * So the stale name has to be corrected by a targeted one-shot instead.
     *
     * Guarded on the CURRENT name as well as by the caller's DataStore flag: if the user has
     * already renamed this column to anything of their own, there is nothing to correct and we
     * leave it alone. Returns false when there was nothing to do.
     */
    suspend fun renameStaleOnlineColumn(): Boolean {
        val network = categoryDao.getById(NETWORK_CATEGORY_ID) ?: return false
        if (network.name != STALE_NETWORK_NAME) return false
        // Read the new name from the one definition rather than writing a second copy of it here,
        // so this cannot drift from the bar if the column is ever renamed again.
        val liveName = builtInCategories().firstOrNull { it.id == NETWORK_CATEGORY_ID }?.name
            ?: return false
        categoryDao.update(network.copy(name = liveName))
        Timber.i("Renamed stale '$STALE_NETWORK_NAME' column to '$liveName'")
        return true
    }

    suspend fun reconcileBuiltInCategories() {
        // Before seeding the live built-ins: an install from an older build, or one that has just
        // restored an older backup, can still be carrying a retired column.
        pruneRetiredCategories()
        // INSERT OR IGNORE adds built-ins introduced after this DB was first seeded
        // without disturbing existing rows or user edits.
        //
        // A NEW built-in is APPENDED past whatever this database already holds, rather than
        // dropped at the position the constant gives it. Those positions are the fresh-install
        // ORDER, and on an established database that order means nothing — the user has arranged
        // their own bar and the constant's number for a column they have never seen is as likely
        // as not to be one of theirs. Two rows sharing a position is a bar whose order depends on
        // which row the query happens to return first.
        //
        // This is what lets BUILT_IN_CATEGORIES be an order rather than a list of reserved
        // numbers. It used to require every new column to take a position past every old one,
        // which meant the default order could never be rearranged.
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
        // The Network column's id, and the name it shipped under before it was renamed. The id
        // is deliberately not "network" spelled twice: PROTECTED_BUILTINS needs it too.
        const val NETWORK_CATEGORY_ID = "network"
        const val STALE_NETWORK_NAME = "Online"

        // Built-in categories the user may hide/reorder but never delete.
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
