package com.psplauncher.feature.xmb.ui.detail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.repository.GameRepository
import com.psplauncher.feature.artwork.api.SgdbApiKeyProvider
import com.psplauncher.feature.artwork.api.SgdbArtType
import com.psplauncher.feature.artwork.api.SsCachedMedia
import com.psplauncher.feature.artwork.api.SteamGridDbApi
import com.psplauncher.feature.artwork.match.CachingMatchEvidence
import com.psplauncher.feature.artwork.match.GameCandidate
import com.psplauncher.feature.artwork.match.GameMatch
import com.psplauncher.feature.artwork.match.GameMatcher
import com.psplauncher.feature.artwork.match.MatchProvider
import com.psplauncher.feature.artwork.match.MatchTier
import com.psplauncher.feature.artwork.match.ProviderCapabilities
import com.psplauncher.feature.artwork.match.ProviderMatchEvidence
import com.psplauncher.feature.artwork.store.ArtworkKind
import com.psplauncher.feature.artwork.store.ArtworkStore
import com.psplauncher.feature.artwork.store.CropProfileRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

data class StudioTab(
    val kind: ArtworkKind,
    val label: String,
    val contract: String,
    val tileClass: StudioTileClass,
)

enum class StudioSource(val label: String) {
    SCREENSCRAPER("ScreenScraper"),
    STEAMGRIDDB("SteamGridDB"),
    IGDB("IGDB"),
    LOCAL("Local File"),
}

data class StudioArt(
    val url: String,
    val thumb: String?,
    val provider: String,
    val label: String? = null,
    val isVideo: Boolean = false,
    val providerAssetId: String? = null,
)

enum class StudioZone { TABS, SOURCES, GRID }

enum class StudioQueueState { QUEUED, DOWNLOADING, ADDED, FAILED }

data class StudioQueueItem(
    val gameId: Long,
    val key: StudioArtKey,
    val art: StudioArt,
    val state: StudioQueueState,
)

enum class StudioTileMark { NONE, PICKED, QUEUED, DOWNLOADING, ADDED, CURRENT, FAILED, TO_REMOVE }

data class StudioQueueSummary(
    val toAdd: Int = 0,
    val toRemove: Int = 0,
    val added: Int = 0,
    val failed: Int = 0,
    val total: Int = 0,
) {
    val hasChanges: Boolean get() = toAdd > 0 || toRemove > 0
    val inQueue: Boolean get() = total > 0
}

enum class StudioLeaveChoice(val label: String) {
    APPLY("Apply and Close"),
    DISCARD("Discard Changes"),
    STAY("Stay"),
}

enum class CropOption {
    PREVIEW,
    SHAPE_PLATFORM_DEFAULT,
    SHAPE_ORIGINAL_IMAGE;

    val shape: CropShapeChoice?
        get() = when (this) {
            PREVIEW -> null
            SHAPE_PLATFORM_DEFAULT -> CropShapeChoice.PLATFORM_DEFAULT
            SHAPE_ORIGINAL_IMAGE -> CropShapeChoice.ORIGINAL_IMAGE
        }
}

enum class CropShapeChoice(val label: String, val storedKey: String?) {
    PLATFORM_DEFAULT("Platform Default", null),
    ORIGINAL_IMAGE("Original Image", CropProfileRegistry.ORIGINAL_KEY);

    companion object {
        fun of(storedKey: String?): CropShapeChoice =
            entries.firstOrNull { it.storedKey != null && it.storedKey == storedKey } ?: PLATFORM_DEFAULT
    }
}

enum class StudioApplyChoice(val label: String) {
    APPLY("Apply"),
    CANCEL("Cancel"),
}

enum class StudioReplaceChoice(val label: String) {
    CANCEL("Cancel"),
    REPLACE("Replace Anyway"),
}

enum class StudioConfirmKind { APPLY, REPLACE }

data class StudioConfirmRow(val label: String, val isDestructive: Boolean = false)

data class StudioConfirmPrompt(
    val kind: StudioConfirmKind,
    val title: String,
    val rows: List<StudioConfirmRow>,
    val selectedIndex: Int,
)

fun studioAssetCount(kind: ArtworkKind, count: Int): String {
    val noun = when (kind) {
        ArtworkKind.SCREENSHOT -> "screenshot"
        ArtworkKind.VIDEO      -> "video"
        else                   -> "image"
    }
    return if (count == 1) "1 $noun" else "$count ${noun}s"
}

fun studioApplyTitle(kind: ArtworkKind, toAdd: Int, toRemove: Int): String = when {
    toAdd > 0 && toRemove > 0 -> "Add ${studioAssetCount(kind, toAdd)} and remove $toRemove?"
    toRemove > 0              -> "Remove ${studioAssetCount(kind, toRemove)}?"
    else                      -> "Add ${studioAssetCount(kind, toAdd)}?"
}

data class ArtworkStudioUiState(
    val game: Game? = null,
    val isLoading: Boolean = true,
    val tabIndex: Int = 0,
    val sourceIndex: Int = 0,
    val zone: StudioZone = StudioZone.TABS,
    val gridIndex: Int = 0,

    val gridColumns: Int = StudioGridCapacity.UNMEASURED.columns,
    val gridRows: Int = StudioGridCapacity.UNMEASURED.rows,
    val page: Int = 0,
    val pageCount: Int = 0,

    val rangeStart: Int = 0,
    val rangeEnd: Int = 0,
    val results: List<StudioArt> = emptyList(),
    val totalResults: Int = 0,
    val resultsLoading: Boolean = false,

    val query: String = "",

    val queryDraft: String = "",
    val searchOpen: Boolean = false,

    val queryIsCustom: Boolean = false,

    val match: GameMatch? = null,
    val matchProvider: MatchProvider? = null,
    val matchResolving: Boolean = false,

    val matchFailed: Boolean = false,

    val changeMatchOpen: Boolean = false,
    val changeMatchDraft: String = "",
    val changeMatchLoading: Boolean = false,
    val changeMatchResults: List<GameCandidate> = emptyList(),

    val changeMatchIndex: Int = -1,

    val changeMatchEditing: Boolean = false,

    val changeMatchAcrossPlatforms: Boolean = false,

    val changeMatchSearchingEveryPlatform: Boolean = false,

    val changeMatchError: String? = null,

    val currentUri: String? = null,

    val filledKinds: Int = 0,

    val requestsToday: Int? = null,
    val dailyRequestCap: Int? = null,

    val previewVersion: Int = 0,
    val includeNsfw: Boolean = false,
    val hasSgdbKey: Boolean = false,

    val unavailableSources: Set<StudioSource> = emptySet(),

    val candidate: StudioArt? = null,

    val selection: Map<StudioArtKey, StudioArt> = emptyMap(),

    val removals: Map<StudioArtKey, StudioArt> = emptyMap(),

    val queue: List<StudioQueueItem> = emptyList(),

    val leavePromptOpen: Boolean = false,
    val leavePromptIndex: Int = 0,

    val applyConfirmOpen: Boolean = false,
    val applyConfirmIndex: Int = 0,

    val replacePromptOpen: Boolean = false,
    val replacePromptIndex: Int = 0,

    val managerOpen: Boolean = false,
    val managerIndex: Int = 0,

    val managerBusy: Boolean = false,

    val library: StudioLibraryAssets = StudioLibraryAssets(),

    val candidateManualPath: String? = null,
    val manualDownloading: Boolean = false,
    val manualPage: Int = 0,
    val manualPageCount: Int = 0,
    val applying: Boolean = false,
    val message: String? = null,

    val localPickKind: ArtworkKind? = null,

    val actionsOpen: Boolean = false,
    val actionsIndex: Int = 0,

    val actionsSelectedAction: StudioAction? = null,

    val sgdbSourceActive: Boolean = false,
    val info: StudioArtworkInfo? = null,
    val showFileInfo: Boolean = false,

    val cropEditorPath: String? = null,
    val cropVideoSourcePath: String? = null,

    val cropCandidate: StudioArt? = null,

    val cropPreviewEnabled: Boolean =
        com.psplauncher.core.data.repository.CropPreviewPreferences.DEFAULT_ENABLED,

    val cropProfileOverride: String? = null,

    val cropOptionsOpen: Boolean = false,
    val cropOptionsIndex: Int = 0,
    val cropOptionRows: List<CropOption> = emptyList(),
    val cropPreparing: Boolean = false,
    val cropSrcW: Int = 0,
    val cropSrcH: Int = 0,
    val cropZoom: Float = 1f,
    val cropCenterX: Float = 0.5f,
    val cropCenterY: Float = 0.5f,

    val cropL: Float = 0f,
    val cropT: Float = 0f,
    val cropR: Float = 1f,
    val cropB: Float = 1f,
    val closed: Boolean = false,
) {
    val skeletonCount: Int get() = if (resultsLoading) pageSize else 0

    val pageSize: Int get() = gridColumns * gridRows

    val matchTitle: String? get() = match?.candidate?.title

    val matchIsConfirmed: Boolean get() = match?.userConfirmed == true

    val canChangeMatch: Boolean
        get() = matchProvider?.let { ProviderCapabilities[it].supportsTitleSearch } == true

    val hasPreviousPage: Boolean get() = page > 0
    val hasNextPage: Boolean get() = page < pageCount - 1

    val selectsMultiple: Boolean
        get() = STUDIO_TABS.getOrNull(tabIndex)?.kind
            ?.let(com.psplauncher.feature.artwork.store.ArtworkFileNaming::supportsMultiple) == true

    fun isSelected(art: StudioArt): Boolean =
        STUDIO_TABS.getOrNull(tabIndex)?.let { StudioArtKey.of(it.kind, art) in selection } == true

    val selectedOnTab: Int
        get() = STUDIO_TABS.getOrNull(tabIndex)?.kind?.let { kind -> selection.keys.count { it.kind == kind } } ?: 0

    val canPreviewFocused: Boolean
        get() = zone == StudioZone.GRID && selectsMultiple && results.getOrNull(gridIndex) != null

    fun queueStateOf(art: StudioArt): StudioQueueState? {
        val kind = STUDIO_TABS.getOrNull(tabIndex)?.kind ?: return null
        val key = StudioArtKey.of(kind, art)
        return queue.firstOrNull { it.gameId == game?.id && it.key == key }?.state
    }

    fun tileMarkOf(art: StudioArt): StudioTileMark {
        val kind = STUDIO_TABS.getOrNull(tabIndex)?.kind ?: return StudioTileMark.NONE
        val key = StudioArtKey.of(kind, art)
        val queued = queueStateOf(art)
        return when {
            queued == StudioQueueState.QUEUED      -> StudioTileMark.QUEUED
            queued == StudioQueueState.DOWNLOADING -> StudioTileMark.DOWNLOADING
            queued == StudioQueueState.FAILED      -> StudioTileMark.FAILED
            key in removals                        -> StudioTileMark.TO_REMOVE
            queued == StudioQueueState.ADDED       -> StudioTileMark.ADDED

            library.holds(kind, art)               ->
                if (selectsMultiple) StudioTileMark.ADDED else StudioTileMark.CURRENT
            key in selection                       -> StudioTileMark.PICKED
            else                                   -> StudioTileMark.NONE
        }
    }

    val removalsOnTab: Int
        get() = STUDIO_TABS.getOrNull(tabIndex)?.kind?.let { kind -> removals.keys.count { it.kind == kind } } ?: 0

    val queueSummary: StudioQueueSummary
        get() {
            val kind = STUDIO_TABS.getOrNull(tabIndex)?.kind ?: return StudioQueueSummary()
            val items = queue.filter { it.gameId == game?.id && it.key.kind == kind }
            return StudioQueueSummary(
                toAdd = selectedOnTab,
                toRemove = removalsOnTab,
                added = items.count { it.state == StudioQueueState.ADDED },
                failed = items.count { it.state == StudioQueueState.FAILED },
                total = items.size,
            )
        }

    val managedAssets: List<com.psplauncher.feature.artwork.store.StudioArtworkSlot>
        get() = library.slots

    val overCapacityBy: Int
        get() {
            if (!selectsMultiple) return 0
            val after = library.slots.size - removalsOnTab + selectedOnTab
            val capacity = com.psplauncher.feature.artwork.store.ArtworkFileNaming.MAX_SORT_ORDER + 1
            return (after - capacity).coerceAtLeast(0)
        }

    val confirmPrompt: StudioConfirmPrompt?
        get() = when {
            applyConfirmOpen -> StudioConfirmPrompt(
                kind = StudioConfirmKind.APPLY,
                title = STUDIO_TABS.getOrNull(tabIndex)
                    ?.let { studioApplyTitle(it.kind, queueSummary.toAdd, queueSummary.toRemove) }
                    ?: "Apply changes?",
                rows = StudioApplyChoice.entries.map {
                    StudioConfirmRow(it.label, isDestructive = it == StudioApplyChoice.APPLY && queueSummary.toRemove > 0)
                },
                selectedIndex = applyConfirmIndex,
            )
            replacePromptOpen -> StudioConfirmPrompt(
                kind = StudioConfirmKind.REPLACE,
                title = "${STUDIO_TABS.getOrNull(tabIndex)?.label ?: "This artwork"} is already this image",
                rows = StudioReplaceChoice.entries.map {
                    StudioConfirmRow(it.label, isDestructive = it == StudioReplaceChoice.REPLACE)
                },
                selectedIndex = replacePromptIndex,
            )
            else -> null
        }

    val availableActions: List<StudioAction>
        get() = buildList {
            val kind = STUDIO_TABS.getOrNull(tabIndex)?.kind
            val hasCurrent = currentUri != null
            if (queueSummary.hasChanges) add(StudioAction.APPLY_CHANGES)
            if (canPreviewFocused) add(StudioAction.PREVIEW)
            if (queueSummary.failed > 0) {
                add(StudioAction.RETRY_FAILED)
                add(StudioAction.REMOVE_FAILED)
            }

            if (selectsMultiple && library.slots.size > 1) add(StudioAction.MANAGE_ASSETS)
            if (hasCurrent && kind != null && kind in CROPPABLE_KINDS) add(StudioAction.CROP)

            if (zone == StudioZone.GRID && kind != null && kind in CROPPABLE_KINDS &&
                results.getOrNull(gridIndex)?.isVideo == false
            ) {
                add(StudioAction.CROP_BEFORE_APPLY)
            }
            if (info?.hasPrevious == true) add(StudioAction.RESTORE_PREVIOUS)
            if (info?.originUrl != null) add(StudioAction.RESET_DEFAULT)
            if (hasCurrent) add(StudioAction.CLEAR)
            if (hasCurrent) add(StudioAction.FILE_INFO)

            if (sgdbSourceActive) add(StudioAction.TOGGLE_MATURE)

            if (matchProvider != null) add(StudioAction.CHANGE_MATCH)
            if (matchIsConfirmed) add(StudioAction.FORGET_MATCH)
        }

    val resolvedActionsIndex: Int
        get() {
            val actions = availableActions
            if (actions.isEmpty()) return 0
            val byAction = actionsSelectedAction?.let(actions::indexOf) ?: -1
            return if (byAction >= 0) byAction else actionsIndex.coerceIn(0, actions.lastIndex)
        }
}

