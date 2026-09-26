package com.psplauncher.feature.xmb.ui

import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameCollection
import com.psplauncher.core.domain.model.MemoryCard
import com.psplauncher.core.domain.repository.GameRepository
import com.psplauncher.core.data.repository.CollectionRepository
import com.psplauncher.core.data.repository.MemoryCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal const val PICKER_COLLECTIONS_HEADER = "COLLECTIONS_HEADER"
internal fun pickerPlatformId(platformId: String) = "platform_$platformId"
internal fun pickerGameId(gameId: Long) = "game_$gameId"
internal fun pickerCollectionId(collectionId: Long) = "collection_$collectionId"

data class GamePickerState(
    val platformGroups: List<PlatformGameGroup> = emptyList(),
    val pcShortcuts: List<GameCollection> = emptyList(),
    val selectedGameIds: Set<Long> = emptySet(),
    val selectedCollectionIds: Set<Long> = emptySet(),
    val platformExpandedStates: Map<String, Boolean> = emptyMap(),
    val isLoading: Boolean = false,
    val selectedItemId: String? = null,
)

data class PlatformGameGroup(
    val platform: MemoryCard,
    val games: List<Game>,
    val isExpanded: Boolean = true,
    val selectedCount: Int = 0,
) {
    val isAllSelected: Boolean get() = selectedCount == games.size && games.isNotEmpty()
}

