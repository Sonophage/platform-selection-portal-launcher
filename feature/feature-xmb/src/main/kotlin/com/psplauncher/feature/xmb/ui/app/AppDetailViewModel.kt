package com.psplauncher.feature.xmb.ui.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.data.repository.CollectionRepository
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.repository.GameRepository
import com.psplauncher.feature.xmb.ui.collection.CollectionPickerOption
import com.psplauncher.feature.xmb.ui.collection.CollectionPickerUi
import com.psplauncher.feature.artwork.api.SgdbArtType
import com.psplauncher.feature.artwork.api.SteamGridDbApi
import com.psplauncher.feature.artwork.api.SgdbApiKeyProvider
import com.psplauncher.feature.artwork.store.ArtworkKind
import com.psplauncher.feature.artwork.store.ArtworkStore
import com.psplauncher.feature.xmb.ui.detail.ArtPickerItem
import com.psplauncher.feature.xmb.ui.detail.ArtworkType
import com.psplauncher.feature.xmb.ui.detail.displayLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class AppDetailOption(val label: String, val isDestructive: Boolean = false) {
    CHANGE_NAME("Change Display Name"),
    CHANGE_ICON("Change Game Icon"),
    CHANGE_BACKGROUND("Change Background"),
    ADD_TO_COLLECTION("Add to Collection"),
    RESET_ARTWORK("Reset All Artwork", isDestructive = true),
    ;

    companion object {
        val OPTIONS_MENU = listOf(CHANGE_NAME, ADD_TO_COLLECTION)

        val ARTWORK_MENU = listOf(CHANGE_ICON, CHANGE_BACKGROUND, RESET_ARTWORK)
    }
}

