package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.ui.components.MenuGroup
import com.psplauncher.core.ui.components.MenuRow
import com.psplauncher.core.ui.components.MenuSelect
import com.psplauncher.core.ui.components.MenuState
import com.psplauncher.core.ui.components.chose
import com.psplauncher.core.ui.components.rowsShown
import com.psplauncher.core.domain.model.PlatformIds.ANDROID as ANDROID_PLATFORM_ID

import com.psplauncher.core.domain.model.PlatformIds.WINDOWS as WINDOWS_PLATFORM_ID

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.data.database.dao.PlatformDao
import com.psplauncher.core.data.database.entity.HiddenPlacementEntity
import com.psplauncher.core.data.database.entity.PlatformEntity
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.data.repository.CategoryRepositoryImpl
import com.psplauncher.core.data.repository.CollectionRepository
import com.psplauncher.core.data.repository.ControllerMappingRepository
import com.psplauncher.core.data.repository.CustomIconStore
import com.psplauncher.core.data.repository.MemoryCardRepository
import com.psplauncher.core.data.repository.PfpThemeStore
import com.psplauncher.core.ui.icons.CustomIcon
import com.psplauncher.core.ui.icons.GifFrameProbe
import com.psplauncher.core.ui.media.bundledDefaultUri
import com.psplauncher.core.ui.media.resolveBootAudio
import com.psplauncher.core.ui.media.resolveGameBootAudio
import com.psplauncher.themekit.CustomizableIcons
import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.ControllerHintPolicy
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameCollection
import com.psplauncher.core.domain.model.GameContentType
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.feature.xmb.ui.detail.DetailPanelContent
import com.psplauncher.feature.xmb.ui.detail.DetailPanelPage
import com.psplauncher.feature.xmb.ui.detail.detailPanelContentFor
import com.psplauncher.feature.xmb.ui.detail.stepPanelPage
import com.psplauncher.core.domain.model.HiddenPlacement
import com.psplauncher.core.domain.model.HideLocationType
import com.psplauncher.core.domain.model.IconDisplayMode
import com.psplauncher.core.domain.model.PlayState
import com.psplauncher.core.domain.model.VideoSnapPlacement
import com.psplauncher.core.domain.model.MemoryCard
import com.psplauncher.core.domain.model.MusicTrack
import com.psplauncher.core.domain.model.XmbColorScheme
import com.psplauncher.core.domain.model.XmbPalette
import com.psplauncher.core.ui.theme.withWaveTint
import com.psplauncher.core.domain.model.displayLabel
import com.psplauncher.core.domain.model.resolve
import com.psplauncher.core.domain.repository.GameRepository
import com.psplauncher.core.ui.icons.GameIconStyle
import com.psplauncher.core.ui.notification.AndroidNotice
import com.psplauncher.core.ui.notification.AndroidNotifications
import com.psplauncher.core.ui.notification.BackgroundTaskNotifier
import com.psplauncher.feature.artwork.match.MetadataApply
import com.psplauncher.feature.artwork.match.MetadataApplyPolicy
import com.psplauncher.feature.artwork.match.MetadataField
import com.psplauncher.feature.xmb.ui.detail.ManualViewerUi
import com.psplauncher.feature.xmb.ui.detail.MetadataPreviewUi
import com.psplauncher.feature.artwork.store.ArtworkKind
import com.psplauncher.core.ui.notification.SystemToasts
import com.psplauncher.core.ui.notification.ToastKind
import com.psplauncher.core.ui.sound.MenuSound
import com.psplauncher.core.ui.theme.DefaultPFPColors
import com.psplauncher.core.ui.theme.PFPColors
import com.psplauncher.core.ui.wave.WaveStyle
import com.psplauncher.feature.appbar.AppCategoryRepository
import com.psplauncher.feature.appbar.CategorizedApp
import com.psplauncher.feature.appbar.LauncherShortcutRepository
import com.psplauncher.feature.launcher.LaunchDispatchResult
import com.psplauncher.feature.launcher.LaunchRecoveryAction
import com.psplauncher.feature.launcher.ResolvedLaunch
import com.psplauncher.feature.artwork.api.ArtworkRepository
import com.psplauncher.feature.library.scanner.LibraryScanner
import com.psplauncher.feature.library.scanner.ScanStatus
import com.psplauncher.feature.library.scanner.scanOutcomeMessage
import com.psplauncher.feature.xmb.R
import com.psplauncher.feature.xmb.gamepad.GamepadInputHandler
import com.psplauncher.feature.xmb.gamepad.ShoulderHold
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private fun XmbPalette.toPFPColors() = PFPColors(
    waveColor         = androidx.compose.ui.graphics.Color(waveColor),
    accentColor       = androidx.compose.ui.graphics.Color(accentColor),
    textPrimary       = androidx.compose.ui.graphics.Color(textColor),
    textSecondary     = androidx.compose.ui.graphics.Color(textColor).copy(alpha = 0.7f),
    backgroundOverlay = androidx.compose.ui.graphics.Color(0x88000000),
    selectedItem      = androidx.compose.ui.graphics.Color(accentColor),
    categoryBar       = androidx.compose.ui.graphics.Color(0x00000000),
    backgroundTop     = androidx.compose.ui.graphics.Color(backgroundTop),
    backgroundBottom  = androidx.compose.ui.graphics.Color(backgroundBottom),
)

data class XMBContextMenu(
    val state: MenuState<String>,

    val primaryId: String? = null,

    val platformId: String? = null,

    val isAllGames: Boolean = false,
    val gameId: Long? = null,
    val packageName: String? = null,

    val categoryContext: String? = null,

    val pendingAppAction: String? = null,

    val isAddMenu: Boolean = false,

    val collectionGameId: Long? = null,

    val collectionRowId: Long? = null,

    val shortcutId: String? = null,

    val launchIntentUri: String? = null,

    val musicFolderId: String? = null,
    val musicTrackId: String? = null,

    val playlistId: Long? = null,

    val playlistPickerTrackId: String? = null,

    val videoPlaylistId: Long? = null,

    val videoFileId: String? = null,

    val videoLibraryId: String? = null,

    val videoPlaylistPickerVideoId: String? = null,

    val bookFileId: String? = null,

    val photoFileId: String? = null,

    val photoLibraryId: String? = null,
) {
    val title: String get() = state.title
    val items: List<XMBContextMenuItem> get() = state.rows
    val subtitle: String? get() = state.subtitle
    val selectedIndex: Int? get() = state.selectedIndex
    val parent: MenuState<String>? get() = state.parent

    fun withSelected(index: Int): XMBContextMenu = copy(state = state.copy(selectedIndex = index))
}

typealias XMBContextMenuItem = MenuRow<String>

data class CollectionNameDialogState(
    val title: String,
    val initialText: String = "",

    val text: String = initialText,
    val forGameId: Long? = null,

    val renameCollectionId: Long? = null,

    val editTitleGameId: Long? = null,

    val editNoteGameId: Long? = null,

    val quickSearch: Boolean = false,

    val placeholder: String = "e.g. RPGs, Currently Playing",
    val confirmLabel: String = "Save",
)

data class InfoDialogState(
    val title: String,
    val message: String,
)

data class ColorSchemePickerState(
    val options: List<ColorSchemeOption>,
    val selectedIndex: Int = 0,
)

data class ColorSchemeOption(
    val scheme: XmbColorScheme?,
    val label: String,

    val sublabel: String?,
    val swatch: Long,
    val isCustom: Boolean = false,
)

data class CustomColorPickerState(
    val hue: Float,
    val saturation: Float,
    val brightness: Float,
    val selectedChannel: Int = 0,
)

data class XmbLayoutAdjustSession(
    val draft: com.psplauncher.themekit.XmbLayoutAdjust,
    val original: com.psplauncher.themekit.XmbLayoutAdjust,
    val bucketKey: String,
    val slidersVisible: Boolean = false,
)

data class CustomIconSession(
    val groups: List<com.psplauncher.themekit.IconSlot.Group>,
    val groupIndex: Int = 0,
    val slotIndex: Int = 0,
    val message: String? = null,

    val revision: Int = 0,
) {
    val group: com.psplauncher.themekit.IconSlot.Group get() = groups[groupIndex]

    val focusedSlot: com.psplauncher.themekit.IconSlot?
        get() = CustomizableIcons.group(group).getOrNull(slotIndex)
}

sealed interface AppPickerTarget {
    data class AndroidGames(val platformId: String) : AppPickerTarget

    data class CategoryShortcuts(val categoryId: String) : AppPickerTarget
}

data class AppPickerEntry(
    val packageName: String,
    val label: String,

    val icon: android.graphics.drawable.Drawable? = null,
)

const val PICKER_GRID_COLUMNS = 7

data class AppPickerState(
    val title: String,
    val target: AppPickerTarget,
    val apps: List<AppPickerEntry>,
    val selected: Set<String> = emptySet(),

    val initialSelected: Set<String> = emptySet(),

    val focusedIndex: Int = 0,
    val query: String = "",
    val searchActive: Boolean = false,
    val confirmingRemovals: Boolean = false,

    val confirmFocusedOption: Int = CONFIRM_CANCEL,

    val columns: Int = PICKER_GRID_COLUMNS,

    val usingTouch: Boolean = false,
) {
    companion object {
        const val CONFIRM_CANCEL = 0
        const val CONFIRM_REMOVE = 1
    }
}

sealed interface VideoNav {
    data object Root : VideoNav
    data object AllVideos : VideoNav

    data object Collections : VideoNav
    data object RecentlyWatched : VideoNav
    data object Favorites : VideoNav
    data object Playlists : VideoNav
    data class Playlist(val id: Long, val name: String) : VideoNav
    data object Libraries : VideoNav
    data class Library(val id: String, val name: String) : VideoNav
}

private val VideoNav.isVideoCollectionChild: Boolean
    get() = this == VideoNav.RecentlyWatched || this == VideoNav.Favorites || this == VideoNav.Playlists

sealed interface BooksNav {
    data object Root : BooksNav
    data object AllBooks : BooksNav
    data object Shelves : BooksNav
    data class Shelf(val id: String, val name: String) : BooksNav

    data object SeriesList : BooksNav

    data class Series(val name: String) : BooksNav
}

data class BookSeries(val name: String, val bookCount: Int, val coverUri: String?)

sealed interface PhotoNav {
    data object Root : PhotoNav
    data object AllPhotos : PhotoNav
    data object Albums : PhotoNav
    data class Library(val id: String, val name: String) : PhotoNav
}

data class PhotoViewerRequest(
    val photoId: String,
    val libraryId: String?,
    val openWallpaperPreview: Boolean = false,
)

sealed interface MusicNav {
    data object Root : MusicNav
    data object AllMusic : MusicNav
    data object Playlists : MusicNav
    data class Playlist(val id: Long, val name: String) : MusicNav
}

sealed interface MusicBrowserView {
    data object AllMusic : MusicBrowserView
    data object Playlists : MusicBrowserView
    data class Playlist(val id: Long, val name: String) : MusicBrowserView

    data object Artists : MusicBrowserView
    data object Albums : MusicBrowserView
    data class Artist(val name: String, val key: String) : MusicBrowserView
    data class Album(val name: String, val key: String) : MusicBrowserView
}

internal val MusicBrowserView.listsGroups: Boolean
    get() = this == MusicBrowserView.Artists || this == MusicBrowserView.Albums

data class MusicBrowserState(
    val view: MusicBrowserView,
    val title: String,
    val query: String = "",
    val rows: List<XMBItem> = emptyList(),
    val selectedIndex: Int = 0,

    val sortLabel: String? = null,

    val scrollToTopToken: Int = 0,
)

data class SearchState(
    val scope: SearchScope,
    val query: String = "",
    val rows: List<XMBItem> = emptyList(),
    val selectedIndex: Int = 0,

    val scrollToTopToken: Int = 0,

    val loaded: Boolean = false,

    val columns: Int = SEARCH_GRID_COLUMNS,
)

data class PlaylistNameDialogState(
    val title: String,
    val initialText: String = "",

    val text: String = initialText,
    val forTrackId: String? = null,

    val renamePlaylistId: Long? = null,

    val videoContext: Boolean = false,

    val forVideoId: String? = null,
)

data class MusicTrackPickerState(
    val playlistId: Long,
    val playlistName: String,
    val tracks: List<MusicTrack>,
    val selected: Set<String> = emptySet(),
    val selectedIndex: Int = 0,
)

enum class DrillOutStep {
    MUSIC,

    VIDEO_LIBRARY,
    VIDEO_PLAYLIST,
    VIDEO_COLLECTION_CHILD,
    VIDEO,

    PHOTO_LIBRARY,
    PHOTO,

    LIBRARY_SHELF,

    LIBRARY_SERIES,
    LIBRARY,

    PLATFORM_FOLDER,
}

@androidx.compose.runtime.Immutable

data class MediaCovers(
    val music: List<String> = emptyList(),
    val video: List<String> = emptyList(),
    val photo: List<String> = emptyList(),
    val books: List<String> = emptyList(),
)

data class XMBUiState(

    val categories: List<Category> = emptyList(),
    val selectedCategoryIndex: Int = 0,
    val platformGameCounts: Map<String, Int> = emptyMap(),

    val allGamesCount: Int = 0,

    val cardFanCovers: Map<String, List<String>> = emptyMap(),

    val shelfFanCovers: Map<String, List<String>> = emptyMap(),

    val favoritesCount: Int = 0,

    val missingCount: Int = 0,

    val playStateCounts: Map<PlayState, Int> = emptyMap(),
    val recentlyAddedCount: Int = 0,
    val selectedPlatformId: String? = null,

    val selectedCollectionId: Long? = null,

    val collections: List<GameCollection> = emptyList(),

    val musicNav: MusicNav = MusicNav.Root,
    val musicFolders: List<com.psplauncher.core.domain.model.MusicFolder> = emptyList(),

    val mediaCovers: MediaCovers = MediaCovers(),

    val musicPlaylists: List<com.psplauncher.core.domain.model.Playlist> = emptyList(),
    val videoPlaylists: List<com.psplauncher.core.domain.model.VideoPlaylist> = emptyList(),

    val gameSortMode: XmbSortMode = XmbSortMode.TITLE,
    val musicSortMode: XmbSortMode = XmbSortMode.TITLE,
    val videoSortMode: XmbSortMode = XmbSortMode.TITLE,
    val bookSortMode: XmbSortMode = XmbSortMode.TITLE,
    val sortLabel: String? = null,

    val musicPlayerVisible: Boolean = false,
    val musicPlayback: com.psplauncher.feature.xmb.music.MusicPlaybackState =
        com.psplauncher.feature.xmb.music.MusicPlaybackState(),

    val currentItems: List<XMBItem> = emptyList(),
    val selectedItemIndex: Int = 0,

    val letterJump: LetterJumpState? = null,

    val drillTitle: String? = null,

    val drillSiblings: List<XMBItem> = emptyList(),
    val drillSiblingIndex: Int = 0,

    val scrollToTopToken: Int = 0,

    val lastInputWasTouch: Boolean = false,

    val showContextMenuHint: Boolean = false,

    val idle: Boolean = false,

    val showAppDrawerHint: Boolean = false,

    val showSettingsHint: Boolean = false,

    val contextMenuHintEnabled: Boolean = ControllerHintPolicy.DEFAULT_ENABLED,

    val contextMenuHintDelaySeconds: Float = ControllerHintPolicy.DEFAULT_DELAY_SECONDS,
    val touchNavButtonMode: com.psplauncher.core.domain.model.TouchNavButtonMode =
        com.psplauncher.core.domain.model.TouchNavButtonMode.AUTO,

    val touchSensitivity: com.psplauncher.core.domain.model.TouchSensitivity =
        com.psplauncher.core.domain.model.TouchSensitivity.NORMAL,

    val waveStyle: WaveStyle = WaveStyle.ANIMATED,

    val respectBatterySaver: Boolean = true,

    val waveOverWallpaper: Boolean = false,

    val wallpaperAccent: Long? = null,
    val thermalThrottleAware: Boolean = true,
    val customWallpaperPath: String? = null,

    val motionWallpaperPath: String? = null,

    val showBootSequence: Boolean = true,

    val bootVideoPath: String? = null,
    val bootAudioPath: String? = null,

    val activeGameBoot: com.psplauncher.feature.launcher.GameBootRequest? = null,

    val gameBootIsPreview: Boolean = false,

    val discCeremony: DiscCeremonyState? = null,

    val startupPermissionsSettled: Boolean = false,

    val initialSetupDecided: Boolean = false,

    val activeSettingsScreen: String? = null,

    val settingsReturnTo: String? = null,

    val leftBacksOut: Boolean = true,
    val pendingSettingsAction: GamepadAction? = null,
    val activeAppDrawerFilter: String? = null,
    val pendingDrawerAction: GamepadAction? = null,

    val pendingDrawerTypedChar: String? = null,
    val pendingGameDetailAction: GamepadAction? = null,
    val isFetchingArtwork: Boolean = false,

    val artworkStudioGameId: Long? = null,

    val pendingArtworkStudioAction: GamepadAction? = null,

    val manualViewer: com.psplauncher.feature.xmb.ui.detail.ManualViewerUi? = null,

    val metadataPreview: com.psplauncher.feature.xmb.ui.detail.MetadataPreviewUi? = null,

    val metadataPreviewGameId: Long? = null,

    val activeGameId: Long? = null,

    val activeGameAutoLaunch: Boolean = false,

    val activeGameAction: String? = null,

    val activeGameDiscId: Long? = null,

    val activeAppId: Long? = null,

    val activeAppCollectionCategoryId: String = BuiltInCategory.GAMES,
    val pendingAppDetailAction: GamepadAction? = null,

    val videoNav: VideoNav = VideoNav.Root,
    val videoLibraries: List<com.psplauncher.core.domain.model.VideoLibrary> = emptyList(),
    val activeVideoId: String? = null,

    val resumeVideo: com.psplauncher.core.domain.model.Video? = null,

    val activeVideoAutoPlay: Boolean = false,
    val pendingVideoDetailAction: GamepadAction? = null,

    val photoNav: PhotoNav = PhotoNav.Root,
    val booksNav: BooksNav = BooksNav.Root,

    val continueBook: com.psplauncher.core.domain.model.Book? = null,
    val bookLibraries: List<com.psplauncher.core.domain.model.BookLibrary> = emptyList(),

    val bookSeries: List<BookSeries> = emptyList(),

    val defaultReader: String? = null,
    val defaultReaderLabel: String? = null,
    val photoLibraries: List<com.psplauncher.core.domain.model.PhotoLibrary> = emptyList(),
    val activePhotoViewer: PhotoViewerRequest? = null,
    val pendingPhotoViewerAction: GamepadAction? = null,

    val activeContextMenu: XMBContextMenu? = null,

    val colorSchemePicker: ColorSchemePickerState? = null,
    val customColorPicker: CustomColorPickerState? = null,

    val renameAppTarget: String? = null,
    val renameAppCurrent: String? = null,
    val renameAppText: String = "",

    val collectionNameDialog: CollectionNameDialogState? = null,

    val playlistNameDialog: PlaylistNameDialogState? = null,

    val musicTrackPicker: MusicTrackPickerState? = null,

    val musicBrowser: MusicBrowserState? = null,
    val search: SearchState? = null,

    val infoDialog: InfoDialogState? = null,

    val showWindowsSetupPrompt: Boolean = false,

    val launchRecovery: com.psplauncher.feature.launcher.LaunchRecoveryRequest? = null,

    val launchRecoveryCursor: Int = 0,

    val appPicker: AppPickerState? = null,

    val gamePickerCategoryId: String? = null,
    val pendingGamePickerAction: GamepadAction? = null,

    val iconStyle: GameIconStyle = GameIconStyle.PSP_RECTANGLE,

    val iconDisplayMode: IconDisplayMode = IconDisplayMode.DEFAULT,

    val iconDisplayModeByPlatform: Map<String, IconDisplayMode> = emptyMap(),

    val iconLegibility: com.psplauncher.core.domain.model.IconLegibilityStyle =
        com.psplauncher.core.domain.model.IconLegibilityStyle.DEFAULT,

    val fadeByDistance: Boolean = true,

    val cardArtGrid: Boolean = true,

    val recentsIncludeApps: Boolean = false,

    val textShadow: Boolean = true,

    val focusedGameVideo: com.psplauncher.feature.xmb.ui.FocusedGameVideo? = null,

    val snapPlacement: com.psplauncher.core.domain.model.VideoSnapPlacement =
        com.psplauncher.core.domain.model.VideoSnapPlacement.DEFAULT,

    val gameMetadataVisible: Boolean = true,

    val itemBackdropEnabled: Boolean = true,

    val focusedItemAccentArgb: Long? = null,

    val focusedItemBackdrop: String? = null,

    val recentFilter: RecentFilter = RecentFilter.ALL,

    val recentRailVisible: Boolean = false,

    val pillCursor: PillCursor? = null,

    val notificationsOpen: Boolean = false,

    val noticeCursor: Int = 0,

    val androidNotices: List<AndroidNotice> = emptyList(),

    val resumeGame: Game? = null,
    val panelPage: DetailPanelPage = DetailPanelPage.LOGO,
    val panelPageGameId: Long? = null,
    val librarySetupComplete: Boolean = false,
    val themeColors: PFPColors = DefaultPFPColors,

    val iconOverrides: Map<String, CustomIcon> = emptyMap(),

    val customIcons: Map<String, CustomIcon> = emptyMap(),

    val customIconSession: CustomIconSession? = null,

    val pendingThemeShareFile: java.io.File? = null,

    val pendingCustomIconsAction: GamepadAction? = null,

    val saveThemeNameDialog: PlaylistNameDialogState? = null,

    val layoutSpec: com.psplauncher.themekit.XmbLayoutSpec = com.psplauncher.themekit.XmbLayoutSpec.DEFAULT,

    val xmbScale: Float = 1f,

    val xmbLayoutAdjustMap: Map<String, com.psplauncher.themekit.XmbLayoutAdjust> = emptyMap(),

    val xmbLayoutAdjust: XmbLayoutAdjustSession? = null,
) {
    val isInSubItem: Boolean
        get() = drillOutStep != null

    val drillOutStep: DrillOutStep?
        get() = when {
            musicNav != MusicNav.Root -> DrillOutStep.MUSIC
            videoNav is VideoNav.Library -> DrillOutStep.VIDEO_LIBRARY
            videoNav is VideoNav.Playlist -> DrillOutStep.VIDEO_PLAYLIST
            videoNav.isVideoCollectionChild -> DrillOutStep.VIDEO_COLLECTION_CHILD
            videoNav != VideoNav.Root -> DrillOutStep.VIDEO
            photoNav is PhotoNav.Library -> DrillOutStep.PHOTO_LIBRARY
            photoNav != PhotoNav.Root -> DrillOutStep.PHOTO
            booksNav is BooksNav.Series -> DrillOutStep.LIBRARY_SERIES
            booksNav is BooksNav.Shelf -> DrillOutStep.LIBRARY_SHELF
            booksNav != BooksNav.Root -> DrillOutStep.LIBRARY
            selectedPlatformId != null || selectedCollectionId != null -> DrillOutStep.PLATFORM_FOLDER
            else -> null
        }

    val focusedItem: XMBItem?
        get() = currentItems.getOrNull(selectedItemIndex)

    val hoverPanelItem: XMBItem?
        get() = focusedItem?.takeIf {
            (it.isRealGame || onLastPlayedHome) && it.backdropArt.isNotEmpty()
        }

    val effectivePanelPage: DetailPanelPage
        get() = if (panelStripOpen) panelPage else DetailPanelPage.LOGO

    val panelStripOpen: Boolean
        get() = panelPageGameId != null && panelPageGameId == hoverPanelItem?.gameId

    val onLastPlayedHome: Boolean
        get() = categories.getOrNull(selectedCategoryIndex)?.id == BuiltInCategory.RECENTLY_PLAYED &&
            !isInSubItem &&
            !(recentFilter == RecentFilter.ALL && currentItems.isEmpty())

    val hoverPanelContent: DetailPanelContent?
        get() = hoverPanelItem?.let { item ->
            detailPanelContentFor(
                item = item,
                platformName = item.platformId?.uppercase().orEmpty(),
                videoUri = focusedGameVideo?.takeIf { it.gameId == item.gameId }?.uri,
            )
        }

    val canFilterRecents: Boolean
        get() = onLastPlayedHome

    val hoverPanelHasPages: Boolean
        get() = (hoverPanelContent?.pages?.size ?: 0) > 1

    val focusedItemHasContextMenu: Boolean
        get() = focusedItem?.hasContextMenu(this) == true

    val canSortCurrentList: Boolean
        get() = activeSortModes() != null

    val resolvedShowTouchButton: Boolean
        get() = when (touchNavButtonMode) {
            com.psplauncher.core.domain.model.TouchNavButtonMode.AUTO -> lastInputWasTouch
            com.psplauncher.core.domain.model.TouchNavButtonMode.ALWAYS_SHOW -> true
            com.psplauncher.core.domain.model.TouchNavButtonMode.ALWAYS_HIDE -> false
        }

    val overlayKeepsChrome: Boolean
        get() = (activeContextMenu != null || notificationsOpen) && !otherBlockingOverlay

    val noticeFocusables: List<NoticeFocus>
        get() = buildList {
            if (musicPlayback.track != null || resumeGame != null) add(NoticeFocus.Media)
            androidNotices.take(NOTICE_ROWS).forEach { add(NoticeFocus.Notice(it.key)) }
        }

    val focusedNotice: NoticeFocus?
        get() = noticeFocusables.let { rows ->
            if (rows.isEmpty()) null else rows[noticeCursor.coerceIn(0, rows.lastIndex)]
        }

    val shelfCards: List<ShelfCard>
        get() = buildList {
            if (favoritesCount > 0) add(ShelfCard.Favorites(favoritesCount))
            PlayState.entries.forEach { state ->
                playStateCounts[state]?.takeIf { it > 0 }?.let { add(ShelfCard.Marked(state, it)) }
            }
            if (recentlyAddedCount > 0) add(ShelfCard.RecentlyAdded(recentlyAddedCount))
        }

    fun categoryReachable(category: Category): Boolean =
        category.id != BuiltInCategory.SHELVES || shelfCards.isNotEmpty()

    val enterOpensAppDrawer: Boolean
        get() = search == null &&
            !hasBlockingOverlay &&
            !isInSubItem &&
            !onLastPlayedHome &&
            activePillIndex() == null

    val hasBlockingOverlay: Boolean
        get() = otherBlockingOverlay || activeContextMenu != null || notificationsOpen

    private val otherBlockingOverlay: Boolean
        get() = chromeOverlay || fullscreenOverlay

    private val chromeOverlay: Boolean
        get() = activeSettingsScreen != null ||
            appPicker != null ||
            gamePickerCategoryId != null ||
            activeAppDrawerFilter != null ||
            activeGameId != null ||
            activeAppId != null ||
            search != null

    private val fullscreenOverlay: Boolean
        get() = showBootSequence ||
            artworkStudioGameId != null ||
            manualViewer != null ||
            metadataPreview != null ||
            activeGameBoot != null ||
            discCeremony != null ||
            activeVideoId != null ||
            activePhotoViewer != null ||
            colorSchemePicker != null ||
            customColorPicker != null ||
            xmbLayoutAdjust != null ||
            customIconSession != null ||
            saveThemeNameDialog != null ||
            renameAppTarget != null ||
            collectionNameDialog != null ||
            playlistNameDialog != null ||
            musicTrackPicker != null ||
            musicBrowser != null ||
            musicPlayerVisible ||
            infoDialog != null ||
            launchRecovery != null ||
            showWindowsSetupPrompt

    val stripShowsXmbContext: Boolean
        get() = !hasBlockingOverlay || overlayKeepsChrome

    val statusStripVisible: Boolean
        get() = !fullscreenOverlay
}

data class DiscCeremonyState(val art: Any?)

const val SEARCH_GRID_COLUMNS = 7

const val SEARCH_GRID_MIN_COLUMNS = 3
const val SEARCH_GRID_MAX_COLUMNS = 12

enum class XMBItemType {
    STANDARD,
    ALL_GAMES,
    FAVORITES,

    SHELF,

    MISSING,
    MEMORY_CARD,
    COLLECTION,

    MUSIC_GROUP,

    MUSIC_ARTISTS,
    MUSIC_ALBUMS,
    MUSIC_TRACK,
    PLAYLIST,
    VIDEO_LIBRARY,
    VIDEO_FOLDER,
    VIDEO_FILE,
    VIDEO_RECENT,
    VIDEO_FAVORITES,
    VIDEO_COLLECTIONS,
    PHOTO_ALBUMS,
    PHOTO_FOLDER,

    LIBRARY_SHELVES,
    LIBRARY_READER,
    LIBRARY_FOLDER,
    LIBRARY_BOOK,

    LIBRARY_SERIES,
    PHOTO_FILE,
    CAMERA,

    SEARCH,

    ADD_ACTION,
    EMPTY,
}

enum class XmbSortMode(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    RECENT_PLAYED("Recently Played"),
    DATE_ADDED("Date Added"),
    SERIES("Series"),
}

private val MUSIC_SORTS = listOf(XmbSortMode.TITLE, XmbSortMode.ARTIST, XmbSortMode.ALBUM, XmbSortMode.DATE_ADDED)
private val GAME_SORTS  = listOf(XmbSortMode.TITLE, XmbSortMode.RECENT_PLAYED, XmbSortMode.DATE_ADDED)
private val VIDEO_SORTS = listOf(XmbSortMode.TITLE, XmbSortMode.DATE_ADDED, XmbSortMode.RECENT_PLAYED)
private val BOOK_SORTS  = listOf(XmbSortMode.TITLE, XmbSortMode.SERIES, XmbSortMode.DATE_ADDED)

private val BY_SERIES_POSITION = compareBy<com.psplauncher.core.domain.model.Book>(
    { it.seriesIndex ?: Double.MAX_VALUE },
    { it.displayTitle.lowercase() },
)

internal fun List<com.psplauncher.core.domain.model.Book>.inSeriesOrder(): List<com.psplauncher.core.domain.model.Book> =
    sortedWith(BY_SERIES_POSITION)

internal fun List<com.psplauncher.core.domain.model.Book>.seriesGroups(): List<BookSeries> =
    filter { it.seriesName != null }
        .groupBy { it.seriesName!! }
        .map { (name, books) ->
            BookSeries(
                name = name,
                bookCount = books.size,
                coverUri = books.inSeriesOrder().firstNotNullOfOrNull { it.coverUri },
            )
        }
        .sortedBy { it.name.lowercase() }

internal fun List<com.psplauncher.core.domain.model.Book>.bookSorted(mode: XmbSortMode): List<com.psplauncher.core.domain.model.Book> = when (mode) {
    XmbSortMode.SERIES -> sortedWith(
        compareBy<com.psplauncher.core.domain.model.Book> { it.seriesName == null }
            .thenBy { it.seriesName?.lowercase() ?: "" }
            .then(BY_SERIES_POSITION)
    )
    XmbSortMode.DATE_ADDED -> sortedByDescending { it.dateAdded ?: 0L }
    else -> sortedBy { it.displayTitle.lowercase() }
}

internal fun List<com.psplauncher.core.domain.model.Video>.videoSorted(mode: XmbSortMode): List<com.psplauncher.core.domain.model.Video> = when (mode) {
    XmbSortMode.RECENT_PLAYED -> sortedByDescending { it.lastWatchedAt ?: 0L }
    XmbSortMode.DATE_ADDED    -> sortedByDescending { it.dateAdded ?: 0L }
    else                      -> sortedBy { it.displayTitle.lowercase() }
}

internal fun List<Game>.gameSorted(mode: XmbSortMode): List<Game> = when (mode) {
    XmbSortMode.RECENT_PLAYED -> sortedByDescending { it.lastPlayedAt ?: 0L }
    XmbSortMode.DATE_ADDED    -> sortedByDescending { it.id }
    else                      -> sortedBy { it.displayTitle.lowercase() }
}

internal fun cursorAfterRefresh(previous: List<XMBItem>, previousIndex: Int, next: List<XMBItem>): Int {
    val selectedId = previous.getOrNull(previousIndex)?.id
    val kept = selectedId?.let { id -> next.indexOfFirst { it.id == id } } ?: -1
    return if (kept >= 0) kept else previousIndex.coerceIn(0, (next.size - 1).coerceAtLeast(0))
}

internal fun List<Game>.projectGamesForDisplay(): List<Game> {
    val singles = filter { it.discSetKey == null && !it.isMissing }
    val sets = groupBy { it.discSetKey }
        .filterKeys { it != null }
        .values
        .mapNotNull { members ->
            val present = members.filterNot { it.isMissing }
            if (present.isEmpty()) return@mapNotNull null
            val display = members.firstOrNull { it.isDiscPrimary } ?: present.first()

            display.copy(isFavorite = members.any { it.isFavorite })
        }
    return singles + sets
}

internal fun List<MusicTrack>.trackSorted(mode: XmbSortMode): List<MusicTrack> = when (mode) {
    XmbSortMode.ARTIST     -> sortedWith(
        compareBy(nullsLast<String>()) { t: MusicTrack -> t.artist?.lowercase() }
            .thenBy(nullsLast<String>()) { t -> t.album?.lowercase() }
            .thenBy { t -> t.displayTitle.lowercase() }
    )
    XmbSortMode.ALBUM      -> sortedWith(
        compareBy(nullsLast<String>()) { t: MusicTrack -> t.album?.lowercase() }
            .thenBy { t -> t.trackNumber ?: Int.MAX_VALUE }
            .thenBy { t -> t.displayTitle.lowercase() }
    )
    XmbSortMode.DATE_ADDED -> sortedByDescending { it.lastModified ?: 0L }
    else                   -> sortedBy { it.displayTitle.lowercase() }
}

internal fun XMBItem.owningCategory(): String? = when (type) {
    XMBItemType.VIDEO_FILE -> BuiltInCategory.VIDEO
    XMBItemType.PHOTO_FILE -> BuiltInCategory.PHOTO
    XMBItemType.LIBRARY_BOOK -> BuiltInCategory.LIBRARY
    XMBItemType.MUSIC_TRACK -> BuiltInCategory.MUSIC
    else -> if (gameId != null) BuiltInCategory.GAMES else null
}

internal fun XMBItem.menuHostCategory(currentCategoryId: String?): String? =
    owningCategory() ?: currentCategoryId

fun XMBItem.hasContextMenu(state: XMBUiState): Boolean {
    val categoryId = menuHostCategory(state.categories.getOrNull(state.selectedCategoryIndex)?.id)
    return when {
        categoryId == BuiltInCategory.MUSIC && (
            id == XMBViewModel.NOW_PLAYING_ITEM_ID ||
                type == XMBItemType.MUSIC_TRACK ||
                (type == XMBItemType.PLAYLIST && playlistId != null)
        ) -> true

        categoryId == BuiltInCategory.VIDEO && (
            (type == XMBItemType.VIDEO_FILE && id.startsWith("vid_")) ||
                (type == XMBItemType.VIDEO_FOLDER && id.startsWith("vlib_")) ||
                (type == XMBItemType.PLAYLIST && playlistId != null)
        ) -> true

        categoryId == BuiltInCategory.LIBRARY &&
            type == XMBItemType.LIBRARY_BOOK && id.startsWith("book_") -> true

        categoryId == BuiltInCategory.PHOTO && (
            (type == XMBItemType.PHOTO_FILE && id.startsWith("pho_")) ||
                (type == XMBItemType.PHOTO_FOLDER && id.startsWith("plib_"))
        ) -> true
        gameId != null -> true
        collectionId != null && type == XMBItemType.COLLECTION -> true
        type == XMBItemType.ALL_GAMES -> true
        platformId != null -> true
        packageName != null -> true
        else -> false
    }
}

fun XMBUiState.activeSortModes(): List<XmbSortMode>? {
    val cat = categories.getOrNull(selectedCategoryIndex) ?: return null
    return when {
        cat.id == BuiltInCategory.MUSIC &&
            (musicNav == MusicNav.AllMusic || musicNav is MusicNav.Playlist) -> MUSIC_SORTS

        cat.id == BuiltInCategory.VIDEO &&
            (videoNav == VideoNav.AllVideos || videoNav == VideoNav.Favorites ||
                videoNav is VideoNav.Library) -> VIDEO_SORTS

        cat.id == BuiltInCategory.LIBRARY &&
            (booksNav == BooksNav.AllBooks || booksNav is BooksNav.Shelf) -> BOOK_SORTS
        cat.id == BuiltInCategory.GAMES &&
            (selectedPlatformId != null || selectedCollectionId != null) -> GAME_SORTS
        cat.isGamingCategory -> GAME_SORTS
        else -> null
    }
}

internal fun canonicalXmbCategories(
    categories: List<Category>,
    fallbacks: List<Category>,
): List<Category> {
    val byId = categories.associateBy { it.id }
    val builtInIds = fallbacks.map { it.id }.toSet()

    val builtIns = fallbacks.mapNotNull { fallback ->
        val stored = byId[fallback.id]

        if (stored == null && fallback.id != BuiltInCategory.SETTINGS) return@mapNotNull null
        fallback.copy(
            name             = stored?.name?.takeIf { it.isNotBlank() } ?: fallback.name,
            position         = stored?.position ?: fallback.position,
            accentColor      = stored?.accentColor,
            customIconUri    = stored?.customIconUri,
            filterRules      = stored?.filterRules,

            isGamingCategory = stored?.isGamingCategory ?: fallback.isGamingCategory,
        )
    }

    val customCategories = categories.filter { it.id !in builtInIds }

    return (builtIns + customCategories).sortedBy { it.position }
}

internal fun XMBUiState.sortModeFor(cycle: List<XmbSortMode>): XmbSortMode = when {
    cycle === MUSIC_SORTS -> musicSortMode
    cycle === VIDEO_SORTS -> videoSortMode
    cycle === BOOK_SORTS  -> bookSortMode
    else                  -> gameSortMode
}

internal fun XMBUiState.withSortMode(cycle: List<XmbSortMode>, mode: XmbSortMode): XMBUiState = when {
    cycle === MUSIC_SORTS -> copy(musicSortMode = mode)
    cycle === VIDEO_SORTS -> copy(videoSortMode = mode)
    cycle === BOOK_SORTS  -> copy(bookSortMode = mode)
    else                  -> copy(gameSortMode = mode)
}

val XMBUiState.hintsAutoHide: Boolean
    get() = contextMenuHintDelaySeconds > 0f

internal fun XMBUiState.withHintsShownNow(): XMBUiState = copy(
    showContextMenuHint = shouldShowContextMenuHint(this, 0L),
    showAppDrawerHint = shouldShowAppDrawerHint(this, 0L),
    showSettingsHint = shouldShowSettingsHint(this, 0L),
)

fun shouldShowContextMenuHint(state: XMBUiState, idleMs: Long): Boolean =
    state.contextMenuHintEnabled &&

        state.stripShowsXmbContext &&

        (state.focusedItemHasContextMenu || state.canSortCurrentList || state.canFilterRecents) &&
        idleMs >= (state.contextMenuHintDelaySeconds * 1_000f).toLong()

fun shouldShowAppDrawerHint(state: XMBUiState, idleMs: Long): Boolean =
    state.contextMenuHintEnabled &&
        state.activeAppDrawerFilter != null &&
        state.activeContextMenu == null &&

        !state.notificationsOpen &&
        idleMs >= (state.contextMenuHintDelaySeconds * 1_000f).toLong()

fun shouldShowSettingsHint(state: XMBUiState, idleMs: Long): Boolean =
    state.contextMenuHintEnabled &&
        state.activeSettingsScreen != null &&
        idleMs >= (state.contextMenuHintDelaySeconds * 1_000f).toLong()

internal fun gameMetadataLine(
    releaseYear: Int?,
    genre: String?,
    developer: String?,
    players: String?,
): String? {
    fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    val parts = listOfNotNull(

        releaseYear?.takeIf { it > 0 }?.toString(),
        genre.clean(),
        developer.clean(),

        players.clean()?.let { if (it == "1") "1 player" else "$it players" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString("   ·   ")
}

data class XMBItem(
    val id: String,
    val title: String,
    val artworkUri: String? = null,
    val heroUri: String? = null,
    val iconUri: String? = null,
    val logoUri: String? = null,

    val boxArtUri: String? = null,
    val physicalMediaUri: String? = null,
    val box3dUri: String? = null,
    val iconDisplayModeOverride: String? = null,
    val subtitle: String? = null,

    val metadataLine: String? = null,

    val description: String? = null,
    val romPath: String? = null,

    val totalPlayTimeMillis: Long = 0L,

    val insideCovers: List<String> = emptyList(),
    val gameId: Long? = null,
    val platformId: String? = null,
    val collectionId: Long? = null,
    val iconKey: String? = null,
    val accentColor: Long? = null,
    val isFavorite: Boolean = false,

    val playState: String? = null,
    val isAndroidApp: Boolean = false,

    val isRealGame: Boolean = false,
    val packageName: String? = null,

    val shortcutId: String? = null,

    val launchIntentUri: String? = null,

    val musicFolderId: String? = null,

    val musicGroupKey: String? = null,
    val mediaUri: String? = null,
    val mimeType: String? = null,

    val coverUri: String? = null,

    val progressFraction: Float? = null,

    val progressLabel: String? = null,

    val playlistId: Long? = null,

    val textOnly: Boolean = false,

    val type: XMBItemType = XMBItemType.STANDARD,
) {
    val backdropArt: List<String>
        get() = listOfNotNull(artworkUri, heroUri, coverUri, boxArtUri, iconUri)
            .filter { it.isNotBlank() && it != XMBViewModel.MEMORY_CARD_ASSET_URI }

    val shelfCoverArt: String?
        get() = listOfNotNull(boxArtUri, coverUri, artworkUri, heroUri, iconUri)
            .firstOrNull { it.isNotBlank() && it != XMBViewModel.MEMORY_CARD_ASSET_URI }

    val hasVisibleLogo: Boolean
        get() = !logoUri.isNullOrBlank() && backdropArt.isNotEmpty()
}

data class ResolvedIcon(val uri: String?, val naturalAspect: Boolean, val mode: IconDisplayMode)

fun resolveIconDisplay(
    item: XMBItem,
    globalMode: IconDisplayMode,
    platformModes: Map<String, IconDisplayMode> = emptyMap(),
): ResolvedIcon {
    val mode = IconDisplayMode.fromName(item.iconDisplayModeOverride)
        ?: item.platformId?.let { platformModes[it] }
        ?: globalMode
    return when (mode) {
        IconDisplayMode.ICON0 ->
            ResolvedIcon(item.iconUri, naturalAspect = false, mode = mode)
        IconDisplayMode.BOX_ART ->
            ResolvedIcon(item.boxArtUri, naturalAspect = true, mode = mode)
        IconDisplayMode.PHYSICAL_MEDIA ->
            ResolvedIcon(item.physicalMediaUri, naturalAspect = item.physicalMediaUri != null, mode = mode)
        IconDisplayMode.BOX_3D ->
            ResolvedIcon(item.box3dUri, naturalAspect = true, mode = mode)
    }
}

data class BackgroundTaskInfo(
    val id: String,
    val label: String,
    val progress: Float?,
)

fun XMBUiState.withNamePromptText(text: String): XMBUiState = when {
    renameAppTarget != null      -> copy(renameAppText = text)
    collectionNameDialog != null -> copy(collectionNameDialog = collectionNameDialog.copy(text = text))
    playlistNameDialog != null   -> copy(playlistNameDialog = playlistNameDialog.copy(text = text))
    saveThemeNameDialog != null  -> copy(saveThemeNameDialog = saveThemeNameDialog.copy(text = text))
    else -> this
}

@HiltViewModel
class XMBViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val platformDao: PlatformDao,
    private val memoryCardRepository: MemoryCardRepository,
    private val collectionRepository: CollectionRepository,
    private val categoryRepository: CategoryRepositoryImpl,
    private val appCategoryRepository: AppCategoryRepository,
    private val gameCategoryRepository: com.psplauncher.core.data.repository.GameCategoryRepository,
    private val launcherShortcutRepository: LauncherShortcutRepository,
    private val libraryScanner: LibraryScanner,
    private val artworkRepository: ArtworkRepository,
    @ApplicationContext private val context: Context,
    private val gamepadInputHandler: GamepadInputHandler,
    private val remapCoordinator: com.psplauncher.core.data.repository.RemapCoordinator,
    private val mappingRepository: ControllerMappingRepository,
    private val controllerLayoutRepository: com.psplauncher.core.data.repository.ControllerLayoutRepository,
    private val menuSound: com.psplauncher.core.ui.sound.MenuSoundPlayer,
    private val musicRepository: com.psplauncher.core.domain.repository.MusicRepository,
    private val musicScanner: com.psplauncher.feature.library.scanner.MusicScanner,
    private val musicPlayer: com.psplauncher.feature.xmb.music.MusicPlayerController,
    private val emulatorProfileRepository: com.psplauncher.feature.launcher.EmulatorProfileRepository,
    private val intentResolver: com.psplauncher.feature.launcher.EmulatorIntentResolver,
    private val videoRepository: com.psplauncher.core.domain.repository.VideoRepository,
    private val photoRepository: com.psplauncher.core.domain.repository.PhotoRepository,
    private val photoScanner: com.psplauncher.feature.library.scanner.PhotoScanner,
    private val bookRepository: com.psplauncher.core.domain.repository.BookRepository,
    private val bookIntentResolver: com.psplauncher.core.data.book.BookIntentResolver,
    private val hiddenPlacementDao: com.psplauncher.core.data.database.dao.HiddenPlacementDao,
    private val iconDisplayPreferences: com.psplauncher.core.data.repository.IconDisplayPreferences,
    private val artworkStore: com.psplauncher.feature.artwork.store.ArtworkStore,
    private val artworkAccent: com.psplauncher.core.data.repository.ArtworkAccent,
    private val windowsLibrarySetup: com.psplauncher.core.data.repository.WindowsLibrarySetup,
    private val pcShortcutImporter: com.psplauncher.feature.launcher.PcShortcutImporter,
    private val pcGameScanner: com.psplauncher.feature.settings.pc.PcGameScanner,
    private val pcGameExporter: com.psplauncher.feature.settings.pc.PcGameExporter,
    private val launchDispatcher: com.psplauncher.feature.launcher.LaunchDispatcher,
    private val launchResolver: com.psplauncher.feature.launcher.GameLaunchResolver,
    private val setupStateProvider: com.psplauncher.feature.launcher.SetupStateProvider,
    private val customIconStore: CustomIconStore,
    private val pfpThemeStore: PfpThemeStore,
    private val uiMediaStore: com.psplauncher.core.data.repository.UiMediaStore,
    private val gameBootGate: com.psplauncher.feature.launcher.GameBootGate,
    private val mediaLaunchGate: com.psplauncher.core.data.launch.MediaLaunchGate,

    private val uiMediaAudioPlayer: com.psplauncher.core.ui.media.UiMediaAudioPlayer,
) : ViewModel() {
    private var currentMusicTracks: List<MusicTrack> = emptyList()
    private var currentMusicTracksRaw: List<MusicTrack> = emptyList()

    private var lastHadPlayingTrack = false

    @Volatile
    private var defaultMusicPlayer: String? = null

    @Volatile
    private var lastInteractionMs: Long = 0L

    private val _uiState = MutableStateFlow(XMBUiState())
    val uiState: StateFlow<XMBUiState> = _uiState.asStateFlow()

    private var currentItemsJob: Job? = null

    private var musicBrowserJob: Job? = null
    private var browserRawTracks: List<MusicTrack> = emptyList()
    private var browserRawPlaylists: List<com.psplauncher.core.domain.model.Playlist> = emptyList()
    private var platformCache: Map<String, PlatformEntity> = emptyMap()
    private var enabledCards: List<MemoryCard> = emptyList()
    private var baseThemeColors: PFPColors = DefaultPFPColors

    private val taskNotifier = BackgroundTaskNotifier(context)

    init {
        gamepadInputHandler.scope = viewModelScope

        musicPlayer.onTrackStarted = { track ->
            viewModelScope.launch {
                runCatching { musicRepository.markTrackPlayed(track.id, System.currentTimeMillis()) }
                    .onFailure { Timber.w(it, "Could not stamp ${track.displayTitle} as played") }
            }
        }
        observeContextMenuHintIdle()
        observeIconDisplayMode()
        observeFocusedGameVideo()
        observeFocusedItemAccent()
        observeBackgroundSettings()
        observeTouchNavButtonMode()
        observeWallpaper()
        observeLibrarySetupState()
        checkInitialSetup()
        logStartupSequence()
        observeColorScheme()
        observeCategoryBar()
        observeCategories()
        observeMissingGames()
        observeAppChanges()
        observeGamepadMappings()
        observeBootPreferences()
        observeGameBoot()
        observeMediaLaunch()
        observeMusic()
        observeVideo()
        observePhoto()
        observeBooks()
        observeMediaCovers()
        observeContinueBook()
        observeHiddenPlacements()
        observeAndroidNotices()
        observeResumeGame()
        observeShelfCounts()
        collectGamepadActions()
        consumeWindowsSetupPrompt()
        observeLaunchRecoveryRequests()
        observeSetupState()

        pcShortcutImporter.watchPinChanges(viewModelScope)
    }

    private fun observeLaunchRecoveryRequests() {
        viewModelScope.launch {
            launchDispatcher.recoveryRequests.collect { request ->
                _uiState.update { it.copy(launchRecovery = request) }
            }
        }
    }

    fun onLaunchRecoveryAction(action: LaunchRecoveryAction) {
        when (action) {
            LaunchRecoveryAction.DISMISS -> launchDispatcher.dismissRecovery()
            LaunchRecoveryAction.RETRY   -> {
                val request = _uiState.value.launchRecovery ?: return
                launchDispatcher.dismissRecovery()
                launchGameDirectly(request.gameId)
            }
            LaunchRecoveryAction.CHANGE_EMULATOR -> {
                val gameId = _uiState.value.launchRecovery?.gameId ?: return
                launchDispatcher.dismissRecovery()
                openEmulatorPickerMenu(gameId)
            }
            LaunchRecoveryAction.PER_SYSTEM_DEFAULTS -> {
                launchDispatcher.dismissRecovery()
                _uiState.update { it.copy(activeSettingsScreen = "settings_emulators_assign") }
            }

            LaunchRecoveryAction.OPEN_LIBRARY -> {
                launchDispatcher.dismissRecovery()
                _uiState.update { it.copy(activeSettingsScreen = "settings_library") }
            }
            LaunchRecoveryAction.COPY_DIAGNOSTIC -> {
                val request = _uiState.value.launchRecovery ?: return
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText(
                    "PSP launch diagnostic", request.diagnostic,
                ))
                taskNotifier.complete(
                    "launch_diag_${request.gameId}", request.gameTitle, "Diagnostic copied to clipboard",
                )
            }
        }
    }

    private fun consumeWindowsSetupPrompt() {
        viewModelScope.launch {
            if (runCatching { windowsLibrarySetup.consumeSetupPrompt() }.getOrDefault(false)) {
                _uiState.update { it.copy(showWindowsSetupPrompt = true) }
            }
        }
    }

    fun confirmWindowsSetupPrompt() = _uiState.update {
        it.copy(showWindowsSetupPrompt = false, activeSettingsScreen = "settings_library")
    }

    fun dismissWindowsSetupPrompt() = _uiState.update { it.copy(showWindowsSetupPrompt = false) }

    private fun gameMetaLabel(g: Game): String = gameMetaLine(
        platform = platformCache[g.platformId]?.name ?: g.platformId,
        lastPlayedAt = g.lastPlayedAt,
        publisher = g.publisher,
    )

    private fun observeMediaCovers() {
        viewModelScope.launch {
            combine(
                musicRepository.observeNewestArtUris(MEDIA_COVER_POOL),
                videoRepository.observeNewestArtUris(MEDIA_COVER_POOL),
                photoRepository.observeNewestArtUris(MEDIA_COVER_POOL),
                bookRepository.observeNewestArtUris(MEDIA_COVER_POOL),
            ) { music, video, photo, books -> MediaCovers(music, video, photo, books) }
                .collect { covers ->
                    if (_uiState.value.mediaCovers == covers) return@collect
                    _uiState.update { it.copy(mediaCovers = covers) }
                    val id = currentCategory()?.id
                    if (id == BuiltInCategory.MUSIC || id == BuiltInCategory.VIDEO ||
                        id == BuiltInCategory.PHOTO || id == BuiltInCategory.LIBRARY
                    ) {
                        loadItemsForCategory(currentCategory(), keepCursorOnRow = true)
                    }
                }
        }
    }

    private fun observeMusic() {
        viewModelScope.launch {
            musicRepository.observeFolders().collect { folders ->
                _uiState.update { it.copy(musicFolders = folders) }
                if (currentCategory()?.id == BuiltInCategory.MUSIC &&
                    _uiState.value.musicNav == MusicNav.Root
                ) {
                    loadItemsForCategory(currentCategory())
                }
            }
        }
        viewModelScope.launch {
            musicRepository.observeDefaultPlayerPackage().collect { defaultMusicPlayer = it }
        }
        viewModelScope.launch {
            musicPlayer.state.collect { playback ->
                _uiState.update { it.copy(musicPlayback = playback) }

                val hasTrack = playback.track != null
                if (hasTrack != lastHadPlayingTrack) {
                    lastHadPlayingTrack = hasTrack
                    if (currentCategory()?.id == BuiltInCategory.MUSIC &&
                        _uiState.value.musicNav == MusicNav.Root
                    ) {
                        refreshMusicRootPreservingCursor()
                    }
                }
            }
        }
    }

    private data class SchemePrefs(
        val schemeName: String,
        val accentOverride: Long?,
        val iconColor: Long?,
        val iconsStamp: Long?,
        val layoutJson: String?,

        val xmbScale: Float?,
        val barTopOverride: Float?,

        val layoutAdjustJson: String?,

        val customIconsStamp: Long?,

        val textColor: Long?,
    )

    private fun observeColorScheme() {
        viewModelScope.launch {
            context.pfpDataStore.data
                .map { prefs ->
                    SchemePrefs(
                        schemeName = prefs[KEY_COLOR_SCHEME] ?: XmbColorScheme.CLASSIC_BLUE.name,
                        accentOverride = prefs[KEY_ACCENT_OVERRIDE],
                        iconColor = prefs[KEY_ICON_COLOR],
                        iconsStamp = prefs[com.psplauncher.core.data.repository.PfpThemeStore.KEY_THEME_ICONS_STAMP],
                        layoutJson = prefs[com.psplauncher.core.data.repository.PfpThemeStore.KEY_THEME_LAYOUT],
                        xmbScale = prefs[KEY_XMB_SCALE],
                        barTopOverride = prefs[KEY_BAR_TOP_FRACTION],
                        layoutAdjustJson = prefs[KEY_XMB_LAYOUT_ADJUST],
                        customIconsStamp = prefs[CustomIconStore.KEY_CUSTOM_ICONS_STAMP],
                        textColor = prefs[KEY_TEXT_COLOR],
                    )
                }
                .distinctUntilChanged()
                .collect { (name, accentOverride, iconColorArgb, iconsStamp, layoutJson, xmbScale, barTopOverride, layoutAdjustJson, customIconsStamp, textColorArgb) ->
                    val base = if (accentOverride != null) {
                        DefaultPFPColors.withWaveTint(
                            androidx.compose.ui.graphics.Color(accentOverride and 0xFFFFFFFFL),
                        )
                    } else {
                        val scheme = runCatching { XmbColorScheme.valueOf(name) }
                            .getOrDefault(XmbColorScheme.CLASSIC_BLUE)
                        val month = java.time.LocalDate.now().monthValue
                        scheme.resolve(month).toPFPColors()
                    }

                    val textColor = textColorArgb
                        ?.let { androidx.compose.ui.graphics.Color(it and 0xFFFFFFFFL) }
                        ?: base.textPrimary
                    baseThemeColors = base.copy(
                        iconColor = iconColorArgb
                            ?.let { androidx.compose.ui.graphics.Color(it and 0xFFFFFFFFL) }
                            ?: androidx.compose.ui.graphics.Color.White,
                        textPrimary = textColor,
                        textSecondary = textColor.copy(alpha = 0.7f),
                    )

                    val iconOverrides = if (iconsStamp != null) loadThemeIconOverrides() else emptyMap()
                    val customIcons =
                        if (customIconsStamp != null) customIconStore.load() else emptyMap()

                    val themeSpec = com.psplauncher.themekit.XmbLayoutSpecCodec.decode(layoutJson)
                        ?: com.psplauncher.themekit.XmbLayoutSpec.DEFAULT
                    val layoutSpec = if (barTopOverride != null) {
                        com.psplauncher.themekit.XmbLayoutSpecCodec.sanitize(
                            themeSpec.copy(barTopFraction = barTopOverride)
                        )
                    } else themeSpec

                    val adjustMap = com.psplauncher.themekit.XmbLayoutAdjustCodec.decode(layoutAdjustJson)
                    _uiState.update {
                        it.copy(
                            themeColors = baseThemeColors,
                            iconOverrides = iconOverrides,
                            customIcons = customIcons,
                            layoutSpec = layoutSpec,
                            xmbScale = (xmbScale ?: 1f).coerceIn(0.75f, 1.3f),
                            xmbLayoutAdjustMap = adjustMap,
                        )
                    }
                }
        }
    }

    private suspend fun loadThemeIconOverrides(): Map<String, CustomIcon> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val iconsDir = java.io.File(context.filesDir, PfpThemeStore.THEME_ICONS_DIR)
            iconsDir.listFiles { f -> f.isFile }.orEmpty().mapNotNull { file ->
                val key = file.nameWithoutExtension
                if (!CustomizableIcons.isValidKey(key)) return@mapNotNull null
                val ext = file.extension.lowercase()

                val bitmap = com.psplauncher.core.data.repository.SafeMedia
                    .decodeFileCapped(file.absolutePath, maxDimension = 2048, targetDimension = 2048)
                    ?: return@mapNotNull null
                val firstFrame = bitmap.asImageBitmap()
                if (ext == "gif") {
                    if (GifFrameProbe.countFrames(file) > 1) {
                        key to CustomIcon.Animated(path = file.absolutePath, firstFrame = firstFrame)
                    } else {
                        key to CustomIcon.Still(firstFrame)
                    }
                } else {
                    key to CustomIcon.Still(firstFrame)
                }
            }.toMap()
        }

    private fun observeCategoryBar() {
        viewModelScope.launch {
            categoryRepository.observeVisible().collect { categories ->
                val allCategories = canonicalXmbCategories(categories.ifEmpty { FALLBACK_CATEGORIES })
                val prevId   = _uiState.value.categories.getOrNull(_uiState.value.selectedCategoryIndex)?.id
                val isInitialSelection = _uiState.value.categories.isEmpty()

                val newIndex = if (isInitialSelection) {
                    defaultXmbCategoryIndex(allCategories)
                } else {
                    allCategories.indexOfFirst { it.id == prevId }
                        .takeIf { it >= 0 }
                        ?: defaultXmbCategoryIndex(allCategories)
                }
                val newId    = allCategories.getOrNull(newIndex)?.id

                _uiState.update { it.copy(categories = allCategories, selectedCategoryIndex = newIndex) }

                if (newId != prevId || _uiState.value.currentItems.isEmpty()) {
                    tintWaveForCategory(allCategories.getOrNull(newIndex))
                    loadItemsForCategory(allCategories.getOrNull(newIndex))
                }
            }
        }
    }

    private fun observeMissingGames() {
        viewModelScope.launch {
            gameRepository.observeMissing().collect { missing ->
                val was = _uiState.value.missingCount
                _uiState.update { it.copy(missingCount = missing.size) }

                if ((was == 0) != (missing.size == 0) &&
                    currentCategory()?.id == BuiltInCategory.GAMES
                ) {
                    loadItemsForCategory(currentCategory())
                }
            }
        }
    }

    private fun observeCategories() {
        viewModelScope.launch {
            combine(
                memoryCardRepository.observeEnabled(),
                gameRepository.observeAll(),
                platformDao.observeAll(),
                collectionRepository.observeCollections(),
                gameRepository.observeFavorites(),
            ) { cards, games, platforms, collections, favorites ->
                CardsGamesPlatformsCollections(cards, games, platforms, collections, favorites)
            }
                .collect { (cards, games, platforms, collections, favorites) ->
                    platformCache = platforms.associateBy { it.id }
                    enabledCards  = cards

                    val displayGames = games.projectGamesForDisplay()
                    val counts = displayGames.filter { it.contentType == GameContentType.GAME }
                        .groupBy { it.platformId }.mapValues { it.value.size }
                    val gamesOnlyTotal = displayGames.count { it.contentType == GameContentType.GAME }

                    val favoritesTotal = favorites.size

                    fun fanOf(games: List<Game>): List<String> = fanCoversOf(games)
                    val realGames = displayGames.filter { it.contentType == GameContentType.GAME }
                    val fanCovers = buildMap<String, List<String>> {
                        put(ALL_GAMES_ITEM_ID, fanOf(realGames))

                        realGames.groupBy { it.platformId }
                            .forEach { (pid, list) -> put(cardItemId(pid), fanOf(list)) }
                    }

                    val validPlatformId = _uiState.value.selectedPlatformId
                        ?.takeIf { id ->
                            id == ALL_GAMES_PLATFORM_ID ||
                                id == FAVORITES_PLATFORM_ID ||
                                id == MISSING_PLATFORM_ID ||
                                cards.any { c -> c.platformId == id }
                        }

                    val validCollectionId = _uiState.value.selectedCollectionId
                        ?.takeIf { id -> collections.any { c -> c.id == id } }

                    _uiState.update { it.copy(
                        platformGameCounts = counts,
                        allGamesCount = gamesOnlyTotal,
                        cardFanCovers = fanCovers,
                        favoritesCount = favoritesTotal,
                        selectedPlatformId = validPlatformId,
                        selectedCollectionId = validCollectionId,
                        collections = collections,
                    )}

                    if (categoryShowsCollections(currentCategory())) {
                        loadItemsForCategory(currentCategory(), keepCursorOnRow = true)
                    }
                }
        }
    }

    private data class CardsGamesPlatformsCollections(
        val cards: List<MemoryCard>,
        val games: List<Game>,
        val platforms: List<PlatformEntity>,
        val collections: List<GameCollection>,
        val favorites: List<Game>,
    )

    private fun observeAppChanges() {
        viewModelScope.launch {
            appCategoryRepository.changes().collect {
                val category = currentCategory() ?: return@collect
                if (isAppCategory(category.id)) loadItemsForCategory(category)
            }
        }
    }

    private fun currentCategory(): Category? = _uiState.value.currentCategoryOrNull()

    private fun isAppCategory(categoryId: String): Boolean =
        categoryId != BuiltInCategory.SETTINGS && categoryId != BuiltInCategory.GAMES

    private val nonCollectionCategoryIds = setOf(
        BuiltInCategory.FAVORITES, BuiltInCategory.RECENTLY_PLAYED, BuiltInCategory.MUSIC,
        BuiltInCategory.VIDEO, BuiltInCategory.PHOTO, BuiltInCategory.ANDROID,
        BuiltInCategory.APP_DRAWER, BuiltInCategory.SETTINGS,
    )

    private fun categoryShowsCollections(category: Category?): Boolean {
        if (category == null) return false
        return category.isGamingCategory || category.id !in nonCollectionCategoryIds
    }

    private fun collectionHomeCategoryId(): String {
        val cat = currentCategory() ?: return BuiltInCategory.GAMES
        return if (categoryShowsCollections(cat)) cat.id else BuiltInCategory.GAMES
    }

    private fun loadItemsForCategory(category: Category?, keepCursorOnRow: Boolean = false) {
        currentItemsJob?.cancel()
        if (category == null) { _uiState.update { it.copy(currentItems = emptyList(), sortLabel = null, drillTitle = null, drillSiblings = emptyList(), drillSiblingIndex = 0) }; return }
        val drill = computeDrillTitle()
        val (sibs, sibIdx) = if (drill != null) computeDrillSiblings(category) else (emptyList<XMBItem>() to 0)
        _uiState.update { it.copy(sortLabel = currentSortLabel(), drillTitle = drill, drillSiblings = sibs, drillSiblingIndex = sibIdx) }

        currentItemsJob = viewModelScope.launch {
            when (category.id) {
                BuiltInCategory.FAVORITES -> {
                    var keepCursor = keepCursorOnRow
                    gameRepository.observeFavorites().collect { games ->
                        publishGameItems(games.notHiddenAt(HideLocationType.FAVORITES).gameSorted(_uiState.value.gameSortMode).toXmbItems(), keepCursor)
                        keepCursor = true
                    }
                }
                BuiltInCategory.SHELVES -> {
                    var keepCursor = keepCursorOnRow
                    when (val shelf = shelfCardFor(_uiState.value.selectedPlatformId)) {
                        null -> _uiState.update { s ->
                            val items = s.shelfCards.map { card ->
                                XMBItem(
                                    id       = card.cardId,
                                    title    = card.title,
                                    subtitle = countLabel(card.count, "game", "games"),
                                    insideCovers = s.shelfFanCovers[card.cardId].orEmpty(),
                                    type     = XMBItemType.SHELF,
                                )
                            }
                            s.copy(
                                currentItems = items,
                                selectedItemIndex = s.selectedItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                            )
                        }

                        is ShelfCard.Favorites -> gameRepository.observeFavorites().collect { games ->
                            publishGameItems(
                                games.notHiddenAt(HideLocationType.FAVORITES)
                                    .gameSorted(_uiState.value.gameSortMode).toXmbItems(),
                                keepCursor,
                            )
                            keepCursor = true
                        }
                        is ShelfCard.Marked -> gameRepository.observeByPlayState(shelf.state).collect { games ->
                            publishGameItems(
                                games.notHiddenAt(HideLocationType.ALL_GAMES)
                                    .gameSorted(_uiState.value.gameSortMode).toXmbItems(),
                                keepCursor,
                            )
                            keepCursor = true
                        }

                        is ShelfCard.RecentlyAdded -> gameRepository.observeRecentlyAdded().collect { games ->
                            publishGameItems(
                                games.notHiddenAt(HideLocationType.ALL_GAMES).toXmbItems(),
                                keepCursor,
                            )
                            keepCursor = true
                        }
                    }
                }
                BuiltInCategory.RECENTLY_PLAYED -> {
                    var keepCursor = keepCursorOnRow
                    combine(
                        gameRepository.observeRecentlyPlayed(RECENTLY_PLAYED_LIMIT),
                        musicRepository.observeRecentlyPlayedTracks(RECENTLY_PLAYED_LIMIT),
                        bookRepository.observeRecentlyOpenedBooks(RECENTLY_PLAYED_LIMIT),
                        videoRepository.observeRecentlyWatched(),

                        recentFilterAndApps(),
                    ) { games, tracks, books, videos, filterAndApps ->
                        val (filter, appRows) = filterAndApps

                        currentMusicTracks = tracks
                        val visibleGames = games.notHiddenAt(HideLocationType.ALL_GAMES)
                        mergeRecents(
                            games  = visibleGames.map { it.lastPlayedAt ?: 0L }.zip(visibleGames.toXmbItems()),

                            music  = tracks.recentMusicRows(),
                            books  = books.map { it.lastOpenedAt ?: 0L }.zip(bookItems(books)),
                            videos = videos.map { it.lastWatchedAt ?: 0L }.zip(videos.toVideoItems()),
                            apps   = appRows,
                            filter = filter,
                            limit  = RECENTLY_PLAYED_LIMIT,
                        )
                    }.collect { items ->

                        publishGameItems(items, keepCursor)
                        keepCursor = true
                    }
                }
                BuiltInCategory.ANDROID -> {
                    _uiState.update { it.copy(currentItems = ANDROID_ITEMS) }
                }
                BuiltInCategory.SETTINGS -> {
                    _uiState.update { state ->
                        val items = SETTINGS_ROOT_ITEMS
                        state.copy(
                            currentItems = items,
                            selectedItemIndex = state.selectedItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                        )
                    }
                }
                BuiltInCategory.GAMES -> {
                    val platformId = _uiState.value.selectedPlatformId
                    val collectionId = _uiState.value.selectedCollectionId
                    if (collectionId != null) {
                        var keepCursor = keepCursorOnRow
                        collectionRepository.observeGames(collectionId).collect { games ->
                            val visible = games.notHiddenAt(HideLocationType.COLLECTION, collectionId.toString())
                            val items = if (visible.isEmpty()) listOf(emptyCollectionItem())
                                        else visible.gameSorted(_uiState.value.gameSortMode).toXmbItems()
                            publishGameItems(items, keepCursor)
                            keepCursor = true
                        }
                    } else if (platformId == ALL_GAMES_PLATFORM_ID) {
                        var keepCursor = keepCursorOnRow
                        gameRepository.observeAllGames().collect { games ->
                            val visible = games.notHiddenAt(HideLocationType.ALL_GAMES)
                            val items = if (visible.isEmpty()) listOf(emptyAllGamesItem())
                                        else visible.gameSorted(_uiState.value.gameSortMode).toXmbItems()
                            publishGameItems(items, keepCursor)
                            keepCursor = true
                        }
                    } else if (platformId == FAVORITES_PLATFORM_ID) {
                        var keepCursor = keepCursorOnRow
                        gameRepository.observeFavorites().collect { games ->
                            val visible = games.notHiddenAt(HideLocationType.FAVORITES)
                            val items = if (visible.isEmpty()) listOf(emptyFavoritesItem())
                                        else visible.gameSorted(_uiState.value.gameSortMode).toXmbItems()
                            publishGameItems(items, keepCursor)
                            keepCursor = true
                        }
                    } else if (platformId == MISSING_PLATFORM_ID) {
                        var keepCursor = keepCursorOnRow
                        gameRepository.observeMissing().collect { games ->
                            val items = if (games.isEmpty()) listOf(emptyMissingItem())
                                        else games.gameSorted(_uiState.value.gameSortMode)
                                            .toXmbItems()

                                            .map { it.copy(subtitle = MISSING_REASON) }
                            publishGameItems(items, keepCursor)
                            keepCursor = true
                        }
                    } else if (platformId != null) {
                        var keepCursor = keepCursorOnRow
                        gameRepository.observePlatformGames(platformId).collect { all ->

                            val games = all.filter { it.contentType == GameContentType.GAME }

                            val visible = if (platformId == ANDROID_PLATFORM_ID)
                                games.notHiddenAt(HideLocationType.ANDROID_PLATFORM)
                            else
                                games.notHiddenAt(HideLocationType.PLATFORM, platformId)
                            val items = if (visible.isEmpty()) listOf(emptyFolderItem(platformId))
                                        else visible.gameSorted(_uiState.value.gameSortMode).toXmbItems()
                            publishGameItems(items, keepCursor)
                            keepCursor = true
                        }
                    } else {
                        combine(
                            memoryCardRepository.observeEnabled(),
                            gameRepository.observeAll(),
                            collectionRepository.observeCollections(),
                        ) { _, _, _ -> }.collect {
                            _uiState.update { it.copy(currentItems = memoryCardItems()) }
                        }
                    }
                }
                BuiltInCategory.MUSIC -> when (val nav = _uiState.value.musicNav) {
                    MusicNav.Root -> {
                        clearMusicTrackCache()
                        _uiState.update { it.copy(currentItems = musicRootItems()) }
                    }
                    MusicNav.AllMusic -> musicRepository.observeAllTracks().collect { tracks ->
                        setMusicTrackItems(tracks, emptyAllMusicItem())
                    }
                    is MusicNav.Playlist -> musicRepository.observePlaylistTracks(nav.id).collect { tracks ->
                        setMusicTrackItems(tracks, emptyPlaylistItem(), trailing = listOf(addTracksItem()))
                    }
                    MusicNav.Playlists -> {
                        clearMusicTrackCache()
                        musicRepository.observePlaylists().collect { playlists ->
                            _uiState.update { it.copy(currentItems = playlistRootItems(playlists), musicPlaylists = playlists) }
                        }
                    }
                }
                BuiltInCategory.VIDEO -> when (val nav = _uiState.value.videoNav) {
                    VideoNav.Root -> _uiState.update { it.copy(currentItems = videoRootItems()) }
                    VideoNav.Collections -> _uiState.update { it.copy(currentItems = videoCollectionsItems()) }
                    VideoNav.AllVideos -> videoRepository.observeAllVideos().collect { videos ->
                        setVideoItems(videos, emptyAllVideosItem())
                    }
                    VideoNav.RecentlyWatched -> videoRepository.observeRecentlyWatched().collect { videos ->

                        setVideoItems(videos, emptyRecentItem(), sortable = false)
                    }
                    VideoNav.Favorites -> videoRepository.observeFavorites().collect { videos ->
                        setVideoItems(videos, emptyFavoriteVideosItem())
                    }
                    VideoNav.Playlists -> videoRepository.observePlaylists().collect { playlists ->
                        _uiState.update { it.copy(currentItems = videoPlaylistItems(playlists), videoPlaylists = playlists) }
                    }
                    is VideoNav.Playlist -> videoRepository.observePlaylistVideos(nav.id).collect { videos ->

                        setVideoItems(videos, emptyPlaylistVideosItem(), sortable = false)
                    }
                    VideoNav.Libraries -> videoRepository.observeLibraries().collect { libs ->
                        _uiState.update { it.copy(currentItems = videoLibraryItems(libs)) }
                    }
                    is VideoNav.Library -> videoRepository.observeVideosByLibrary(nav.id).collect { videos ->
                        setVideoItems(videos, emptyAllVideosItem())
                    }
                }
                BuiltInCategory.PHOTO -> when (val nav = _uiState.value.photoNav) {
                    PhotoNav.Root -> _uiState.update { it.copy(currentItems = photoRootItems()) }
                    PhotoNav.AllPhotos -> photoRepository.observeAllPhotos().collect { photos ->
                        setPhotoItems(photos, emptyAllPhotosItem())
                    }
                    PhotoNav.Albums -> photoRepository.observeLibraries().collect { libs ->
                        _uiState.update { it.copy(currentItems = photoAlbumItems(libs)) }
                    }
                    is PhotoNav.Library -> photoRepository.observePhotosByLibrary(nav.id).collect { photos ->
                        setPhotoItems(photos, emptyLibraryPhotosItem())
                    }
                }
                BuiltInCategory.LIBRARY -> when (val nav = _uiState.value.booksNav) {
                    BooksNav.Root -> _uiState.update { it.copy(currentItems = booksRootItems()) }
                    BooksNav.Shelves -> bookRepository.observeLibraries().collect { shelves ->
                        _uiState.update { it.copy(bookLibraries = shelves, currentItems = bookShelfItems()) }
                    }
                    BooksNav.AllBooks -> bookRepository.observeAllBooks().collect { books ->
                        _uiState.update { it.copy(currentItems = bookItems(books.bookSorted(it.bookSortMode)).ifEmpty { listOf(emptyBooksItem()) }) }
                    }
                    is BooksNav.Shelf -> bookRepository.observeBooksByLibrary(nav.id).collect { books ->
                        _uiState.update { it.copy(currentItems = bookItems(books.bookSorted(it.bookSortMode)).ifEmpty { listOf(emptyBooksItem()) }) }
                    }
                    BooksNav.SeriesList -> bookRepository.observeAllBooks().collect { books ->
                        _uiState.update {
                            it.copy(
                                bookSeries = books.seriesGroups(),
                                currentItems = bookSeriesItems().ifEmpty { listOf(emptySeriesItem()) },
                            )
                        }
                    }

                    is BooksNav.Series -> bookRepository.observeAllBooks().collect { books ->
                        val inSeries = books.filter { it.seriesName == nav.name }.inSeriesOrder()
                        _uiState.update { it.copy(currentItems = bookItems(inSeries).ifEmpty { listOf(emptyBooksItem()) }) }
                    }
                }
                else -> {
                    val openCollectionId = _uiState.value.selectedCollectionId
                    if (openCollectionId != null) {
                        var keepCursor = keepCursorOnRow
                        collectionRepository.observeGames(openCollectionId).collect { games ->
                            val visible = games.notHiddenAt(HideLocationType.COLLECTION, openCollectionId.toString())
                            val items = if (visible.isEmpty()) listOf(emptyCollectionItem())
                                        else visible.gameSorted(_uiState.value.gameSortMode).toXmbItems()
                            publishGameItems(items, keepCursor)
                            keepCursor = true
                        }
                        return@launch
                    }
                    if (category.isGamingCategory) {
                        val gameRows = gameCategoryRepository.itemsForCategory(category.id)
                            .filterIsInstance<com.psplauncher.core.data.repository.GameCategoryItem.GameItem>()
                            .filterNot { isHiddenAt(HiddenPlacement.gameKey(it.game.id), HideLocationType.CATEGORY, category.id) }
                        val pinnedGameIds = gameRows.filter { it.pinned }.map { it.game.id }.toSet()
                        val gameItems = gameRows.map { it.game }.gameSorted(_uiState.value.gameSortMode).toXmbItems().map { xmb ->
                            if (xmb.gameId in pinnedGameIds) xmb.copy(subtitle = "Pinned") else xmb
                        }

                        val collectionItems = _uiState.value.collections
                            .filter { it.categoryId == category.id }
                            .sortedByDescending { it.isPinned }
                            .map { collection ->
                                val games = countLabel(collection.gameCount, "game", "games")
                                XMBItem(
                                    id = "col_${collection.id}",
                                    title = collection.name,
                                    subtitle = if (collection.isPinned) "Pinned · $games" else games,
                                    collectionId = collection.id,
                                    iconKey = collection.iconKey,
                                    type = XMBItemType.COLLECTION,
                                )
                            }
                        val combined = collectionItems + gameItems
                        val items = if (combined.isEmpty()) listOf(emptyCategoryItem(category)) else combined

                        publishGameItems(items + addGamesItem(), keepCursorOnRow)
                    } else {
                        val apps = appCategoryRepository.appsForCategory(category.id)
                            .notHiddenAt(HideLocationType.CATEGORY, category.id)
                        val appItems = apps.map { it.toXmbItem(gameRepository.getAppEntry(it.packageName)) }

                        val collectionItems = _uiState.value.collections
                            .filter { it.categoryId == category.id }
                            .sortedByDescending { it.isPinned }
                            .map { collection ->
                                val count = countLabel(collection.gameCount, "app", "apps")
                                XMBItem(
                                    id = "col_${collection.id}",
                                    title = collection.name,
                                    subtitle = if (collection.isPinned) "Pinned · $count" else count,
                                    collectionId = collection.id,
                                    iconKey = collection.iconKey,
                                    type = XMBItemType.COLLECTION,
                                )
                            }
                        val combined = collectionItems + appItems
                        val items = if (combined.isEmpty()) listOf(emptyCategoryItem(category)) else combined

                        val lead = if (category.id == NETWORK_CATEGORY_ID) listOf(quickSearchItem()) else emptyList()

                        _uiState.update { it.copy(currentItems = lead + items + addAppsItem()) }
                    }
                }
            }
        }
    }

    private fun CategorizedApp.toXmbItem(
        artwork: com.psplauncher.core.domain.model.Game? = null,
    ): XMBItem = XMBItem(
        id           = "app_$packageName",
        title        = label,
        subtitle     = if (pinned) "Pinned" else null,
        packageName  = packageName,
        isAndroidApp = true,
        iconUri      = artwork?.let { it.iconUri ?: it.heroUri ?: it.artworkUri },

        artworkUri   = artwork?.let { it.artworkUri ?: it.heroUri },
        heroUri      = artwork?.heroUri,
        accentColor  = artwork?.let { platformCache[it.platformId]?.accentColor },
    )

    private fun libraryColumn(body: List<XMBItem>, scope: SearchScope): List<XMBItem> =
        body + librarySearchItem(scope)

    private fun librarySearchItem(scope: SearchScope): XMBItem = XMBItem(
        id       = SEARCH_ITEM_ID,
        title    = scope.label,
        subtitle = scope.hint,
        type     = XMBItemType.SEARCH,
    )

    private fun quickSearchItem(): XMBItem = XMBItem(
        id       = QUICK_SEARCH_ITEM_ID,
        title    = "Quick Search",
        subtitle = "Search the web, or type an address",
        type     = XMBItemType.SEARCH,
    )

    private fun addAppsItem(): XMBItem = XMBItem(
        id       = ADD_APPS_ITEM_ID,
        title    = "Add Apps",
        subtitle = "Pick installed apps to add to this section",
        type     = XMBItemType.ADD_ACTION,
    )

    private fun addGamesItem(): XMBItem = XMBItem(
        id       = ADD_GAMES_ITEM_ID,
        title    = "Add Games",
        subtitle = "Pick games and collections to add to this category",
        type     = XMBItemType.ADD_ACTION,
    )

    private suspend fun refreshMusicRootPreservingCursor() {
        val s = _uiState.value
        val selectedId = s.currentItems.getOrNull(s.selectedItemIndex)?.id
        clearMusicTrackCache()
        val items = musicRootItems()
        val restored = selectedId
            ?.let { id -> items.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: s.selectedItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentItems = items, selectedItemIndex = restored) }
    }

    private fun musicAddActions(): List<XMBItem> = buildList {
        if (_uiState.value.musicFolders.none { it.lastScannedAt != null }) add(addMusicFolderItem())
        add(addMusicAppsItem())
    }

    private suspend fun musicRootItems(): List<XMBItem> =
        libraryColumn(
            _uiState.value.musicRootSections() + musicAppItems() + collapseAddRows(musicAddActions()),
            SearchScope.MUSIC,
        )

    private fun currentAddActions(): List<XMBItem> = when (currentCategory()?.id) {
        BuiltInCategory.MUSIC   -> musicAddActions()
        BuiltInCategory.VIDEO   -> videoAddActions()
        BuiltInCategory.PHOTO   -> photoAddActions()
        BuiltInCategory.LIBRARY -> booksAddActions()
        else -> emptyList()
    }

    private fun openAddMenu() {
        val actions = currentAddActions()
        if (actions.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                activeContextMenu = XMBContextMenu(state = MenuState(title = "Add", rows = actions.map { XMBContextMenuItem(it.id, it.title) }), isAddMenu = true)
            )
        }
    }

    private fun addMusicFolderItem(): XMBItem = XMBItem(
        id       = ADD_MUSIC_FOLDER_ITEM_ID,
        title    = "Add Music Folder",
        subtitle = "Set your Music root folder in Settings to get started",
        type     = XMBItemType.ADD_ACTION,
    )

    private fun playlistRootItems(playlists: List<com.psplauncher.core.domain.model.Playlist>): List<XMBItem> {
        val rows = playlists.map { pl ->
            XMBItem(
                id         = "pl_${pl.id}",
                title      = pl.name,
                subtitle   = countLabel(pl.trackCount, "track", "tracks"),
                playlistId = pl.id,
                type       = XMBItemType.PLAYLIST,
            )
        }
        return rows + XMBItem(
            id       = CREATE_PLAYLIST_ITEM_ID,
            title    = "Create Playlist",
            subtitle = "Start a new playlist",
            type     = XMBItemType.ADD_ACTION,
        )
    }

    private suspend fun musicAppItems(): List<XMBItem> {
        val apps = appCategoryRepository.appsForCategory(MUSIC_APPS_CATEGORY_ID)
            .notHiddenAt(HideLocationType.CATEGORY, MUSIC_APPS_CATEGORY_ID)
        return apps.map { it.toXmbItem(gameRepository.getAppEntry(it.packageName)) }
    }

    private fun addMusicAppsItem(): XMBItem = XMBItem(
        id       = ADD_MUSIC_APPS_ITEM_ID,
        title    = "Add Music Apps",
        subtitle = "Pick installed apps to show here",
        type     = XMBItemType.ADD_ACTION,
    )

    private fun addTracksItem(): XMBItem = XMBItem(
        id       = ADD_TRACKS_ITEM_ID,
        title    = "Add Tracks",
        subtitle = "Pick songs to add to this playlist",
        type     = XMBItemType.ADD_ACTION,
    )

    private fun setMusicTrackItems(
        tracks: List<MusicTrack>,
        emptyItem: XMBItem,
        trailing: List<XMBItem> = emptyList(),
    ) {
        currentMusicTracksRaw = tracks
        val sorted = tracks.trackSorted(_uiState.value.musicSortMode)
        currentMusicTracks = sorted
        val items = if (sorted.isEmpty()) listOf(emptyItem) else sorted.toMusicItems()
        _uiState.update { it.copy(currentItems = items + trailing) }
    }

    private fun clearMusicTrackCache() {
        currentMusicTracks = emptyList()
        currentMusicTracksRaw = emptyList()
    }

    private fun emptyAllMusicItem(): XMBItem = XMBItem(
        id       = EMPTY_CATEGORY_ITEM_ID,
        title    = "No music found",
        subtitle = "Add a music folder in Settings → Music",
        type     = XMBItemType.EMPTY,
    )

    private fun emptyPlaylistItem(): XMBItem = XMBItem(
        id       = EMPTY_PLAYLIST_ITEM_ID,
        title    = "This playlist is empty",
        subtitle = "Add tracks below or from a song's Options menu.",
        type     = XMBItemType.EMPTY,
    )

    private fun openMusicView(nav: MusicNav) = navigateRememberingCursor { it.copy(musicNav = nav) }

    private fun closeMusicView() = openMusicView(MusicNav.Root)

    @Volatile private var hiddenKeys: Set<String> = emptySet()

    private fun observeHiddenPlacements() {
        viewModelScope.launch {
            hiddenPlacementDao.observeAll().collect { rows ->
                hiddenKeys = rows.map { "${it.itemKey}|${it.locationType}|${it.locationId}" }.toSet()

                loadItemsForCategory(currentCategory())
            }
        }
    }

    private fun isHiddenAt(itemKey: String, type: HideLocationType, locationId: String = ""): Boolean =
        hiddenKeys.contains("$itemKey|${type.name}|$locationId")

    @JvmName("gamesNotHiddenAt")
    private fun List<Game>.notHiddenAt(type: HideLocationType, locationId: String = ""): List<Game> =
        filterNot { isHiddenAt(HiddenPlacement.gameKey(it.id), type, locationId) }

    @JvmName("appsNotHiddenAt")
    private fun List<CategorizedApp>.notHiddenAt(type: HideLocationType, locationId: String = ""): List<CategorizedApp> =
        filterNot { isHiddenAt(HiddenPlacement.appKey(it.packageName), type, locationId) }

    private fun persistHide(itemKey: String, itemLabel: String, type: HideLocationType, locationId: String, locationLabel: String) {
        viewModelScope.launch {
            hiddenPlacementDao.upsert(
                HiddenPlacementEntity(itemKey, itemLabel, type.name, locationId, locationLabel, System.currentTimeMillis())
            )
        }
    }

    private fun categoryDisplayName(id: String): String = _uiState.value.categoryDisplayNameOf(id)

    private fun currentHideLocation(): Triple<HideLocationType, String, String>? {
        val s = _uiState.value
        val cat = currentCategory()
        return when {
            s.selectedCollectionId != null -> {
                val name = s.collections.firstOrNull { it.id == s.selectedCollectionId }?.name ?: "Collection"
                Triple(HideLocationType.COLLECTION, s.selectedCollectionId.toString(), name)
            }
            s.selectedPlatformId == FAVORITES_PLATFORM_ID || cat?.id == BuiltInCategory.FAVORITES ->
                Triple(HideLocationType.FAVORITES, "", "Favorites")

            s.selectedPlatformId == MISSING_PLATFORM_ID -> null
            s.selectedPlatformId == ANDROID_PLATFORM_ID -> Triple(HideLocationType.ANDROID_PLATFORM, "", "Android")

            s.selectedPlatformId == ALL_GAMES_PLATFORM_ID ->
                Triple(HideLocationType.ALL_GAMES, "", "All Games")

            s.selectedPlatformId != null -> {
                val name = enabledCards.firstOrNull { it.platformId == s.selectedPlatformId }?.displayName
                    ?: s.selectedPlatformId
                Triple(HideLocationType.PLATFORM, s.selectedPlatformId, name)
            }

            cat != null && cat.isGamingCategory && cat.id != BuiltInCategory.GAMES ->
                Triple(HideLocationType.CATEGORY, cat.id, cat.name)
            else -> null
        }
    }

    private fun observeVideo() {
        viewModelScope.launch {
            videoRepository.observeRecentlyWatched().collect { videos ->
                val resumable = videos.firstOrNull { v ->
                    val total = v.durationMs ?: 0L
                    total > 0L && v.resumePositionMs > 0L &&
                        v.resumePositionMs.toFloat() / total < RESUME_DONE_FRACTION
                }
                if (_uiState.value.resumeVideo?.id != resumable?.id) {
                    _uiState.update { it.copy(resumeVideo = resumable) }
                    if (currentCategory()?.id == BuiltInCategory.VIDEO &&
                        _uiState.value.videoNav == VideoNav.Root
                    ) {
                        loadItemsForCategory(currentCategory(), keepCursorOnRow = true)
                    }
                }
            }
        }
        viewModelScope.launch {
            videoRepository.observeLibraries().collect { libraries ->
                _uiState.update { it.copy(videoLibraries = libraries) }
                if (currentCategory()?.id == BuiltInCategory.VIDEO &&
                    _uiState.value.videoNav == VideoNav.Root
                ) {
                    loadItemsForCategory(currentCategory())
                }
            }
        }
    }

    private fun videoAddActions(): List<XMBItem> = buildList {
        if (_uiState.value.videoLibraries.none { it.lastScannedAt != null }) add(addVideosItem())
        add(addVideoAppsItem())
    }

    private suspend fun videoRootItems(): List<XMBItem> =
        libraryColumn(
            _uiState.value.videoRootSections() + videoAppItems() + collapseAddRows(videoAddActions()),
            SearchScope.VIDEOS,
        )

    private fun addVideosItem(): XMBItem = XMBItem(
        id       = ADD_VIDEOS_ITEM_ID,
        title    = "Add Videos",
        subtitle = "Set your Video root folder in Settings to get started",
        type     = XMBItemType.ADD_ACTION,
    )

    private fun videoCollectionsItems(): List<XMBItem> = listOf(
        XMBItem(
            id       = RECENTLY_WATCHED_ITEM_ID,
            title    = "Recently Watched",
            subtitle = "Pick up where you left off",
            type     = XMBItemType.VIDEO_RECENT,
        ),
        XMBItem(
            id       = FAVORITE_VIDEOS_ITEM_ID,
            title    = "Favorites",
            subtitle = "Your starred videos",
            type     = XMBItemType.VIDEO_FAVORITES,
        ),
        XMBItem(
            id       = VIDEO_PLAYLISTS_ITEM_ID,
            title    = "Playlists",
            subtitle = "Build and play your own lists",
            type     = XMBItemType.PLAYLIST,
        ),
    )

    private fun videoLibraryItems(libraries: List<com.psplauncher.core.domain.model.VideoLibrary>): List<XMBItem> {
        val rows = libraries.map { lib ->
            XMBItem(
                id       = "vlib_${lib.id}",
                title    = lib.displayName,
                subtitle = countLabel(lib.videoCount, "video", "videos"),
                coverUri = lib.artworkUri,
                type     = XMBItemType.VIDEO_FOLDER,
            )
        }
        return rows.ifEmpty {
            listOf(
                XMBItem(
                    id = EMPTY_CATEGORY_ITEM_ID,
                    title = "No video libraries yet",
                    subtitle = "Set a root folder in Settings → Video",
                    type = XMBItemType.EMPTY,
                ),
            )
        }
    }

    private suspend fun videoAppItems(): List<XMBItem> {
        val apps = appCategoryRepository.appsForCategory(VIDEO_APPS_CATEGORY_ID)
            .notHiddenAt(HideLocationType.CATEGORY, VIDEO_APPS_CATEGORY_ID)
        return apps.map { it.toXmbItem(gameRepository.getAppEntry(it.packageName)) }
    }

    private fun addVideoAppsItem(): XMBItem = XMBItem(
        id       = ADD_VIDEO_APPS_ITEM_ID,
        title    = "Add Video Apps",
        subtitle = "Pick installed apps to show here",
        type     = XMBItemType.ADD_ACTION,
    )

    private fun List<com.psplauncher.core.domain.model.Video>.toVideoItems(): List<XMBItem> =
        map { it.toXmbRow() }

    private fun setVideoItems(
        videos: List<com.psplauncher.core.domain.model.Video>,
        emptyItem: XMBItem,
        sortable: Boolean = true,
    ) {
        val ordered = if (sortable) videos.videoSorted(_uiState.value.videoSortMode) else videos
        val items = if (ordered.isEmpty()) listOf(emptyItem) else ordered.toVideoItems()
        _uiState.update { it.copy(currentItems = items) }
    }

    private fun videoPlaylistItems(playlists: List<com.psplauncher.core.domain.model.VideoPlaylist>): List<XMBItem> {
        val rows = playlists.map { pl ->
            XMBItem(
                id         = "vpl_${pl.id}",
                title      = pl.name,
                subtitle   = countLabel(pl.videoCount, "video", "videos"),
                playlistId = pl.id,
                type       = XMBItemType.PLAYLIST,
            )
        }
        return rows + XMBItem(
            id       = CREATE_VIDEO_PLAYLIST_ITEM_ID,
            title    = "Create Playlist",
            subtitle = "Start a new video playlist",
            type     = XMBItemType.ADD_ACTION,
        )
    }

    private fun emptyAllVideosItem(): XMBItem = XMBItem(
        id       = EMPTY_CATEGORY_ITEM_ID,
        title    = "No videos found",
        subtitle = "Add a video library in Settings → Video",
        type     = XMBItemType.EMPTY,
    )

    private fun emptyRecentItem(): XMBItem = XMBItem(
        id       = EMPTY_CATEGORY_ITEM_ID,
        title    = "Nothing watched yet",
        subtitle = "Videos you play show up here",
        type     = XMBItemType.EMPTY,
    )

    private fun emptyFavoriteVideosItem(): XMBItem = XMBItem(
        id       = EMPTY_CATEGORY_ITEM_ID,
        title    = "No favorites yet",
        subtitle = "Star a video from its ⚙ Options menu",
        type     = XMBItemType.EMPTY,
    )

    private fun emptyPlaylistVideosItem(): XMBItem = XMBItem(
        id       = EMPTY_PLAYLIST_ITEM_ID,
        title    = "This playlist is empty",
        subtitle = "Add videos from a video's ⚙ Options menu",
        type     = XMBItemType.EMPTY,
    )

    private fun handleVideoSelection(item: XMBItem): Boolean = when {
        item.id == SEARCH_ITEM_ID -> { openSearch(SearchScope.VIDEOS); true }
        item.id == ADD_MENU_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openAddMenu(); true }
        item.type == XMBItemType.EMPTY -> true
        item.id == ALL_VIDEOS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openVideoView(VideoNav.AllVideos); true }
        item.id == VIDEO_COLLECTIONS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openVideoView(VideoNav.Collections); true }
        item.id == RECENTLY_WATCHED_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openVideoView(VideoNav.RecentlyWatched); true }
        item.id == FAVORITE_VIDEOS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openVideoView(VideoNav.Favorites); true }
        item.id == VIDEO_PLAYLISTS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openVideoView(VideoNav.Playlists); true }
        item.id == CREATE_VIDEO_PLAYLIST_ITEM_ID -> { menuSound.play(MenuSound.SELECT); promptCreateVideoPlaylist(); true }
        item.id.startsWith("vpl_") && item.playlistId != null -> {
            menuSound.play(MenuSound.SELECT); openVideoView(VideoNav.Playlist(item.playlistId, item.title)); true
        }
        item.id == VIDEO_LIBRARIES_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openVideoView(VideoNav.Libraries); true }
        item.id == ADD_VIDEOS_ITEM_ID -> {
            menuSound.play(MenuSound.SELECT)
            _uiState.update { it.copy(activeSettingsScreen = "settings_video") }
            true
        }
        item.id == ADD_VIDEO_APPS_ITEM_ID -> {
            menuSound.play(MenuSound.SELECT)
            openAppPicker(AppPickerTarget.CategoryShortcuts(VIDEO_APPS_CATEGORY_ID), "Add Video Apps")
            true
        }
        item.id.startsWith("vlib_") -> {
            menuSound.play(MenuSound.SELECT)
            val libId = item.id.removePrefix("vlib_")
            openVideoView(VideoNav.Library(libId, item.title))
            true
        }
        item.type == XMBItemType.VIDEO_FILE -> {
            menuSound.play(MenuSound.SELECT)
            _uiState.update { it.copy(activeVideoId = item.id.removePrefix("vid_")) }
            true
        }

        item.packageName != null -> {
            menuSound.play(MenuSound.LAUNCH)
            launchAppWithDisc(item.packageName, item.shelfCoverArt)
            true
        }
        else -> false
    }

    private val viewCursor = mutableMapOf<String, Int>()

    private fun viewCursorKey(s: XMBUiState): String {
        val catId = s.categories.getOrNull(s.selectedCategoryIndex)?.id ?: "none"
        val sub = when {
            catId == BuiltInCategory.MUSIC -> "music_${musicNavKey(s.musicNav)}"
            catId == BuiltInCategory.VIDEO -> "video_${videoNavKey(s.videoNav)}"
            catId == BuiltInCategory.PHOTO -> "photo_${photoNavKey(s.photoNav)}"
            catId == BuiltInCategory.LIBRARY -> "books_${booksNavKey(s.booksNav)}"
            catId == BuiltInCategory.SETTINGS -> "settings_root"
            s.selectedCollectionId != null -> "col_${s.selectedCollectionId}"
            s.selectedPlatformId != null   -> "plat_${s.selectedPlatformId}"
            else                           -> "root"
        }
        return "$catId/$sub"
    }

    private fun navigateRememberingCursor(mutate: (XMBUiState) -> XMBUiState) {
        val cur = _uiState.value
        viewCursor[viewCursorKey(cur)] = cur.selectedItemIndex
        _uiState.update { state ->
            val next = mutate(state)
            val remembered = viewCursor[viewCursorKey(next)] ?: 0
            next.copy(selectedItemIndex = remembered)
        }
        loadItemsForCategory(currentCategory())
    }

    private fun musicNavKey(nav: MusicNav): String = when (nav) {
        MusicNav.Root        -> "root"
        MusicNav.AllMusic    -> "all"
        MusicNav.Playlists   -> "playlists"
        is MusicNav.Playlist -> "playlist_${nav.id}"
    }

    private fun videoNavKey(nav: VideoNav): String = when (nav) {
        VideoNav.Root            -> "root"
        VideoNav.AllVideos       -> "all"
        VideoNav.Collections     -> "collections"
        VideoNav.RecentlyWatched -> "recent"
        VideoNav.Favorites       -> "favorites"
        VideoNav.Playlists       -> "playlists"
        is VideoNav.Playlist     -> "playlist_${nav.id}"
        VideoNav.Libraries       -> "libraries"
        is VideoNav.Library      -> "library_${nav.id}"
    }

    private fun openVideoView(nav: VideoNav) = navigateRememberingCursor { it.copy(videoNav = nav) }

    private fun closeVideoView() = openVideoView(VideoNav.Root)

    fun onCloseVideoDetail() {
        _uiState.update { it.copy(activeVideoId = null, activeVideoAutoPlay = false, pendingVideoDetailAction = null) }
    }

    fun consumeVideoDetailAction() {
        _uiState.update { it.copy(pendingVideoDetailAction = null) }
    }

    private fun promptCreateVideoPlaylist(forVideoId: String? = null) {
        _uiState.update { it.copy(
            playlistNameDialog = PlaylistNameDialogState(title = "New Video Playlist", videoContext = true, forVideoId = forVideoId)
        )}
    }

    private fun promptRenameVideoPlaylist(playlistId: Long) {
        val name = _uiState.value.currentItems.firstOrNull { it.playlistId == playlistId }?.title.orEmpty()
        _uiState.update { it.copy(
            playlistNameDialog = PlaylistNameDialogState(
                title = "Rename Playlist",
                initialText = name,
                renamePlaylistId = playlistId,
                videoContext = true,
            )
        )}
    }

    private fun openVideoPlaylistContextMenu(playlistId: Long, name: String) {
        val items = videoPlaylistContextMenuItems()
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(state = MenuState(title = name, rows = items), videoPlaylistId = playlistId)) }
    }

    private fun openVideoContextMenu(item: XMBItem): Boolean {
        if (item.menuHostCategory(currentCategory()?.id) != BuiltInCategory.VIDEO) return false
        return when {
            item.type == XMBItemType.VIDEO_FILE && item.id.startsWith("vid_") -> {
                openVideoFileContextMenu(item.id.removePrefix("vid_"), item.title); true
            }
            item.type == XMBItemType.VIDEO_FOLDER && item.id.startsWith("vlib_") -> {
                openVideoLibraryContextMenu(item.id.removePrefix("vlib_"), item.title); true
            }
            item.type == XMBItemType.PLAYLIST && item.playlistId != null -> {
                openVideoPlaylistContextMenu(item.playlistId, item.title); true
            }
            item.packageName != null -> {
                openAppContextMenu(item, categoryIdOverride = VIDEO_APPS_CATEGORY_ID); true
            }
            else -> false
        }
    }

    private fun openVideoFileContextMenu(videoId: String, title: String) {
        viewModelScope.launch {
            val video = videoRepository.getVideo(videoId) ?: return@launch
            val inPlaylist = _uiState.value.videoNav is VideoNav.Playlist
            val items = videoFileContextMenuItems(
                isFavorite = video.isFavorite,
                resumePositionMs = video.resumePositionMs,
                hasWatchStamp = video.lastWatchedAt != null,
                inPlaylist = inPlaylist,
            )
            _uiState.update { it.copy(activeContextMenu = XMBContextMenu(state = MenuState(title = title, rows = items), videoFileId = videoId)) }
        }
    }

    private fun handleVideoFileAction(videoId: String, itemId: String) {
        when (itemId) {
            "video_play", "video_resume", "video_details" ->
                _uiState.update { it.copy(activeVideoId = videoId) }
            "video_favorite" -> appAction {
                val v = videoRepository.getVideo(videoId) ?: return@appAction
                videoRepository.setFavorite(videoId, !v.isFavorite)
            }
            "video_add_playlist" -> openVideoPlaylistPicker(videoId)
            "video_remove_playlist" -> (_uiState.value.videoNav as? VideoNav.Playlist)?.let { nav ->
                appAction { videoRepository.removeVideoFromPlaylist(nav.id, videoId) }
            }

            "video_remove_recent" -> appAction { videoRepository.clearLastWatched(videoId) }
            "video_remove" -> appAction { videoRepository.removeVideo(videoId) }
        }
    }

    private fun openVideoPlaylistPicker(videoId: String, selectIndex: Int? = 0) {
        viewModelScope.launch {
            val playlists = videoRepository.observePlaylists().first()
            val memberOf = videoRepository.getPlaylistIdsForVideo(videoId).toSet()
            val items = buildList {
                playlists.forEach { pl -> add(XMBContextMenuItem("vpl_${pl.id}", pl.name, checked = pl.id in memberOf)) }
                add(XMBContextMenuItem("vpl_new", "Create New Playlist"))
            }
            _uiState.update { it.copy(
                activeContextMenu = XMBContextMenu(state = MenuState(title = "Add to Playlist", rows = items, selectedIndex = selectIndex?.coerceIn(0, items.lastIndex.coerceAtLeast(0))), videoPlaylistPickerVideoId = videoId)
            )}
        }
    }

    private fun openVideoLibraryContextMenu(libraryId: String, name: String) {
        val items = videoLibraryContextMenuItems()
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(state = MenuState(title = name, rows = items), videoLibraryId = libraryId)) }
    }

    private fun handleVideoLibraryAction(libraryId: String, itemId: String) {
        when (itemId) {
            "video_lib_open" -> {
                val name = _uiState.value.currentItems.firstOrNull { it.id == "vlib_$libraryId" }?.title.orEmpty()
                openVideoView(VideoNav.Library(libraryId, name))
            }
            "video_lib_manage" -> _uiState.update { it.copy(activeSettingsScreen = "settings_video") }
        }
    }

    private fun handleVideoPlaylistRowAction(playlistId: Long, itemId: String) {
        when (itemId) {
            "open_video_playlist" -> {
                val name = _uiState.value.currentItems.firstOrNull { it.playlistId == playlistId }?.title.orEmpty()
                openVideoView(VideoNav.Playlist(playlistId, name))
            }
            "rename_video_playlist" -> promptRenameVideoPlaylist(playlistId)
            "delete_video_playlist" -> appAction {
                videoRepository.deletePlaylist(playlistId)
                if ((_uiState.value.videoNav as? VideoNav.Playlist)?.id == playlistId) openVideoView(VideoNav.Playlists)
            }
        }
    }

    private fun observeContinueBook() {
        viewModelScope.launch {
            bookRepository.observeRecentlyOpenedBooks(1).collect { books ->
                val latest = books.firstOrNull()
                if (_uiState.value.continueBook?.id != latest?.id) {
                    _uiState.update { it.copy(continueBook = latest) }
                    if (currentCategory()?.id == BuiltInCategory.LIBRARY &&
                        _uiState.value.booksNav == BooksNav.Root
                    ) {
                        loadItemsForCategory(currentCategory(), keepCursorOnRow = true)
                    }
                }
            }
        }
    }

    private fun observeBooks() {
        viewModelScope.launch {
            bookRepository.observeLibraries().collect { libraries ->
                _uiState.update { it.copy(bookLibraries = libraries) }
                refreshBooksRootIfShowing()
            }
        }
        viewModelScope.launch {
            bookRepository.observeAllBooks().collect { books ->
                _uiState.update { it.copy(bookSeries = books.seriesGroups()) }
                refreshBooksRootIfShowing()
            }
        }
        viewModelScope.launch {
            bookRepository.observeDefaultReader().collect { reader ->
                _uiState.update {
                    it.copy(
                        defaultReader = reader,
                        defaultReaderLabel = reader?.let { pkg -> bookIntentResolver.readerLabel(pkg) },
                    )
                }
                refreshBooksRootIfShowing()
            }
        }
    }

    private suspend fun refreshBooksRootIfShowing() {
        if (currentCategory()?.id == BuiltInCategory.LIBRARY &&
            _uiState.value.booksNav == BooksNav.Root
        ) {
            _uiState.update { it.copy(currentItems = booksRootItems()) }
        }
    }

    private fun booksAddActions(): List<XMBItem> = buildList {
        if (_uiState.value.bookLibraries.none { it.lastScannedAt != null }) {
            add(
                XMBItem(
                    id       = ADD_BOOK_FOLDER_ITEM_ID,
                    title    = "Add Book Folder",
                    subtitle = "Point the Library at a folder of EPUBs",
                    type     = XMBItemType.ADD_ACTION,
                )
            )
        }
        add(addBookAppsItem())
    }

    private suspend fun booksRootItems(): List<XMBItem> =
        libraryColumn(
            _uiState.value.booksRootSections() + bookAppItems() + collapseAddRows(booksAddActions()),
            SearchScope.BOOKS,
        )

    private suspend fun bookAppItems(): List<XMBItem> {
        val apps = appCategoryRepository.appsForCategory(LIBRARY_APPS_CATEGORY_ID)
            .notHiddenAt(HideLocationType.CATEGORY, LIBRARY_APPS_CATEGORY_ID)
        return apps.map { it.toXmbItem(gameRepository.getAppEntry(it.packageName)) }
    }

    private fun addBookAppsItem(): XMBItem = XMBItem(
        id       = ADD_LIBRARY_APPS_ITEM_ID,
        title    = "Add Book Apps",
        subtitle = "Pick installed apps to show here",
        type     = XMBItemType.ADD_ACTION,
    )

    private fun bookItems(books: List<com.psplauncher.core.domain.model.Book>): List<XMBItem> =
        books.map { book ->
            XMBItem(
                id       = "book_${book.id}",
                title    = book.displayTitle,
                subtitle = bookRowSubtitle(book.author, book.seriesName, book.seriesIndex),
                coverUri = book.coverUri,

                artworkUri = book.coverUri,
                type     = XMBItemType.LIBRARY_BOOK,
            )
        }

    private fun emptyBooksItem(): XMBItem = XMBItem(
        id       = "books_empty",
        title    = "No books yet",
        subtitle = "Add a folder of EPUBs in Settings, then rescan",
        type     = XMBItemType.EMPTY,
    )

    private fun bookShelfItems(): List<XMBItem> =
        _uiState.value.bookLibraries.map {
            XMBItem(
                id       = "shelf_${it.id}",
                title    = it.displayName,
                subtitle = countLabel(it.bookCount, "book", "books"),
                type     = XMBItemType.LIBRARY_FOLDER,
            )
        }

    private fun bookSeriesItems(): List<XMBItem> =
        _uiState.value.bookSeries.map { series ->
            XMBItem(
                id       = "series_${series.name}",
                title    = series.name,
                subtitle = countLabel(series.bookCount, "book", "books"),
                coverUri = series.coverUri,
                artworkUri = series.coverUri,
                type     = XMBItemType.LIBRARY_SERIES,
            )
        }

    private fun emptySeriesItem(): XMBItem = XMBItem(
        id       = "series_empty",
        title    = "No series yet",
        subtitle = "No scanned book declares one. Embed series metadata, then Deep Rescan.",
        type     = XMBItemType.EMPTY,
    )

    private fun handleBooksSelection(item: XMBItem): Boolean = when {
        item.id == SEARCH_ITEM_ID -> { openSearch(SearchScope.BOOKS); true }
        item.id == ADD_MENU_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openAddMenu(); true }
        item.id == OPEN_READER_ITEM_ID -> {
            menuSound.play(MenuSound.LAUNCH)
            val reader = _uiState.value.defaultReader
            val error = reader?.let { bookIntentResolver.launchReader(it) }
            if (error != null) {
                _uiState.update { it.copy(infoDialog = InfoDialogState(title = "Library", message = error)) }
            }
            true
        }
        item.id == BOOK_SHELVES_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openBooksView(BooksNav.Shelves); true }
        item.id == ALL_BOOKS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openBooksView(BooksNav.AllBooks); true }
        item.id == BOOK_SERIES_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openBooksView(BooksNav.SeriesList); true }
        item.id == ADD_BOOK_FOLDER_ITEM_ID -> {
            menuSound.play(MenuSound.SELECT)
            _uiState.update { it.copy(activeSettingsScreen = "settings_books") }
            true
        }
        item.type == XMBItemType.LIBRARY_SERIES -> {
            menuSound.play(MenuSound.SELECT)
            openBooksView(BooksNav.Series(item.title))
            true
        }
        item.type == XMBItemType.LIBRARY_FOLDER -> {
            menuSound.play(MenuSound.SELECT)
            openBooksView(BooksNav.Shelf(item.id.removePrefix("shelf_"), item.title))
            true
        }
        item.type == XMBItemType.LIBRARY_BOOK -> { openBook(item.id.removePrefix("book_")); true }
        item.id == ADD_LIBRARY_APPS_ITEM_ID -> {
            menuSound.play(MenuSound.SELECT)
            openAppPicker(AppPickerTarget.CategoryShortcuts(LIBRARY_APPS_CATEGORY_ID), "Add Book Apps")
            true
        }

        item.packageName != null -> {
            menuSound.play(MenuSound.LAUNCH)
            launchAppWithDisc(item.packageName, item.shelfCoverArt)
            true
        }
        else -> false
    }

    private fun openBook(bookId: String) {
        menuSound.play(MenuSound.LAUNCH)
        viewModelScope.launch {
            val book = bookRepository.getBook(bookId) ?: return@launch

            awaitDiscHandOff(book.coverUri)
            val error = bookIntentResolver.launch(book, _uiState.value.defaultReader)
            if (error != null) {
                _uiState.update { it.copy(infoDialog = InfoDialogState(title = book.displayTitle, message = error)) }
                return@launch
            }

            bookRepository.markBookOpened(bookId, System.currentTimeMillis())
        }
    }

    private fun booksNavKey(nav: BooksNav): String = when (nav) {
        BooksNav.Root     -> "root"
        BooksNav.AllBooks -> "all"
        BooksNav.Shelves  -> "shelves"
        is BooksNav.Shelf -> "shelf_${nav.id}"
        BooksNav.SeriesList -> "series"
        is BooksNav.Series  -> "series_${nav.name}"
    }

    private fun openBooksView(nav: BooksNav) = navigateRememberingCursor { it.copy(booksNav = nav) }

    private fun closeBooksView() = openBooksView(BooksNav.Root)

    private fun observePhoto() {
        viewModelScope.launch {
            photoRepository.observeLibraries().collect { libraries ->
                _uiState.update { it.copy(photoLibraries = libraries) }
                if (currentCategory()?.id == BuiltInCategory.PHOTO &&
                    _uiState.value.photoNav == PhotoNav.Root
                ) {
                    _uiState.update { it.copy(currentItems = photoRootItems()) }
                }
            }
        }
    }

    @Suppress("QueryPermissionsNeeded")
    private val cameraAvailable: Boolean by lazy {
        runCatching {
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .resolveActivity(context.packageManager) != null
        }.getOrDefault(false)
    }

    private fun launchCamera() {
        runCatching {
            context.startActivity(
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Timber.w(it, "Could not launch a camera app") }
    }

    private fun photoAddActions(): List<XMBItem> = buildList {
        if (_uiState.value.photoLibraries.none { it.lastScannedAt != null }) add(addPhotoLibraryItem())
        add(addPhotoAppsItem())
    }

    private suspend fun photoRootItems(): List<XMBItem> =
        libraryColumn(
            _uiState.value.photoRootSections(cameraAvailable) + photoAppItems() + collapseAddRows(photoAddActions()),
            SearchScope.PHOTOS,
        )

    private fun addPhotoLibraryItem(): XMBItem = XMBItem(
        id       = ADD_PHOTO_LIBRARY_ITEM_ID,
        title    = "Add Photo Library",
        subtitle = "Set your Photo root folder in Settings to get started",
        type     = XMBItemType.ADD_ACTION,
    )

    private suspend fun photoAppItems(): List<XMBItem> {
        val apps = appCategoryRepository.appsForCategory(PHOTO_APPS_CATEGORY_ID)
            .notHiddenAt(HideLocationType.CATEGORY, PHOTO_APPS_CATEGORY_ID)
        return apps.map { it.toXmbItem(gameRepository.getAppEntry(it.packageName)) }
    }

    private fun addPhotoAppsItem(): XMBItem = XMBItem(
        id       = ADD_PHOTO_APPS_ITEM_ID,
        title    = "Add Photo Apps",
        subtitle = "Pick installed apps to show here",
        type     = XMBItemType.ADD_ACTION,
    )

    private fun photoAlbumItems(libraries: List<com.psplauncher.core.domain.model.PhotoLibrary>): List<XMBItem> {
        val rows = libraries.map { lib ->
            XMBItem(
                id       = "plib_${lib.id}",
                title    = lib.displayName,
                subtitle = countLabel(lib.photoCount, "photo", "photos"),
                type     = XMBItemType.PHOTO_FOLDER,
            )
        }
        return rows.ifEmpty {
            listOf(
                XMBItem(
                    id = EMPTY_CATEGORY_ITEM_ID,
                    title = "No albums yet",
                    subtitle = "Set a root folder in Settings → Photo",
                    type = XMBItemType.EMPTY,
                ),
            )
        }
    }

    private fun List<com.psplauncher.core.domain.model.Photo>.toPhotoItems(): List<XMBItem> =
        map { photo ->
            XMBItem(
                id       = "pho_${photo.id}",
                title    = photo.displayName,
                subtitle = photoRowSubtitle(photo.displayDateMs, photo.resolutionLabel, photo.sizeBytes),
                type     = XMBItemType.PHOTO_FILE,
                mediaUri = photo.uri,
                mimeType = photo.mimeType,
                coverUri = photo.thumbnailUri,
            )
        }

    private fun setPhotoItems(
        photos: List<com.psplauncher.core.domain.model.Photo>,
        emptyItem: XMBItem,
    ) {
        val items = if (photos.isEmpty()) listOf(emptyItem) else photos.toPhotoItems()
        _uiState.update { it.copy(currentItems = items) }
    }

    private fun emptyAllPhotosItem(): XMBItem = XMBItem(
        id       = EMPTY_CATEGORY_ITEM_ID,
        title    = "No photos found",
        subtitle = "Add a photo library and scan it",
        type     = XMBItemType.EMPTY,
    )

    private fun emptyLibraryPhotosItem(): XMBItem = XMBItem(
        id       = EMPTY_CATEGORY_ITEM_ID,
        title    = "No photos in this album",
        subtitle = "Scan it from its ⚙ Options menu or in Settings → Photo",
        type     = XMBItemType.EMPTY,
    )

    private fun handlePhotoSelection(item: XMBItem): Boolean = when {
        item.id == SEARCH_ITEM_ID -> { openSearch(SearchScope.PHOTOS); true }
        item.id == ADD_MENU_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openAddMenu(); true }
        item.id == ALL_PHOTOS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openPhotoView(PhotoNav.AllPhotos); true }
        item.id == PHOTO_ALBUMS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openPhotoView(PhotoNav.Albums); true }
        item.id == CAMERA_ITEM_ID -> { menuSound.play(MenuSound.LAUNCH); launchCamera(); true }
        item.id == ADD_PHOTO_LIBRARY_ITEM_ID -> {
            menuSound.play(MenuSound.SELECT)
            _uiState.update { it.copy(activeSettingsScreen = "settings_photo") }
            true
        }
        item.id == ADD_PHOTO_APPS_ITEM_ID -> {
            menuSound.play(MenuSound.SELECT)
            openAppPicker(AppPickerTarget.CategoryShortcuts(PHOTO_APPS_CATEGORY_ID), "Add Photo Apps")
            true
        }

        item.packageName != null -> {
            menuSound.play(MenuSound.LAUNCH)
            launchAppWithDisc(item.packageName, item.shelfCoverArt)
            true
        }
        item.type == XMBItemType.PHOTO_FOLDER && item.id.startsWith("plib_") -> {
            menuSound.play(MenuSound.SELECT)
            openPhotoView(PhotoNav.Library(item.id.removePrefix("plib_"), item.title))
            true
        }
        item.type == XMBItemType.PHOTO_FILE && item.id.startsWith("pho_") -> {
            menuSound.play(MenuSound.SELECT)
            openPhotoViewer(item.id.removePrefix("pho_"))
            true
        }
        else -> false
    }

    private fun photoNavKey(nav: PhotoNav): String = when (nav) {
        PhotoNav.Root       -> "root"
        PhotoNav.AllPhotos  -> "all"
        PhotoNav.Albums     -> "albums"
        is PhotoNav.Library -> "library_${nav.id}"
    }

    private fun openPhotoView(nav: PhotoNav) = navigateRememberingCursor { it.copy(photoNav = nav) }

    private fun closePhotoView() = openPhotoView(PhotoNav.Root)

    private fun openPhotoViewer(photoId: String, wallpaperPreview: Boolean = false) {
        val libraryId = (_uiState.value.photoNav as? PhotoNav.Library)?.id
        _uiState.update {
            it.copy(activePhotoViewer = PhotoViewerRequest(photoId, libraryId, openWallpaperPreview = wallpaperPreview))
        }
    }

    fun onClosePhotoViewer() {
        _uiState.update { it.copy(activePhotoViewer = null, pendingPhotoViewerAction = null) }
    }

    fun consumePhotoViewerAction() {
        _uiState.update { it.copy(pendingPhotoViewerAction = null) }
    }

    private fun openBookContextMenu(item: XMBItem): Boolean {
        if (item.menuHostCategory(currentCategory()?.id) != BuiltInCategory.LIBRARY) return false
        if (item.type != XMBItemType.LIBRARY_BOOK || !item.id.startsWith("book_")) return false
        val bookId = item.id.removePrefix("book_")
        viewModelScope.launch {
            val onShelf = runCatching { bookRepository.getBook(bookId) }
                .getOrNull()?.lastOpenedAt != null
            val items = bookContextMenuItems(hasOpenStamp = onShelf)
            _uiState.update {
                it.copy(activeContextMenu = XMBContextMenu(state = MenuState(title = item.title, rows = items), bookFileId = bookId))
            }
        }
        return true
    }

    private fun handleBookAction(bookId: String, itemId: String) {
        when (itemId) {
            "book_open" -> openBook(bookId)

            "book_remove_recent" -> appAction { bookRepository.clearBookLastOpened(bookId) }
            "book_remove" -> appAction { bookRepository.removeBook(bookId) }
        }
    }

    private fun openPhotoContextMenu(item: XMBItem): Boolean {
        if (item.menuHostCategory(currentCategory()?.id) != BuiltInCategory.PHOTO) return false
        return when {
            item.type == XMBItemType.PHOTO_FILE && item.id.startsWith("pho_") -> {
                openPhotoFileContextMenu(item.id.removePrefix("pho_"), item.title); true
            }
            item.type == XMBItemType.PHOTO_FOLDER && item.id.startsWith("plib_") -> {
                openPhotoLibraryContextMenu(item.id.removePrefix("plib_"), item.title); true
            }
            item.packageName != null -> {
                openAppContextMenu(item, categoryIdOverride = PHOTO_APPS_CATEGORY_ID); true
            }
            else -> false
        }
    }

    private fun openPhotoFileContextMenu(photoId: String, title: String) {
        val items = photoFileContextMenuItems()
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(state = MenuState(title = title, rows = items), photoFileId = photoId)) }
    }

    private fun handlePhotoFileAction(photoId: String, itemId: String) {
        when (itemId) {
            "photo_open"          -> openPhotoViewer(photoId)

            "photo_set_wallpaper" -> openPhotoViewer(photoId, wallpaperPreview = true)
            "photo_remove"        -> appAction { photoRepository.removePhoto(photoId) }
        }
    }

    private fun openPhotoLibraryContextMenu(libraryId: String, name: String) {
        val items = photoLibraryContextMenuItems()
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(state = MenuState(title = name, rows = items), photoLibraryId = libraryId)) }
    }

    private fun handlePhotoLibraryAction(libraryId: String, itemId: String) {
        when (itemId) {
            "photo_lib_open" -> {
                val name = _uiState.value.photoLibraries.firstOrNull { it.id == libraryId }?.displayName.orEmpty()
                openPhotoView(PhotoNav.Library(libraryId, name))
            }
            "photo_lib_scan" -> scanPhotoLibrary(libraryId)
            "photo_lib_manage" -> _uiState.update { it.copy(activeSettingsScreen = "settings_photo") }
        }
    }

    private fun scanPhotoLibrary(libraryId: String) {
        viewModelScope.launch {
            val library = photoRepository.getLibrary(libraryId) ?: return@launch
            val taskId = "photo_scan_${library.id}"
            val notifier = BackgroundTaskNotifier(context)
            notifier.running(taskId, "Scanning ${library.displayName}", null)
            val existing = photoRepository.getPhotosForLibrary(library.id)
            photoScanner.scan(library, deep = false, existing = existing).collect { result ->
                when (result) {
                    is com.psplauncher.feature.library.scanner.PhotoScanResult.Progress ->
                        notifier.running(taskId, "Scanning ${result.libraryName}", null)
                    is com.psplauncher.feature.library.scanner.PhotoScanResult.Complete -> {
                        photoRepository.replacePhotosForLibrary(result.libraryId, result.photos, System.currentTimeMillis())
                        notifier.complete(taskId, "Scanned ${library.displayName}", "${result.photos.size} photos")
                    }
                    is com.psplauncher.feature.library.scanner.PhotoScanResult.Error ->
                        notifier.failed(taskId, "Scan failed: ${library.displayName}", result.message)
                }
            }
        }
    }

    private fun openMusicBrowser(view: MusicBrowserView) {
        musicBrowserJob?.cancel()
        val title = when (view) {
            MusicBrowserView.AllMusic    -> "Songs"
            MusicBrowserView.Playlists   -> "Playlists"
            MusicBrowserView.Artists     -> "Artists"
            MusicBrowserView.Albums      -> "Albums"
            is MusicBrowserView.Playlist -> view.name
            is MusicBrowserView.Artist   -> view.name
            is MusicBrowserView.Album    -> view.name
        }
        _uiState.update { it.copy(musicBrowser = MusicBrowserState(view = view, title = title)) }
        musicBrowserJob = viewModelScope.launch {
            when (view) {
                MusicBrowserView.AllMusic -> musicRepository.observeAllTracks().collect { tracks ->
                    browserRawTracks = tracks; rebuildBrowserTrackRows()
                }
                is MusicBrowserView.Playlist -> musicRepository.observePlaylistTracks(view.id).collect { tracks ->
                    browserRawTracks = tracks; rebuildBrowserTrackRows()
                }
                MusicBrowserView.Playlists -> musicRepository.observePlaylists().collect { playlists ->
                    browserRawPlaylists = playlists; rebuildBrowserPlaylistRows()
                }

                MusicBrowserView.Artists, MusicBrowserView.Albums ->
                    musicRepository.observeAllTracks().collect { tracks ->
                        browserRawTracks = tracks; rebuildBrowserGroupRows()
                    }
                is MusicBrowserView.Artist -> musicRepository.observeAllTracks().collect { tracks ->

                    browserRawTracks = tracks.tracksByArtistKey(view.key)
                    rebuildBrowserTrackRows()
                }
                is MusicBrowserView.Album -> musicRepository.observeAllTracks().collect { tracks ->
                    browserRawTracks = tracks.filter { it.album.musicGroupKey() == view.key }
                    rebuildBrowserTrackRows()
                }
            }
        }
    }

    private fun MusicTrack.matchesQuery(q: String): Boolean =
        displayTitle.lowercase().contains(q) ||
            artist?.lowercase()?.contains(q) == true ||
            album?.lowercase()?.contains(q) == true

    private fun rebuildBrowserTrackRows() {
        val state = _uiState.value.musicBrowser ?: return
        val isPlaylist = state.view is MusicBrowserView.Playlist
        val q = state.query.trim().lowercase()
        val sorted = browserRawTracks.trackSorted(_uiState.value.musicSortMode)
        val filtered = if (q.isBlank()) sorted else sorted.filter { it.matchesQuery(q) }
        currentMusicTracks = filtered
        val baseRows = when {
            filtered.isNotEmpty() -> filtered.toMusicItems()
            q.isNotBlank()        -> listOf(browserNoResultsItem())
            isPlaylist            -> listOf(emptyPlaylistItem())
            else                  -> listOf(emptyAllMusicItem())
        }
        val rows = if (isPlaylist) baseRows + addTracksItem() else baseRows

        val label = _uiState.value.musicSortMode.label
        _uiState.update { it.copy(musicBrowser = it.musicBrowser?.copy(
            rows = rows,
            selectedIndex = state.selectedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0)),
            sortLabel = label,
        )) }
    }

    private fun rebuildBrowserGroupRows() {
        val state = _uiState.value.musicBrowser ?: return
        val q = state.query.trim().lowercase()
        val prefix = if (state.view == MusicBrowserView.Artists) "art" else "alb"
        val groups = if (state.view == MusicBrowserView.Artists) browserRawTracks.artistGroups()
                     else browserRawTracks.albumGroups()
        val filtered = if (q.isBlank()) groups else groups.filter { it.name.lowercase().contains(q) }
        val rows = when {
            filtered.isNotEmpty() -> filtered.map { it.toBrowserItem(prefix) }
            q.isNotBlank()        -> listOf(browserNoResultsItem())
            else                  -> listOf(emptyAllMusicItem())
        }
        _uiState.update { it.copy(musicBrowser = it.musicBrowser?.copy(
            rows = rows,
            selectedIndex = state.selectedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0)),
            sortLabel = null,
        )) }
    }

    private fun MusicGroup.toBrowserItem(prefix: String): XMBItem = XMBItem(
        id            = "mg_${prefix}_$key",
        title         = name,
        subtitle      = subtitle,
        coverUri      = artUri,
        musicGroupKey = key,
        type          = XMBItemType.MUSIC_GROUP,
    )

    private fun rebuildBrowserPlaylistRows() {
        val state = _uiState.value.musicBrowser ?: return
        val q = state.query.trim().lowercase()
        val filtered = if (q.isBlank()) browserRawPlaylists
                       else browserRawPlaylists.filter { it.name.lowercase().contains(q) }
        val rows = playlistRootItems(filtered)
        _uiState.update { it.copy(musicBrowser = it.musicBrowser?.copy(
            rows = rows,
            selectedIndex = state.selectedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0)),
            sortLabel = null,
        )) }
    }

    private fun browserNoResultsItem(): XMBItem = XMBItem(
        id = EMPTY_CATEGORY_ITEM_ID, title = "No matches", subtitle = "Try a different search.",
        type = XMBItemType.EMPTY,
    )

    fun onMusicBrowserQueryChange(query: String) {
        markTouchInput()
        val state = _uiState.value.musicBrowser ?: return
        _uiState.update { it.copy(musicBrowser = it.musicBrowser?.copy(
            query = query, selectedIndex = 0,
            scrollToTopToken = state.scrollToTopToken + 1,
        )) }
        when {
            state.view is MusicBrowserView.Playlists -> rebuildBrowserPlaylistRows()
            state.view.listsGroups -> rebuildBrowserGroupRows()
            else -> rebuildBrowserTrackRows()
        }
    }

    private var searchGames: List<com.psplauncher.core.domain.model.Game> = emptyList()

    private var searchApps: List<com.psplauncher.feature.appbar.InstalledApp> = emptyList()
    private var searchVideos: List<com.psplauncher.core.domain.model.Video> = emptyList()
    private var searchPhotos: List<com.psplauncher.core.domain.model.Photo> = emptyList()
    private var searchBooks: List<com.psplauncher.core.domain.model.Book> = emptyList()
    private var searchTracks: List<com.psplauncher.core.domain.model.MusicTrack> = emptyList()

    fun enterOpensAppDrawer(): Boolean = _uiState.value.enterOpensAppDrawer

    fun onTypedCharacter(ch: String): Boolean {
        if (_uiState.value.activeAppDrawerFilter != null) {
            _uiState.update { it.copy(pendingDrawerTypedChar = ch) }
            return true
        }
        if (!typeToSearchAllowed()) return false
        openSearchTyping(ch)
        return true
    }

    fun onDrawerTypedCharConsumed() {
        _uiState.update { it.copy(pendingDrawerTypedChar = null) }
    }

    fun typeToSearchAllowed(): Boolean {
        val state = _uiState.value
        return state.search == null && state.stripShowsXmbContext
    }

    fun openSearchTyping(query: String) {
        openSearch(SearchScope.ALL)
        onSearchQueryChange(query)
    }

    fun openSearch(scope: SearchScope) {
        menuSound.play(MenuSound.SELECT)
        _uiState.update { it.copy(search = SearchState(scope = scope)) }
        viewModelScope.launch {
            val wantsGames = scope == SearchScope.ALL || scope == SearchScope.GAMES
            val wantsVideos = scope == SearchScope.ALL || scope == SearchScope.VIDEOS
            val wantsPhotos = scope == SearchScope.ALL || scope == SearchScope.PHOTOS
            val wantsBooks = scope == SearchScope.ALL || scope == SearchScope.BOOKS
            searchGames = if (wantsGames) gameRepository.observeAllGames().first() else emptyList()
            searchVideos = if (wantsVideos) videoRepository.observeAllVideos().first() else emptyList()
            searchPhotos = if (wantsPhotos) photoRepository.observeAllPhotos().first() else emptyList()
            searchBooks = if (wantsBooks) bookRepository.observeAllBooks().first() else emptyList()

            val wantsTracks = scope == SearchScope.ALL || scope == SearchScope.MUSIC
            searchTracks = if (wantsTracks) musicRepository.observeAllTracks().first() else emptyList()

            searchApps = if (scope == SearchScope.ALL) appCategoryRepository.allInstalledApps() else emptyList()
            _uiState.update { it.copy(search = it.search?.copy(loaded = true)) }
            rebuildSearchRows()
        }
    }

    fun onSearchQueryChange(query: String) {
        markTouchInput()
        val state = _uiState.value.search ?: return
        _uiState.update { it.copy(search = it.search?.copy(
            query = query,
            selectedIndex = 0,
            scrollToTopToken = state.scrollToTopToken + 1,
        )) }
        rebuildSearchRows()
    }

    fun closeSearch() {
        menuSound.play(MenuSound.BACK)

        searchGames = emptyList(); searchVideos = emptyList(); searchPhotos = emptyList()
        searchApps = emptyList()
        searchBooks = emptyList(); searchTracks = emptyList()
        _uiState.update { it.copy(search = null) }
    }

    private fun rebuildSearchRows() {
        val state = _uiState.value.search ?: return
        val q = state.query
        val rows = buildList {
            searchApps.filter { matchesSearch(q, it.label, it.packageName) }
                .take(SEARCH_RESULTS_PER_LIBRARY)
                .forEach { app ->
                    add(
                        XMBItem(
                            id = "searchapp_${app.packageName}",
                            title = app.label,
                            subtitle = "App",
                            packageName = app.packageName,

                            isAndroidApp = true,
                        ),
                    )
                }

            searchGames.filter {
                matchesSearch(q, it.title, it.developer, it.publisher, platformCache[it.platformId]?.name)
            }
                .take(SEARCH_RESULTS_PER_LIBRARY)
                .forEach { add(it.toSearchRow()) }
            searchVideos.filter { matchesSearch(q, it.displayTitle, it.displayName) }
                .take(SEARCH_RESULTS_PER_LIBRARY)
                .forEach { add(it.toSearchRow()) }
            searchPhotos.filter { matchesSearch(q, it.displayName, it.relativePath) }
                .take(SEARCH_RESULTS_PER_LIBRARY)
                .forEach { add(it.toSearchRow()) }
            searchBooks.filter { matchesSearch(q, it.displayTitle, it.author, it.seriesName) }
                .take(SEARCH_RESULTS_PER_LIBRARY)
                .forEach { add(it.toSearchRow()) }
            searchTracks.filter { matchesSearch(q, it.displayTitle, it.artist, it.album) }
                .take(SEARCH_RESULTS_PER_LIBRARY)
                .forEach { add(it.toSearchRow()) }
        }

        val anyContent = searchGames.isNotEmpty() || searchVideos.isNotEmpty() ||
            searchPhotos.isNotEmpty() || searchBooks.isNotEmpty() || searchTracks.isNotEmpty() ||
            searchApps.isNotEmpty()
        val display = when {
            rows.isNotEmpty() -> rows
            else -> when (searchEmptyState(state.loaded, q, anyContent)) {
                SearchEmptyState.LOADING -> searchNoticeItem("Reading your libraries", "One moment.")
                SearchEmptyState.EMPTY_LIBRARY -> searchNoticeItem(state.scope.emptyTitle, state.scope.emptyHint)
                SearchEmptyState.PROMPT -> searchNoticeItem("Type to search", state.scope.hint)
                SearchEmptyState.NO_MATCHES -> searchNoticeItem("No matches", "Nothing here matches that.")
            }.let(::listOf)
        }
        _uiState.update { it.copy(search = it.search?.copy(
            rows = display,
            selectedIndex = state.selectedIndex.coerceIn(0, (display.size - 1).coerceAtLeast(0)),
        )) }
    }

    private fun searchNoticeItem(title: String, subtitle: String): XMBItem =
        XMBItem(id = EMPTY_CATEGORY_ITEM_ID, title = title, subtitle = subtitle, type = XMBItemType.EMPTY)

    private fun searchColumns(): Int =
        (_uiState.value.search?.columns ?: SEARCH_GRID_COLUMNS).coerceAtLeast(1)

    fun onSearchColumnsMeasured(columns: Int) {
        if (columns <= 0) return
        val current = _uiState.value.search ?: return
        if (current.columns == columns) return
        _uiState.update { it.copy(search = it.search?.copy(columns = columns)) }
    }

    private fun moveSearch(delta: Int) {
        val state = _uiState.value.search ?: return
        if (state.rows.isEmpty()) return
        val next = (state.selectedIndex + delta).coerceIn(0, state.rows.lastIndex)
        if (next == state.selectedIndex) return
        menuSound.play(MenuSound.SCROLL)
        _uiState.update { it.copy(search = it.search?.copy(selectedIndex = next)) }
    }

    fun onSearchActivatedAt(index: Int) {
        val state = _uiState.value.search ?: return
        val row = state.rows.getOrNull(index) ?: return
        if (row.type == XMBItemType.EMPTY) return
        _uiState.update { it.copy(search = it.search?.copy(selectedIndex = index)) }

        val appPackage = row.packageName?.takeIf { row.isInstalledApp }
        if (appPackage != null) {
            closeSearch()
            launchAppWithDisc(appPackage, row.shelfCoverArt)
            return
        }

        val categoryId = searchRowCategory(row) ?: return
        closeSearch()
        selectCategoryById(categoryId)
        when (row.type) {
            XMBItemType.VIDEO_FILE ->
                _uiState.update { it.copy(activeVideoId = row.id.removePrefix("vid_"), activeVideoAutoPlay = true) }
            XMBItemType.PHOTO_FILE -> openSearchedPhoto(row)
            XMBItemType.LIBRARY_BOOK -> openBook(row.id.removePrefix("book_"))
            XMBItemType.MUSIC_TRACK -> openSearchedTrack(row)

            else -> row.gameId?.let { id -> launchGameDirectly(id) }
        }
    }

    private fun searchRowCategory(row: XMBItem): String? = row.owningCategory()

    private fun selectCategoryById(categoryId: String) {
        val index = _uiState.value.categories.indexOfFirst { it.id == categoryId }
        if (index >= 0) onCategorySelected(index)
    }

    private fun openSearchedPhoto(row: XMBItem) {
        val photoId = row.id.removePrefix("pho_")
        val libraryId = searchPhotos.firstOrNull { it.id == photoId }?.libraryId ?: return
        val name = _uiState.value.photoLibraries.firstOrNull { it.id == libraryId }?.displayName.orEmpty()
        _uiState.update { it.copy(photoNav = PhotoNav.Library(libraryId, name)) }
        openPhotoViewer(photoId)
    }

    private fun openSearchedTrack(row: XMBItem) {
        val trackId = row.id.removePrefix("mt_")
        val track = searchTracks.firstOrNull { it.id == trackId } ?: return
        viewModelScope.launch {
            awaitDiscHandOff(track.artUri)
            musicPlayer.setQueue(listOf(track), 0)
            _uiState.update { it.copy(musicPlayerVisible = true) }
        }
    }

    private fun com.psplauncher.core.domain.model.Game.toSearchRow(): XMBItem = XMBItem(
        id = "search_game_$id",
        title = title,
        subtitle = listOfNotNull("Game", platformCache[platformId]?.name).joinToString("  ·  "),

        boxArtUri = boxArtUri,
        coverUri = artworkUri,
        gameId = id,
        platformId = platformId,
        type = XMBItemType.STANDARD,
    )

    private fun com.psplauncher.core.domain.model.Video.toSearchRow(): XMBItem = XMBItem(
        id = "vid_$id",
        title = displayTitle,
        subtitle = listOfNotNull("Video", videoRowSubtitle(durationMs, resolutionLabel, sizeBytes)).joinToString("  ·  "),
        coverUri = effectiveThumbnailUri,
        mediaUri = uri,
        mimeType = mimeType,
        type = XMBItemType.VIDEO_FILE,
    )

    private fun com.psplauncher.core.domain.model.Photo.toSearchRow(): XMBItem = XMBItem(
        id = "pho_$id",
        title = displayName,
        subtitle = listOfNotNull("Photo", relativePath).joinToString("  ·  "),
        coverUri = thumbnailUri ?: uri,
        mediaUri = uri,
        type = XMBItemType.PHOTO_FILE,
    )

    private fun com.psplauncher.core.domain.model.Book.toSearchRow(): XMBItem = XMBItem(
        id = "book_$id",
        title = displayTitle,
        subtitle = listOfNotNull("Book", bookRowSubtitle(author, seriesName, seriesIndex)).joinToString("  ·  "),
        coverUri = coverUri,
        type = XMBItemType.LIBRARY_BOOK,
    )

    private fun com.psplauncher.core.domain.model.MusicTrack.toSearchRow(): XMBItem = XMBItem(
        id = "mt_$id",
        title = displayTitle,
        subtitle = listOfNotNull("Music", musicRowSubtitle(artist, album, durationMs)).joinToString("  ·  "),
        coverUri = artUri,
        mediaUri = uri,
        mimeType = mimeType,
        type = XMBItemType.MUSIC_TRACK,
    )

    private fun moveMusicBrowser(delta: Int) {
        val b = _uiState.value.musicBrowser ?: return
        val next = (b.selectedIndex + delta).coerceIn(0, (b.rows.size - 1).coerceAtLeast(0))
        if (next != b.selectedIndex) {
            _uiState.update { it.copy(musicBrowser = b.copy(selectedIndex = next)) }
            menuSound.play(MenuSound.SCROLL)
        }
    }

    private fun activateMusicBrowser() {
        val b = _uiState.value.musicBrowser ?: return
        handleMusicBrowserRow(b.rows.getOrNull(b.selectedIndex) ?: return)
    }

    fun onMusicBrowserActivatedAt(index: Int) {
        markTouchInput()
        _uiState.update { it.copy(musicBrowser = it.musicBrowser?.copy(selectedIndex = index)) }
        activateMusicBrowser()
    }

    private fun handleMusicBrowserRow(item: XMBItem) {
        when {
            item.type == XMBItemType.EMPTY -> Unit
            item.id == CREATE_PLAYLIST_ITEM_ID -> { menuSound.play(MenuSound.SELECT); promptCreatePlaylist() }
            item.id == ADD_TRACKS_ITEM_ID -> {
                menuSound.play(MenuSound.SELECT)
                (_uiState.value.musicBrowser?.view as? MusicBrowserView.Playlist)?.let { openMusicTrackPicker(it.id) }
            }
            item.type == XMBItemType.PLAYLIST && item.playlistId != null -> {
                menuSound.play(MenuSound.SELECT)
                openMusicBrowser(MusicBrowserView.Playlist(item.playlistId, item.title))
            }
            item.type == XMBItemType.MUSIC_GROUP && item.musicGroupKey != null -> {
                menuSound.play(MenuSound.SELECT)
                openMusicBrowser(
                    if (_uiState.value.musicBrowser?.view == MusicBrowserView.Artists)
                        MusicBrowserView.Artist(item.title, item.musicGroupKey)
                    else MusicBrowserView.Album(item.title, item.musicGroupKey)
                )
            }
            item.type == XMBItemType.MUSIC_TRACK -> { menuSound.play(MenuSound.SELECT); openMusicPlayerForItem(item) }
        }
    }

    private fun openMusicBrowserContextMenu() {
        val b = _uiState.value.musicBrowser ?: return
        val item = b.rows.getOrNull(b.selectedIndex) ?: return
        when {
            item.type == XMBItemType.MUSIC_TRACK -> openMusicTrackContextMenu(item)
            item.type == XMBItemType.PLAYLIST && item.playlistId != null ->
                openPlaylistRowContextMenu(item.playlistId, item.title)
        }
    }

    fun onMusicBrowserLongPressAt(index: Int) {
        markTouchInput()
        _uiState.update { it.copy(musicBrowser = it.musicBrowser?.copy(selectedIndex = index)) }
        openMusicBrowserContextMenu()
    }

    fun onMusicBrowserBack() {
        markTouchInput()
        val b = _uiState.value.musicBrowser ?: return
        menuSound.play(MenuSound.BACK)
        when (b.view) {
            is MusicBrowserView.Playlist -> openMusicBrowser(MusicBrowserView.Playlists)
            is MusicBrowserView.Artist -> openMusicBrowser(MusicBrowserView.Artists)
            is MusicBrowserView.Album -> openMusicBrowser(MusicBrowserView.Albums)
            else -> closeMusicBrowser()
        }
    }

    private fun closeMusicBrowser() {
        musicBrowserJob?.cancel(); musicBrowserJob = null
        val view = _uiState.value.musicBrowser?.view
        browserRawTracks = emptyList(); browserRawPlaylists = emptyList()
        _uiState.update { it.copy(musicBrowser = null) }

        if (currentCategory()?.id == BuiltInCategory.MUSIC && _uiState.value.musicNav == MusicNav.Root) {
            val targetId = when (view) {
                is MusicBrowserView.Playlists, is MusicBrowserView.Playlist -> PLAYLISTS_ITEM_ID
                is MusicBrowserView.Artists, is MusicBrowserView.Artist -> MUSIC_ARTISTS_ITEM_ID
                is MusicBrowserView.Albums, is MusicBrowserView.Album -> MUSIC_ALBUMS_ITEM_ID
                else -> ALL_MUSIC_ITEM_ID
            }
            val idx = _uiState.value.currentItems.indexOfFirst { it.id == targetId }
            if (idx >= 0) _uiState.update { it.copy(selectedItemIndex = idx) }
        }
    }

    fun onMusicBrowserSortTapped() {
        markTouchInput()
        cycleSort()
    }

    fun onMusicBrowserOptionsTapped() {
        markTouchInput()
        openMusicBrowserContextMenu()
    }

    private fun currentPlaylistContextId(): Long? =
        (_uiState.value.musicBrowser?.view as? MusicBrowserView.Playlist)?.id
            ?: (_uiState.value.musicNav as? MusicNav.Playlist)?.id

    private fun handleMusicSelection(item: XMBItem): Boolean = when {
        item.id == SEARCH_ITEM_ID -> { openSearch(SearchScope.MUSIC); true }
        item.id == ADD_MENU_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openAddMenu(); true }
        item.type == XMBItemType.EMPTY -> true
        item.id == NOW_PLAYING_ITEM_ID -> {
            menuSound.play(MenuSound.SELECT)
            if (_uiState.value.musicPlayback.track != null) _uiState.update { it.copy(musicPlayerVisible = true) }
            true
        }

        item.id == PLAYLISTS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openMusicBrowser(MusicBrowserView.Playlists); true }
        item.id == ALL_MUSIC_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openMusicBrowser(MusicBrowserView.AllMusic); true }
        item.id == MUSIC_ARTISTS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openMusicBrowser(MusicBrowserView.Artists); true }
        item.id == MUSIC_ALBUMS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openMusicBrowser(MusicBrowserView.Albums); true }
        item.id == ADD_MUSIC_FOLDER_ITEM_ID -> {
            menuSound.play(MenuSound.SELECT)
            _uiState.update { it.copy(activeSettingsScreen = "settings_music") }
            true
        }
        item.id == CREATE_PLAYLIST_ITEM_ID -> { menuSound.play(MenuSound.SELECT); promptCreatePlaylist(); true }
        item.id == ADD_MUSIC_APPS_ITEM_ID -> {
            menuSound.play(MenuSound.SELECT)
            openAppPicker(AppPickerTarget.CategoryShortcuts(MUSIC_APPS_CATEGORY_ID), "Add Music Apps")
            true
        }
        item.id == ADD_TRACKS_ITEM_ID -> {
            menuSound.play(MenuSound.SELECT)
            (_uiState.value.musicNav as? MusicNav.Playlist)?.let { openMusicTrackPicker(it.id) }
            true
        }
        item.type == XMBItemType.MUSIC_TRACK -> { menuSound.play(MenuSound.SELECT); openMusicPlayerForItem(item); true }
        item.type == XMBItemType.PLAYLIST && item.playlistId != null -> {
            menuSound.play(MenuSound.SELECT); openMusicView(MusicNav.Playlist(item.playlistId, item.title)); true
        }

        item.packageName != null -> {
            menuSound.play(MenuSound.LAUNCH)
            launchAppWithDisc(item.packageName, item.shelfCoverArt)
            true
        }
        else -> false
    }

    private fun openMusicPlayerForItem(item: XMBItem) {
        val trackId = item.id.removePrefix("mt_")
        val startIndex = currentMusicTracks.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
        if (currentMusicTracks.isEmpty()) return
        val track = currentMusicTracks[startIndex]
        viewModelScope.launch {
            awaitDiscHandOff(track.artUri)
            musicPlayer.setQueue(currentMusicTracks, startIndex)
            _uiState.update { it.copy(musicPlayerVisible = true) }
        }
    }

    fun musicPlayPause() = musicPlayer.playPause()
    fun musicNext() = musicPlayer.next()
    fun musicPrev() = musicPlayer.prev()
    fun musicSeekTo(ms: Int) = musicPlayer.seekTo(ms)
    private fun musicSeekBy(deltaMs: Int) = musicPlayer.seekBy(deltaMs)

    fun closeMusicPlayer() {
        _uiState.update { it.copy(musicPlayerVisible = false) }
    }

    private fun stopAndCloseMusicPlayer() {
        musicPlayer.stop()
        _uiState.update { it.copy(musicPlayerVisible = false) }
    }

    private fun openMusicPlayerOptions() {
        val title = musicPlayer.currentTrack()?.displayTitle ?: "Now Playing"
        _uiState.update {
            it.copy(
                activeContextMenu = XMBContextMenu(state = MenuState(title = title, rows = listOf(
                        XMBContextMenuItem("music_background", "Play in Background"),
                        XMBContextMenuItem("music_close", "Stop & Close"),
                    )), musicTrackId = MUSIC_PLAYER_MENU_MARKER)
            )
        }
    }

    private fun openNowPlayingContextMenu() {
        val playback = _uiState.value.musicPlayback
        if (playback.track == null) return
        _uiState.update {
            it.copy(
                activeContextMenu = XMBContextMenu(state = MenuState(title = playback.track.displayTitle, rows = nowPlayingContextMenuItems(playback.isPlaying)), musicTrackId = MUSIC_PLAYER_MENU_MARKER)
            )
        }
    }

    private fun musicPlayInBackground() {
        if (musicPlayer.currentTrack() == null) return
        com.psplauncher.feature.xmb.music.MusicPlaybackService.start(context)
        _uiState.update { it.copy(musicPlayerVisible = false) }
    }

    private fun openMusicTrackContextMenu(item: XMBItem) {
        val playlistId = currentPlaylistContextId()

        val trackId = item.id.removePrefix("mt_")
        viewModelScope.launch {
            val onShelf = runCatching { musicRepository.getTrack(trackId) }
                .getOrNull()?.lastPlayedAt != null
            val items = musicTrackContextMenuItems(playlistId = playlistId, hasPlayStamp = onShelf)
            _uiState.update { it.copy(
                activeContextMenu = XMBContextMenu(state = MenuState(title = item.title, rows = items), musicTrackId = trackId, playlistId = playlistId)
            )}
        }
    }

    private fun openPlaylistRowContextMenu(playlistId: Long, name: String) {
        val items = playlistRowContextMenuItems()
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(state = MenuState(title = name, rows = items), playlistId = playlistId)) }
    }

    private fun openPlaylistPicker(trackId: String, selectIndex: Int? = 0) {
        viewModelScope.launch {
            val playlists = musicRepository.observePlaylists().first()
            val memberOf = musicRepository.getPlaylistIdsForTrack(trackId).toSet()
            val items = buildList {
                playlists.forEach { pl ->
                    add(XMBContextMenuItem("pl_${pl.id}", pl.name, checked = pl.id in memberOf))
                }
                add(XMBContextMenuItem("pl_new", "Create New Playlist"))
            }
            _uiState.update { it.copy(
                activeContextMenu = XMBContextMenu(state = MenuState(title = "Add to Playlist", rows = items, selectedIndex = selectIndex?.coerceIn(0, items.lastIndex.coerceAtLeast(0))), playlistPickerTrackId = trackId)
            )}
        }
    }

    private fun openMusicContextMenu(item: XMBItem): Boolean {
        if (item.menuHostCategory(currentCategory()?.id) != BuiltInCategory.MUSIC) return false
        return when {
            item.id == NOW_PLAYING_ITEM_ID -> { openNowPlayingContextMenu(); true }
            item.type == XMBItemType.MUSIC_TRACK -> { openMusicTrackContextMenu(item); true }
            item.type == XMBItemType.PLAYLIST && item.playlistId != null -> {
                openPlaylistRowContextMenu(item.playlistId, item.title); true
            }
            item.packageName != null -> {
                openAppContextMenu(item, categoryIdOverride = MUSIC_APPS_CATEGORY_ID); true
            }
            else -> false
        }
    }

    private fun promptCreatePlaylist(forTrackId: String? = null) {
        _uiState.update { it.copy(
            playlistNameDialog = PlaylistNameDialogState(title = "New Playlist", forTrackId = forTrackId)
        )}
    }

    private fun promptRenamePlaylist(playlistId: Long) {
        val name = _uiState.value.currentItems.firstOrNull { it.playlistId == playlistId }?.title.orEmpty()
        _uiState.update { it.copy(
            playlistNameDialog = PlaylistNameDialogState(
                title = "Rename Playlist",
                initialText = name,
                renamePlaylistId = playlistId,
            )
        )}
    }

    fun onConfirmPlaylistName(name: String) {
        val dialog = _uiState.value.playlistNameDialog ?: return
        _uiState.update { it.copy(playlistNameDialog = null) }
        if (name.isBlank()) return
        viewModelScope.launch {
            val renameId = dialog.renamePlaylistId
            if (dialog.videoContext) {
                if (renameId != null) {
                    videoRepository.renamePlaylist(renameId, name)
                } else {
                    val id = videoRepository.createPlaylist(name)
                    dialog.forVideoId?.let { videoRepository.addVideoToPlaylist(id, it) }
                }
            } else if (renameId != null) {
                musicRepository.renamePlaylist(renameId, name)
            } else {
                val id = musicRepository.createPlaylist(name)
                dialog.forTrackId?.let { musicRepository.addTrackToPlaylist(id, it) }
            }
        }
    }

    fun onCancelPlaylistName() {
        _uiState.update { it.copy(playlistNameDialog = null) }
    }

    private fun openMusicTrackPicker(playlistId: Long) {
        viewModelScope.launch {
            val playlist = musicRepository.observePlaylists().first().firstOrNull { it.id == playlistId }

            val inPlaylist = musicRepository.observePlaylistTracks(playlistId).first().map { it.id }.toSet()
            val tracks = musicRepository.observeAllTracks().first()
                .filterNot { it.id in inPlaylist }
                .trackSorted(_uiState.value.musicSortMode)
            _uiState.update { it.copy(
                musicTrackPicker = MusicTrackPickerState(
                    playlistId   = playlistId,
                    playlistName = playlist?.name ?: "Playlist",
                    tracks       = tracks,
                )
            )}
        }
    }

    private fun moveMusicTrackPicker(delta: Int) {
        val picker = _uiState.value.musicTrackPicker ?: return
        val maxIndex = picker.tracks.size
        val next = (picker.selectedIndex + delta).coerceIn(0, maxIndex)
        _uiState.update { it.copy(musicTrackPicker = picker.copy(selectedIndex = next)) }
    }

    private fun activateMusicTrackPicker() {
        val picker = _uiState.value.musicTrackPicker ?: return
        if (picker.selectedIndex == 0) {
            confirmMusicTrackPicker()
        } else {
            val track = picker.tracks.getOrNull(picker.selectedIndex - 1) ?: return
            val selected = if (track.id in picker.selected) picker.selected - track.id
                           else picker.selected + track.id
            _uiState.update { it.copy(musicTrackPicker = picker.copy(selected = selected)) }
        }
    }

    fun onMusicTrackPickerActivatedAt(index: Int) {
        _uiState.update { it.copy(musicTrackPicker = it.musicTrackPicker?.copy(selectedIndex = index)) }
        activateMusicTrackPicker()
    }

    fun onMusicTrackPickerConfirm() = confirmMusicTrackPicker()

    fun closeMusicTrackPicker() {
        _uiState.update { it.copy(musicTrackPicker = null) }
    }

    private fun confirmMusicTrackPicker() {
        val picker = _uiState.value.musicTrackPicker ?: return
        val playlistId = picker.playlistId
        val trackIds = picker.tracks.map { it.id }.filter { it in picker.selected }
        closeMusicTrackPicker()
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            trackIds.forEach { musicRepository.addTrackToPlaylist(playlistId, it) }
        }
    }

    private fun handleMusicFolderAction(folderId: String, itemId: String) {
        when (itemId) {
            "scan_folder" -> scanMusicFolder(folderId)
            "rename_folder" -> _uiState.update { it.copy(activeSettingsScreen = "settings_music") }
            "enable_folder" -> appAction { musicRepository.setFolderEnabled(folderId, true) }
            "disable_folder" -> appAction { musicRepository.setFolderEnabled(folderId, false) }
            "remove_folder" -> appAction { musicRepository.removeFolder(folderId) }
        }
    }

    private fun handleMusicTrackAction(trackId: String, itemId: String, playlistId: Long?) {
        when (itemId) {
            "play" -> {
                val startIndex = currentMusicTracks.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                if (currentMusicTracks.isNotEmpty()) {
                    musicPlayer.setQueue(currentMusicTracks, startIndex)
                    _uiState.update { it.copy(musicPlayerVisible = true) }
                }
            }

            "play_background" -> {
                val startIndex = currentMusicTracks.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                if (currentMusicTracks.isNotEmpty()) {
                    musicPlayer.setQueue(currentMusicTracks, startIndex)
                    com.psplauncher.feature.xmb.music.MusicPlaybackService.start(context)
                }
            }
            "add_to_playlist" -> openPlaylistPicker(trackId)
            "remove_from_playlist" -> if (playlistId != null) {
                appAction { musicRepository.removeTrackFromPlaylist(playlistId, trackId) }
            }

            "remove_from_recent" -> appAction { musicRepository.clearTrackLastPlayed(trackId) }
            "remove_track" -> appAction {
                val track = musicRepository.getTrack(trackId) ?: return@appAction
                removeSingleTrack(track.folderId, trackId)
            }
        }
    }

    private fun handlePlaylistRowAction(playlistId: Long, itemId: String) {
        when (itemId) {
            "open_playlist"   -> {
                val name = _uiState.value.currentItems.firstOrNull { it.playlistId == playlistId }?.title.orEmpty()
                openMusicView(MusicNav.Playlist(playlistId, name))
            }
            "add_tracks"      -> openMusicTrackPicker(playlistId)
            "rename_playlist" -> promptRenamePlaylist(playlistId)
            "delete_playlist" -> appAction {
                musicRepository.deletePlaylist(playlistId)
                if ((_uiState.value.musicNav as? MusicNav.Playlist)?.id == playlistId) closeMusicView()
            }
        }
    }

    private fun scanMusicFolder(folderId: String) {
        viewModelScope.launch {
            val folder = musicRepository.getFolder(folderId) ?: return@launch
            val taskId = "music_scan_$folderId"
            addBackgroundTask(BackgroundTaskInfo(taskId, "Scanning ${folder.displayName}", null))
            musicScanner.scan(folder).collect { result ->
                when (result) {
                    is com.psplauncher.feature.library.scanner.MusicScanResult.Progress -> Unit
                    is com.psplauncher.feature.library.scanner.MusicScanResult.Complete -> {
                        musicRepository.replaceTracksForFolder(result.folderId, result.tracks, System.currentTimeMillis())
                        completeBackgroundTask(taskId, "${result.tracks.size} tracks")
                    }
                    is com.psplauncher.feature.library.scanner.MusicScanResult.Error ->
                        failBackgroundTask(taskId, result.message)
                }
            }
        }
    }

    private suspend fun removeSingleTrack(folderId: String, trackId: String) {
        val tracks = musicRepository.observeTracksByFolder(folderId).first().filterNot { it.id == trackId }
        musicRepository.replaceTracksForFolder(folderId, tracks, System.currentTimeMillis())
    }

    private fun activeSortContext(): List<XmbSortMode>? = _uiState.value.activeSortModes()

    fun onSortLabelTapped() {
        markTouchInput()
        cycleSort()
    }

    private fun recentFilterAndApps(): Flow<Pair<RecentFilter, List<Pair<Long, XMBItem>>>> =
        combine(
            _uiState.map { it.recentFilter }.distinctUntilChanged(),
            _uiState.map { it.recentsIncludeApps }.distinctUntilChanged(),
            appCategoryRepository.changes().onStart { emit(Unit) },
        ) { filter, includeApps, _ -> filter to includeApps }
            .map { (filter, includeApps) ->
                if (!includeApps) return@map filter to emptyList<Pair<Long, XMBItem>>()
                val rows = appCategoryRepository.allInstalledApps()
                    .filter { it.lastUsedAt > 0L }

                    .filterNot { isHiddenAt(HiddenPlacement.appKey(it.packageName), HideLocationType.RECENTS) }
                    .sortedByDescending { it.lastUsedAt }
                    .take(RECENTLY_PLAYED_LIMIT)
                    .map { app ->
                        app.lastUsedAt to XMBItem(
                            id = "$RECENT_APP_ID_PREFIX${app.packageName}",
                            title = app.label,
                            subtitle = "App",
                            packageName = app.packageName,

                            isAndroidApp = true,
                        )
                    }
                filter to rows
            }

    private fun cycleRecentFilter() =
        setRecentFilter(_uiState.value.let { it.recentFilter.next(it.recentsIncludeApps) })

    fun setRecentFilter(filter: RecentFilter) {
        menuSound.play(MenuSound.SCROLL)
        _uiState.update { it.copy(recentFilter = filter, selectedItemIndex = 0) }
    }

    fun toggleRecentRail() {
        if (!_uiState.value.onLastPlayedHome) return
        menuSound.play(MenuSound.SYSTEM_BROWSE)
        _uiState.update { it.copy(recentRailVisible = !it.recentRailVisible) }
    }

    private fun cycleSort() {
        _uiState.value.musicBrowser?.let { browser ->
            if (browser.view is MusicBrowserView.Playlists || browser.view.listsGroups) return
            val next = MUSIC_SORTS[(MUSIC_SORTS.indexOf(_uiState.value.musicSortMode).coerceAtLeast(0) + 1) % MUSIC_SORTS.size]
            menuSound.play(MenuSound.SYSTEM_BROWSE)
            _uiState.update { it.copy(
                musicSortMode = next,
                musicBrowser = it.musicBrowser?.copy(
                    selectedIndex = 0,
                    scrollToTopToken = browser.scrollToTopToken + 1,
                ),
            )}
            rebuildBrowserTrackRows()
            return
        }
        val cycle = activeSortContext() ?: return
        val isMusic = cycle === MUSIC_SORTS
        val current = _uiState.value.sortModeFor(cycle)
        val next = cycle[(cycle.indexOf(current).coerceAtLeast(0) + 1) % cycle.size]
        menuSound.play(MenuSound.SYSTEM_BROWSE)

        _uiState.update {
            it.withSortMode(cycle, next)
                .copy(selectedItemIndex = 0, scrollToTopToken = it.scrollToTopToken + 1)
        }

        if (isMusic) {
            val trailing = if (_uiState.value.musicNav is MusicNav.Playlist) listOf(addTracksItem()) else emptyList()
            val emptyItem = if (_uiState.value.musicNav is MusicNav.Playlist) emptyPlaylistItem() else emptyAllMusicItem()
            setMusicTrackItems(currentMusicTracksRaw, emptyItem, trailing)
            _uiState.update { it.copy(sortLabel = currentSortLabel()) }
            return
        }
        loadItemsForCategory(currentCategory())
    }

    private fun computeDrillTitle(): String? {
        val s = _uiState.value

        val musicTitle = when (val nav = s.musicNav) {
            MusicNav.AllMusic    -> "Music"
            MusicNav.Playlists   -> "Playlist"
            is MusicNav.Playlist -> nav.name
            MusicNav.Root        -> null
        }
        if (musicTitle != null) return musicTitle

        val videoTitle = when (val nav = s.videoNav) {
            VideoNav.AllVideos       -> "All Videos"
            VideoNav.Collections     -> "Collections"
            VideoNav.RecentlyWatched -> "Recently Watched"
            VideoNav.Favorites       -> "Favorites"
            VideoNav.Playlists       -> "Playlists"
            is VideoNav.Playlist     -> nav.name
            VideoNav.Libraries       -> "Video Libraries"
            is VideoNav.Library      -> nav.name
            VideoNav.Root            -> null
        }
        if (videoTitle != null) return videoTitle

        val photoTitle = when (val nav = s.photoNav) {
            PhotoNav.AllPhotos  -> "All Photos"
            PhotoNav.Albums     -> "Albums"
            is PhotoNav.Library -> nav.name
            PhotoNav.Root       -> null
        }
        if (photoTitle != null) return photoTitle
        val booksTitle = when (val nav = s.booksNav) {
            BooksNav.AllBooks -> "Books"
            BooksNav.Shelves  -> "Shelves"
            is BooksNav.Shelf -> nav.name
            BooksNav.SeriesList -> "Series"
            is BooksNav.Series  -> nav.name
            BooksNav.Root     -> null
        }
        if (booksTitle != null) return booksTitle
        return when {
            s.selectedCollectionId != null ->
                s.collections.firstOrNull { it.id == s.selectedCollectionId }?.name ?: "Collection"
            s.selectedPlatformId == ALL_GAMES_PLATFORM_ID -> "All Games"
            s.selectedPlatformId == FAVORITES_PLATFORM_ID -> "Favorites"
            s.selectedPlatformId == MISSING_PLATFORM_ID   -> "Missing"
            s.selectedPlatformId != null ->
                enabledCards.firstOrNull { it.platformId == s.selectedPlatformId }?.displayName
                    ?: platformCache[s.selectedPlatformId]?.name
                    ?: s.selectedPlatformId
            else -> null
        }
    }

    private fun videoLibrarySiblings(): List<XMBItem> =
        _uiState.value.videoLibraries.map { XMBItem(id = "vlib_${it.id}", title = it.displayName, type = XMBItemType.VIDEO_FOLDER) }

    private fun photoAlbumSiblings(): List<XMBItem> =
        _uiState.value.photoLibraries.map { XMBItem(id = "plib_${it.id}", title = it.displayName, type = XMBItemType.PHOTO_FOLDER) }

    private fun musicPlaylistSiblings(): List<XMBItem> =
        _uiState.value.musicPlaylists.map { XMBItem(id = "pl_${it.id}", title = it.name, playlistId = it.id, type = XMBItemType.PLAYLIST) }

    private fun videoPlaylistSiblings(): List<XMBItem> =
        _uiState.value.videoPlaylists.map { XMBItem(id = "vpl_${it.id}", title = it.name, playlistId = it.id, type = XMBItemType.PLAYLIST) }

    private fun computeDrillSiblings(category: Category?): Pair<List<XMBItem>, Int> {
        val s = _uiState.value

        if (s.musicNav != MusicNav.Root) {
            (s.musicNav as? MusicNav.Playlist)?.let { nav ->
                val pls = musicPlaylistSiblings()
                if (pls.isNotEmpty()) return pls to pls.indexOfFirst { it.playlistId == nav.id }.coerceAtLeast(0)
            }
            val sibs = _uiState.value.musicRootSections().filter {
                it.type == XMBItemType.PLAYLIST || it.type == XMBItemType.MEMORY_CARD
            }
            val idx = sibs.indexOfFirst { sib ->
                when (s.musicNav) {
                    MusicNav.AllMusic  -> sib.type == XMBItemType.MEMORY_CARD
                    else               -> sib.type == XMBItemType.PLAYLIST
                }
            }.coerceAtLeast(0)
            return sibs to idx
        }

        if (s.videoNav != VideoNav.Root) {
            (s.videoNav as? VideoNav.Library)?.let { nav ->
                val libs = videoLibrarySiblings()
                if (libs.isNotEmpty()) return libs to libs.indexOfFirst { it.id == "vlib_${nav.id}" }.coerceAtLeast(0)
            }
            (s.videoNav as? VideoNav.Playlist)?.let { nav ->
                val pls = videoPlaylistSiblings()
                if (pls.isNotEmpty()) return pls to pls.indexOfFirst { it.playlistId == nav.id }.coerceAtLeast(0)
            }

            if (s.videoNav.isVideoCollectionChild || s.videoNav is VideoNav.Playlist) {
                val sibs = videoCollectionsItems()
                val idx = sibs.indexOfFirst { sib ->
                    when (s.videoNav) {
                        VideoNav.RecentlyWatched -> sib.type == XMBItemType.VIDEO_RECENT
                        VideoNav.Favorites       -> sib.type == XMBItemType.VIDEO_FAVORITES
                        else                     -> sib.type == XMBItemType.PLAYLIST
                    }
                }.coerceAtLeast(0)
                return sibs to idx
            }

            val sibs = _uiState.value.videoRootSections().filter {
                it.type == XMBItemType.MEMORY_CARD || it.type == XMBItemType.VIDEO_COLLECTIONS ||
                    it.type == XMBItemType.VIDEO_LIBRARY
            }
            val idx = sibs.indexOfFirst { sib ->
                when (s.videoNav) {
                    VideoNav.AllVideos   -> sib.type == XMBItemType.MEMORY_CARD
                    VideoNav.Collections -> sib.type == XMBItemType.VIDEO_COLLECTIONS
                    else                 -> sib.type == XMBItemType.VIDEO_LIBRARY
                }
            }.coerceAtLeast(0)
            return sibs to idx
        }

        if (s.photoNav != PhotoNav.Root) {
            (s.photoNav as? PhotoNav.Library)?.let { nav ->
                val albums = photoAlbumSiblings()
                if (albums.isNotEmpty()) return albums to albums.indexOfFirst { it.id == "plib_${nav.id}" }.coerceAtLeast(0)
            }
            val sibs = _uiState.value.photoRootSections(cameraAvailable).filter {
                it.type == XMBItemType.MEMORY_CARD || it.type == XMBItemType.PHOTO_ALBUMS
            }
            val idx = sibs.indexOfFirst { sib ->
                when (s.photoNav) {
                    PhotoNav.AllPhotos -> sib.type == XMBItemType.MEMORY_CARD
                    else               -> sib.type == XMBItemType.PHOTO_ALBUMS
                }
            }.coerceAtLeast(0)
            return sibs to idx
        }
        if (category?.id == BuiltInCategory.GAMES) {
            val sibs = memoryCardItems().filter {
                it.type == XMBItemType.ALL_GAMES || it.type == XMBItemType.FAVORITES ||
                    it.type == XMBItemType.MISSING ||
                    it.type == XMBItemType.MEMORY_CARD || it.type == XMBItemType.COLLECTION
            }
            val idx = sibs.indexOfFirst { sib ->
                when {
                    s.selectedPlatformId == ALL_GAMES_PLATFORM_ID -> sib.type == XMBItemType.ALL_GAMES
                    s.selectedPlatformId == FAVORITES_PLATFORM_ID -> sib.type == XMBItemType.FAVORITES
                    s.selectedPlatformId == MISSING_PLATFORM_ID   -> sib.type == XMBItemType.MISSING
                    s.selectedCollectionId != null               -> sib.collectionId == s.selectedCollectionId
                    s.selectedPlatformId != null                 -> sib.platformId == s.selectedPlatformId
                    else -> false
                }
            }.coerceAtLeast(0)
            return sibs to idx
        }

        val parent = XMBItem(id = "drill_parent", title = computeDrillTitle().orEmpty(), type = XMBItemType.COLLECTION)
        return listOf(parent) to 0
    }

    private fun currentSortLabel(): String? {
        val cycle = activeSortContext() ?: return null
        return _uiState.value.sortModeFor(cycle).label
    }

    private fun emptyCategoryItem(category: Category): XMBItem {
        val (message, subtitle) = if (category.isGamingCategory) {
            "No games assigned." to "Add games to this category."
        } else {
            val msg = when (category.id) {
                "network"   -> "No browser apps found."
                else        -> "No apps assigned."
            }
            msg to "Install some apps to get started."
        }
        return XMBItem(
            id       = EMPTY_CATEGORY_ITEM_ID,
            title    = message,
            subtitle = subtitle,
            type     = XMBItemType.EMPTY,
        )
    }

    private fun cardItemId(platformId: String): String = "card_" + platformId

    private fun memoryCardItems(): List<XMBItem> {
        val totalGames = _uiState.value.allGamesCount
        val allGamesItem = XMBItem(
            id       = ALL_GAMES_ITEM_ID,
            title    = "All Games",
            subtitle = countLabel(totalGames, "game", "games"),
            insideCovers = _uiState.value.cardFanCovers[ALL_GAMES_ITEM_ID].orEmpty(),
            type     = XMBItemType.ALL_GAMES,
        )

        val missingCount = _uiState.value.missingCount
        val missingItem = if (missingCount > 0) {
            XMBItem(
                id       = MISSING_ITEM_ID,
                title    = "Missing",
                subtitle = countLabel(missingCount, "game", "games"),
                type     = XMBItemType.MISSING,
            )
        } else null
        val header = listOfNotNull(allGamesItem, missingItem)

        val collectionItems = _uiState.value.collections
            .filter { it.categoryId == BuiltInCategory.GAMES }
            .sortedByDescending { it.isPinned }
            .map { collection ->
            val games = countLabel(collection.gameCount, "game", "games")
            XMBItem(
                id           = "collection_${collection.id}",
                title        = collection.name,
                subtitle     = if (collection.isPinned) "Pinned · $games" else games,
                collectionId = collection.id,
                iconKey      = collection.iconKey,
                type         = XMBItemType.COLLECTION,
            )
        }

        val visibleCards = enabledCards.filter { card ->
            card.platformId != WINDOWS_PLATFORM_ID ||
                (_uiState.value.platformGameCounts[WINDOWS_PLATFORM_ID] ?: card.gameCount) > 0
        }

        if (visibleCards.isEmpty()) {
            return libraryColumn(
                header + collectionItems + XMBItem(
                    id       = NO_CONSOLES_ITEM_ID,
                    title    = "No consoles configured",
                    subtitle = "Open Library Manager to add a Memory Card",
                    type     = XMBItemType.EMPTY,
                ),
                SearchScope.GAMES,
            )
        }

        val cardRows = visibleCards.map { card ->
            val count = _uiState.value.platformGameCounts[card.platformId] ?: card.gameCount
            XMBItem(
                id          = cardItemId(card.platformId),
                title       = if (card.platformId == WINDOWS_PLATFORM_ID) "Windows Games" else card.displayName,
                subtitle    = countLabel(count, "game", "games"),
                platformId  = card.platformId,
                insideCovers = _uiState.value.cardFanCovers[cardItemId(card.platformId)].orEmpty(),
                accentColor = platformCache[card.platformId]?.accentColor,
                type        = XMBItemType.MEMORY_CARD,
            )
        }

        val gapRow = if (totalGames == 0) setupGapItem() else null
        return libraryColumn(
            header + collectionItems + cardRows + listOfNotNull(gapRow),
            SearchScope.GAMES,
        )
    }

    private fun setupGapItem(): XMBItem? {
        val gap = setupState.firstGap
        if (gap == com.psplauncher.feature.launcher.SetupGap.NONE) return null
        return XMBItem(
            id       = SETUP_GAP_ITEM_ID,
            title    = gap.message,
            subtitle = "Press confirm to open Settings and fix it.",
            type     = XMBItemType.EMPTY,
        )
    }

    private fun emptyAllGamesItem(): XMBItem {
        setupGapItem()?.let { return it }
        return XMBItem(
            id       = NO_GAMES_ITEM_ID,
            title    = "No games imported yet",
            subtitle = "Open a Memory Card to scan your library.",
            type     = XMBItemType.EMPTY,
        )
    }

    private fun emptyCollectionItem(): XMBItem = XMBItem(
        id       = EMPTY_COLLECTION_ITEM_ID,
        title    = "This collection is empty",
        subtitle = "Add games from any console with the options (△) menu.",
        type     = XMBItemType.EMPTY,
    )

    private fun emptyFavoritesItem(): XMBItem = XMBItem(
        id       = EMPTY_FAVORITES_ITEM_ID,
        title    = "No favorites yet",
        subtitle = "Mark a game as a favorite from its options (△) menu.",
        type     = XMBItemType.EMPTY,
    )

    private fun emptyMissingItem(): XMBItem = XMBItem(
        id       = EMPTY_MISSING_ITEM_ID,
        title    = "Nothing missing",
        subtitle = "Every game's file was found on the last scan.",
        type     = XMBItemType.EMPTY,
    )

    private fun emptyFolderItem(platformId: String): XMBItem {
        if (platformId == ANDROID_PLATFORM_ID) {
            return XMBItem(
                id         = FIND_GAMES_ITEM_ID,
                title      = "Find Games",
                subtitle   = "Pick installed apps to add to this library",
                platformId = platformId,
            )
        }
        val card = enabledCards.firstOrNull { it.platformId == platformId }

        val gap = setupState.firstGap
        if (gap != com.psplauncher.feature.launcher.SetupGap.NONE) {
            return XMBItem(
                id         = SETUP_GAP_ITEM_ID,
                title      = gap.message,
                subtitle   = "Press confirm to open Settings and fix it.",
                platformId = platformId,
                type       = XMBItemType.EMPTY,
            )
        }
        val subtitle = when {
            card?.romDirectory == null -> "ROM directory not configured"
            else                       -> "Press ▲ to scan this console"
        }
        return XMBItem(
            id         = NO_GAMES_ITEM_ID,
            title      = "No games found in this folder",
            subtitle   = subtitle,
            platformId = platformId,
            type       = XMBItemType.EMPTY,
        )
    }

    private fun publishGameItems(items: List<XMBItem>, keepCursorOnRow: Boolean) = _uiState.update {
        if (!keepCursorOnRow) it.copy(currentItems = items)
        else it.copy(
            currentItems = items,
            selectedItemIndex = cursorAfterRefresh(it.currentItems, it.selectedItemIndex, items),
        )
    }

    private fun List<com.psplauncher.core.domain.model.Game>.toXmbItems() = map { g ->
        XMBItem(
            id           = g.id.toString(),
            title        = g.displayTitle,
            artworkUri   = g.artworkUri,
            heroUri      = g.heroUri,
            iconUri      = g.iconUri,
            logoUri      = g.logoUri,
            boxArtUri    = g.boxArtUri,
            physicalMediaUri = g.physicalMediaUri,
            box3dUri     = g.box3dUri,
            iconDisplayModeOverride = g.iconDisplayMode,
            subtitle     = gameMetaLabel(g),
            metadataLine = gameMetadataLine(g.releaseYear, g.genre, g.developer, g.players),
            description  = g.description,
            romPath      = g.romPath,
            totalPlayTimeMillis = g.totalPlayTimeMillis,
            gameId       = g.id,
            platformId   = g.platformId,
            accentColor  = platformCache[g.platformId]?.accentColor,
            isFavorite   = g.isFavorite,
            playState    = g.playState,
            isAndroidApp = g.packageName != null,
            isRealGame   = g.contentType == GameContentType.GAME,
            packageName  = g.packageName,
            shortcutId   = g.shortcutId,
            launchIntentUri = g.launchIntentUri,
        )
    }

    private fun tintWaveForCategory(category: Category?) {
        _uiState.update { it.copy(themeColors = baseThemeColors) }
    }

    private fun observeGamepadMappings() {
        viewModelScope.launch {
            mappingRepository.mappings.collect { mappings ->
                gamepadInputHandler.currentMappings = mappings
            }
        }
        viewModelScope.launch {
            controllerLayoutRepository.prefs.collect { prefs ->

                gamepadInputHandler.scrollSpeed = prefs.scrollSpeed
                gamepadInputHandler.stickSensitivity = prefs.stickSensitivity
                _uiState.update { it.copy(leftBacksOut = prefs.leftBacksOut) }
            }
        }
    }

    fun onPromptTapped(action: GamepadAction) {
        markTouchInput()
        dispatchGamepadAction(action)
    }

    fun onClaimedKey(action: GamepadAction) {
        markControllerInput()
        onUserInteraction()
        dispatchGamepadAction(action)
    }

    private fun collectGamepadActions() {
        viewModelScope.launch {
            gamepadInputHandler.actions.collect { action ->
                onUserInteraction()
                dispatchGamepadAction(action)
            }
        }
        viewModelScope.launch {
            gamepadInputHandler.shoulderHolds.collect { hold ->
                onUserInteraction()
                when (hold) {
                    is ShoulderHold.Start -> openLetterJump()

                    is ShoulderHold.End ->
                        if (_uiState.value.letterJump != null) closeLetterJump()
                        else dispatchGamepadAction(hold.action)
                }
            }
        }
    }

    private fun openLetterJump() {
        val s = _uiState.value
        if (s.hasBlockingOverlay || s.letterJump != null) return
        val rail = letterJumpFor(s.currentItems, s.selectedItemIndex) ?: return
        _uiState.update { it.copy(letterJump = rail, selectedItemIndex = rail.targetIndex) }
    }

    private fun closeLetterJump() = _uiState.update { it.copy(letterJump = null) }

    private fun moveLetterJump(delta: Int) {
        val rail = _uiState.value.letterJump ?: return
        val next = rail.move(delta)
        if (next === rail) return
        menuSound.play(MenuSound.SCROLL)
        _uiState.update { it.copy(letterJump = next, selectedItemIndex = next.targetIndex) }
    }

    fun onLetterRailTouch(fraction: Float) {
        onUserInteraction()
        val s = _uiState.value

        if (s.hasBlockingOverlay) return
        val rail = s.letterJump
            ?: letterJumpFor(s.currentItems, s.selectedItemIndex)?.also { raised ->
                _uiState.update { it.copy(letterJump = raised) }
            }
            ?: return
        val next = rail.atFraction(fraction)
        if (next === rail) return
        menuSound.play(MenuSound.SCROLL)
        _uiState.update { it.copy(letterJump = next, selectedItemIndex = next.targetIndex) }
    }

    fun onLetterRailReleased() = closeLetterJump()

    private fun observeContextMenuHintIdle() {
        viewModelScope.launch {
            while (isActive) {
                delay(IDLE_HINT_POLL_MS)
                val s = _uiState.value
                val idleMs = SystemClock.elapsedRealtime() - lastInteractionMs

                val waveIdle = idleMs >= WAVE_IDLE_MS
                if (waveIdle != s.idle) _uiState.update { it.copy(idle = waveIdle) }
                val shouldShow = com.psplauncher.feature.xmb.viewmodel.shouldShowContextMenuHint(
                    state = s,
                    idleMs = idleMs,
                )

                val shouldShowDrawer = com.psplauncher.feature.xmb.viewmodel.shouldShowAppDrawerHint(
                    state = s,
                    idleMs = idleMs,
                )
                val shouldShowSettings = com.psplauncher.feature.xmb.viewmodel.shouldShowSettingsHint(
                    state = s,
                    idleMs = idleMs,
                )
                if (shouldShow != s.showContextMenuHint ||
                    shouldShowDrawer != s.showAppDrawerHint ||
                    shouldShowSettings != s.showSettingsHint
                ) {
                    _uiState.update {
                        it.copy(
                            showContextMenuHint = shouldShow,
                            showAppDrawerHint = shouldShowDrawer,
                            showSettingsHint = shouldShowSettings,
                        )
                    }
                }
            }
        }
    }

    private fun dispatchGamepadAction(action: GamepadAction) {
        markControllerInput()
        val state = _uiState.value

        if (state.letterJump != null) {
            when (action) {
                GamepadAction.NAVIGATE_UP -> moveLetterJump(-1)
                GamepadAction.NAVIGATE_DOWN -> moveLetterJump(+1)
                GamepadAction.BACK -> _uiState.update {
                    it.copy(letterJump = null, selectedItemIndex = state.letterJump.returnIndex)
                }
                else -> Unit
            }
            return
        }

        if (state.appPicker != null) {
            when (action) {
                GamepadAction.NAVIGATE_UP,
                GamepadAction.NAVIGATE_DOWN,
                GamepadAction.NAVIGATE_LEFT,
                GamepadAction.NAVIGATE_RIGHT -> moveAppPicker(action)

                GamepadAction.SELECT -> {
                    val picker = state.appPicker
                    if (picker.confirmingRemovals) {
                        if (picker.confirmFocusedOption == AppPickerState.CONFIRM_REMOVE) commitAppPicker()
                        else cancelConfirm()
                    } else toggleFocusedApp()
                }

                GamepadAction.HOME -> requestApplyAppPicker()
                GamepadAction.CHANGE_SORT -> _uiState.update { s ->
                    s.copy(appPicker = s.appPicker?.let { p ->
                        (if (p.searchActive) closeAppPickerSearch(p) else p.copy(searchActive = true)).clampFocus()
                    })
                }

                GamepadAction.BACK,
                GamepadAction.OPEN_CONTEXT_MENU -> handleAppPickerBack()
                else -> Unit
            }
            return
        }

        if (state.musicTrackPicker != null) {
            when (action) {
                GamepadAction.NAVIGATE_UP   -> moveMusicTrackPicker(-1)
                GamepadAction.NAVIGATE_DOWN -> moveMusicTrackPicker(+1)
                GamepadAction.SELECT        -> activateMusicTrackPicker()
                GamepadAction.HOME          -> confirmMusicTrackPicker()
                GamepadAction.BACK,
                GamepadAction.OPEN_CONTEXT_MENU    -> closeMusicTrackPicker()
                else -> Unit
            }
            return
        }

        if (state.gamePickerCategoryId != null) {
            when (action) {
                GamepadAction.NAVIGATE_UP,
                GamepadAction.NAVIGATE_DOWN,
                GamepadAction.SELECT,
                GamepadAction.HOME,
                GamepadAction.BACK,
                GamepadAction.OPEN_CONTEXT_MENU -> _uiState.update { it.copy(pendingGamePickerAction = action) }
                else -> Unit
            }
            return
        }

        if (state.notificationsOpen) {
            when (action) {
                GamepadAction.NAVIGATE_UP   -> moveNoticeCursor(-1)
                GamepadAction.NAVIGATE_DOWN -> moveNoticeCursor(+1)

                GamepadAction.NAVIGATE_LEFT  ->
                    if (state.focusedNotice == NoticeFocus.Media) musicPlayer.prev()
                GamepadAction.NAVIGATE_RIGHT ->
                    if (state.focusedNotice == NoticeFocus.Media) musicPlayer.next()
                GamepadAction.SELECT             -> activateFocusedNotice()
                GamepadAction.OPEN_CONTEXT_MENU  -> dismissFocusedNotice()
                GamepadAction.BACK,
                GamepadAction.HOME               -> {
                    menuSound.play(MenuSound.BACK)
                    closeNotifications()
                }
                else -> Unit
            }
            return
        }

        if (state.activeContextMenu != null) {
            when (action) {
                GamepadAction.NAVIGATE_UP   -> shiftContextMenu(-1)
                GamepadAction.NAVIGATE_DOWN -> shiftContextMenu(+1)

                GamepadAction.SELECT        -> {
                    val menu = state.activeContextMenu
                    val picked = menu?.selectedIndex?.let { state.menuRows().getOrNull(it) }
                    when {
                        picked != null -> onContextMenuItemActivatedAt(menu.selectedIndex!!)
                        menu?.primaryId != null -> activateContextMenuItem(menu.primaryId)
                        else -> Unit
                    }
                }
                GamepadAction.BACK                   -> popContextMenu()
                GamepadAction.OPEN_CONTEXT_MENU      -> closeContextMenu()
                else -> Unit
            }
            return
        }

        if (state.xmbLayoutAdjust != null) {
            when (action) {
                GamepadAction.NAVIGATE_LEFT  -> nudgeXmbLayoutHorizontal(-1)
                GamepadAction.NAVIGATE_RIGHT -> nudgeXmbLayoutHorizontal(+1)
                GamepadAction.NAVIGATE_UP    -> nudgeXmbLayoutVertical(-1)
                GamepadAction.NAVIGATE_DOWN  -> nudgeXmbLayoutVertical(+1)
                GamepadAction.PREV_CATEGORY  -> nudgeXmbLayoutScale(-1)
                GamepadAction.NEXT_CATEGORY  -> nudgeXmbLayoutScale(+1)
                GamepadAction.OPEN_CONTEXT_MENU -> resetXmbLayoutAdjust()

                GamepadAction.CHANGE_SORT       -> toggleXmbLayoutSliders()
                GamepadAction.SELECT         -> saveXmbLayoutAdjust()
                GamepadAction.BACK           -> cancelXmbLayoutAdjust()
                else -> Unit
            }
            return
        }

        if (state.musicPlayerVisible) {
            when (action) {
                GamepadAction.SELECT         -> musicPlayPause()
                GamepadAction.NAVIGATE_LEFT  -> musicPrev()
                GamepadAction.NAVIGATE_RIGHT -> musicNext()
                GamepadAction.NAVIGATE_UP    -> musicSeekBy(10_000)
                GamepadAction.NAVIGATE_DOWN  -> musicSeekBy(-10_000)
                GamepadAction.OPEN_CONTEXT_MENU     -> openMusicPlayerOptions()
                GamepadAction.BACK           -> closeMusicPlayer()
                else -> Unit
            }
            return
        }

        if (state.customColorPicker != null) {
            when (action) {
                GamepadAction.NAVIGATE_UP -> moveCustomColorChannel(-1)
                GamepadAction.NAVIGATE_DOWN -> moveCustomColorChannel(1)
                GamepadAction.NAVIGATE_LEFT -> adjustCustomColor(-0.04f)
                GamepadAction.NAVIGATE_RIGHT -> adjustCustomColor(0.04f)
                GamepadAction.SELECT -> confirmCustomColor()
                GamepadAction.BACK, GamepadAction.OPEN_CONTEXT_MENU -> cancelCustomColor()
                else -> Unit
            }
            return
        }
        if (state.colorSchemePicker != null) {
            when (action) {
                GamepadAction.NAVIGATE_UP   -> moveColorSchemePicker(-1)
                GamepadAction.NAVIGATE_DOWN -> moveColorSchemePicker(+1)
                GamepadAction.SELECT        -> confirmColorSchemePicker()
                GamepadAction.BACK,
                GamepadAction.OPEN_CONTEXT_MENU    -> cancelColorSchemePicker()
                else -> Unit
            }
            return
        }

        if (state.renameAppTarget != null) {
            when (action) {
                GamepadAction.SELECT -> onConfirmAppRename(state.renameAppText)
                GamepadAction.BACK   -> onCancelAppRename()
                else                 -> Unit
            }
            return
        }
        if (state.collectionNameDialog != null) {
            when (action) {
                GamepadAction.SELECT -> onConfirmCollectionName(state.collectionNameDialog.text)
                GamepadAction.BACK   -> onCancelCollectionName()
                else                 -> Unit
            }
            return
        }
        if (state.playlistNameDialog != null) {
            when (action) {
                GamepadAction.SELECT -> onConfirmPlaylistName(state.playlistNameDialog.text)
                GamepadAction.BACK   -> onCancelPlaylistName()
                else                 -> Unit
            }
            return
        }

        if (state.infoDialog != null) {
            if (action == GamepadAction.BACK || action == GamepadAction.SELECT) dismissInfoDialog()
            return
        }

        state.launchRecovery?.let { recovery ->
            val actions = com.psplauncher.feature.launcher.launchRecoveryActions(recovery)
            when (action) {
                GamepadAction.NAVIGATE_UP -> _uiState.update {
                    it.copy(launchRecoveryCursor = (it.launchRecoveryCursor - 1 + actions.size) % actions.size)
                }
                GamepadAction.NAVIGATE_DOWN -> _uiState.update {
                    it.copy(launchRecoveryCursor = (it.launchRecoveryCursor + 1) % actions.size)
                }
                GamepadAction.SELECT -> actions
                    .getOrNull(state.launchRecoveryCursor.coerceIn(0, actions.lastIndex))
                    ?.let { (a, _) -> onLaunchRecoveryAction(a) }
                GamepadAction.BACK -> onLaunchRecoveryAction(LaunchRecoveryAction.DISMISS)
                else -> Unit
            }
            return
        }

        if (state.showWindowsSetupPrompt) {
            when (action) {
                GamepadAction.SELECT -> confirmWindowsSetupPrompt()
                GamepadAction.BACK   -> dismissWindowsSetupPrompt()
                else                 -> Unit
            }
            return
        }

        if (state.search != null) {
            when (action) {
                GamepadAction.NAVIGATE_UP    -> moveSearch(-searchColumns())
                GamepadAction.NAVIGATE_DOWN  -> moveSearch(+searchColumns())
                GamepadAction.NAVIGATE_LEFT  -> moveSearch(-1)
                GamepadAction.NAVIGATE_RIGHT -> moveSearch(+1)
                GamepadAction.SELECT        -> onSearchActivatedAt(state.search.selectedIndex)
                GamepadAction.BACK          -> closeSearch()
                else -> Unit
            }
            return
        }

        if (state.musicBrowser != null) {
            when (action) {
                GamepadAction.NAVIGATE_UP    -> moveMusicBrowser(-1)
                GamepadAction.NAVIGATE_DOWN  -> moveMusicBrowser(+1)
                GamepadAction.SELECT         -> activateMusicBrowser()
                GamepadAction.BACK           -> onMusicBrowserBack()
                GamepadAction.OPEN_CONTEXT_MENU     -> openMusicBrowserContextMenu()
                GamepadAction.CHANGE_SORT    -> cycleSort()
                else -> Unit
            }
            return
        }

        if (state.showBootSequence) {
            if (action == GamepadAction.SELECT || action == GamepadAction.BACK) {
                onBootSequenceComplete()
            }
            return
        }

        if (state.activeGameBoot != null) {
            if (action == GamepadAction.SELECT || action == GamepadAction.BACK) {
                onGameBootComplete()
            }
            return
        }

        if (action == GamepadAction.HOME && state.statusStripVisible && state.activeGameId == null) {
            toggleNotifications()
            return
        }

        when {
            state.activePhotoViewer != null -> {
                _uiState.update { it.copy(pendingPhotoViewerAction = action) }
                return
            }
            state.activeVideoId != null -> {
                _uiState.update { it.copy(pendingVideoDetailAction = action) }
                return
            }
            state.metadataPreview != null -> {
                handleMetadataPreviewInput(action)
                return
            }
            state.manualViewer != null -> {
                handleManualViewerInput(action)
                return
            }
            state.artworkStudioGameId != null -> {
                _uiState.update { it.copy(pendingArtworkStudioAction = action) }
                return
            }
            state.activeGameId != null -> {
                _uiState.update { it.copy(pendingGameDetailAction = action) }
                return
            }
            state.activeAppId != null -> {
                _uiState.update { it.copy(pendingAppDetailAction = action) }
                return
            }
            state.activeSettingsScreen != null -> {
                Timber.d("Gamepad → settings(${state.activeSettingsScreen}): $action")

                when (action) {
                    GamepadAction.BACK,
                    GamepadAction.NAVIGATE_UP,
                    GamepadAction.NAVIGATE_DOWN,

                    GamepadAction.NAVIGATE_LEFT,
                    GamepadAction.NAVIGATE_RIGHT,
                    GamepadAction.OPEN_CONTEXT_MENU,
                    GamepadAction.CHANGE_SORT,

                    GamepadAction.PREV_CATEGORY,
                    GamepadAction.NEXT_CATEGORY,
                    GamepadAction.SELECT -> _uiState.update { it.copy(pendingSettingsAction = action) }
                    else -> Unit
                }
                return
            }
            state.activeAppDrawerFilter != null -> {
                _uiState.update { it.copy(pendingDrawerAction = action) }
                return
            }
            state.saveThemeNameDialog != null -> {
                when (action) {
                    GamepadAction.SELECT -> confirmSaveCurrentLookAsTheme(state.saveThemeNameDialog.text)
                    GamepadAction.BACK   -> dismissSaveThemeNameDialog()
                    else                 -> Unit
                }
                return
            }
            state.customIconSession != null -> {
                when (action) {
                    GamepadAction.NAVIGATE_LEFT, GamepadAction.NAVIGATE_UP -> onCustomIconSlotMove(-1)
                    GamepadAction.NAVIGATE_RIGHT, GamepadAction.NAVIGATE_DOWN -> onCustomIconSlotMove(+1)
                    GamepadAction.PREV_CATEGORY -> onCustomIconGroupMove(-1)
                    GamepadAction.NEXT_CATEGORY -> onCustomIconGroupMove(+1)
                    GamepadAction.SELECT,
                    GamepadAction.OPEN_CONTEXT_MENU,
                    GamepadAction.BACK -> _uiState.update { it.copy(pendingCustomIconsAction = action) }
                    else -> Unit
                }
                return
            }
        }

        if (state.hasBlockingOverlay) {
            Timber.w("Gamepad action $action dropped: a blocking overlay has no branch in this dispatcher")
            return
        }

        when (action) {
            GamepadAction.NAVIGATE_UP   -> {
                if (state.activePillIndex() != null) {
                    menuSound.play(MenuSound.SCROLL)
                    _uiState.update { it.copy(pillCursor = null) }
                    return
                }
                if (!moveItemCursor(-1)) gamepadInputHandler.cancelRepeat()
            }
            GamepadAction.NAVIGATE_DOWN -> {
                if (state.activePillIndex() != null) {
                    gamepadInputHandler.cancelRepeat()
                    return
                }
                if (moveItemCursor(+1)) return

                if (state.pillRowVisible && pillPressHandled(action, state)) return
                gamepadInputHandler.cancelRepeat()
            }
            GamepadAction.NAVIGATE_LEFT -> {
                if (state.activePillIndex() != null && pillPressHandled(action, state)) return

                if (state.isInSubItem) {
                    gamepadInputHandler.cancelRepeat()
                    if (!state.leftBacksOut) return
                    menuSound.play(MenuSound.BACK)
                    backOutOfDrill(state)
                    return
                }

                if (state.onLastPlayedHome && !state.recentRailVisible) {
                    menuSound.play(MenuSound.SYSTEM_BROWSE)
                    _uiState.update { it.copy(recentRailVisible = true) }
                    return
                }
                if (state.pillRowVisible && pillPressHandled(action, state)) return
                val next = state.stepToReachableCategory(-1)
                if (next != state.selectedCategoryIndex) onCategorySelected(next)
                else gamepadInputHandler.cancelRepeat()
            }
            GamepadAction.NAVIGATE_RIGHT -> {
                if (state.activePillIndex() != null && pillPressHandled(action, state)) return

                if (state.onLastPlayedHome && state.recentRailVisible) {
                    _uiState.update { it.copy(recentRailVisible = false) }
                }

                if (state.pillRowVisible && pillPressHandled(action, state)) return
                if (state.isInSubItem) { gamepadInputHandler.cancelRepeat(); return }
                val next = state.stepToReachableCategory(+1)
                if (next != state.selectedCategoryIndex) onCategorySelected(next)
                else gamepadInputHandler.cancelRepeat()
            }
            GamepadAction.SELECT     -> {
                val pill = state.activePillIndex()?.let { state.focusedPills().getOrNull(it) }
                if (pill != null) onPillActivated(pill.id) else onItemSelected(state.selectedItemIndex)
            }
            GamepadAction.BACK       -> {
                menuSound.play(MenuSound.BACK)

                if (state.activePillIndex() != null) {
                    _uiState.update { it.copy(pillCursor = null) }
                    return
                }

                if (state.onLastPlayedHome && state.recentRailVisible) {
                    _uiState.update { it.copy(recentRailVisible = false) }
                    return
                }

                if (!backOutOfDrill(state)) onOpenAppDrawer()
            }

            GamepadAction.OPEN_CONTEXT_MENU -> openContextMenuForFocusedItem()

            GamepadAction.HOME          -> toggleNotifications()

            GamepadAction.CHANGE_SORT ->
                if (state.onLastPlayedHome) cycleRecentFilter() else cycleSort()

            GamepadAction.OPEN_SEARCH -> openSearch(SearchScope.ALL)

            GamepadAction.PREV_CATEGORY -> stepHoverPanelPage(-1)
            GamepadAction.NEXT_CATEGORY -> stepHoverPanelPage(+1)
        }
    }

    fun onPanelPageTapped(page: DetailPanelPage) = _uiState.update {
        it.copy(panelPage = page, panelPageGameId = it.hoverPanelItem?.gameId)
    }

    private fun stepHoverPanelPage(delta: Int) = _uiState.update { s ->
        val content = s.hoverPanelContent ?: return@update s

        if (!s.panelStripOpen) {
            return@update s.copy(
                panelPage = DetailPanelPage.LOGO,
                panelPageGameId = s.hoverPanelItem?.gameId,
            )
        }

        if (delta < 0 && s.effectivePanelPage == DetailPanelPage.LOGO) {
            return@update s.copy(panelPageGameId = null)
        }
        s.copy(

            panelPage = stepPanelPage(s.effectivePanelPage, content.pages, delta),
            panelPageGameId = s.hoverPanelItem?.gameId,
        )
    }

    private fun openPlatformContextMenu(platformId: String) {
        val card = enabledCards.firstOrNull { it.platformId == platformId } ?: return
        val items = platformContextMenuItems(
            platformId = platformId,
            pinned = card.pinned,
            iconDisplayLabel = platformIconDisplayLabel(platformId),
        )

        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(state = MenuState(title = card.displayName, rows = items), platformId = platformId)
        )}
    }

    private fun openAllGamesContextMenu() {
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(state = MenuState(title = "All Games", rows = allGamesContextMenuItems(it.iconDisplayMode.label)), isAllGames = true)
        )}
    }

    private fun platformIconDisplayLabel(platformId: String): String {
        val state = _uiState.value
        val override = state.iconDisplayModeByPlatform[platformId]
        return override?.label ?: "Global: ${state.iconDisplayMode.label}"
    }

    private fun openPlatformIconDisplayPickerMenu(platformId: String) {
        val state = _uiState.value
        val override = state.iconDisplayModeByPlatform[platformId]
        val items = buildList {
            add(XMBContextMenuItem(
                action = "picondisp_default",
                label   = "Use Global Setting (${state.iconDisplayMode.label})",
                checked = override == null,
            ))
            IconDisplayMode.entries.forEach { mode ->
                add(XMBContextMenuItem("picondisp_${mode.name}", mode.label, checked = override == mode))
            }
        }
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(state = MenuState(title = "Icon Display", rows = items), platformId = platformId)
        )}
    }

    private fun openGlobalIconDisplayPickerMenu() {
        val current = _uiState.value.iconDisplayMode
        val items = IconDisplayMode.entries.map { mode ->
            XMBContextMenuItem("gicondisp_${mode.name}", mode.label, checked = mode == current)
        }
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(state = MenuState(title = "Icon Display", rows = items), isAllGames = true)
        )}
    }

    private fun openGameContextMenu(item: XMBItem) {
        val gameId = item.gameId
        if (gameId == null) {
            openGameContextMenuCore(item, discCount = 0)
            return
        }

        viewModelScope.launch {
            val game = runCatching { gameRepository.getById(gameId) }.getOrNull()
            val discCount = runCatching {
                game?.discSetKey?.let { gameRepository.getDiscSetMembers(it).size } ?: 0
            }.getOrDefault(0)

            openGameContextMenuCore(item, discCount, onRecentShelf = game?.lastPlayedAt != null)
        }
    }

    private fun openGameContextMenuCore(item: XMBItem, discCount: Int, onRecentShelf: Boolean = false) {
        val state = _uiState.value
        val currentCat = currentCategory()
        val inGamingCategory = currentCat?.isGamingCategory == true
        val items = gameContextMenuItems(
            item = item,
            state = state,
            discCount = discCount,
            onRecentShelf = onRecentShelf,
            hideLocation = currentHideLocation(),
        )
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(state = MenuState(title = item.title, rows = items), gameId = item.gameId, packageName = item.packageName, shortcutId = item.shortcutId, launchIntentUri = item.launchIntentUri, categoryContext = if (inGamingCategory) currentCat.id else null, primaryId = "play")
        )}
    }

    private fun openCollectionPicker(gameId: Long, selectIndex: Int? = 0) {
        viewModelScope.launch {
            val collections = collectionRepository.getAll()
            val memberOf = collectionRepository.getCollectionIdsForGame(gameId).toSet()
            val items = buildList {
                collections.forEach { c ->
                    add(XMBContextMenuItem(
                        action = "col_${c.id}",
                        label   = c.name,
                        checked = c.id in memberOf,
                    ))
                }
                add(XMBContextMenuItem("col_new", "Create New Collection"))
            }
            _uiState.update { it.copy(
                activeContextMenu = XMBContextMenu(state = MenuState(title = "Add to Collection", rows = items, selectedIndex = selectIndex?.coerceIn(0, items.lastIndex.coerceAtLeast(0))), gameId = gameId, collectionGameId = gameId)
            )}
        }
    }

    private fun openAppContextMenu(item: XMBItem, categoryIdOverride: String? = null) {
        val pkg = item.packageName ?: return
        val categoryId = categoryIdOverride ?: currentCategory()?.id

        val items = appContextMenuItems(_uiState.value, categoryId, onRecentShelf = item.id.startsWith(RECENT_APP_ID_PREFIX))
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(state = MenuState(title = item.title, rows = items), gameId = item.gameId, packageName = pkg, categoryContext = categoryId)
        )}
    }

    private fun openCollectionRowContextMenu(collectionId: Long) {
        val collection = _uiState.value.collections.firstOrNull { it.id == collectionId } ?: return

        val hasOtherCategory = collectionMoveTargets(collection.categoryId).isNotEmpty()
        val items = collectionRowContextMenuItems(
            isPinned = collection.isPinned,
            hasOtherCategory = hasOtherCategory,
        )
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(state = MenuState(title = collection.name, rows = items), collectionRowId = collectionId)
        )}
    }

    private fun collectionMoveTargets(fromCategoryId: String): List<Category> {
        val fromIsGaming = _uiState.value.categories.firstOrNull { it.id == fromCategoryId }?.isGamingCategory
            ?: (fromCategoryId == BuiltInCategory.GAMES)
        return _uiState.value.categories.filter { cat ->
            cat.id != fromCategoryId && categoryShowsCollections(cat) && cat.isGamingCategory == fromIsGaming
        }
    }

    private fun openCollectionCategoryPicker(collectionId: Long, fromCategoryId: String) {
        val items = collectionMoveTargets(fromCategoryId)
            .map { cat -> XMBContextMenuItem("movecol_${cat.id}", cat.name) }
        if (items.isEmpty()) return
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(state = MenuState(title = "Move Collection To", rows = items), collectionRowId = collectionId)
        )}
    }

    private fun openCategoryPicker(pkg: String, fromCategory: String?, action: String) {
        val items = _uiState.value.categories.map { cat ->
            XMBContextMenuItem("pick_${cat.id}", cat.name)
        }
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(state = MenuState(title = if (action == "move") "Move To…" else "Add To…", rows = items), packageName = pkg, categoryContext = fromCategory, pendingAppAction = action)
        )}
    }

    private fun shiftContextMenu(delta: Int) {
        val state = _uiState.value
        val menu = state.activeContextMenu ?: return

        val rows = state.menuRows()
        if (rows.isEmpty()) return

        val current = menu.selectedIndex ?: return run {
            val entry = if (delta > 0) 0 else rows.lastIndex
            _uiState.update { it.copy(activeContextMenu = menu.withSelected(entry)) }
        }
        val next = (current + delta).coerceIn(0, rows.size - 1)
        _uiState.update { it.copy(activeContextMenu = menu.withSelected(next)) }
    }

    private fun activateContextMenuItem(itemId: String) {
        val state  = _uiState.value
        val menu   = state.activeContextMenu ?: return

        if (itemId.startsWith("cat_") && menu.gameId != null && menu.categoryContext != null && menu.pendingAppAction != null) {
            val gameId = menu.gameId
            val fromCategory = menu.categoryContext
            val toCategory = itemId.removePrefix("cat_")
            val action = menu.pendingAppAction
            closeContextMenu()

            appAction {
                when (action) {
                    "move" -> gameCategoryRepository.moveGameToCategory(gameId, fromCategory, toCategory)
                    "add"  -> gameCategoryRepository.addGameToCategory(gameId, toCategory)
                }
                if (currentCategory()?.id == fromCategory) {
                    loadItemsForCategory(currentCategory())
                }
            }
            return
        }

        if (menu.isAddMenu) {
            val row = currentAddActions().firstOrNull { it.id == itemId }
            closeContextMenu()
            if (row != null) dispatchCategorySelection(row)
            return
        }

        if (menu.collectionGameId != null) {
            val gameId = menu.collectionGameId
            val keepIndex = menu.selectedIndex
            when {
                itemId == "col_new" -> {
                    closeContextMenu()
                    promptCreateCollection(forGameId = gameId)
                }
                itemId.startsWith("col_") -> {
                    val collectionId = itemId.removePrefix("col_").toLongOrNull() ?: return
                    viewModelScope.launch {
                        collectionRepository.toggleGame(collectionId, gameId)

                        openCollectionPicker(gameId, keepIndex)
                    }
                }
            }
            return
        }

        if (menu.videoPlaylistPickerVideoId != null) {
            val videoId = menu.videoPlaylistPickerVideoId
            val keepIndex = menu.selectedIndex
            when {
                itemId == "vpl_new" -> {
                    closeContextMenu()
                    promptCreateVideoPlaylist(forVideoId = videoId)
                }
                itemId.startsWith("vpl_") -> {
                    val playlistId = itemId.removePrefix("vpl_").toLongOrNull() ?: return
                    viewModelScope.launch {
                        videoRepository.toggleVideoInPlaylist(playlistId, videoId)
                        openVideoPlaylistPicker(videoId, keepIndex)
                    }
                }
            }
            return
        }

        if (menu.playlistPickerTrackId != null) {
            val trackId = menu.playlistPickerTrackId
            val keepIndex = menu.selectedIndex
            when {
                itemId == "pl_new" -> {
                    closeContextMenu()
                    promptCreatePlaylist(forTrackId = trackId)
                }
                itemId.startsWith("pl_") -> {
                    val playlistId = itemId.removePrefix("pl_").toLongOrNull() ?: return
                    viewModelScope.launch {
                        musicRepository.toggleTrackInPlaylist(playlistId, trackId)

                        openPlaylistPicker(trackId, keepIndex)
                    }
                }
            }
            return
        }

        closeContextMenu()

        if (menu.videoFileId != null) {
            handleVideoFileAction(menu.videoFileId, itemId)
            return
        }
        if (menu.bookFileId != null) {
            handleBookAction(menu.bookFileId, itemId)
            return
        }
        if (menu.videoLibraryId != null) {
            handleVideoLibraryAction(menu.videoLibraryId, itemId)
            return
        }
        if (menu.photoFileId != null) {
            handlePhotoFileAction(menu.photoFileId, itemId)
            return
        }
        if (menu.photoLibraryId != null) {
            handlePhotoLibraryAction(menu.photoLibraryId, itemId)
            return
        }
        if (menu.videoPlaylistId != null) {
            handleVideoPlaylistRowAction(menu.videoPlaylistId, itemId)
            return
        }

        if (menu.playlistId != null && menu.musicTrackId == null) {
            handlePlaylistRowAction(menu.playlistId, itemId)
            return
        }

        if (menu.collectionRowId != null) {
            val collectionId = menu.collectionRowId
            when {
                itemId.startsWith("movecol_") -> {
                    val toCategory = itemId.removePrefix("movecol_")
                    appAction { collectionRepository.setCategory(collectionId, toCategory) }
                }
                itemId == "open_collection"   -> openCollectionFolder(collectionId)
                itemId == "rename_collection" -> promptRenameCollection(collectionId)
                itemId == "move_collection_category" -> {
                    val from = _uiState.value.collections.firstOrNull { it.id == collectionId }?.categoryId
                        ?: BuiltInCategory.GAMES
                    openCollectionCategoryPicker(collectionId, from)
                }
                itemId == "pin_collection"   -> appAction { collectionRepository.setPinned(collectionId, true) }
                itemId == "unpin_collection" -> appAction { collectionRepository.setPinned(collectionId, false) }
                itemId == "manage_collections" -> _uiState.update { it.copy(activeSettingsScreen = "settings_categories") }
                itemId == "delete_collection"  -> appAction {
                    collectionRepository.delete(collectionId)
                    if (_uiState.value.selectedCollectionId == collectionId) closePlatformFolder()
                }
            }
            return
        }

        when {
            menu.musicTrackId == MUSIC_PLAYER_MENU_MARKER -> when (itemId) {
                "music_background" -> musicPlayInBackground()
                "music_playpause"  -> musicPlayPause()
                "music_close"      -> stopAndCloseMusicPlayer()
            }
            menu.musicTrackId != null -> handleMusicTrackAction(menu.musicTrackId, itemId, menu.playlistId)
            menu.musicFolderId != null -> handleMusicFolderAction(menu.musicFolderId, itemId)
            menu.isAllGames -> if (itemId.startsWith("gicondisp_")) {
                IconDisplayMode.fromName(itemId.removePrefix("gicondisp_"))?.let { mode ->
                    viewModelScope.launch { iconDisplayPreferences.setMode(mode) }
                }
            } else when (itemId) {
                "library_manager" -> _uiState.update { it.copy(activeSettingsScreen = "settings_library") }
                "import_pc_games" -> _uiState.update { it.copy(activeSettingsScreen = "settings_import_pc") }
                "icon_display_global" -> openGlobalIconDisplayPickerMenu()
            }
            menu.platformId != null -> if (itemId.startsWith("picondisp_")) {
                val choice = itemId.removePrefix("picondisp_")
                val pid = menu.platformId
                viewModelScope.launch {
                    iconDisplayPreferences.setPlatformMode(pid, IconDisplayMode.fromName(choice))
                }
            } else when (itemId) {
                "find_games"       -> openAppPicker(AppPickerTarget.AndroidGames(menu.platformId), "Find Games")
                "import_pc_games"  -> _uiState.update { it.copy(activeSettingsScreen = "settings_import_pc") }
                "icon_display_platform" -> openPlatformIconDisplayPickerMenu(menu.platformId)
                "scan_roms"        -> scanCard(menu.platformId)
                "scrape_missing_artwork" -> scrapeMissingArtworkForPlatform(menu.platformId)
                "update_metadata"        -> updatePlatformMetadata(menu.platformId)
                "pin"              -> setCardPinned(menu.platformId, true)
                "unpin"            -> setCardPinned(menu.platformId, false)
                "library_manager"  -> _uiState.update { it.copy(activeSettingsScreen = "settings_library") }
                "hide"             -> hideCard(menu.platformId)
                "remove"           -> removeCard(menu.platformId)
            }
            menu.gameId != null -> if (itemId.startsWith("emu_pick_")) {
                val gid = menu.gameId
                val choice = itemId.removePrefix("emu_pick_")
                appAction {
                    gameRepository.setPreferredEmulator(gid, choice.takeIf { it != "default" })
                }
            } else if (itemId.startsWith("detail_")) {
                val gid = menu.gameId
                when (val what = itemId.removePrefix("detail_")) {
                    "title" -> viewModelScope.launch {
                        val game = gameRepository.getById(gid) ?: return@launch
                        closeContextMenu()
                        _uiState.update { it.copy(collectionNameDialog = CollectionNameDialogState(
                            title = "Edit Title",
                            initialText = game.displayTitle,
                            editTitleGameId = gid,
                            placeholder = "Leave blank to use the scanned name",
                        ))}
                    }
                    "note" -> viewModelScope.launch {
                        val game = gameRepository.getById(gid) ?: return@launch
                        closeContextMenu()
                        _uiState.update { it.copy(collectionNameDialog = CollectionNameDialogState(
                            title = "Edit Note",
                            initialText = game.userNote.orEmpty(),
                            editNoteGameId = gid,
                            placeholder = "Anything you want to remember about this game",
                        ))}
                    }
                    "open" -> _uiState.update {
                        it.copy(activeGameId = gid, activeGameAutoLaunch = false, activeGameAction = null)
                    }

                    "ARTWORK"  -> openArtworkStudio(gid)
                    "MANUAL"   -> openManualFor(gid)
                    "METADATA" -> openMetadataPreviewFor(gid)
                    "REFRESH"  -> fetchArtworkFor(gid)
                    else -> Timber.w("Details row '$what' has no handler")
                }
            } else if (itemId.startsWith("pstate_")) {
                val gid = menu.gameId
                val choice = itemId.removePrefix("pstate_")
                appAction {
                    gameRepository.setPlayState(gid, PlayState.fromName(choice))
                }
            } else if (itemId.startsWith("icondisp_")) {
                val gid = menu.gameId
                val choice = itemId.removePrefix("icondisp_")
                appAction {
                    gameRepository.setIconDisplayMode(gid, IconDisplayMode.fromName(choice)?.name)
                }
            } else if (itemId.startsWith("disc_pick_")) {
                val discId = itemId.removePrefix("disc_pick_").toLongOrNull()
                if (discId != null) {
                    menuSound.play(MenuSound.SELECT)
                    appAction { gameRepository.setPreferredDisc(menu.gameId, discId) }
                }
            } else when (itemId) {
                "game_details"           -> openGameDetailsMenu(menu.gameId)

                "play"                   -> launchGameDirectly(menu.gameId)
                "choose_disc"             -> openDiscPickerMenu(menu.gameId)
                "export_game"            -> exportGameFromMenu(menu.gameId)
                "edit_app"               -> openAppDetail(menu.gameId, menu.packageName ?: return)
                "favorite"               -> toggleGameFavorite(menu.gameId, true)
                "unfavorite"             -> toggleGameFavorite(menu.gameId, false)

                "remove_from_recent"     -> {
                    val gid = menu.gameId
                    appAction { gameRepository.clearLastPlayed(gid) }
                }
                "add_to_collection"      -> openCollectionPicker(menu.gameId)
                "remove_from_collection" -> {
                    val gid = menu.gameId
                    _uiState.value.selectedCollectionId?.let { cid ->
                        appAction { collectionRepository.removeGame(cid, gid) }
                    }
                }
                "manage_collections"     -> _uiState.update { it.copy(activeSettingsScreen = "settings_categories") }
                "add_category"           -> menu.categoryContext?.let { openGameCategoryPicker(menu.gameId, it, "add") }
                "move_category"          -> menu.categoryContext?.let { openGameCategoryPicker(menu.gameId, it, "move") }
                "remove_category"        -> menu.categoryContext?.let { cat ->
                    val gid = menu.gameId
                    appAction {
                        gameCategoryRepository.removeGameFromCategory(gid, cat)
                        loadItemsForCategory(currentCategory())
                    }
                }
                "pin_category"           -> menu.categoryContext?.let { cat ->
                    val gid = menu.gameId
                    appAction {
                        gameCategoryRepository.pinGameInCategory(gid, cat, true)
                        loadItemsForCategory(currentCategory())
                    }
                }
                "unpin_category"         -> menu.categoryContext?.let { cat ->
                    val gid = menu.gameId
                    appAction {
                        gameCategoryRepository.pinGameInCategory(gid, cat, false)
                        loadItemsForCategory(currentCategory())
                    }
                }
                "file_location"          -> showGameFileLocation(menu.gameId)
                "change_emulator"        -> openEmulatorPickerMenu(menu.gameId)
                "icon_display"           -> openIconDisplayPickerMenu(menu.gameId)
                "play_state"             -> openPlayStatePickerMenu(menu.gameId)

                "remove_game", "remove_missing" -> {
                    val gid = menu.gameId
                    appAction { removeGameFromLibrary(gid) }
                }
                "hide_here"              -> currentHideLocation()?.let { (type, id, label) ->
                    persistHide(HiddenPlacement.gameKey(menu.gameId), menu.title, type, id, label)
                }
                "remove_app"             -> {
                    val gid = menu.gameId
                    appAction {
                        gameRepository.delete(gid)
                        memoryCardRepository.recountGames(ANDROID_PLATFORM_ID)
                    }
                }

                "unmark_game"            -> {
                    val gid = menu.gameId
                    appAction {
                        gameRepository.getById(gid)?.let { g ->
                            gameRepository.upsert(g.copy(
                                platformId  = APP_SHORTCUT_PLATFORM_ID,
                                contentType = GameContentType.ANDROID_APP,
                            ))
                        }
                        memoryCardRepository.recountGames(ANDROID_PLATFORM_ID)
                        loadItemsForCategory(currentCategory())
                    }
                }
            }
            menu.packageName != null -> {
                val pkg = menu.packageName
                if (itemId.startsWith("pick_")) {
                    val targetCategory = itemId.removePrefix("pick_")
                    when (menu.pendingAppAction) {
                        "move" -> appAction { appCategoryRepository.moveToCategory(pkg, targetCategory) }
                        "add"  -> appAction { appCategoryRepository.addToCategory(pkg, targetCategory) }
                    }
                } else when (itemId) {
                    "launch"    -> launchAppWithDisc(pkg, selectedItemArt())
                    "edit_app"  -> openAppDetail(menu.gameId, pkg)

                    "mark_game" -> appAction {
                        val existing = gameRepository.getAppEntry(pkg)
                        if (existing == null) {
                            gameRepository.upsert(Game(
                                title         = menu.title,
                                platformId    = ANDROID_PLATFORM_ID,
                                packageName   = pkg,
                                isManualEntry = true,
                                contentType   = GameContentType.GAME,
                            ))
                        } else {
                            gameRepository.upsert(existing.copy(
                                platformId  = ANDROID_PLATFORM_ID,
                                contentType = GameContentType.GAME,
                            ))
                        }
                        memoryCardRepository.recountGames(ANDROID_PLATFORM_ID)
                    }
                    "favorite"          -> addAppToFavorites(pkg, menu.title)
                    "add_to_collection" -> addAppToCollection(pkg, menu.title)
                    "move"      -> openCategoryPicker(pkg, menu.categoryContext, "move")
                    "add"       -> openCategoryPicker(pkg, menu.categoryContext, "add")
                    "remove"    -> menu.categoryContext?.let { cat -> appAction { appCategoryRepository.removeFromCategory(pkg, cat) } }
                    "pin"       -> menu.categoryContext?.let { cat -> appAction { appCategoryRepository.pinToCategory(pkg, cat) } }
                    "hide_from_category" -> menu.categoryContext?.let { cat ->
                        persistHide(HiddenPlacement.appKey(pkg), menu.title, HideLocationType.CATEGORY, cat, categoryDisplayName(cat))
                    }

                    "remove_from_recent" -> persistHide(
                        HiddenPlacement.appKey(pkg), menu.title, HideLocationType.RECENTS, "", "Recently Played",
                    )
                    "hide_everywhere" -> appAction { appCategoryRepository.setHidden(pkg, true) }
                    "rename"    -> _uiState.update {
                        it.copy(renameAppTarget = pkg, renameAppCurrent = menu.title, renameAppText = menu.title)
                    }
                }
            }
        }
    }

    private fun appAction(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun openGameDetailsMenu(gameId: Long) {
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(state = MenuState(title = "Details", rows = listOf(
                XMBContextMenuItem("detail_title", "Edit Title"),
                XMBContextMenuItem("detail_note", "Edit Note"),
                XMBContextMenuItem("detail_ARTWORK", "Artwork"),
                XMBContextMenuItem("detail_METADATA", "Update Metadata"),
                XMBContextMenuItem("detail_MANUAL", "Manual"),
                XMBContextMenuItem("detail_REFRESH", "Refresh Artwork"),
                XMBContextMenuItem("detail_open", "Open Game Details"),
            )), gameId = gameId))}
    }

    private fun openPlayStatePickerMenu(gameId: Long) {
        viewModelScope.launch {
            val game = gameRepository.getById(gameId) ?: return@launch
            val current = PlayState.fromName(game.playState)
            val items = buildList {
                add(XMBContextMenuItem("pstate_none", "Unmarked", checked = current == null))
                PlayState.entries.forEach { state ->
                    add(XMBContextMenuItem("pstate_${state.name}", state.label, checked = current == state))
                }
            }
            _uiState.update { it.copy(activeContextMenu = XMBContextMenu(state = MenuState(title = "Mark As", rows = items), gameId = gameId))}
        }
    }

    private fun openIconDisplayPickerMenu(gameId: Long) {
        viewModelScope.launch {
            val game = gameRepository.getById(gameId) ?: return@launch
            val override = IconDisplayMode.fromName(game.iconDisplayMode)

            val state = _uiState.value
            val inherited = state.iconDisplayModeByPlatform[game.platformId] ?: state.iconDisplayMode
            val items = buildList {
                add(XMBContextMenuItem(
                    action = "icondisp_default",
                    label   = "Use Default (${inherited.label})",
                    checked = override == null,
                ))
                IconDisplayMode.entries.forEach { mode ->
                    add(XMBContextMenuItem("icondisp_${mode.name}", mode.label, checked = override == mode))
                }
            }
            _uiState.update { it.copy(activeContextMenu = XMBContextMenu(state = MenuState(title = "Icon Display", rows = items), gameId = gameId))}
        }
    }

    private fun openEmulatorPickerMenu(gameId: Long) {
        viewModelScope.launch {
            val game = gameRepository.getById(gameId) ?: return@launch
            val profiles = emulatorProfileRepository.getProfilesForPlatform(game.platformId)
            val items = buildList {
                add(XMBContextMenuItem("emu_pick_default", "Use Platform Default"))
                profiles.forEach { add(XMBContextMenuItem("emu_pick_${it.id}", it.name)) }
            }
            _uiState.update { it.copy(activeContextMenu = XMBContextMenu(state = MenuState(title = "Choose Emulator", rows = items), gameId = gameId))}
        }
    }

    private fun openDiscPickerMenu(gameId: Long) {
        viewModelScope.launch {
            val game = gameRepository.getById(gameId) ?: return@launch
            val key = game.discSetKey ?: return@launch
            val members = gameRepository.getDiscSetMembers(key)
            if (members.size <= 1) return@launch
            val preferredDiscId = members.firstOrNull { it.isDiscPrimary }?.id
            val items = members
                .sortedWith(compareBy<Game> { it.discNumber == null }.thenBy { it.discNumber ?: Int.MAX_VALUE }.thenBy { it.id })
                .map { member ->
                    XMBContextMenuItem(
                        action = "disc_pick_${member.id}",
                        label   = member.discNumber?.let { "Disc $it" } ?: "Playlist",
                        checked = member.id == preferredDiscId,
                    )
                }
            _uiState.update { it.copy(activeContextMenu = XMBContextMenu(state = MenuState(title = "Choose Disc", rows = items), gameId = gameId))}
        }
    }

    private suspend fun removeGameFromLibrary(gameId: Long) {
        val game = gameRepository.getById(gameId) ?: return
        gameRepository.delete(gameId)
        memoryCardRepository.recountGames(game.platformId)
        loadItemsForCategory(currentCategory())
    }

    fun onConfirmAppRename(newLabel: String) {
        val pkg = _uiState.value.renameAppTarget ?: return
        viewModelScope.launch {
            appCategoryRepository.rename(pkg, newLabel.ifBlank { null })
            _uiState.update { it.copy(renameAppTarget = null, renameAppCurrent = null) }
        }
    }

    fun onNamePromptTextChanged(text: String) {
        _uiState.update { it.withNamePromptText(text) }
    }

    fun onCancelAppRename() {
        _uiState.update { it.copy(renameAppTarget = null, renameAppCurrent = null) }
    }

    private fun promptCreateCollection(forGameId: Long? = null) {
        _uiState.update { it.copy(
            collectionNameDialog = CollectionNameDialogState(title = "New Collection", forGameId = forGameId)
        )}
    }

    private fun promptRenameCollection(collectionId: Long) {
        val name = _uiState.value.collections.firstOrNull { it.id == collectionId }?.name.orEmpty()
        _uiState.update { it.copy(
            collectionNameDialog = CollectionNameDialogState(
                title = "Rename Collection",
                initialText = name,
                renameCollectionId = collectionId,
            )
        )}
    }

    fun onConfirmCollectionName(name: String) {
        val dialog = _uiState.value.collectionNameDialog ?: return
        _uiState.update { it.copy(collectionNameDialog = null) }
        if (dialog.quickSearch) { runQuickSearch(name); return }

        if (dialog.editTitleGameId != null) {
            viewModelScope.launch {
                gameRepository.updateUserTitleOverride(dialog.editTitleGameId, name.trim().ifBlank { null })

                loadItemsForCategory(currentCategory(), keepCursorOnRow = true)
            }
            return
        }
        if (dialog.editNoteGameId != null) {
            viewModelScope.launch {
                gameRepository.updateNote(dialog.editNoteGameId, name.trim().ifBlank { null })
            }
            return
        }
        if (name.isBlank()) return
        viewModelScope.launch {
            val renameId = dialog.renameCollectionId
            if (renameId != null) {
                collectionRepository.rename(renameId, name)
            } else {
                val id = collectionRepository.create(name, collectionHomeCategoryId())
                dialog.forGameId?.let { collectionRepository.addGame(id, it) }
            }

            if (categoryShowsCollections(currentCategory())) {
                loadItemsForCategory(currentCategory())
            }
        }
    }

    private fun runQuickSearch(text: String) {
        val intent = when (val action = quickSearchActionFor(text)) {
            is QuickSearchAction.None -> return
            is QuickSearchAction.Open ->
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(action.url))
            is QuickSearchAction.Search ->
                android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH)
                    .putExtra(android.app.SearchManager.QUERY, action.query)
        }.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        menuSound.play(MenuSound.LAUNCH)
        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Timber.w(e, "No app can handle Quick Search")
            _uiState.update {
                it.copy(
                    infoDialog = InfoDialogState(
                        title = "Nothing to search with",
                        message = "No app on this device can open a web search. Install a browser, " +
                            "then try again.",
                    )
                )
            }
        }
    }

    fun onCancelCollectionName() {
        _uiState.update { it.copy(collectionNameDialog = null) }
    }

    private fun showGameFileLocation(gameId: Long) {
        viewModelScope.launch {
            val game = gameRepository.getById(gameId) ?: return@launch
            val location = game.romPath
                ?: game.packageName?.let { "Package: $it" }
                ?: "No file location on record"
            _uiState.update {
                it.copy(infoDialog = InfoDialogState(title = game.displayTitle, message = location))
            }
        }
    }

    fun dismissInfoDialog() = _uiState.update { it.copy(infoDialog = null) }

    private fun exportGameFromMenu(gameId: Long) {
        viewModelScope.launch {
            val game = gameRepository.getById(gameId) ?: return@launch
            val report = runCatching { pcGameExporter.exportGame(gameId) }
                .onFailure { Timber.e(it, "Export Game failed for gameId=$gameId") }
                .getOrNull()
            _uiState.update {
                it.copy(infoDialog = InfoDialogState(title = game.displayTitle, message = report?.message ?: "Export failed — see the log."))
            }
        }
    }

    private fun normalizePcTitleKey(title: String): String =
        title.lowercase().filter { it.isLetterOrDigit() }

    private fun openContextMenuForFocusedItem() {
        val state = _uiState.value
        val item = state.currentItems.getOrNull(state.selectedItemIndex)
        when {
            item != null && openMusicContextMenu(item) -> Unit
            item != null && openVideoContextMenu(item) -> Unit
            item != null && openBookContextMenu(item) -> Unit
            item != null && openPhotoContextMenu(item) -> Unit
            item?.gameId != null -> openGameContextMenu(item)
            item?.collectionId != null && item.type == XMBItemType.COLLECTION -> openCollectionRowContextMenu(item.collectionId)
            item?.type == XMBItemType.ALL_GAMES -> openAllGamesContextMenu()
            item?.platformId != null -> openPlatformContextMenu(item.platformId)
            item?.packageName != null -> openAppContextMenu(item)
        }
    }

    private fun pillPressHandled(action: GamepadAction, state: XMBUiState): Boolean {
        val pills = state.focusedPills()
        return when (val nav = pillNav(action, state.activePillIndex(), pills.size)) {
            is PillNav.Move -> {
                val item = state.currentItems.getOrNull(state.selectedItemIndex) ?: return false
                menuSound.play(MenuSound.SCROLL)
                _uiState.update { it.copy(pillCursor = PillCursor(item.id, nav.index)) }
                true
            }
            PillNav.ExitAndPass -> {
                _uiState.update { it.copy(pillCursor = null) }
                false
            }
            PillNav.Pass -> false
        }
    }

    private fun observeAndroidNotices() {
        viewModelScope.launch {
            AndroidNotifications.active.collect { notices ->
                _uiState.update { it.copy(androidNotices = notices) }
            }
        }
    }

    private fun observeShelfCounts() {
        viewModelScope.launch {
            val marks = PlayState.entries

            combine(
                marks.map { gameRepository.observeByPlayState(it) } +
                    gameRepository.observeRecentlyAdded() +
                    gameRepository.observeFavorites(),
            ) { lists ->
                val byState = marks.mapIndexed { i, state -> state to lists[i] }.toMap()
                Triple(byState, lists[marks.size], lists[marks.size + 1])
            }.collect { (byState, recentlyAdded, favorites) ->
                _uiState.update { state ->
                    state.copy(
                        playStateCounts = byState.mapValues { (_, games) -> games.size },
                        recentlyAddedCount = recentlyAdded.size,

                        shelfFanCovers = buildMap {
                            put(SHELF_FAVORITES_ID, fanCoversOf(favorites))
                            put(SHELF_RECENT_ID, fanCoversOf(recentlyAdded))
                            byState.forEach { (mark, games) ->
                                put("$SHELF_MARKED_PREFIX${mark.name}", fanCoversOf(games))
                            }
                        },
                    )
                }
            }
        }
    }

    private fun observeResumeGame() {
        viewModelScope.launch {
            gameRepository.observeRecentlyPlayed(1).collect { games ->
                _uiState.update { it.copy(resumeGame = games.firstOrNull()) }
            }
        }
    }

    fun toggleNotifications() {
        menuSound.play(if (_uiState.value.notificationsOpen) MenuSound.BACK else MenuSound.SYSTEM_BROWSE)
        _uiState.update { it.copy(notificationsOpen = !it.notificationsOpen, noticeCursor = 0) }
    }

    fun closeNotifications() {
        _uiState.update { it.copy(notificationsOpen = false) }
    }

    private fun moveNoticeCursor(delta: Int) {
        val rows = _uiState.value.noticeFocusables
        if (rows.isEmpty()) return
        val next = (_uiState.value.noticeCursor + delta).coerceIn(0, rows.lastIndex)
        if (next == _uiState.value.noticeCursor) {
            gamepadInputHandler.cancelRepeat()
            return
        }
        menuSound.play(MenuSound.SCROLL)
        _uiState.update { it.copy(noticeCursor = next) }
    }

    fun activateFocusedNotice() {
        when (val focus = _uiState.value.focusedNotice) {
            null -> Unit

            NoticeFocus.Media -> if (_uiState.value.musicPlayback.track != null) {
                musicPlayer.playPause()
            } else {
                _uiState.value.resumeGame?.let { game ->
                    closeNotifications()
                    launchGameDirectly(game.id)
                }
            }
            is NoticeFocus.Notice -> {
                menuSound.play(MenuSound.SELECT)

                if (!AndroidNotifications.open(focus.key)) {
                    Timber.i("Notification ${focus.key} had nothing to open")
                }
                closeNotifications()
            }
        }
    }

    fun dismissFocusedNotice() {
        val focus = _uiState.value.focusedNotice as? NoticeFocus.Notice ?: return
        val notice = _uiState.value.androidNotices.firstOrNull { it.key == focus.key } ?: return

        if (!notice.canDismiss) return
        menuSound.play(MenuSound.BACK)
        AndroidNotifications.dismiss(focus.key)
    }

    fun onNoticeTapped(key: String) {
        val rows = _uiState.value.noticeFocusables
        val index = rows.indexOfFirst { it is NoticeFocus.Notice && it.key == key }
        if (index < 0) return
        _uiState.update { it.copy(noticeCursor = index) }
        activateFocusedNotice()
    }

    fun onNoticeDismissTapped(key: String) {
        val rows = _uiState.value.noticeFocusables
        val index = rows.indexOfFirst { it is NoticeFocus.Notice && it.key == key }
        if (index < 0) return
        _uiState.update { it.copy(noticeCursor = index) }
        dismissFocusedNotice()
    }

    fun onNoticeMediaPrimary() {
        val rows = _uiState.value.noticeFocusables
        val index = rows.indexOfFirst { it == NoticeFocus.Media }
        if (index < 0) return
        _uiState.update { it.copy(noticeCursor = index) }
        activateFocusedNotice()
    }

    fun onNoticeMediaPlayPause() = musicPlayer.playPause()
    fun onNoticeMediaNext() = musicPlayer.next()
    fun onNoticeMediaPrev() = musicPlayer.prev()

    private val MENU_RAISE_TIMEOUT_MS = 500L

    fun onPillActivated(pillId: String) {
        viewModelScope.launch {
            openContextMenuForFocusedItem()

            val menu = withTimeoutOrNull(MENU_RAISE_TIMEOUT_MS) {
                uiState.first { it.activeContextMenu != null }.activeContextMenu
            }
            if (menu == null) {
                Timber.w("Pill '$pillId' pressed on a row that raised no menu")
                return@launch
            }

            if (menu.items.none { it.action == pillId }) {
                Timber.w("Pill '$pillId' is not offered by the focused row's menu")
                closeContextMenu()
                return@launch
            }
            activateContextMenuItem(pillId)
        }
    }

    fun onContextMenuItemActivatedAt(index: Int) {
        val state = _uiState.value
        val menu = state.activeContextMenu ?: return

        when (val chosen = state.menuWithPills()?.chose(index)) {
            is MenuSelect.Replace -> _uiState.update { it.copy(activeContextMenu = menu.copy(state = chosen.state)) }
            is MenuSelect.Run -> {
                _uiState.update { it.copy(activeContextMenu = menu.withSelected(index)) }
                activateContextMenuItem(chosen.action)
            }
            else -> Unit
        }
    }

    fun closeContextMenu() {
        _uiState.update { it.copy(activeContextMenu = null) }
    }

    fun popContextMenu() {
        _uiState.update { s ->
            val menu = s.activeContextMenu ?: return@update s
            s.copy(activeContextMenu = menu.state.parent?.let { menu.copy(state = it) })
        }
    }

    private fun openAppPicker(target: AppPickerTarget, title: String) {
        viewModelScope.launch {
            val installed = appCategoryRepository.allInstalledApps()

            val entries = installed.map {
                AppPickerEntry(packageName = it.packageName, label = it.label, icon = it.icon)
            }
            val membership: Set<String> = when (target) {
                is AppPickerTarget.AndroidGames ->
                    gameRepository.observeByPlatform(target.platformId).first()
                        .mapNotNull { it.packageName }
                        .toSet()
                is AppPickerTarget.CategoryShortcuts ->
                    appCategoryRepository.packagesIn(target.categoryId)
            }
            _uiState.update {
                it.copy(appPicker = AppPickerState(
                    title           = title,
                    target          = target,
                    apps            = entries,
                    selected        = membership,
                    initialSelected = membership,
                ))
            }
        }
    }

    fun onAppPickerColumnsMeasured(columns: Int) {
        if (columns <= 0) return
        _uiState.update {
            val picker = it.appPicker ?: return@update it
            if (picker.columns == columns) it else it.copy(appPicker = picker.copy(columns = columns))
        }
    }

    fun onAppPickerTileTapped(index: Int) {
        markTouchInput()

        if (_uiState.value.appPicker?.confirmingRemovals == true) return
        _uiState.update {
            val picker = it.appPicker ?: return@update it
            val visible = picker.visibleApps()
            val app = visible.getOrNull(index) ?: return@update it
            it.copy(appPicker = picker.copy(focusedIndex = index, usingTouch = true)
                .toggle(app.packageName))
        }
    }

    fun onAppPickerTouchBrowse(index: Int) {
        markTouchInput()
        if (_uiState.value.appPicker?.confirmingRemovals == true) return
        _uiState.update {
            val picker = it.appPicker ?: return@update it
            val lastIndex = (picker.visibleApps().size - 1).coerceAtLeast(0)
            it.copy(appPicker = picker.copy(
                focusedIndex = index.coerceIn(0, lastIndex),
                usingTouch = true,
            ))
        }
    }

    fun onAppPickerHeaderBack() {
        markTouchInput()
        handleAppPickerBack()
    }

    fun onAppPickerConfirmRemoval() {
        markTouchInput()
        commitAppPicker()
    }

    fun onAppPickerCancelRemoval() {
        markTouchInput()
        cancelConfirm()
    }

    fun onAppPickerApply() {
        markTouchInput()
        requestApplyAppPicker()
    }

    fun onAppPickerSearchToggle(active: Boolean) {
        markTouchInput()
        _uiState.update {
            val picker = it.appPicker ?: return@update it
            it.copy(appPicker = (if (active) picker.copy(searchActive = true) else closeAppPickerSearch(picker)).clampFocus())
        }
    }

    fun onAppPickerQueryChange(query: String) {
        _uiState.update {
            val picker = it.appPicker ?: return@update it

            it.copy(appPicker = picker.copy(query = query).clampFocus())
        }
    }

    private fun closeAppPickerSearch(picker: AppPickerState): AppPickerState =
        picker.copy(searchActive = false, query = "")

    fun onAppPickerSearchDone() {
    }

    private fun moveAppPicker(action: GamepadAction) {
        _uiState.update { state ->
            val picker = state.appPicker ?: return@update state

            state.copy(appPicker = if (picker.confirmingRemovals) picker.moveConfirm(action) else picker.move(action))
        }
    }

    private fun toggleFocusedApp() {
        _uiState.update {
            val picker = it.appPicker ?: return@update it
            val app = picker.visibleApps().getOrNull(picker.focusedIndex) ?: return@update it
            it.copy(appPicker = picker.toggle(app.packageName))
        }
    }

    private fun cancelConfirm() {
        _uiState.update {
            val picker = it.appPicker ?: return@update it
            it.copy(appPicker = picker.cancelConfirm())
        }
    }

    fun closeAppPicker() {
        _uiState.update { it.copy(appPicker = null) }
    }

    private fun requestApplyAppPicker() {
        val picker = _uiState.value.appPicker ?: return
        val adds = picker.pendingAdds()
        val removals = picker.pendingRemovals()
        if (adds.isEmpty() && removals.isEmpty()) {
            closeAppPicker()
            return
        }
        if (removals.isNotEmpty() && !picker.confirmingRemovals) {
            _uiState.update { state ->
                state.copy(appPicker = state.appPicker?.openConfirm())
            }
            return
        }
        commitAppPicker()
    }

    private fun commitAppPicker() {
        val picker = _uiState.value.appPicker ?: return
        val adds = picker.pendingAdds()
        val removals = picker.pendingRemovals()
        if (adds.isEmpty() && removals.isEmpty()) {
            closeAppPicker()
            return
        }

        menuSound.play(MenuSound.CONFIRM)
        val target = picker.target
        closeAppPicker()

        viewModelScope.launch {
            when (target) {
                is AppPickerTarget.AndroidGames -> {
                    if (adds.isNotEmpty()) importAndroidGames(target.platformId, adds)
                    if (removals.isNotEmpty()) removeAndroidGames(target.platformId, removals)

                    memoryCardRepository.recountGames(target.platformId)
                }
                is AppPickerTarget.CategoryShortcuts -> {
                    adds.forEach { pkg -> appCategoryRepository.addToCategory(pkg, target.categoryId) }
                    removals.forEach { pkg -> appCategoryRepository.removeFromCategory(pkg, target.categoryId) }
                }
            }
        }
    }

    private suspend fun removeAndroidGames(platformId: String, packages: Set<String>) {
        packages.forEach { pkg ->
            val entry = gameRepository.getAppEntry(pkg) ?: return@forEach
            if (entry.platformId != platformId) return@forEach
            gameRepository.delete(entry.id)
        }
        Timber.i("Android library removal: ${packages.size} app(s) removed from $platformId")
    }

    private fun handleAppPickerBack() {
        val picker = _uiState.value.appPicker ?: return
        when {
            picker.searchActive -> _uiState.update { state ->
                state.copy(appPicker = state.appPicker?.let(::closeAppPickerSearch)?.clampFocus())
            }
            picker.confirmingRemovals -> cancelConfirm()
            else -> closeAppPicker()
        }
    }

    fun openGamePicker(categoryId: String) {
        _uiState.update { it.copy(gamePickerCategoryId = categoryId) }
    }

    fun closeGamePicker() {
        _uiState.update { it.copy(gamePickerCategoryId = null, pendingGamePickerAction = null) }
    }

    fun consumeGamePickerAction() {
        _uiState.update { it.copy(pendingGamePickerAction = null) }
    }

    fun confirmGamePicker(selectedGameIds: Set<Long>, selectedCollectionIds: Set<Long>) {
        val categoryId = _uiState.value.gamePickerCategoryId ?: return
        menuSound.play(MenuSound.CONFIRM)
        closeGamePicker()

        viewModelScope.launch {
            selectedGameIds.forEach { gameId ->
                gameCategoryRepository.addGameToCategory(gameId, categoryId)
            }

            selectedCollectionIds.forEach { collectionId ->
                collectionRepository.setCategory(collectionId, categoryId)
            }

            val category = _uiState.value.categories.getOrNull(_uiState.value.selectedCategoryIndex)
            if (category?.id == categoryId) {
                loadItemsForCategory(category)
            }
        }
    }

    private fun openGameCategoryPicker(gameId: Long, fromCategoryId: String, action: String) {
        val items = buildList {
            _uiState.value.categories
                .filter { it.isGamingCategory && it.id != fromCategoryId && it.id != BuiltInCategory.GAMES }
                .forEach { cat ->
                    add(XMBContextMenuItem("cat_${cat.id}", cat.name))
                }
        }

        if (items.isEmpty()) return

        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(state = MenuState(title = if (action == "move") "Move Game To" else "Add Game To", rows = items), gameId = gameId, categoryContext = fromCategoryId, pendingAppAction = action)
        )}
    }

    private suspend fun importAndroidGames(platformId: String, packages: Set<String>) {
        val labels = appCategoryRepository.allInstalledApps().associateBy { it.packageName }

        packages.forEach { pkg ->

            val existing = gameRepository.getAppEntry(pkg)
            when {
                existing == null -> gameRepository.upsert(
                    com.psplauncher.core.domain.model.Game(
                        title         = labels[pkg]?.label ?: pkg,
                        platformId    = platformId,
                        packageName   = pkg,
                        isManualEntry = true,

                        contentType   = com.psplauncher.core.domain.model.GameContentType.GAME,
                    )
                )

                existing.platformId != platformId ||
                    existing.contentType != com.psplauncher.core.domain.model.GameContentType.GAME ->
                    gameRepository.upsert(existing.copy(
                        platformId  = platformId,
                        contentType = com.psplauncher.core.domain.model.GameContentType.GAME,
                    ))
            }
        }
        memoryCardRepository.recountGames(platformId)
        Timber.i("Android library import: ${packages.size} app(s) selected for $platformId")
    }

    fun onPlatformLongPress(categoryIndex: Int) {
        _uiState.value.currentItems.getOrNull(categoryIndex)?.platformId?.let(::openPlatformContextMenu)
    }

    private fun scanCard(platformId: String) {
        viewModelScope.launch {
            val card = memoryCardRepository.getById(platformId) ?: return@launch
            val taskId = "scan_$platformId"

            if (platformId == WINDOWS_PLATFORM_ID) {
                addBackgroundTask(BackgroundTaskInfo(id = taskId, label = "Scanning ${card.displayName}…", progress = null))
                val report = runCatching { pcGameScanner.scan() }
                    .onFailure { Timber.e(it, "PC scan failed") }
                    .getOrNull()
                if (report == null) {
                    failBackgroundTask(taskId, "PC scan failed")
                } else {
                    memoryCardRepository.recordScan(platformId, System.currentTimeMillis())
                    completeBackgroundTask(
                        taskId,
                        if (report.newGames == 0) "No new PC games found" else report.message,
                    )
                }
                return@launch
            }

            addBackgroundTask(BackgroundTaskInfo(id = taskId, label = "Scanning ${card.displayName}…", progress = null))
            val outcome = libraryScanner.scanPlatform(platformId, removeMissing = true)
            when (outcome.status) {
                ScanStatus.COMPLETED -> completeBackgroundTask(
                    taskId,
                    scanOutcomeMessage(outcome, removeMissing = true),
                )
                else -> failBackgroundTask(
                    taskId,
                    scanOutcomeMessage(outcome, removeMissing = true),
                )
            }
        }
    }

    private fun cardName(platformId: String): String =
        enabledCards.firstOrNull { it.platformId == platformId }?.displayName ?: platformId.uppercase()

    private fun scrapeMissingArtworkForPlatform(platformId: String) {
        viewModelScope.launch {
            val taskId = "scrape_missing_$platformId"
            addBackgroundTask(BackgroundTaskInfo(id = taskId, label = "Scraping missing artwork: ${cardName(platformId)}", progress = 0f))
            runCatching {
                artworkRepository.scrapeMissingForPlatform(platformId) { p ->
                    updateBackgroundTask(taskId, p.current.toFloat() / p.total.coerceAtLeast(1))
                }
            }.onSuccess { result ->
                completeBackgroundTask(taskId,
                    if (result.total == 0) "No games are missing artwork"
                    else "${result.succeeded} of ${result.total} game(s) updated"
                )
                loadItemsForCategory(currentCategory())
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                failBackgroundTask(taskId, "Artwork scrape failed")
            }
        }
    }

    private fun updatePlatformMetadata(platformId: String) {
        viewModelScope.launch {
            val taskId = "update_metadata_$platformId"
            addBackgroundTask(BackgroundTaskInfo(id = taskId, label = "Updating metadata: ${cardName(platformId)}", progress = 0f))
            runCatching {
                artworkRepository.updateMetadataForPlatform(platformId) { p ->
                    updateBackgroundTask(taskId, p.current.toFloat() / p.total.coerceAtLeast(1))
                }
            }.onSuccess { result ->
                completeBackgroundTask(taskId,
                    if (result.total == 0) "No games on this card"
                    else "${result.succeeded} of ${result.total} game(s) updated"
                )
                loadItemsForCategory(currentCategory())
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                failBackgroundTask(taskId, "Metadata update failed")
            }
        }
    }

    private fun setCardPinned(platformId: String, pinned: Boolean) {
        viewModelScope.launch { memoryCardRepository.setPinned(platformId, pinned) }
    }

    private fun hideCard(platformId: String) {
        viewModelScope.launch {
            memoryCardRepository.setEnabled(platformId, false)
            if (_uiState.value.selectedPlatformId == platformId) closePlatformFolder()
        }
    }

    private fun removeCard(platformId: String) {
        viewModelScope.launch {
            memoryCardRepository.remove(platformId)
            if (_uiState.value.selectedPlatformId == platformId) closePlatformFolder()
        }
    }

    private fun toggleGameFavorite(gameId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            gameRepository.setFavorite(gameId, isFavorite)
        }
    }

    private val taskLabels = mutableMapOf<String, String>()

    private fun addBackgroundTask(task: BackgroundTaskInfo) {
        taskLabels[task.id] = task.label
        taskNotifier.running(task.id, task.label, task.progress)
    }

    private fun updateBackgroundTask(id: String, progress: Float) {
        val label = taskLabels[id] ?: return
        taskNotifier.running(id, label, progress.coerceIn(0f, 1f))
    }

    private fun completeBackgroundTask(id: String, message: String? = null) {
        val label = taskLabels.remove(id) ?: "Done"
        taskNotifier.complete(id, label, message)
    }

    private fun failBackgroundTask(id: String, message: String) {
        val label = taskLabels.remove(id) ?: "Task failed"
        taskNotifier.failed(id, label, message)
    }

    fun onCategorySelected(index: Int) {
        if (index != _uiState.value.selectedCategoryIndex) menuSound.play(MenuSound.SYSTEM_BROWSE)
        val category = _uiState.value.categories.getOrNull(index)

        _uiState.update { it.copy(selectedCategoryIndex = index, selectedItemIndex = 0, recentRailVisible = false, selectedPlatformId = null, selectedCollectionId = null, musicNav = MusicNav.Root, videoNav = VideoNav.Root, photoNav = PhotoNav.Root, activeAppDrawerFilter = null) }
        tintWaveForCategory(category)
        loadItemsForCategory(category)
    }

    fun onCategoryTapped(index: Int) {
        markTouchInput()
        val s = _uiState.value
        if (s.hasBlockingOverlay || s.isInSubItem) return
        onCategorySelected(index)
    }

    fun stepCategory(direction: Int) {
        markTouchInput()
        val s = _uiState.value
        if (s.hasBlockingOverlay) return

        if (s.isInSubItem) return
        val next = (s.selectedCategoryIndex + direction)
            .coerceIn(0, (s.categories.size - 1).coerceAtLeast(0))
        if (next != s.selectedCategoryIndex) onCategorySelected(next)
    }

    private fun XMBUiState.stepToReachableCategory(delta: Int): Int {
        var next = selectedCategoryIndex + delta
        while (next in categories.indices) {
            if (categoryReachable(categories[next])) return next
            next += delta
        }
        return selectedCategoryIndex
    }

    private fun moveItemCursor(delta: Int): Boolean {
        val s = _uiState.value
        if (s.hasBlockingOverlay || delta == 0) return false
        val max = (s.currentItems.size - 1).coerceAtLeast(0)
        val next = (s.selectedItemIndex + delta).coerceIn(0, max)
        if (next == s.selectedItemIndex) return false
        _uiState.update { it.copy(selectedItemIndex = next) }
        menuSound.play(MenuSound.SCROLL)
        return true
    }

    fun stepItem(steps: Int) {
        markTouchInput()
        moveItemCursor(steps)
    }

    fun onItemTap(index: Int) {
        markTouchInput()
        val s = _uiState.value
        if (s.hasBlockingOverlay) return
        if (index == s.selectedItemIndex) {
            activateSelected()
        } else {
            val clamped = index.coerceIn(0, (s.currentItems.size - 1).coerceAtLeast(0))
            if (clamped != s.selectedItemIndex) {
                _uiState.update { it.copy(selectedItemIndex = clamped) }
                menuSound.play(MenuSound.SCROLL)
            }
        }
    }

    private fun activateSelected() {
        onItemSelected(_uiState.value.selectedItemIndex)
    }

    fun markTouchInput() {
        lastInteractionMs = SystemClock.elapsedRealtime()

        _uiState.update {
            if (!it.hintsAutoHide) {
                it.copy(lastInputWasTouch = true).withHintsShownNow()
            } else if (it.lastInputWasTouch &&
                !it.showContextMenuHint &&
                !it.showAppDrawerHint &&
                !it.showSettingsHint
            ) it
            else it.copy(
                lastInputWasTouch = true,
                showContextMenuHint = false,
                showAppDrawerHint = false,
                showSettingsHint = false,
            )
        }
    }

    private fun dispatchCategorySelection(item: XMBItem): Boolean =
        when (item.menuHostCategory(currentCategory()?.id)) {
            BuiltInCategory.MUSIC   -> handleMusicSelection(item)
            BuiltInCategory.VIDEO   -> handleVideoSelection(item)
            BuiltInCategory.PHOTO   -> handlePhotoSelection(item)
            BuiltInCategory.LIBRARY -> handleBooksSelection(item)
            else -> false
        }

    private fun markControllerInput() {
        lastInteractionMs = SystemClock.elapsedRealtime()
        _uiState.update {
            if (!it.hintsAutoHide) {
                it.copy(lastInputWasTouch = false).withHintsShownNow()
            } else if (!it.lastInputWasTouch &&
                !it.showContextMenuHint &&
                !it.showAppDrawerHint &&
                !it.showSettingsHint
            ) it
            else it.copy(
                lastInputWasTouch = false,
                showContextMenuHint = false,
                showAppDrawerHint = false,
                showSettingsHint = false,
            )
        }
    }

    private fun backOutOfDrill(s: XMBUiState): Boolean {
        when (s.drillOutStep) {
            DrillOutStep.MUSIC -> closeMusicView()

            DrillOutStep.VIDEO_LIBRARY -> openVideoView(VideoNav.Libraries)
            DrillOutStep.VIDEO_PLAYLIST -> openVideoView(VideoNav.Playlists)
            DrillOutStep.VIDEO_COLLECTION_CHILD -> openVideoView(VideoNav.Collections)
            DrillOutStep.VIDEO -> closeVideoView()

            DrillOutStep.PHOTO_LIBRARY -> openPhotoView(PhotoNav.Albums)
            DrillOutStep.PHOTO -> closePhotoView()
            DrillOutStep.LIBRARY_SERIES -> openBooksView(BooksNav.SeriesList)
            DrillOutStep.LIBRARY_SHELF -> openBooksView(BooksNav.Shelves)
            DrillOutStep.LIBRARY -> closeBooksView()
            DrillOutStep.PLATFORM_FOLDER -> closePlatformFolder()
            null -> return false
        }
        return true
    }

    fun onHomeBack() {
        markTouchInput()
        val s = _uiState.value
        if (s.hasBlockingOverlay) return
        menuSound.play(MenuSound.BACK)
        if (!backOutOfDrill(s)) onOpenAppDrawer()
    }

    fun onItemSelected(index: Int) {
        if (_uiState.value.hasBlockingOverlay) return
        _uiState.update { it.copy(selectedItemIndex = index) }
        val category = _uiState.value.categories.getOrNull(_uiState.value.selectedCategoryIndex)
        val item     = _uiState.value.currentItems.getOrNull(index)

        if (item != null && dispatchCategorySelection(item)) return

        val silentRow = item?.id in setOf(NO_GAMES_ITEM_ID, EMPTY_COLLECTION_ITEM_ID, EMPTY_CATEGORY_ITEM_ID)

        val launchesGame = item?.gameId != null && item.isRealGame
        val launches = item?.launchIntentUri != null ||
            (item?.shortcutId != null && item.packageName != null) ||
            item?.packageName != null

        val event = when {
            silentRow -> null
            launchesGame -> null
            launches -> MenuSound.LAUNCH
            else -> MenuSound.SELECT
        }
        event?.let { menuSound.play(it) }

        when (item?.id) {
            NO_CONSOLES_ITEM_ID -> {
                _uiState.update { it.copy(activeSettingsScreen = "settings_library") }
                return
            }
            SETUP_GAP_ITEM_ID -> {
                _uiState.update {
                    it.copy(activeSettingsScreen = setupState.firstGap.repairScreenId)
                }
                return
            }
            ALL_GAMES_ITEM_ID -> {
                openAllGamesFolder()
                return
            }

            in SHELF_CARD_IDS -> {
                item?.id?.let { openShelf(it) }
                return
            }
            MISSING_ITEM_ID -> {
                openMissingFolder()
                return
            }
            QUICK_SEARCH_ITEM_ID -> {
                _uiState.update {
                    it.copy(collectionNameDialog = CollectionNameDialogState(
                        title = "Quick Search",
                        quickSearch = true,
                        placeholder = "Search the web, or type an address",
                        confirmLabel = "Search",
                    ))
                }
                return
            }
            SEARCH_ITEM_ID -> {
                openSearch(SearchScope.GAMES)
                return
            }
            ADD_APPS_ITEM_ID -> {
                category?.id?.let { openAppPicker(AppPickerTarget.CategoryShortcuts(it), "Add Apps") }
                return
            }
            ADD_GAMES_ITEM_ID -> {
                category?.id?.let { openGamePicker(it) }
                return
            }
            FIND_GAMES_ITEM_ID -> {
                (item.platformId ?: _uiState.value.selectedPlatformId)?.let {
                    openAppPicker(AppPickerTarget.AndroidGames(it), "Find Games")
                }
                return
            }
            NO_GAMES_ITEM_ID,
            EMPTY_COLLECTION_ITEM_ID,
            EMPTY_CATEGORY_ITEM_ID -> return
        }

        if (item?.collectionId != null && item.type == XMBItemType.COLLECTION) {
            openCollectionFolder(item.collectionId)
            return
        }

        if (item != null) when (item.type) {
            XMBItemType.VIDEO_FILE -> {
                menuSound.play(MenuSound.SELECT)
                _uiState.update { it.copy(activeVideoId = item.id.removePrefix("vid_")) }
                return
            }
            XMBItemType.LIBRARY_BOOK -> {
                menuSound.play(MenuSound.SELECT)
                openBook(item.id.removePrefix("book_"))
                return
            }
            XMBItemType.MUSIC_TRACK -> {
                menuSound.play(MenuSound.SELECT)
                openMusicPlayerForItem(item)
                return
            }

            XMBItemType.MUSIC_GROUP -> {
                item.musicGroupKey?.let {
                    menuSound.play(MenuSound.SELECT)
                    openMusicBrowser(MusicBrowserView.Album(item.title, it))
                }
                return
            }
            else -> Unit
        }

        if (item?.gameId != null && item.isRealGame) {
            launchGameDirectly(item.gameId)
            return
        }

        if (item?.launchIntentUri != null) {
            launchStoredIntent(item.launchIntentUri, item.title)
            return
        }

        if (item?.shortcutId != null && item.packageName != null) {
            launchHarvestedShortcut(item.packageName, item.shortcutId)
            return
        }

        if (item?.packageName != null) {
            launchAppWithDisc(item.packageName, item.shelfCoverArt)
            return
        }

        if (item?.gameId != null) {
            _uiState.update { it.copy(activeGameId = item.gameId) }
            return
        }
        if (item?.platformId != null) {
            openPlatformFolder(item.platformId)
            return
        }

        when (item?.id) {
            SETUP_ITEM_ID -> {
                Timber.d("Opening settings screen: settings_library (via setup prompt)")
                _uiState.update { it.copy(activeSettingsScreen = "settings_library") }
            }

            ANDROID_SETTINGS_ITEM_ID -> {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { Timber.w(it, "Could not open device settings") }
            }
            OPEN_SETTINGS_ITEM_ID -> {
                val root = com.psplauncher.core.domain.model.SETTINGS_ROOT_SCREEN_ID
                Timber.d("Opening settings: $root")
                _uiState.update { it.copy(activeSettingsScreen = root) }
            }
            else -> when (category?.id) {
                BuiltInCategory.SETTINGS -> {
                    item?.id?.let { id ->
                        Timber.d("Opening settings screen: $id")
                        _uiState.update { it.copy(activeSettingsScreen = id) }
                    }
                }
                BuiltInCategory.ANDROID -> {
                    if (item?.id?.startsWith("drawer_") == true) {
                        val filter = item.id.removePrefix("drawer_").uppercase()
                        _uiState.update { it.copy(activeAppDrawerFilter = filter) }
                    }
                }
            }
        }
    }

    fun onItemLongPress(index: Int) {
        if (_uiState.value.hasBlockingOverlay) return
        val item = _uiState.value.currentItems.getOrNull(index)
        when {
            item != null && openMusicContextMenu(item) -> Unit
            item != null && openVideoContextMenu(item) -> Unit
            item != null && openBookContextMenu(item) -> Unit
            item != null && openPhotoContextMenu(item) -> Unit
            item?.gameId != null -> openGameContextMenu(item)
            item?.collectionId != null && item.type == XMBItemType.COLLECTION -> openCollectionRowContextMenu(item.collectionId)
            item?.type == XMBItemType.ALL_GAMES -> openAllGamesContextMenu()
            item?.platformId != null -> openPlatformContextMenu(item.platformId)
            item?.packageName != null -> openAppContextMenu(item)
        }
    }

    private fun openPlatformFolder(platformId: String) {
        val gamesCategoryIndex = _uiState.value.categories.indexOfFirst { it.id == BuiltInCategory.GAMES }
        navigateRememberingCursor {
            it.copy(
                selectedCategoryIndex = gamesCategoryIndex.takeIf { index -> index >= 0 } ?: it.selectedCategoryIndex,
                selectedPlatformId = platformId,
            )
        }
    }

    private fun openAllGamesFolder() {
        val gamesCategoryIndex = _uiState.value.categories.indexOfFirst { it.id == BuiltInCategory.GAMES }
        navigateRememberingCursor {
            it.copy(
                selectedCategoryIndex = gamesCategoryIndex.takeIf { index -> index >= 0 } ?: it.selectedCategoryIndex,
                selectedPlatformId = ALL_GAMES_PLATFORM_ID,
                selectedCollectionId = null,
            )
        }
    }

    private fun openShelf(cardId: String) {
        navigateRememberingCursor {
            it.copy(selectedPlatformId = cardId, selectedCollectionId = null)
        }
    }

    private fun openMissingFolder() {
        val gamesCategoryIndex = _uiState.value.categories.indexOfFirst { it.id == BuiltInCategory.GAMES }
        navigateRememberingCursor {
            it.copy(
                selectedCategoryIndex = gamesCategoryIndex.takeIf { index -> index >= 0 } ?: it.selectedCategoryIndex,
                selectedPlatformId = MISSING_PLATFORM_ID,
                selectedCollectionId = null,
            )
        }
    }

    private fun openCollectionFolder(collectionId: Long) {
        val targetCategoryId = _uiState.value.collections
            .firstOrNull { it.id == collectionId }?.categoryId ?: BuiltInCategory.GAMES
        val categoryIndex = _uiState.value.categories.indexOfFirst { it.id == targetCategoryId }
        navigateRememberingCursor {
            it.copy(
                selectedCategoryIndex = categoryIndex.takeIf { index -> index >= 0 } ?: it.selectedCategoryIndex,
                selectedPlatformId = null,
                selectedCollectionId = collectionId,
            )
        }
    }

    private fun closePlatformFolder() = navigateRememberingCursor {
        it.copy(selectedPlatformId = null, selectedCollectionId = null)
    }


    fun openArtworkStudio(gameId: Long) {
        closeContextMenu()
        _uiState.update { it.copy(artworkStudioGameId = gameId) }
    }

    fun consumeArtworkStudioAction() =
        _uiState.update { it.copy(pendingArtworkStudioAction = null) }

    fun closeArtworkStudio() {
        val id = _uiState.value.artworkStudioGameId
        _uiState.update { it.copy(artworkStudioGameId = null) }
        if (id != null) viewModelScope.launch { loadItemsForCategory(currentCategory()) }
    }

    private fun openManualFor(gameId: Long) {
        closeContextMenu()
        viewModelScope.launch {
            val game = gameRepository.getById(gameId) ?: return@launch
            val path = artworkStore.find(gameId, ArtworkKind.MANUAL)
            if (path == null) {
                SystemToasts.post("No manual available for this game", null, ToastKind.ERROR)
                return@launch
            }
            _uiState.update {
                it.copy(manualViewer = ManualViewerUi(uri = path, title = game.displayTitle))
            }
        }
    }

    fun closeManualViewer() = _uiState.update { it.copy(manualViewer = null) }

    fun setManualPageCount(count: Int) = _uiState.update { s ->
        val m = s.manualViewer ?: return@update s
        s.copy(manualViewer = m.copy(pageCount = count, page = m.page.coerceIn(0, (count - 1).coerceAtLeast(0))))
    }

    fun manualPrevPage() = _uiState.update { s ->
        val m = s.manualViewer ?: return@update s
        s.copy(manualViewer = m.copy(page = (m.page - 1).coerceAtLeast(0), scrollSteps = 0))
    }

    fun manualNextPage() = _uiState.update { s ->
        val m = s.manualViewer ?: return@update s
        s.copy(manualViewer = m.copy(
            page = (m.page + 1).coerceAtMost((m.pageCount - 1).coerceAtLeast(0)),
            scrollSteps = 0,
        ))
    }

    private fun scrollManual(delta: Int) = _uiState.update { s ->
        val m = s.manualViewer ?: return@update s
        s.copy(manualViewer = m.copy(scrollSteps = (m.scrollSteps + delta).coerceIn(0, MANUAL_MAX_SCROLL_STEPS_)))
    }

    private fun handleManualViewerInput(action: GamepadAction) {
        when (action) {
            GamepadAction.NAVIGATE_LEFT  -> manualPrevPage()
            GamepadAction.NAVIGATE_RIGHT -> manualNextPage()
            GamepadAction.NAVIGATE_DOWN  -> scrollManual(+1)
            GamepadAction.NAVIGATE_UP    -> scrollManual(-1)
            GamepadAction.BACK           -> closeManualViewer()
            else -> Unit
        }
    }

    private fun fetchArtworkFor(gameId: Long) {
        closeContextMenu()
        if (_uiState.value.isFetchingArtwork) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingArtwork = true) }
            val before = gameRepository.getById(gameId)
            val result = artworkRepository.fetchArtworkForGame(gameId, before?.title.orEmpty())
            val updated = gameRepository.getById(gameId)
            artworkRepository.evictFromImageCache((artRefsOf(before) + artRefsOf(updated)).toSet())
            _uiState.update { it.copy(isFetchingArtwork = false) }
            SystemToasts.post(
                when {
                    result.success -> "Artwork updated"
                    result.skipped -> "Already has artwork"
                    else           -> result.errorMessage ?: "Artwork fetch failed"
                },
                null,
                if (result.success || result.skipped) ToastKind.SUCCESS else ToastKind.ERROR,
            )
            loadItemsForCategory(currentCategory())
        }
    }

    private fun artRefsOf(game: Game?): List<String> = listOfNotNull(
        game?.artworkUri, game?.heroUri, game?.logoUri, game?.iconUri,
        game?.boxArtUri, game?.physicalMediaUri, game?.box3dUri,
    )

    private fun openMetadataPreviewFor(gameId: Long) {
        closeContextMenu()
        if (_uiState.value.metadataPreview != null) return
        val generation = ++metadataPreviewGeneration
        _uiState.update { it.copy(metadataPreview = MetadataPreviewUi(), metadataPreviewGameId = gameId) }
        viewModelScope.launch {
            val outcome = runCatching { artworkRepository.fetchMetadataPreview(gameId) }
                .onFailure { Timber.w(it, "Metadata preview failed for game $gameId") }
            val preview = outcome.getOrNull()
            if (generation != metadataPreviewGeneration) return@launch
            _uiState.update { s ->
                if (s.metadataPreview == null) return@update s
                if (preview == null || preview.presets.isEmpty()) {
                    return@update s.copy(
                        metadataPreview = MetadataPreviewUi(loading = false, failed = outcome.isFailure),
                    )
                }
                s.copy(metadataPreview = MetadataPreviewUi(
                    loading = false,
                    current = preview.current,
                    presets = preview.presets,
                    chosen  = MetadataApply.changedFields(preview.current, preview.presets.first()),
                ))
            }
        }
    }

    fun closeMetadataPreview() {
        metadataPreviewGeneration++
        _uiState.update { it.copy(metadataPreview = null, metadataPreviewGameId = null) }
    }

    fun selectMetadataPolicy(policy: MetadataApplyPolicy) = updateMetadataPreview { it.copy(policy = policy) }

    private fun cycleMetadataPolicy(delta: Int) = updateMetadataPreview { p ->
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

    private fun moveMetadataFocus(delta: Int) = updateMetadataPreview { p ->
        p.copy(focus = (p.focus + delta).coerceIn(0, p.applyIndex))
    }

    fun applyMetadataPreview() {
        val gameId = _uiState.value.metadataPreviewGameId ?: return
        val p = _uiState.value.metadataPreview ?: return
        if (p.loading || p.applying) return

        val preset = p.preset ?: return closeMetadataPreview()
        if (p.policy == MetadataApplyPolicy.KEEP_CURRENT) {
            closeMetadataPreview()
            SystemToasts.post("Kept current metadata", null, ToastKind.SUCCESS)
            return
        }
        _uiState.update { it.copy(metadataPreview = p.copy(applying = true)) }
        viewModelScope.launch {
            val written = runCatching { artworkRepository.applyMetadata(gameId, preset, p.policy, p.chosen) }
                .onFailure { Timber.w(it, "Metadata apply failed for game $gameId") }
            metadataPreviewGeneration++
            _uiState.update { it.copy(metadataPreview = null, metadataPreviewGameId = null) }
            SystemToasts.post(
                written.fold(
                    onSuccess = { fields ->
                        when (fields.size) {
                            0    -> "Nothing to change"
                            1    -> "Updated 1 field from ${preset.provider.label}"
                            else -> "Updated ${fields.size} fields from ${preset.provider.label}"
                        }
                    },
                    onFailure = { "Metadata update failed" },
                ),
                null,
                if (written.isSuccess) ToastKind.SUCCESS else ToastKind.ERROR,
            )
            loadItemsForCategory(currentCategory())
        }
    }

    private fun updateMetadataPreview(
        transform: (MetadataPreviewUi) -> MetadataPreviewUi,
    ) = _uiState.update { s ->
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
            GamepadAction.NAVIGATE_UP    -> moveMetadataFocus(-1)
            GamepadAction.NAVIGATE_DOWN  -> moveMetadataFocus(+1)
            GamepadAction.SELECT         ->
                if (p.focus >= p.applyIndex) applyMetadataPreview()
                else p.rows.getOrNull(p.focus)?.let { toggleMetadataField(it.field) }
            else -> Unit
        }
    }

    private var metadataPreviewGeneration = 0

    private val MANUAL_MAX_SCROLL_STEPS_ = 20

    private fun launchGameDirectly(gameId: Long, discId: Long? = null) {
        _uiState.update { it.copy(activeGameId = null, activeGameAutoLaunch = false, activeGameDiscId = null, activeGameAction = null) }
        viewModelScope.launch {
            val selected = gameRepository.getById(gameId) ?: run {
                Timber.w("Direct launch requested for missing game id=$gameId")
                return@launch
            }
            val game = if (discId != null) gameRepository.getById(discId) ?: selected else selected
            if (game.isMissing) {
                Timber.i("Direct launch refused for missing game: ${game.title}")
                return@launch
            }
            launchResolvedGame(game)
        }
    }

    private suspend fun launchResolvedGame(game: Game) {
        val shortcutId = game.shortcutId
        val packageName = game.packageName
        if (shortcutId != null && packageName != null) {
            launcherShortcutRepository.launch(packageName, shortcutId)
                .onFailure { e ->
                    Timber.w(e, "Direct shortcut launch failed")
                    launchDispatcher.recordPreflightFailure(game, null, "Couldn't launch: ${e.message}")
                }
            return
        }
        if (game.launchIntentUri != null) {
            runCatching {
                val parsed = Intent.parseUri(game.launchIntentUri, Intent.URI_INTENT_SCHEME)
                com.psplauncher.core.common.security.ShortcutIntentSanitizer.sanitize(parsed, context.packageManager)
                    ?: error("Captured shortcut is not safe to launch")
            }.onSuccess { intent -> launchIntentFromXmb(intent, game, null) }
                .onFailure { e ->
                    Timber.w(e, "Direct stored-intent launch failed")
                    launchDispatcher.recordPreflightFailure(game, null, "Couldn't launch: ${e.message}")
                }
            return
        }
        if (game.romPath.isNullOrBlank() && !game.packageName.isNullOrBlank()) {
            intentResolver.resolveNativeApp(game).fold(
                onSuccess = { intent -> launchIntentFromXmb(intent, game, null) },
                onFailure = { e ->
                    Timber.w(e, "Direct native-app launch failed")
                    launchDispatcher.recordPreflightFailure(game, null, e.message ?: "Could not launch ${game.title}")
                },
            )
            return
        }

        val resolvedLaunch = launchResolver.resolve(game).getOrElse { reason ->
            Timber.w(reason, "Direct launch unresolved: gameId=${game.id}, platform=${game.platformId}")

            launchDispatcher.recordPreflightFailure(
                game, null,
                reason.message ?: "No emulator is set up for ${game.platformId.uppercase()}.",
            )
            return
        }
        val profile = resolvedLaunch.profile

        val validation = runCatching { intentResolver.validateBeforeLaunch(game, profile) }
        if (validation.isFailure) {
            Timber.w(
                validation.exceptionOrNull(),
                "Direct emulator launch blocked by preflight: ${profile.name}",
            )
            val blocked = validation.exceptionOrNull() as? com.psplauncher.feature.launcher.LaunchBlockedException
            launchDispatcher.recordPreflightFailure(
                game, resolvedLaunch,
                validation.exceptionOrNull()?.message ?: "Could not launch ${profile.name}",

                kind = blocked?.kind ?: com.psplauncher.feature.launcher.LaunchFailureKind.UNKNOWN,
            )
            return
        }
        intentResolver.resolve(game, profile).fold(
            onSuccess = { intent -> launchIntentFromXmb(intent, game, resolvedLaunch) },
            onFailure = { e ->
                Timber.w(e, "Direct emulator launch failed: ${profile.name}")
                launchDispatcher.recordPreflightFailure(game, resolvedLaunch, e.message ?: "Could not launch ${profile.name}")
            },
        )
    }

    private suspend fun launchIntentFromXmb(intent: Intent, game: Game, resolved: ResolvedLaunch?) {
        when (val result = launchDispatcher.launch(game, resolved, intent)) {
            is LaunchDispatchResult.Rejected -> Timber.w("Direct launch rejected: ${result.message}")
            LaunchDispatchResult.Accepted -> Unit
        }
    }

    fun onCloseGameDetail() {
        _uiState.update {
            it.copy(
                activeGameId = null,
                activeGameAutoLaunch = false,
                activeGameDiscId = null,

                activeGameAction = null,
                pendingGameDetailAction = null,
            )
        }

        loadItemsForCategory(currentCategory(), keepCursorOnRow = true)
    }

    fun consumeGameDetailAction() {
        _uiState.update { it.copy(pendingGameDetailAction = null) }
    }

    private fun openAppDetail(knownGameId: Long?, packageName: String) {
        val collectionHome = collectionHomeCategoryId()
        if (knownGameId != null) {
            _uiState.update { it.copy(activeAppId = knownGameId, activeAppCollectionCategoryId = collectionHome) }
            return
        }
        viewModelScope.launch {
            val id = ensureAppShortcut(packageName)
            _uiState.update { it.copy(activeAppId = id, activeAppCollectionCategoryId = collectionHome) }
        }
    }

    private suspend fun ensureAppShortcut(packageName: String): Long {
        gameRepository.getAppEntry(packageName)?.let { return it.id }
        val label = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault(packageName)
        return gameRepository.upsert(
            Game(
                title         = label,

                platformId    = APP_SHORTCUT_PLATFORM_ID,
                packageName   = packageName,
                isManualEntry = true,
                contentType   = GameContentType.ANDROID_APP,
            )
        )
    }

    private fun addAppToFavorites(packageName: String, label: String) {
        viewModelScope.launch {
            runCatching {
                val id = ensureAppShortcut(packageName)
                gameRepository.setFavorite(id, true)
            }.onSuccess {
                Timber.i("App shortcut favorited: $packageName")
                taskNotifier.complete("shortcut_fav_$packageName", label, "Added to Favorites")
            }.onFailure { e ->
                Timber.e(e, "Failed to add app to Favorites: $packageName")
                taskNotifier.failed("shortcut_fav_$packageName", label, "Couldn't add to Favorites: ${e.message}")
            }
        }
    }

    private fun addAppToCollection(packageName: String, label: String) {
        viewModelScope.launch {
            runCatching { ensureAppShortcut(packageName) }
                .onSuccess { id -> openCollectionPicker(id) }
                .onFailure { e ->
                    Timber.e(e, "Failed to prepare app shortcut for collection: $packageName")
                    taskNotifier.failed("shortcut_col_$packageName", label, "Couldn't create shortcut: ${e.message}")
                }
        }
    }

    private fun launchHarvestedShortcut(hostPackage: String?, shortcutId: String?) {
        if (hostPackage == null || shortcutId == null) return
        launcherShortcutRepository.launch(hostPackage, shortcutId).onFailure { e ->
            Timber.e(e, "Failed to launch shortcut $hostPackage/$shortcutId")
            taskNotifier.failed("launch_sc_$shortcutId", hostPackage, "Couldn't launch: ${e.message}")
        }
    }

    private fun launchStoredIntent(intentUri: String, label: String) {
        runCatching {
            val parsed = android.content.Intent.parseUri(intentUri, android.content.Intent.URI_INTENT_SCHEME)

            val launch = (com.psplauncher.core.common.security.ShortcutIntentSanitizer
                .sanitize(parsed, context.packageManager)
                ?: error("Captured shortcut is not safe to launch"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
            context.startActivity(
                launch,
                com.psplauncher.core.common.launch.LaunchTransition.options(context),
            )
        }.onFailure { e ->
            Timber.e(e, "Failed to launch captured shortcut: $label")
            taskNotifier.failed("launch_intent_${label.hashCode()}", label, "Couldn't launch: ${e.message}")
        }
    }

    fun onCloseAppDetail() {
        _uiState.update { it.copy(activeAppId = null, pendingAppDetailAction = null) }

        loadItemsForCategory(currentCategory(), keepCursorOnRow = true)
    }

    fun consumeAppDetailAction() {
        _uiState.update { it.copy(pendingAppDetailAction = null) }
    }

    fun onOpenSettingsScreen(screenId: String) {
        if (screenId != com.psplauncher.core.domain.model.SETTINGS_ROOT_SCREEN_ID &&
            com.psplauncher.core.domain.model.settingsEntryFor(screenId) == null
        ) {
            Timber.w("Settings rail asked for a screen outside the catalog: %s", screenId)
            return
        }
        Timber.d("Settings rail -> %s", screenId)
        _uiState.update {
            it.copy(
                activeSettingsScreen = screenId,
                settingsReturnTo = returnAddressFor(it.activeSettingsScreen),
            )
        }
    }

    fun onSettingsBack() {
        _uiState.value.settingsReturnTo?.let { returnTo ->
            _uiState.update {
                it.copy(
                    activeSettingsScreen = returnTo,
                    settingsReturnTo = null,
                    pendingSettingsAction = null,
                )
            }
            return
        }
        val current = _uiState.value.activeSettingsScreen
        if (current != null &&
            current != com.psplauncher.core.domain.model.SETTINGS_ROOT_SCREEN_ID &&
            com.psplauncher.core.domain.model.settingsEntryFor(current) != null
        ) {
            _uiState.update {
                it.copy(
                    activeSettingsScreen = com.psplauncher.core.domain.model.SETTINGS_ROOT_SCREEN_ID,
                    pendingSettingsAction = null,
                )
            }
            return
        }
        onCloseSettingsScreen()
    }

    fun onCloseSettingsScreen() {
        Timber.d("Settings closed")

        val closing = _uiState.value.activeSettingsScreen
        if (closing in WIZARD_SCREEN_IDS) {
            markInitialSetupSeen()
        }
        _uiState.update {
            it.copy(activeSettingsScreen = null, settingsReturnTo = null, pendingSettingsAction = null)
        }
    }

    fun openAndroidLibraryPicker() {
        _uiState.update { it.copy(activeSettingsScreen = null, pendingSettingsAction = null) }
        openAppPicker(AppPickerTarget.AndroidGames(ANDROID_PLATFORM_ID), "Add Android Apps")
    }

    fun consumeSettingsAction() {
        _uiState.update { it.copy(pendingSettingsAction = null) }
    }

    fun onOpenAppDrawer() {
        _uiState.update { it.copy(activeAppDrawerFilter = com.psplauncher.feature.appbar.AppFilter.DEFAULT.name) }
    }

    fun addAppToOpenCategory(packageName: String) {
        val category = currentCategory() ?: return
        if (!categoryShowsApps(category)) {
            SystemToasts.post("${category.name} can't hold apps", null, ToastKind.ERROR)
            return
        }
        viewModelScope.launch {
            appCategoryRepository.addToCategory(packageName, category.id)
            SystemToasts.post("Added to ${category.name}", null, ToastKind.SUCCESS)
        }
    }

    fun onCloseAppDrawer() {
        _uiState.update { it.copy(activeAppDrawerFilter = null, pendingDrawerAction = null, pendingDrawerTypedChar = null) }
    }

    fun consumeDrawerAction() {
        _uiState.update { it.copy(pendingDrawerAction = null) }
    }

    private var colorSchemeOriginal: XmbColorScheme? = null

    private var accentOverrideOriginal: Long? = null

    fun openColorSchemePicker() {
        viewModelScope.launch {
            val prefs = context.pfpDataStore.data.first()
            val current = runCatching {
                XmbColorScheme.valueOf(prefs[KEY_COLOR_SCHEME] ?: XmbColorScheme.CLASSIC_BLUE.name)
            }.getOrDefault(XmbColorScheme.CLASSIC_BLUE)
            colorSchemeOriginal = current
            accentOverrideOriginal = prefs[KEY_ACCENT_OVERRIDE]

            val month = java.time.LocalDate.now().monthValue
            val options = XmbColorScheme.values().map { scheme ->
                ColorSchemeOption(
                    scheme   = scheme,
                    label    = scheme.displayLabel(),

                    sublabel = if (scheme == XmbColorScheme.ORIGINAL) "Changes with the month" else null,
                    swatch   = scheme.resolve(month).waveColor,
                )
            }
            val custom = prefs[KEY_ACCENT_OVERRIDE]
            val pickerOptions = options + ColorSchemeOption(
                scheme = null,
                label = "Custom",
                sublabel = "Choose a custom accent color",
                swatch = custom ?: 0xFF888888L,
                isCustom = true,
            )
            val index = if (custom != null) pickerOptions.lastIndex else options.indexOfFirst { it.scheme == current }.coerceAtLeast(0)
            _uiState.update { it.copy(colorSchemePicker = ColorSchemePickerState(pickerOptions, index)) }
        }
    }

    private fun moveColorSchemePicker(delta: Int) {
        val picker = _uiState.value.colorSchemePicker ?: return
        val next = (picker.selectedIndex + delta).coerceIn(0, picker.options.lastIndex)
        if (next == picker.selectedIndex) { gamepadInputHandler.cancelRepeat(); return }
        _uiState.update { it.copy(colorSchemePicker = picker.copy(selectedIndex = next)) }
        picker.options[next].scheme?.let(::previewColorScheme)
    }

    fun onColorSchemeHighlightedAt(index: Int) {
        val picker = _uiState.value.colorSchemePicker ?: return
        if (index !in picker.options.indices || index == picker.selectedIndex) return
        _uiState.update { it.copy(colorSchemePicker = picker.copy(selectedIndex = index)) }
        picker.options[index].scheme?.let(::previewColorScheme)
    }

    private fun previewColorScheme(scheme: XmbColorScheme) {
        viewModelScope.launch {
            context.pfpDataStore.edit {
                it[KEY_COLOR_SCHEME] = scheme.name

                it.remove(KEY_ACCENT_OVERRIDE)
            }
        }
    }

    fun confirmColorSchemePicker() {
        val picker = _uiState.value.colorSchemePicker ?: return
        val selected = picker.options.getOrNull(picker.selectedIndex)
        val chosen = selected?.scheme
        if (selected?.isCustom == true) {
            openCustomColorPicker()
            return
        }
        viewModelScope.launch {
            if (chosen != null) {
                context.pfpDataStore.edit {
                    it[KEY_COLOR_SCHEME] = chosen.name

                    it.remove(KEY_ACCENT_OVERRIDE)
                    it.remove(com.psplauncher.core.data.repository.PfpThemeStore.KEY_THEME_ICONS_STAMP)
                    it.remove(com.psplauncher.core.data.repository.PfpThemeStore.KEY_THEME_LAYOUT)
                }
            }
            colorSchemeOriginal = null
            accentOverrideOriginal = null
            _uiState.update { it.copy(colorSchemePicker = null) }
        }
    }

    private fun openCustomColorPicker() {
        val argb = _uiState.value.themeColors.accentColor.toArgb().toLong() and 0xFFFFFFFFL
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV((argb and 0xFFFFFFFFL).toInt(), hsv)
        _uiState.update { state ->
            return@update state.copy(customColorPicker = CustomColorPickerState(hsv[0], hsv[1], hsv[2]))
        }
    }

    fun updateCustomColor(channel: Int, fraction: Float) {
        _uiState.update { state ->
            val picker = state.customColorPicker ?: return@update state
            val clamped = fraction.coerceIn(0f, 1f)
            return@update state.copy(customColorPicker = picker.copy(
                hue = if (channel == 0) clamped * 360f else picker.hue,
                saturation = if (channel == 1) clamped else picker.saturation,
                brightness = if (channel == 2) clamped else picker.brightness,
                selectedChannel = channel,
            ))
        }
    }

    fun moveCustomColorChannel(delta: Int) {
        _uiState.update { state ->
            val picker = state.customColorPicker ?: return@update state
            state.copy(customColorPicker = picker.copy(selectedChannel = (picker.selectedChannel + delta + 3) % 3))
        }
    }

    fun adjustCustomColor(delta: Float) {
        val picker = _uiState.value.customColorPicker ?: return
        val value = when (picker.selectedChannel) {
            0 -> ((picker.hue / 360f) + delta).mod(1f)
            1 -> picker.saturation + delta
            else -> picker.brightness + delta
        }
        updateCustomColor(picker.selectedChannel, value)
    }

    fun confirmCustomColor() {
        val picker = _uiState.value.customColorPicker ?: return
        viewModelScope.launch {
            context.pfpDataStore.edit { it[KEY_ACCENT_OVERRIDE] = android.graphics.Color.HSVToColor(floatArrayOf(picker.hue, picker.saturation, picker.brightness)).toLong() and 0xFFFFFFFFL }
            _uiState.update { it.copy(customColorPicker = null, colorSchemePicker = null) }
        }
    }

    fun cancelCustomColor() {
        _uiState.update { it.copy(customColorPicker = null) }
    }

    fun cancelColorSchemePicker() {
        val original = colorSchemeOriginal
        val accentOriginal = accentOverrideOriginal
        viewModelScope.launch {
            if (original != null) {
                context.pfpDataStore.edit {
                    it[KEY_COLOR_SCHEME] = original.name

                    if (accentOriginal != null) it[KEY_ACCENT_OVERRIDE] = accentOriginal
                }
            }
            colorSchemeOriginal = null
            accentOverrideOriginal = null
            _uiState.update { it.copy(colorSchemePicker = null) }
        }
    }

    fun openXmbLayoutAdjust() {
        val swDp = context.resources.configuration.smallestScreenWidthDp
        val bucket = com.psplauncher.themekit.XmbFormFactor.forSmallestWidthDp(swDp).key
        val s = _uiState.value
        val seed = s.xmbLayoutAdjustMap[bucket] ?: com.psplauncher.themekit.XmbLayoutAdjust(
            scale = s.xmbScale,
            barLeftFraction = 0f,
            barTopFraction = s.layoutSpec.barTopFraction,
        )
        _uiState.update {
            it.copy(

                activeSettingsScreen = null,
                pendingSettingsAction = null,
                xmbLayoutAdjust = XmbLayoutAdjustSession(draft = seed, original = seed, bucketKey = bucket),
            )
        }
    }

    private fun updateAdjustDraft(transform: (com.psplauncher.themekit.XmbLayoutAdjust) -> com.psplauncher.themekit.XmbLayoutAdjust) {
        val session = _uiState.value.xmbLayoutAdjust ?: return
        val next = com.psplauncher.themekit.XmbLayoutAdjustCodec.sanitize(transform(session.draft))
        _uiState.update { it.copy(xmbLayoutAdjust = session.copy(draft = next)) }
    }

    fun nudgeXmbLayoutHorizontal(dir: Int) = updateAdjustDraft { it.copy(barLeftFraction = it.barLeftFraction + dir * 0.01f) }
    fun nudgeXmbLayoutVertical(dir: Int) = updateAdjustDraft { it.copy(barTopFraction = it.barTopFraction + dir * 0.01f) }
    fun nudgeXmbLayoutScale(dir: Int) = updateAdjustDraft { it.copy(scale = it.scale + dir * 0.02f) }

    fun setXmbLayoutScale(v: Float) = updateAdjustDraft { it.copy(scale = v) }
    fun setXmbLayoutHorizontal(v: Float) = updateAdjustDraft { it.copy(barLeftFraction = v) }
    fun setXmbLayoutVertical(v: Float) = updateAdjustDraft { it.copy(barTopFraction = v) }

    fun toggleXmbLayoutSliders() {
        val session = _uiState.value.xmbLayoutAdjust ?: return
        _uiState.update { it.copy(xmbLayoutAdjust = session.copy(slidersVisible = !session.slidersVisible)) }
    }

    fun resetXmbLayoutAdjust() = updateAdjustDraft { com.psplauncher.themekit.XmbLayoutAdjust() }

    fun saveXmbLayoutAdjust() {
        val session = _uiState.value.xmbLayoutAdjust ?: return
        val map = _uiState.value.xmbLayoutAdjustMap.toMutableMap()
        map[session.bucketKey] = session.draft
        viewModelScope.launch {
            context.pfpDataStore.edit {
                it[KEY_XMB_LAYOUT_ADJUST] = com.psplauncher.themekit.XmbLayoutAdjustCodec.encode(map)
            }
            _uiState.update { it.copy(xmbLayoutAdjust = null) }
        }
    }

    fun cancelXmbLayoutAdjust() {
        _uiState.update { it.copy(xmbLayoutAdjust = null) }
    }

    private val customIconGroups: List<com.psplauncher.themekit.IconSlot.Group> =
    listOf(
        com.psplauncher.themekit.IconSlot.Group.CATEGORY_BAR,
        com.psplauncher.themekit.IconSlot.Group.ITEMS,
        com.psplauncher.themekit.IconSlot.Group.STATUS,
        com.psplauncher.themekit.IconSlot.Group.CONSOLE,
    )

    fun openCustomIcons() {
        _uiState.update {
            it.copy(

                activeSettingsScreen = null,
                pendingSettingsAction = null,
                customIconSession = CustomIconSession(groups = customIconGroups),
            )
        }
    }

    fun closeCustomIcons() {
        _uiState.update { it.copy(customIconSession = null, saveThemeNameDialog = null) }
    }

    fun onCustomIconGroupMove(dir: Int) {
        val session = _uiState.value.customIconSession ?: return
        val next = (session.groupIndex + dir).mod(session.groups.size)
        _uiState.update {
            it.copy(customIconSession = session.copy(groupIndex = next, slotIndex = 0, message = null))
        }
    }

    fun onCustomIconSlotMove(dir: Int) {
        val session = _uiState.value.customIconSession ?: return
        val count = CustomizableIcons.group(session.group).size
        val next = (session.slotIndex + dir).coerceIn(0, (count - 1).coerceAtLeast(0))
        _uiState.update { it.copy(customIconSession = session.copy(slotIndex = next, message = null)) }
    }

    fun onCustomIconSlotFocused(index: Int) {
        val session = _uiState.value.customIconSession ?: return
        _uiState.update { it.copy(customIconSession = session.copy(slotIndex = index, message = null)) }
    }

    fun onIconPicked(slotKey: String, uri: android.net.Uri) {
        val session = _uiState.value.customIconSession ?: return
        viewModelScope.launch {
            val mime = context.contentResolver.getType(uri)
            val result = customIconStore.import(slotKey, uri, mime)

            menuSound.play(if (result.ok) MenuSound.CONFIRM else MenuSound.ERROR)
            _uiState.update {
                val s = it.customIconSession ?: return@update it
                it.copy(customIconSession = s.copy(message = result.message, revision = s.revision + 1))
            }
        }
    }

    fun onResetSlot(slotKey: String) {
        viewModelScope.launch {
            val removed = customIconStore.clear(slotKey)
            _uiState.update {
                val s = it.customIconSession ?: return@update it
                val themed = it.iconOverrides.containsKey(slotKey)
                val message = when {
                    removed && themed -> context.getString(R.string.xmb_icons_reset_removed_themed)
                    removed -> null
                    themed -> context.getString(R.string.xmb_icons_reset_themed_slot)
                    else -> context.getString(R.string.xmb_icons_reset_builtin_slot)
                }
                it.copy(customIconSession = s.copy(message = message, revision = s.revision + 1))
            }
        }
    }

    fun onResetAll() {
        viewModelScope.launch {
            val removed = customIconStore.clearAll()
            _uiState.update {
                val s = it.customIconSession ?: return@update it
                val message = when {
                    !removed -> context.getString(R.string.xmb_icons_reset_all_none)
                    it.iconOverrides.isNotEmpty() -> context.getString(R.string.xmb_icons_reset_all_themed)
                    else -> null
                }
                it.copy(customIconSession = s.copy(message = message, revision = s.revision + 1))
            }
        }
    }

    fun onThemeShareConsumed() {
        _uiState.update { it.copy(pendingThemeShareFile = null) }
    }

    fun requestSaveCurrentLookAsTheme() {
        _uiState.update {
            it.copy(saveThemeNameDialog = PlaylistNameDialogState(title = "Save Current Look as Theme"))
        }
    }

    fun confirmSaveCurrentLookAsTheme(name: String) {
        _uiState.update { it.copy(saveThemeNameDialog = null) }
        menuSound.play(MenuSound.CONFIRM)
        saveCurrentLookAsTheme(name)
    }

    fun dismissSaveThemeNameDialog() {
        _uiState.update { it.copy(saveThemeNameDialog = null) }
    }

    fun onCustomIconsActionConsumed() {
        _uiState.update { it.copy(pendingCustomIconsAction = null) }
    }

    fun saveCurrentLookAsTheme(name: String) {
        viewModelScope.launch {
            val saved = pfpThemeStore.saveCurrentLook(name)
            val message = when {
                saved == null -> "Couldn't save the theme"
                else -> "Theme saved — ${saved.name}"
            }
            val shareFile = saved?.let { pfpThemeStore.exportForShare(it.id) }
            _uiState.update {
                val s = it.customIconSession ?: return@update it
                it.copy(
                    customIconSession = s.copy(message = message, revision = s.revision + 1),
                    pendingThemeShareFile = shareFile,
                )
            }
        }
    }

    @Volatile
    private var bootEnabled: Boolean = true

    @Volatile
    private var bootOnResume: Boolean = false

    private fun observeBootPreferences() {
        viewModelScope.launch {
            val prefs = context.pfpDataStore.data.first()
            bootEnabled = prefs[KEY_SHOW_BOOT] ?: true
            bootOnResume = prefs[KEY_BOOT_ON_RESUME] ?: false
            if (!bootEnabled) {
                _uiState.update { it.copy(showBootSequence = false) }
            }
        }
        viewModelScope.launch {
            context.pfpDataStore.data
                .map { (it[KEY_SHOW_BOOT] ?: true) to (it[KEY_BOOT_ON_RESUME] ?: false) }
                .distinctUntilChanged()
                .collect { (enabled, onResume) ->
                    bootEnabled = enabled
                    bootOnResume = onResume
                }
        }

        viewModelScope.launch {
            uiMediaStore.stamp
                .distinctUntilChanged()
                .collect {
                    val (video, audio) = withContext(Dispatchers.IO) {
                        val video = uiMediaStore.pathFor(com.psplauncher.core.domain.model.UiMediaSlot.BOOT_VIDEO)
                        val audio = resolveBootAudio(
                            customVideoPath = video,
                            customAudioPath = uiMediaStore.pathFor(com.psplauncher.core.domain.model.UiMediaSlot.BOOT_AUDIO),
                            bundledDefaultUri = com.psplauncher.core.domain.model.UiMediaSlot.BOOT_AUDIO
                                .bundledDefaultUri(context.packageName),
                        )
                        video to audio
                    }
                    _uiState.update { it.copy(bootVideoPath = video, bootAudioPath = audio) }
                }
        }
    }

    fun onHostResumed() {
        if (!bootEnabled || !bootOnResume) return
        _uiState.update { it.copy(showBootSequence = true) }
    }

    private fun observeMediaLaunch() {
        viewModelScope.launch {
            mediaLaunchGate.active.collect { request ->
                _uiState.update { it.copy(discCeremony = request?.let { r -> DiscCeremonyState(r.art) }) }
            }
        }
    }

    private suspend fun awaitDiscHandOff(art: Any?) = mediaLaunchGate.awaitHandOff(art)

    private fun selectedItemArt(): Any? =
        _uiState.value.currentItems.getOrNull(_uiState.value.selectedItemIndex)?.shelfCoverArt

    private fun launchAppWithDisc(packageName: String, art: Any?) {
        viewModelScope.launch {
            awaitDiscHandOff(art ?: appLauncherIcon(packageName))
            appCategoryRepository.launch(packageName)
        }
    }

    private fun appLauncherIcon(packageName: String): Any? =
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

    fun onDiscCeremonyHandOff() = mediaLaunchGate.onHandOff()

    fun onDiscCeremonyFinished() = mediaLaunchGate.onDismissed()

    private fun observeGameBoot() {
        viewModelScope.launch {
            gameBootGate.active.collect { request ->
                _uiState.update {
                    if (request == null && it.gameBootIsPreview) it
                    else it.copy(activeGameBoot = request, gameBootIsPreview = false)
                }
            }
        }
    }

    fun onGameBootHandOff() {
        if (!_uiState.value.gameBootIsPreview) gameBootGate.onPresentationFinished()
    }

    fun onGameBootComplete() {
        val wasPreview = _uiState.value.gameBootIsPreview
        _uiState.update { it.copy(activeGameBoot = null, gameBootIsPreview = false) }
        if (wasPreview) {
            uiMediaAudioPlayer.stop()
        } else {
            gameBootGate.onPresentationFinished()
            gameBootGate.onPresentationDismissed()
        }
    }

    fun previewBootSequence() {
        _uiState.update { it.copy(showBootSequence = true) }
    }

    fun previewGameBoot() {
        viewModelScope.launch {
            val (video, audio) = withContext(Dispatchers.IO) {
                val customVideo = uiMediaStore.pathFor(com.psplauncher.core.domain.model.UiMediaSlot.GAMEBOOT_VIDEO)
                customVideo to resolveGameBootAudio(
                    customVideoPath = customVideo,
                    customAudioPath = uiMediaStore.pathFor(
                        com.psplauncher.core.domain.model.UiMediaSlot.GAMEBOOT_AUDIO,
                    ),
                    defaultUri = com.psplauncher.core.ui.media.gameBootDefaultAudioUri(context.packageName),
                )
            }

            val previewArt = if (video != null) null else runCatching {
                gameRepository.observeAllGames().first().firstNotNullOfOrNull { it.discFaceUri }
            }.getOrNull()

            audio?.let {
                uiMediaAudioPlayer.play(
                    uri = it,
                    clipEndMs = com.psplauncher.themekit.UiMediaLimits.GAMEBOOT_SEQUENCE_MS,
                    label = "gameboot-preview",
                )
            }
            _uiState.update {
                it.copy(
                    activeGameBoot = com.psplauncher.feature.launcher.GameBootRequest(
                        gameTitle = "Preview",
                        videoPath = video,
                        audioPath = audio,
                        coverArt = previewArt,
                    ),
                    gameBootIsPreview = true,
                )
            }
        }
    }

    fun onBootSequenceComplete() {
        Timber.d("StartupSeq: boot sequence complete")
        _uiState.update { it.copy(showBootSequence = false) }
    }

    fun onStartupPermissionsSettled() {
        Timber.d("StartupSeq: notification permission settled")
        _uiState.update { it.copy(startupPermissionsSettled = true) }
    }

    private fun checkInitialSetup() {
        viewModelScope.launch {
            val prefs = context.pfpDataStore.data.first()
            when {
                prefs[KEY_INITIAL_SETUP_SEEN] == true ->
                    Timber.d("StartupSeq: initial setup already seen")
                hasExistingSetupConfig(prefs) || hasExistingLibrary() -> {
                    context.pfpDataStore.edit { it[KEY_INITIAL_SETUP_SEEN] = true }
                    Timber.i("StartupSeq: existing configuration found, wizard seeded as seen")
                }
                else -> {
                    Timber.i("StartupSeq: fresh install, opening first-run wizard")
                    _uiState.update { it.copy(activeSettingsScreen = INITIAL_SETUP_FIRST_RUN_SCREEN_ID) }
                }
            }

            _uiState.update { it.copy(initialSetupDecided = true) }
        }
    }

    private suspend fun hasExistingLibrary(): Boolean =
        runCatching { memoryCardRepository.getAll().isNotEmpty() }.getOrDefault(false)

    private fun logStartupSequence() {
        viewModelScope.launch {
            _uiState
                .map { Triple(it.startupPermissionsSettled, it.showBootSequence, it.activeSettingsScreen) }
                .distinctUntilChanged()
                .transformWhile { emit(it); it.second }
                .collect { (settled, boot, screen) ->
                    Timber.v(
                        "StartupSeq: permissionsSettled=$settled showBootSequence=$boot " +
                            "activeSettingsScreen=$screen xmbForegroundVisible=${!boot && screen == null}"
                    )
                }
        }
    }

    private fun markInitialSetupSeen() {
        viewModelScope.launch {
            context.pfpDataStore.edit { it[KEY_INITIAL_SETUP_SEEN] = true }
        }
    }

    fun openLibraryManager() {
        markInitialSetupSeen()
        _uiState.update { it.copy(activeSettingsScreen = "settings_library") }
    }

    fun goToLibrary() {
        markInitialSetupSeen()
        _uiState.update { it.copy(activeSettingsScreen = null, pendingSettingsAction = null) }
        openAllGamesFolder()
        viewModelScope.launch {
            val first = runCatching { gameRepository.observeGamesOnly().first() }
                .getOrDefault(emptyList())
                .filterNot { it.isMissing }
                .minByOrNull { it.title.lowercase() }
            if (first != null) {
                val idx = _uiState.value.currentItems.indexOfFirst { it.gameId == first.id }
                if (idx > 0) _uiState.update { it.copy(selectedItemIndex = idx) }
            }
        }
    }

    private var setupState: com.psplauncher.feature.launcher.SetupState =
        com.psplauncher.feature.launcher.SetupState()

    private fun observeSetupState() {
        viewModelScope.launch {
            setupStateProvider.observe().collect { fresh ->
                if (fresh != setupState) {
                    setupState = fresh

                    if (_uiState.value.currentItems.any { it.type == XMBItemType.EMPTY }) {
                        loadItemsForCategory(currentCategory())
                    }
                }
            }
        }
    }

    fun onUserInteraction() {
        lastInteractionMs = SystemClock.elapsedRealtime()
        if (!_uiState.value.hintsAutoHide) {
            _uiState.update { it.withHintsShownNow() }
        } else if (_uiState.value.showContextMenuHint ||
            _uiState.value.showAppDrawerHint ||
            _uiState.value.showSettingsHint
        ) {
            _uiState.update {
                it.copy(
                    showContextMenuHint = false,
                    showAppDrawerHint = false,
                    showSettingsHint = false,
                )
            }
        }
    }

    private fun observeLibrarySetupState() {
        viewModelScope.launch {
            context.pfpDataStore.data.collect { prefs ->
                val complete = prefs[KEY_SETUP_COMPLETE] ?: false
                _uiState.update { it.copy(librarySetupComplete = complete) }
            }
        }
    }

    private fun observeIconDisplayMode() {
        viewModelScope.launch {
            iconDisplayPreferences.modeFlow.collect { mode ->
                _uiState.update { it.copy(iconDisplayMode = mode) }
            }
        }
        viewModelScope.launch {
            iconDisplayPreferences.platformModesFlow.collect { modes ->
                _uiState.update { it.copy(iconDisplayModeByPlatform = modes) }
            }
        }
        viewModelScope.launch {
            iconDisplayPreferences.animatedIconsFlow.collect { enabled ->
                animatedIconsEnabled = enabled
                if (!enabled) _uiState.update { it.copy(focusedGameVideo = null) }
            }
        }
        viewModelScope.launch {
            iconDisplayPreferences.gameMetadataFlow.collect { visible ->
                _uiState.update { it.copy(gameMetadataVisible = visible) }
            }
        }
        viewModelScope.launch {
            iconDisplayPreferences.itemBackdropFlow.collect { on ->
                _uiState.update { it.copy(itemBackdropEnabled = on) }
            }
        }
        viewModelScope.launch {
            iconDisplayPreferences.snapPlacementFlow.collect { placement ->

                _uiState.update { it.copy(snapPlacement = placement, focusedGameVideo = null) }
            }
        }
        viewModelScope.launch {
            iconDisplayPreferences.lingerDelaySecondsFlow.collect { seconds ->
                icon1LingerMs = (seconds * 1_000f).toLong()
            }
        }
    }

    @Volatile private var animatedIconsEnabled = true

    private val ACCENT_SETTLE_MS = 220L

    @Volatile private var icon1LingerMs = ICON1_LINGER_MS

    private fun observeFocusedItemAccent() {
        viewModelScope.launch {
            _uiState
                .map { s -> s.currentItems.getOrNull(s.selectedItemIndex)?.takeIf { it.backdropArt.isNotEmpty() } }

                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .collectLatest { item ->
                    if (item == null) {
                        _uiState.update {
                            it.copy(focusedItemAccentArgb = null, focusedItemBackdrop = null)
                        }
                        return@collectLatest
                    }
                    kotlinx.coroutines.delay(ACCENT_SETTLE_MS)
                    val art = artworkAccent.resolve(*item.backdropArt.toTypedArray())

                    _uiState.update {
                        it.copy(
                            focusedItemAccentArgb = art?.accent,
                            focusedItemBackdrop = art?.uri,
                        )
                    }
                }
        }
    }

    private fun observeFocusedGameVideo() {
        viewModelScope.launch {
            _uiState
                .map { s ->
                    val item = s.currentItems.getOrNull(s.selectedItemIndex)

                    val eligible = item?.gameId != null && item.isRealGame &&
                        !s.hasBlockingOverlay &&
                        com.psplauncher.feature.xmb.ui.snapSiteFor(
                            s.snapPlacement,
                            resolveIconDisplay(item, s.iconDisplayMode, s.iconDisplayModeByPlatform).mode,
                            s.effectivePanelPage == DetailPanelPage.VIDEO,
                        ) != null
                    if (eligible) item.gameId else null
                }
                .distinctUntilChanged()
                .collectLatest { gameId ->
                    if (_uiState.value.focusedGameVideo?.gameId != gameId) {
                        _uiState.update { it.copy(focusedGameVideo = null) }
                    }
                    if (gameId == null) return@collectLatest
                    kotlinx.coroutines.delay(icon1LingerMs)
                    if (!videoSnapsAllowed()) {
                        Timber.d("ICON1: gates vetoed playback for game $gameId (toggle/battery/thermal)")
                        return@collectLatest
                    }

                    val uri = artworkStore.find(gameId, com.psplauncher.feature.artwork.store.ArtworkKind.ICON1)
                        ?: artworkStore.find(gameId, com.psplauncher.feature.artwork.store.ArtworkKind.VIDEO)
                    if (uri == null) {
                        Timber.d("ICON1: no icon video stored for game $gameId (enable Download Video Snaps + rescrape)")
                        return@collectLatest
                    }
                    Timber.d("ICON1: playing snap for game $gameId from $uri")
                    _uiState.update {
                        it.copy(
                            focusedGameVideo = com.psplauncher.feature.xmb.ui.FocusedGameVideo(
                                gameId = gameId,
                                uri = uri,
                                placement = it.snapPlacement,
                            ),
                        )
                    }
                }
        }
    }

    private fun videoSnapsAllowed(): Boolean {
        if (!animatedIconsEnabled) return false
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        if (pm?.isPowerSaveMode == true) return false
        if ((pm?.currentThermalStatus ?: 0) >= android.os.PowerManager.THERMAL_STATUS_MODERATE) return false
        val bm = context.getSystemService(android.os.BatteryManager::class.java)
        val level = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        if (level in 1 until 20 && bm?.isCharging != true) return false
        return true
    }

    private fun observeTouchNavButtonMode() {
        viewModelScope.launch {
            context.pfpDataStore.data.collect { prefs ->
                val mode = com.psplauncher.core.domain.model.TouchNavButtonMode
                    .fromName(prefs[KEY_TOUCH_NAV_BUTTON])
                val sensitivity = com.psplauncher.core.domain.model.TouchSensitivity
                    .fromName(prefs[KEY_TOUCH_SENSITIVITY])
                val hintEnabled = prefs[KEY_CONTEXT_MENU_HINT] ?: ControllerHintPolicy.DEFAULT_ENABLED
                val hintDelaySeconds =
                    ControllerHintPolicy.clampDelay(
                        prefs[KEY_CONTEXT_MENU_HINT_DELAY_SECONDS] ?: ControllerHintPolicy.DEFAULT_DELAY_SECONDS
                    )
                val legibility = com.psplauncher.core.domain.model.IconLegibilityStyle
                    .fromName(prefs[KEY_ICON_LEGIBILITY])
                val fadeByDistance = prefs[KEY_FADE_BY_DISTANCE] ?: true
                val cardArtGrid = prefs[KEY_CARD_ART_GRID] ?: true
                val recentsIncludeApps = prefs[KEY_RECENTS_INCLUDE_APPS] ?: false
                val textShadow = prefs[KEY_TEXT_SHADOW] ?: true
                _uiState.update {
                    it.copy(
                        touchNavButtonMode = mode,
                        touchSensitivity = sensitivity,
                        contextMenuHintEnabled = hintEnabled,
                        contextMenuHintDelaySeconds = hintDelaySeconds,
                        iconLegibility = legibility,
                        fadeByDistance = fadeByDistance,
                        cardArtGrid = cardArtGrid,
                        recentsIncludeApps = recentsIncludeApps,
                        textShadow = textShadow,
                    )
                }
            }
        }
    }

    private fun observeBackgroundSettings() {
        viewModelScope.launch {
            context.pfpDataStore.data.collect { prefs ->
                val style = runCatching {
                    WaveStyle.valueOf(prefs[KEY_WAVE_STYLE] ?: WaveStyle.ANIMATED.name)
                }.getOrDefault(WaveStyle.ANIMATED)
                _uiState.update {
                    it.copy(
                        waveStyle            = style,
                        respectBatterySaver  = prefs[KEY_RESPECT_BATTERY] ?: true,
                        waveOverWallpaper    = prefs[KEY_WAVE_OVER_WALLPAPER] ?: false,
                        thermalThrottleAware = prefs[KEY_THERMAL_AWARE] ?: true,
                    )
                }
            }
        }
    }

    private fun observeWallpaper() {
        viewModelScope.launch {
            context.pfpDataStore.data.collect { prefs ->
                val path = prefs[KEY_CUSTOM_WALLPAPER]

                val validPath = if (path != null && java.io.File(path).exists()) path else null

                val motionPath = prefs[KEY_MOTION_WALLPAPER]
                    ?.takeIf { validPath != null && java.io.File(it).exists() }

                val accent = prefs[com.psplauncher.core.data.wallpaper.WallpaperLuminanceProbe.KEY_WALLPAPER_ACCENT]
                    ?.takeIf { validPath != null }
                _uiState.update {
                    it.copy(
                        customWallpaperPath = validPath,
                        motionWallpaperPath = motionPath,
                        wallpaperAccent = accent,
                    )
                }
            }
        }
    }

    companion object {
        private val KEY_WAVE_STYLE        = stringPreferencesKey("display_wave_style")

        private val KEY_RESPECT_BATTERY   = booleanPreferencesKey("display_battery_saver")

        private val KEY_WAVE_OVER_WALLPAPER = booleanPreferencesKey("display_wave_over_wallpaper")
        private val KEY_THERMAL_AWARE     = booleanPreferencesKey("display_thermal_aware")
        private val KEY_COLOR_SCHEME      = stringPreferencesKey("display_color_scheme")

        private val KEY_ACCENT_OVERRIDE   = longPreferencesKey("theme_accent_override")

        private val KEY_ICON_COLOR        = longPreferencesKey("theme_icon_color")

        private val KEY_TEXT_COLOR        = longPreferencesKey("display_text_color")

        private val KEY_XMB_SCALE         = androidx.datastore.preferences.core.floatPreferencesKey("display_xmb_scale")
        private val KEY_BAR_TOP_FRACTION  = androidx.datastore.preferences.core.floatPreferencesKey("display_bar_top_fraction")

        internal const val WAVE_IDLE_MS = 12_000L

        internal const val IDLE_HINT_POLL_MS  = 500L
        private val KEY_XMB_LAYOUT_ADJUST = stringPreferencesKey("display_xmb_layout_adjust")
        private val KEY_SETUP_COMPLETE    = booleanPreferencesKey("library_setup_complete")

        private val KEY_INITIAL_SETUP_SEEN = booleanPreferencesKey("initial_setup_seen")

        internal fun returnAddressFor(screenId: String?): String? =
            screenId.takeIf { it in WIZARD_SCREEN_IDS }

        internal val WIZARD_SCREEN_IDS: Set<String>
            get() = setOf(INITIAL_SETUP_SCREEN_ID, INITIAL_SETUP_FIRST_RUN_SCREEN_ID)

        internal const val INITIAL_SETUP_SCREEN_ID = "settings_initial_setup"

        internal const val INITIAL_SETUP_FIRST_RUN_SCREEN_ID = "settings_initial_setup_first"

        private val EXISTING_CONFIG_STRING_KEYS = listOf(
            stringPreferencesKey("library_rom_root_tree_uris"),
            stringPreferencesKey("library_rom_root_tree_uri"),
            stringPreferencesKey("music_root_tree_uris"),
            stringPreferencesKey("video_root_tree_uris"),
            stringPreferencesKey("photo_root_tree_uris"),
            stringPreferencesKey("artwork_folder_tree_uri"),

            stringPreferencesKey("sgdb_api_key"),
            stringPreferencesKey("igdb_client_id"),
            stringPreferencesKey("ss_username"),
            stringPreferencesKey("ra_username"),
            stringPreferencesKey("steam_id64"),
        )

        internal fun hasExistingSetupConfig(prefs: androidx.datastore.preferences.core.Preferences): Boolean =
            prefs[KEY_SETUP_COMPLETE] == true ||
                EXISTING_CONFIG_STRING_KEYS.any { !prefs[it].isNullOrBlank() }
        private val KEY_CUSTOM_WALLPAPER  = stringPreferencesKey("display_custom_wallpaper")

        private val KEY_MOTION_WALLPAPER = stringPreferencesKey("display_motion_wallpaper")

        private val KEY_SHOW_BOOT       = booleanPreferencesKey("display_show_boot")
        private val KEY_BOOT_ON_RESUME  = booleanPreferencesKey("display_boot_on_resume")

        private val KEY_TOUCH_NAV_BUTTON  = stringPreferencesKey("interface_touch_nav_button")

        private val KEY_CONTEXT_MENU_HINT = booleanPreferencesKey("interface_context_menu_hint")
        private val KEY_CONTEXT_MENU_HINT_DELAY_SECONDS =
            floatPreferencesKey("interface_context_menu_hint_delay_seconds")

        private val KEY_TOUCH_SENSITIVITY = stringPreferencesKey("interface_touch_sensitivity")

        private val KEY_ICON_LEGIBILITY = stringPreferencesKey("display_icon_legibility")

        private val KEY_FADE_BY_DISTANCE = booleanPreferencesKey("display_fade_by_distance")

        private val KEY_CARD_ART_GRID = booleanPreferencesKey("display_card_art_grid")

        private val KEY_RECENTS_INCLUDE_APPS = booleanPreferencesKey("display_recents_include_apps")

        private val KEY_TEXT_SHADOW = booleanPreferencesKey("display_text_shadow")

        private const val ICON1_LINGER_MS = 1_500L
        private const val SETUP_ITEM_ID = "library_setup"
        private const val NO_CONSOLES_ITEM_ID = "no_consoles"

        private const val SETUP_GAP_ITEM_ID = "setup_gap"
        private const val NO_GAMES_ITEM_ID    = "no_games"
        private const val EMPTY_COLLECTION_ITEM_ID = "empty_collection"
        private const val EMPTY_FAVORITES_ITEM_ID = "empty_favorites"
        private const val EMPTY_CATEGORY_ITEM_ID = "empty_category"
        private const val ALL_GAMES_ITEM_ID = "all_games"

        private const val RESUME_DONE_FRACTION = 0.97f
        private const val ALL_GAMES_PLATFORM_ID = "__all_games__"
        private const val FAVORITES_ITEM_ID = "favorites_folder"

        internal const val FAVORITES_PLATFORM_ID = "__favorites__"
        private const val MISSING_ITEM_ID = "missing_folder"
        internal const val MISSING_PLATFORM_ID = "__missing__"
        private const val EMPTY_MISSING_ITEM_ID = "empty_missing"

        private const val MISSING_REASON = "File not found on last scan"
        private const val ADD_APPS_ITEM_ID = "add_apps"

        internal const val RECENT_APP_ID_PREFIX = "recentapp_"
        private const val ADD_GAMES_ITEM_ID = "add_games"
        private const val FIND_GAMES_ITEM_ID = "find_games"

        private const val APP_SHORTCUT_PLATFORM_ID = "app_shortcut"

        private const val ADD_MUSIC_FOLDER_ITEM_ID = "add_music_folder"
        internal const val ALL_MUSIC_ITEM_ID = "all_music"
        internal const val NOW_PLAYING_ITEM_ID = "now_playing"
        internal const val PLAYLISTS_ITEM_ID = "playlists"
        internal const val MUSIC_ARTISTS_ITEM_ID = "music_artists"
        internal const val MUSIC_ALBUMS_ITEM_ID = "music_albums"
        private const val ADD_MUSIC_APPS_ITEM_ID = "add_music_apps"
        private const val CREATE_PLAYLIST_ITEM_ID = "create_playlist"
        private const val ADD_TRACKS_ITEM_ID = "add_tracks"
        private const val EMPTY_PLAYLIST_ITEM_ID = "empty_playlist"

        internal const val MUSIC_APPS_CATEGORY_ID = "music"

        internal const val ALL_VIDEOS_ITEM_ID = "all_videos"
        internal const val VIDEO_COLLECTIONS_ITEM_ID = "video_collections"
        private const val RECENTLY_WATCHED_ITEM_ID = "recently_watched"
        private const val FAVORITE_VIDEOS_ITEM_ID = "favorite_videos"
        private const val VIDEO_PLAYLISTS_ITEM_ID = "video_playlists"
        private const val CREATE_VIDEO_PLAYLIST_ITEM_ID = "create_video_playlist"
        internal const val VIDEO_LIBRARIES_ITEM_ID = "video_libraries"
        private const val ADD_VIDEOS_ITEM_ID = "add_videos"
        private const val ADD_VIDEO_APPS_ITEM_ID = "add_video_apps"
        internal const val VIDEO_APPS_CATEGORY_ID = "videos"

        internal const val ALL_PHOTOS_ITEM_ID = "all_photos"
        internal const val CAMERA_ITEM_ID = "photo_camera"
        private const val ADD_PHOTO_LIBRARY_ITEM_ID = "add_photo_library"
        internal const val PHOTO_ALBUMS_ITEM_ID = "photo_albums"
        internal const val OPEN_READER_ITEM_ID = "library_open_reader"
        internal const val BOOK_SHELVES_ITEM_ID = "library_shelves"
        internal const val BOOK_SERIES_ITEM_ID = "library_series"
        internal const val ALL_BOOKS_ITEM_ID = "all_books"
        private const val ADD_BOOK_FOLDER_ITEM_ID = "add_book_folder"
        private const val ADD_LIBRARY_APPS_ITEM_ID = "add_library_apps"

        private const val RECENTLY_PLAYED_LIMIT = 20
        internal const val ADD_MENU_ITEM_ID = "add_menu"
        internal const val QUICK_SEARCH_ITEM_ID = "quick_search"
        internal const val SEARCH_ITEM_ID = "library_search"

        private const val SEARCH_RESULTS_PER_LIBRARY = 40
        private const val NETWORK_CATEGORY_ID = "network"

        private const val LIBRARY_APPS_CATEGORY_ID = BuiltInCategory.LIBRARY
        private const val ADD_PHOTO_APPS_ITEM_ID = "add_photo_apps"
        private const val PHOTO_APPS_CATEGORY_ID = "photos"

        internal const val MEMORY_CARD_ASSET_URI =
            "file:///android_asset/systems/physical-media/_default.png"

        private const val MUSIC_PLAYER_MENU_MARKER = "__music_player__"

        val FALLBACK_CATEGORIES: List<Category> =
            com.psplauncher.core.domain.model.BUILT_IN_CATEGORIES

        private val ANDROID_ITEMS = com.psplauncher.feature.appbar.AppFilter.entries.map { filter ->
            XMBItem(
                id = "drawer_${filter.name.lowercase()}",
                title = filter.label,
                subtitle = filter.subtitle,
            )
        }

        internal const val ANDROID_SETTINGS_ITEM_ID = "settings_android_system"

        internal const val OPEN_SETTINGS_ITEM_ID = "settings_open"

        internal val SETTINGS_ROOT_ITEMS = listOf(
            XMBItem(
                id = OPEN_SETTINGS_ITEM_ID,
                title = "Settings",
                subtitle = "Library, emulators, appearance, media & system",
            ),
            XMBItem(id = ANDROID_SETTINGS_ITEM_ID, title = "Android Settings", subtitle = "Opens device settings"),
        )
    }

    private fun canonicalXmbCategories(categories: List<Category>): List<Category> =
        canonicalXmbCategories(categories, FALLBACK_CATEGORIES)

    private fun defaultXmbCategoryIndex(categories: List<Category>): Int =
        categories.indexOfFirst { it.id == BuiltInCategory.RECENTLY_PLAYED }
            .takeIf { it >= 0 }
            ?: categories.indexOfFirst { it.id == BuiltInCategory.GAMES }
                .takeIf { it >= 0 }
            ?: 0
}