@HiltViewModel
class GamePickerViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val collectionRepository: CollectionRepository,
    private val memoryCardRepository: MemoryCardRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GamePickerState(isLoading = true, selectedItemId = null))
    val state: StateFlow<GamePickerState> = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                memoryCardRepository.observeEnabled().combine(gameRepository.observeAll()) { cards, allGames ->
                    Pair(cards, allGames)
                }.collect { (cards, allGames) ->
                    try {
                        val collections = try {
                            collectionRepository.getAll()
                        } catch (e: Exception) {
                            emptyList()
                        }

                        val platformGroups = cards.mapNotNull { card ->
                            val platformGames = allGames.filter {
                                it.platformId == card.platformId &&
                                    it.contentType == com.psplauncher.core.domain.model.GameContentType.GAME
                            }
                            if (platformGames.isNotEmpty()) {
                                PlatformGameGroup(
                                    platform = card,
                                    games = platformGames,
                                    isExpanded = true,
                                )
                            } else {
                                null
                            }
                        }

                        val allCollections = collections

                        val newExpandedStates = platformGroups.associate { group ->
                            group.platform.platformId to false
                        }

                        val newState = GamePickerState(
                            platformGroups = platformGroups,
                            pcShortcuts = allCollections,
                            isLoading = false,
                            platformExpandedStates = newExpandedStates,
                            selectedItemId = if (_state.value.selectedItemId == null) {
                                platformGroups.firstOrNull()?.platform?.platformId?.let { pickerPlatformId(it) }
                            } else {
                                _state.value.selectedItemId
                            }
                        )

                        _state.update { newState }
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing picker data")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading picker data")
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleGameSelection(gameId: Long) {
        _state.update { state ->
            val newSelected = if (gameId in state.selectedGameIds) {
                state.selectedGameIds - gameId
            } else {
                state.selectedGameIds + gameId
            }
            state.copy(selectedGameIds = newSelected)
        }
        updateGroupCounts()
    }

    fun toggleCollectionSelection(collectionId: Long) {
        _state.update { state ->
            val newSelected = if (collectionId in state.selectedCollectionIds) {
                state.selectedCollectionIds - collectionId
            } else {
                state.selectedCollectionIds + collectionId
            }
            state.copy(selectedCollectionIds = newSelected)
        }
    }

    fun togglePlatformAllSelection(platformId: String, selectAll: Boolean) {
        val group = _state.value.platformGroups.firstOrNull { it.platform.platformId == platformId } ?: return

        _state.update { state ->
            val newSelected = if (selectAll) {
                state.selectedGameIds + group.games.map { it.id }
            } else {
                state.selectedGameIds - group.games.map { it.id }.toSet()
            }
            state.copy(selectedGameIds = newSelected)
        }
        updateGroupCounts()
    }

    fun togglePlatformExpanded(platformId: String) {
        _state.update { state ->
            val newExpandedStates = state.platformExpandedStates.toMutableMap()
            newExpandedStates[platformId] = !(newExpandedStates[platformId] ?: true)
            state.copy(platformExpandedStates = newExpandedStates)
        }
    }

    private fun updateGroupCounts() {
        _state.update { state ->
            val updatedGroups = state.platformGroups.map { group ->
                val count = group.games.count { it.id in state.selectedGameIds }
                group.copy(selectedCount = count)
            }
            state.copy(platformGroups = updatedGroups)
        }
    }

    fun getSelectedItems(): Pair<Set<Long>, Set<Long>> {
        return _state.value.selectedGameIds to _state.value.selectedCollectionIds
    }

    fun clearSelection() {
        _state.update { state ->
            state.copy(
                selectedGameIds = emptySet(),
                selectedCollectionIds = emptySet(),
                platformGroups = state.platformGroups.map { it.copy(selectedCount = 0) },
                platformExpandedStates = state.platformGroups.associate { it.platform.platformId to false },
                selectedItemId = state.platformGroups.firstOrNull()?.platform?.platformId?.let { pickerPlatformId(it) },
            )
        }
    }

    fun addNewCollection(name: String) {
        viewModelScope.launch {
            try {
                val newCollection = com.psplauncher.core.domain.model.GameCollection(
                    name = name,
                    gameCount = 0,
                )
            } catch (e: Exception) {
                Timber.e(e, "Error creating collection")
            }
        }
    }

    fun moveSelection(delta: Int) {
        val state = _state.value

        val itemIds = buildPickerItemIds(state)
        if (itemIds.isEmpty()) return

        val currentId = state.selectedItemId
        val currentIndex = if (currentId != null) itemIds.indexOf(currentId) else -1

        val newIndex = if (currentIndex < 0) {
            0
        } else {
            (currentIndex + delta).coerceIn(0, itemIds.size - 1)
        }

        if (newIndex >= 0 && newIndex < itemIds.size) {
            _state.update { it.copy(selectedItemId = itemIds[newIndex]) }
        }
    }

    fun activateSelection() {
        val state = _state.value
        val selectedId = state.selectedItemId ?: return

        for (group in state.platformGroups) {
            if (pickerPlatformId(group.platform.platformId) == selectedId) {
                val selectAll = !group.isAllSelected
                togglePlatformAllSelection(group.platform.platformId, selectAll)
                return
            }

            for (game in group.games) {
                if (pickerGameId(game.id) == selectedId) {
                    toggleGameSelection(game.id)
                    return
                }
            }
        }

        if (selectedId == PICKER_COLLECTIONS_HEADER) {
            return
        }

        for (collection in state.pcShortcuts) {
            if (pickerCollectionId(collection.id) == selectedId) {
                toggleCollectionSelection(collection.id)
                return
            }
        }
    }

    fun toggleSelectedPlatform() {
        val state = _state.value
        val selectedId = state.selectedItemId ?: return

        for (group in state.platformGroups) {
            if (pickerPlatformId(group.platform.platformId) == selectedId) {
                togglePlatformExpanded(group.platform.platformId)
                return
            }
        }
    }
}

internal fun buildPickerItemIds(state: GamePickerState): List<String> {
    val ids = mutableListOf<String>()

    for (group in state.platformGroups) {
        ids.add(pickerPlatformId(group.platform.platformId))
        if (state.platformExpandedStates[group.platform.platformId] == true) {
            for (game in group.games) {
                ids.add(pickerGameId(game.id))
            }
        }
    }

    if (state.pcShortcuts.isNotEmpty()) {
        ids.add(PICKER_COLLECTIONS_HEADER)
        for (collection in state.pcShortcuts) {
            ids.add(pickerCollectionId(collection.id))
        }
    }

    return ids
}