data class AppDetailUiState(
    val game: Game? = null,
    val isLoading: Boolean = true,

    val mainFocus: Int = 0,

    val showOptions: Boolean = false,
    val showArtworkMenu: Boolean = false,
    val optionsIndex: Int = 0,

    val collectionPicker: CollectionPickerUi = CollectionPickerUi(),

    val showArtworkPicker: Boolean = false,
    val artworkPickerType: ArtworkType = ArtworkType.ICON,
    val artworkPickerLoading: Boolean = false,
    val artworkPickerItems: List<ArtPickerItem> = emptyList(),
    val artworkPickerFocus: Int = 0,
    val artworkPickerError: String? = null,
    val artworkIsProcessing: Boolean = false,
    val artworkMessage: String? = null,
    val artworkPendingLocal: ArtworkType? = null,
    val isEditingName: Boolean = false,
    val nameText: String = "",
    val closed: Boolean = false,
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val collectionRepository: CollectionRepository,
    private val steamGridDb: SteamGridDbApi,
    private val sgdbKeyProvider: SgdbApiKeyProvider,
    private val artworkStore: ArtworkStore,
    private val appCategoryRepository: com.psplauncher.feature.appbar.AppCategoryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    private var collectionCategoryId: String = "games"

    fun prepareForOpen() {
        _uiState.value = AppDetailUiState()
    }

    fun setCollectionCategory(categoryId: String) {
        collectionCategoryId = categoryId
    }

    fun loadApp(gameId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val game = gameRepository.getById(gameId)
            _uiState.update { it.copy(game = game, isLoading = false) }
        }
    }

    fun openArtworkPickerFor(type: ArtworkType) {
        _uiState.update {
            it.copy(
                showArtworkPicker    = true,
                artworkPickerType    = type,
                artworkPickerItems   = emptyList(),
                artworkPickerFocus   = 0,
                artworkPickerError   = null,
                artworkPickerLoading = false,
                artworkMessage       = null,
            )
        }
        loadSgdbForType(type)
    }

    private fun loadSgdbForType(type: ArtworkType) {
        val game = _uiState.value.game ?: return
        val searchName = game.displayTitle
        viewModelScope.launch {
            _uiState.update { it.copy(artworkPickerLoading = true, artworkPickerError = null) }
            val sgdbKey = sgdbKeyProvider.getKey()
            if (sgdbKey.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        artworkPickerLoading = false,
                        artworkPickerError   = "SteamGridDB API key not configured — add one in Artwork Settings",
                    )
                }
                return@launch
            }
            val match = steamGridDb.searchGame(searchName).getOrElse { e ->
                Timber.w(e, "SGDB search failed for '$searchName'")
                _uiState.update {
                    it.copy(artworkPickerLoading = false, artworkPickerError = "Search failed: ${e.message}")
                }
                return@launch
            }.firstOrNull()

            if (match == null) {
                _uiState.update {
                    it.copy(
                        artworkPickerLoading = false,
                        artworkPickerError   = "No results for \"$searchName\" on SteamGridDB",
                    )
                }
                return@launch
            }

            val arts = steamGridDb.getArt(match.id, type.toSgdbArtType()).getOrElse { e ->
                Timber.w(e, "SGDB getArt failed for ${type.displayLabel}")
                _uiState.update {
                    it.copy(artworkPickerLoading = false, artworkPickerError = "Failed to load artwork: ${e.message}")
                }
                return@launch
            }

            val items = arts.map { ArtPickerItem(url = it.url, thumbUrl = it.thumb) }
            if (items.isEmpty()) {
                _uiState.update {
                    it.copy(
                        artworkPickerLoading = false,
                        artworkPickerError   = "No ${type.displayLabel} art found on SteamGridDB",
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(artworkPickerLoading = false, artworkPickerItems = items, artworkPickerFocus = 0)
            }
        }
    }

    fun closeArtworkPicker() {
        _uiState.update { it.copy(showArtworkPicker = false, artworkPickerItems = emptyList()) }
    }

    fun onSgdbArtSelected(url: String) {
        val game = _uiState.value.game ?: return
        val type = _uiState.value.artworkPickerType
        viewModelScope.launch {
            _uiState.update { it.copy(artworkIsProcessing = true, artworkPickerItems = emptyList()) }
            val localPath = artworkStore.saveVersionedFromUrl(game.id, type.toKind(), url)
            if (localPath == null) {
                _uiState.update {
                    it.copy(artworkIsProcessing = false, artworkMessage = "Could not download ${type.displayLabel}")
                }
                return@launch
            }
            saveArtwork(game.id, type, localPath)
            val updated = gameRepository.getById(game.id)
            _uiState.update {
                it.copy(
                    game               = updated ?: it.game,
                    artworkIsProcessing = false,
                    artworkMessage      = "${type.displayLabel} updated",
                    showArtworkPicker   = false,
                )
            }
        }
    }

    fun requestLocalFilePick(type: ArtworkType) {
        _uiState.update { it.copy(artworkPendingLocal = type) }
    }

    fun consumeLocalFilePick() {
        _uiState.update { it.copy(artworkPendingLocal = null) }
    }

    fun onLocalFilePicked(uri: Uri, type: ArtworkType) {
        val game = _uiState.value.game ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(artworkIsProcessing = true) }
            val localPath = artworkStore.saveVersionedFromUri(game.id, type.toKind(), uri)
            if (localPath == null) {
                _uiState.update {
                    it.copy(artworkIsProcessing = false, artworkMessage = "Could not import ${type.displayLabel}")
                }
                return@launch
            }
            saveArtwork(game.id, type, localPath)
            val updated = gameRepository.getById(game.id)
            _uiState.update {
                it.copy(
                    game               = updated ?: it.game,
                    artworkIsProcessing = false,
                    artworkMessage      = "${type.displayLabel} updated",
                    showArtworkPicker   = false,
                )
            }
        }
    }

    fun clearArtwork(type: ArtworkType) {
        val gameId = _uiState.value.game?.id ?: return
        viewModelScope.launch {
            saveArtwork(gameId, type, null)
            val updated = gameRepository.getById(gameId)
            _uiState.update {
                it.copy(
                    game              = updated ?: it.game,
                    artworkMessage    = "${type.displayLabel} reset",
                    showArtworkPicker = false,
                )
            }
        }
    }

    fun clearAllArtwork() {
        val gameId = _uiState.value.game?.id ?: return
        viewModelScope.launch {
            gameRepository.updateIconArt(gameId, null)
            gameRepository.updateHeroArt(gameId, null)
            gameRepository.updateBoxArt(gameId, null)
            val updated = gameRepository.getById(gameId)
            _uiState.update {
                it.copy(game = updated ?: it.game, artworkMessage = "All artwork reset")
            }
        }
    }

    fun startEditingName() {
        val current = _uiState.value.game?.displayTitle ?: ""
        _uiState.update { it.copy(isEditingName = true, nameText = current) }
    }

    fun onNameTextChanged(text: String) {
        _uiState.update { it.copy(nameText = text) }
    }

    fun confirmNameEdit() {
        val gameId = _uiState.value.game?.id ?: return
        val newName = _uiState.value.nameText.trim()
        viewModelScope.launch {
            gameRepository.updateUserTitleOverride(gameId, newName.ifBlank { null })
            val updated = gameRepository.getById(gameId)
            _uiState.update { it.copy(game = updated ?: it.game, isEditingName = false) }
        }
    }

    fun resetNameToDefault() {
        val gameId = _uiState.value.game?.id ?: return
        viewModelScope.launch {
            gameRepository.updateUserTitleOverride(gameId, null)
            val updated = gameRepository.getById(gameId)
            _uiState.update { it.copy(game = updated ?: it.game, isEditingName = false) }
        }
    }

    fun cancelNameEdit() {
        _uiState.update { it.copy(isEditingName = false) }
    }

    fun handleGamepadAction(action: GamepadAction) {
        val s = _uiState.value
        if (s.isEditingName) {
            if (action == GamepadAction.BACK) cancelNameEdit()
            return
        }
        if (s.collectionPicker.visible) {
            handleCollectionPickerInput(action)
            return
        }
        if (s.showArtworkPicker) {
            handlePickerGamepad(action)
            return
        }
        if (s.showOptions || s.showArtworkMenu) {
            handleMenuGamepad(action, if (s.showOptions) AppDetailOption.OPTIONS_MENU else AppDetailOption.ARTWORK_MENU)
            return
        }
        handleMainGamepad(action)
    }

    private fun handleMainGamepad(action: GamepadAction) {
        when (action) {
            GamepadAction.NAVIGATE_LEFT  -> _uiState.update { it.copy(mainFocus = (it.mainFocus - 1).coerceIn(0, MAIN_FOCUS_LAST), artworkMessage = null) }
            GamepadAction.NAVIGATE_RIGHT -> _uiState.update { it.copy(mainFocus = (it.mainFocus + 1).coerceIn(0, MAIN_FOCUS_LAST), artworkMessage = null) }
            GamepadAction.NAVIGATE_UP    -> _uiState.update { it.copy(mainFocus = 0, artworkMessage = null) }
            GamepadAction.NAVIGATE_DOWN  -> _uiState.update { if (it.mainFocus == 0) it.copy(mainFocus = 1, artworkMessage = null) else it }
            GamepadAction.SELECT         -> when (_uiState.value.mainFocus) {
                0    -> launchApp()
                1    -> openOptions()
                else -> openArtworkMenu()
            }

            GamepadAction.OPEN_CONTEXT_MENU -> openOptions()
            GamepadAction.BACK -> close()
            else -> Unit
        }
    }

    private fun handleMenuGamepad(action: GamepadAction, rows: List<AppDetailOption>) {
        when (action) {
            GamepadAction.NAVIGATE_UP   -> _uiState.update { it.copy(optionsIndex = (it.optionsIndex - 1).coerceIn(0, rows.lastIndex.coerceAtLeast(0))) }
            GamepadAction.NAVIGATE_DOWN -> _uiState.update { it.copy(optionsIndex = (it.optionsIndex + 1).coerceIn(0, rows.lastIndex.coerceAtLeast(0))) }
            GamepadAction.SELECT        -> rows.getOrNull(_uiState.value.optionsIndex)?.let(::activateOption)
            GamepadAction.BACK          -> closeMenus()
            else -> Unit
        }
    }

    fun launchApp() {
        val game = _uiState.value.game ?: return
        val pkg = game.packageName
        if (pkg.isNullOrBlank()) {
            _uiState.update { it.copy(artworkMessage = "This app has no launchable package") }
            return
        }
        appCategoryRepository.launch(pkg)
    }

    fun openOptions() = _uiState.update {
        it.copy(showOptions = true, showArtworkMenu = false, optionsIndex = 0, artworkMessage = null)
    }

    fun openArtworkMenu() = _uiState.update {
        it.copy(showArtworkMenu = true, showOptions = false, optionsIndex = 0, artworkMessage = null)
    }

    fun closeMenus() = _uiState.update { it.copy(showOptions = false, showArtworkMenu = false) }

    fun activateOption(option: AppDetailOption) {
        closeMenus()
        when (option) {
            AppDetailOption.ADD_TO_COLLECTION -> openCollectionPicker()
            AppDetailOption.CHANGE_NAME       -> startEditingName()
            AppDetailOption.CHANGE_ICON       -> openArtworkPickerFor(ArtworkType.ICON)
            AppDetailOption.CHANGE_BACKGROUND -> openArtworkPickerFor(ArtworkType.BACKGROUND)
            AppDetailOption.RESET_ARTWORK     -> clearAllArtwork()
        }
    }

    private fun handlePickerGamepad(action: GamepadAction) {
        val items = _uiState.value.artworkPickerItems
        when (action) {
            GamepadAction.NAVIGATE_LEFT -> _uiState.update {
                it.copy(artworkPickerFocus = (it.artworkPickerFocus - 1).coerceAtLeast(0))
            }
            GamepadAction.NAVIGATE_RIGHT -> _uiState.update {
                it.copy(artworkPickerFocus = (it.artworkPickerFocus + 1).coerceAtMost((items.size - 1).coerceAtLeast(0)))
            }
            GamepadAction.SELECT -> {
                val item = items.getOrNull(_uiState.value.artworkPickerFocus) ?: return
                onSgdbArtSelected(item.url)
            }
            GamepadAction.BACK -> closeArtworkPicker()
            else -> Unit
        }
    }

    fun onCollectionsClicked() = openCollectionPicker()

    private fun openCollectionPicker() {
        val gameId = _uiState.value.game?.id ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(collectionPicker = CollectionPickerUi(
                    visible = true,
                    options = buildCollectionOptions(gameId),
                    selectedIndex = 0,
                ))
            }
        }
    }

    private suspend fun buildCollectionOptions(gameId: Long): List<CollectionPickerOption> {
        val memberOf = collectionRepository.getCollectionIdsForGame(gameId).toSet()
        return collectionRepository.getAll().map {
            CollectionPickerOption(id = it.id, name = it.name, checked = it.id in memberOf)
        }
    }

    fun onCollectionRowClick(index: Int) {
        _uiState.update { it.copy(collectionPicker = it.collectionPicker.copy(selectedIndex = index)) }
        activateCollectionRow()
    }

    private fun moveCollectionPicker(delta: Int) {
        _uiState.update {
            val cp = it.collectionPicker

            if (cp.rowCount <= 0) return@update it
            it.copy(collectionPicker = cp.copy(selectedIndex = (cp.selectedIndex + delta).coerceIn(0, cp.rowCount - 1)))
        }
    }

    private fun activateCollectionRow() {
        val cp = _uiState.value.collectionPicker
        val gameId = _uiState.value.game?.id ?: return
        if (cp.isCreateRow) {
            _uiState.update { it.copy(collectionPicker = it.collectionPicker.copy(showCreateDialog = true, createText = "")) }
            return
        }
        val option = cp.options.getOrNull(cp.selectedIndex) ?: return
        viewModelScope.launch {
            collectionRepository.toggleGame(option.id, gameId)
            _uiState.update { it.copy(collectionPicker = it.collectionPicker.copy(options = buildCollectionOptions(gameId))) }
        }
    }

    fun onCreateCollectionTextChanged(text: String) {
        _uiState.update { it.copy(collectionPicker = it.collectionPicker.copy(createText = text)) }
    }

    fun confirmCreateCollection() {
        val gameId = _uiState.value.game?.id ?: return
        val name = _uiState.value.collectionPicker.createText
        if (name.isBlank()) { cancelCreateCollection(); return }
        viewModelScope.launch {
            val id = collectionRepository.create(name, collectionCategoryId)
            collectionRepository.addGame(id, gameId)
            _uiState.update {
                it.copy(collectionPicker = it.collectionPicker.copy(
                    showCreateDialog = false,
                    createText = "",
                    options = buildCollectionOptions(gameId),
                ))
            }
        }
    }

    fun cancelCreateCollection() {
        _uiState.update { it.copy(collectionPicker = it.collectionPicker.copy(showCreateDialog = false, createText = "")) }
    }

    fun closeCollectionPicker() {
        _uiState.update { it.copy(collectionPicker = CollectionPickerUi()) }
    }

    private fun handleCollectionPickerInput(action: GamepadAction) {
        if (_uiState.value.collectionPicker.showCreateDialog) {
            when (action) {
                GamepadAction.SELECT -> confirmCreateCollection()
                GamepadAction.BACK   -> cancelCreateCollection()
                else                 -> Unit
            }
            return
        }
        when (action) {
            GamepadAction.NAVIGATE_UP   -> moveCollectionPicker(-1)
            GamepadAction.NAVIGATE_DOWN -> moveCollectionPicker(+1)
            GamepadAction.SELECT        -> activateCollectionRow()
            GamepadAction.BACK          -> closeCollectionPicker()
            else -> Unit
        }
    }

    fun close() {
        _uiState.update { it.copy(closed = true) }
    }

    private companion object {
        const val MAIN_FOCUS_LAST = 2
    }

    private suspend fun saveArtwork(gameId: Long, type: ArtworkType, path: String?) {
        when (type) {
            ArtworkType.ICON       -> gameRepository.updateIconArt(gameId, path)
            ArtworkType.HERO       -> gameRepository.updateHeroArt(gameId, path)
            ArtworkType.BACKGROUND -> gameRepository.updateBoxArt(gameId, path)
        }
    }

    private fun ArtworkType.toSgdbArtType() = when (this) {
        ArtworkType.ICON       -> SgdbArtType.GRID
        ArtworkType.HERO       -> SgdbArtType.HERO
        ArtworkType.BACKGROUND -> SgdbArtType.HERO
    }

    private fun ArtworkType.toKind() = when (this) {
        ArtworkType.ICON       -> ArtworkKind.ICON
        ArtworkType.HERO       -> ArtworkKind.HERO
        ArtworkType.BACKGROUND -> ArtworkKind.BACKGROUND
    }
}
