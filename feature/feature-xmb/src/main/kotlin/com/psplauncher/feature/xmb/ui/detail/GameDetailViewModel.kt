package com.psplauncher.feature.xmb.ui.detail

import com.psplauncher.core.domain.model.PlatformIds.ANDROID as ANDROID_PLATFORM_ID

import com.psplauncher.core.domain.model.PlatformIds.WINDOWS as WINDOWS_PLATFORM_ID

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.data.database.dao.PlatformDao
import com.psplauncher.core.data.database.entity.PlatformEntity
import com.psplauncher.core.data.repository.CollectionRepository
import com.psplauncher.core.domain.model.Game
import com.psplauncher.feature.xmb.ui.collection.CollectionPickerOption
import com.psplauncher.feature.xmb.ui.collection.CollectionPickerUi
import com.psplauncher.core.domain.repository.GameRepository
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.navigation.NavigationLogger
import com.psplauncher.core.navigation.NavigationNode
import com.psplauncher.feature.artwork.api.ArtworkRepository
import com.psplauncher.feature.artwork.match.MetadataApply
import com.psplauncher.feature.artwork.match.MetadataApplyPolicy
import com.psplauncher.feature.artwork.match.MetadataField
import com.psplauncher.feature.artwork.match.MetadataFieldRow
import com.psplauncher.feature.artwork.match.MetadataPreset
import com.psplauncher.core.domain.model.EmulatorProfile
import com.psplauncher.core.domain.model.IntentType
import com.psplauncher.feature.artwork.store.ArtworkKind
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psplauncher.core.data.datastore.pfpDataStore
import kotlinx.coroutines.flow.first
import com.psplauncher.feature.artwork.store.ArtworkStore
import com.psplauncher.feature.launcher.EmulatorIntentResolver
import com.psplauncher.feature.launcher.EmulatorProfileRepository
import com.psplauncher.feature.launcher.LaunchDispatchResult
import com.psplauncher.feature.launcher.ResolvedLaunch
import com.psplauncher.feature.launcher.byLaunchPreference
import com.psplauncher.feature.launcher.supportsPlatform
import com.psplauncher.feature.xmb.viewmodel.MenuGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class ArtworkType { ICON, HERO, BACKGROUND }

val ArtworkType.displayLabel: String
    get() = when (this) {
        ArtworkType.ICON       -> "Game Icon"
        ArtworkType.HERO       -> "Hero Banner"
        ArtworkType.BACKGROUND -> "Background"
    }

private val KEY_VIDEO_DEFAULT_PLAYER = stringPreferencesKey("video_default_player")

data class DetailMedia(val uri: String, val isVideo: Boolean)

data class ArtPickerItem(
    val url: String,
    val thumbUrl: String? = null,
    val label: String? = null,
)

data class GameDetailUiState(
    val game: Game? = null,
    val platform: PlatformEntity? = null,

    val discMembers: List<Game> = emptyList(),
    val selectedDiscId: Long? = null,
    val isLoading: Boolean = true,
    val isEditingNote: Boolean = false,
    val noteText: String = "",

    val artAccentArgb: Long? = null,
    val isFetchingArtwork: Boolean = false,
    val artworkMessage: String? = null,
    val launchError: String? = null,

    val videoUri: String? = null,
    val hasManual: Boolean = false,
    val showVideoPlayer: Boolean = false,

    val detailMedia: List<DetailMedia> = emptyList(),
    val imageViewerUri: String? = null,

    val navFocusKey: String? = null,

    val cursorVisible: Boolean = true,

    val descriptionExpanded: Boolean = false,

    val panelPage: DetailPanelPage = DetailPanelPage.LOGO,

    val showOptions: Boolean = false,

    val optionsIndex: Int = 0,

    val showDetailsMenu: Boolean = false,
    val detailsIndex: Int = 0,

    val resolvedLaunch: ResolvedLaunch? = null,
    val confirmRemove: Boolean = false,
    val actionMessage: String? = null,
    val closed: Boolean = false,

    val isEditingTitle: Boolean = false,
    val titleText: String = "",

    val manualViewerUri: String? = null,
    val manualPage: Int = 0,
    val manualPageCount: Int = 0,
    val manualScrollSteps: Int = 0,

    val showArtworkStudio: Boolean = false,

    val metadataPreview: MetadataPreviewUi? = null,

    val showEmulatorPicker: Boolean = false,
    val emulatorPickerOptions: List<EmulatorProfile> = emptyList(),
    val emulatorPickerIndex: Int = 0,

    val collectionPicker: CollectionPickerUi = CollectionPickerUi(),
) {
    val selectedDisc: Game?
        get() = discMembers.firstOrNull { it.id == selectedDiscId } ?: game

    val showDiscPicker: Boolean
        get() = discMembers.size > 1

    val panelContent: DetailPanelContent?
        get() = game?.let { g ->
            detailPanelContentFor(
                game = g,
                platformName = platform?.name ?: g.platformId.uppercase(),
                media = detailMedia,
                videoUri = videoUri,
            )
        }

    val effectivePanelPage: DetailPanelPage
        get() = panelContent?.let { resolvePanelPage(panelPage, it.pages) } ?: DetailPanelPage.LOGO

    val isPackageBacked: Boolean
        get() = game != null && game.romPath == null && game.packageName != null

    val showEmulatorAction: Boolean
        get() = game != null && !isPackageBacked

    val showInfoBand: Boolean
        get() {
            val loaded = game ?: return false
            return listOf(
                loaded.releaseYear,
                loaded.developer?.takeIf { it.isNotBlank() },
                loaded.publisher?.takeIf { it.isNotBlank() },
                loaded.genre?.takeIf { it.isNotBlank() },
                loaded.lastPlayedAt,
                loaded.totalPlayTimeMillis.takeIf { it > 0 },
                resolvedLaunch?.profile?.name,
            ).any { it != null }
        }

    val visibleActions: List<DetailAction>
        get() = DetailAction.entries.filter { action ->
            when (action) {
                DetailAction.EMULATOR -> !isPackageBacked
                DetailAction.EXPORT   -> game?.platformId == WINDOWS_PLATFORM_ID
                else                  -> true
            }
        }

    val visibleDetailRows: List<DetailQuickAction>
        get() = DetailQuickAction.entries.filter { it != DetailQuickAction.MANUAL || hasManual }
}