enum class StudioAction(val label: String) {
    APPLY_CHANGES("Apply Changes"),
    PREVIEW("Preview"),
    RETRY_FAILED("Retry Failed Downloads"),
    REMOVE_FAILED("Remove Failed Downloads"),
    MANAGE_ASSETS("Reorder Stored Artwork"),
    CROP("Adjust Crop / Position"),
    CROP_BEFORE_APPLY("Crop Before Applying"),
    RESTORE_PREVIOUS("Restore Previous"),
    RESET_DEFAULT("Reset to Scraped Default"),
    CLEAR("Clear Artwork"),
    FILE_INFO("View File Information"),
    TOGGLE_MATURE("Mature Content (SteamGridDB)"),
    CHANGE_MATCH("Change Match"),
    FORGET_MATCH("Forget Match"),
}

val CROPPABLE_KINDS = setOf(
    ArtworkKind.ICON, ArtworkKind.ICON1, ArtworkKind.BOX_ART, ArtworkKind.BOX_3D,
    ArtworkKind.PHYSICAL_MEDIA, ArtworkKind.HERO, ArtworkKind.BACKGROUND, ArtworkKind.LOGO,
    ArtworkKind.SCREENSHOT, ArtworkKind.TITLESCREEN,
)

typealias StudioArtworkInfo = com.psplauncher.feature.artwork.store.StudioArtworkInfo

private const val CROP_PAN_STEP = 0.03f

private const val MAX_QUERY_LENGTH = 120

private val SS_TYPES_FOR_KIND: Map<ArtworkKind, List<String>> = mapOf(
    ArtworkKind.ICON           to listOf("mixrbv2", "mixrbv1", "screenmarquee", "steamgrid", "box-2D"),
    ArtworkKind.BOX_ART        to listOf("box-2D"),
    ArtworkKind.BOX_3D         to listOf("box-3D"),
    ArtworkKind.PHYSICAL_MEDIA to listOf("support-2D", "support-texture"),
    ArtworkKind.HERO           to listOf("fanart", "ss"),
    ArtworkKind.BACKGROUND     to listOf("fanart", "ss", "box-2D"),
    ArtworkKind.LOGO           to listOf("wheel", "wheel-hd"),
    ArtworkKind.SCREENSHOT     to listOf("ss", "sstitle"),
    ArtworkKind.MANUAL         to listOf("manuel"),
    ArtworkKind.VIDEO          to listOf("video"),
    ArtworkKind.ICON1          to listOf("video-normalized", "video"),
)

private val NO_IMAGE_PROVIDER_KINDS = setOf(ArtworkKind.ICON1, ArtworkKind.MANUAL, ArtworkKind.VIDEO)

private val BACKGROUND_SOURCES = listOf(
    StudioSource.STEAMGRIDDB to MatchProvider.STEAMGRIDDB,
    StudioSource.IGDB to MatchProvider.IGDB,
)

private val SHOW_ALL_ART_KINDS = setOf(ArtworkKind.BOX_3D, ArtworkKind.PHYSICAL_MEDIA, ArtworkKind.SCREENSHOT)

val STUDIO_TABS = listOf(
    StudioTab(ArtworkKind.ICON,           "ICON0",       "XMB tile · 144×80 · crop",                       StudioTileClass.LANDSCAPE),
    StudioTab(ArtworkKind.ICON1,          "ICON1",       "XMB icon animation · 60 s muted snap",           StudioTileClass.LANDSCAPE),
    StudioTab(ArtworkKind.BOX_ART,        "BOX ART",     "XMB tile (Box Art mode) · natural aspect",       StudioTileClass.PORTRAIT),
    StudioTab(ArtworkKind.BOX_3D,         "3D BOX",      "XMB tile (3D Box mode) · natural aspect",        StudioTileClass.PORTRAIT),
    StudioTab(ArtworkKind.PHYSICAL_MEDIA, "PHYS. MEDIA", "XMB tile (Physical Media mode) · natural aspect", StudioTileClass.SQUARE),
    StudioTab(ArtworkKind.HERO,           "HERO",        "Game Details banner · wide · crop",              StudioTileClass.LANDSCAPE),
    StudioTab(ArtworkKind.BACKGROUND,     "BACKGROUND",  "XMB hover background · full screen",             StudioTileClass.LANDSCAPE),
    StudioTab(ArtworkKind.LOGO,           "LOGO",        "PIC0 overlay · transparent PNG · fit",           StudioTileClass.WIDE),
    StudioTab(ArtworkKind.SCREENSHOT,     "SCREENSHOT",  "Game Details media strip",                       StudioTileClass.LANDSCAPE),
    StudioTab(ArtworkKind.MANUAL,         "MANUAL",      "In-app PDF manual",                              StudioTileClass.PORTRAIT),
    StudioTab(ArtworkKind.VIDEO,          "VIDEO",       "Game Details media strip · full video",          StudioTileClass.LANDSCAPE),
)