data class MetadataPreviewUi(
    val loading: Boolean = true,
    val applying: Boolean = false,

    val failed: Boolean = false,
    val current: Map<MetadataField, Any?> = emptyMap(),
    val presets: List<MetadataPreset> = emptyList(),
    val presetIndex: Int = 0,
    val policy: MetadataApplyPolicy = MetadataApplyPolicy.FILL_MISSING_ONLY,
    val chosen: Set<MetadataField> = emptySet(),

    val focus: Int = 0,
) {
    val preset: MetadataPreset? get() = presets.getOrNull(presetIndex)

    val nothingFound: Boolean get() = !loading && presets.isEmpty()
    val rows: List<MetadataFieldRow> get() = preset?.let { MetadataApply.rows(current, it) }.orEmpty()
    val willWrite: Set<MetadataField>
        get() = preset?.let { MetadataApply.plan(current, it, policy, chosen).keys }.orEmpty()
    val applyIndex: Int get() = rows.size
}

enum class DetailAction(val label: String, val group: MenuGroup) {
    FAVORITE("Favorite", MenuGroup.LIBRARY),
    COLLECTIONS("Collections", MenuGroup.LIBRARY),

    EMULATOR("Emulator", MenuGroup.SETTINGS),
    SAVES("Saves", MenuGroup.SETTINGS),
    MANUAL("Manual", MenuGroup.SETTINGS),
    RENAME("Edit Title", MenuGroup.SETTINGS),
    EDIT("Edit Note", MenuGroup.SETTINGS),
    ARTWORK("Artwork", MenuGroup.SETTINGS),
    METADATA("Update Metadata", MenuGroup.SETTINGS),
    REFRESH("Refresh", MenuGroup.SETTINGS),
    LOCATION("Open Location", MenuGroup.SETTINGS),
    EXPORT("Export Game", MenuGroup.SETTINGS),

    REMOVE("Remove", MenuGroup.REMOVE),
}

enum class DetailQuickAction(val label: String) {
    FAVORITE("Favorite"),
    ARTWORK("Artwork"),
    MANUAL("Manual"),
    OPTIONS("Options"),
}

fun sectionHeadings(actions: List<DetailAction>): List<String?> =
    actions.mapIndexed { index, action ->
        action.group.heading.takeIf { action.group != actions.getOrNull(index - 1)?.group }
    }

internal const val DEFAULT_EMULATOR_SENTINEL = "default"

private const val DISC_KEY_PREFIX = "game-detail:disc:"
private const val MEDIA_KEY_PREFIX = "game-detail:media:"

const val MAX_PAGE_SCROLL_STEPS = 20

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val platformDao: PlatformDao,
    private val collectionRepository: CollectionRepository,
    private val profileRepository: EmulatorProfileRepository,
    private val intentResolver: EmulatorIntentResolver,
    private val artworkRepository: ArtworkRepository,
    private val artworkAccent: com.psplauncher.core.data.repository.ArtworkAccent,
    private val artworkStore: ArtworkStore,
    private val artworkRecordDao: com.psplauncher.core.data.database.dao.ArtworkRecordDao,
    private val menuSound: com.psplauncher.core.ui.sound.MenuSoundPlayer,
    private val launcherShortcutRepository: com.psplauncher.feature.appbar.LauncherShortcutRepository,
    private val launchDispatcher: com.psplauncher.feature.launcher.LaunchDispatcher,
    private val launchResolver: com.psplauncher.feature.launcher.GameLaunchResolver,
    private val pcGameExporter: com.psplauncher.feature.settings.pc.PcGameExporter,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    private val nav = GameDetailNav(logger = NavigationLogger { Timber.w(it) })

    init {
        nav.onActivate = ::activateNode

        viewModelScope.launch {
            uiState
                .map { navContentOf(it) }
                .distinctUntilChanged()
                .collect { content ->
                    nav.updateContent(content)

                    if (content.loaded) nav.markReady()
                    publishNav()
                }
        }

        viewModelScope.launch {
            uiState
                .map { topModalId(it) }
                .distinctUntilChanged()
                .collect {
                    syncNavStack()
                    publishNav()
                }
        }

        viewModelScope.launch {
            uiState.collect { s ->
                val active = unlessModal(nav) ?: return@collect
                nav.updateModalNodes(modalNodesFor(active), preferredModalFocus(active, s))
                publishNav()
            }
        }
    }

    private fun unlessModal(nav: GameDetailNav): String? =
        if (nav.isModalActive) nav.activeContextId else null

    private fun navContentOf(s: GameDetailUiState): GameDetailNavContent {
        val game = s.game
        val loaded = game != null && !s.isLoading
        return GameDetailNavContent(
            gameId      = game?.id ?: 0L,
            loaded      = loaded,

            showEmulatorControls = loaded && s.showEmulatorAction,
            discIds     = if (s.showDiscPicker) s.discMembers.map { it.id } else emptyList(),
            mediaIds    = s.detailMedia.map { mediaStableId(it) },

            onMediaPage = loaded && s.effectivePanelPage == DetailPanelPage.GALLERY,
        )
    }

    private fun mediaIndexFor(key: String): Int =
        _uiState.value.detailMedia.indexOfFirst { GameDetailKeys.media(mediaStableId(it)) == key }

    private fun activateNode(key: String) {
        when {
            key == GameDetailKeys.LAUNCH -> { Timber.d("Controller SELECT activated Launch"); launch() }
            key == GameDetailKeys.DETAILS -> openDetailsMenu()

            key == GameDetailKeys.FAVORITE -> toggleFavorite()
            key == GameDetailKeys.OPTIONS -> openOptions()
            key.startsWith(DISC_KEY_PREFIX) ->
                key.removePrefix(DISC_KEY_PREFIX).toLongOrNull()?.let(::selectDisc)
            key.startsWith(MEDIA_KEY_PREFIX) -> {
                val index = mediaIndexFor(key)
                if (index >= 0) openMediaAt(index)
            }
            else -> Unit
        }
    }

    private fun toggleDescriptionExpanded() =
        _uiState.update { it.copy(descriptionExpanded = !it.descriptionExpanded) }

    private fun finishInput() {
        syncNavStack()
        publishNav()
    }

    private fun publishNav() {
        _uiState.update { s ->
            var next = s.copy(navFocusKey = nav.focusedKey, cursorVisible = nav.cursorVisible)
            val focus = next.navFocusKey
            if (next.showOptions) {
                val index = next.visibleActions.indexOfFirst { GameDetailKeys.option(it.name) == focus }
                if (index >= 0) next = next.copy(optionsIndex = index)
            }
            if (next.showDetailsMenu) {
                val index = next.visibleDetailRows.indexOfFirst { GameDetailKeys.detailsRow(it.name) == focus }
                if (index >= 0) next = next.copy(detailsIndex = index)
            }
            if (next.showEmulatorPicker) {
                val index = next.emulatorPickerOptions.indexOfFirst { GameDetailKeys.emulatorPick(it.id) == focus }
                if (index >= 0) next = next.copy(emulatorPickerIndex = index)
            }
            if (next.collectionPicker.visible) {
                val index = collectionIndexFor(next, focus)
                if (index >= 0) next = next.copy(collectionPicker = next.collectionPicker.copy(selectedIndex = index))
            }
            val preview = next.metadataPreview
            if (preview != null) {
                val index = metadataFocusFor(preview, focus)
                if (index >= 0) next = next.copy(metadataPreview = preview.copy(focus = index))
            }
            next
        }
    }

    private fun topModalId(s: GameDetailUiState): String? = when {
        s.showArtworkStudio -> GameDetailKeys.MODAL_ARTWORK_STUDIO
        s.imageViewerUri != null -> GameDetailKeys.MODAL_IMAGE_VIEWER
        s.showVideoPlayer -> GameDetailKeys.MODAL_VIDEO_PLAYER
        s.manualViewerUri != null -> GameDetailKeys.MODAL_MANUAL_VIEWER
        s.confirmRemove -> GameDetailKeys.MODAL_CONFIRM_REMOVE
        s.isEditingNote -> GameDetailKeys.MODAL_NOTE_EDITOR
        s.isEditingTitle -> GameDetailKeys.MODAL_TITLE_EDITOR
        s.metadataPreview != null -> GameDetailKeys.MODAL_METADATA
        s.showEmulatorPicker -> GameDetailKeys.MODAL_EMULATOR_PICKER
        s.collectionPicker.visible -> GameDetailKeys.MODAL_COLLECTION_PICKER
        s.showOptions -> GameDetailKeys.MODAL_OPTIONS

        s.showDetailsMenu -> GameDetailKeys.MODAL_DETAILS
        else -> null
    }

    private fun syncNavStack() {
        val s = _uiState.value
        val target = topModalId(s)
        val active = if (nav.isModalActive) nav.activeContextId else null
        if (target == active) {
            if (target != null) {
                nav.updateModalNodes(modalNodesFor(target), preferredModalFocus(target, s))
            }
            return
        }
        while (nav.isModalActive) nav.popModal()
        if (target != null) {
            nav.pushModal(target, modalNodesFor(target), preferredModalFocus(target, s))
        }
    }

    private fun modalNodesFor(contextId: String): List<NavigationNode> {
        val s = _uiState.value
        return when (contextId) {
            GameDetailKeys.MODAL_OPTIONS -> s.visibleActions.map { action ->
                NavigationNode(GameDetailKeys.option(action.name), onSelect = { activateAction(action) })
            }
            GameDetailKeys.MODAL_DETAILS -> s.visibleDetailRows.map { row ->
                NavigationNode(GameDetailKeys.detailsRow(row.name), onSelect = { activateQuickAction(row) })
            }
            GameDetailKeys.MODAL_EMULATOR_PICKER -> s.emulatorPickerOptions.map { profile ->
                NavigationNode(GameDetailKeys.emulatorPick(profile.id), onSelect = { confirmEmulatorPick(profile.id) })
            }
            GameDetailKeys.MODAL_COLLECTION_PICKER -> buildList {
                s.collectionPicker.options.forEach { option ->
                    add(
                        NavigationNode(
                            GameDetailKeys.collectionRow(option.id),
                            onSelect = { toggleCollection(option.id) },
                        ),
                    )
                }
                add(NavigationNode(GameDetailKeys.COLLECTION_CREATE_ROW, onSelect = { startCreateCollection() }))
            }
            GameDetailKeys.MODAL_METADATA -> buildList {
                s.metadataPreview?.let { preview ->
                    preview.rows.forEach { row ->
                        add(
                            NavigationNode(
                                GameDetailKeys.metadataField(row.field.name),
                                onSelect = { toggleMetadataField(row.field) },
                            ),
                        )
                    }
                    if (preview.preset != null) {
                        add(NavigationNode(GameDetailKeys.METADATA_APPLY, onSelect = { applyMetadataPreview() }))
                    }
                }
            }

            GameDetailKeys.MODAL_CONFIRM_REMOVE -> listOf(
                NavigationNode(GameDetailKeys.CONFIRM_CANCEL, onSelect = { cancelRemove() }),
                NavigationNode(GameDetailKeys.CONFIRM_REMOVE, onSelect = { confirmRemoveGame() }),
            )

            else -> emptyList()
        }
    }

    private fun preferredModalFocus(contextId: String, s: GameDetailUiState): String? = when (contextId) {
        GameDetailKeys.MODAL_OPTIONS -> s.visibleActions.firstOrNull()?.let { GameDetailKeys.option(it.name) }
        GameDetailKeys.MODAL_DETAILS -> s.visibleDetailRows.firstOrNull()?.let { GameDetailKeys.detailsRow(it.name) }
        GameDetailKeys.MODAL_EMULATOR_PICKER ->
            s.emulatorPickerOptions.getOrNull(s.emulatorPickerIndex)?.let { GameDetailKeys.emulatorPick(it.id) }
        GameDetailKeys.MODAL_COLLECTION_PICKER -> collectionKeyAt(s, s.collectionPicker.selectedIndex)

        GameDetailKeys.MODAL_METADATA -> GameDetailKeys.METADATA_APPLY

        GameDetailKeys.MODAL_CONFIRM_REMOVE -> GameDetailKeys.CONFIRM_CANCEL
        else -> null
    }

    private fun collectionKeyAt(s: GameDetailUiState, index: Int): String? {
        val options = s.collectionPicker.options
        return if (index >= options.size) GameDetailKeys.COLLECTION_CREATE_ROW
        else options.getOrNull(index)?.let { GameDetailKeys.collectionRow(it.id) }
    }

    private fun collectionIndexFor(s: GameDetailUiState, focus: String?): Int {
        if (focus == null) return -1
        val options = s.collectionPicker.options
        if (focus == GameDetailKeys.COLLECTION_CREATE_ROW) return options.size
        val optionIndex = options.indexOfFirst { GameDetailKeys.collectionRow(it.id) == focus }
        return if (optionIndex >= 0) optionIndex else -1
    }

    private fun metadataFocusFor(preview: MetadataPreviewUi, focus: String?): Int {
        if (focus == null) return -1
        if (focus == GameDetailKeys.METADATA_APPLY) return preview.applyIndex
        val rowIndex = preview.rows.indexOfFirst { GameDetailKeys.metadataField(it.field.name) == focus }
        return if (rowIndex >= 0) rowIndex else -1
    }

    private fun close() = _uiState.update { it.copy(closed = true) }

    private fun closeActiveModal() {
        val s = _uiState.value
        when {
            s.metadataPreview != null -> closeMetadataPreview()
            s.showEmulatorPicker -> closeEmulatorPicker()
            s.collectionPicker.visible -> closeCollectionPicker()
            s.showOptions -> closeOptions()
            s.showDetailsMenu -> closeDetailsMenu()

            s.confirmRemove -> cancelRemove()
            else -> close()
        }
    }

    fun onNodeTapped(key: String) {
        nav.touch(key)
        finishInput()
    }

    fun onTouchInput() {
        nav.markTouchInput()
        publishNav()
    }

    fun onPageLaidOut() {
        nav.markReady()
        publishNav()
    }

    internal fun focusableNodeKeys(): Set<String> = nav.reachableKeys()

    fun onNodeGeometry(geometry: Map<String, Float>) {
        nav.reportGeometry(geometry)
        publishNav()
    }

    fun onScrollAlignmentChanged(aligning: Boolean) {
        if (aligning) nav.beginRecoveryLock() else nav.endRecoveryLock()
    }

    fun prepareForOpen() {
        _uiState.update {
            it.copy(
                closed = false,
                showOptions = false,
                confirmRemove = false,
                isEditingNote = false,
                isEditingTitle = false,
                actionMessage = null,
                launchError = null,

                showEmulatorPicker = false,
                metadataPreview = null,
                collectionPicker = CollectionPickerUi(),
                manualViewerUri = null,
                imageViewerUri = null,
                showVideoPlayer = false,
                showArtworkStudio = false,
            )
        }
        syncNavStack()
        publishNav()
    }

    private suspend fun resolveArtAccent(game: Game) {
        val accent = artworkAccent.of(game.heroUri, game.artworkUri, game.boxArtUri, game.iconUri)

        _uiState.update { if (it.game?.id == game.id) it.copy(artAccentArgb = accent) else it }
    }

    fun loadGame(id: Long, requestedDiscId: Long? = null) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    closed = false,
                    showOptions = false,
                    confirmRemove = false,
                    isEditingNote = false,
                    isEditingTitle = false,
                    showEmulatorPicker = false,
                    metadataPreview = null,
                    collectionPicker = CollectionPickerUi(),
                    manualViewerUri = null,
                    imageViewerUri = null,
                    showVideoPlayer = false,

                    artAccentArgb = null,
                )
            }
            syncNavStack()
            publishNav()
            val game     = gameRepository.getById(id)

            val discMembers = game?.discSetKey
                ?.let { gameRepository.getDiscSetMembers(it) }
                ?.takeIf { it.isNotEmpty() }
                ?.sortedWith(
                    compareBy<Game> { it.discNumber == null }
                        .thenBy { it.discNumber ?: Int.MAX_VALUE }
                        .thenBy { it.id },
                )
                ?: listOfNotNull(game)
            val selectedDisc = discMembers.firstOrNull { it.id == requestedDiscId }
                ?: discMembers.firstOrNull { it.isDiscPrimary }
                ?: discMembers.firstOrNull()
            val platform = game?.let { platformDao.getById(it.platformId) }
            val resolvedLaunch = game?.let { resolveLaunchProfile(it, platform).getOrNull() }

            val videoUris = game
                ?.let { g ->
                    artworkStore.findAll(g.id, ArtworkKind.VIDEO)
                        .ifEmpty { listOfNotNull(artworkStore.find(g.id, ArtworkKind.ICON1)) }
                }
                ?: emptyList()
            val screenshotUris = game?.let { artworkStore.findAll(it.id, ArtworkKind.SCREENSHOT) } ?: emptyList()
            _uiState.update {
                it.copy(
                    game              = game,
                    platform          = platform,
                    discMembers       = discMembers,
                    selectedDiscId    = selectedDisc?.id,
                    noteText          = game?.userNote ?: "",
                    resolvedLaunch    = resolvedLaunch,

                    videoUri          = videoUris.firstOrNull(),
                    detailMedia       = if (game == null) emptyList() else buildList {
                        videoUris.forEach { add(DetailMedia(it, isVideo = true)) }
                        screenshotUris.forEach { add(DetailMedia(it, isVideo = false)) }
                        artworkStore.find(game.id, ArtworkKind.TITLESCREEN)?.let { add(DetailMedia(it, isVideo = false)) }
                    },

                    hasManual         = game?.let { g -> manualPath(g.id) } != null,
                    showVideoPlayer   = false,
                    imageViewerUri    = null,
                    isLoading         = false,

                    descriptionExpanded = false,
                    manualViewerUri   = null,
                    showOptions       = false,
                    optionsIndex      = 0,
                    confirmRemove     = false,
                    isEditingNote     = false,
                    isEditingTitle    = false,
                    actionMessage     = null,
                    launchError       = null,
                    closed            = false,
                )
            }

            game?.let { g -> viewModelScope.launch { resolveArtAccent(g) } }
            finishInput()
        }
    }

    fun selectDisc(id: Long) {
        val state = _uiState.value
        if (state.discMembers.any { it.id == id }) {
            _uiState.update { it.copy(selectedDiscId = id, actionMessage = null, launchError = null) }

            val gameId = state.game?.id
            if (gameId != null && state.discMembers.size > 1) {
                viewModelScope.launch {
                    gameRepository.setPreferredDisc(gameId, id)
                }
            }
        }
    }

    fun handleGamepadAction(action: GamepadAction) {
        syncNavStack()
        val s = _uiState.value

        if (s.imageViewerUri != null) {
            if (action == GamepadAction.BACK || action == GamepadAction.SELECT) closeImageViewer()
            finishInput()
            return
        }
        if (s.showArtworkStudio) return
        if (s.showVideoPlayer) {
            if (action == GamepadAction.BACK || action == GamepadAction.SELECT) closeVideoPlayer()
            finishInput()
            return
        }
        if (s.manualViewerUri != null) {
            handleManualViewerInput(action)
            finishInput()
            return
        }
        if (s.collectionPicker.showCreateDialog) {
            when (action) {
                GamepadAction.SELECT -> confirmCreateCollection()
                GamepadAction.BACK   -> cancelCreateCollection()
                else                 -> Unit
            }
            finishInput()
            return
        }
        if (s.isEditingNote) {
            if (action == GamepadAction.BACK) cancelNote()
            finishInput()
            return
        }
        if (s.isEditingTitle) {
            if (action == GamepadAction.BACK) cancelTitleEdit()
            finishInput()
            return
        }
        if (s.metadataPreview != null) {
            handleMetadataPreviewInput(action)
            finishInput()
            return
        }

        when (action) {
            GamepadAction.OPEN_CONTEXT_MENU -> if (!nav.isModalActive) openOptions()
            GamepadAction.BACK -> if (nav.isModalActive) closeActiveModal() else close()

            GamepadAction.HOME -> Unit

            GamepadAction.PREV_CATEGORY -> if (!nav.isModalActive) stepPanelPage(-1)
            GamepadAction.NEXT_CATEGORY -> if (!nav.isModalActive) stepPanelPage(+1)
            else -> nav.handleAction(action)
        }
        finishInput()
    }

    private fun stepPanelPage(delta: Int) = _uiState.update { s ->
        val pages = s.panelContent?.pages ?: return@update s
        s.copy(panelPage = com.psplauncher.feature.xmb.ui.detail.stepPanelPage(s.effectivePanelPage, pages, delta))
    }

    fun onPanelPageTapped(page: DetailPanelPage) = _uiState.update { it.copy(panelPage = page) }

    fun openArtworkManager() {
        _uiState.update { it.copy(showArtworkStudio = true) }
    }

    fun onArtworkStudioClosed() {
        _uiState.update { it.copy(showArtworkStudio = false) }
        val id = _uiState.value.game?.id ?: return
        loadGame(id)
    }

    fun openDetailsMenu() =
        _uiState.update { it.copy(showDetailsMenu = true, detailsIndex = 0, actionMessage = null) }

    fun closeDetailsMenu() = _uiState.update { it.copy(showDetailsMenu = false) }

    fun onDetailsRowTapped(row: DetailQuickAction) {
        if (!nav.touch(GameDetailKeys.detailsRow(row.name))) activateQuickAction(row)
        finishInput()
    }

    fun activateQuickAction(row: DetailQuickAction) {
        _uiState.update { it.copy(showDetailsMenu = false) }
        when (row) {
            DetailQuickAction.FAVORITE -> toggleFavorite()
            DetailQuickAction.ARTWORK  -> openArtworkManager()
            DetailQuickAction.MANUAL   -> openManual()
            DetailQuickAction.OPTIONS  -> openOptions()
        }
    }

    fun openOptions()  = _uiState.update { it.copy(showOptions = true, optionsIndex = 0, actionMessage = null) }
    fun closeOptions() = _uiState.update { it.copy(showOptions = false) }

    fun onOptionClicked(action: DetailAction) {
        _uiState.update { it.copy(optionsIndex = it.visibleActions.indexOf(action).coerceAtLeast(0)) }
        activateAction(action)
    }

    fun onOptionRowTapped(action: DetailAction) {
        if (!nav.touch(GameDetailKeys.option(action.name))) activateAction(action)
        finishInput()
    }

    fun onPlayClicked()    { Timber.d("Play clicked"); launch() }
    fun onOptionsClicked() = openOptions()

    fun activateAction(action: DetailAction) {
        _uiState.update { it.copy(showOptions = false) }
        when (action) {
            DetailAction.FAVORITE  -> toggleFavorite()
            DetailAction.COLLECTIONS -> openCollectionPicker()
            DetailAction.ARTWORK   -> openArtworkManager()
            DetailAction.SAVES     -> showActionMessage("Save management isn't available yet")
            DetailAction.EMULATOR  -> openEmulatorPicker()
            DetailAction.MANUAL    -> openManual()
            DetailAction.REFRESH   -> fetchArtwork()
            DetailAction.METADATA  -> openMetadataPreview()
            DetailAction.EXPORT    -> exportGame()
            DetailAction.RENAME    -> startEditTitle()
            DetailAction.EDIT      -> startEditNote()
            DetailAction.LOCATION  -> showActionMessage(
                _uiState.value.game?.romPath
                    ?: _uiState.value.game?.packageName?.let { "Package: $it" }
                    ?: "No file location on record"
            )
            DetailAction.REMOVE    -> _uiState.update { it.copy(confirmRemove = true) }
        }
    }

    private fun showActionMessage(msg: String) = _uiState.update { it.copy(actionMessage = msg) }

    private fun exportGame() {
        val gameId = _uiState.value.game?.id ?: return
        viewModelScope.launch {
            val report = runCatching { pcGameExporter.exportGame(gameId) }
                .onFailure { Timber.e(it, "Export Game failed for gameId=$gameId") }
                .getOrNull()
            showActionMessage(report?.message ?: "Export failed — see the log.")
        }
    }

    fun onManualClicked() = openManual()

    fun onVideoClicked() {
        val uri = _uiState.value.videoUri ?: run {
            _uiState.update { it.copy(actionMessage = "No video snap — enable Download Video Snaps and re-scrape") }
            return
        }
        viewModelScope.launch {
            val playerPackage = runCatching {
                context.pfpDataStore.data.first()[KEY_VIDEO_DEFAULT_PLAYER]
            }.getOrNull()?.takeIf { it.isNotBlank() }
            if (playerPackage != null) {
                val sent = runCatching {
                    val content = if (uri.startsWith("content://")) android.net.Uri.parse(uri)
                    else androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", java.io.File(uri),
                    )
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(content, "video/mp4")
                            setPackage(playerPackage)
                            addFlags(
                                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    )
                    true
                }.getOrDefault(false)
                if (sent) return@launch
                Timber.w("External video player '$playerPackage' failed — using built-in")
            }
            _uiState.update { it.copy(showVideoPlayer = true) }
        }
    }

    fun closeVideoPlayer() = _uiState.update { it.copy(showVideoPlayer = false) }

    fun openMediaAt(index: Int) {
        val media = _uiState.value.detailMedia.getOrNull(index) ?: return
        if (media.isVideo) onVideoClicked()
        else _uiState.update { it.copy(imageViewerUri = media.uri) }
    }

    fun closeImageViewer() = _uiState.update { it.copy(imageViewerUri = null) }

    private fun openManual() {
        val game = _uiState.value.game ?: return
        viewModelScope.launch {
            val path = manualPath(game.id)
            if (path == null) {
                showActionMessage("No manual available for this game")
                return@launch
            }

            _uiState.update {
                it.copy(
                    showOptions = false,
                    manualViewerUri = path,
                    manualPage = 0,
                    manualPageCount = 0,
                    manualScrollSteps = 0,
                )
            }
        }
    }

    private suspend fun manualPath(gameId: Long): String? =
        artworkStore.find(gameId, ArtworkKind.MANUAL)
            ?: artworkRecordDao.get(gameId, ArtworkKind.MANUAL.name)?.documentUri

    fun closeManualViewer() = _uiState.update { it.copy(manualViewerUri = null) }

    fun setManualPageCount(count: Int) = _uiState.update {
        it.copy(manualPageCount = count, manualPage = it.manualPage.coerceIn(0, (count - 1).coerceAtLeast(0)))
    }

    fun manualPrevPage() = _uiState.update {
        it.copy(manualPage = (it.manualPage - 1).coerceAtLeast(0), manualScrollSteps = 0)
    }

    fun manualNextPage() = _uiState.update {
        it.copy(
            manualPage = (it.manualPage + 1).coerceAtMost((it.manualPageCount - 1).coerceAtLeast(0)),
            manualScrollSteps = 0,
        )
    }

    private fun handleManualViewerInput(action: GamepadAction) {
        when (action) {
            GamepadAction.NAVIGATE_LEFT  -> manualPrevPage()
            GamepadAction.NAVIGATE_RIGHT -> manualNextPage()
            GamepadAction.NAVIGATE_DOWN  -> _uiState.update {
                it.copy(manualScrollSteps = (it.manualScrollSteps + 1).coerceAtMost(MAX_PAGE_SCROLL_STEPS))
            }
            GamepadAction.NAVIGATE_UP    -> _uiState.update {
                it.copy(manualScrollSteps = (it.manualScrollSteps - 1).coerceAtLeast(0))
            }
            GamepadAction.BACK           -> closeManualViewer()
            else -> Unit
        }
    }

    fun dismissActionMessage() = _uiState.update { it.copy(actionMessage = null) }

    fun requestRemove() = _uiState.update { it.copy(confirmRemove = true) }
    fun cancelRemove()  = _uiState.update { it.copy(confirmRemove = false) }
    fun confirmRemoveGame() {
        val game = _uiState.value.game ?: return
        viewModelScope.launch {
            gameRepository.delete(game.id)
            _uiState.update { it.copy(confirmRemove = false, closed = true) }
        }
    }

    fun launch(playSound: Boolean = true) {
        val selectedGame = _uiState.value.selectedDisc ?: run {
            Timber.w("Play requested before game detail state was loaded")
            _uiState.update { it.copy(actionMessage = null, launchError = "Game is still loading") }
            return
        }

        if (selectedGame.isMissing) {
            Timber.i("Launch refused for missing game: ${selectedGame.title}")
            _uiState.update {
                it.copy(
                    actionMessage = null,
                    launchError = "File not found on the last scan. Reconnect the card or restore " +
                        "the file, then rescan.",
                )
            }
            return
        }

        if (playSound) {
            menuSound.play(com.psplauncher.core.ui.sound.MenuSound.SELECT)
        }
        _uiState.update {
            it.copy(
                launchError = null,
                actionMessage = "Launching ${selectedGame.displayTitle}...",
            )
        }
        viewModelScope.launch {
            val game = gameRepository.getById(selectedGame.id) ?: selectedGame
            val platform = platformDao.getById(game.platformId) ?: _uiState.value.platform
            Timber.d(
                "Launch requested: gameId=${game.id}, title=${game.title}, platform=${game.platformId}, rom=${game.romPath ?: game.packageName.orEmpty()}"
            )

            if (game.shortcutId != null && game.packageName != null) {
                launcherShortcutRepository.launch(game.packageName!!, game.shortcutId!!)
                    .onSuccess { _uiState.update { it.copy(actionMessage = null) } }
                    .onFailure { e ->
                        Timber.e(e, "Shortcut launch failed: ${game.packageName}/${game.shortcutId}")

                        launchDispatcher.recordPreflightFailure(
                            game   = game,
                            resolved = null,
                            reason = "Couldn't launch: ${e.message}",
                            offerRecovery = true,
                        )
                        _uiState.update {
                            it.copy(actionMessage = null, launchError = "Couldn't launch: ${e.message}")
                        }
                    }
                return@launch
            }

            if (game.launchIntentUri != null) {
                runCatching {
                    val parsed = Intent.parseUri(game.launchIntentUri, Intent.URI_INTENT_SCHEME)
                    com.psplauncher.core.common.security.ShortcutIntentSanitizer
                        .sanitize(parsed, context.packageManager)
                        ?: error("Captured shortcut is not safe to launch")
                }.onSuccess { intent ->
                    dispatchLaunch(intent, game, null)
                }.onFailure { e ->
                    Timber.e(e, "Stored-intent launch failed for gameId=${game.id}")
                    launchDispatcher.recordPreflightFailure(game, null, "Couldn't launch: ${e.message}", offerRecovery = false)
                    _uiState.update {
                        it.copy(actionMessage = null, launchError = "Couldn't launch: ${e.message}")
                    }
                }
                return@launch
            }

            if (game.romPath.isNullOrBlank() && !game.packageName.isNullOrBlank()) {
                val nativeResult = intentResolver.resolveNativeApp(game)
                nativeResult.onFailure { e ->
                    Timber.w(e, "Native game launch failed: gameId=${game.id}, package=${game.packageName}")
                    _uiState.update {
                        it.copy(
                            actionMessage = null,
                            launchError = e.message ?: "Could not launch ${game.displayTitle}",
                        )
                    }
                    return@launch
                }
                val nativeIntent = nativeResult.getOrNull() ?: return@launch
                Timber.i(
                    "Launching native gameId=${game.id}, title=${game.title}, package=${game.packageName}, intent=${nativeIntent.toUri(Intent.URI_INTENT_SCHEME)}"
                )
                dispatchLaunch(nativeIntent, game, null)
                return@launch
            }

            val resolved = resolveLaunchProfile(game, platform)
            if (resolved.isFailure) {
                val reason = resolved.exceptionOrNull()?.message ?: "Could not resolve emulator for ${game.displayTitle}"
                Timber.w(
                    "Launch blocked: gameId=${game.id}, title=${game.title}, platform=${game.platformId}, reason=$reason"
                )
                launchDispatcher.recordPreflightFailure(game, null, reason, offerRecovery = false)
                _uiState.update { it.copy(actionMessage = null, launchError = reason) }
                return@launch
            }
            val resolvedLaunch = resolved.getOrThrow()
            val profile = resolvedLaunch.profile
            Timber.d(
                "Launch emulator resolved: gameId=${game.id}, platform=${game.platformId}, emulatorId=${profile.id}, emulatorName=${profile.name}, source=${resolvedLaunch.source.name}"
            )

            val result = intentResolver.resolve(game, profile)
            result.onFailure { e ->
                Timber.w(
                    e,
                    "Launch failed before startActivity: gameId=${game.id}, platform=${game.platformId}, emulatorId=${profile.id}, source=${resolvedLaunch.source.name}"
                )
                launchDispatcher.recordPreflightFailure(
                    game, resolvedLaunch, e.message ?: "Could not launch ${profile.name}", offerRecovery = false,
                )
                _uiState.update {
                    it.copy(
                        actionMessage = null,
                        launchError = e.message ?: "Could not launch ${profile.name}",
                    )
                }
                return@launch
            }
            val intent = result.getOrNull() ?: return@launch
            Timber.i(
                "Launching gameId=${game.id}, title=${game.title}, platform=${game.platformId}, emulatorId=${profile.id}, emulator=${profile.name}, source=${resolvedLaunch.source.name}, core=${resolvedLaunch.corePath.orEmpty()}, rom=${game.romPath.orEmpty()}, intent=${intent.toUri(Intent.URI_INTENT_SCHEME)}"
            )
            dispatchLaunch(intent, game, resolvedLaunch)
        }
    }

    private suspend fun dispatchLaunch(
        intent: Intent,
        game: Game,
        resolved: ResolvedLaunch?,
    ) {
        when (val result = launchDispatcher.launch(game, resolved, intent)) {
            is LaunchDispatchResult.Rejected -> {
                _uiState.update {
                    it.copy(actionMessage = null, launchError = result.message)
                }
            }
            LaunchDispatchResult.Accepted -> {
                _uiState.update { it.copy(actionMessage = null) }
            }
        }
    }

    fun onLaunchFailed(message: String) {
        _uiState.update { it.copy(actionMessage = null, launchError = message) }
    }

    fun requestLaunchHelp() {
        val game = _uiState.value.game ?: return
        val error = _uiState.value.launchError ?: return
        viewModelScope.launch {
            launchDispatcher.requestRecovery(game, _uiState.value.resolvedLaunch, error)
        }
    }

    private suspend fun resolveLaunchProfile(
        game: Game,
        platform: PlatformEntity? = null,
    ): Result<ResolvedLaunch> = launchResolver.resolve(game, platform)

    private fun systemDefaultOption(platformId: String) = EmulatorProfile(
        id = DEFAULT_EMULATOR_SENTINEL,
        name = "Use system default",
        packageName = "",
        intentType = IntentType.ACTION_VIEW,
        supportedPlatformIds = listOf(platformId),
    )

    private fun openEmulatorPicker() {
        val game = _uiState.value.game ?: return
        val options = profileRepository.getInstalledProfiles()
            .filter { it.isAvailable && it.supportsPlatform(game.platformId) }
            .byLaunchPreference()
        if (options.isEmpty()) {
            showActionMessage("No emulators installed for ${game.platformId.uppercase()}")
            return
        }

        val rows = listOf(systemDefaultOption(game.platformId)) + options
        val stored = game.emulatorPackage
        val currentIndex = if (stored != null) {
            rows.indexOfFirst { it.id == stored || it.packageName == stored }.coerceAtLeast(0)
        } else 0
        _uiState.update {
            it.copy(
                showOptions           = false,
                showEmulatorPicker    = true,
                emulatorPickerOptions = rows,
                emulatorPickerIndex   = currentIndex,
            )
        }
    }

    fun closeEmulatorPicker() {
        _uiState.update { it.copy(showEmulatorPicker = false) }
    }

    fun requestChangeEmulator() = openEmulatorPicker()

    fun onEmulatorPickTapped(profileId: String) {
        if (!nav.touch(GameDetailKeys.emulatorPick(profileId))) confirmEmulatorPick(profileId)
        finishInput()
    }

    fun confirmEmulatorPick(profileId: String) {
        val game = _uiState.value.game ?: return
        viewModelScope.launch {
            gameRepository.setPreferredEmulator(game.id, profileId.takeIf { it != DEFAULT_EMULATOR_SENTINEL })
            val updated = gameRepository.getById(game.id)
            val profile = profileRepository.getInstalledProfiles().firstOrNull { it.id == profileId }

            val resolved = updated?.let {
                resolveLaunchProfile(it, _uiState.value.platform).getOrNull()
            }
            _uiState.update {
                it.copy(
                    game               = updated ?: it.game,
                    resolvedLaunch     = resolved,
                    showEmulatorPicker = false,
                    actionMessage      = if (profileId == DEFAULT_EMULATOR_SENTINEL) {
                        "Using the system default emulator"
                    } else {
                        profile?.let { p -> "Emulator set to ${p.name}" }
                    },
                )
            }
        }
    }

    private fun openCollectionPicker() {
        val gameId = _uiState.value.game?.id ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showOptions = false,
                    collectionPicker = CollectionPickerUi(
                        visible = true,
                        options = buildCollectionOptions(gameId),
                        selectedIndex = 0,
                    ),
                )
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
        val key = collectionKeyAt(_uiState.value, index)

        if (key == null || !nav.touch(key)) activateCollectionRowAt(index)
        finishInput()
    }

    private fun activateCollectionRowAt(index: Int) {
        val options = _uiState.value.collectionPicker.options
        if (index >= options.size) {
            startCreateCollection()
            return
        }
        options.getOrNull(index)?.let { toggleCollection(it.id) }
    }

    private fun toggleCollection(collectionId: Long) {
        val gameId = _uiState.value.game?.id ?: return
        viewModelScope.launch {
            collectionRepository.toggleGame(collectionId, gameId)
            _uiState.update { it.copy(collectionPicker = it.collectionPicker.copy(options = buildCollectionOptions(gameId))) }
        }
    }

    private fun startCreateCollection() {
        _uiState.update {
            it.copy(collectionPicker = it.collectionPicker.copy(showCreateDialog = true, createText = ""))
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
            val id = collectionRepository.create(name)
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

    fun toggleFavorite() {
        val game = _uiState.value.game ?: return
        viewModelScope.launch {
            val next = !game.isFavorite
            gameRepository.setFavorite(game.id, next)
            _uiState.update { it.copy(game = game.copy(isFavorite = next)) }
        }
    }

    fun startEditNote() {
        _uiState.update { it.copy(isEditingNote = true, noteText = it.game?.userNote ?: "") }
    }

    fun onNoteChanged(text: String) = _uiState.update { it.copy(noteText = text) }

    fun saveNote() {
        val game = _uiState.value.game ?: return
        val note = _uiState.value.noteText.trim().ifEmpty { null }
        viewModelScope.launch {
            gameRepository.updateNote(game.id, note)
            _uiState.update { it.copy(game = game.copy(userNote = note), isEditingNote = false) }
        }
    }

    fun cancelNote() {
        _uiState.update { it.copy(isEditingNote = false, noteText = _uiState.value.game?.userNote ?: "") }
    }

    fun startEditTitle() {
        val game = _uiState.value.game ?: return
        _uiState.update { it.copy(isEditingTitle = true, titleText = game.displayTitle) }
    }

    fun onTitleChanged(text: String) = _uiState.update { it.copy(titleText = text) }

    fun saveTitle() {
        val game = _uiState.value.game ?: return
        val newTitle = _uiState.value.titleText.trim().ifEmpty { null }
        viewModelScope.launch {
            gameRepository.updateUserTitleOverride(game.id, newTitle)
            val updated = gameRepository.getById(game.id)
            _uiState.update {
                it.copy(
                    game           = updated ?: it.game,
                    isEditingTitle = false,
                    actionMessage  = if (newTitle != null) "Title updated to \"$newTitle\"" else "Title reset to default",
                )
            }
        }
    }

    fun resetTitleToDefault() {
        val game = _uiState.value.game ?: return
        viewModelScope.launch {
            gameRepository.updateUserTitleOverride(game.id, null)
            val updated = gameRepository.getById(game.id)
            _uiState.update {
                it.copy(
                    game           = updated ?: it.game,
                    isEditingTitle = false,
                    actionMessage  = "Title reset to \"${updated?.displayTitle ?: game.title}\"",
                )
            }
        }
    }

    fun cancelTitleEdit() {
        _uiState.update { it.copy(isEditingTitle = false, titleText = _uiState.value.game?.displayTitle ?: "") }
    }

    fun fetchArtwork() {
        val game = _uiState.value.game ?: return
        if (_uiState.value.isFetchingArtwork) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingArtwork = true, artworkMessage = null) }
            val result = artworkRepository.fetchArtworkForGame(game.id, game.title)
            val updated = gameRepository.getById(game.id)

            artworkRepository.evictFromImageCache((artRefsOf(game) + artRefsOf(updated)).toSet())
            _uiState.update {
                it.copy(
                    game              = updated ?: it.game,
                    isFetchingArtwork = false,
                    artworkMessage    = when {
                        result.success -> "Artwork updated"
                        result.skipped -> "Already has artwork"
                        else           -> result.errorMessage ?: "Artwork fetch failed"
                    },
                )
            }
        }
    }

    fun dismissArtworkMessage() = _uiState.update { it.copy(artworkMessage = null) }
    fun dismissLaunchError()    = _uiState.update { it.copy(launchError = null) }

    private var metadataPreviewGeneration = 0L

    fun openMetadataPreview() {
        val game = _uiState.value.game ?: return
        if (_uiState.value.metadataPreview != null) return
        val generation = ++metadataPreviewGeneration
        _uiState.update { it.copy(showOptions = false, metadataPreview = MetadataPreviewUi(), actionMessage = null) }
        viewModelScope.launch {
            val outcome = runCatching { artworkRepository.fetchMetadataPreview(game.id) }
                .onFailure { Timber.w(it, "Metadata preview failed for game ${game.id}") }
            val preview = outcome.getOrNull()
            if (generation != metadataPreviewGeneration) return@launch
            _uiState.update { s ->
                if (s.metadataPreview == null) return@update s
                if (preview == null || preview.presets.isEmpty()) {
                    return@update s.copy(
                        metadataPreview = MetadataPreviewUi(loading = false, failed = outcome.isFailure),
                    )
                }
                val loaded = MetadataPreviewUi(
                    loading = false,
                    current = preview.current,
                    presets = preview.presets,
                    chosen  = MetadataApply.changedFields(preview.current, preview.presets.first()),
                )

                s.copy(metadataPreview = loaded.copy(focus = loaded.applyIndex))
            }
        }
    }

    fun closeMetadataPreview() {
        metadataPreviewGeneration++
        _uiState.update { it.copy(metadataPreview = null) }
    }

    fun selectMetadataPolicy(policy: MetadataApplyPolicy) = updateMetadataPreview { it.copy(policy = policy) }

    fun cycleMetadataPolicy(delta: Int) = updateMetadataPreview { p ->
        val all = MetadataApplyPolicy.entries
        p.copy(policy = all[(p.policy.ordinal + delta).mod(all.size)])
    }

    fun cycleMetadataSource(delta: Int) = updateMetadataPreview { p ->
        if (p.presets.size < 2) return@updateMetadataPreview p
        val index = (p.presetIndex + delta).mod(p.presets.size)
        val next = p.copy(presetIndex = index, chosen = MetadataApply.changedFields(p.current, p.presets[index]))
        next.copy(focus = next.focus.coerceIn(0, next.applyIndex))
    }

    fun toggleMetadataField(field: MetadataField) = updateMetadataPreview { p ->
        p.copy(
            policy = MetadataApplyPolicy.CHOOSE_FIELDS,
            chosen = if (field in p.chosen) p.chosen - field else p.chosen + field,
        )
    }

    fun applyMetadataPreview() {
        val game = _uiState.value.game ?: return
        val p = _uiState.value.metadataPreview ?: return
        if (p.loading || p.applying) return

        val preset = p.preset ?: return closeMetadataPreview()
        if (p.policy == MetadataApplyPolicy.KEEP_CURRENT) {
            closeMetadataPreview()
            showActionMessage("Kept current metadata")
            return
        }
        _uiState.update { it.copy(metadataPreview = p.copy(applying = true)) }
        viewModelScope.launch {
            val written = runCatching { artworkRepository.applyMetadata(game.id, preset, p.policy, p.chosen) }
                .onFailure { Timber.w(it, "Metadata apply failed for game ${game.id}") }
            val updated = gameRepository.getById(game.id)
            metadataPreviewGeneration++
            _uiState.update {
                it.copy(
                    game = updated ?: it.game,
                    metadataPreview = null,
                    actionMessage = written.fold(
                        onSuccess = { fields ->
                            when (fields.size) {
                                0    -> "Nothing to change"
                                1    -> "Updated 1 field from ${preset.provider.label}"
                                else -> "Updated ${fields.size} fields from ${preset.provider.label}"
                            }
                        },
                        onFailure = { "Metadata update failed" },
                    ),
                )
            }
        }
    }

    private fun updateMetadataPreview(transform: (MetadataPreviewUi) -> MetadataPreviewUi) = _uiState.update { s ->
        val p = s.metadataPreview ?: return@update s
        if (p.loading || p.applying) s else s.copy(metadataPreview = transform(p))
    }

    private fun handleMetadataPreviewInput(action: GamepadAction) {
        val p = _uiState.value.metadataPreview ?: return
        if (p.applying) return
        if (p.nothingFound) {
            if (action == GamepadAction.SELECT || action == GamepadAction.BACK) closeMetadataPreview()
            return
        }
        when (action) {
            GamepadAction.BACK           -> closeMetadataPreview()
            GamepadAction.NAVIGATE_LEFT  -> cycleMetadataPolicy(-1)
            GamepadAction.NAVIGATE_RIGHT -> cycleMetadataPolicy(+1)
            GamepadAction.PREV_CATEGORY  -> cycleMetadataSource(-1)
            GamepadAction.NEXT_CATEGORY  -> cycleMetadataSource(+1)
            GamepadAction.NAVIGATE_UP,
            GamepadAction.NAVIGATE_DOWN,
            GamepadAction.SELECT         -> nav.handleAction(action)
            else -> Unit
        }
    }

    private fun artRefsOf(game: Game?): List<String> = listOfNotNull(
        game?.artworkUri, game?.heroUri, game?.logoUri, game?.iconUri,
        game?.boxArtUri, game?.physicalMediaUri, game?.box3dUri,
    )
}