@HiltViewModel
class ArtworkStudioViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val gameRepository: GameRepository,
    private val artworkStore: ArtworkStore,

    private val routingStore: com.psplauncher.feature.artwork.store.RoutingArtworkStore,
    private val ssMediaCatalog: com.psplauncher.feature.artwork.api.SsMediaCatalog,
    private val steamGridDb: SteamGridDbApi,
    private val screenScraper: com.psplauncher.feature.artwork.api.ScreenScraperApi,
    private val sgdbKeyProvider: SgdbApiKeyProvider,
    private val igdbApi: com.psplauncher.feature.artwork.api.IgdbApi,
    private val videoSnapTranscoder: com.psplauncher.feature.artwork.video.VideoSnapTranscoder,
    private val matchEvidence: ProviderMatchEvidence,
    private val cropPreviewPreferences: com.psplauncher.core.data.repository.CropPreviewPreferences,
    titleSearchStore: com.psplauncher.feature.artwork.match.TitleSearchStore,
) : ViewModel(), ArtworkStudioActions {
    private val titleSearches = CachingMatchEvidence(matchEvidence, titleSearchStore)

    private val matcher = GameMatcher(titleSearches)

    private val appCacheDir: java.io.File get() = appContext.cacheDir

    private val _uiState = MutableStateFlow(ArtworkStudioUiState())
    val uiState: StateFlow<ArtworkStudioUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            cropPreviewPreferences.enabledFlow.collect { enabled ->
                _uiState.update { it.copy(cropPreviewEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            screenScraper.quota.collect { user ->
                _uiState.update {
                    it.copy(
                        requestsToday = user?.requestsToday?.toIntOrNull(),
                        dailyRequestCap = user?.maxRequestsPerDay?.toIntOrNull(),
                    )
                }
            }
        }
    }

    private val resultCache = StudioResultCache()

    private var activeKey: StudioRequestKey? = null

    private var generation: Long = 0

    private var loadJob: kotlinx.coroutines.Job? = null

    internal var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

    private var queueJob: kotlinx.coroutines.Job? = null

    private var gameId: Long = -1

    private var gridSlotDp: Pair<Float, Float>? = null

    fun load(gameId: Long) {
        _uiState.update { s ->
            s.copy(
                closed = false, zone = StudioZone.TABS, selection = emptyMap(), removals = emptyMap(),
                leavePromptOpen = false, applyConfirmOpen = false, replacePromptOpen = false,
                managerOpen = false,
                queue = s.queue.filter { it.state == StudioQueueState.QUEUED || it.state == StudioQueueState.DOWNLOADING },
            )
        }

        titleSearches.clear()
        matchMemo.clear()
        if (this.gameId == gameId && _uiState.value.game != null) {
            viewModelScope.launch {
                val before = _uiState.value.unavailableSources
                refreshProviderAvailability()
                if (_uiState.value.unavailableSources != before) {
                    landOnAvailableSource()
                    loadResults()
                }

                refreshCurrent()

                resolveInBackground()
            }
            return
        }
        this.gameId = gameId

        cancelLoad()
        cancelBackgroundResolutions()
        viewModelScope.launch {
            val game = gameRepository.getById(gameId)
            refreshProviderAvailability()
            resultCache.clear()

            val seed = game?.displayTitle.orEmpty()
            _uiState.update {
                it.copy(
                    game = game, isLoading = false,
                    query = seed, queryDraft = seed, queryIsCustom = false,
                )
            }
            landOnAvailableSource()
            refreshCurrent()
            loadResults()
            resolveInBackground()
        }
    }

    private fun gameTitle(): String = _uiState.value.game?.displayTitle.orEmpty()

    private fun tab() = STUDIO_TABS[_uiState.value.tabIndex]

    override fun sourcesForTab(): List<StudioSource> = StudioSource.entries

    private fun servesKind(source: StudioSource, kind: ArtworkKind): Boolean = when (source) {
        StudioSource.SCREENSCRAPER -> SS_TYPES_FOR_KIND.containsKey(kind)
        StudioSource.STEAMGRIDDB,
        StudioSource.IGDB          -> kind !in NO_IMAGE_PROVIDER_KINDS
        StudioSource.LOCAL         -> true
    }

    private fun sgdbTypesFor(kind: ArtworkKind): List<SgdbArtType> = when (kind) {
        ArtworkKind.ICON    -> listOf(SgdbArtType.GRID)
        ArtworkKind.BOX_ART -> listOf(SgdbArtType.GRID)
        ArtworkKind.HERO,
        ArtworkKind.BACKGROUND -> listOf(SgdbArtType.HERO)
        ArtworkKind.LOGO    -> listOf(SgdbArtType.LOGO)
        in SHOW_ALL_ART_KINDS -> SgdbArtType.entries
        else                -> emptyList()
    }

    private fun legacyUriFor(kind: ArtworkKind, game: Game?): String? = when (kind) {
        ArtworkKind.ICON           -> game?.iconUri
        ArtworkKind.BOX_ART        -> game?.boxArtUri
        ArtworkKind.BOX_3D         -> game?.box3dUri
        ArtworkKind.PHYSICAL_MEDIA -> game?.physicalMediaUri
        ArtworkKind.HERO           -> game?.heroUri
        ArtworkKind.BACKGROUND     -> game?.artworkUri
        ArtworkKind.LOGO           -> game?.logoUri
        else                       -> null
    }

    private suspend fun refreshCurrent() {
        val kind = tab().kind
        val current = artworkStore.find(gameId, kind)
            ?: legacyUriFor(kind, _uiState.value.game)
        _uiState.update { it.copy(currentUri = current) }
        refreshFilledCount()
        refreshLibrary()
    }

    private suspend fun refreshFilledCount() {
        val game = _uiState.value.game
        val filled = STUDIO_TABS.count { tab ->
            artworkStore.find(gameId, tab.kind) != null || !legacyUriFor(tab.kind, game).isNullOrBlank()
        }
        _uiState.update { it.copy(filledKinds = filled) }
    }

    private suspend fun refreshLibrary() {
        val kind = tab().kind
        _uiState.update { it.copy(library = StudioLibraryAssets.of(kind, routingStore.studioAssetsOnDisk(gameId, kind))) }
    }

    private fun loadResults() {
        loadJob?.cancel()
        val token = ++generation
        val state = _uiState.value
        val source = sourcesForTab().getOrNull(state.sourceIndex) ?: StudioSource.LOCAL
        val kind = tab().kind
        val provider = providerFor(source)
        val known = knownMatch(state, provider)
        _uiState.update {
            it.copy(matchProvider = provider, match = known?.match, matchResolving = known == null, matchFailed = false)
        }

        if (known != null) {
            val key = requestKey(state, source, kind, known.match)
            activeKey = key
            resultCache[key]?.let { cached ->
                showPage(cached, pageIndex = 0, key = key, token = token)
                return
            }
            if (source == StudioSource.LOCAL) {
                resultCache[key] = emptyList()
                showPage(emptyList(), pageIndex = 0, key = key, token = token)
                return
            }
        } else {
            activeKey = null
        }

        _uiState.update {
            it.copy(resultsLoading = true, results = emptyList(), gridIndex = 0, page = 0,
                pageCount = 0, rangeStart = 0, rangeEnd = 0, totalResults = 0)
        }
        loadJob = viewModelScope.launch {
            val romLookup = if (known == null && provider == MatchProvider.SCREENSCRAPER) lookUpSsRomIdentity() else null

            var match = if (known != null) {
                known.match
            } else {
                resolveMatch(checkNotNull(provider), state.query, token, skipRomHash = romLookup != null)
            }
            var key = requestKey(state, source, kind, match)
            activeKey = key
            browse(source, kind, state.query, match, key, token, romLookup)
            if (source != StudioSource.SCREENSCRAPER || !refreshSsIdentityAfterBrowse()) return@launch

            match = resolveMatch(MatchProvider.SCREENSCRAPER, state.query, token)
            val movedKey = requestKey(state, source, kind, match)
            if (movedKey == key) return@launch
            key = movedKey
            activeKey = key
            browse(source, kind, state.query, match, key, token)
        }
    }

    private fun requestKey(state: ArtworkStudioUiState, source: StudioSource, kind: ArtworkKind, match: GameMatch?) =
        StudioRequestKey.of(state.query, source, kind, state.includeNsfw, match?.matchKey)

    private class SsRomLookup(val ssId: Long?, val medias: List<SsCachedMedia>?)

    private suspend fun lookUpSsRomIdentity(): SsRomLookup? {
        val game = _uiState.value.game ?: return null
        if (game.ssId != null || (game.romPath == null && game.romUri == null)) return null
        val medias = try {
            ssMediaCatalog.mediasFor(gameId, matchedSsId = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "ScreenScraper ROM lookup failed")
            return null
        }
        val stored = gameRepository.getById(gameId) ?: game
        currentCoroutineContext().ensureActive()
        if (stored.ssId != game.ssId || stored.romCrc32 != game.romCrc32) {
            _uiState.update { it.copy(game = stored) }

            forgetMatches(MatchProvider.SCREENSCRAPER)
        }
        return SsRomLookup(stored.ssId, medias)
    }

    private suspend fun browse(
        source: StudioSource,
        kind: ArtworkKind,
        query: String,
        match: GameMatch?,
        key: StudioRequestKey,
        token: Long,
        romLookup: SsRomLookup? = null,
    ) {
        val cached = resultCache[key]
        if (cached != null) {
            showPage(cached, pageIndex = 0, key = key, token = token)
            return
        }
        val fetched = when (source) {
            StudioSource.SCREENSCRAPER -> ssResults(kind, match, romLookup)
            StudioSource.STEAMGRIDDB   -> sgdbResults(kind, query, match)
            StudioSource.IGDB          -> igdbResults(kind, query, match)
            StudioSource.LOCAL         -> emptyList()
        }

        currentCoroutineContext().ensureActive()

        resultCache[key] = fetched
        showPage(fetched, pageIndex = 0, key = key, token = token)
    }

    private fun showPage(
        all: List<StudioArt>,
        pageIndex: Int,
        key: StudioRequestKey,
        token: Long,
        gridIndex: Int = 0,
    ) {
        if (token != generation || key != activeKey) return
        _uiState.update {
            val page = StudioPage.of(all, pageIndex, it.pageSize)
            it.copy(
                resultsLoading = false,
                results = page.items,
                totalResults = page.totalResults,
                page = page.pageIndex,
                pageCount = page.pageCount,
                rangeStart = page.rangeStart,
                rangeEnd = page.rangeEnd,
                gridIndex = gridIndex.coerceIn(0, page.items.lastIndex.coerceAtLeast(0)),
            )
        }
    }

    override fun onGridMeasured(widthDp: Float, heightDp: Float) {
        gridSlotDp = widthDp to heightDp
        val capacity = capacityFor(_uiState.value.tabIndex) ?: return
        applyCapacity(capacity)
    }

    private fun capacityFor(tabIndex: Int): StudioGridCapacity? =
        gridSlotDp?.let { (width, height) -> StudioGridCapacity.of(width, height, STUDIO_TABS[tabIndex].tileClass) }

    private fun applyCapacity(capacity: StudioGridCapacity) {
        val before = _uiState.value
        if (capacity.columns == before.gridColumns && capacity.rows == before.gridRows) return

        val focused = before.page * before.pageSize + before.gridIndex
        _uiState.update { it.copy(gridColumns = capacity.columns, gridRows = capacity.rows) }

        if (before.resultsLoading) return
        val key = activeKey ?: return
        val all = activeResults()
        if (all.isEmpty()) return
        showPage(all, focused / capacity.pageSize, key, generation, gridIndex = focused % capacity.pageSize)
    }

    private suspend fun ssResults(kind: ArtworkKind, match: GameMatch?, romLookup: SsRomLookup?): List<StudioArt> {
        val types = SS_TYPES_FOR_KIND[kind] ?: return emptyList()
        val matchedSsId = match?.candidate
            ?.takeIf { it.provider == MatchProvider.SCREENSCRAPER }
            ?.providerGameId?.toLongOrNull()
        val usesLookup = romLookup != null && (matchedSsId == null || matchedSsId == romLookup.ssId)
        val medias = (if (usesLookup) romLookup.medias else ssMediaCatalog.mediasFor(gameId, matchedSsId))
            ?: return emptyList()
        return screenScraperTiles(kind, types, medias)
    }

    private suspend fun sgdbResults(kind: ArtworkKind, query: String, match: GameMatch?): List<StudioArt> {
        val types = sgdbTypesFor(kind).ifEmpty { return emptyList() }
        val game = _uiState.value.game ?: return emptyList()

        val sgdbMatch = match?.takeIf { it.candidate.provider == MatchProvider.STEAMGRIDDB }

        val confirmed = sgdbMatch?.takeIf { it.userConfirmed }?.candidate?.providerGameId?.toLongOrNull()

        val savedId = game.steamGridDbId?.takeIf { StudioQuery.sameQuery(query, game.displayTitle) }

        val resolved = sgdbMatch
            ?.takeIf { it.tier == MatchTier.CONTENT_ID || it.tier == MatchTier.EXACT_TITLE }
            ?.candidate?.providerGameId?.toLongOrNull()
        val sgdbId = confirmed
            ?: savedId
            ?: resolved
            ?: firstSgdbHit(query, game.platformId)
            ?: return emptyList()

        return types.flatMap { type ->
            steamGridDb.getArt(
                gameId = sgdbId,
                type = type,
                dimensions = emptyList(),
                includeNsfw = _uiState.value.includeNsfw,
            ).getOrElse {
                Timber.w(it, "SGDB browse failed")
                emptyList()
            }.map { art ->
                StudioArt(
                    url = art.url,
                    thumb = art.thumb,
                    provider = "SteamGridDB",
                    label = listOfNotNull(
                        type.endpoint.takeIf { types.size > 1 },
                        art.style,
                        art.width?.let { w -> "${w}×${art.height}" },
                    ).joinToString(" · "),

                    providerAssetId = "${type.endpoint}:${art.id}",
                )
            }
        }
    }

    private suspend fun firstSgdbHit(query: String, platformId: String): Long? = try {
        titleSearches.searchByTitle(MatchProvider.STEAMGRIDDB, query, platformId).firstOrNull()?.providerGameId?.toLongOrNull()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "SGDB search failed")
        null
    }

    private suspend fun igdbResults(kind: ArtworkKind, query: String, match: GameMatch?): List<StudioArt> {
        val game = _uiState.value.game ?: return emptyList()
        val matchedId = match?.candidate
            ?.takeIf { it.provider == MatchProvider.IGDB }
            ?.providerGameId?.toLongOrNull()
        val info = runCatching {
            if (matchedId != null) igdbApi.fetchGameInfoById(matchedId)
            else igdbApi.fetchGameInfo(game.platformId, query)
        }
            .onFailure { Timber.w(it, "IGDB browse failed") }.getOrNull()
            ?: return emptyList()
        if (kind in SHOW_ALL_ART_KINDS) {
            return listOfNotNull(
                info.artworkUrl?.let { StudioArt(it, null, "IGDB", "cover") },
                info.heroUrl?.let { StudioArt(it, null, "IGDB", "artwork") },
            )
        }
        if (kind == ArtworkKind.ICON) {
            return listOfNotNull(
                info.artworkUrl?.let { StudioArt(it, null, "IGDB", "cover · crop to tile") },
                info.heroUrl?.let { StudioArt(it, null, "IGDB", "artwork · crop to tile") },
            )
        }
        val url = when (kind) {
            ArtworkKind.BOX_ART                        -> info.artworkUrl
            ArtworkKind.HERO, ArtworkKind.BACKGROUND   -> info.heroUrl
            ArtworkKind.LOGO                           -> info.logoUrl
            else                                       -> null
        } ?: return emptyList()
        return listOf(StudioArt(url = url, thumb = null, provider = "IGDB", label = "best title match"))
    }

    override fun selectTab(index: Int) {
        val tabIndex = index.coerceIn(0, STUDIO_TABS.lastIndex)

        val capacity = capacityFor(tabIndex)
        _uiState.update {
            it.copy(
                tabIndex = tabIndex, sourceIndex = 0, zone = StudioZone.TABS,
                gridColumns = capacity?.columns ?: it.gridColumns,
                gridRows = capacity?.rows ?: it.gridRows,
            )
        }
        landOnAvailableSource()

        viewModelScope.launch { refreshCurrent() }

        loadResults()
    }

    fun cycleTab(delta: Int) = selectTab((_uiState.value.tabIndex + delta).mod(STUDIO_TABS.size))

    override fun selectSource(index: Int) {
        val sources = sourcesForTab()

        if (sources.isEmpty()) return
        val clamped = index.coerceIn(0, sources.lastIndex)
        val source = sources[clamped]
        if (!isSourceAvailable(source)) {
            _uiState.update { it.copy(message = unavailableReason(source)) }
            return
        }
        _uiState.update { it.copy(sourceIndex = clamped, zone = StudioZone.SOURCES) }

        loadResults()
    }

    fun cycleSource(delta: Int) {
        val sources = sourcesForTab()

        val count = sources.size
        if (count == 0) return

        var index = _uiState.value.sourceIndex
        repeat(count) {
            index = (index + delta).mod(count)
            if (isSourceAvailable(sources[index])) {
                selectSource(index)
                return
            }
        }
    }

    private suspend fun refreshSsIdentityAfterBrowse(): Boolean {
        val shown = _uiState.value.game ?: return false
        val stored = gameRepository.getById(gameId) ?: return false
        currentCoroutineContext().ensureActive()
        if (stored.ssId == shown.ssId && stored.romCrc32 == shown.romCrc32) return false
        _uiState.update { it.copy(game = stored) }
        forgetMatches(MatchProvider.SCREENSCRAPER)
        return true
    }

    private suspend fun refreshProviderAvailability() {
        val unavailable = buildSet {
            if (!screenScraper.isEnabled()) add(StudioSource.SCREENSCRAPER)
            if (sgdbKeyProvider.getKey().isNullOrBlank()) add(StudioSource.STEAMGRIDDB)
            if (!igdbApi.hasCredentials()) add(StudioSource.IGDB)
        }
        _uiState.update {
            it.copy(unavailableSources = unavailable, hasSgdbKey = StudioSource.STEAMGRIDDB !in unavailable)
        }
    }

    fun isSourceAvailable(source: StudioSource): Boolean = sourceBadge(source) == null

    override fun sourceBadge(source: StudioSource): String? = when {
        !servesKind(source, tab().kind)                -> "n/a"
        source in _uiState.value.unavailableSources    -> "no key"
        else                                           -> null
    }

    private fun unavailableReason(source: StudioSource): String = when {
        !servesKind(source, tab().kind) -> "${source.label} has no ${tab().label} artwork"
        source == StudioSource.IGDB     -> "IGDB needs a Client ID and Secret — add them in Settings ▸ Artwork"
        else                            -> "${source.label} needs an API key — add one in Settings ▸ Artwork"
    }

    private fun landOnAvailableSource() {
        val sources = sourcesForTab()
        val current = sources.getOrNull(_uiState.value.sourceIndex)
        if (current != null && isSourceAvailable(current)) return
        val first = sources.indexOfFirst { isSourceAvailable(it) }
        if (first >= 0) _uiState.update { it.copy(sourceIndex = first) }
    }

    override fun toggleNsfw() {
        if (!sgdbActive()) return
        _uiState.update { it.copy(includeNsfw = !it.includeNsfw, actionsOpen = false) }
        loadResults()
    }

    private fun sgdbActive(): Boolean =
        sourcesForTab().getOrNull(_uiState.value.sourceIndex) == StudioSource.STEAMGRIDDB

    private fun providerFor(source: StudioSource?): MatchProvider? = when (source) {
        StudioSource.SCREENSCRAPER -> MatchProvider.SCREENSCRAPER
        StudioSource.STEAMGRIDDB   -> MatchProvider.STEAMGRIDDB
        StudioSource.IGDB          -> MatchProvider.IGDB
        StudioSource.LOCAL, null   -> null
    }

    private val matchMemo = HashMap<MatchMemoKey, GameMatch?>()

    private data class MatchMemoKey(val provider: MatchProvider, val query: String) {
        companion object {
            private val WHITESPACE = Regex("\\s+")

            fun of(provider: MatchProvider, query: String) =
                MatchMemoKey(provider, query.trim().replace(WHITESPACE, " ").lowercase(java.util.Locale.ROOT))
        }
    }

    private class KnownMatch(val match: GameMatch?)

    private fun knownMatch(state: ArtworkStudioUiState, provider: MatchProvider?): KnownMatch? {
        if (provider == null) return KnownMatch(null)
        state.match?.takeIf { it.userConfirmed && it.candidate.provider == provider }?.let { return KnownMatch(it) }
        val key = MatchMemoKey.of(provider, state.query)
        return if (matchMemo.containsKey(key)) KnownMatch(matchMemo[key]) else null
    }

    private fun forgetMatches(provider: MatchProvider) {
        matchMemo.keys.removeAll { it.provider == provider }

        cancelBackgroundResolutions(provider)
    }

    private fun cancelLoad() {
        loadJob?.cancel()
        _uiState.update { it.copy(matchResolving = false) }
    }

    private var changeMatchJob: kotlinx.coroutines.Job? = null

    private suspend fun resolveMatch(
        provider: MatchProvider,
        query: String,
        token: Long,
        skipRomHash: Boolean = false,
    ): GameMatch? {
        val game = _uiState.value.game ?: return null
        val outcome = joinBackgroundResolution(provider, query)
            ?: resolveAndRemember(game, provider, query, skipRomHash)
        val resolved = outcome.getOrNull()

        if (token == generation) {
            _uiState.update { it.copy(match = resolved, matchResolving = false, matchFailed = outcome.isFailure) }
        }
        return resolved
    }

    private suspend fun resolveAndRemember(
        game: Game,
        provider: MatchProvider,
        query: String,
        skipRomHash: Boolean = false,
        background: Boolean = false,
    ): Result<GameMatch?> {
        val startedAt = System.currentTimeMillis()
        val outcome = try {
            Result.success(matcher.resolve(game, provider, query, skipRomHash))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Match resolution failed for %s", provider)
            Result.failure(e)
        }

        currentCoroutineContext().ensureActive()
        Timber.d(
            "Studio match %s%s: %s in %d ms",
            provider,
            if (background) " (background)" else "",
            outcome.fold({ it?.tier?.name ?: "none" }, { "failed" }),
            System.currentTimeMillis() - startedAt,
        )
        if (outcome.isSuccess) matchMemo[MatchMemoKey.of(provider, query)] = outcome.getOrNull()
        return outcome
    }

    private class BackgroundResolution(val key: MatchMemoKey, val outcome: Deferred<Result<GameMatch?>>)

    private val backgroundResolutions = HashMap<MatchProvider, BackgroundResolution>()

    private fun resolveInBackground() {
        val state = _uiState.value
        val game = state.game ?: return
        val active = providerFor(sourcesForTab().getOrNull(state.sourceIndex))
        BACKGROUND_SOURCES.forEach { (source, provider) ->
            if (provider == active || source in state.unavailableSources) return@forEach
            val key = MatchMemoKey.of(provider, state.query)
            if (matchMemo.containsKey(key) || backgroundResolutions[provider]?.key == key) return@forEach
            backgroundResolutions.remove(provider)?.outcome?.cancel()

            val outcome = viewModelScope.async(start = CoroutineStart.LAZY) {
                resolveAndRemember(game, provider, state.query, background = true)
            }
            val entry = BackgroundResolution(key, outcome)
            backgroundResolutions[provider] = entry
            outcome.invokeOnCompletion { backgroundResolutions.remove(provider, entry) }
            outcome.start()
        }
    }

    private suspend fun joinBackgroundResolution(provider: MatchProvider, query: String): Result<GameMatch?>? {
        val running = backgroundResolutions[provider]?.takeIf { it.key == MatchMemoKey.of(provider, query) } ?: return null
        return try {
            running.outcome.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    private fun cancelBackgroundResolutions(provider: MatchProvider? = null) {
        val cancelled = if (provider == null) backgroundResolutions.values.toList() else listOfNotNull(backgroundResolutions[provider])
        cancelled.forEach { it.outcome.cancel() }
    }

    override fun onChangeMatchPressed() {
        val state = _uiState.value
        if (state.canChangeMatch) {
            openChangeMatch()
            return
        }
        val label = state.matchProvider?.label ?: return
        _uiState.update {
            it.copy(
                message = "$label can't be searched by title — there are no alternatives to choose from.",
                actionsOpen = false,
                showFileInfo = false,
            )
        }
    }

    fun openChangeMatch() {
        if (!_uiState.value.canChangeMatch) return
        val seed = _uiState.value.query.ifBlank { gameTitle() }
        _uiState.update {
            it.copy(
                changeMatchOpen = true,
                changeMatchDraft = seed,
                changeMatchResults = emptyList(),
                changeMatchIndex = -1,
                changeMatchEditing = false,
                actionsOpen = false,
                searchOpen = false,
            )
        }
        submitChangeMatch()
    }

    override fun onChangeMatchDraftChanged(text: String) =
        _uiState.update { it.copy(changeMatchDraft = text.take(MAX_QUERY_LENGTH)) }

    override fun cancelChangeMatch() {
        changeMatchJob?.cancel()
        _uiState.update {
            it.copy(
                changeMatchOpen = false,
                changeMatchResults = emptyList(),
                changeMatchIndex = -1,
                changeMatchEditing = false,
                changeMatchLoading = false,
                changeMatchSearchingEveryPlatform = false,
                changeMatchError = null,
            )
        }
    }

    override fun startChangeMatchEdit() = _uiState.update {
        if (!it.changeMatchOpen) it else it.copy(changeMatchEditing = true, changeMatchIndex = -1)
    }

    override fun stopChangeMatchEdit() = _uiState.update { it.copy(changeMatchEditing = false) }

    fun moveChangeMatchCursor(delta: Int) = _uiState.update {
        it.copy(changeMatchIndex = (it.changeMatchIndex + delta).coerceIn(-1, it.changeMatchResults.lastIndex))
    }

    override fun submitChangeMatch() {
        val state = _uiState.value
        val provider = state.matchProvider ?: return
        val game = state.game ?: return
        val query = state.changeMatchDraft.trim().ifBlank { gameTitle() }
        changeMatchJob?.cancel()
        _uiState.update {
            it.copy(
                changeMatchLoading = true, changeMatchResults = emptyList(), changeMatchIndex = -1,
                changeMatchEditing = false, changeMatchAcrossPlatforms = false,
                changeMatchSearchingEveryPlatform = false, changeMatchError = null,
            )
        }
        changeMatchJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val outcome = runCatching { changeMatchCandidates(provider, query, game.platformId) }

            ensureActive()
            outcome.onFailure { Timber.w(it, "Change Match search failed") }
            Timber.d(
                "Studio Change Match %s: %s in %d ms, every platform: %b",
                provider,
                outcome.fold({ "${it.first.size} candidates" }, { "failed" }),
                System.currentTimeMillis() - startedAt,
                _uiState.value.changeMatchSearchingEveryPlatform,
            )
            val (results, acrossPlatforms) = outcome.getOrDefault(emptyList<GameCandidate>() to false)

            _uiState.update {
                it.copy(
                    changeMatchLoading = false,
                    changeMatchSearchingEveryPlatform = false,
                    changeMatchResults = results,
                    changeMatchAcrossPlatforms = acrossPlatforms,

                    changeMatchError = outcome.exceptionOrNull()
                        ?.let { "${provider.label} didn't answer. Press Search to try again." },
                    changeMatchIndex = if (results.isEmpty()) -1 else 0,
                )
            }
        }
    }

    private suspend fun changeMatchCandidates(
        provider: MatchProvider,
        query: String,
        platformId: String,
    ): Pair<List<GameCandidate>, Boolean> {
        if (provider != MatchProvider.SCREENSCRAPER) return titleSearches.searchByTitle(provider, query, platformId) to false
        if (!matchEvidence.searchesEveryPlatformFirst(provider, platformId)) {
            val onPlatform = titleSearches.searchByTitle(provider, query, platformId)
            if (onPlatform.isNotEmpty()) return onPlatform to false
        }
        _uiState.update { it.copy(changeMatchSearchingEveryPlatform = true) }
        val everyPlatform = titleSearches.remember(provider, query, scope = "every-platform:$platformId") {
            matchEvidence.searchScreenScraperOnAnyPlatform(query, preferredPlatformId = platformId)
        }
        return everyPlatform to everyPlatform.isNotEmpty()
    }

    override fun confirmMatch(index: Int) {
        val state = _uiState.value
        val provider = state.matchProvider ?: return
        val candidate = state.changeMatchResults.getOrNull(index) ?: return
        changeMatchJob?.cancel()
        cancelLoad()

        forgetMatches(provider)
        _uiState.update {
            it.copy(

                match = GameMatch(candidate, MatchTier.SAVED_PROVIDER_ID, userConfirmed = true),
                matchFailed = false,
                changeMatchOpen = false,
                changeMatchResults = emptyList(),
                changeMatchIndex = 0,
                message = "Matched as ${candidate.title}",
            )
        }
        viewModelScope.launch {
            gameRepository.updateProviderMatch(gameId, provider.name, candidate.providerGameId.toLongOrNull())
            _uiState.update { it.copy(game = gameRepository.getById(gameId) ?: it.game) }
            loadResults()
        }
    }

    override fun forgetMatch() {
        val provider = _uiState.value.matchProvider ?: return
        cancelLoad()
        forgetMatches(provider)
        _uiState.update { it.copy(match = null, changeMatchOpen = false, actionsOpen = false) }
        viewModelScope.launch {
            gameRepository.updateProviderMatch(gameId, provider.name, null)
            _uiState.update { it.copy(game = gameRepository.getById(gameId) ?: it.game) }

            loadResults()
        }
    }

    override fun openSearch() = _uiState.update {
        it.copy(searchOpen = true, queryDraft = it.query, actionsOpen = false, showFileInfo = false)
    }

    override fun onQueryDraftChanged(text: String) = _uiState.update { it.copy(queryDraft = text.take(MAX_QUERY_LENGTH)) }

    override fun cancelSearch() = _uiState.update { it.copy(searchOpen = false, queryDraft = it.query) }

    override fun submitSearch() {
        val state = _uiState.value
        val submitted = state.queryDraft.trim().ifBlank { gameTitle() }
        val unchanged = StudioQuery.sameQuery(submitted, state.query)
        _uiState.update {
            it.copy(
                searchOpen = false,
                query = submitted,
                queryDraft = submitted,
                queryIsCustom = !StudioQuery.sameQuery(submitted, gameTitle()),
            )
        }
        if (!unchanged) {
            loadResults()
        }
    }

    override fun resetSearchToTitle() {
        val title = gameTitle()
        if (StudioQuery.sameQuery(title, _uiState.value.query)) {
            _uiState.update { it.copy(searchOpen = false, queryDraft = title, query = title, queryIsCustom = false) }
            return
        }
        _uiState.update {
            it.copy(searchOpen = false, query = title, queryDraft = title, queryIsCustom = false)
        }
        loadResults()
    }

    private fun activeResults(): List<StudioArt> = activeKey?.let { resultCache[it] }.orEmpty()

    override fun nextPage() = goToPage(_uiState.value.page + 1)

    override fun previousPage() = goToPage(_uiState.value.page - 1)

    private fun goToPage(index: Int) {
        val key = activeKey ?: return
        val all = activeResults()
        if (index < 0 || index * _uiState.value.pageSize >= all.size) return
        showPage(all, index, key, generation)
    }

    override fun openCandidate(index: Int) {
        val art = _uiState.value.results.getOrNull(index) ?: return
        _uiState.update { it.copy(candidate = art, gridIndex = index) }

        if (tab().kind == ArtworkKind.MANUAL) {
            _uiState.update {
                it.copy(manualDownloading = true, candidateManualPath = null, manualPage = 0, manualPageCount = 0)
            }
            viewModelScope.launch(ioDispatcher) {
                val tmp = downloadToCache(art.url, ".pdf")
                _uiState.update { it.copy(manualDownloading = false, candidateManualPath = tmp?.absolutePath) }
            }
        }
    }

    override fun toggleSelection(index: Int) = _uiState.update { s ->
        val art = s.results.getOrNull(index)
        if (art == null || !s.selectsMultiple) return@update s
        val key = StudioArtKey.of(STUDIO_TABS[s.tabIndex].kind, art)
        when (s.tileMarkOf(art)) {
            StudioTileMark.QUEUED, StudioTileMark.DOWNLOADING, StudioTileMark.FAILED -> s

            StudioTileMark.CURRENT   -> s
            StudioTileMark.ADDED     -> s.copy(gridIndex = index, removals = s.removals + (key to art))
            StudioTileMark.TO_REMOVE -> s.copy(gridIndex = index, removals = s.removals - key)
            StudioTileMark.PICKED    -> s.copy(gridIndex = index, selection = s.selection - key)
            StudioTileMark.NONE      -> s.copy(gridIndex = index, selection = s.selection + (key to art))
        }
    }

    override fun applyChanges() = _uiState.update { s ->
        val over = s.overCapacityBy
        when {
            !s.queueSummary.hasChanges -> s
            over > 0 -> s.copy(
                actionsOpen = false,
                message = STUDIO_TABS.getOrNull(s.tabIndex)?.kind?.let { kind ->
                    val capacity = com.psplauncher.feature.artwork.store.ArtworkFileNaming.MAX_SORT_ORDER + 1
                    "A game holds ${studioAssetCount(kind, capacity)} at most — uncheck $over to apply"
                } ?: s.message,
            )
            else -> s.copy(applyConfirmOpen = true, applyConfirmIndex = 0, actionsOpen = false)
        }
    }

    fun resolveApplyConfirm(choice: StudioApplyChoice) {
        _uiState.update { it.copy(applyConfirmOpen = false) }
        if (choice == StudioApplyChoice.APPLY) {
            val kind = tab().kind
            commit { it.kind == kind }
        }
    }

    private fun moveApplyConfirmCursor(delta: Int) = _uiState.update {
        it.copy(applyConfirmIndex = (it.applyConfirmIndex + delta).mod(StudioApplyChoice.entries.size))
    }

    fun resolveReplacePrompt(choice: StudioReplaceChoice) {
        _uiState.update { it.copy(replacePromptOpen = false) }
        if (choice == StudioReplaceChoice.REPLACE) performApplyCandidate()
    }

    private fun moveReplacePromptCursor(delta: Int) = _uiState.update {
        it.copy(replacePromptIndex = (it.replacePromptIndex + delta).mod(StudioReplaceChoice.entries.size))
    }

    override fun openAssetManager() = _uiState.update {
        if (!it.selectsMultiple) it else it.copy(managerOpen = true, managerIndex = 0, actionsOpen = false)
    }

    override fun closeAssetManager() = _uiState.update { it.copy(managerOpen = false) }

    override fun focusManagedAsset(index: Int) = _uiState.update {
        if (index in it.managedAssets.indices) it.copy(managerIndex = index) else it
    }

    private fun moveManagerCursor(delta: Int) = _uiState.update {
        val n = it.managedAssets.size
        if (n == 0) it else it.copy(managerIndex = (it.managerIndex + delta).coerceIn(0, n - 1))
    }

    override fun moveManagedAsset(delta: Int) {
        val s = _uiState.value
        val from = s.managerIndex
        val to = from + delta
        if (from !in s.managedAssets.indices || to !in s.managedAssets.indices) return
        val order = s.managedAssets.map { it.sortOrder }.toMutableList()
        order.add(to, order.removeAt(from))
        commitOrder(order, cursor = to)
    }

    override fun makeManagedAssetPrimary() {
        val s = _uiState.value
        val from = s.managerIndex
        if (from <= 0 || from !in s.managedAssets.indices) return
        val order = s.managedAssets.map { it.sortOrder }.toMutableList()
        order.add(0, order.removeAt(from))
        commitOrder(order, cursor = 0)
    }

    private fun commitOrder(order: List<Int>, cursor: Int) {
        if (_uiState.value.managerBusy) return
        val kind = tab().kind
        val gid = gameId
        _uiState.update { it.copy(managerBusy = true) }
        viewModelScope.launch {
            try {
                routingStore.reorderAssets(gid, kind, order)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Studio asset reorder failed")
                _uiState.update { it.copy(message = "Could not reorder artwork") }
            }

            if (gid == gameId && kind == tab().kind) refreshCurrent()
            _uiState.update {
                it.copy(
                    managerBusy = false,
                    managerIndex = cursor.coerceIn(0, (it.managedAssets.size - 1).coerceAtLeast(0)),
                )
            }
        }
    }

    override fun resolveConfirm(index: Int) {
        when (_uiState.value.confirmPrompt?.kind) {
            StudioConfirmKind.APPLY   -> StudioApplyChoice.entries.getOrNull(index)?.let(::resolveApplyConfirm)
            StudioConfirmKind.REPLACE -> StudioReplaceChoice.entries.getOrNull(index)?.let(::resolveReplacePrompt)
            null -> Unit
        }
    }

    override fun dismissConfirm() {
        when (_uiState.value.confirmPrompt?.kind) {
            StudioConfirmKind.APPLY   -> resolveApplyConfirm(StudioApplyChoice.CANCEL)
            StudioConfirmKind.REPLACE -> resolveReplacePrompt(StudioReplaceChoice.CANCEL)
            null -> Unit
        }
    }

    private fun moveConfirmCursor(delta: Int) {
        when (_uiState.value.confirmPrompt?.kind) {
            StudioConfirmKind.APPLY   -> moveApplyConfirmCursor(delta)
            StudioConfirmKind.REPLACE -> moveReplacePromptCursor(delta)
            null -> Unit
        }
    }

    private fun commit(which: (StudioArtKey) -> Boolean) {
        val removals = _uiState.value.removals.filterKeys(which)
        if (removals.isEmpty()) {
            enqueue(which)
            return
        }
        _uiState.update { it.copy(removals = it.removals - removals.keys, actionsOpen = false) }
        viewModelScope.launch {
            removeStored(removals)
            enqueue(which)
        }
    }

    private suspend fun removeStored(removals: Map<StudioArtKey, StudioArt>) {
        val gid = gameId
        var failed = 0
        for ((kind, entries) in removals.entries.groupBy { it.key.kind }) {
            val library = StudioLibraryAssets.of(kind, routingStore.studioAssets(gid, kind))
            val positions = entries.flatMap { library.sortOrdersHolding(it.value) }.distinct().sortedDescending()
            for (position in positions) {
                val removed = try {
                    routingStore.deleteAssetAt(gid, kind, position)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Studio asset removal failed")
                    false
                }
                if (!removed) failed++
            }
        }
        _uiState.update { s ->
            s.copy(

                queue = s.queue.filterNot { it.gameId == gid && it.key in removals.keys && it.state == StudioQueueState.ADDED },
                message = if (failed > 0) "Some artwork could not be removed" else s.message,
            )
        }
        if (gid == gameId) refreshCurrent()
    }

    override fun retryFailed() {
        val kind = tab().kind
        _uiState.update { s ->
            s.copy(
                actionsOpen = false,
                queue = s.queue.map { if (it.isFailedOn(kind)) it.copy(state = StudioQueueState.QUEUED) else it },
            )
        }
        drainQueue()
    }

    override fun removeFailed() {
        val kind = tab().kind
        _uiState.update { s -> s.copy(actionsOpen = false, queue = s.queue.filterNot { it.isFailedOn(kind) }) }
    }

    private fun StudioQueueItem.isFailedOn(kind: ArtworkKind) =
        gameId == this@ArtworkStudioViewModel.gameId && key.kind == kind && state == StudioQueueState.FAILED

    override fun resolveLeavePrompt(choice: StudioLeaveChoice) {
        _uiState.update { it.copy(leavePromptOpen = false) }
        when (choice) {
            StudioLeaveChoice.APPLY   -> { commit { true }; close() }
            StudioLeaveChoice.DISCARD -> { _uiState.update { it.copy(selection = emptyMap(), removals = emptyMap()) }; close() }
            StudioLeaveChoice.STAY    -> Unit
        }
    }

    private fun moveLeavePromptCursor(delta: Int) = _uiState.update {
        it.copy(leavePromptIndex = (it.leavePromptIndex + delta).mod(StudioLeaveChoice.entries.size))
    }

    private fun enqueue(which: (StudioArtKey) -> Boolean) {
        _uiState.update { s ->
            val picked = s.selection.filterKeys(which)
            if (picked.isEmpty()) return@update s
            s.copy(
                actionsOpen = false,
                selection = s.selection - picked.keys,
                queue = s.queue + picked.map { (key, art) -> StudioQueueItem(gameId, key, art, StudioQueueState.QUEUED) },
            )
        }
        drainQueue()
    }

    private fun drainQueue() {
        if (queueJob?.isActive == true) return
        queueJob = viewModelScope.launch {
            while (true) {
                val next = _uiState.value.queue.firstOrNull { it.state == StudioQueueState.QUEUED } ?: break
                setQueueState(next, StudioQueueState.DOWNLOADING)
                val path = try {
                    routingStore.studioAppendFromUrl(
                        next.gameId, next.key.kind, next.art.url, next.art.provider, next.art.providerAssetId,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Studio queue download failed")
                    null
                }
                setQueueState(next, if (path != null) StudioQueueState.ADDED else StudioQueueState.FAILED)
                if (path != null && next.gameId == gameId && next.key.kind == tab().kind) refreshCurrent()
            }
        }
    }

    private fun setQueueState(item: StudioQueueItem, state: StudioQueueState) = _uiState.update { s ->
        s.copy(queue = s.queue.map { if (it.gameId == item.gameId && it.key == item.key) it.copy(state = state) else it })
    }

    private fun downloadToCache(url: String, suffix: String): java.io.File? = runCatching {
        val tmp = java.io.File.createTempFile("studio_", suffix, appCacheDir)
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    total += n
                    if (total > 60L * 1024 * 1024) error("preview download too large")
                    out.write(buf, 0, n)
                }
            }
        }
        tmp.takeIf { it.length() > 0 } ?: run { tmp.delete(); null }
    }.onFailure { Timber.w(it, "Candidate preview download failed") }.getOrNull()

    override fun onManualPageCount(count: Int) = _uiState.update {
        it.copy(manualPageCount = count, manualPage = it.manualPage.coerceIn(0, (count - 1).coerceAtLeast(0)))
    }

    override fun manualPreviousPage() = _uiState.update {
        it.copy(manualPage = (it.manualPage - 1).coerceAtLeast(0))
    }

    override fun manualNextPage() = _uiState.update {
        it.copy(manualPage = (it.manualPage + 1).coerceAtMost((it.manualPageCount - 1).coerceAtLeast(0)))
    }

    override fun requestLocalPick() = _uiState.update { it.copy(localPickKind = tab().kind) }
    fun consumeLocalPick() = _uiState.update { it.copy(localPickKind = null) }

    fun applyLocal(uri: Uri) {
        val kind = tab().kind
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true) }

            val tmp = withContext(ioDispatcher) {
                runCatching {
                    val suffix = "." + (appContext.contentResolver.getType(uri)?.substringAfterLast('/') ?: "bin")
                    java.io.File.createTempFile("studio_local_", suffix, appCacheDir).also { f ->
                        appContext.contentResolver.openInputStream(uri)?.use { input ->
                            f.outputStream().use { input.copyTo(it) }
                        } ?: run { f.delete(); return@runCatching null }
                    }
                }.getOrNull()
            }
            val path = if (tmp != null) {
                routingStore.studioApplyFromFile(gameId, kind, tmp, provider = "Local file", originUrl = null)
            } else {
                artworkStore.saveVersionedFromUri(gameId, kind, uri)
            }
            finishApply(kind, path, "Local file")
        }
    }

    override fun applyCandidate() {
        val s = _uiState.value
        val art = s.candidate ?: return
        if (!s.selectsMultiple && s.library.holds(tab().kind, art)) {
            _uiState.update { it.copy(replacePromptOpen = true, replacePromptIndex = 0) }
            return
        }
        performApplyCandidate()
    }

    private fun performApplyCandidate() {
        val art = _uiState.value.candidate ?: return
        val kind = tab().kind
        val manualFile = _uiState.value.candidateManualPath?.let { java.io.File(it) }
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true) }

            val path = if (kind == ArtworkKind.MANUAL && manualFile?.exists() == true) {
                routingStore.studioApplyFromFile(gameId, kind, manualFile, provider = art.provider, originUrl = art.url)
            } else {
                routingStore.studioApplyFromUrl(
                    gameId, kind, art.url, provider = art.provider, providerAssetId = art.providerAssetId,
                )
            }
            _uiState.update { it.copy(candidateManualPath = null) }
            finishApply(kind, path, art.provider)
        }
    }

    override fun openActions() {
        val sgdb = sgdbActive()
        val s = _uiState.value
        if (s.currentUri == null && !sgdb && s.matchProvider == null && !s.canPreviewFocused &&
            !s.queueSummary.hasChanges && s.queueSummary.failed == 0
        ) return
        viewModelScope.launch {
            val info = routingStore.studioInfo(gameId, tab().kind)
            _uiState.update {
                val opened = it.copy(
                    info = info, actionsOpen = true, actionsIndex = 0, showFileInfo = false,
                    sgdbSourceActive = sgdb,
                )
                opened.copy(actionsSelectedAction = opened.availableActions.getOrNull(0))
            }
        }
    }

    override fun closeActions() = _uiState.update { it.copy(actionsOpen = false, showFileInfo = false) }

    private fun moveActionsCursor(delta: Int) = _uiState.update {
        val actions = it.availableActions
        val n = actions.size
        if (n == 0) {
            it
        } else {
            val newIndex = (it.resolvedActionsIndex + delta).mod(n)
            it.copy(actionsIndex = newIndex, actionsSelectedAction = actions.getOrNull(newIndex))
        }
    }

    override fun runAction(action: StudioAction) {
        when (action) {
            StudioAction.APPLY_CHANGES    -> applyChanges()
            StudioAction.PREVIEW          -> { closeActions(); openCandidate(_uiState.value.gridIndex) }
            StudioAction.RETRY_FAILED     -> retryFailed()
            StudioAction.MANAGE_ASSETS    -> openAssetManager()
            StudioAction.REMOVE_FAILED    -> removeFailed()
            StudioAction.CROP             -> beginCrop()
            StudioAction.CROP_BEFORE_APPLY -> beginCropForCandidate()
            StudioAction.RESTORE_PREVIOUS -> restorePrevious()
            StudioAction.RESET_DEFAULT    -> resetToScrapedDefault()
            StudioAction.CLEAR            -> { closeActions(); clearCurrent() }
            StudioAction.FILE_INFO        -> _uiState.update { it.copy(showFileInfo = true) }
            StudioAction.TOGGLE_MATURE    -> toggleNsfw()

            StudioAction.CHANGE_MATCH     -> onChangeMatchPressed()
            StudioAction.FORGET_MATCH     -> forgetMatch()
        }
    }

    private fun restorePrevious() {
        val kind = tab().kind
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true, actionsOpen = false) }
            val path = routingStore.restorePrevious(gameId, kind)
            if (path == null) {
                _uiState.update { it.copy(applying = false, message = "No previous version to restore") }
            } else {
                repointColumn(kind, path)
                _uiState.update {
                    it.copy(applying = false, currentUri = path, previewVersion = it.previewVersion + 1,
                        message = "${tab().label} restored to previous")
                }
            }
        }
    }

    private fun resetToScrapedDefault() {
        val kind = tab().kind
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true, actionsOpen = false) }
            val path = routingStore.resetToScrapedDefault(gameId, kind)
            if (path == null) {
                _uiState.update { it.copy(applying = false, message = "Could not re-download the scraped default") }
            } else {
                repointColumn(kind, path)
                _uiState.update {
                    it.copy(applying = false, currentUri = path, previewVersion = it.previewVersion + 1,
                        message = "${tab().label} reset to scraped default")
                }
            }
        }
    }

    override fun toggleCropPreview() {
        val next = !_uiState.value.cropPreviewEnabled
        _uiState.update { it.copy(cropPreviewEnabled = next) }
        viewModelScope.launch { cropPreviewPreferences.setEnabled(next) }
    }

    private fun beginCropForCandidate() {
        val s = _uiState.value
        val kind = tab().kind
        val art = s.results.getOrNull(s.gridIndex) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(actionsOpen = false, cropPreparing = true) }
            val temp = withContext(ioDispatcher) {
                routingStore.candidateToTemp(kind, art.url)
            }
            if (temp == null) {
                _uiState.update { it.copy(cropPreparing = false, message = "Could not download that pick to crop") }
                return@launch
            }
            val (w, h) = withContext(ioDispatcher) { decodeBounds(temp) }
            if (w <= 0 || h <= 0) {
                temp.delete()
                _uiState.update { it.copy(cropPreparing = false, message = "That pick is not an image this can crop") }
                return@launch
            }
            val override = routingStore.cropProfileOverride(gameId, kind)

            _uiState.update {
                it.copy(
                    cropPreparing = false, cropEditorPath = temp.absolutePath,
                    cropVideoSourcePath = null, cropCandidate = art,
                    cropProfileOverride = override,
                    cropSrcW = w, cropSrcH = h, cropZoom = 1f,
                    cropCenterX = 0.5f, cropCenterY = 0.5f,
                )
            }
            recomputeCropRect()
        }
    }

    private fun beginCrop() {
        val kind = tab().kind
        viewModelScope.launch {
            _uiState.update { it.copy(actionsOpen = false, cropPreparing = true) }
            val original = routingStore.originalToTemp(gameId, kind)
            if (original == null) {
                _uiState.update { it.copy(cropPreparing = false, message = "Could not open the original to crop") }
                return@launch
            }
            val prepared = withContext(ioDispatcher) {
                if (isVideoKind(kind)) {
                    val frame = extractVideoFrame(original)
                    if (frame == null) { original.delete(); null }
                    else Triple(frame.first.absolutePath, original.absolutePath, frame.second to frame.third)
                } else {
                    val (w, h) = decodeBounds(original)
                    Triple(original.absolutePath, null, w to h)
                }
            }
            if (prepared == null) {
                _uiState.update { it.copy(cropPreparing = false, message = "Could not open the original to crop") }
                return@launch
            }
            val (displayPath, videoPath, dims) = prepared
            val seed = _uiState.value.info?.cropRect?.let { parseCropRect(it) }
            val override = routingStore.cropProfileOverride(gameId, kind)
            _uiState.update {
                it.copy(
                    cropPreparing = false, cropEditorPath = displayPath, cropVideoSourcePath = videoPath,
                    cropProfileOverride = override,
                    cropSrcW = dims.first, cropSrcH = dims.second, cropZoom = 1f,
                    cropCenterX = seed?.let { r -> (r[0] + r[2]) / 2f } ?: 0.5f,
                    cropCenterY = seed?.let { r -> (r[1] + r[3]) / 2f } ?: 0.5f,
                )
            }
            recomputeCropRect()
        }
    }

    private fun isVideoKind(kind: ArtworkKind) = kind == ArtworkKind.ICON1 || kind == ArtworkKind.VIDEO

    private fun extractVideoFrame(video: java.io.File): Triple<java.io.File, Int, Int>? = runCatching {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.absolutePath)
            val durMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val pointsUs = if (durMs > 0)
                listOf(0.5, 0.33, 0.66, 0.15, 0.85).map { (durMs * it * 1000).toLong() }
            else listOf(0L)
            var best: android.graphics.Bitmap? = null
            var bestLuma = -1.0
            for (us in pointsUs) {
                val f = retriever.getFrameAtTime(us, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: continue
                val luma = averageLuma(f)
                if (luma > bestLuma) { best?.recycle(); best = f; bestLuma = luma } else f.recycle()
                if (bestLuma > 0.12) break
            }
            val frame = best ?: return null
            val out = java.io.File.createTempFile("studio_frame_", ".png", appCacheDir)
            out.outputStream().use { frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            val dims = Triple(out, frame.width, frame.height)
            frame.recycle()
            dims
        } finally {
            runCatching { retriever.release() }
        }
    }.onFailure { Timber.w(it, "Video frame extraction failed") }.getOrNull()

    private fun averageLuma(bmp: android.graphics.Bitmap): Double {
        val stepX = (bmp.width / 16).coerceAtLeast(1)
        val stepY = (bmp.height / 16).coerceAtLeast(1)
        var sum = 0.0; var n = 0
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                sum += (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)) / 255.0
                n++; x += stepX
            }
            y += stepY
        }
        return if (n > 0) sum / n else 0.0
    }

    private fun recomputeCropRect() = _uiState.update { s ->
        if (s.cropSrcW <= 0 || s.cropSrcH <= 0) return@update s
        val srcAspect = s.cropSrcW.toFloat() / s.cropSrcH
        val profile = CropProfileRegistry.Default.resolve(
            tab().kind, s.game?.platformId, s.game?.region, s.cropProfileOverride,
        )
        val target = profile.aspect ?: srcAspect

        var wN: Float; var hN: Float
        if (target >= srcAspect) { wN = 1f; hN = srcAspect / target } else { hN = 1f; wN = target / srcAspect }
        wN /= s.cropZoom; hN /= s.cropZoom
        val cx = s.cropCenterX.coerceIn(wN / 2f, 1f - wN / 2f)
        val cy = s.cropCenterY.coerceIn(hN / 2f, 1f - hN / 2f)
        s.copy(
            cropCenterX = cx, cropCenterY = cy,
            cropL = cx - wN / 2f, cropT = cy - hN / 2f, cropR = cx + wN / 2f, cropB = cy + hN / 2f,
        )
    }

    override fun openCropOptions() {
        val s = _uiState.value

        val rows = buildList {
            if (cropPreviewChromeFor(tab().kind) != null) add(CropOption.PREVIEW)
            add(CropOption.SHAPE_PLATFORM_DEFAULT)
            add(CropOption.SHAPE_ORIGINAL_IMAGE)
        }
        val current = CropShapeChoice.of(s.cropProfileOverride)
        _uiState.update {
            it.copy(
                cropOptionsOpen = true,
                cropOptionRows = rows,
                cropOptionsIndex = rows.indexOfFirst { row -> row.shape == current }.coerceAtLeast(0),
            )
        }
    }

    override fun closeCropOptions() = _uiState.update { it.copy(cropOptionsOpen = false) }

    override fun moveCropOptionsCursor(delta: Int) = _uiState.update {
        it.copy(cropOptionsIndex = (it.cropOptionsIndex + delta).coerceIn(0, it.cropOptionRows.lastIndex.coerceAtLeast(0)))
    }

    override fun activateCropOption(index: Int) {
        val row = _uiState.value.cropOptionRows.getOrNull(index) ?: return
        val shape = row.shape
        if (shape == null) {
            toggleCropPreview()
            _uiState.update { it.copy(cropOptionsOpen = false) }
            return
        }
        val kind = tab().kind
        val key = shape.storedKey
        viewModelScope.launch {
            routingStore.setCropProfileOverride(gameId, kind, key)
            _uiState.update { it.copy(cropProfileOverride = key, cropOptionsOpen = false) }
            recomputeCropRect()
        }
    }

    override fun panCrop(dx: Float, dy: Float) {
        _uiState.update { it.copy(cropCenterX = (it.cropCenterX + dx), cropCenterY = (it.cropCenterY + dy)) }
        recomputeCropRect()
    }

    override fun zoomCrop(factor: Float) {
        _uiState.update { it.copy(cropZoom = (it.cropZoom * factor).coerceIn(1f, 6f)) }
        recomputeCropRect()
    }

    override fun applyCrop() {
        val kind = tab().kind
        val s = _uiState.value
        val displayPath = s.cropEditorPath ?: return
        val videoPath = s.cropVideoSourcePath

        val candidate = s.cropCandidate
        val l = s.cropL; val t = s.cropT; val r = s.cropR; val b = s.cropB
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    applying = true, cropEditorPath = null, cropVideoSourcePath = null,
                    cropCandidate = null, cropProfileOverride = null, cropOptionsOpen = false,
                )
            }
            val baked = if (videoPath != null) {
                val out = java.io.File.createTempFile("studio_crop_", ".mp4", appCacheDir)
                val ok = videoSnapTranscoder.transcodeCropped(java.io.File(videoPath), out, l, t, r, b)
                java.io.File(videoPath).delete()
                if (ok) out else { out.delete(); null }
            } else {
                withContext(ioDispatcher) { bakeCrop(java.io.File(displayPath), l, t, r, b) }
            }
            java.io.File(displayPath).delete()
            if (baked == null) {
                _uiState.update { it.copy(applying = false, message = "Crop failed") }
                return@launch
            }
            val rect = "%.4f,%.4f,%.4f,%.4f".format(java.util.Locale.US, l, t, r, b)
            val path = routingStore.saveCropBaked(
                gameId, kind, baked, rect,
                candidateOriginUrl = candidate?.url,
                candidateProvider = candidate?.provider,
                candidateAssetId = candidate?.providerAssetId,
            )
            if (path == null) {
                _uiState.update { it.copy(applying = false, message = "Could not save the cropped artwork") }
            } else {
                repointColumn(kind, path)
                _uiState.update {
                    it.copy(applying = false, currentUri = path, previewVersion = it.previewVersion + 1,
                        message = "${tab().label} cropped")
                }
            }
        }
    }

    override fun cancelCrop() {
        val s = _uiState.value
        s.cropEditorPath?.let { runCatching { java.io.File(it).delete() } }
        s.cropVideoSourcePath?.let { runCatching { java.io.File(it).delete() } }
        _uiState.update {
            it.copy(
                cropEditorPath = null, cropVideoSourcePath = null,
                cropCandidate = null, cropProfileOverride = null, cropOptionsOpen = false,
            )
        }
    }

    private fun decodeBounds(file: java.io.File): Pair<Int, Int> {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
        return (opts.outWidth.takeIf { it > 0 } ?: 1) to (opts.outHeight.takeIf { it > 0 } ?: 1)
    }

    private fun parseCropRect(s: String): FloatArray? =
        s.split(',').mapNotNull { it.trim().toFloatOrNull() }.takeIf { it.size == 4 }?.toFloatArray()

    private fun bakeCrop(src: java.io.File, left: Float, top: Float, right: Float, bottom: Float): java.io.File? =
        runCatching {
            val full = android.graphics.BitmapFactory.decodeFile(src.absolutePath) ?: return null
            val w = full.width; val h = full.height
            val x = (left * w).toInt().coerceIn(0, w - 1)
            val y = (top * h).toInt().coerceIn(0, h - 1)
            val cw = ((right - left) * w).toInt().coerceIn(1, w - x)
            val ch = ((bottom - top) * h).toInt().coerceIn(1, h - y)
            val cropped = android.graphics.Bitmap.createBitmap(full, x, y, cw, ch)
            if (cropped != full) full.recycle()
            val out = java.io.File.createTempFile("studio_crop_", ".png", appCacheDir)
            out.outputStream().use { cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            cropped.recycle()
            out.takeIf { it.length() > 0 } ?: run { out.delete(); null }
        }.onFailure { Timber.w(it, "bakeCrop failed") }.getOrNull()

    private suspend fun repointColumn(kind: ArtworkKind, path: String?) {
        when (kind) {
            ArtworkKind.ICON           -> gameRepository.updateIconArt(gameId, path)
            ArtworkKind.BOX_ART        -> gameRepository.updateBoxArtTile(gameId, path)
            ArtworkKind.BOX_3D         -> gameRepository.updateBox3dArt(gameId, path)
            ArtworkKind.PHYSICAL_MEDIA -> gameRepository.updatePhysicalMediaArt(gameId, path)
            ArtworkKind.HERO           -> gameRepository.updateHeroArt(gameId, path)
            ArtworkKind.BACKGROUND     -> gameRepository.updateBoxArt(gameId, path)
            ArtworkKind.LOGO           -> gameRepository.updateLogoArt(gameId, path)
            else                       -> Unit
        }
    }

    private suspend fun finishApply(kind: ArtworkKind, path: String?, provider: String) {
        if (path == null) {
            _uiState.update { it.copy(applying = false, message = "Could not apply — download or file was rejected") }
            return
        }

        repointColumn(kind, path)
        refreshLibrary()
        val game = gameRepository.getById(gameId)
        _uiState.update {
            it.copy(
                game = game,
                applying = false,
                candidate = null,
                currentUri = path,
                previewVersion = it.previewVersion + 1,
                message = "${tab().label} updated from $provider",
            )
        }
    }

    fun clearCurrent() {
        val kind = tab().kind
        viewModelScope.launch {
            routingStore.clearArtwork(gameId, kind)
            repointColumn(kind, null)
            refreshLibrary()
            _uiState.update {
                it.copy(currentUri = null, info = null, previewVersion = it.previewVersion + 1,
                    message = "${tab().label} cleared")
            }
        }
    }

    override fun dismissCandidate() {
        _uiState.value.candidateManualPath?.let { runCatching { java.io.File(it).delete() } }
        _uiState.update {
            it.copy(
                candidate = null, candidateManualPath = null, manualDownloading = false,
                manualPage = 0, manualPageCount = 0,

                replacePromptOpen = false,
            )
        }
    }
    override fun dismissMessage() = _uiState.update { it.copy(message = null) }
    fun close() {
        cancelBackgroundResolutions()

        cancelLoad()
        changeMatchJob?.cancel()
        _uiState.update {
            it.copy(
                closed = true,
                resultsLoading = false,
                changeMatchLoading = false,
                changeMatchSearchingEveryPlatform = false,
            )
        }
    }

    fun consumeClosed() = _uiState.update { it.copy(closed = false) }

    override fun handleGamepadAction(action: GamepadAction) {
        val s = _uiState.value

        if (s.searchOpen) {
            when (action) {
                GamepadAction.SELECT -> submitSearch()
                GamepadAction.BACK   -> cancelSearch()
                else -> Unit
            }
            return
        }

        if (s.changeMatchOpen) {
            if (s.changeMatchEditing) {
                stopChangeMatchEdit()
                if (action == GamepadAction.BACK) return
            }
            val picker = _uiState.value
            when (action) {
                GamepadAction.NAVIGATE_UP   -> moveChangeMatchCursor(-1)
                GamepadAction.NAVIGATE_DOWN -> moveChangeMatchCursor(+1)
                GamepadAction.CHANGE_SORT   -> startChangeMatchEdit()
                GamepadAction.SELECT        ->
                    if (picker.changeMatchIndex < 0) startChangeMatchEdit()
                    else confirmMatch(picker.changeMatchIndex)
                GamepadAction.BACK          -> cancelChangeMatch()
                else -> Unit
            }
            return
        }

        if (s.cropOptionsOpen) {
            when (action) {
                GamepadAction.NAVIGATE_UP   -> moveCropOptionsCursor(-1)
                GamepadAction.NAVIGATE_DOWN -> moveCropOptionsCursor(+1)
                GamepadAction.SELECT        -> activateCropOption(_uiState.value.cropOptionsIndex)
                GamepadAction.BACK          -> closeCropOptions()
                else -> Unit
            }
            return
        }

        if (s.cropEditorPath != null) {
            when (action) {
                GamepadAction.NAVIGATE_LEFT  -> panCrop(-CROP_PAN_STEP, 0f)
                GamepadAction.NAVIGATE_RIGHT -> panCrop(CROP_PAN_STEP, 0f)
                GamepadAction.NAVIGATE_UP    -> panCrop(0f, -CROP_PAN_STEP)
                GamepadAction.NAVIGATE_DOWN  -> panCrop(0f, CROP_PAN_STEP)
                GamepadAction.NEXT_CATEGORY  -> zoomCrop(1.1f)
                GamepadAction.PREV_CATEGORY  -> zoomCrop(1f / 1.1f)
                GamepadAction.SELECT         -> applyCrop()
                GamepadAction.BACK           -> cancelCrop()

                GamepadAction.OPEN_CONTEXT_MENU -> openCropOptions()
                else -> Unit
            }
            return
        }

        s.confirmPrompt?.let { prompt ->
            when (action) {
                GamepadAction.NAVIGATE_UP   -> moveConfirmCursor(-1)
                GamepadAction.NAVIGATE_DOWN -> moveConfirmCursor(+1)
                GamepadAction.SELECT        -> resolveConfirm(prompt.selectedIndex)
                GamepadAction.BACK          -> dismissConfirm()
                else -> Unit
            }
            return
        }

        if (s.leavePromptOpen) {
            when (action) {
                GamepadAction.NAVIGATE_UP   -> moveLeavePromptCursor(-1)
                GamepadAction.NAVIGATE_DOWN -> moveLeavePromptCursor(+1)
                GamepadAction.SELECT        -> resolveLeavePrompt(StudioLeaveChoice.entries[s.leavePromptIndex])
                GamepadAction.BACK          -> resolveLeavePrompt(StudioLeaveChoice.STAY)
                else -> Unit
            }
            return
        }

        if (s.managerOpen) {
            when (action) {
                GamepadAction.NAVIGATE_UP    -> moveManagerCursor(-1)
                GamepadAction.NAVIGATE_DOWN  -> moveManagerCursor(+1)
                GamepadAction.PREV_CATEGORY  -> moveManagedAsset(-1)
                GamepadAction.NEXT_CATEGORY  -> moveManagedAsset(+1)
                GamepadAction.SELECT         -> makeManagedAssetPrimary()
                GamepadAction.BACK           -> closeAssetManager()
                else -> Unit
            }
            return
        }
        if (s.actionsOpen) {
            val actions = s.availableActions
            when (action) {
                GamepadAction.NAVIGATE_UP   -> moveActionsCursor(-1)
                GamepadAction.NAVIGATE_DOWN -> moveActionsCursor(+1)
                GamepadAction.SELECT        -> actions.getOrNull(s.resolvedActionsIndex)?.let { runAction(it) }
                GamepadAction.BACK          ->
                    if (s.showFileInfo) _uiState.update { it.copy(showFileInfo = false) } else closeActions()
                else -> Unit
            }
            return
        }
        if (s.candidate != null) {
            when (action) {
                GamepadAction.SELECT -> applyCandidate()
                GamepadAction.BACK   -> dismissCandidate()

                GamepadAction.NAVIGATE_LEFT  -> if (s.candidateManualPath != null) manualPreviousPage()
                GamepadAction.NAVIGATE_RIGHT -> if (s.candidateManualPath != null) manualNextPage()
                else -> Unit
            }
            return
        }

        when (action) {
            GamepadAction.BACK -> when (s.zone) {
                StudioZone.TABS    ->
                    if (s.selection.isNotEmpty() || s.removals.isNotEmpty()) {
                        _uiState.update { it.copy(leavePromptOpen = true, leavePromptIndex = 0) }
                    }
                    else close()
                StudioZone.SOURCES -> _uiState.update { it.copy(zone = StudioZone.TABS) }
                StudioZone.GRID    -> _uiState.update { it.copy(zone = StudioZone.SOURCES) }
            }
            GamepadAction.NAVIGATE_LEFT -> when (s.zone) {
                StudioZone.TABS    -> cycleTab(-1)
                StudioZone.SOURCES -> cycleSource(-1)
                StudioZone.GRID    ->
                    if (s.gridIndex > 0) _uiState.update { it.copy(gridIndex = s.gridIndex - 1) }
            }
            GamepadAction.NAVIGATE_RIGHT -> when (s.zone) {
                StudioZone.TABS    -> cycleTab(+1)
                StudioZone.SOURCES -> cycleSource(+1)
                StudioZone.GRID    ->
                    if (s.gridIndex < s.results.lastIndex) _uiState.update { it.copy(gridIndex = s.gridIndex + 1) }
            }
            GamepadAction.NAVIGATE_UP -> if (s.zone == StudioZone.GRID && s.gridIndex >= s.gridColumns) {
                _uiState.update { it.copy(gridIndex = s.gridIndex - s.gridColumns) }
            }
            GamepadAction.NAVIGATE_DOWN -> if (s.zone == StudioZone.GRID &&
                s.gridIndex + s.gridColumns <= s.results.lastIndex
            ) {
                _uiState.update { it.copy(gridIndex = s.gridIndex + s.gridColumns) }
            }
            GamepadAction.PREV_CATEGORY -> when (s.zone) {
                StudioZone.TABS    -> cycleTab(-1)
                StudioZone.SOURCES -> cycleSource(-1)
                StudioZone.GRID    -> previousPage()
            }
            GamepadAction.NEXT_CATEGORY -> when (s.zone) {
                StudioZone.TABS    -> cycleTab(+1)
                StudioZone.SOURCES -> cycleSource(+1)
                StudioZone.GRID    -> nextPage()
            }
            GamepadAction.SELECT -> when (s.zone) {
                StudioZone.TABS    -> _uiState.update { it.copy(zone = StudioZone.SOURCES) }

                StudioZone.SOURCES ->
                    if (sourcesForTab().getOrNull(s.sourceIndex) == StudioSource.LOCAL) requestLocalPick()
                    else _uiState.update { it.copy(zone = StudioZone.GRID) }

                StudioZone.GRID    -> if (s.selectsMultiple) toggleSelection(s.gridIndex) else openCandidate(s.gridIndex)
            }

            GamepadAction.CHANGE_SORT, GamepadAction.OPEN_SEARCH -> openSearch()

            GamepadAction.HOME -> applyChanges()

            GamepadAction.OPEN_CONTEXT_MENU -> openActions()
        }
    }
}
