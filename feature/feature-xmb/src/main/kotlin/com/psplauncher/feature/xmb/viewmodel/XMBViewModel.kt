package com.psplauncher.feature.xmb.viewmodel

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
import com.psplauncher.core.domain.model.CategoryType
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameCollection
import com.psplauncher.core.domain.model.GameContentType
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.HiddenPlacement
import com.psplauncher.core.domain.model.HideLocationType
import com.psplauncher.core.domain.model.IconDisplayMode
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
import com.psplauncher.core.ui.notification.BackgroundTaskNotifier
import com.psplauncher.core.ui.sound.MenuSound
import com.psplauncher.core.ui.theme.DefaultPFPColors
import com.psplauncher.core.ui.theme.PFPColors
import com.psplauncher.core.ui.wave.WaveStyle
import com.psplauncher.feature.appbar.AppCategoryRepository
import com.psplauncher.feature.appbar.CategorizedApp
import com.psplauncher.feature.appbar.LauncherShortcutRepository
import com.psplauncher.feature.launcher.LaunchDispatchResult
import com.psplauncher.feature.launcher.LaunchRecoveryAction
import com.psplauncher.feature.launcher.LaunchSource
import com.psplauncher.feature.launcher.ResolvedLaunch
import com.psplauncher.feature.launcher.corePathFor
import com.psplauncher.feature.artwork.api.ArtworkRepository
import com.psplauncher.feature.library.scanner.LibraryScanner
import com.psplauncher.feature.library.scanner.ScanStatus
import com.psplauncher.feature.library.scanner.scanOutcomeMessage
import com.psplauncher.feature.xmb.R
import com.psplauncher.feature.xmb.gamepad.GamepadInputHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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


// ── Context menu types ────────────────────────────────────────────────────────

data class XMBContextMenu(
    val title: String,
    val items: List<XMBContextMenuItem>,
    val selectedIndex: Int = 0,
    // Identifies the source of the menu (platform card, game, or app)
    val platformId: String? = null,
    // Set on the "All Games" card's menu (which is not a real Memory Card).
    val isAllGames: Boolean = false,
    val gameId: Long? = null,
    val packageName: String? = null,
    // The category the app is being acted on from (for remove/pin)
    val categoryContext: String? = null,
    // Set on the category-picker submenu: "move" or "add"
    val pendingAppAction: String? = null,
    // Set on the "Add to Collection" submenu — the game being added.
    val collectionGameId: Long? = null,
    // Set on a collection row's own options menu (rename / delete / open).
    val collectionRowId: Long? = null,
    // Host app's launcher-shortcut id when the menu is for a harvested per-game entry.
    val shortcutId: String? = null,
    // Captured legacy INSTALL_SHORTCUT launch intent when the menu is for such an entry.
    val launchIntentUri: String? = null,
    // Set on a music folder's options menu / a music track's options menu.
    val musicFolderId: String? = null,
    val musicTrackId: String? = null,
    // Set on a playlist row's options menu, and as context on a track menu opened inside a playlist
    // (so "Remove from this Playlist" knows which playlist).
    val playlistId: Long? = null,
    // Set on the "Add to Playlist" submenu — the track being added (marks that submenu).
    val playlistPickerTrackId: String? = null,
    // Set on a video playlist row's options menu.
    val videoPlaylistId: Long? = null,
    // Set on a video file's options menu.
    val videoFileId: String? = null,
    // Set on a video library card's options menu.
    val videoLibraryId: String? = null,
    // Set on the "Add to Playlist" submenu opened for a video (marks that submenu).
    val videoPlaylistPickerVideoId: String? = null,
    // Set on a photo row's options menu.
    val photoFileId: String? = null,
    // Set on a photo library (Album) card's options menu.
    val photoLibraryId: String? = null,
)

data class XMBContextMenuItem(
    val id: String,
    val label: String,
    val isDestructive: Boolean = false,
    // Renders a checkmark (e.g. collections the game already belongs to).
    val checked: Boolean = false,
)

// Drives the shared text-input dialog. Creating a collection is the default; the optional
// targets repurpose it for renames and the game Edit Title / Edit Note actions.
data class CollectionNameDialogState(
    val title: String,
    val initialText: String = "",
    val forGameId: Long? = null,
    // When set, confirming renames this collection instead of creating a new one.
    val renameCollectionId: Long? = null,
    // When set, confirming saves a display-title override for this game (blank resets it).
    val editTitleGameId: Long? = null,
    // When set, confirming saves this game's note (blank clears it).
    val editNoteGameId: Long? = null,
)

// A simple read-only message dialog (e.g. "View File Location"). Dismissed with A/B or tap.
data class InfoDialogState(
    val title: String,
    val message: String,
)

// ── Color-scheme picker (opened from Settings ▸ Themes ▸ Color Scheme) ──────────
// A PSP-style submenu that previews each scheme live as the cursor moves over it.

data class ColorSchemePickerState(
    val options: List<ColorSchemeOption>,
    val selectedIndex: Int = 0,
)

data class ColorSchemeOption(
    val scheme: XmbColorScheme?,
    val label: String,
    val sublabel: String,
    val swatch: Long,
    val isCustom: Boolean = false,
)

data class CustomColorPickerState(
    val hue: Float,
    val saturation: Float,
    val brightness: Float,
    val selectedChannel: Int = 0,
)

// ── Live "Adjust XMB Layout" editor ────────────────────────────────────────────
// A full-screen editor rendered OVER the real XMB (settings closed): D-pad / shoulder buttons or
// on-screen sliders drive the cross's scale + horizontal + vertical placement live. [draft] is the
// value applied while editing; [original] is restored on cancel; [bucketKey] is the form-factor the
// result is saved under so a handheld and a foldable each keep their own tuning.
data class XmbLayoutAdjustSession(
    val draft: com.psplauncher.themekit.XmbLayoutAdjust,
    val original: com.psplauncher.themekit.XmbLayoutAdjust,
    val bucketKey: String,
    val slidersVisible: Boolean = false,
)

// ── Live "Customize XMB Icons" editor ─────────────────────────────────────────
// A translucent editor rendered OVER the real XMB (settings closed), shaped after
// XmbLayoutAdjustSession. There is deliberately NO draft/commit pair: picks apply to the
// CustomIconStore immediately (the XMB behind updates as each lands — the whole point of a
// live editor), and Reset / Reset All are the undo. [groups] is the editable tab order;
// [groupIndex]/[slotIndex] place the gamepad cursor; [message] is the last import outcome.
data class CustomIconSession(
    val groups: List<com.psplauncher.themekit.IconSlot.Group>,
    val groupIndex: Int = 0,
    val slotIndex: Int = 0,
    val message: String? = null,
    /** Bumped when the store's contents change, so the overlay re-reads the icons map. */
    val revision: Int = 0,
) {
    /** The group the cursor is currently on. */
    val group: com.psplauncher.themekit.IconSlot.Group get() = groups[groupIndex]

    /** The focused slot, or null when the group has no slots. */
    val focusedSlot: com.psplauncher.themekit.IconSlot?
        get() = CustomizableIcons.group(group).getOrNull(slotIndex)
}


// ── Installed-app picker ───────────────────────────────────────────────────────
// A reusable multi-select picker over installed apps. Where the selection goes is
// described by the target, so the same flow serves the Android Library ("Find Games")
// and app sections like Video / Music ("Add Apps").

sealed interface AppPickerTarget {
    // Selected apps become launchable Game entries under an Android-style Memory Card.
    data class AndroidGames(val platformId: String) : AppPickerTarget
    // Selected apps become launchable shortcuts in an app category (Video, Music, …).
    data class CategoryShortcuts(val categoryId: String) : AppPickerTarget
}

data class AppPickerEntry(
    val packageName: String,
    val label: String,
    // Icon resolved once, off the main thread, when the picker opens — never per-tile in
    // composition (a 7-wide grid would run PackageManager binder calls on the UI thread).
    val icon: android.graphics.drawable.Drawable? = null,
)

// Columns of the picker grid — one denser than the App Drawer's 6, and declared beside
// PICKER_GRID_COLUMNS in AppPickerLogic so the layout and the navigation math can't drift.
const val PICKER_GRID_COLUMNS = 7

data class AppPickerState(
    val title: String,
    val target: AppPickerTarget,
    val apps: List<AppPickerEntry>,
    val selected: Set<String> = emptySet(),
    /** Membership at open time — the baseline the Apply diff runs against. */
    val initialSelected: Set<String> = emptySet(),
    /** Index into `visibleApps()`, NOT into `apps` — the Confirm row is gone (Apply moved to the footer). */
    val focusedIndex: Int = 0,
    val query: String = "",
    val searchActive: Boolean = false,
    val confirmingRemovals: Boolean = false,
    /**
     * Which confirm-panel option the gamepad cursor sits on while [confirmingRemovals] is up.
     * The modal is a hard input boundary: while it is open, dpad navigation drives THIS cursor,
     * never the grid behind the scrim.
     */
    val confirmFocusedOption: Int = CONFIRM_CANCEL,
    /** Mirrors AppDrawerUiState.usingTouch — hides the cursor and suppresses auto-scroll. */
    val usingTouch: Boolean = false,
) {
    companion object {
        const val CONFIRM_CANCEL = 0
        const val CONFIRM_REMOVE = 1
    }
}

// ── Music navigation ───────────────────────────────────────────────────────────
// Which Music sub-screen is open. The Music root shows the static items (Now Playing / Playlist /
// Music Apps) plus the single "All Music" memory-card item; drilling into any of them swaps the
// item list without leaving the Music category.

// Which Video sub-screen is open. Mirrors [MusicNav]: the Video root shows the static items
// (All Videos / Video Libraries / Android Video Apps + add rows); drilling swaps the item list
// without leaving the Video category.
sealed interface VideoNav {
    data object Root : VideoNav
    data object AllVideos : VideoNav
    // "Collections" groups the three curated views (Recently Watched / Favorites / Playlists) under
    // one root entry so the Video root stays uncluttered. Those three live one level below it.
    data object Collections : VideoNav
    data object RecentlyWatched : VideoNav
    data object Favorites : VideoNav
    data object Playlists : VideoNav
    data class Playlist(val id: Long, val name: String) : VideoNav
    data object Libraries : VideoNav
    data class Library(val id: String, val name: String) : VideoNav
    data object VideoApps : VideoNav
}

// The three views that live under "Collections" — used so BACK from them returns to Collections
// rather than the Video root. A Playlist backs to Playlists first (handled separately).
private val VideoNav.isVideoCollectionChild: Boolean
    get() = this == VideoNav.RecentlyWatched || this == VideoNav.Favorites || this == VideoNav.Playlists

// Which Photo sub-screen is open. Mirrors [VideoNav], kept deliberately minimal (PSP memory-card
// style): the Photo root shows All Photos / Camera / Add Photo Library / the user's Albums;
// drilling swaps the item list without leaving the Photo category.
/**
 * Library (books) sub-navigation. Shelves are the configured root folders; a shelf lists the books
 * found under it. Deliberately shallower than Photo: no favorites, no collections.
 */
sealed interface BooksNav {
    data object Root : BooksNav
    data object AllBooks : BooksNav
    data object Shelves : BooksNav
    data class Shelf(val id: String, val name: String) : BooksNav
    /** The list of series found across every shelf. */
    data object SeriesList : BooksNav
    /** One series, listed in reading order. Keyed by name: a series has no id of its own. */
    data class Series(val name: String) : BooksNav
}

/**
 * One series and what the Library knows about it. Derived from the books rather than stored: a
 * series is whatever the scanned files agree to call one, so there is nothing to keep in step.
 */
data class BookSeries(val name: String, val bookCount: Int, val coverUri: String?)

sealed interface PhotoNav {
    data object Root : PhotoNav
    data object AllPhotos : PhotoNav
    data object Albums : PhotoNav
    data object PhotoApps : PhotoNav
    data class Library(val id: String, val name: String) : PhotoNav
}

// A request to open the fullscreen photo viewer. [libraryId] scopes L1/R1 next/previous to the
// list the photo was opened from (null = All Photos). [openWallpaperPreview] opens straight into
// the wallpaper preview (the row's "Set as Launcher Wallpaper" context action) — still
// preview-first, so nothing changes until the user confirms.
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
    data object MusicApps : MusicNav
}

// this stays a sealed interface so the drill/back plumbing keeps a stable type.


// ── Settings ───────────────────────────────────────────────────────
// The Settings category is two rows: one that opens the settings screens and one that opens
// Android's own. It used to be the Android row plus six section rows, each of which drilled into
// a two-pane flyout listing that section's screens.
//
// The flyout is gone because the settings screens grew their own section rail, which lists the
// same six sections AND the current one's screens down the left of every page. Keeping the
// crossbar version would have meant two ways to reach the same screen, one of which put the tree
// on the home screen and the other inside it.

// ── Fullscreen music browser (Settings-style, searchable) ───────────────────────
// Opened from the "Music" and "Playlist" root items as a fullscreen overlay (not the inline XMB
// list). Rows reuse XMBItem so the same row visuals/actions apply: tracks play, playlists drill in,
// plus Create Playlist / Add Tracks action rows.

sealed interface MusicBrowserView {
    data object AllMusic : MusicBrowserView
    data object Playlists : MusicBrowserView
    data class Playlist(val id: Long, val name: String) : MusicBrowserView
}

data class MusicBrowserState(
    val view: MusicBrowserView,
    val title: String,
    val query: String = "",
    val rows: List<XMBItem> = emptyList(),   // already filtered + sorted, ready to render
    val selectedIndex: Int = 0,
    // Status-bar style sort hint, non-null on track views (AllMusic / a Playlist's tracks).
    val sortLabel: String? = null,
    // Bumped to snap the list back to the top (sort change / query change).
    val scrollToTopToken: Int = 0,
)

// Drives the "New / Rename Playlist" text dialog. When [forTrackId] is set, the freshly created
// playlist immediately receives that track.
data class PlaylistNameDialogState(
    val title: String,
    val initialText: String = "",
    val forTrackId: String? = null,
    // When set, confirming renames this playlist instead of creating a new one.
    val renamePlaylistId: Long? = null,
    // Video playlist variant: routes create/rename to the video repository instead of music.
    val videoContext: Boolean = false,
    // When set (video create), the freshly-created playlist immediately receives this video.
    val forVideoId: String? = null,
)

// Multi-select picker over all scanned tracks, used by a playlist's "Add Tracks" row.
data class MusicTrackPickerState(
    val playlistId: Long,
    val playlistName: String,
    val tracks: List<MusicTrack>,
    val selected: Set<String> = emptySet(),
    val selectedIndex: Int = 0,   // index 0 = the Confirm row; 1..n = tracks
)

// ── Main XMB state ────────────────────────────────────────────────────────────

/**
 * One rung of the home screen's drill-out ladder — what a single Back (button, left-edge pull,
 * leftward swipe, or D-pad LEFT) unwinds next. [XMBUiState.drillOutStep] picks the rung from state
 * and [XMBViewModel.backOutOfDrill] performs it; keeping the choice separate from the doing is what
 * lets the ladder's precedence be pinned by a plain state test, with no ViewModel to build.
 */
enum class DrillOutStep {
    MUSIC,
    /** A video Library backs out to the Libraries list before leaving Video. */
    VIDEO_LIBRARY,
    VIDEO_PLAYLIST,
    VIDEO_COLLECTION_CHILD,
    VIDEO,
    /** A photo album backs out to the Albums list before leaving Photo. */
    PHOTO_LIBRARY,
    PHOTO,
    /** A shelf backs out to the Shelves list before leaving Library. */
    LIBRARY_SHELF,
    /** A series backs out to the Series list before leaving Library. */
    LIBRARY_SERIES,
    LIBRARY,
    /** A Games platform folder, collection, All Games or Favorites. */
    PLATFORM_FOLDER,
}

data class XMBUiState(
    // ── Horizontal axis: platforms (SD cards) + utility tabs ──────────────
    val categories: List<Category> = emptyList(),
    val selectedCategoryIndex: Int = 0,
    val platformGameCounts: Map<String, Int> = emptyMap(),
    // Total real games (content_type = GAME) across all platforms — the "All Games" count.
    val allGamesCount: Int = 0,
    // Count of favorited entries — drives the Games-root "Favorites" item visibility.
    val favoritesCount: Int = 0,
    // Games flagged missing by the reconciler. Tracked separately because every other count here
    // comes from queries that filter is_missing = 0, so missing rows are invisible to them.
    val missingCount: Int = 0,
    val selectedPlatformId: String? = null,
    // When non-null, the Games category is showing the contents of a user collection.
    val selectedCollectionId: Long? = null,
    // User-created collections, ordered, shown under "All Games" in the Games root.
    val collections: List<GameCollection> = emptyList(),
    // Music drill-down: which Music sub-screen is open (Root shows the static items + All Music).
    val musicNav: MusicNav = MusicNav.Root,
    val musicFolders: List<com.psplauncher.core.domain.model.MusicFolder> = emptyList(),
    // Last-seen playlist lists, cached so the drill flyout can show a specific playlist's siblings.
    val musicPlaylists: List<com.psplauncher.core.domain.model.Playlist> = emptyList(),
    val videoPlaylists: List<com.psplauncher.core.domain.model.VideoPlaylist> = emptyList(),
    // PSP-style sort (X / Square). Tracked per context so switching categories doesn't carry a
    // music sort into games. sortLabel is the status-bar hint, non-null only on a sortable list.
    val gameSortMode: XmbSortMode = XmbSortMode.TITLE,
    val musicSortMode: XmbSortMode = XmbSortMode.TITLE,
    val videoSortMode: XmbSortMode = XmbSortMode.TITLE,
    val bookSortMode: XmbSortMode = XmbSortMode.TITLE,
    val sortLabel: String? = null,
    // In-app music player: visible when a song is selected; playback state mirrors the controller.
    val musicPlayerVisible: Boolean = false,
    val musicPlayback: com.psplauncher.feature.xmb.music.MusicPlaybackState =
        com.psplauncher.feature.xmb.music.MusicPlaybackState(),

    // ── Vertical axis: games / settings items ─────────────────────────────
    val currentItems: List<XMBItem> = emptyList(),
    val selectedItemIndex: Int = 0,
    // Non-null while drilled into a Games sub-item (a platform card, All Games, Favorites, or a
    // collection): the parent's label, which drives the two-pane "flyout" listing (parent on the
    // left, children in a centre-locked column on the right). Null = normal single-column list.
    val drillTitle: String? = null,
    // The current category's sibling items (All Games / Favorites / collections / memory cards),
    // shown as the flyout's left icon column (PSP-style); [drillSiblingIndex] is the one currently
    // drilled into, which sits centred on the arrow. Empty when not drilled in.
    val drillSiblings: List<XMBItem> = emptyList(),
    val drillSiblingIndex: Int = 0,
    // Bumped whenever the list should snap back to the top regardless of cursor position — e.g. a
    // sort cycle. The item list scrolls to item 0 each time this changes (keyed reorders otherwise
    // keep the viewport anchored to the old top item).
    val scrollToTopToken: Int = 0,

    // ── Input source (drives the on-screen touch-navigation button) ────────
    // True when touch was the most recent input, false when a controller/key was. Flips only on a
    // real input event, so the contextual button doesn't flicker.
    val lastInputWasTouch: Boolean = false,
    // The user's chosen controller button-glyph style (Settings ▸ Controller ▸ Display Type).
    // True when the user has been idle on an item that has a context menu, while touch controls
    // are active and no overlay is up — drives the small "Options" hint pill. See
    // XMBViewModel's idle-timer loop for the gate conditions.
    val showContextMenuHint: Boolean = false,
    // True when the user has been idle inside the App Drawer with a controller — the drawer's own
    // contextual hint bar (see shouldShowAppDrawerHint). Deliberately a separate flag from
    // showContextMenuHint: the drawer is a blocking overlay, so the XMB pill's gate is false
    // exactly while the drawer is open; AppDrawerScreen renders its own pill from this flag.
    val showAppDrawerHint: Boolean = false,
    // True when the user has been idle on the Sound settings screen with a controller. Settings
    // overlays are blocking by design, so this needs its own idle flag rather than reusing the XMB
    // context-menu hint (whose blocking-overlay gate would always suppress it).
    val showSettingsHint: Boolean = false,
    // User setting (Display ▸ Context Menu Hint). When false the idle hint never shows.
    val contextMenuHintEnabled: Boolean = true,
    // User-configured idle delay in seconds, clamped to 1..5 and defaulting to the original 2.5s.
    val contextMenuHintDelaySeconds: Float = 2.5f,
    val touchNavButtonMode: com.psplauncher.core.domain.model.TouchNavButtonMode =
        com.psplauncher.core.domain.model.TouchNavButtonMode.AUTO,
    // Swipe sensitivity for the XMB gesture layer (Settings ▸ Display ▸ Touch Sensitivity).
    val touchSensitivity: com.psplauncher.core.domain.model.TouchSensitivity =
        com.psplauncher.core.domain.model.TouchSensitivity.NORMAL,

    // ── Background + rendering ────────────────────────────────────────────
    // A custom wallpaper, when set, automatically replaces the wave.
    val waveStyle: WaveStyle = WaveStyle.ANIMATED,
    // When set (Settings ▸ Display, both default-on), the wave animation freezes while the system is
    // in battery-saver mode / thermally throttling — the wave is a non-essential flourish, so it
    // shouldn't compete for power when the device is trying to conserve it.
    val respectBatterySaver: Boolean = true,
    val thermalThrottleAware: Boolean = true,
    val customWallpaperPath: String? = null,
    // Looping motion wallpaper (MP4/WebM/GIF) behind the XMB. Only meaningful together with
    // [customWallpaperPath] — the poster is the freeze/failure fallback, so on read "motion set,
    // poster missing" degrades to "no motion" rather than trying to recover.
    val motionWallpaperPath: String? = null,

    val showBootSequence: Boolean = true,
    // The user's boot media, when assigned (Settings ▸ Display ▸ Boot Sequence). Null means the
    // built-in logo animation / silence — both are supported, in any combination.
    val bootVideoPath: String? = null,
    val bootAudioPath: String? = null,
    // The GameBoot presentation currently on screen, from the launch gate or from the settings
    // preview. Null the rest of the time.
    val activeGameBoot: com.psplauncher.feature.launcher.GameBootRequest? = null,
    // True when [activeGameBoot] came from Settings ▸ Display ▸ GameBoot ▸ Preview, which must
    // never touch the gate — nothing is launching.
    val gameBootIsPreview: Boolean = false,
    // Startup choreography: the boot sequence holds on a black frame until MainActivity reports
    // the notification-permission dialog is out of the way, so the order on a fresh install is
    // permission dialog -> boot animation -> first-run setup wizard.
    val startupPermissionsSettled: Boolean = false,
    // True once checkInitialSetup has resolved (wizard opened, seeded, or already seen). The
    // boot animation also holds on this, so on a fresh install the wizard is guaranteed to be
    // composed beneath the boot overlay before the dissolve can reveal what's under it.
    val initialSetupDecided: Boolean = false,

    // ── Overlay screens ───────────────────────────────────────────────────
    val activeSettingsScreen: String? = null,
    // The drilled-into Settings L1 section — non-null while its two-pane flyout shows the L2 rows,
    // null at the flat section root. Deliberately NOT part of hasBlockingOverlay: the flyout is
    // XMB foreground, so input keeps driving the item list exactly like every other drill.
    // Settings ▸ Controller ▸ Left Backs Out. Mirrored from ControllerLayoutRepository so both the
    // XMB's own LEFT and the Settings overlay's read one value. Default true matches the pref's.
    val leftBacksOut: Boolean = true,
    val pendingSettingsAction: GamepadAction? = null,
    val activeAppDrawerFilter: String? = null,
    val pendingDrawerAction: GamepadAction? = null,
    val pendingGameDetailAction: GamepadAction? = null,
    val activeGameId: Long? = null,
    // True when the Game Detail screen should fire its Play action as soon as the game loads —
    // set by direct-launch confirms and the Options menu's "Launch Game" entry; cleared on close.
    val activeGameAutoLaunch: Boolean = false,
    // The specific disc to open (and auto-launch) when [activeGameId] is set — set by the game
    // context menu's "Choose Disc" so a direct-launch user can pick a non-primary disc. The
    // primary remains the default whenever this is null.
    val activeGameDiscId: Long? = null,
    // Global launch behavior: confirm on a game launches directly (true) or opens Detail (false).
    val directLaunch: Boolean = false,
    val activeAppId: Long? = null,
    // Where a collection created from the App Detail screen should live — the category the app
    // row was opened from when it renders collections, otherwise the Main Game default.
    val activeAppCollectionCategoryId: String = BuiltInCategory.GAMES,
    val pendingAppDetailAction: GamepadAction? = null,
    // ── Video ─────────────────────────────────────────────────────────────
    val videoNav: VideoNav = VideoNav.Root,
    val videoLibraries: List<com.psplauncher.core.domain.model.VideoLibrary> = emptyList(),
    val activeVideoId: String? = null,
    val pendingVideoDetailAction: GamepadAction? = null,

    // ── Photo ─────────────────────────────────────────────────────────────
    val photoNav: PhotoNav = PhotoNav.Root,
    val booksNav: BooksNav = BooksNav.Root,
    val bookLibraries: List<com.psplauncher.core.domain.model.BookLibrary> = emptyList(),
    // Derived from every scanned book, so the Series row's count is live.
    val bookSeries: List<BookSeries> = emptyList(),
    // Package name of the reader a book opens in, or null for the system chooser.
    val defaultReader: String? = null,
    val defaultReaderLabel: String? = null,
    val photoLibraries: List<com.psplauncher.core.domain.model.PhotoLibrary> = emptyList(),
    val activePhotoViewer: PhotoViewerRequest? = null,
    val pendingPhotoViewerAction: GamepadAction? = null,

    // ── Context menu (Y/Triangle) ─────────────────────────────────────────
    val activeContextMenu: XMBContextMenu? = null,


    // ── Color-scheme picker (Settings ▸ Themes ▸ Color Scheme) ─────────────
    val colorSchemePicker: ColorSchemePickerState? = null,
    val customColorPicker: CustomColorPickerState? = null,

    // ── App rename dialog ─────────────────────────────────────────────────
    val renameAppTarget: String? = null,    // package name being renamed
    val renameAppCurrent: String? = null,   // current label, prefills the field

    // ── Create-collection text dialog ─────────────────────────────────────
    val collectionNameDialog: CollectionNameDialogState? = null,

    // ── Create/rename-playlist text dialog ────────────────────────────────
    val playlistNameDialog: PlaylistNameDialogState? = null,

    // ── "Add Tracks" picker (inside a playlist) ───────────────────────────
    val musicTrackPicker: MusicTrackPickerState? = null,

    // ── Fullscreen searchable music browser (Music / Playlist) ─────────────
    val musicBrowser: MusicBrowserState? = null,

    // ── Simple read-only info dialog (e.g. file location) ──────────────────
    val infoDialog: InfoDialogState? = null,

    // ── One-time "finish setting up your Windows Library" prompt ───────────
    // Raised by the pin workflow when a PC shortcut arrived before setup was complete
    // (docs/windows-library-refactor-plan.md section 3); consumed on first XMB open.
    val showWindowsSetupPrompt: Boolean = false,

    // ── Launch recovery sheet (B1) ─────────────────────────────────────────
    // Non-null while the recovery sheet should be drawn over the shell.
    val launchRecovery: com.psplauncher.feature.launcher.LaunchRecoveryRequest? = null,

    // ── Installed-app picker (Android Library / Video / Music) ─────────────
    val appPicker: AppPickerState? = null,

    // ── Game picker (for adding games to gaming categories) ────────────────
    val gamePickerCategoryId: String? = null,
    val pendingGamePickerAction: GamepadAction? = null,

    // ── Misc ──────────────────────────────────────────────────────────────
    val iconStyle: GameIconStyle = GameIconStyle.PSP_RECTANGLE,
    // Global icon display mode (Custom ICON0 / Box Art / Physical Media / 3D Box) — the default
    // every console follows until it is given an override of its own. Per-game overrides ride on
    // each XMBItem; resolution happens at render via [resolveIconDisplay].
    val iconDisplayMode: IconDisplayMode = IconDisplayMode.DEFAULT,
    // Per-console overrides keyed by platform id; a console absent here follows [iconDisplayMode].
    val iconDisplayModeByPlatform: Map<String, IconDisplayMode> = emptyMap(),
    // Icon legibility treatment (PSP-style matte behind XMB silhouette glyphs), Display ▸
    // Appearance. Provided as LocalIconLegibility; NONE renders today's glyph exactly.
    val iconLegibility: com.psplauncher.core.domain.model.IconLegibilityStyle =
        com.psplauncher.core.domain.model.IconLegibilityStyle.DEFAULT,
    // "Solid Unfocused Icons": when true, unselected XMB icons skip the unfocused alpha dim
    // (selection still reads by icon size and label). Default false = today's dimming.
    val solidUnfocusedIcons: Boolean = false,
    // "Text Shadow": directional drop shadow behind XMB row subtitles (the faded gray helper
    // text), so it stays readable over bright wallpaper regions. Default on — without it the
    // subtitle is the only row label with no separation treatment.
    val textShadow: Boolean = true,
    // The focused game's ICON1 video snap — set only after the linger + battery gates pass.
    val focusedGameVideo: com.psplauncher.feature.xmb.ui.FocusedGameVideo? = null,
    // Where an approved snap plays. Lives in UiState rather than a @Volatile field because the
    // eligibility rule below reads it, and that rule is a map over this state: as UiState it
    // re-evaluates the moment the user changes the setting.
    val snapPlacement: com.psplauncher.core.domain.model.VideoSnapPlacement =
        com.psplauncher.core.domain.model.VideoSnapPlacement.DEFAULT,
    // Whether the focused game's scraped one-liner is drawn under its logo.
    val gameMetadataVisible: Boolean = true,
    /**
     * The focused game's own colour, read out of its artwork. Null on a non-game row, on art
     * with no dominant hue, and for the moment before it has been read -- in all three the XMB
     * keeps the user's theme, which is what it looked like before this existed.
     */
    val focusedGameAccentArgb: Long? = null,
    /**
     * The image behind the focused game: the first of its art candidates that actually decodes.
     *
     * NOT simply artworkUri, which is what this used to read. 125 of the 147 games on the real
     * library name an internal artwork path that no longer exists, so reading the named slot
     * showed the wallpaper for all of them and the per-game backdrop only ever appeared for the
     * five with a content:// one.
     */
    val focusedGameBackdrop: String? = null,
    val librarySetupComplete: Boolean = false,
    val themeColors: PFPColors = DefaultPFPColors,
    // Custom icon slots of the applied theme (theme slot key → CustomIcon); empty = the
    // theme tier contributes nothing. Provided as LocalXmbIconOverrides.
    val iconOverrides: Map<String, CustomIcon> = emptyMap(),
    // The user's per-slot picks (custom-icons dir), ABOVE the theme tier at every render
    // site. Empty = nothing customized; user picks survive theme switches by design.
    val customIcons: Map<String, CustomIcon> = emptyMap(),
    // Non-null while the live "Customize XMB Icons" editor is open (rendered over the real XMB).
    val customIconSession: CustomIconSession? = null,
    // One-shot: a saved-theme bundle awaiting the share sheet (Save as Theme… flow). Consumed
    // by XMBShell via onThemeShareConsumed once ACTION_SEND has fired.
    val pendingThemeShareFile: java.io.File? = null,
    // One-shot forwarded pad action for the icon editor (SELECT / OPTIONS / BACK); the overlay
    // consumes it via onCustomIconsActionConsumed.
    val pendingCustomIconsAction: GamepadAction? = null,
    // Non-null while the "Save as Theme…" name dialog is up over the icon editor.
    val saveThemeNameDialog: PlaylistNameDialogState? = null,
    // Per-theme XMB geometry (crossbar line, headroom, previous-item rise). DEFAULT holds the
    // pixel-tuned authentic-PSP values; imported themes may override (theme-kit XmbLayoutSpec).
    val layoutSpec: com.psplauncher.themekit.XmbLayoutSpec = com.psplauncher.themekit.XmbLayoutSpec.DEFAULT,
    // Whole-launcher UI scale (Display ▸ Scale & Layout) — applied as a density multiplier at
    // the shell root so every screen scales together to fit different devices. Legacy default used
    // when the active form-factor has no saved layout adjustment.
    val xmbScale: Float = 1f,
    // Saved "Adjust XMB Layout" tunings, keyed by form-factor bucket (XmbFormFactor.key). The shell
    // resolves the entry for the current screen; absent = fall back to [xmbScale] + theme bar line.
    val xmbLayoutAdjustMap: Map<String, com.psplauncher.themekit.XmbLayoutAdjust> = emptyMap(),
    // Non-null while the live layout editor is open (rendered over the real XMB).
    val xmbLayoutAdjust: XmbLayoutAdjustSession? = null,
) {
    // True when the user has drilled into a sub-item on the home screen (a Games platform/collection/
    // All Games/Favorites, or a Music sub-view). Drives the floating Back button and locks Left/Right
    // category switching until the user backs out.
    //
    // Defined as "there is a level to back out of" rather than as its own list of conditions: the
    // ladder below and this flag used to be two hand-written lists that had to agree, and three
    // callers (gamepad BACK, touch Back, and now D-pad LEFT) is exactly where they would drift.
    val isInSubItem: Boolean
        get() = drillOutStep != null

    // The single rung [XMBViewModel.backOutOfDrill] would unwind next, or null at the category
    // root. Order IS the precedence: two-level video/photo paths back out through their list
    // before leaving the section, so each press climbs exactly one level.
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

    // The item currently under the XMB cursor, or null.
    val focusedItem: XMBItem?
        get() = currentItems.getOrNull(selectedItemIndex)

    // True iff a Y/Triangle press on the focused item would open a context menu — the exact mirror
    // of XMBViewModel.onItemLongPress / dispatchGamepadAction(OPEN_CONTEXT_MENU)'s when-branches, so the
    // idle hint and the real trigger never drift apart. Computed (never stored) so it stays
    // current with the cursor without plumbing at every stepItem call site.
    val focusedItemHasContextMenu: Boolean
        get() = focusedItem?.hasContextMenu(this) == true

    // True iff an X/Square press would re-sort the list currently on screen — drives the Sort
    // half of the hint pill. Computed, like focusedItemHasContextMenu, so it tracks the cursor
    // and the drill level without plumbing.
    val canSortCurrentList: Boolean
        get() = activeSortModes() != null

    // Whether the bottom-right contextual button (App Drawer / Back) should be shown, per the
    // user's Touch Navigation Button setting. AUTO follows the last input source.
    val resolvedShowTouchButton: Boolean
        get() = when (touchNavButtonMode) {
            com.psplauncher.core.domain.model.TouchNavButtonMode.AUTO -> lastInputWasTouch
            com.psplauncher.core.domain.model.TouchNavButtonMode.ALWAYS_SHOW -> true
            com.psplauncher.core.domain.model.TouchNavButtonMode.ALWAYS_HIDE -> false
        }

    // True whenever something is layered over the main XMB. The gamepad dispatcher uses this
    // as a final guard so D-Pad/A never drives the category bar or item list behind an overlay.
    val hasBlockingOverlay: Boolean
        get() = showBootSequence ||
            activeGameBoot != null ||
            activeSettingsScreen != null ||
            activeAppDrawerFilter != null ||
            activeGameId != null ||
            activeAppId != null ||
            activeVideoId != null ||
            activePhotoViewer != null ||
            activeContextMenu != null ||
            colorSchemePicker != null ||
            customColorPicker != null ||
            xmbLayoutAdjust != null ||
            customIconSession != null ||
            saveThemeNameDialog != null ||
            appPicker != null ||
            gamePickerCategoryId != null ||
            renameAppTarget != null ||
            collectionNameDialog != null ||
            playlistNameDialog != null ||
            musicTrackPicker != null ||
            musicBrowser != null ||
            musicPlayerVisible ||
            infoDialog != null ||
            launchRecovery != null ||
            showWindowsSetupPrompt
}

enum class XMBItemType {
    STANDARD,
    ALL_GAMES,
    FAVORITES,
    // The Missing bucket: games whose ROM file was gone on the last trustworthy scan. Sits beside
    // All Games / Favorites and only appears when something is actually missing.
    MISSING,
    MEMORY_CARD,
    COLLECTION,
    MUSIC_FOLDER,
    MUSIC_TRACK,
    PLAYLIST,
    MUSIC_APPS,
    VIDEO_LIBRARY,
    VIDEO_FOLDER,
    VIDEO_FILE,
    VIDEO_APPS,
    VIDEO_RECENT,
    VIDEO_FAVORITES,
    VIDEO_COLLECTIONS,
    PHOTO_ALBUMS,
    PHOTO_FOLDER,
    // Library (books) rows.
    LIBRARY_SHELVES,
    LIBRARY_READER,
    LIBRARY_FOLDER,
    LIBRARY_BOOK,
    // The "Series" root row, and each series folder inside it.
    LIBRARY_SERIES,
    PHOTO_FILE,
    PHOTO_APPS,
    CAMERA,
    // "Add …" / "Create …" rows (add library/folder/apps/tracks, create playlist) — plus glyph.
    ADD_ACTION,
    EMPTY,
}

// PSP-style sort cycling (X / Square). Each list type cycles only the modes that make sense for
// it (see MUSIC_SORTS / GAME_SORTS); a shared enum keeps the status-bar label simple.
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

/**
 * Book ordering. SERIES groups a series together and puts it in reading order, which is the whole
 * point of the mode: a series read alphabetically by title is in no useful order at all.
 *
 * Books with no series sort last rather than first. Not every EPUB declares one, so on a mixed
 * library the alternative is a wall of unrelated titles above the series the user asked to see.
 * Within a series, an unnumbered book sorts after the numbered ones for the same reason.
 */
/**
 * Where a book sits WITHIN its series: by index, then by title for the unnumbered.
 *
 * One definition, used by both the SERIES sort mode and the Series folder. They have to agree or
 * the same three books read in one order on the flat list and another inside their own folder, and
 * nothing would catch that because each looks right on its own.
 */
private val BY_SERIES_POSITION = compareBy<com.psplauncher.core.domain.model.Book>(
    { it.seriesIndex ?: Double.MAX_VALUE },
    { it.displayTitle.lowercase() },
)

/** The books of one series, in reading order. */
internal fun List<com.psplauncher.core.domain.model.Book>.inSeriesOrder(): List<com.psplauncher.core.domain.model.Book> =
    sortedWith(BY_SERIES_POSITION)

/**
 * The series across a set of books, alphabetical, each carrying the cover of its earliest volume.
 *
 * Books declaring no series are simply absent. There is no "No series" bucket: the Books row
 * already lists everything, so a bucket holding over half the library would be a second, worse
 * copy of it.
 */
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

// Pure sort comparators (top-level so they're unit-testable). DATE_ADDED uses the autoincrement
// game id / file lastModified as the recency proxy; modes that don't apply fall back to title.
internal fun List<Game>.gameSorted(mode: XmbSortMode): List<Game> = when (mode) {
    XmbSortMode.RECENT_PLAYED -> sortedByDescending { it.lastPlayedAt ?: 0L }
    XmbSortMode.DATE_ADDED    -> sortedByDescending { it.id }
    else                      -> sortedBy { it.displayTitle.lowercase() }
}

/**
 * Where the cursor belongs after the list it is on is refreshed: on the same row, found by id.
 * Game lists sort by display title, so a rename re-sorts the list under the cursor. Keeping the
 * INDEX would leave it on whichever game moved into that slot. A row that is gone falls back to
 * the old index, clamped to the new list.
 */
internal fun cursorAfterRefresh(previous: List<XMBItem>, previousIndex: Int, next: List<XMBItem>): Int {
    val selectedId = previous.getOrNull(previousIndex)?.id
    val kept = selectedId?.let { id -> next.indexOfFirst { it.id == id } } ?: -1
    return if (kept >= 0) kept else previousIndex.coerceIn(0, (next.size - 1).coerceAtLeast(0))
}

// Projects a raw game snapshot for display-only counts. DAO-backed list flows already apply the
// same rule, but the category collector also drives card subtitles and must not count every disc.
internal fun List<Game>.projectGamesForDisplay(): List<Game> {
    val singles = filter { it.discSetKey == null && !it.isMissing }
    val sets = groupBy { it.discSetKey }
        .filterKeys { it != null }
        .values
        .mapNotNull { members ->
            val present = members.filterNot { it.isMissing }
            if (present.isEmpty()) return@mapNotNull null
            val display = members.firstOrNull { it.isDiscPrimary } ?: present.first()
            // A favorite on any member makes the logical set favorite; preserve that signal when
            // this snapshot feeds the Favorites count and card badges.
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

/**
 * True iff a Y/Triangle press on [this] item would open a context menu under [state] — the exact
 * mirror of `XMBViewModel.onItemLongPress` / `dispatchGamepadAction(OPEN_CONTEXT_MENU)`'s `when` branches.
 * Pure (no side effects) so the idle hint can read it without opening a menu, and so it is
 * unit-testable. The per-category `openXxxContextMenu` functions start with a category guard that
 * returns false on mismatch; those guards are reproduced here as category-id checks so this never
 * calls the side-effectful openers.
 */
fun XMBItem.hasContextMenu(state: XMBUiState): Boolean {
    val categoryId = state.categories.getOrNull(state.selectedCategoryIndex)?.id
    return when {
        // Music tracks / Now Playing / playlists / music-apps.
        categoryId == BuiltInCategory.MUSIC && (
            id == XMBViewModel.NOW_PLAYING_ITEM_ID ||
                type == XMBItemType.MUSIC_TRACK ||
                (type == XMBItemType.PLAYLIST && playlistId != null) ||
                (state.musicNav == MusicNav.MusicApps && packageName != null)
        ) -> true
        // Video files / libraries / playlists / video-apps.
        categoryId == BuiltInCategory.VIDEO && (
            (type == XMBItemType.VIDEO_FILE && id.startsWith("vid_")) ||
                (type == XMBItemType.VIDEO_FOLDER && id.startsWith("vlib_")) ||
                (type == XMBItemType.PLAYLIST && playlistId != null) ||
                (state.videoNav == VideoNav.VideoApps && packageName != null)
        ) -> true
        // Photo files / libraries / photo-apps.
        categoryId == BuiltInCategory.PHOTO && (
            (type == XMBItemType.PHOTO_FILE && id.startsWith("pho_")) ||
                (type == XMBItemType.PHOTO_FOLDER && id.startsWith("plib_")) ||
                (state.photoNav == PhotoNav.PhotoApps && packageName != null)
        ) -> true
        gameId != null -> true
        collectionId != null && type == XMBItemType.COLLECTION -> true
        type == XMBItemType.ALL_GAMES -> true
        platformId != null -> true
        packageName != null -> true
        else -> false
    }
}

/**
 * The sort modes valid for the list currently on screen, or null when it isn't sortable (the
 * Games memory-card root, the Music root, playlist lists, and app sections don't sort).
 *
 * Pure and top-level for the same reason as [XMBItem.hasContextMenu]: the idle hint has to ask
 * "can this list sort?" without triggering a sort, and both it and the real X/Square handler now
 * read one function, so the pill cannot promise an action the press won't perform.
 */
fun XMBUiState.activeSortModes(): List<XmbSortMode>? {
    val cat = categories.getOrNull(selectedCategoryIndex) ?: return null
    return when {
        cat.id == BuiltInCategory.MUSIC &&
            (musicNav == MusicNav.AllMusic || musicNav is MusicNav.Playlist) -> MUSIC_SORTS
        // Video lists sort, except the intrinsically-ordered ones (recency / manual playlist).
        cat.id == BuiltInCategory.VIDEO &&
            (videoNav == VideoNav.AllVideos || videoNav == VideoNav.Favorites ||
                videoNav is VideoNav.Library) -> VIDEO_SORTS
        // Book lists sort; the Library root and the shelf list are fixed rows, not a library.
        // A series folder is intentionally absent: it is always in reading order, which is the
        // whole reason it exists as a folder rather than a filter.
        cat.id == BuiltInCategory.LIBRARY &&
            (booksNav == BooksNav.AllBooks || booksNav is BooksNav.Shelf) -> BOOK_SORTS
        cat.id == BuiltInCategory.GAMES &&
            (selectedPlatformId != null || selectedCollectionId != null) -> GAME_SORTS
        cat.isGamingCategory -> GAME_SORTS
        else -> null
    }
}

/**
 * Rebuilds the crossbar from the canonical built-in definitions merged with each row's stored
 * fields. [categories] is the *visible* set, so a built-in the user hid is simply absent and
 * dropped here, which is what makes "Show On Bar" work. Settings is the one exception and is always
 * kept, since it is the only route back into category management.
 *
 * **Name and position come from the stored row, not the constant.** They did not until 2026-09-18,
 * and the result was a contradiction with a comment on each side of it: `CategoryRepositoryImpl`
 * says built-ins are ones "the user may hide/reorder but never delete" and its `move()` writes the
 * swapped positions, while this function rebuilt every built-in from [XMBViewModel.FALLBACK_CATEGORIES]
 * and then sorted by the constant's position. Reordering or renaming a built-in wrote to the
 * database and changed nothing on the bar. Category Manager offers both edits, so the bar honours
 * both; the id and the icon stay canonical because neither is editable by this route.
 *
 * Top-level and pure so the merge is unit-testable without a ViewModel, which is what was missing
 * when the bar and the repository drifted apart.
 */
internal fun canonicalXmbCategories(
    categories: List<Category>,
    fallbacks: List<Category>,
): List<Category> {
    val byId = categories.associateBy { it.id }
    val builtInIds = fallbacks.map { it.id }.toSet()

    val builtIns = fallbacks.mapNotNull { fallback ->
        val stored = byId[fallback.id]
        // Drop hidden built-ins (absent from the visible set); never drop Settings.
        if (stored == null && fallback.id != BuiltInCategory.SETTINGS) return@mapNotNull null
        fallback.copy(
            name             = stored?.name?.takeIf { it.isNotBlank() } ?: fallback.name,
            position         = stored?.position ?: fallback.position,
            accentColor      = stored?.accentColor,
            customIconUri    = stored?.customIconUri,
            filterRules      = stored?.filterRules,
            // Preserve the system-defined gaming flag from the DB (reconciled each launch);
            // without this the rebuilt Main Game category loses isGamingCategory, which hides
            // "Move to Category" for collections and suppresses live refresh.
            isGamingCategory = stored?.isGamingCategory ?: fallback.isGamingCategory,
        )
    }

    val customCategories = categories.filter { it.id !in builtInIds }

    return (builtIns + customCategories).sortedBy { it.position }
}

/**
 * The sort mode [cycle] is currently on, and the state with it changed.
 *
 * These two exist as a pair because the alternative was three `when` blocks keyed on list identity
 * whose last branch is `else -> gameSortMode`: one to read the mode for the status-bar label, one
 * to read it for the cycle, one to write the next one. A section added to [activeSortModes] but
 * missed in any of them does not fail, it silently cycles the GAMES mode and prints the games
 * label over somebody else's list. Two functions is the fewest that can express read and write, and
 * they are pure so the round trip is unit-testable.
 */
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

/**
 * Pure decision: should the idle context-menu hint be visible right now? Top-level so unit tests
 * can exercise it without a ViewModel instance. Gates:
 *  - the most recent input came from a controller (not touch);
 *  - no blocking overlay, no open context menu ([XMBUiState.hasBlockingOverlay],
 *    [XMBUiState.activeContextMenu]);
 *  - the pill has something true to say — the focused item has a context menu
 *    ([XMBUiState.focusedItemHasContextMenu]) or the current list can sort
 *    ([XMBUiState.canSortCurrentList]);
 *  - the idle delay [idleMs] has elapsed (>= IDLE_HINT_DELAY_MS).
 *
 * Deliberately NOT gated on [XMBUiState.isInSubItem]: drilled-in items (the game flyout, a
 * library's files) have context menus and can sort, so that gate hid the hint exactly where a
 * new user is most likely to need it. The App Drawer button it used to pair with is touch-only
 * and this hint is controller-only, so the two are never really on screen together anyway.
 */
fun shouldShowContextMenuHint(state: XMBUiState, idleMs: Long): Boolean =
    state.contextMenuHintEnabled &&
        !state.lastInputWasTouch &&
        !state.hasBlockingOverlay &&
        state.activeContextMenu == null &&
        (state.focusedItemHasContextMenu || state.canSortCurrentList) &&
        idleMs >= (state.contextMenuHintDelaySeconds * 1_000f).toLong()

/**
 * Pure decision: should the App Drawer's contextual controller hint bar be visible right now?
 * Top-level so unit tests can exercise it without a ViewModel instance. Gates:
 *  - the most recent input came from a controller (not touch);
 *  - the App Drawer is actually open ([XMBUiState.activeAppDrawerFilter] non-null);
 *  - no context menu is up (one can never sit over the drawer, but the gate stays symmetric with
 *    [shouldShowContextMenuHint]);
 *  - the idle delay [idleMs] has elapsed.
 *
 * Deliberately separate from [shouldShowContextMenuHint]: the drawer is a blocking overlay
 * ([XMBUiState.hasBlockingOverlay]), so the XMB pill's gate is false whenever the drawer is open —
 * and this gate is true only then. Both share the same idle clock and the
 * contextMenuHintEnabled / contextMenuHintDelaySeconds settings, so Display ▸ Context Menu Hint
 * toggles the drawer hint too.
 */
fun shouldShowAppDrawerHint(state: XMBUiState, idleMs: Long): Boolean =
    state.contextMenuHintEnabled &&
        !state.lastInputWasTouch &&
        state.activeAppDrawerFilter != null &&
        state.activeContextMenu == null &&
        idleMs >= (state.contextMenuHintDelaySeconds * 1_000f).toLong()

/**
 * Pure decision for the settings helper footer. It deliberately does not use
 * [XMBUiState.hasBlockingOverlay], because the settings screen itself is the overlay that owns
 * this footer. Keeping the same delay and enable setting as the XMB/App Drawer makes all helper
 * chrome appear on one timing contract.
 *
 * Any open settings screen qualifies — the gate is simply "a settings screen is up". SettingsScaffold
 * already renders the footer band on every non-wizard screen and falls back to the Enter/Back
 * prompts when the screen supplies no items of its own, so restricting this to a named screen only
 * ever left the other screens with a reserved band that could never fill in. The wizard passes its
 * own themed footer and never consults this flag.
 */
fun shouldShowSettingsHint(state: XMBUiState, idleMs: Long): Boolean =
    state.contextMenuHintEnabled &&
        !state.lastInputWasTouch &&
        state.activeSettingsScreen != null &&
        idleMs >= (state.contextMenuHintDelaySeconds * 1_000f).toLong()

/**
 * The scraped one-liner under a focused game's logo: year, genre, developer, player count, in
 * that order, separated by a middle dot. Any field the scraper never filled is simply left out
 * rather than printed empty, and a game with none of them gets null so nothing is drawn at all.
 *
 * Pure and top-level so the formatting is testable without a ViewModel: the interesting cases
 * are all absence, and absence is exactly what a UI test would be worst at catching.
 */
internal fun gameMetadataLine(
    releaseYear: Int?,
    genre: String?,
    developer: String?,
    players: String?,
): String? {
    fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    val parts = listOfNotNull(
        // Year 0 is the scraper's "unknown", not the year zero.
        releaseYear?.takeIf { it > 0 }?.toString(),
        genre.clean(),
        developer.clean(),
        // Stored as "1", "1-2", "1-5". The count is meaningless without the noun.
        players.clean()?.let { if (it == "1") "1 player" else "$it players" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString("   ·   ")
}

data class XMBItem(
    val id: String,
    val title: String,
    val artworkUri: String? = null,
    val heroUri: String? = null,        // PIC1 / hero background art
    val iconUri: String? = null,        // landscape 144:80 icon art (SGDB horizontal grid)
    val logoUri: String? = null,        // clear logo — the PIC0-style overlay on the hover bg
    // Icon-display-mode artwork + the game's per-mode override; resolved against the global
    // setting at render time by [resolveIconDisplay].
    val boxArtUri: String? = null,
    val physicalMediaUri: String? = null,
    val box3dUri: String? = null,
    val iconDisplayModeOverride: String? = null,
    val subtitle: String? = null,
    // One-line scraped metadata for a game row, shown under the PIC0 logo when the user has it
    // on. Formatted once here rather than carrying four nullable columns into the UI, and null
    // when the game was never scraped.
    val metadataLine: String? = null,
    val gameId: Long? = null,
    val platformId: String? = null,
    val collectionId: Long? = null,     // set on COLLECTION rows in the Games root
    val iconKey: String? = null,        // catalog icon key for COLLECTION rows (null = default memory-card art)
    val accentColor: Long? = null,
    val isFavorite: Boolean = false,
    val isAndroidApp: Boolean = false,
    // True for contentType GAME rows — real games open the Game Detail page on select, even when
    // package/shortcut-backed (Android/Windows gaming apps). Standard apps launch directly.
    val isRealGame: Boolean = false,
    val packageName: String? = null,
    // Host app's launcher-shortcut id for harvested per-game entries; launched via LauncherApps.
    val shortcutId: String? = null,
    // Captured legacy INSTALL_SHORTCUT launch intent (Intent.toUri); launched by parsing it.
    val launchIntentUri: String? = null,
    // Music: folder id (on MUSIC_FOLDER rows) and the track's SAF uri + mime (on MUSIC_TRACK rows).
    val musicFolderId: String? = null,
    val mediaUri: String? = null,
    val mimeType: String? = null,
    // Square album-cover art (on MUSIC_TRACK rows); a file:// uri cached during scan, may be null.
    val coverUri: String? = null,
    // Playlist id on PLAYLIST rows.
    val playlistId: Long? = null,
    // Text-only row: never draws a leading icon/tile and always shows its label, regardless of
    // selection: the row reads as plain text plus a reason rather than an icon and a title.
    val textOnly: Boolean = false,
    // Prestige Bones earned (player-card summary row only); renders "• N [bone glyph]" after the
    // title when greater than zero.
    val type: XMBItemType = XMBItemType.STANDARD,
)

/**
 * The tile art [GameIcon] should draw for [item] under the given global mode.
 * [naturalAspect] = render at the art's own aspect (fit-inside, chrome hugging the fitted
 * bounds); false = PSP-authentic 144:80 edge-to-edge fill. [mode] is the RESOLVED mode and
 * drives the placeholder when [uri] is null: PHYSICAL_MEDIA → the bundled per-platform
 * cartridge/disc icon; BOX_ART / BOX_3D → a letter tile shaped like the platform's box.
 */
data class ResolvedIcon(val uri: String?, val naturalAspect: Boolean, val mode: IconDisplayMode)

// Top-level and pure so mode/fallback behaviour is unit-testable. Each mode shows ONLY its
// own asset, and each owns its missing-art placeholder so switching modes always visibly
// changes the tile: ICON0 → the 144:80 letter tile, BOX_ART / BOX_3D → a letter tile in the
// platform's box shape, PHYSICAL_MEDIA → the bundled per-platform cartridge/disc icon.
//
// Resolution order is game override > console override ([platformModes], keyed by platform id)
// > the global mode, so picking a mode on one Memory Card never moves any other console.
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

// A unit of background work surfaced to the notification bar. [progress] null = indeterminate.
data class BackgroundTaskInfo(
    val id: String,
    val label: String,
    val progress: Float?,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * The single source of truth for the XMB home screen.
 *
 * Exposes one [XMBUiState] via [uiState] that the stateless `XMBShell` renders. It owns:
 *  - the category bar (built-in + custom categories) and the item list under the selected category;
 *  - navigation into synthetic Games-root folders (All Games, Favorites, collections) and Memory
 *    Card consoles, tracked by [XMBUiState.selectedPlatformId] / `selectedCollectionId`;
 *  - the gamepad input dispatcher, which routes D-pad/A/B/Y to whichever overlay or layer has focus
 *    (guarded by [XMBUiState.hasBlockingOverlay]);
 *  - game/app launching, context menus, and the various modal overlays (pickers, dialogs).
 *
 * Library state (memory cards, game counts, collections, favorites) is observed reactively, so the
 * XMB re-renders as the underlying data changes.
 */
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
    private val gameLaunchPreferences: com.psplauncher.core.data.repository.GameLaunchPreferences,
    private val windowsLibrarySetup: com.psplauncher.core.data.repository.WindowsLibrarySetup,
    private val pcShortcutImporter: com.psplauncher.feature.launcher.PcShortcutImporter,
    private val pcGameScanner: com.psplauncher.feature.settings.pc.PcGameScanner,
    private val pcGameExporter: com.psplauncher.feature.settings.pc.PcGameExporter,
    private val launchDispatcher: com.psplauncher.feature.launcher.LaunchDispatcher,
    private val setupStateProvider: com.psplauncher.feature.launcher.SetupStateProvider,
    private val customIconStore: CustomIconStore,
    private val pfpThemeStore: PfpThemeStore,
    private val uiMediaStore: com.psplauncher.core.data.repository.UiMediaStore,
    private val gameBootGate: com.psplauncher.feature.launcher.GameBootGate,
    // The preview plays its own audio: the gate owns playback for a real launch, and a preview
    // must never touch the gate. Same singleton player, so the two can never sound different.
    private val uiMediaAudioPlayer: com.psplauncher.core.ui.media.UiMediaAudioPlayer,
) : ViewModel() {


    // The track list currently on screen (in display/sort order), used as the in-app player's queue
    // when a song is picked. [currentMusicTracksRaw] is the same set in DB order, kept so a sort
    // cycle can re-order instantly without waiting on a fresh DB emission.
    private var currentMusicTracks: List<MusicTrack> = emptyList()
    private var currentMusicTracksRaw: List<MusicTrack> = emptyList()

    // Tracks whether playback currently has a track, so the Music root only rebuilds (to add/drop
    // the "Now Playing" row) when that flips — not on every half-second playback tick.
    private var lastHadPlayingTrack = false

    // Cached so a track launch (a discrete event) doesn't need to suspend-read DataStore.
    @Volatile
    private var defaultMusicPlayer: String? = null

    // Elapsed-realtime ms of the most recent user input (touch or controller). Drives the idle
    // context-menu hint: after IDLE_HINT_DELAY_MS with no input, if the focused item has a context
    // menu and touch controls are active, the hint pill fades in. Refreshed by markTouchInput,
    // markControllerInput, and onUserInteraction.
    @Volatile
    private var lastInteractionMs: Long = 0L

    private val _uiState = MutableStateFlow(XMBUiState())
    val uiState: StateFlow<XMBUiState> = _uiState.asStateFlow()

    private var currentItemsJob: Job? = null
    // Backs the fullscreen music browser: a collector job over the active view's data, plus the raw
    // (unfiltered, DB-order) lists kept so query/sort changes re-derive rows without a DB round-trip.
    private var musicBrowserJob: Job? = null
    private var browserRawTracks: List<MusicTrack> = emptyList()
    private var browserRawPlaylists: List<com.psplauncher.core.domain.model.Playlist> = emptyList()
    private var platformCache: Map<String, PlatformEntity> = emptyMap()
    // emulator package → friendly name (e.g. "org.ppsspp.ppsspp" → "PPSSPP"), for the game subtitle's
    // "Platform (Emulator)" label. Populated from the emulator profiles.
    private var emulatorNameByPackage: Map<String, String> = emptyMap()
    private var enabledCards: List<MemoryCard> = emptyList()
    private var baseThemeColors: PFPColors = DefaultPFPColors

    // Background work is surfaced to the Android notification bar, not an in-app tray.
    private val taskNotifier = BackgroundTaskNotifier(context)

    init {
        gamepadInputHandler.scope = viewModelScope
        observeContextMenuHintIdle()
        observeIconDisplayMode()
        observeFocusedGameVideo()
        observeFocusedGameAccent()
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
        observeMusic()
        observeVideo()
        observePhoto()
        observeBooks()
        observeHiddenPlacements()
        observeEmulatorProfiles()
        collectGamepadActions()
        consumeWindowsSetupPrompt()
        observeLaunchRecoveryRequests()
        observeSetupState()
        // Live pin reconcile: an emulator UPDATING an already-pinned shortcut never fires the
        // confirm activity, so the OS callback is the only signal — import it the moment it lands.
        pcShortcutImporter.watchPinChanges(viewModelScope)
    }

    // ── Launch recovery sheet (B1) ─────────────────────────────────────────────
    //
    // The shared LaunchDispatcher raises a LaunchRecoveryRequest whenever a game-path launch
    // fails outright or never reaches the emulator's foreground. This shell is its host: the
    // request lands in XMBUiState so XMBShell can draw the sheet over whatever is on screen,
    // and the gamepad router gives it SELECT/BACK handling like every other overlay.
    private fun observeLaunchRecoveryRequests() {
        viewModelScope.launch {
            launchDispatcher.recoveryRequests.collect { request ->
                _uiState.update { it.copy(launchRecovery = request) }
            }
        }
    }

    /** A button on the recovery sheet. */
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
            LaunchRecoveryAction.COPY_DIAGNOSTIC -> {
                val request = _uiState.value.launchRecovery ?: return
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText(
                    "PFP launch diagnostic", request.diagnostic,
                ))
                taskNotifier.complete(
                    "launch_diag_${request.gameId}", request.gameTitle, "Diagnostic copied to clipboard",
                )
            }
        }
    }

    // One-shot: a PC shortcut arrived while the Windows Library was unconfigured, so the pin flow
    // flagged a follow-up prompt. Consuming clears the flag — the dialog fires once, never nags.
    private fun consumeWindowsSetupPrompt() {
        viewModelScope.launch {
            if (runCatching { windowsLibrarySetup.consumeSetupPrompt() }.getOrDefault(false)) {
                _uiState.update { it.copy(showWindowsSetupPrompt = true) }
            }
        }
    }

    /** "Set Up" on the Windows setup prompt: straight into Library Manager. */
    fun confirmWindowsSetupPrompt() = _uiState.update {
        it.copy(showWindowsSetupPrompt = false, activeSettingsScreen = "settings_library")
    }

    fun dismissWindowsSetupPrompt() = _uiState.update { it.copy(showWindowsSetupPrompt = false) }

    // Keeps the emulator package → name map current so game subtitles can show "Platform (Emulator)".
    // Reloads the on-screen items once names arrive so already-listed games pick up their emulator.
    private fun observeEmulatorProfiles() {
        viewModelScope.launch {
            emulatorProfileRepository.profiles.collect { profiles ->
                val map = profiles.associate { it.packageName to it.name }
                if (map != emulatorNameByPackage) {
                    emulatorNameByPackage = map
                    if (_uiState.value.currentItems.any { it.gameId != null }) {
                        loadItemsForCategory(currentCategory())
                    }
                }
            }
        }
    }

    // "Platform (Emulator)" for a game's subtitle: the platform's display name, plus the emulator's
    // friendly name in parens when one is resolvable (the game's override, else the platform default).
    private fun platformEmulatorLabel(g: Game): String {
        val platform = platformCache[g.platformId]?.name ?: g.platformId
        val emulatorPkg = g.emulatorPackage ?: platformCache[g.platformId]?.preferredEmulatorPackage
        val emulator = emulatorPkg?.let { emulatorNameByPackage[it] }
        return if (emulator != null) "$platform ($emulator)" else platform
    }

    // Music folders drive the Music category's root list; the default player is cached for launch.
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
                // Rebuild the Music root only when playback gains/loses a track, so the "Now Playing"
                // row appears/disappears — never on the half-second position ticks.
                val hasTrack = playback.track != null
                if (hasTrack != lastHadPlayingTrack) {
                    lastHadPlayingTrack = hasTrack
                    if (currentCategory()?.id == BuiltInCategory.MUSIC &&
                        _uiState.value.musicNav == MusicNav.Root
                    ) {
                        // Preserve the cursor by row id: the new "Now Playing" row shifts every
                        // index, and this often happens behind the fullscreen browser — the XMB
                        // must already be re-anchored when it's revealed (no visible snap).
                        refreshMusicRootPreservingCursor()
                    }
                }
            }
        }
    }

    // ── Color scheme ────────────────────────────────────────────────────────────

    // The active XMB color scheme (PSP-style presets + the month-based "Original" theme)
    // is the source of truth for the palette. ORIGINAL re-resolves to the current month each
    // time the scheme is (re)observed — i.e. on app start and whenever the user changes it.
    // A custom-theme accent override (imported/created themes — the one-color cascade) takes
    // precedence over the preset; the unified icon tint rides along either way.
    private data class SchemePrefs(
        val schemeName: String,
        val accentOverride: Long?,
        val iconColor: Long?,
        val iconsStamp: Long?,
        val layoutJson: String?,
        // Display ▸ Scale & Layout: whole-UI scale factor and the user's crossbar-position
        // override (null = keep the theme's / default bar position).
        val xmbScale: Float?,
        val barTopOverride: Float?,
        // Per-form-factor "Adjust XMB Layout" tunings (JSON map, one prefs string).
        val layoutAdjustJson: String?,
        // User-tier icon stamp (custom-icons dir) — separate from the theme's icons stamp so
        // a theme apply/revert never reloads (or drops) the user's picks.
        val customIconsStamp: Long?,
        // Display ▸ Font Colour. null = the theme's own text colour (white on every preset).
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
                        // One accent drives everything: wave color + re-derived gradient.
                        DefaultPFPColors.withWaveTint(
                            androidx.compose.ui.graphics.Color(accentOverride and 0xFFFFFFFFL),
                        )
                    } else {
                        val scheme = runCatching { XmbColorScheme.valueOf(name) }
                            .getOrDefault(XmbColorScheme.CLASSIC_BLUE)
                        val month = java.time.LocalDate.now().monthValue
                        scheme.resolve(month).toPFPColors()
                    }
                    // The user's font colour joins here, beside iconColor and by the same rule:
                    // absent = inherit the theme's own value, which is white on every preset, so
                    // this is a no-op until Display ▸ Font Colour is set. Secondary keeps the 0.7
                    // alpha relationship toPFPColors already establishes, so a picked colour
                    // carries its own sublabels rather than stranding them on white.
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
                    // Custom icon slots of the applied theme (stamp present = extracted dir
                    // has icons; the stamp value only bumps to trigger reloads), plus the
                    // user's per-slot picks (custom-icons dir, its own stamp). Two tiers by
                    // design: user picks survive theme switches, theme icons don't.
                    val iconOverrides = if (iconsStamp != null) loadThemeIconOverrides() else emptyMap()
                    val customIcons =
                        if (customIconsStamp != null) customIconStore.load() else emptyMap()
                    // Per-theme XMB geometry — lenient + sanitized, so a mangled pref can
                    // never wedge the crossbar offscreen. The user's Display ▸ bar-position
                    // override wins over the theme's value; the codec clamp still applies.
                    val themeSpec = com.psplauncher.themekit.XmbLayoutSpecCodec.decode(layoutJson)
                        ?: com.psplauncher.themekit.XmbLayoutSpec.DEFAULT
                    val layoutSpec = if (barTopOverride != null) {
                        com.psplauncher.themekit.XmbLayoutSpecCodec.sanitize(
                            themeSpec.copy(barTopFraction = barTopOverride)
                        )
                    } else themeSpec
                    // One theme color across the whole XMB (PSP-authentic) — no per-category tint.
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

    /**
     * Loads the THEME tier (filesDir/theme-icons/) as slot key → CustomIcon, mirroring
     * CustomIconStore.load's extension handling: png loads as Still, gif as Animated (or
     * Still when it carries a single frame). Ignoring stray files, never crashing.
     */
    private suspend fun loadThemeIconOverrides(): Map<String, CustomIcon> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val iconsDir = java.io.File(context.filesDir, PfpThemeStore.THEME_ICONS_DIR)
            iconsDir.listFiles { f -> f.isFile }.orEmpty().mapNotNull { file ->
                val key = file.nameWithoutExtension
                if (!CustomizableIcons.isValidKey(key)) return@mapNotNull null
                val ext = file.extension.lowercase()
                // Bounds-checked decode: the extraction dir is ours, but the bundle author
                // isn't — a 20k×20k "icon" must never reach a pixel allocation.
                val bitmap = com.psplauncher.core.data.repository.SafeMedia
                    .decodeFileCapped(file.absolutePath, maxDimension = 2048, targetDimension = 2048)
                    ?: return@mapNotNull null
                val firstFrame = bitmap.asImageBitmap()
                if (ext == "gif") {
                    // Frame-count probe: 1 frame = Still, no decoder started (same rule as
                    // the user tier's store).
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

    // ── Category bar (DB-driven) ────────────────────────────────────────────────

    // The main XMB is intentionally the seven PSP-style categories from the launcher spec.
    // Platform folders stay inside Game as Memory Card rows.
    private fun observeCategoryBar() {
        viewModelScope.launch {
            categoryRepository.observeVisible().collect { categories ->
                val allCategories = canonicalXmbCategories(categories.ifEmpty { FALLBACK_CATEGORIES })
                val prevId   = _uiState.value.categories.getOrNull(_uiState.value.selectedCategoryIndex)?.id
                val isInitialSelection = _uiState.value.categories.isEmpty()
                // Keep the same category selected across reorders/hides when possible.
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

    // ── Library (memory cards + game counts) ────────────────────────────────────

    // Missing games need their own collector: every other library flow here (observeAll,
    // observeGamesOnly, the per-platform counts) filters is_missing = 0, so a game becoming missing
    // is invisible to observeCategories and the bucket would never appear or update.
    private fun observeMissingGames() {
        viewModelScope.launch {
            gameRepository.observeMissing().collect { missing ->
                val was = _uiState.value.missingCount
                _uiState.update { it.copy(missingCount = missing.size) }
                // Crossing the zero boundary adds or removes the Missing row itself, so the Games
                // root has to rebuild. Count-only changes just re-render the row's subtitle, which
                // memoryCardItems() reads from state on the next natural render.
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
                    // Card subtitles count what the card actually shows: real games only. Standard
                    // (unmarked) apps stay rows in the table but are invisible to Memory Cards.
                    val displayGames = games.projectGamesForDisplay()
                    val counts = displayGames.filter { it.contentType == GameContentType.GAME }
                        .groupBy { it.platformId }.mapValues { it.value.size }
                    val gamesOnlyTotal = displayGames.count { it.contentType == GameContentType.GAME }
                    // The display snapshot contains only primaries, so a favorite on a secondary
                    // disc would otherwise be lost. observeFavorites already applies the set-level
                    // projection and is the authoritative count for this folder.
                    val favoritesTotal = favorites.size

                    // Drop a stale platform folder if its card was removed or disabled. The
                    // synthetic All Games, Favorites, and Missing folders are always valid.
                    val validPlatformId = _uiState.value.selectedPlatformId
                        ?.takeIf { id ->
                            id == ALL_GAMES_PLATFORM_ID ||
                                id == FAVORITES_PLATFORM_ID ||
                                id == MISSING_PLATFORM_ID ||
                                cards.any { c -> c.platformId == id }
                        }
                    // Drop a stale collection folder if the collection was deleted.
                    val validCollectionId = _uiState.value.selectedCollectionId
                        ?.takeIf { id -> collections.any { c -> c.id == id } }

                    _uiState.update { it.copy(
                        platformGameCounts = counts,
                        allGamesCount = gamesOnlyTotal,
                        favoritesCount = favoritesTotal,
                        selectedPlatformId = validPlatformId,
                        selectedCollectionId = validCollectionId,
                        collections = collections,
                    )}

                    // Refresh any collection-rendering category live as cards/counts/collections
                    // change — collections place themselves by categoryId, so gaming categories
                    // AND non-gaming app categories (Network / App Store / custom) must re-render
                    // when the collection list changes.
                    if (categoryShowsCollections(currentCategory())) {
                        // Same list, reloaded — and any games-table write lands here, a rename
                        // included. Restarting the list job would otherwise publish it as a fresh
                        // list and leave the cursor on the renamed game's old slot.
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

    // ── App category changes (assignments / overrides) ──────────────────────────

    private fun observeAppChanges() {
        viewModelScope.launch {
            appCategoryRepository.changes().collect {
                val category = currentCategory() ?: return@collect
                if (isAppCategory(category.id)) loadItemsForCategory(category)
            }
        }
    }

    private fun currentCategory(): Category? =
        _uiState.value.categories.getOrNull(_uiState.value.selectedCategoryIndex)

    // App-populated categories are everything except Settings and Games.
    private fun isAppCategory(categoryId: String): Boolean =
        categoryId != BuiltInCategory.SETTINGS && categoryId != BuiltInCategory.GAMES

    // Categories with their own dedicated loadItemsForCategory branch — these never render
    // collection rows (media/system sections own their layouts).
    private val nonCollectionCategoryIds = setOf(
        BuiltInCategory.FAVORITES, BuiltInCategory.RECENTLY_PLAYED, BuiltInCategory.MUSIC,
        BuiltInCategory.VIDEO, BuiltInCategory.PHOTO, BuiltInCategory.ANDROID,
        BuiltInCategory.APP_DRAWER, BuiltInCategory.SETTINGS,
    )

    /** True for categories that render collection rows: gaming categories, and the generic app
     *  categories (Network / App Store / custom non-gaming) served by the `else` item branch. */
    private fun categoryShowsCollections(category: Category?): Boolean {
        if (category == null) return false
        return category.isGamingCategory || category.id !in nonCollectionCategoryIds
    }

    /** The category a collection created from the current context should live in: the current
     *  category when it renders collections, otherwise the Main Game default. */
    private fun collectionHomeCategoryId(): String {
        val cat = currentCategory() ?: return BuiltInCategory.GAMES
        return if (categoryShowsCollections(cat)) cat.id else BuiltInCategory.GAMES
    }

    /**
     * @param keepCursorOnRow when true, even the FIRST game list this load publishes keeps the
     *   cursor on the row it was on, by id. Later emissions of a live game list always do; this is
     *   for a caller that reloads the list already on screen, like Edit Title.
     */
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
                BuiltInCategory.ANDROID -> {
                    _uiState.update { it.copy(currentItems = ANDROID_ITEMS) }
                }
                BuiltInCategory.SETTINGS -> {
                    // One flat list. Still clamped rather than reset: the cursor is restored from
                    // this category's own memory and must not survive as an out-of-range index.
                    _uiState.update { state ->
                        state.copy(
                            currentItems = SETTINGS_ROOT_ITEMS,
                            selectedItemIndex = state.selectedItemIndex
                                .coerceIn(0, (SETTINGS_ROOT_ITEMS.size - 1).coerceAtLeast(0)),
                        )
                    }
                }
                BuiltInCategory.GAMES -> {
                    val platformId = _uiState.value.selectedPlatformId
                    val collectionId = _uiState.value.selectedCollectionId
                    if (collectionId != null) {
                        // A user collection — games from any platform, app entries allowed only
                        // because they were explicitly added by the user.
                        var keepCursor = keepCursorOnRow
                        collectionRepository.observeGames(collectionId).collect { games ->
                            val visible = games.notHiddenAt(HideLocationType.COLLECTION, collectionId.toString())
                            val items = if (visible.isEmpty()) listOf(emptyCollectionItem())
                                        else visible.gameSorted(_uiState.value.gameSortMode).toXmbItems()
                            publishGameItems(items, keepCursor)
                            keepCursor = true
                        }
                    } else if (platformId == ALL_GAMES_PLATFORM_ID) {
                        // All Games aggregates real games only (content_type = GAME), minus any
                        // the user hid from this card (recoverable in Settings > Hidden Items).
                        // Multi-disc sets project one row (the primary) — see observeAllGames.
                        var keepCursor = keepCursorOnRow
                        gameRepository.observeAllGames().collect { games ->
                            val visible = games.notHiddenAt(HideLocationType.ALL_GAMES)
                            val items = if (visible.isEmpty()) listOf(emptyAllGamesItem())
                                        else visible.gameSorted(_uiState.value.gameSortMode).toXmbItems()
                            publishGameItems(items, keepCursor)
                            keepCursor = true
                        }
                    } else if (platformId == FAVORITES_PLATFORM_ID) {
                        // Favorites folder — every favorited entry (games and app shortcuts).
                        var keepCursor = keepCursorOnRow
                        gameRepository.observeFavorites().collect { games ->
                            val visible = games.notHiddenAt(HideLocationType.FAVORITES)
                            val items = if (visible.isEmpty()) listOf(emptyFavoritesItem())
                                        else visible.gameSorted(_uiState.value.gameSortMode).toXmbItems()
                            publishGameItems(items, keepCursor)
                            keepCursor = true
                        }
                    } else if (platformId == MISSING_PLATFORM_ID) {
                        // The Missing bucket. Deliberately NOT filtered by notHiddenAt: a game the
                        // user hid from a normal view still needs to be reachable here, since this
                        // is the only place "Remove permanently" is offered.
                        var keepCursor = keepCursorOnRow
                        gameRepository.observeMissing().collect { games ->
                            val items = if (games.isEmpty()) listOf(emptyMissingItem())
                                        else games.gameSorted(_uiState.value.gameSortMode)
                                            .toXmbItems()
                                            // Each row states why it is here, per the plan. The
                                            // subtitle would otherwise carry play stats that are
                                            // meaningless for a file that isn't there.
                                            .map { it.copy(subtitle = MISSING_REASON) }
                            publishGameItems(items, keepCursor)
                            keepCursor = true
                        }
                    } else if (platformId != null) {
                        // Multi-disc sets project one row (the primary) — see observePlatformGames.
                        var keepCursor = keepCursorOnRow
                        gameRepository.observePlatformGames(platformId).collect { all ->
                            // Memory Cards show real games only — a standard (unmarked) app row on
                            // this platform stays in the table for art/collections but not here.
                            val games = all.filter { it.contentType == GameContentType.GAME }
                            // Per-card hiding: Android keeps its legacy location type; every other
                            // card hides via PLATFORM keyed by its platform id.
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
                        // The Games root re-renders live: a pin/scan can create a card or change
                        // counts while this screen is up (previously a one-shot snapshot that went
                        // stale until the next navigation). enabledCards/counts are refreshed by
                        // observeCategories' collector over these same sources before this fires.
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
                    MusicNav.MusicApps -> {
                        clearMusicTrackCache()
                        val items = musicAppItems()
                        _uiState.update { it.copy(currentItems = items) }
                    }
                }
                BuiltInCategory.VIDEO -> when (val nav = _uiState.value.videoNav) {
                    VideoNav.Root -> _uiState.update { it.copy(currentItems = videoRootItems()) }
                    VideoNav.Collections -> _uiState.update { it.copy(currentItems = videoCollectionsItems()) }
                    VideoNav.AllVideos -> videoRepository.observeAllVideos().collect { videos ->
                        setVideoItems(videos, emptyAllVideosItem())
                    }
                    VideoNav.RecentlyWatched -> videoRepository.observeRecentlyWatched().collect { videos ->
                        // Recency order is intrinsic — don't apply the user sort here.
                        setVideoItems(videos, emptyRecentItem(), sortable = false)
                    }
                    VideoNav.Favorites -> videoRepository.observeFavorites().collect { videos ->
                        setVideoItems(videos, emptyFavoriteVideosItem())
                    }
                    VideoNav.Playlists -> videoRepository.observePlaylists().collect { playlists ->
                        _uiState.update { it.copy(currentItems = videoPlaylistItems(playlists), videoPlaylists = playlists) }
                    }
                    is VideoNav.Playlist -> videoRepository.observePlaylistVideos(nav.id).collect { videos ->
                        // Manual playlist order — keep it, don't re-sort.
                        setVideoItems(videos, emptyPlaylistVideosItem(), sortable = false)
                    }
                    VideoNav.Libraries -> videoRepository.observeLibraries().collect { libs ->
                        _uiState.update { it.copy(currentItems = videoLibraryItems(libs)) }
                    }
                    is VideoNav.Library -> videoRepository.observeVideosByLibrary(nav.id).collect { videos ->
                        setVideoItems(videos, emptyAllVideosItem())
                    }
                    VideoNav.VideoApps -> {
                        val items = videoAppItems()
                        _uiState.update { it.copy(currentItems = items) }
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
                    PhotoNav.PhotoApps -> {
                        val items = photoAppItems()
                        _uiState.update { it.copy(currentItems = items) }
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
                    // A series folder is always in reading order, whatever the flat list's sort
                    // mode is. Ordering a series by title is the one thing the folder exists to
                    // stop, so it does not take the sort mode and does not offer one.
                    is BooksNav.Series -> bookRepository.observeAllBooks().collect { books ->
                        val inSeries = books.filter { it.seriesName == nav.name }.inSeriesOrder()
                        _uiState.update { it.copy(currentItems = bookItems(inSeries).ifEmpty { listOf(emptyBooksItem()) }) }
                    }
                }
                else -> {
                    // Gaming categories show games and collections
                    // Drilled into one of this category's collections — show its members. Works
                    // for gaming and non-gaming categories alike: members are games-table rows,
                    // which in non-gaming (app) collections are ANDROID_APP shortcut rows that
                    // launch by package like anywhere else.
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
                        // Games are assigned via the junction table (echo/copy model). Reuse the
                        // canonical Game→XMBItem mapping so icons/artwork/launch fields match the
                        // Main Game category exactly; only overlay the "Pinned" marker.
                        val gameRows = gameCategoryRepository.itemsForCategory(category.id)
                            .filterIsInstance<com.psplauncher.core.data.repository.GameCategoryItem.GameItem>()
                            .filterNot { isHiddenAt(HiddenPlacement.gameKey(it.game.id), HideLocationType.CATEGORY, category.id) }
                        val pinnedGameIds = gameRows.filter { it.pinned }.map { it.game.id }.toSet()
                        val gameItems = gameRows.map { it.game }.gameSorted(_uiState.value.gameSortMode).toXmbItems().map { xmb ->
                            if (xmb.gameId in pinnedGameIds) xmb.copy(subtitle = "Pinned") else xmb
                        }
                        // Collections belong to exactly one category, tracked by categoryId —
                        // the single source of truth for placement (not the junction table).
                        // Pinned collections sort to the top.
                        val collectionItems = _uiState.value.collections
                            .filter { it.categoryId == category.id }
                            .sortedByDescending { it.isPinned }
                            .map { collection ->
                                val games = "${collection.gameCount} ${if (collection.gameCount == 1) "Game" else "Games"}"
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
                        // A one-shot read: it re-runs only when something reloads the category.
                        publishGameItems(items + addGamesItem(), keepCursorOnRow)
                    } else {
                        // Non-gaming categories show apps (Photo / Music / Video / Network / App Store / custom).
                        // Apps the user has given artwork (via Edit App Details → a games-table row keyed
                        // by package, typed ANDROID_APP) render that art so the category looks uniform.
                        // The rows stay ANDROID_APP, so this never makes them appear in All Games.
                        val apps = appCategoryRepository.appsForCategory(category.id)
                            .notHiddenAt(HideLocationType.CATEGORY, category.id)
                        val appItems = apps.map { it.toXmbItem(gameRepository.getAppEntry(it.packageName)) }
                        // App collections homed in this category (categoryId is the single source
                        // of truth for placement, same as gaming categories). Pinned sort first.
                        val collectionItems = _uiState.value.collections
                            .filter { it.categoryId == category.id }
                            .sortedByDescending { it.isPinned }
                            .map { collection ->
                                val count = "${collection.gameCount} ${if (collection.gameCount == 1) "App" else "Apps"}"
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
                        // "Add Apps" is offered on every app section so the same picker serves
                        // Video, Music, Network, App Store and custom categories alike.
                        _uiState.update { it.copy(currentItems = items + addAppsItem()) }
                    }
                }
            }
        }
    }

    // [artwork] is the app's optional games-table row (ANDROID_APP, keyed by package). When it
    // carries landscape art, the item shows the game-style tile; gameId stays null so the row keeps
    // app behaviour (app context menu, package launch) and never aggregates into All Games.
    private fun CategorizedApp.toXmbItem(
        artwork: com.psplauncher.core.domain.model.Game? = null,
    ): XMBItem = XMBItem(
        id           = "app_$packageName",
        title        = label,
        subtitle     = if (pinned) "Pinned" else null,
        packageName  = packageName,
        isAndroidApp = true,
        iconUri      = artwork?.let { it.iconUri ?: it.heroUri ?: it.artworkUri },
        // The XMB hover background reads artworkUri — populate it (with a hero fallback) so a
        // non-gaming category app shows its assigned background, like games do.
        artworkUri   = artwork?.let { it.artworkUri ?: it.heroUri },
        heroUri      = artwork?.heroUri,
        accentColor  = artwork?.let { platformCache[it.platformId]?.accentColor },
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

    // ── Music ───────────────────────────────────────────────────────────────────

    /** Rebuilds the Music root list in place, relocating the cursor to the same row id so a shape
     *  change (the "Now Playing" row appearing/disappearing) never moves the visible selection. */
    private fun refreshMusicRootPreservingCursor() {
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

    // Music root: the static items (Now Playing, when something is playing; Playlist; Music Apps)
    // followed by the single "All Music" memory-card item. The root folder is managed in Settings →
    // Music; a getting-started "Add Music Folder" row shows until a root has been added and scanned
    // (keyed off the scan completing, not the track count), then drops away.
    private fun musicRootItems(): List<XMBItem> {
        val folders = _uiState.value.musicFolders
        val totalTracks = folders.sumOf { it.trackCount }
        val hasScannedFolder = folders.any { it.lastScannedAt != null }
        return buildList {
            // Now Playing — only when a track is loaded; clicking returns to the active song.
            _uiState.value.musicPlayback.track?.let { track ->
                add(
                    XMBItem(
                        id       = NOW_PLAYING_ITEM_ID,
                        title    = track.displayTitle,
                        subtitle = listOfNotNull("Now Playing", track.artist).joinToString("  ·  "),
                        coverUri = track.artUri,
                        type     = XMBItemType.MUSIC_TRACK,   // renders the album-cover leading tile
                    )
                )
            }
            add(
                XMBItem(
                    id       = PLAYLISTS_ITEM_ID,
                    title    = "Playlist",
                    subtitle = "Build and play your own track lists",
                    type     = XMBItemType.PLAYLIST,
                )
            )
            add(
                XMBItem(
                    id       = MUSIC_APPS_ITEM_ID,
                    title    = "Music Apps",
                    subtitle = "Open your installed music apps",
                    type     = XMBItemType.MUSIC_APPS,
                )
            )
            // All scanned music collapses into one memory-card item (like All Games). Uses the
            // physical-media "_default.png" memory-card art rather than the blank console fallback.
            add(
                XMBItem(
                    id       = ALL_MUSIC_ITEM_ID,
                    title    = "Music",
                    subtitle = "$totalTracks ${if (totalTracks == 1) "track" else "tracks"}",
                    coverUri = MEMORY_CARD_ASSET_URI,
                    type     = XMBItemType.MEMORY_CARD,
                )
            )
            // Getting-started prompt: opens Settings → Music. Drops away once a root has been
            // scanned (even if it found no tracks), since the root is then managed in Settings.
            if (!hasScannedFolder) add(addMusicFolderItem())
        }
    }

    private fun addMusicFolderItem(): XMBItem = XMBItem(
        id       = ADD_MUSIC_FOLDER_ITEM_ID,
        title    = "Add Music Folder",
        subtitle = "Set your Music root folder in Settings to get started",
        type     = XMBItemType.ADD_ACTION,
    )

    // Playlist list: one row per playlist + a "Create Playlist" row.
    private fun playlistRootItems(playlists: List<com.psplauncher.core.domain.model.Playlist>): List<XMBItem> {
        val rows = playlists.map { pl ->
            XMBItem(
                id         = "pl_${pl.id}",
                title      = pl.name,
                subtitle   = "${pl.trackCount} ${if (pl.trackCount == 1) "track" else "tracks"}",
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

    // Music Apps: the apps the user added (stored under a dedicated pseudo-category so they don't
    // mix with the built-in Music category), plus an "Add Music Apps" row.
    private suspend fun musicAppItems(): List<XMBItem> {
        val apps = appCategoryRepository.appsForCategory(MUSIC_APPS_CATEGORY_ID)
            .notHiddenAt(HideLocationType.CATEGORY, MUSIC_APPS_CATEGORY_ID)
        val appItems = apps.map { it.toXmbItem(gameRepository.getAppEntry(it.packageName)) }
        return appItems + XMBItem(
            id       = ADD_MUSIC_APPS_ITEM_ID,
            title    = "Add Music Apps",
            subtitle = "Pick installed apps to show here",
            type     = XMBItemType.ADD_ACTION,
        )
    }

    private fun addTracksItem(): XMBItem = XMBItem(
        id       = ADD_TRACKS_ITEM_ID,
        title    = "Add Tracks",
        subtitle = "Pick songs to add to this playlist",
        type     = XMBItemType.ADD_ACTION,
    )

    private fun List<com.psplauncher.core.domain.model.MusicTrack>.toMusicItems(): List<XMBItem> =
        map { track ->
            XMBItem(
                id            = "mt_${track.id}",
                title         = track.displayTitle,
                subtitle      = track.artist?.takeIf { it.isNotBlank() },
                type          = XMBItemType.MUSIC_TRACK,
                mediaUri      = track.uri,
                mimeType      = track.mimeType,
                coverUri      = track.artUri,
                musicFolderId = track.folderId,
            )
        }

    // Caches the on-screen track list (raw + sorted) and pushes the sorted items, or an empty-state
    // row when there are none. [trailing] rows (e.g. a playlist's "Add Tracks") always show.
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

    // ── Music navigation (drill into / out of the Music sub-screens) ────────────
    private fun openMusicView(nav: MusicNav) = navigateRememberingCursor { it.copy(musicNav = nav) }

    private fun closeMusicView() = openMusicView(MusicNav.Root)

    // ── Per-location hiding ───────────────────────────────────────────────────────

    // Fast lookup set of "itemKey|LOCATION_TYPE|locationId" for every hidden placement, refreshed
    // reactively. Item lists are filtered against it as they're built.
    @Volatile private var hiddenKeys: Set<String> = emptySet()

    private fun observeHiddenPlacements() {
        viewModelScope.launch {
            hiddenPlacementDao.observeAll().collect { rows ->
                hiddenKeys = rows.map { "${it.itemKey}|${it.locationType}|${it.locationId}" }.toSet()
                // Re-render the current list so a hide/unhide takes effect immediately.
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

    // Persists a hide placement, caching labels so the Hidden Items manager renders without joins.
    private fun persistHide(itemKey: String, itemLabel: String, type: HideLocationType, locationId: String, locationLabel: String) {
        viewModelScope.launch {
            hiddenPlacementDao.upsert(
                HiddenPlacementEntity(itemKey, itemLabel, type.name, locationId, locationLabel, System.currentTimeMillis())
            )
        }
    }

    private fun categoryDisplayName(id: String): String = when (id) {
        MUSIC_APPS_CATEGORY_ID -> "Music Apps"
        VIDEO_APPS_CATEGORY_ID -> "Video Apps"
        else -> _uiState.value.categories.firstOrNull { it.id == id }?.name ?: id
    }

    // The location a GAME row is currently being shown in (for "Hide from here"), or null when the
    // current view doesn't support per-location hiding (the Games root).
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
            // No per-location hide in the Missing bucket. It is the only place "Remove permanently"
            // is offered, so hiding a row here would strand the entry: invisible everywhere (it is
            // already filtered out of normal views by is_missing) and no longer removable.
            s.selectedPlatformId == MISSING_PLATFORM_ID -> null
            s.selectedPlatformId == ANDROID_PLATFORM_ID -> Triple(HideLocationType.ANDROID_PLATFORM, "", "Android")
            // The aggregated All Games card — hides from THIS view only; the game stays on its
            // own Memory Card, in collections, and in Favorites.
            s.selectedPlatformId == ALL_GAMES_PLATFORM_ID ->
                Triple(HideLocationType.ALL_GAMES, "", "All Games")
            // Any other Memory Card (ROM platforms, Windows Games) — hides from THIS card only;
            // the game stays in All Games, collections, and categories.
            s.selectedPlatformId != null -> {
                val name = enabledCards.firstOrNull { it.platformId == s.selectedPlatformId }?.displayName
                    ?: s.selectedPlatformId
                Triple(HideLocationType.PLATFORM, s.selectedPlatformId, name)
            }
            // Reached only when no platform or collection is selected — the branches above
            // have already claimed every one of those cases.
            cat != null && cat.isGamingCategory && cat.id != BuiltInCategory.GAMES ->
                Triple(HideLocationType.CATEGORY, cat.id, cat.name)
            else -> null
        }
    }

    // ── Video ───────────────────────────────────────────────────────────────────

    // Library list drives the Video root; re-render the root when it changes.
    private fun observeVideo() {
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

    // Video root: browse rows first (Collections, Video Libraries), then the Video Apps counterpart
    // directly above the "Videos" memory card (second-to-bottom). The root folder is managed in
    // Settings → Video; a getting-started "Add Videos" row shows until a root has been added and
    // scanned (keyed off the scan completing, not the video count), then drops away.
    private fun videoRootItems(): List<XMBItem> {
        val libraries = _uiState.value.videoLibraries
        val totalVideos = libraries.sumOf { it.videoCount }
        val hasScannedLibrary = libraries.any { it.lastScannedAt != null }
        return buildList {
            // The three curated views collapse into one "Collections" entry (drills into
            // Recently Watched / Favorites / Playlists) to keep the Video root uncluttered.
            add(
                XMBItem(
                    id       = VIDEO_COLLECTIONS_ITEM_ID,
                    title    = "Collections",
                    subtitle = "Recently Watched, Favorites & Playlists",
                    type     = XMBItemType.VIDEO_COLLECTIONS,
                )
            )
            add(
                XMBItem(
                    id       = VIDEO_LIBRARIES_ITEM_ID,
                    title    = "Video Libraries",
                    subtitle = "${libraries.size} ${if (libraries.size == 1) "library" else "libraries"}",
                    type     = XMBItemType.VIDEO_LIBRARY,
                )
            )
            // Video Apps counterpart, sitting directly above the memory card.
            add(
                XMBItem(
                    id       = VIDEO_APPS_ITEM_ID,
                    title    = "Video Apps",
                    subtitle = "Open your installed video apps",
                    type     = XMBItemType.VIDEO_APPS,
                )
            )
            add(
                XMBItem(
                    id       = ALL_VIDEOS_ITEM_ID,
                    title    = "Videos",
                    subtitle = "$totalVideos ${if (totalVideos == 1) "video" else "videos"}",
                    coverUri = MEMORY_CARD_ASSET_URI,
                    type     = XMBItemType.MEMORY_CARD,
                )
            )
            // Getting-started prompt: opens Settings → Video. Drops away once a root has been
            // scanned (even if it found no videos), since the root is then managed in Settings.
            if (!hasScannedLibrary) add(addVideosItem())
        }
    }

    private fun addVideosItem(): XMBItem = XMBItem(
        id       = ADD_VIDEOS_ITEM_ID,
        title    = "Add Videos",
        subtitle = "Set your Video root folder in Settings to get started",
        type     = XMBItemType.ADD_ACTION,
    )

    // The "Collections" drill-in: the three curated views, one level below the Video root.
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

    // One card per video library, drillable into its videos. The root folder is managed in
    // Settings → Video, so there is no add row here.
    private fun videoLibraryItems(libraries: List<com.psplauncher.core.domain.model.VideoLibrary>): List<XMBItem> {
        val rows = libraries.map { lib ->
            XMBItem(
                id       = "vlib_${lib.id}",
                title    = lib.displayName,
                subtitle = "${lib.videoCount} ${if (lib.videoCount == 1) "video" else "videos"}",
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
        val appItems = apps.map { it.toXmbItem(gameRepository.getAppEntry(it.packageName)) }
        return appItems + XMBItem(
            id       = ADD_VIDEO_APPS_ITEM_ID,
            title    = "Add Video Apps",
            subtitle = "Pick installed apps to show here",
            type     = XMBItemType.ADD_ACTION,
        )
    }

    private fun List<com.psplauncher.core.domain.model.Video>.toVideoItems(): List<XMBItem> =
        map { video ->
            XMBItem(
                id       = "vid_${video.id}",
                title    = video.displayTitle,
                subtitle = video.durationMs?.let { formatDuration(it) },
                type     = XMBItemType.VIDEO_FILE,
                mediaUri = video.uri,
                mimeType = video.mimeType,
                coverUri = video.effectiveThumbnailUri,
            )
        }

    private fun setVideoItems(
        videos: List<com.psplauncher.core.domain.model.Video>,
        emptyItem: XMBItem,
        sortable: Boolean = true,
    ) {
        val ordered = if (sortable) videos.videoSorted(_uiState.value.videoSortMode) else videos
        val items = if (ordered.isEmpty()) listOf(emptyItem) else ordered.toVideoItems()
        _uiState.update { it.copy(currentItems = items) }
    }

    // Playlist list: one row per playlist + a "Create Playlist" row.
    private fun videoPlaylistItems(playlists: List<com.psplauncher.core.domain.model.VideoPlaylist>): List<XMBItem> {
        val rows = playlists.map { pl ->
            XMBItem(
                id         = "vpl_${pl.id}",
                title      = pl.name,
                subtitle   = "${pl.videoCount} ${if (pl.videoCount == 1) "video" else "videos"}",
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

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return ""
        val totalSec = ms / 1000
        val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    // Handles A/Cross on any Video row. Returns true when [item] is a Video row it owns.
    private fun handleVideoSelection(item: XMBItem): Boolean = when {
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
        item.id == VIDEO_APPS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openVideoView(VideoNav.VideoApps); true }
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
        // Video-app rows launch the app.
        _uiState.value.videoNav == VideoNav.VideoApps && item.packageName != null -> {
            menuSound.play(MenuSound.LAUNCH); appCategoryRepository.launch(item.packageName); true
        }
        else -> false
    }

    // ── View cursor memory ──────────────────────────────────────────────────────
    // One remembered cursor position per drillable view, across EVERY category: Games memory-card
    // folders / All Games / Favorites / collections (built-in and custom categories alike, keyed by
    // category id so future custom categories get their own slots for free), and the Music / Video /
    // Photo sub-views. Drilling in/out (or re-entering a view) lands where the user left off instead
    // of snapping to the first item.
    private val viewCursor = mutableMapOf<String, Int>()

    // Remembered cursor position per category (keyed by category id), so switching categories and
    // returning restores the item you were on instead of the first.
    private val categoryCursor = mutableMapOf<String, Int>()

    // Stable key for whatever list [s] currently shows.
    private fun viewCursorKey(s: XMBUiState): String {
        val catId = s.categories.getOrNull(s.selectedCategoryIndex)?.id ?: "none"
        val sub = when {
            catId == BuiltInCategory.MUSIC -> "music_${musicNavKey(s.musicNav)}"
            catId == BuiltInCategory.VIDEO -> "video_${videoNavKey(s.videoNav)}"
            catId == BuiltInCategory.PHOTO -> "photo_${photoNavKey(s.photoNav)}"
            catId == BuiltInCategory.LIBRARY -> "books_${booksNavKey(s.booksNav)}"
            s.selectedCollectionId != null -> "col_${s.selectedCollectionId}"
            s.selectedPlatformId != null   -> "plat_${s.selectedPlatformId}"
            else                           -> "root"
        }
        return "$catId/$sub"
    }

    // Performs a drill navigation with cursor memory: saves the current view's cursor, applies
    // [mutate] (which must not touch selectedItemIndex), restores the destination view's remembered
    // cursor (0 the first time), then reloads the item list.
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
        MusicNav.MusicApps   -> "apps"
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
        VideoNav.VideoApps       -> "apps"
    }

    private fun openVideoView(nav: VideoNav) = navigateRememberingCursor { it.copy(videoNav = nav) }

    private fun closeVideoView() = openVideoView(VideoNav.Root)

    fun onCloseVideoDetail() {
        _uiState.update { it.copy(activeVideoId = null, pendingVideoDetailAction = null) }
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

    // Long-press options for a video playlist row: open / rename / delete.
    private fun openVideoPlaylistContextMenu(playlistId: Long, name: String) {
        val items = listOf(
            XMBContextMenuItem("open_video_playlist", "Open"),
            XMBContextMenuItem("rename_video_playlist", "Rename Playlist"),
            XMBContextMenuItem("delete_video_playlist", "Delete Playlist", isDestructive = true),
        )
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(name, items, videoPlaylistId = playlistId)) }
    }

    // Opens the △ options menu for a Video row. Returns true when [item] is a video row it owns
    // (a video file, a library card, a playlist row, or a video-app row), so the generic
    // Y/long-press handler can stop.
    private fun openVideoContextMenu(item: XMBItem): Boolean {
        if (currentCategory()?.id != BuiltInCategory.VIDEO) return false
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
            _uiState.value.videoNav == VideoNav.VideoApps && item.packageName != null -> {
                openAppContextMenu(item, categoryIdOverride = VIDEO_APPS_CATEGORY_ID); true
            }
            else -> false
        }
    }

    // Options for a single video file. Favorite label + "Remove from this Playlist" reflect the
    // current state/context. Fetches the video first so the favorite label is correct.
    private fun openVideoFileContextMenu(videoId: String, title: String) {
        viewModelScope.launch {
            val video = videoRepository.getVideo(videoId) ?: return@launch
            val inPlaylist = _uiState.value.videoNav is VideoNav.Playlist
            val items = buildList {
                add(XMBContextMenuItem("video_play", "Play"))
                if (video.resumePositionMs > 0) add(XMBContextMenuItem("video_resume", "Resume"))
                add(XMBContextMenuItem("video_favorite", if (video.isFavorite) "Remove from Favorites" else "Add to Favorites"))
                add(XMBContextMenuItem("video_add_playlist", "Add to Playlist"))
                if (inPlaylist) add(XMBContextMenuItem("video_remove_playlist", "Remove from this Playlist", isDestructive = true))
                add(XMBContextMenuItem("video_details", "Details"))
                add(XMBContextMenuItem("video_remove", "Remove From Library", isDestructive = true))
            }
            _uiState.update { it.copy(activeContextMenu = XMBContextMenu(title, items, videoFileId = videoId)) }
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
            "video_remove" -> appAction { videoRepository.removeVideo(videoId) }
        }
    }

    // Second-level menu: the playlists a video can be added to (checkmarks show membership), plus
    // "Create New Playlist". Stays open while toggling so several can be picked at once.
    private fun openVideoPlaylistPicker(videoId: String, selectIndex: Int = 0) {
        viewModelScope.launch {
            val playlists = videoRepository.observePlaylists().first()
            val memberOf = videoRepository.getPlaylistIdsForVideo(videoId).toSet()
            val items = buildList {
                playlists.forEach { pl -> add(XMBContextMenuItem("vpl_${pl.id}", pl.name, checked = pl.id in memberOf)) }
                add(XMBContextMenuItem("vpl_new", "Create New Playlist"))
            }
            _uiState.update { it.copy(
                activeContextMenu = XMBContextMenu(
                    title = "Add to Playlist",
                    items = items,
                    selectedIndex = selectIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
                    videoPlaylistPickerVideoId = videoId,
                )
            )}
        }
    }

    // Options for a video library card: open, scan, or manage in Settings.
    private fun openVideoLibraryContextMenu(libraryId: String, name: String) {
        val items = listOf(
            XMBContextMenuItem("video_lib_open", "Open"),
            XMBContextMenuItem("video_lib_manage", "Manage in Settings"),
        )
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(name, items, videoLibraryId = libraryId)) }
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

    // ── Library (books) ─────────────────────────────────────────────────────────

    // The shelf list and the chosen reader both drive the Library root, so a change to either
    // re-renders it while the user is standing there.
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

    private fun refreshBooksRootIfShowing() {
        if (currentCategory()?.id == BuiltInCategory.LIBRARY &&
            _uiState.value.booksNav == BooksNav.Root
        ) {
            _uiState.update { it.copy(currentItems = booksRootItems()) }
        }
    }

    private fun booksRootItems(): List<XMBItem> {
        val shelves = _uiState.value.bookLibraries
        val totalBooks = shelves.sumOf { it.bookCount }
        val hasScannedShelf = shelves.any { it.lastScannedAt != null }
        val reader = _uiState.value.defaultReader
        return buildList {
            // The reader, first, so the app you read in is one press away whether or not you are
            // opening something from the library. Hidden when no reader is set, since there is
            // nothing to open: the picker lives in Settings.
            if (reader != null) {
                add(
                    XMBItem(
                        id       = OPEN_READER_ITEM_ID,
                        title    = _uiState.value.defaultReaderLabel ?: "Open Reader",
                        subtitle = "Open your reader",
                        type     = XMBItemType.LIBRARY_READER,
                    )
                )
            }
            add(
                XMBItem(
                    id       = BOOK_SHELVES_ITEM_ID,
                    title    = "Shelves",
                    subtitle = "${shelves.size} ${if (shelves.size == 1) "shelf" else "shelves"}",
                    type     = XMBItemType.LIBRARY_SHELVES,
                )
            )
            // Only worth a row once something declares a series. A library of standalones would
            // otherwise carry a row that opens an empty list.
            val series = _uiState.value.bookSeries
            if (series.isNotEmpty()) {
                add(
                    XMBItem(
                        id       = BOOK_SERIES_ITEM_ID,
                        title    = "Series",
                        subtitle = "${series.size} ${if (series.size == 1) "series" else "series"}",
                        type     = XMBItemType.LIBRARY_SERIES,
                    )
                )
            }
            add(
                XMBItem(
                    id       = ALL_BOOKS_ITEM_ID,
                    title    = "Books",
                    subtitle = "$totalBooks ${if (totalBooks == 1) "book" else "books"}",
                    coverUri = MEMORY_CARD_ASSET_URI,
                    type     = XMBItemType.MEMORY_CARD,
                )
            )
            // Getting-started prompt, gone once a shelf has been scanned even if it found nothing.
            if (!hasScannedShelf) {
                add(
                    XMBItem(
                        id       = ADD_BOOK_FOLDER_ITEM_ID,
                        title    = "Add Book Folder",
                        subtitle = "Point the Library at a folder of EPUBs",
                        type     = XMBItemType.ADD_ACTION,
                    )
                )
            }
        }
    }

    private fun bookItems(books: List<com.psplauncher.core.domain.model.Book>): List<XMBItem> =
        books.map { book ->
            XMBItem(
                id       = "book_${book.id}",
                title    = book.displayTitle,
                subtitle = bookSubtitle(book),
                coverUri = book.coverUri,
                // Same image in both slots on purpose: coverUri draws the list tile, artworkUri is
                // the shell's hover-background slot. XMBShell already crossfades artworkUri behind
                // whatever row is selected, for any item type, so a book gets the treatment games
                // get without a second rendering path.
                artworkUri = book.coverUri,
                type     = XMBItemType.LIBRARY_BOOK,
            )
        }

    /**
     * What sits under a book's title: where it falls in its series, and who wrote it.
     *
     * The series is shown whatever the sort mode, not only when sorting by series. A list sorted
     * by title is exactly where "book 3 of something" is the fact the user is missing.
     */
    private fun bookSubtitle(book: com.psplauncher.core.domain.model.Book): String? {
        val series = book.seriesName?.let { name ->
            // A whole number is written without its decimal: "Dune #2", not "Dune #2.0". A .5
            // keeps it, because that IS the information (a novella between two books).
            val index = book.seriesIndex?.let { i ->
                if (i == Math.floor(i)) "#${i.toInt()}" else "#$i"
            }
            listOfNotNull(name, index).joinToString(" ")
        }
        return listOfNotNull(series, book.author).joinToString(" · ").takeIf { it.isNotBlank() }
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
                subtitle = "${it.bookCount} ${if (it.bookCount == 1) "book" else "books"}",
                type     = XMBItemType.LIBRARY_FOLDER,
            )
        }

    private fun bookSeriesItems(): List<XMBItem> =
        _uiState.value.bookSeries.map { series ->
            XMBItem(
                id       = "series_${series.name}",
                title    = series.name,
                subtitle = "${series.bookCount} ${if (series.bookCount == 1) "book" else "books"}",
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

    /** Returns true when [item] was a Library row and has been handled. */
    private fun handleBooksSelection(item: XMBItem): Boolean = when {
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
        else -> false
    }

    /** Hands the book to the chosen reader, or to the chooser when none is set. */
    private fun openBook(bookId: String) {
        menuSound.play(MenuSound.LAUNCH)
        viewModelScope.launch {
            val book = bookRepository.getBook(bookId) ?: return@launch
            val error = bookIntentResolver.launch(book, _uiState.value.defaultReader)
            if (error != null) {
                _uiState.update { it.copy(infoDialog = InfoDialogState(title = book.displayTitle, message = error)) }
            }
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

    // ── Photo ───────────────────────────────────────────────────────────────────

    // Library (Album) list drives the Photo root; re-render the root when it changes.
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

    // Whether the device can open a camera app. Checked once (the set of camera apps doesn't
    // change while PFP is on screen) so the Photo root never shows a broken Camera item.
    private val cameraAvailable: Boolean by lazy {
        runCatching {
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .resolveActivity(context.packageManager) != null
        }.getOrDefault(false)
    }

    // Launches the system camera app (no result expected, no camera permission needed — the
    // standard safe hand-off). Failure is logged, never crashes the shell.
    private fun launchCamera() {
        runCatching {
            context.startActivity(
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Timber.w(it, "Could not launch a camera app") }
    }

    // Photo root, PSP-style: Camera (when a camera app exists) and Albums first, then the Photo Apps
    // counterpart directly above the "Photos" memory card (second-to-bottom), with the "Add Photo
    // Library" row last — it disappears once a library has been scanned (further libraries are added
    // from Settings → Photo).
    private fun photoRootItems(): List<XMBItem> {
        val libraries = _uiState.value.photoLibraries
        val totalPhotos = libraries.sumOf { it.photoCount }
        val hasScannedLibrary = libraries.any { it.lastScannedAt != null }
        return buildList {
            if (cameraAvailable) {
                add(
                    XMBItem(
                        id       = CAMERA_ITEM_ID,
                        title    = "Camera",
                        subtitle = "Open the camera",
                        type     = XMBItemType.CAMERA,
                    )
                )
            }
            add(
                XMBItem(
                    id       = PHOTO_ALBUMS_ITEM_ID,
                    title    = "Albums",
                    subtitle = "${libraries.size} ${if (libraries.size == 1) "album" else "albums"}",
                    type     = XMBItemType.PHOTO_ALBUMS,
                )
            )
            // Photo Apps counterpart, sitting directly above the memory card.
            add(
                XMBItem(
                    id       = PHOTO_APPS_ITEM_ID,
                    title    = "Photo Apps",
                    subtitle = "Open your installed photo apps",
                    type     = XMBItemType.PHOTO_APPS,
                )
            )
            add(
                XMBItem(
                    id       = ALL_PHOTOS_ITEM_ID,
                    title    = "Photos",
                    subtitle = "$totalPhotos ${if (totalPhotos == 1) "photo" else "photos"}",
                    coverUri = MEMORY_CARD_ASSET_URI,
                    type     = XMBItemType.MEMORY_CARD,
                )
            )
            // Getting-started prompt: opens Settings → Photo. Drops away once a root has been
            // scanned (even if it found no photos), since the root is then managed in Settings.
            if (!hasScannedLibrary) add(addPhotoLibraryItem())
        }
    }

    private fun addPhotoLibraryItem(): XMBItem = XMBItem(
        id       = ADD_PHOTO_LIBRARY_ITEM_ID,
        title    = "Add Photo Library",
        subtitle = "Set your Photo root folder in Settings to get started",
        type     = XMBItemType.ADD_ACTION,
    )

    // Photo Apps: the apps the user added (stored under a dedicated pseudo-category so they don't
    // mix with the built-in Photo category), plus an "Add Photo Apps" row. Mirrors Music/Video Apps.
    private suspend fun photoAppItems(): List<XMBItem> {
        val apps = appCategoryRepository.appsForCategory(PHOTO_APPS_CATEGORY_ID)
            .notHiddenAt(HideLocationType.CATEGORY, PHOTO_APPS_CATEGORY_ID)
        val appItems = apps.map { it.toXmbItem(gameRepository.getAppEntry(it.packageName)) }
        return appItems + XMBItem(
            id       = ADD_PHOTO_APPS_ITEM_ID,
            title    = "Add Photo Apps",
            subtitle = "Pick installed apps to show here",
            type     = XMBItemType.ADD_ACTION,
        )
    }

    // One folder card per Album, drillable into its photos. The root folder is managed in
    // Settings → Photo, so there is no add row here.
    private fun photoAlbumItems(libraries: List<com.psplauncher.core.domain.model.PhotoLibrary>): List<XMBItem> {
        val rows = libraries.map { lib ->
            XMBItem(
                id       = "plib_${lib.id}",
                title    = lib.displayName,
                subtitle = "${lib.photoCount} ${if (lib.photoCount == 1) "photo" else "photos"}",
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
                subtitle = photoSubtitle(photo),
                type     = XMBItemType.PHOTO_FILE,
                mediaUri = photo.uri,
                mimeType = photo.mimeType,
                coverUri = photo.thumbnailUri,
            )
        }

    // "4032×3024  ·  Jul 14, 2026" — whichever parts are known; null when neither is.
    private fun photoSubtitle(photo: com.psplauncher.core.domain.model.Photo): String? {
        val date = photo.displayDateMs?.let {
            java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))
        }
        return listOfNotNull(photo.resolutionLabel, date).joinToString("  ·  ").ifEmpty { null }
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

    // Handles A/Cross on any Photo row. Returns true when [item] is a Photo row it owns.
    private fun handlePhotoSelection(item: XMBItem): Boolean = when {
        item.id == ALL_PHOTOS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openPhotoView(PhotoNav.AllPhotos); true }
        item.id == PHOTO_ALBUMS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openPhotoView(PhotoNav.Albums); true }
        item.id == PHOTO_APPS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openPhotoView(PhotoNav.PhotoApps); true }
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
        // Photo-app rows launch the app.
        _uiState.value.photoNav == PhotoNav.PhotoApps && item.packageName != null -> {
            menuSound.play(MenuSound.LAUNCH); appCategoryRepository.launch(item.packageName); true
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
        PhotoNav.PhotoApps  -> "apps"
        is PhotoNav.Library -> "library_${nav.id}"
    }

    private fun openPhotoView(nav: PhotoNav) = navigateRememberingCursor { it.copy(photoNav = nav) }

    private fun closePhotoView() = openPhotoView(PhotoNav.Root)

    // Opens the fullscreen viewer for a photo, scoped to the list it was opened from so L1/R1
    // pages through the same set the user was browsing.
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

    // Opens the △ options menu for a Photo row. Returns true when [item] is a photo row it owns
    // (a photo file or an Album card), so the generic menus don't also fire.
    private fun openPhotoContextMenu(item: XMBItem): Boolean {
        if (currentCategory()?.id != BuiltInCategory.PHOTO) return false
        return when {
            item.type == XMBItemType.PHOTO_FILE && item.id.startsWith("pho_") -> {
                openPhotoFileContextMenu(item.id.removePrefix("pho_"), item.title); true
            }
            item.type == XMBItemType.PHOTO_FOLDER && item.id.startsWith("plib_") -> {
                openPhotoLibraryContextMenu(item.id.removePrefix("plib_"), item.title); true
            }
            _uiState.value.photoNav == PhotoNav.PhotoApps && item.packageName != null -> {
                openAppContextMenu(item, categoryIdOverride = PHOTO_APPS_CATEGORY_ID); true
            }
            else -> false
        }
    }

    // Options for a single photo row. Viewing-related options (zoom, rotate, wallpaper) live in
    // the fullscreen viewer's own Options menu; the list row only opens/removes.
    private fun openPhotoFileContextMenu(photoId: String, title: String) {
        val items = listOf(
            XMBContextMenuItem("photo_open", "Open"),
            XMBContextMenuItem("photo_set_wallpaper", "Set as Launcher Wallpaper"),
            XMBContextMenuItem("photo_remove", "Remove From Library", isDestructive = true),
        )
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(title, items, photoFileId = photoId)) }
    }

    private fun handlePhotoFileAction(photoId: String, itemId: String) {
        when (itemId) {
            "photo_open"          -> openPhotoViewer(photoId)
            // Opens the viewer with the wallpaper preview already up — apply/cancel from there.
            "photo_set_wallpaper" -> openPhotoViewer(photoId, wallpaperPreview = true)
            "photo_remove"        -> appAction { photoRepository.removePhoto(photoId) }
        }
    }

    // Options for an Album card: open, scan, or manage (rename / change folder / remove) in Settings.
    private fun openPhotoLibraryContextMenu(libraryId: String, name: String) {
        val items = listOf(
            XMBContextMenuItem("photo_lib_open", "Open"),
            XMBContextMenuItem("photo_lib_scan", "Scan Album"),
            XMBContextMenuItem("photo_lib_manage", "Manage in Settings"),
        )
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(name, items, photoLibraryId = libraryId)) }
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

    // Quick scan of one Album straight from the XMB card, surfaced via the notification tray like
    // every other background scan. The library list flow refreshes the counts when it lands.
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

    // ── Fullscreen music browser (searchable) ───────────────────────────────────
    // Opens "Music" (all tracks) or "Playlist" (playlists → a playlist's tracks) as a fullscreen,
    // searchable overlay. A collector keeps the active view in sync with the DB; query/sort changes
    // re-derive the visible rows from the cached raw list without re-hitting the DB.
    private fun openMusicBrowser(view: MusicBrowserView) {
        musicBrowserJob?.cancel()
        val title = when (view) {
            MusicBrowserView.AllMusic    -> "Music"
            MusicBrowserView.Playlists   -> "Playlists"
            is MusicBrowserView.Playlist -> view.name
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
        currentMusicTracks = filtered   // the play queue is exactly what's on screen
        val baseRows = when {
            filtered.isNotEmpty() -> filtered.toMusicItems()
            q.isNotBlank()        -> listOf(browserNoResultsItem())
            isPlaylist            -> listOf(emptyPlaylistItem())
            else                  -> listOf(emptyAllMusicItem())
        }
        val rows = if (isPlaylist) baseRows + addTracksItem() else baseRows
        val label = "Sort: ${_uiState.value.musicSortMode.label}"
        _uiState.update { it.copy(musicBrowser = it.musicBrowser?.copy(
            rows = rows,
            selectedIndex = state.selectedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0)),
            sortLabel = label,
        )) }
    }

    private fun rebuildBrowserPlaylistRows() {
        val state = _uiState.value.musicBrowser ?: return
        val q = state.query.trim().lowercase()
        val filtered = if (q.isBlank()) browserRawPlaylists
                       else browserRawPlaylists.filter { it.name.lowercase().contains(q) }
        val rows = playlistRootItems(filtered)   // playlist rows + "Create Playlist"
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
        if (state.view is MusicBrowserView.Playlists) rebuildBrowserPlaylistRows() else rebuildBrowserTrackRows()
    }

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
            // A playlist's tracks back out to the playlists list; everything else closes the browser.
            is MusicBrowserView.Playlist -> openMusicBrowser(MusicBrowserView.Playlists)
            else -> closeMusicBrowser()
        }
    }

    private fun closeMusicBrowser() {
        musicBrowserJob?.cancel(); musicBrowserJob = null
        val view = _uiState.value.musicBrowser?.view
        browserRawTracks = emptyList(); browserRawPlaylists = emptyList()
        _uiState.update { it.copy(musicBrowser = null) }
        // Re-anchor the XMB cursor on the row the browser was opened from, so the reveal is
        // seamless even if the root list changed shape while the browser was open.
        if (currentCategory()?.id == BuiltInCategory.MUSIC && _uiState.value.musicNav == MusicNav.Root) {
            val targetId = when (view) {
                is MusicBrowserView.Playlists, is MusicBrowserView.Playlist -> PLAYLISTS_ITEM_ID
                else -> ALL_MUSIC_ITEM_ID
            }
            val idx = _uiState.value.currentItems.indexOfFirst { it.id == targetId }
            if (idx >= 0) _uiState.update { it.copy(selectedItemIndex = idx) }
        }
    }

    /** Touch: the browser's Sort pill — same as the X button. */
    fun onMusicBrowserSortTapped() {
        markTouchInput()
        cycleSort()
    }

    /** Touch: the browser's Options pill — opens the context menu for the highlighted row,
     *  same as the Y button. */
    fun onMusicBrowserOptionsTapped() {
        markTouchInput()
        openMusicBrowserContextMenu()
    }

    // Playlist context for a track's options menu, resolved from the browser or the inline view.
    private fun currentPlaylistContextId(): Long? =
        (_uiState.value.musicBrowser?.view as? MusicBrowserView.Playlist)?.id
            ?: (_uiState.value.musicNav as? MusicNav.Playlist)?.id

    // Handles A/Cross on any Music row. Returns true when [item] is a Music row it owns. Empty-state
    // rows are consumed silently; everything else plays its own select/launch sound.
    private fun handleMusicSelection(item: XMBItem): Boolean = when {
        item.type == XMBItemType.EMPTY -> true   // not selectable
        item.id == NOW_PLAYING_ITEM_ID -> {
            menuSound.play(MenuSound.SELECT)
            if (_uiState.value.musicPlayback.track != null) _uiState.update { it.copy(musicPlayerVisible = true) }
            true
        }
        // "Music" and "Playlist" open the fullscreen, searchable browser instead of the inline list.
        item.id == PLAYLISTS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openMusicBrowser(MusicBrowserView.Playlists); true }
        item.id == ALL_MUSIC_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openMusicBrowser(MusicBrowserView.AllMusic); true }
        item.id == MUSIC_APPS_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openMusicView(MusicNav.MusicApps); true }
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
        // Music-app rows launch the app.
        _uiState.value.musicNav == MusicNav.MusicApps && item.packageName != null -> {
            menuSound.play(MenuSound.LAUNCH); appCategoryRepository.launch(item.packageName); true
        }
        else -> false
    }

    // Selecting a song opens the in-app full player, with the on-screen track list as the queue.
    private fun openMusicPlayerForItem(item: XMBItem) {
        val trackId = item.id.removePrefix("mt_")
        val startIndex = currentMusicTracks.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
        if (currentMusicTracks.isEmpty()) return
        musicPlayer.setQueue(currentMusicTracks, startIndex)
        _uiState.update { it.copy(musicPlayerVisible = true) }
    }

    // ── In-app player controls (driven by the player overlay) ───────────────────
    fun musicPlayPause() = musicPlayer.playPause()
    fun musicNext() = musicPlayer.next()
    fun musicPrev() = musicPlayer.prev()
    fun musicSeekTo(ms: Int) = musicPlayer.seekTo(ms)
    private fun musicSeekBy(deltaMs: Int) = musicPlayer.seekBy(deltaMs)

    // Back / tap-outside on the player only hides the overlay — playback keeps going so the Music
    // root's "Now Playing" item can return to it. Stopping is explicit (player Y → Stop & Close).
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
                activeContextMenu = XMBContextMenu(
                    title = title,
                    items = listOf(
                        XMBContextMenuItem("music_background", "Play in Background"),
                        XMBContextMenuItem("music_close", "Stop & Close"),
                    ),
                    musicTrackId = MUSIC_PLAYER_MENU_MARKER,
                )
            )
        }
    }

    // Options (△) for the "Now Playing" row in the Music root: toggle playback or stop & close.
    private fun openNowPlayingContextMenu() {
        val playback = _uiState.value.musicPlayback
        if (playback.track == null) return
        _uiState.update {
            it.copy(
                activeContextMenu = XMBContextMenu(
                    title = playback.track.displayTitle,
                    items = listOf(
                        XMBContextMenuItem("music_playpause", if (playback.isPlaying) "Pause" else "Resume"),
                        XMBContextMenuItem("music_close", "Stop and Close"),
                    ),
                    musicTrackId = MUSIC_PLAYER_MENU_MARKER,
                )
            )
        }
    }

    // Keep PFP's own playback going and promote it to a foreground media notification so the user
    // can leave PFP and use other apps; just hide the full-screen player UI.
    private fun musicPlayInBackground() {
        if (musicPlayer.currentTrack() == null) return
        com.psplauncher.feature.xmb.music.MusicPlaybackService.start(context)
        _uiState.update { it.copy(musicPlayerVisible = false) }
    }

    private fun openMusicTrackContextMenu(item: XMBItem) {
        // Inside a playlist (inline or browser), offer "Remove from this Playlist"; the playlist id
        // rides on the menu so the action knows which playlist.
        val playlistId = currentPlaylistContextId()
        val items = buildList {
            add(XMBContextMenuItem("play", "Play"))
            add(XMBContextMenuItem("play_background", "Play in Background"))
            add(XMBContextMenuItem("add_to_playlist", "Add to Playlist"))
            if (playlistId != null) {
                add(XMBContextMenuItem("remove_from_playlist", "Remove from this Playlist", isDestructive = true))
            }
            add(XMBContextMenuItem("remove_track", "Remove From Library", isDestructive = true))
        }
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(
                title        = item.title,
                items        = items,
                musicTrackId = item.id.removePrefix("mt_"),
                playlistId   = playlistId,
            )
        )}
    }

    // Options menu for a playlist row: open / rename / add tracks / delete.
    private fun openPlaylistRowContextMenu(playlistId: Long, name: String) {
        val items = listOf(
            XMBContextMenuItem("open_playlist", "Open"),
            XMBContextMenuItem("add_tracks", "Add Tracks"),
            XMBContextMenuItem("rename_playlist", "Rename Playlist"),
            XMBContextMenuItem("delete_playlist", "Delete Playlist", isDestructive = true),
        )
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(name, items, playlistId = playlistId)) }
    }

    // Second-level menu: the playlists a track can be added to (checkmarks show membership), plus
    // "Create New Playlist". Stays open while toggling so several can be picked at once.
    private fun openPlaylistPicker(trackId: String, selectIndex: Int = 0) {
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
                activeContextMenu = XMBContextMenu(
                    title                 = "Add to Playlist",
                    items                 = items,
                    selectedIndex         = selectIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
                    playlistPickerTrackId = trackId,
                )
            )}
        }
    }

    // Opens the right options (△) menu for a Music item. Returns true when [item] is a music row
    // it owns (track / playlist / music-app), so the generic Y handler can stop. The Now Playing
    // row is consumed without a menu (its options live in the full player).
    private fun openMusicContextMenu(item: XMBItem): Boolean {
        if (currentCategory()?.id != BuiltInCategory.MUSIC) return false
        return when {
            item.id == NOW_PLAYING_ITEM_ID -> { openNowPlayingContextMenu(); true }
            item.type == XMBItemType.MUSIC_TRACK -> { openMusicTrackContextMenu(item); true }
            item.type == XMBItemType.PLAYLIST && item.playlistId != null -> {
                openPlaylistRowContextMenu(item.playlistId, item.title); true
            }
            _uiState.value.musicNav == MusicNav.MusicApps && item.packageName != null -> {
                openAppContextMenu(item, categoryIdOverride = MUSIC_APPS_CATEGORY_ID); true
            }
            else -> false
        }
    }

    // ── Playlist name dialog (create / rename) ──────────────────────────────────
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

    // ── "Add Tracks" picker (inside a playlist) ─────────────────────────────────
    private fun openMusicTrackPicker(playlistId: Long) {
        viewModelScope.launch {
            val playlist = musicRepository.observePlaylists().first().firstOrNull { it.id == playlistId }
            // Offer tracks not already in the playlist.
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
        val maxIndex = picker.tracks.size   // 0 = Confirm row, 1..size = tracks
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

    // Music folder context-menu actions, dispatched from activateContextMenuItem. Folder management
    // now lives in Settings → Music; this is retained for the scan/enable/remove paths it backs.
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
            // Play in the in-app full player, queuing from the current on-screen list.
            "play" -> {
                val startIndex = currentMusicTracks.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                if (currentMusicTracks.isNotEmpty()) {
                    musicPlayer.setQueue(currentMusicTracks, startIndex)
                    _uiState.update { it.copy(musicPlayerVisible = true) }
                }
            }
            // Play in PFP and promote straight to the background media notification (no full
            // player UI), queuing from the current on-screen list.
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
            "remove_track" -> appAction {
                val track = musicRepository.getTrack(trackId) ?: return@appAction
                removeSingleTrack(track.folderId, trackId)
            }
        }
    }

    // Playlist row actions, dispatched from activateContextMenuItem.
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
        // Read current tracks once, drop the removed one, and replace the folder set.
        val tracks = musicRepository.observeTracksByFolder(folderId).first().filterNot { it.id == trackId }
        musicRepository.replaceTracksForFolder(folderId, tracks, System.currentTimeMillis())
    }

    // ── Sort (X / Square) ─────────────────────────────────────────────────────

    // Delegates to the pure XMBUiState.activeSortModes() so the sort hint pill and this handler
    // can never disagree about whether the current list sorts.
    private fun activeSortContext(): List<XmbSortMode>? = _uiState.value.activeSortModes()

    /** Touch: the status-bar sort chip — cycles the sort order, same as X/Square. */
    fun onSortLabelTapped() {
        markTouchInput()
        cycleSort()
    }

    private fun cycleSort() {
        // The fullscreen music browser sorts its own track views (not the playlists list).
        _uiState.value.musicBrowser?.let { browser ->
            if (browser.view is MusicBrowserView.Playlists) return
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
        // Re-sorting moves the cursor back to the top item so the user sees the new ordering from
        // the start, and bumps the scroll token so the list snaps to the top every time (not just
        // the first sort after the cursor moved).
        _uiState.update {
            it.withSortMode(cycle, next)
                .copy(selectedItemIndex = 0, scrollToTopToken = it.scrollToTopToken + 1)
        }
        // Music track lists re-sort instantly from the cached raw list — no DB round-trip, so the
        // reorder is always visible immediately. A playlist keeps its trailing "Add Tracks" row.
        if (isMusic) {
            val trailing = if (_uiState.value.musicNav is MusicNav.Playlist) listOf(addTracksItem()) else emptyList()
            val emptyItem = if (_uiState.value.musicNav is MusicNav.Playlist) emptyPlaylistItem() else emptyAllMusicItem()
            setMusicTrackItems(currentMusicTracksRaw, emptyItem, trailing)
            _uiState.update { it.copy(sortLabel = currentSortLabel()) }
            return
        }
        loadItemsForCategory(currentCategory())
    }

    // The parent label for the two-pane flyout, non-null whenever drilled into ANY sub-item — a Games
    // sub-item (platform card / All Games / Favorites / collection) or a Music sub-view (Music Apps /
    // a playlist / All Music). Null = top level, normal single-column list.
    private fun computeDrillTitle(): String? {
        val s = _uiState.value
        // Music sub-navigation is a drill-in too — a non-null title makes it show the two-pane flyout.
        val musicTitle = when (val nav = s.musicNav) {
            MusicNav.MusicApps   -> "Music Apps"
            MusicNav.AllMusic    -> "Music"
            MusicNav.Playlists   -> "Playlist"
            is MusicNav.Playlist -> nav.name
            MusicNav.Root        -> null
        }
        if (musicTitle != null) return musicTitle
        // Video sub-navigation is a drill-in too — a non-null title shows the two-pane flyout.
        val videoTitle = when (val nav = s.videoNav) {
            VideoNav.AllVideos       -> "All Videos"
            VideoNav.Collections     -> "Collections"
            VideoNav.RecentlyWatched -> "Recently Watched"
            VideoNav.Favorites       -> "Favorites"
            VideoNav.Playlists       -> "Playlists"
            is VideoNav.Playlist     -> nav.name
            VideoNav.Libraries       -> "Video Libraries"
            is VideoNav.Library      -> nav.name
            VideoNav.VideoApps       -> "Video Apps"
            VideoNav.Root            -> null
        }
        if (videoTitle != null) return videoTitle
        // Photo sub-navigation is a drill-in too.
        val photoTitle = when (val nav = s.photoNav) {
            PhotoNav.AllPhotos  -> "All Photos"
            PhotoNav.Albums     -> "Albums"
            PhotoNav.PhotoApps  -> "Photo Apps"
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

    // Icon-only sibling lists for the deepest drill level, so the flyout's left column always shows
    // the current level's peers (a library among libraries, an album among albums, a playlist among
    // playlists) — mirroring how the Games flyout shows the console cross.
    private fun videoLibrarySiblings(): List<XMBItem> =
        _uiState.value.videoLibraries.map { XMBItem(id = "vlib_${it.id}", title = it.displayName, type = XMBItemType.VIDEO_FOLDER) }

    private fun photoAlbumSiblings(): List<XMBItem> =
        _uiState.value.photoLibraries.map { XMBItem(id = "plib_${it.id}", title = it.displayName, type = XMBItemType.PHOTO_FOLDER) }

    private fun musicPlaylistSiblings(): List<XMBItem> =
        _uiState.value.musicPlaylists.map { XMBItem(id = "pl_${it.id}", title = it.name, playlistId = it.id, type = XMBItemType.PLAYLIST) }

    private fun videoPlaylistSiblings(): List<XMBItem> =
        _uiState.value.videoPlaylists.map { XMBItem(id = "vpl_${it.id}", title = it.name, playlistId = it.id, type = XMBItemType.PLAYLIST) }

    // The sibling icon column for the flyout's left side. In the Main Game category these are the
    // memory-card root items (All Games / Favorites / collections / consoles); the currently
    // drilled-into one is returned as the centred index. Other categories fall back to just the
    // single parent so the flyout still shows one icon.
    private fun computeDrillSiblings(category: Category?): Pair<List<XMBItem>, Int> {
        val s = _uiState.value
        // Music sub-navigation: the left column is the Music root's sections (Playlist / Music Apps /
        // Music), with the drilled-into one centred on the arrow.
        if (s.musicNav != MusicNav.Root) {
            // Inside a specific playlist: peers are the other playlists.
            (s.musicNav as? MusicNav.Playlist)?.let { nav ->
                val pls = musicPlaylistSiblings()
                if (pls.isNotEmpty()) return pls to pls.indexOfFirst { it.playlistId == nav.id }.coerceAtLeast(0)
            }
            val sibs = musicRootItems().filter {
                it.type == XMBItemType.PLAYLIST || it.type == XMBItemType.MUSIC_APPS ||
                    it.type == XMBItemType.MEMORY_CARD
            }
            val idx = sibs.indexOfFirst { sib ->
                when (s.musicNav) {
                    MusicNav.MusicApps -> sib.type == XMBItemType.MUSIC_APPS
                    MusicNav.AllMusic  -> sib.type == XMBItemType.MEMORY_CARD
                    else               -> sib.type == XMBItemType.PLAYLIST   // Playlists / a Playlist
                }
            }.coerceAtLeast(0)
            return sibs to idx
        }
        // Video sub-navigation. Two levels now: the Collections children (Recently Watched /
        // Favorites / Playlists / a Playlist) show the Collections sub-list as their sibling column;
        // everything else shows the Video root's sections (All Videos / Collections / Video
        // Libraries / Video Apps). The drilled-into one is centred on the arrow.
        if (s.videoNav != VideoNav.Root) {
            // Deepest levels show their own peers: a library among the libraries, a playlist among
            // the playlists.
            (s.videoNav as? VideoNav.Library)?.let { nav ->
                val libs = videoLibrarySiblings()
                if (libs.isNotEmpty()) return libs to libs.indexOfFirst { it.id == "vlib_${nav.id}" }.coerceAtLeast(0)
            }
            (s.videoNav as? VideoNav.Playlist)?.let { nav ->
                val pls = videoPlaylistSiblings()
                if (pls.isNotEmpty()) return pls to pls.indexOfFirst { it.playlistId == nav.id }.coerceAtLeast(0)
            }
            // The three Collections views show the Collections sub-list (distinct icons per view).
            if (s.videoNav.isVideoCollectionChild || s.videoNav is VideoNav.Playlist) {
                val sibs = videoCollectionsItems()
                val idx = sibs.indexOfFirst { sib ->
                    when (s.videoNav) {
                        VideoNav.RecentlyWatched -> sib.type == XMBItemType.VIDEO_RECENT
                        VideoNav.Favorites       -> sib.type == XMBItemType.VIDEO_FAVORITES
                        else                     -> sib.type == XMBItemType.PLAYLIST  // Playlists / a Playlist
                    }
                }.coerceAtLeast(0)
                return sibs to idx
            }
            // Root sections: All Videos / Collections / Video Libraries / Video Apps.
            val sibs = videoRootItems().filter {
                it.type == XMBItemType.MEMORY_CARD || it.type == XMBItemType.VIDEO_COLLECTIONS ||
                    it.type == XMBItemType.VIDEO_LIBRARY || it.type == XMBItemType.VIDEO_APPS
            }
            val idx = sibs.indexOfFirst { sib ->
                when (s.videoNav) {
                    VideoNav.AllVideos   -> sib.type == XMBItemType.MEMORY_CARD
                    VideoNav.Collections -> sib.type == XMBItemType.VIDEO_COLLECTIONS
                    VideoNav.VideoApps   -> sib.type == XMBItemType.VIDEO_APPS
                    else                 -> sib.type == XMBItemType.VIDEO_LIBRARY  // Libraries (list view)
                }
            }.coerceAtLeast(0)
            return sibs to idx
        }
        // Photo sub-navigation: the left column is the Photo root's drillable sections (the All
        // Photos memory card and Albums), with the drilled-into one centred on the arrow. An open
        // Album belongs to the Albums section, like a Video library under Video Libraries.
        if (s.photoNav != PhotoNav.Root) {
            // Inside a specific album: peers are the other albums.
            (s.photoNav as? PhotoNav.Library)?.let { nav ->
                val albums = photoAlbumSiblings()
                if (albums.isNotEmpty()) return albums to albums.indexOfFirst { it.id == "plib_${nav.id}" }.coerceAtLeast(0)
            }
            val sibs = photoRootItems().filter {
                it.type == XMBItemType.MEMORY_CARD || it.type == XMBItemType.PHOTO_ALBUMS ||
                    it.type == XMBItemType.PHOTO_APPS
            }
            val idx = sibs.indexOfFirst { sib ->
                when (s.photoNav) {
                    PhotoNav.AllPhotos -> sib.type == XMBItemType.MEMORY_CARD
                    PhotoNav.PhotoApps -> sib.type == XMBItemType.PHOTO_APPS
                    else               -> sib.type == XMBItemType.PHOTO_ALBUMS  // Albums (list view)
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
        // Custom gaming category drilled into a collection — just show the single collection icon.
        val parent = XMBItem(id = "drill_parent", title = computeDrillTitle().orEmpty(), type = XMBItemType.COLLECTION)
        return listOf(parent) to 0
    }

    // Status-bar hint for the current list ("Sort: Title"), or null when the list isn't sortable.
    private fun currentSortLabel(): String? {
        val cycle = activeSortContext() ?: return null
        return "Sort: ${_uiState.value.sortModeFor(cycle).label}"
    }

    private fun emptyCategoryItem(category: Category): XMBItem {
        val (message, subtitle) = if (category.isGamingCategory) {
            "No games assigned." to "Add games to this category."
        } else {
            val msg = when (category.id) {
                "videos"    -> "No video apps found."
                "network"   -> "No browser apps found."
                "app_store" -> "No app stores found."
                "music"     -> "No music apps found."
                "photos"    -> "No photo apps found."
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

    // Games root: one item per enabled Memory Card (already ordered pinned-first by the DAO).
    private fun memoryCardItems(): List<XMBItem> {
        // Real games only (excludes app-style entries), matching what All Games actually shows.
        val totalGames = _uiState.value.allGamesCount
        val allGamesItem = XMBItem(
            id       = ALL_GAMES_ITEM_ID,
            title    = "All Games",
            subtitle = "Total Games $totalGames",
            type     = XMBItemType.ALL_GAMES,
        )

        // Favorites sits directly under All Games, but only when at least one game is favorited.
        val favoritesCount = _uiState.value.favoritesCount
        val favoritesItem = if (favoritesCount > 0) {
            XMBItem(
                id       = FAVORITES_ITEM_ID,
                title    = "Favorites",
                subtitle = "$favoritesCount ${if (favoritesCount == 1) "Game" else "Games"}",
                type     = XMBItemType.FAVORITES,
            )
        } else null

        // Missing sits under Favorites and only exists while something is actually missing, so a
        // healthy library never sees it. It disappears on its own once the files come back.
        val missingCount = _uiState.value.missingCount
        val missingItem = if (missingCount > 0) {
            XMBItem(
                id       = MISSING_ITEM_ID,
                title    = "Missing",
                subtitle = "$missingCount ${if (missingCount == 1) "Game" else "Games"}",
                type     = XMBItemType.MISSING,
            )
        } else null
        val header = listOfNotNull(allGamesItem, favoritesItem, missingItem)

        // User collections sit just under All Games / Favorites — like Favorites but user-defined.
        // Only collections assigned to this (the Main Game) category appear here; categoryId
        // is the single source of truth for a collection's placement. Pinned collections first.
        val collectionItems = _uiState.value.collections
            .filter { it.categoryId == BuiltInCategory.GAMES }
            .sortedByDescending { it.isPinned }
            .map { collection ->
            val games = "${collection.gameCount} ${if (collection.gameCount == 1) "Game" else "Games"}"
            XMBItem(
                id           = "collection_${collection.id}",
                title        = collection.name,
                subtitle     = if (collection.isPinned) "Pinned · $games" else games,
                collectionId = collection.id,
                iconKey      = collection.iconKey,
                type         = XMBItemType.COLLECTION,
            )
        }

        if (enabledCards.isEmpty()) {
            return header + collectionItems + XMBItem(
                id       = NO_CONSOLES_ITEM_ID,
                title    = "No consoles configured",
                subtitle = "Open Library Manager to add a Memory Card",
                type     = XMBItemType.EMPTY,
            )
        }

        // Windows is import-driven and belongs under Settings ▸ Library rather than the normal
        // console cards. Only surface it here when the Windows card actually contains game rows.
        val visibleCards = enabledCards.filter { card ->
            card.platformId != WINDOWS_PLATFORM_ID ||
                (_uiState.value.platformGameCounts[WINDOWS_PLATFORM_ID] ?: card.gameCount) > 0
        }

        return header + collectionItems + visibleCards.map { card ->
            val count = _uiState.value.platformGameCounts[card.platformId] ?: card.gameCount
            XMBItem(
                id          = "card_${card.platformId}",
                title       = if (card.platformId == WINDOWS_PLATFORM_ID) "Windows Games" else card.displayName,
                subtitle    = "$count ${if (count == 1) "Game" else "Games"}",
                platformId  = card.platformId,
                accentColor = platformCache[card.platformId]?.accentColor,
                type        = XMBItemType.MEMORY_CARD,
            )
        }
    }

    // B3: the empty All Games row names the FIRST unmet setup step and confirms through to the
    // screen that fixes it. "No games imported yet" told a fresh install nothing actionable.
    private fun emptyAllGamesItem(): XMBItem {
        val gap = setupState.firstGap
        if (gap != com.psplauncher.feature.launcher.SetupGap.NONE) {
            return XMBItem(
                id       = SETUP_GAP_ITEM_ID,
                title    = gap.message,
                subtitle = "Press confirm to open Settings and fix it.",
                type     = XMBItemType.EMPTY,
            )
        }
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

    // Only reachable in the gap between the last missing game being resolved and the Missing row
    // disappearing — worth having so the bucket never renders as a blank list.
    private fun emptyMissingItem(): XMBItem = XMBItem(
        id       = EMPTY_MISSING_ITEM_ID,
        title    = "Nothing missing",
        subtitle = "Every game's file was found on the last scan.",
        type     = XMBItemType.EMPTY,
    )

    // Shown when an opened Memory Card has no games yet. Keeps the platformId so the
    // context menu (Triangle) can still offer "Scan This Console".
    private fun emptyFolderItem(platformId: String): XMBItem {
        // Android-style libraries pick installed apps instead of scanning folders.
        if (platformId == ANDROID_PLATFORM_ID) {
            return XMBItem(
                id         = FIND_GAMES_ITEM_ID,
                title      = "Find Games",
                subtitle   = "Pick installed apps to add to this library",
                platformId = platformId,
            )
        }
        val card = enabledCards.firstOrNull { it.platformId == platformId }
        // B3: when setup is still incomplete, the card's empty row names the FIRST unmet step
        // instead of generic folder copy — most often "the root exists but no ROM folder yet".
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

    /**
     * Publishes a game list. With [keepCursorOnRow] the cursor follows its row by id
     * ([cursorAfterRefresh]); without it the cursor keeps its index, which is what a fresh drill-in
     * needs, since navigateRememberingCursor has already set the index it should land on.
     */
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
            subtitle     = platformEmulatorLabel(g),
            metadataLine = gameMetadataLine(g.releaseYear, g.genre, g.developer, g.players),
            gameId       = g.id,
            platformId   = g.platformId,
            accentColor  = platformCache[g.platformId]?.accentColor,
            isFavorite   = g.isFavorite,
            isAndroidApp = g.packageName != null,
            isRealGame   = g.contentType == GameContentType.GAME,
            packageName  = g.packageName,
            shortcutId   = g.shortcutId,
            launchIntentUri = g.launchIntentUri,
        )
    }

    private fun tintWaveForCategory(category: Category?) {
        // PSP-authentic: one theme color across the whole XMB — no per-category wave re-tint.
        _uiState.update { it.copy(themeColors = baseThemeColors) }
    }

    // ── Gamepad ───────────────────────────────────────────────────────────────

    private fun observeGamepadMappings() {
        viewModelScope.launch {
            mappingRepository.mappings.collect { mappings ->
                gamepadInputHandler.currentMappings = mappings
            }
        }
        viewModelScope.launch {
            controllerLayoutRepository.prefs.collect { prefs ->
                // Prompt glyphs are supplied ambiently by ProvideControllerPrompts at the
                // app root, so the display type no longer needs mirroring into UI state.
                gamepadInputHandler.scrollSpeed = prefs.scrollSpeed
                _uiState.update { it.copy(leftBacksOut = prefs.leftBacksOut) }
            }
        }
    }

    private fun collectGamepadActions() {
        viewModelScope.launch {
            gamepadInputHandler.actions.collect { action ->
                onUserInteraction()
                dispatchGamepadAction(action)
            }
        }
    }

    // ── Idle hint pill ─────────────────────────────────────────────────────────
    // A configurable idle pause over an item that has a context menu, or on a list that sorts
    // (after controller input, with no overlay up), fades in the Sort / Options pill next to the
    // App Drawer button, drawn with the user's own controller glyphs.
    //
    // HIDING IS NOT THIS LOOP'S JOB for the common case: every input path clears the flag
    // synchronously (markTouchInput / markControllerInput / onUserInteraction), so the pill goes
    // the instant a button is pressed rather than up to IDLE_HINT_POLL_MS later. The loop still
    // clears it for causes that bypass those hooks (an overlay raised by a background task), and
    // it remains the only thing that RAISES it.
    //
    // Polls cheaply (every ~500ms) and only writes on a visibility transition, so it costs
    // nothing while idle.
    private fun observeContextMenuHintIdle() {
        viewModelScope.launch {
            while (isActive) {
                delay(IDLE_HINT_POLL_MS)
                val s = _uiState.value
                val idleMs = SystemClock.elapsedRealtime() - lastInteractionMs
                val shouldShow = com.psplauncher.feature.xmb.viewmodel.shouldShowContextMenuHint(
                    state = s,
                    idleMs = idleMs,
                )
                // Same clock, second consumer: the App Drawer's own pill. The two gates are mutually
                // exclusive (the drawer is a blocking overlay, so the XMB gate is false while it is
                // open), but both ride this poller so there is a single idle source of truth.
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

        // ── Installed-app picker captures ALL input when open ──────────────────
        if (state.appPicker != null) {
            when (action) {
                GamepadAction.NAVIGATE_UP,
                GamepadAction.NAVIGATE_DOWN,
                GamepadAction.NAVIGATE_LEFT,
                GamepadAction.NAVIGATE_RIGHT -> moveAppPicker(action)
                // Confirm toggles the focused tile — never closes anything (§9).
                // While the removal-confirmation modal is up, SELECT activates the modal's
                // highlighted option (Cancel or Remove) instead of toggling a grid tile.
                GamepadAction.SELECT -> {
                    val picker = state.appPicker
                    if (picker.confirmingRemovals) {
                        if (picker.confirmFocusedOption == AppPickerState.CONFIRM_REMOVE) commitAppPicker()
                        else cancelConfirm()
                    } else toggleFocusedApp()
                }
                // Start applies the diff (with a confirmation pass when removals are pending).
                GamepadAction.HOME -> requestApplyAppPicker()
                GamepadAction.CHANGE_SORT -> _uiState.update { s ->
                    s.copy(appPicker = s.appPicker?.let { p ->
                        (if (p.searchActive) closeAppPickerSearch(p) else p.copy(searchActive = true)).clampFocus()
                    })
                }
                // Back unwinds one layer: search → removal confirmation → picker.
                GamepadAction.BACK,
                GamepadAction.OPEN_CONTEXT_MENU -> handleAppPickerBack()
                else -> Unit
            }
            return
        }

        // ── "Add Tracks" music picker captures ALL input when open ─────────────
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

        // ── Game picker captures ALL input when open ───────────────────────────
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

        // ── Context menu captures ALL input when open ──────────────────────────
        if (state.activeContextMenu != null) {
            when (action) {
                GamepadAction.NAVIGATE_UP   -> shiftContextMenu(-1)
                GamepadAction.NAVIGATE_DOWN -> shiftContextMenu(+1)
                GamepadAction.SELECT        -> activateContextMenuItem()
                GamepadAction.BACK,
                GamepadAction.OPEN_CONTEXT_MENU      -> closeContextMenu()
                else -> Unit
            }
            return
        }

        // ── Live "Adjust XMB Layout" editor captures ALL input while open ──────
        if (state.xmbLayoutAdjust != null) {
            when (action) {
                GamepadAction.NAVIGATE_LEFT  -> nudgeXmbLayoutHorizontal(-1)
                GamepadAction.NAVIGATE_RIGHT -> nudgeXmbLayoutHorizontal(+1)
                GamepadAction.NAVIGATE_UP    -> nudgeXmbLayoutVertical(-1)
                GamepadAction.NAVIGATE_DOWN  -> nudgeXmbLayoutVertical(+1)
                GamepadAction.PREV_CATEGORY  -> nudgeXmbLayoutScale(-1)
                GamepadAction.NEXT_CATEGORY  -> nudgeXmbLayoutScale(+1)
                GamepadAction.OPEN_CONTEXT_MENU -> resetXmbLayoutAdjust()
                // Was a second OPEN_CONTEXT_MENU branch, so it never ran and the sliders could
                // only be reached by touch. The overlay's own hint always named Square/X for it.
                GamepadAction.CHANGE_SORT       -> toggleXmbLayoutSliders()
                GamepadAction.SELECT         -> saveXmbLayoutAdjust()
                GamepadAction.BACK           -> cancelXmbLayoutAdjust()
                else -> Unit
            }
            return
        }

        // ── In-app music player captures ALL input while open ──────────────────
        // (Below the context-menu branch so the player's own Y options menu wins when shown.)
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

        // ── Color-scheme picker captures ALL input when open (sits above Settings) ──
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

        // ── Modal text dialogs capture ALL input — text entry needs a keyboard, so
        //    only BACK is meaningful (cancel). The XMB behind must never move. ──────
        if (state.renameAppTarget != null) {
            if (action == GamepadAction.BACK) onCancelAppRename()
            return
        }
        if (state.collectionNameDialog != null) {
            if (action == GamepadAction.BACK) onCancelCollectionName()
            return
        }
        if (state.playlistNameDialog != null) {
            if (action == GamepadAction.BACK) onCancelPlaylistName()
            return
        }
        // Read-only info dialog (e.g. file location) — A or B closes it.
        if (state.infoDialog != null) {
            if (action == GamepadAction.BACK || action == GamepadAction.SELECT) dismissInfoDialog()
            return
        }
        // Launch recovery sheet (B1) — A confirms the highlighted action, B dismisses.
        if (state.launchRecovery != null) {
            when (action) {
                GamepadAction.SELECT -> onLaunchRecoveryAction(LaunchRecoveryAction.RETRY)
                GamepadAction.BACK   -> onLaunchRecoveryAction(LaunchRecoveryAction.DISMISS)
                else                 -> Unit
            }
            return
        }
        // Windows Library setup prompt — A sets up (Library Manager), B defers.
        if (state.showWindowsSetupPrompt) {
            when (action) {
                GamepadAction.SELECT -> confirmWindowsSetupPrompt()
                GamepadAction.BACK   -> dismissWindowsSetupPrompt()
                else                 -> Unit
            }
            return
        }

        // ── Fullscreen music browser captures input. Below the context-menu / player / dialog
        //    branches above, so a menu (Y) or the player opened from it wins. ─────────────────
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

        // ── Boot sequence overlay swallows input, except the skip ──────────────
        // Confirm or Back ends the presentation through the SAME completion path the animation,
        // the watchdog, and a player error use — so a user who does not want to watch a 10-second
        // custom boot video is never held by it. (There is no START action in this app's mapping
        // vocabulary; Back is the other button a user reaches for to get out of something.)
        if (state.showBootSequence) {
            if (action == GamepadAction.SELECT || action == GamepadAction.BACK) {
                onBootSequenceComplete()
            }
            return
        }

        // ── GameBoot presentation: same deal, and mashing Confirm must not launch twice ────
        // Every press lands here rather than on the game row underneath, so the extra presses a
        // user makes while the transition plays are absorbed, not queued into a second launch.
        if (state.activeGameBoot != null) {
            if (action == GamepadAction.SELECT || action == GamepadAction.BACK) {
                onGameBootComplete()
            }
            return
        }

        // ── Overlays (innermost wins) ──────────────────────────────────────────
        when {
            state.activePhotoViewer != null -> {
                // Forward everything so the fullscreen photo viewer can handle its own controls
                // (options menu, zoom/pan, wallpaper preview) before popping back to the XMB.
                _uiState.update { it.copy(pendingPhotoViewerAction = action) }
                return
            }
            state.activeVideoId != null -> {
                // Forward everything so the Video Detail page (and its player overlay) can handle
                // input and close its own layers before popping back to the XMB.
                _uiState.update { it.copy(pendingVideoDetailAction = action) }
                return
            }
            state.activeGameId != null -> {
                // Forward everything (incl. BACK) so the Details page can close its own inner
                // overlays first and only then pop back to the XMB (via onCloseGameDetail).
                _uiState.update { it.copy(pendingGameDetailAction = action) }
                return
            }
            state.activeAppId != null -> {
                // Forward everything so the App Detail page can close its own inner overlays
                // (artwork picker) before popping back to the XMB (via onCloseAppDetail).
                _uiState.update { it.copy(pendingAppDetailAction = action) }
                return
            }
            state.activeSettingsScreen != null -> {
                Timber.d("Gamepad → settings(${state.activeSettingsScreen}): $action")
                // BACK is forwarded into the settings layer (not handled here) so the active
                // screen can do one-level-up navigation through its own back handler — exactly
                // like the on-screen Back button. The screen calls onCloseSettingsScreen() only
                // when it's already at its top level, which returns to the XMB.
                when (action) {
                    GamepadAction.BACK,
                    GamepadAction.NAVIGATE_UP,
                    GamepadAction.NAVIGATE_DOWN,
                    // Left/Right and the two secondary face buttons are ignored by the
                    // scaffold's default nav but reachable via onInterceptAction — screens with
                    // horizontal strips, per-row context menus or per-row shortcuts (Themes,
                    // Sound) consume them there. Both presses arrive whichever way the X/Y
                    // layout binds them, so both are forwarded (they're treated identically,
                    // as everywhere else).
                    GamepadAction.NAVIGATE_LEFT,
                    GamepadAction.NAVIGATE_RIGHT,
                    GamepadAction.OPEN_CONTEXT_MENU,
                    GamepadAction.CHANGE_SORT,
                    GamepadAction.SELECT -> _uiState.update { it.copy(pendingSettingsAction = action) }
                    else -> Unit
                }
                return
            }
            state.activeAppDrawerFilter != null -> {
                // Every action — including BACK — is forwarded to the drawer. The drawer resolves
                // BACK itself, the same way Game/App Detail do: while its options menu or
                // uninstall confirm is open, BACK pops that inner overlay (never the drawer);
                // only a BACK on the plain grid closes the drawer (via onBack → onCloseAppDrawer).
                _uiState.update { it.copy(pendingDrawerAction = action) }
                return
            }
            state.customIconSession != null -> {
                // The icon editor owns the pad: LEFT/RIGHT (and UP/DOWN, mirrored) step the
                // slot cursor through the group's list — the strip is horizontal, so left and
                // right read naturally — while the L/R shoulders cycle the group tabs
                // [Categories, Items, Status, Consoles]. SELECT opens the SAF picker (the
                // overlay observes the forwarded action), OPTIONS resets the focused slot,
                // BACK exits.
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

        // Defensive net: the main XMB navigation below must NEVER run while any overlay,
        // menu, or modal dialog is on screen. Each case above returns for its own handling;
        // this guards against a future overlay being added without its own branch.
        if (state.hasBlockingOverlay) return

        when (action) {
            // Item cursor moves through the shared moveItemCursor() so touch swipes and the D-pad
            // drive identical logic; cancel auto-repeat when we hit a list boundary.
            GamepadAction.NAVIGATE_UP   -> if (!moveItemCursor(-1)) gamepadInputHandler.cancelRepeat()
            GamepadAction.NAVIGATE_DOWN -> if (!moveItemCursor(+1)) gamepadInputHandler.cancelRepeat()
            GamepadAction.NAVIGATE_LEFT -> {
                // While drilled into a sub-item, LEFT does not escape to another category — it
                // backs out one level, the direction the XMB's own drill-in metaphor implies. It
                // deliberately does NOT fall through to the App Drawer the way BACK does (that is
                // BACK's job, and LEFT would reach it by surprise), and it does not markTouchInput:
                // a controller press must not flip the contextual App Drawer button to touch mode.
                if (state.isInSubItem) {
                    gamepadInputHandler.cancelRepeat()
                    if (!state.leftBacksOut) return
                    menuSound.play(MenuSound.BACK)
                    backOutOfDrill(state)
                    return
                }
                val next = (state.selectedCategoryIndex - 1).coerceAtLeast(0)
                if (next != state.selectedCategoryIndex) onCategorySelected(next)
                else gamepadInputHandler.cancelRepeat()
            }
            GamepadAction.NAVIGATE_RIGHT -> {
                if (state.isInSubItem) { gamepadInputHandler.cancelRepeat(); return }
                val max  = (state.categories.size - 1).coerceAtLeast(0)
                val next = (state.selectedCategoryIndex + 1).coerceAtMost(max)
                if (next != state.selectedCategoryIndex) onCategorySelected(next)
                else gamepadInputHandler.cancelRepeat()
            }
            GamepadAction.SELECT     -> onItemSelected(state.selectedItemIndex)
            GamepadAction.BACK       -> {
                menuSound.play(MenuSound.BACK)
                // One level up, or the App Drawer when there is no level left to leave.
                if (!backOutOfDrill(state)) onOpenAppDrawer()
            }
            GamepadAction.OPEN_CONTEXT_MENU -> {
                // Y / Triangle — open context menu for whichever item type has focus
                val item = state.currentItems.getOrNull(state.selectedItemIndex)
                when {
                    item != null && openMusicContextMenu(item) -> Unit
                    item != null && openVideoContextMenu(item) -> Unit
                    item != null && openPhotoContextMenu(item) -> Unit
                    item?.gameId != null -> openGameContextMenu(item)
                    item?.collectionId != null && item.type == XMBItemType.COLLECTION -> openCollectionRowContextMenu(item.collectionId)
                    item?.type == XMBItemType.ALL_GAMES -> openAllGamesContextMenu()
                    item?.platformId != null -> openPlatformContextMenu(item.platformId)
                    item?.packageName != null -> openAppContextMenu(item)
                }
            }
            // Start button no longer restarts / shows the boot screen.
            GamepadAction.HOME          -> Unit
            // Cycle the sort order of the current list (PSP-style). Whichever face button
            // the user's X/Y layout assigns to sort dispatches this.
            GamepadAction.CHANGE_SORT -> cycleSort()
            GamepadAction.OPEN_CONTEXT_MENU,
            GamepadAction.CHANGE_SORT,
            GamepadAction.PREV_CATEGORY,
            GamepadAction.NEXT_CATEGORY -> Unit
        }
    }

    // ── Context menu ──────────────────────────────────────────────────────────

    private fun openPlatformContextMenu(platformId: String) {
        val card = enabledCards.firstOrNull { it.platformId == platformId } ?: return
        val isAndroid = platformId == ANDROID_PLATFORM_ID
        val items = buildList {
            // Android libraries pick installed apps; consoles scan ROM folders.
            if (isAndroid) add(XMBContextMenuItem("find_games", "Find Games"))
            else           add(XMBContextMenuItem("scan_roms",  "Scan This Console"))
            // The Windows card is import-driven — surface its Import PC Games section here too.
            if (platformId == "windows") add(XMBContextMenuItem("import_pc_games", "Import PC Games"))
            add(XMBContextMenuItem("update_metadata",        "Update Metadata"))
            add(XMBContextMenuItem("scrape_missing_artwork", "Scrape Missing Artwork"))
            // Icon display for THIS console only. Games on other Memory Cards are untouched;
            // "Use Global Setting" here clears the console's override.
            add(XMBContextMenuItem("icon_display_platform", "Icon Display (${platformIconDisplayLabel(platformId)})"))
            if (card.pinned) add(XMBContextMenuItem("unpin", "Unpin"))
            else             add(XMBContextMenuItem("pin",   "Pin To Top"))
            add(XMBContextMenuItem("library_manager",  "Open in Library Manager"))
            add(XMBContextMenuItem("hide",             "Hide From Games"))
            // The Windows Memory Card is managed by the PC import system and cannot be removed.
            if (platformId != "windows") add(XMBContextMenuItem("remove", "Remove Memory Card", isDestructive = true))
        }

        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(
                title      = card.displayName,
                items      = items,
                platformId = platformId,
            )
        )}
    }

    // The "All Games" card isn't a real Memory Card, so it gets its own slim menu.
    private fun openAllGamesContextMenu() {
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(
                title      = "All Games",
                items      = listOf(
                    // Scanning (missing-ROM pass, full re-scan) lives in the Library settings.
                    XMBContextMenuItem("library_manager", "Manage Library"),
                    XMBContextMenuItem("import_pc_games", "Import PC Games"),
                    XMBContextMenuItem("icon_display_global", "Icon Display (${it.iconDisplayMode.label})"),
                ),
                isAllGames = true,
            )
        )}
    }

    // The label shown on a console's Icon Display row: its own override when it has one,
    // otherwise the global mode it is currently following.
    private fun platformIconDisplayLabel(platformId: String): String {
        val state = _uiState.value
        val override = state.iconDisplayModeByPlatform[platformId]
        return override?.label ?: "Global: ${state.iconDisplayMode.label}"
    }

    // Second-level menu: the icon display mode for ONE console. "Use Global Setting" clears the
    // override so the card follows the global mode again; per-game overrides still win.
    private fun openPlatformIconDisplayPickerMenu(platformId: String) {
        val state = _uiState.value
        val override = state.iconDisplayModeByPlatform[platformId]
        val items = buildList {
            add(XMBContextMenuItem(
                id      = "picondisp_default",
                label   = "Use Global Setting (${state.iconDisplayMode.label})",
                checked = override == null,
            ))
            IconDisplayMode.entries.forEach { mode ->
                add(XMBContextMenuItem("picondisp_${mode.name}", mode.label, checked = override == mode))
            }
        }
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(
                title      = "Icon Display",
                items      = items,
                platformId = platformId,   // routes selection through the platform handler branch
            )
        )}
    }

    // Second-level menu: the GLOBAL icon display mode (mirrors Artwork Settings ▸ Game Icon
    // Display). Per-game and per-console overrides keep winning; everything else follows live.
    private fun openGlobalIconDisplayPickerMenu() {
        val current = _uiState.value.iconDisplayMode
        val items = IconDisplayMode.entries.map { mode ->
            XMBContextMenuItem("gicondisp_${mode.name}", mode.label, checked = mode == current)
        }
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(
                title      = "Icon Display",
                items      = items,
                isAllGames = true,   // routes selection through the All Games handler branch
            )
        )}
    }


    private fun openGameContextMenu(item: XMBItem) {
        val gameId = item.gameId
        if (gameId == null) {
            openGameContextMenuCore(item, discCount = 0)
            return
        }
        // Whether the game belongs to a multi-disc set is a DB read, so the "Choose Disc" entry
        // (and only that) is decided asynchronously — the rest of the menu builds unchanged.
        viewModelScope.launch {
            val discCount = runCatching {
                val game = gameRepository.getById(gameId)
                game?.discSetKey?.let { gameRepository.getDiscSetMembers(it).size } ?: 0
            }.getOrDefault(0)
            openGameContextMenuCore(item, discCount)
        }
    }

    private fun openGameContextMenuCore(item: XMBItem, discCount: Int) {
        val inCollection = _uiState.value.selectedCollectionId != null
        val currentCat = currentCategory()
        val inGamingCategory = currentCat?.isGamingCategory == true
        val inMissingBucket = _uiState.value.selectedPlatformId == MISSING_PLATFORM_ID

        val items = buildList {
            // The explicit path to the edit surface, essential when direct launch makes
            // confirm skip straight into the game. Launch/title/note/scrape actions all live
            // in Game Detail — the menu stays navigational.
            add(XMBContextMenuItem("game_details", "View Game Details"))
            // Multi-disc sets: pick which disc to boot — the only way to reach a non-primary
            // disc when direct launch skips Game Detail's picker. Launches the chosen disc.
            if (discCount > 1) add(XMBContextMenuItem("choose_disc", "Choose Disc"))
            if (item.platformId == WINDOWS_PLATFORM_ID) {
                // Writes this game's .pfpgame file so a fresh install can bring it back with its
                // artwork (C18 task X.7). Offered on every PC game; the exporter explains a refusal.
                add(XMBContextMenuItem("export_game", "Export Game"))
            }
            // No "Edit App Details" here: package-backed GAME entries (PC shortcuts, Android
            // gaming apps) are games — art/title/note editing lives in Game Detail and the
            // game rows below, never the slim standard-app editor.
            add(XMBContextMenuItem(
                id    = if (item.isFavorite) "unfavorite" else "favorite",
                label = if (item.isFavorite) "Remove from Favorites" else "Add to Favorites",
            ))
            add(XMBContextMenuItem("add_to_collection", "Add to Collection"))
            // Only offer removal when viewing the game from inside a collection.
            if (inCollection) add(XMBContextMenuItem("remove_from_collection", "Remove from Collection"))
            add(XMBContextMenuItem("manage_collections", "Manage Collections"))

            // Gaming category options. Games in the Main Game category can only be COPIED into
            // another category (never moved out or removed); custom gaming categories allow
            // move / remove / pin. Move/Add only appear when a real destination exists — a
            // custom gaming category other than the current one (Main Game is never a target).
            if (inGamingCategory) {
                val hasOtherCustomCategory = _uiState.value.categories.any {
                    it.isGamingCategory && it.id != BuiltInCategory.GAMES && it.id != currentCat.id
                }
                if (currentCat.id == BuiltInCategory.GAMES) {
                    if (hasOtherCustomCategory) add(XMBContextMenuItem("add_category", "Add to Category"))
                } else {
                    if (hasOtherCustomCategory) add(XMBContextMenuItem("move_category", "Move to Category"))
                    add(XMBContextMenuItem("remove_category", "Remove from Category"))
                    val pinned = item.subtitle == "Pinned"
                    add(XMBContextMenuItem(
                        if (pinned) "unpin_category" else "pin_category",
                        if (pinned) "Unpin" else "Pin",
                    ))
                }
            }

            // Emulator choice only applies to ROM-backed games; package-backed gaming apps
            // launch via their package/shortcut handle.
            if (!item.isAndroidApp) add(XMBContextMenuItem("change_emulator", "Change Emulator"))
            add(XMBContextMenuItem("icon_display", "Icon Display"))
            add(XMBContextMenuItem("file_location",    "View File Location"))
            // Per-location hide for the spot this game is shown in (recoverable in Hidden Items).
            currentHideLocation()?.let { (_, _, label) -> add(XMBContextMenuItem("hide_here", "Hide from $label")) }
            // Android-library apps are user-curated, so let the user remove one like any game,
            // or demote it to a standard app without losing its art/collections.
            if (inMissingBucket) {
                // The plan's explicit user delete, and the only destructive action anywhere in the
                // missing-ROM flow. Mechanically identical to "Remove from Library" (delete row,
                // file untouched), but labelled for what it means here: this bucket is the entry's
                // last visible trace, so removing it ends the line rather than dropping it from one
                // view. Everything else is recoverable by putting the file back.
                add(XMBContextMenuItem("remove_missing", "Remove permanently", isDestructive = true))
            } else if (item.platformId == ANDROID_PLATFORM_ID && item.packageName != null && !inCollection) {
                add(XMBContextMenuItem("unmark_game", "Unmark as Game"))
                add(XMBContextMenuItem("remove_app", "Remove from Library", isDestructive = true))
            } else if (!inCollection) {
                // Every other game gets full delete too (confirmed first). Deleting a scanned ROM
                // entry leaves the file untouched — the next scan re-discovers it.
                add(XMBContextMenuItem("remove_game", "Remove from Library", isDestructive = true))
            }
        }

        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(
                title       = item.title,
                items       = items,
                gameId      = item.gameId,
                packageName = item.packageName,
                shortcutId  = item.shortcutId,
                launchIntentUri = item.launchIntentUri,
                categoryContext = if (inGamingCategory) currentCat.id else null,
            )
        )}
    }

    // Second-level menu: the collections a game can be added to (checkmarks show current
    // membership), plus "Create New Collection". Opened from the game options menu. The menu
    // stays open while toggling so the user can add to several collections at once.
    private fun openCollectionPicker(gameId: Long, selectIndex: Int = 0) {
        viewModelScope.launch {
            val collections = collectionRepository.getAll()
            val memberOf = collectionRepository.getCollectionIdsForGame(gameId).toSet()
            val items = buildList {
                collections.forEach { c ->
                    add(XMBContextMenuItem(
                        id      = "col_${c.id}",
                        label   = c.name,
                        checked = c.id in memberOf,
                    ))
                }
                add(XMBContextMenuItem("col_new", "Create New Collection"))
            }
            _uiState.update { it.copy(
                activeContextMenu = XMBContextMenu(
                    title            = "Add to Collection",
                    items            = items,
                    selectedIndex    = selectIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
                    gameId           = gameId,
                    collectionGameId = gameId,
                )
            )}
        }
    }

    private fun openAppContextMenu(item: XMBItem, categoryIdOverride: String? = null) {
        val pkg = item.packageName ?: return
        val categoryId = categoryIdOverride ?: currentCategory()?.id
        val items = buildList {
            add(XMBContextMenuItem("launch",   "Launch"))
            add(XMBContextMenuItem("edit_app", "Edit App Details"))
            // Promotes the app into the Android Memory Card as a real game.
            add(XMBContextMenuItem("mark_game", "Mark as Game"))
            // Shortcut actions — these materialize a launch shortcut (a games-table row that
            // references the app by package) so it can live in Favorites / Collections without
            // duplicating the app's metadata. Works for every Android app, GameHub included.
            add(XMBContextMenuItem("favorite",          "Add to Favorites"))
            add(XMBContextMenuItem("add_to_collection", "Add to Collection"))
            add(XMBContextMenuItem("move",     "Move To Category"))
            add(XMBContextMenuItem("add",      "Add To Category"))
            if (categoryId != null) add(XMBContextMenuItem("remove", "Remove From Category"))
            if (categoryId != null) add(XMBContextMenuItem("pin",    "Pin To Category"))
            // Per-location hide (recoverable in Settings ▸ Hidden Items) + global hide-everywhere.
            if (categoryId != null) add(XMBContextMenuItem("hide_from_category", "Hide from ${categoryDisplayName(categoryId)}"))
            add(XMBContextMenuItem("hide_everywhere", "Hide Everywhere"))
            add(XMBContextMenuItem("rename",   "Rename Shortcut"))
        }
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(
                title           = item.title,
                items           = items,
                gameId          = item.gameId,
                packageName     = pkg,
                categoryContext = categoryId,
            )
        )}
    }

    // Options menu for a collection row (long-press / △), in any collection-rendering category.
    private fun openCollectionRowContextMenu(collectionId: Long) {
        val collection = _uiState.value.collections.firstOrNull { it.id == collectionId } ?: return
        // Move is only meaningful when there's another category of the same kind to move into
        // (game collections move between gaming categories, app collections between app ones).
        val hasOtherCategory = collectionMoveTargets(collection.categoryId).isNotEmpty()
        val items = buildList {
            add(XMBContextMenuItem("open_collection",   "Open"))
            add(XMBContextMenuItem("rename_collection", "Rename Collection"))
            if (hasOtherCategory) add(XMBContextMenuItem("move_collection_category", "Move to Category"))
            add(XMBContextMenuItem(
                if (collection.isPinned) "unpin_collection" else "pin_collection",
                if (collection.isPinned) "Unpin" else "Pin",
            ))
            add(XMBContextMenuItem("manage_collections", "Manage Collections"))
            add(XMBContextMenuItem("delete_collection",  "Delete Collection", isDestructive = true))
        }
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(
                title           = collection.name,
                items           = items,
                collectionRowId = collectionId,
            )
        )}
    }

    // Second-level menu: pick a destination gaming category for moving a collection. Collections
    // belong to exactly one category, so this reassigns categoryId (the source of truth).
    /** Valid destinations for moving a collection out of [fromCategoryId]: categories of the same
     *  kind (gaming ↔ gaming, app ↔ app) that render collections — an app collection can never
     *  land in a gaming category or a media section, and vice versa. */
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
            activeContextMenu = XMBContextMenu(
                title           = "Move Collection To",
                items           = items,
                collectionRowId = collectionId,
            )
        )}
    }

    // Second-level menu: pick a destination category for Move / Add.
    private fun openCategoryPicker(pkg: String, fromCategory: String?, action: String) {
        val items = _uiState.value.categories.map { cat ->
            XMBContextMenuItem("pick_${cat.id}", cat.name)
        }
        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(
                title            = if (action == "move") "Move To…" else "Add To…",
                items            = items,
                packageName      = pkg,
                categoryContext  = fromCategory,
                pendingAppAction = action,
            )
        )}
    }

    private fun shiftContextMenu(delta: Int) {
        val menu = _uiState.value.activeContextMenu ?: return
        // An empty menu can flash in during a rebuild — no-op instead of coercing into
        // the empty range 0..-1 (IllegalArgumentException).
        if (menu.items.isEmpty()) return
        val next = (menu.selectedIndex + delta).coerceIn(0, menu.items.size - 1)
        _uiState.update { it.copy(activeContextMenu = menu.copy(selectedIndex = next)) }
    }

    private fun activateContextMenuItem() {
        val menu   = _uiState.value.activeContextMenu ?: return
        val itemId = menu.items.getOrNull(menu.selectedIndex)?.id ?: return

        // ── Gaming category picker submenu — move or add game to another category ──
        if (itemId.startsWith("cat_") && menu.gameId != null && menu.categoryContext != null && menu.pendingAppAction != null) {
            val gameId = menu.gameId
            val fromCategory = menu.categoryContext
            val toCategory = itemId.removePrefix("cat_")
            val action = menu.pendingAppAction
            closeContextMenu()
            // Reload AFTER the write completes — reloading synchronously would read stale
            // junction rows and leave the moved game visible in the source category.
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

        // ── Collection picker submenu — handled before closing so toggles stay in place ──
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
                        // Re-open so the checkmark reflects the new membership.
                        openCollectionPicker(gameId, keepIndex)
                    }
                }
            }
            return
        }

        // ── Video "Add to Playlist" submenu — handled before closing so toggles stay in place ──
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
                        openVideoPlaylistPicker(videoId, keepIndex)  // re-open so the checkmark updates
                    }
                }
            }
            return
        }

        // ── Playlist picker submenu — handled before closing so toggles stay in place ──
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
                        // Re-open so the checkmark reflects the new membership.
                        openPlaylistPicker(trackId, keepIndex)
                    }
                }
            }
            return
        }

        closeContextMenu()

        // ── Video file / library / playlist row options menus ───────────────────
        if (menu.videoFileId != null) {
            handleVideoFileAction(menu.videoFileId, itemId)
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

        // ── Playlist row options menu ──────────────────────────────────────────
        if (menu.playlistId != null && menu.musicTrackId == null) {
            handlePlaylistRowAction(menu.playlistId, itemId)
            return
        }

        // ── Collection row options menu (and the Move-to-Category submenu) ──────
        if (menu.collectionRowId != null) {
            val collectionId = menu.collectionRowId
            when {
                // Destination chosen in the Move submenu — reassign the collection's category.
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
                itemId == "manage_collections" -> _uiState.update { it.copy(activeSettingsScreen = "settings_collections") }
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
                // Icon display picked for this console ("default" clears the console override so
                // the card follows the global setting again).
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
                // Emulator chosen from the Change Emulator submenu ("default" clears the override).
                val gid = menu.gameId
                val choice = itemId.removePrefix("emu_pick_")
                appAction {
                    gameRepository.setPreferredEmulator(gid, choice.takeIf { it != "default" })
                }
            } else if (itemId.startsWith("icondisp_")) {
                // Icon display mode picked from the Icon Display submenu ("default" clears the
                // per-game override so the game follows the global setting again).
                val gid = menu.gameId
                val choice = itemId.removePrefix("icondisp_")
                appAction {
                    gameRepository.setIconDisplayMode(gid, IconDisplayMode.fromName(choice)?.name)
                }
            } else if (itemId.startsWith("disc_pick_")) {
                // Disc chosen from the "Choose Disc" submenu — only remember the preferred disc.
                // Launching remains an explicit confirm action from the XMB entity.
                val discId = itemId.removePrefix("disc_pick_").toLongOrNull()
                if (discId != null) {
                    menuSound.play(MenuSound.SELECT)
                    appAction { gameRepository.setPreferredDisc(menu.gameId, discId) }
                }
            } else when (itemId) {
                // Always opens the Game Detail screen (no auto-launch) — the edit surface for
                // artwork, title, notes, emulator when direct launch is the confirm behavior.
                "game_details"           -> _uiState.update {
                    it.copy(activeGameId = menu.gameId, activeGameAutoLaunch = false)
                }
                "choose_disc"             -> openDiscPickerMenu(menu.gameId)
                "export_game"            -> exportGameFromMenu(menu.gameId)
                "edit_app"               -> openAppDetail(menu.gameId, menu.packageName ?: return)
                "favorite"               -> toggleGameFavorite(menu.gameId, true)
                "unfavorite"             -> toggleGameFavorite(menu.gameId, false)
                "add_to_collection"      -> openCollectionPicker(menu.gameId)
                "remove_from_collection" -> {
                    val gid = menu.gameId   // local val so it smart-casts inside the lambda
                    _uiState.value.selectedCollectionId?.let { cid ->
                        appAction { collectionRepository.removeGame(cid, gid) }
                    }
                }
                "manage_collections"     -> _uiState.update { it.copy(activeSettingsScreen = "settings_collections") }
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
                // Two-step delete: a confirm menu first, matching the Game Detail page's guard.
                "remove_game"            -> _uiState.update { it.copy(activeContextMenu = XMBContextMenu(
                    title  = "Remove \"${menu.title}\" from Library?",
                    items  = listOf(
                        XMBContextMenuItem("confirm_remove_game", "Remove", isDestructive = true),
                        XMBContextMenuItem("cancel_remove_game",  "Cancel"),
                    ),
                    gameId = menu.gameId,
                ))}
                "confirm_remove_game"    -> {
                    val gid = menu.gameId
                    appAction { removeGameFromLibrary(gid) }
                }
                "cancel_remove_game"     -> Unit   // menu already closed
                // Same two-step confirm as remove_game, with copy that states the consequence: the
                // file is already gone, so there is no "put it back" once the entry goes too.
                "remove_missing"         -> _uiState.update { it.copy(activeContextMenu = XMBContextMenu(
                    title  = "Permanently remove \"${menu.title}\"?",
                    items  = listOf(
                        XMBContextMenuItem("confirm_remove_missing", "Remove permanently", isDestructive = true),
                        XMBContextMenuItem("cancel_remove_missing",  "Cancel"),
                    ),
                    gameId = menu.gameId,
                ))}
                "confirm_remove_missing" -> {
                    val gid = menu.gameId
                    // Reuses the standard removal: deletes the row, recounts the card; the file is
                    // untouched and a later scan re-discovers it.
                    appAction { removeGameFromLibrary(gid) }
                }
                "cancel_remove_missing"  -> Unit   // menu already closed
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
                // Demote an Android-card game to a standard app: the row survives as a decoration
                // shortcut (art/favorites/collections intact) but leaves the card and All Games.
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
                    "launch"    -> appCategoryRepository.launch(pkg)
                    "edit_app"  -> openAppDetail(menu.gameId, pkg)
                    // Promote a standard app to the Android card as a real game (reuses any
                    // existing decoration row so art/favorites/collections carry over).
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
                    "hide_everywhere" -> appAction { appCategoryRepository.setHidden(pkg, true) }
                    "rename"    -> _uiState.update { it.copy(renameAppTarget = pkg, renameAppCurrent = menu.title) }
                }
            }
        }
    }

    private fun appAction(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    // Submenu listing every installed emulator that supports the game's platform. Selecting a row
    // dispatches "emu_pick_<profileId>" (or "emu_pick_default" to clear the per-game override).
    // Second-level menu: how this game's XMB tile is drawn. Checkmark shows the current choice;
    // "Use Global Setting" clears the per-game override.
    private fun openIconDisplayPickerMenu(gameId: Long) {
        viewModelScope.launch {
            val game = gameRepository.getById(gameId) ?: return@launch
            val override = IconDisplayMode.fromName(game.iconDisplayMode)
            // What the game falls back to: its console's override, else the global mode.
            val state = _uiState.value
            val inherited = state.iconDisplayModeByPlatform[game.platformId] ?: state.iconDisplayMode
            val items = buildList {
                add(XMBContextMenuItem(
                    id      = "icondisp_default",
                    label   = "Use Default (${inherited.label})",
                    checked = override == null,
                ))
                IconDisplayMode.entries.forEach { mode ->
                    add(XMBContextMenuItem("icondisp_${mode.name}", mode.label, checked = override == mode))
                }
            }
            _uiState.update { it.copy(activeContextMenu = XMBContextMenu(
                title  = "Icon Display",
                items  = items,
                gameId = gameId,
            ))}
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
            _uiState.update { it.copy(activeContextMenu = XMBContextMenu(
                title  = "Choose Emulator",
                items  = items,
                gameId = gameId,
            ))}
        }
    }

    // Second-level menu: the discs of a multi-disc set. Picking one boots that disc directly
    // (direct-launch-consistent — the Game Detail picker remains the select-then-play path). The
    // primary row is marked, matching the detail page's default selection.
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
                        id      = "disc_pick_${member.id}",
                        label   = member.discNumber?.let { "Disc $it" } ?: "Playlist",
                        checked = member.id == preferredDiscId,
                    )
                }
            _uiState.update { it.copy(activeContextMenu = XMBContextMenu(
                title  = "Choose Disc",
                items  = items,
                gameId = gameId,
            ))}
        }
    }

    // Deletes a game row (never the file). "Remove from Library" is a plain removal: the file
    // stays on disk and is re-discovered by the next scan of any kind.
    private suspend fun removeGameFromLibrary(gameId: Long) {
        val game = gameRepository.getById(gameId) ?: return
        gameRepository.delete(gameId)
        memoryCardRepository.recountGames(game.platformId)
        loadItemsForCategory(currentCategory())
    }

    // ── App rename dialog ─────────────────────────────────────────────────────

    fun onConfirmAppRename(newLabel: String) {
        val pkg = _uiState.value.renameAppTarget ?: return
        viewModelScope.launch {
            // Blank reverts to the real app label.
            appCategoryRepository.rename(pkg, newLabel.ifBlank { null })
            _uiState.update { it.copy(renameAppTarget = null, renameAppCurrent = null) }
        }
    }

    fun onCancelAppRename() {
        _uiState.update { it.copy(renameAppTarget = null, renameAppCurrent = null) }
    }

    // ── Create-collection dialog ───────────────────────────────────────────────

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
        // Game Edit Title / Edit Note targets: blank input clears the override/note.
        if (dialog.editTitleGameId != null) {
            viewModelScope.launch {
                gameRepository.updateUserTitleOverride(dialog.editTitleGameId, name.trim().ifBlank { null })
                // The new title re-sorts the list: follow the renamed game, not its old slot.
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
                // A collection created from a collection-rendering category (gaming, Network,
                // App Store, custom) is homed there; other contexts default to Main Game.
                val id = collectionRepository.create(name, collectionHomeCategoryId())
                dialog.forGameId?.let { collectionRepository.addGame(id, it) }
            }
            // Reflect the new/renamed collection in the XMB right away instead of waiting on the
            // reactive collection stream.
            if (categoryShowsCollections(currentCategory())) {
                loadItemsForCategory(currentCategory())
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

    /** Export Game from the XMB menu (C18 task X.7); the exporter explains any refusal. */
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


    // Folder-name/title match key, mirroring LocalSteamGameImporter.normalizeTitle.
    private fun normalizePcTitleKey(title: String): String =
        title.lowercase().filter { it.isLetterOrDigit() }

    // Called from touch interaction on the overlay
    fun onContextMenuItemActivatedAt(index: Int) {
        _uiState.update { it.copy(activeContextMenu = it.activeContextMenu?.copy(selectedIndex = index)) }
        activateContextMenuItem()
    }

    fun closeContextMenu() {
        _uiState.update { it.copy(activeContextMenu = null) }
    }

    // ── Installed-app picker ────────────────────────────────────────────────────

    // Opens the picker with current membership pre-checked (both `selected` and
    // `initialSelected`), so Apply diffs against the state the picker opened with.
    private fun openAppPicker(target: AppPickerTarget, title: String) {
        viewModelScope.launch {
            val installed = appCategoryRepository.allInstalledApps()
            // Icons resolve once, here, on IO — never per-tile in composition.
            val entries = installed.map {
                AppPickerEntry(packageName = it.packageName, label = it.label, icon = it.icon)
            }   // already sorted by label
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

    // Touch: a tap on a tile parks the (hidden) cursor there and toggles it.
    fun onAppPickerTileTapped(index: Int) {
        markTouchInput()
        // While the confirmation modal is up, the grid behind the scrim is inert.
        if (_uiState.value.appPicker?.confirmingRemovals == true) return
        _uiState.update {
            val picker = it.appPicker ?: return@update it
            val visible = picker.visibleApps()
            val app = visible.getOrNull(index) ?: return@update it
            it.copy(appPicker = picker.copy(focusedIndex = index, usingTouch = true)
                .toggle(app.packageName))
        }
    }

    // Touch: finger-scroll settled (or drag started) on a tile — park the hidden cursor there.
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

    // Touch: the header's ‹ / title.
    fun onAppPickerHeaderBack() {
        markTouchInput()
        handleAppPickerBack()
    }

    // Touch: the confirmation panel's Remove / Cancel rows.
    fun onAppPickerConfirmRemoval() {
        markTouchInput()
        commitAppPicker()
    }

    fun onAppPickerCancelRemoval() {
        markTouchInput()
        cancelConfirm()
    }

    // Touch: an Apply affordance (footer taps); same two-pass path as gamepad HOME.
    fun onAppPickerApply() {
        markTouchInput()
        requestApplyAppPicker()
    }

    // Touch: toggling the search field on/off. Clearing the query on close matches the drawer.
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
            // Filtering never moves the cursor by itself, but a shrunken list must not strand it.
            it.copy(appPicker = picker.copy(query = query).clampFocus())
        }
    }

    private fun closeAppPickerSearch(picker: AppPickerState): AppPickerState =
        picker.copy(searchActive = false, query = "")

    fun onAppPickerSearchDone() {
        // ImeAction.Search — keep the field open; the query is live. Nothing to commit.
    }

    private fun moveAppPicker(action: GamepadAction) {
        _uiState.update { state ->
            val picker = state.appPicker ?: return@update state
            // While the removal-confirmation modal is up, the dpad belongs to the modal's
            // Cancel/Remove cursor — the grid behind the scrim must not move.
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

    // Apply (HOME) — a full sync: adds newly-checked apps, removes newly-unchecked ones.
    // Removals never run silently: the first pass raises the confirmation panel; the second
    // (confirmed) pass commits.
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
        // A committed Apply — the point of no return, distinct from SELECT's descent into the
        // picker. closeAppPicker plays nothing, so this is a single chime.
        menuSound.play(MenuSound.CONFIRM)
        val target = picker.target
        closeAppPicker()

        viewModelScope.launch {
            when (target) {
                is AppPickerTarget.AndroidGames -> {
                    if (adds.isNotEmpty()) importAndroidGames(target.platformId, adds)
                    if (removals.isNotEmpty()) removeAndroidGames(target.platformId, removals)
                    // One recount after the whole batch, whichever half ran.
                    memoryCardRepository.recountGames(target.platformId)
                }
                is AppPickerTarget.CategoryShortcuts -> {
                    adds.forEach { pkg -> appCategoryRepository.addToCategory(pkg, target.categoryId) }
                    removals.forEach { pkg -> appCategoryRepository.removeFromCategory(pkg, target.categoryId) }
                }
            }
        }
    }

    // Reuses the exact path the Library Manager's Remove row uses: getAppEntry then delete.
    private suspend fun removeAndroidGames(platformId: String, packages: Set<String>) {
        packages.forEach { pkg ->
            val entry = gameRepository.getAppEntry(pkg) ?: return@forEach
            if (entry.platformId != platformId) return@forEach
            gameRepository.delete(entry.id)
        }
        Timber.i("Android library removal: ${packages.size} app(s) removed from $platformId")
    }

    // BACK / ‹ unwinds one layer at a time: search → confirmation → picker. Backing out of a
    // dirty picker must never touch the library.
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

    // ── Game picker (for gaming categories) ────────────────────────────────────

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
            // Collections are placed by categoryId (one category each), not the junction table.
            selectedCollectionIds.forEach { collectionId ->
                collectionRepository.setCategory(collectionId, categoryId)
            }
            // Refresh the current category display
            val category = _uiState.value.categories.getOrNull(_uiState.value.selectedCategoryIndex)
            if (category?.id == categoryId) {
                loadItemsForCategory(category)
            }
        }
    }

    // Shows a menu of other gaming categories for moving/adding a game. Main Game is never a
    // destination for an individual game — every game already lives there via its platform, so
    // moving a game "to Main Game" is redundant (and would only leave a stray junction row).
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
            activeContextMenu = XMBContextMenu(
                title       = if (action == "move") "Move Game To" else "Add Game To",
                items       = items,
                gameId      = gameId,
                categoryContext = fromCategoryId,
                pendingAppAction = action,  // reuse this field to store the action type
            )
        )}
    }

    // Adds the selected apps as launchable Game entries under an Android Memory Card. Stores
    // the package name (launch reference) and label; the icon is loaded by package at render
    // time. Skips apps already present so re-running the picker is safe.
    private suspend fun importAndroidGames(platformId: String, packages: Set<String>) {
        val labels = appCategoryRepository.allInstalledApps().associateBy { it.packageName }

        packages.forEach { pkg ->
            // One row per app. If a shortcut row already exists (from artwork/favorites), promote
            // it into the Android library instead of creating a duplicate; otherwise add a new row.
            val existing = gameRepository.getAppEntry(pkg)
            when {
                existing == null -> gameRepository.upsert(
                    com.psplauncher.core.domain.model.Game(
                        title         = labels[pkg]?.label ?: pkg,
                        platformId    = platformId,
                        packageName   = pkg,
                        isManualEntry = true,
                        // Adding to the Android library is the user saying "this app is a game" —
                        // it counts in All Games and can join gaming categories/collections.
                        contentType   = com.psplauncher.core.domain.model.GameContentType.GAME,
                    )
                )
                // A shortcut/decoration row exists — promote it into the library as a game,
                // keeping its artwork, favorites and collection memberships.
                existing.platformId != platformId ||
                    existing.contentType != com.psplauncher.core.domain.model.GameContentType.GAME ->
                    gameRepository.upsert(existing.copy(
                        platformId  = platformId,
                        contentType = com.psplauncher.core.domain.model.GameContentType.GAME,
                    ))
                // else: already in the library — nothing to do.
            }
        }
        memoryCardRepository.recountGames(platformId)
        Timber.i("Android library import: ${packages.size} app(s) selected for $platformId")
    }

    // ── Platform actions ──────────────────────────────────────────────────────

    fun onPlatformLongPress(categoryIndex: Int) {
        _uiState.value.currentItems.getOrNull(categoryIndex)?.platformId?.let(::openPlatformContextMenu)
    }

    // Scans only this Memory Card's directory for only its supported extensions, assigning
    // every match to its platform. A PSP card can never pull in another console's ROMs.
    private fun scanCard(platformId: String) {
        viewModelScope.launch {
            val card = memoryCardRepository.getById(platformId) ?: return@launch
            val taskId = "scan_$platformId"

            // The Windows card runs the full PC pass (pin sweep incl. pins never added, the
            // <windows>/import exports, emu folder reconcile) — extension scanning means nothing
            // to it, and "Scan This Console" must behave exactly like the Library Manager action.
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

    // Scans this card's games for missing/broken primary artwork and scrapes only those —
    // valid artwork is never re-downloaded or overwritten.
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

    // Text-only metadata pass over the card's games — artwork files and columns are untouched.
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

    // ── Game actions ──────────────────────────────────────────────────────────

    // Silent by decision: favouriting is an operational toggle, not an event worth sonifying.
    private fun toggleGameFavorite(gameId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            gameRepository.setFavorite(gameId, isFavorite)
        }
    }

    // ── Background task management ────────────────────────────────────────────

    // Background work is reported through the Android notification bar. We keep a
    // tiny in-memory label map so progress/complete updates can re-title the same
    // notification without the caller having to re-supply the label each time.
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

    // ── Category / platform selection ─────────────────────────────────────────

    fun onCategorySelected(index: Int) {
        if (index != _uiState.value.selectedCategoryIndex) menuSound.play(MenuSound.SYSTEM_BROWSE)
        val prev = _uiState.value
        // Remember each category's cursor so moving away and back restores your spot instead of
        // snapping to the first item. Left/Right is locked while drilled in, so the saved index is
        // always a root-level list position for that category.
        prev.categories.getOrNull(prev.selectedCategoryIndex)?.id?.let { categoryCursor[it] = prev.selectedItemIndex }
        val category = prev.categories.getOrNull(index)
        val restore = category?.id?.let { categoryCursor[it] } ?: 0
        // activeAppDrawerFilter is cleared as an invariant: landing on a category always shows the
        // plain XMB (the drawer can't normally be open here, but this keeps the contextual button
        // state correct no matter which path selected the category).
        _uiState.update { it.copy(selectedCategoryIndex = index, selectedItemIndex = restore, selectedPlatformId = null, selectedCollectionId = null, musicNav = MusicNav.Root, videoNav = VideoNav.Root, photoNav = PhotoNav.Root, activeAppDrawerFilter = null) }
        tintWaveForCategory(category)
        loadItemsForCategory(category)
    }

    /** Touch tap on a caticon. Unlike the shared [onCategorySelected] (also driven by gamepad ◀ ▶),
     *  this marks the input as touch so the contextual button returns in Auto mode, and it is a
     *  no-op while drilled into a sub-item — matching [stepCategory]'s lock, so a stray tap can't
     *  yank the user out of a folder. */
    fun onCategoryTapped(index: Int) {
        markTouchInput()
        val s = _uiState.value
        if (s.hasBlockingOverlay || s.isInSubItem) return
        onCategorySelected(index)
    }

    /** Touch: step the category selection by [direction] (-1 / +1) from the current one — the swipe
     *  equivalent of D-pad ◀ ▶. */
    fun stepCategory(direction: Int) {
        markTouchInput()
        val s = _uiState.value
        if (s.hasBlockingOverlay) return
        // Locked while drilled into a sub-item — the user must Back out before changing category.
        if (s.isInSubItem) return
        val next = (s.selectedCategoryIndex + direction)
            .coerceIn(0, (s.categories.size - 1).coerceAtLeast(0))
        if (next != s.selectedCategoryIndex) onCategorySelected(next)
    }

    // ── Shared item-cursor movement (D-pad + touch swipe) ─────────────────────────

    /**
     * Moves the item cursor by [delta] rows in one clamped, batched update, playing a single scroll
     * sound if it moved. Returns whether the cursor actually moved (the D-pad path uses this to
     * cancel auto-repeat at a list boundary). Shared by [dispatchGamepadAction]'s NAVIGATE_UP/DOWN
     * and the touch [stepItem], so both drive identical logic — no parallel navigation.
     */
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

    /** Touch: step the item cursor by [steps] rows (a swipe = repeated D-pad ▲▼), batched into one
     *  update so a multi-row swipe is a single recomposition. */
    fun stepItem(steps: Int) {
        markTouchInput()
        moveItemCursor(steps)
    }

    /** Touch tap on row [index]: move the cursor there, or — if it's already the selected row —
     *  activate it. Keeps touch faithful to the XMB cursor model (tap to point, tap again to open).
     *  The controller SELECT path still activates in one press via [activateSelected]. */
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

    /** Activates the currently selected item (launch / drill / open) — the shared body of the
     *  controller SELECT and a second tap on the focused row. */
    private fun activateSelected() {
        onItemSelected(_uiState.value.selectedItemIndex)
    }

    // ── Input-source tracking (drives the touch-navigation button) ────────────────

    /** Marks the last input source as touch. Public so fullscreen overlays (detail screens, the
     *  music browser) can report a touch interaction, keeping the single `lastInputWasTouch` source
     *  of truth — the same one that drives the XMB's contextual App Drawer button. Also refreshes
     *  [lastInteractionMs] so the idle context-menu hint resets. */
    fun markTouchInput() {
        lastInteractionMs = SystemClock.elapsedRealtime()
        // One write for both flags: the hints must clear on the SAME frame as the input (see
        // noteInteraction), and a second update() here would cost an extra recomposition.
        _uiState.update {
            if (it.lastInputWasTouch &&
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

    private fun markControllerInput() {
        lastInteractionMs = SystemClock.elapsedRealtime()
        _uiState.update {
            if (!it.lastInputWasTouch &&
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

    /**
     * Unwinds exactly ONE level of home-screen drill-in, and reports whether it did. False means
     * the cursor was already at a category root — there was nothing to back out of.
     *
     * The single implementation of the ladder. It was written out twice (gamepad BACK and the
     * touch [onHomeBack]) before D-pad LEFT became a third caller, and each caller wants a
     * different thing at the root: BACK opens the App Drawer, LEFT steps the category bar. So the
     * ladder returns the fact and lets the caller decide — it plays no sound, marks no input
     * source, and has no fallback of its own.
     */
    private fun backOutOfDrill(s: XMBUiState): Boolean {
        when (s.drillOutStep) {
            DrillOutStep.MUSIC -> closeMusicView()
            // Two-level video paths back out through their own list first.
            DrillOutStep.VIDEO_LIBRARY -> openVideoView(VideoNav.Libraries)
            DrillOutStep.VIDEO_PLAYLIST -> openVideoView(VideoNav.Playlists)
            DrillOutStep.VIDEO_COLLECTION_CHILD -> openVideoView(VideoNav.Collections)
            DrillOutStep.VIDEO -> closeVideoView()
            // An album drill-in backs out via the Albums list first.
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

    /** Touch: the left-edge-swipe Back — exit an open folder, or open the app drawer at the root
     *  (mirrors the gamepad BACK behaviour on the home screen). No-op while an overlay is up. */
    fun onHomeBack() {
        markTouchInput()
        val s = _uiState.value
        if (s.hasBlockingOverlay) return
        menuSound.play(MenuSound.BACK)
        if (!backOutOfDrill(s)) onOpenAppDrawer()
    }

    // ── Item selection ────────────────────────────────────────────────────────

    fun onItemSelected(index: Int) {
        // Touch guard: XMB rows must never activate while any overlay is up (the gamepad path is
        // guarded in the dispatcher; this closes the same hole for taps that slip through an
        // overlay's non-interactive areas).
        if (_uiState.value.hasBlockingOverlay) return
        _uiState.update { it.copy(selectedItemIndex = index) }
        val category = _uiState.value.categories.getOrNull(_uiState.value.selectedCategoryIndex)
        val item     = _uiState.value.currentItems.getOrNull(index)

        // Music rows are handled together (static items, the All Music card, playlists, tracks,
        // and the various add/setup rows), each owning its sound and returning early.
        if (category?.id == BuiltInCategory.MUSIC && item != null && handleMusicSelection(item)) return

        // Video rows (static items, library cards, video files, app rows) are handled together.
        if (category?.id == BuiltInCategory.VIDEO && item != null && handleVideoSelection(item)) return

        // Photo rows (the All Photos card, Camera, Add Photo Library, Album cards, photo files).
        if (category?.id == BuiltInCategory.PHOTO && item != null && handlePhotoSelection(item)) return

        // Library rows (the reader, shelves, a shelf, a book).
        if (category?.id == BuiltInCategory.LIBRARY && item != null && handleBooksSelection(item)) return

        // Sound: launch for items that boot something immediately; select for opening a folder,
        // detail, picker, or settings; silent for non-selectable placeholder rows.
        val silentRow = item?.id in setOf(NO_GAMES_ITEM_ID, EMPTY_COLLECTION_ITEM_ID, EMPTY_CATEGORY_ITEM_ID)
        // A real game opens the Game Detail page — which only boots the game immediately when
        // direct launch is on; without it, confirm just opens the detail "menu" (select).
        val opensGameDetail = item?.gameId != null && item.isRealGame
        val launches = if (opensGameDetail) {
            _uiState.value.directLaunch
        } else {
            item?.launchIntentUri != null ||
                (item?.shortcutId != null && item.packageName != null) ||
                item?.packageName != null
        }
        // A real game booting immediately is the only case GameBoot covers. A game boot is never
        // scored by the menu's launch sound: GameBoot owns sfx_launch when it is on, and with
        // GameBoot off the launch is silent by decision — a silent launch, not the sfx wearing a
        // different hat. Plain app launches keep their sound (funnelling those is a separate
        // refactor, explicitly out of scope).
        val launchesGame = opensGameDetail && _uiState.value.directLaunch
        val event = when {
            silentRow -> null
            launchesGame -> null
            launches -> MenuSound.LAUNCH
            else -> MenuSound.SELECT
        }
        event?.let { menuSound.play(it) }

        // Empty-state rows
        when (item?.id) {
            NO_CONSOLES_ITEM_ID -> {
                _uiState.update { it.copy(activeSettingsScreen = "settings_library") }
                return
            }
            SETUP_GAP_ITEM_ID -> {
                // B3: the setup-gap row deep-links to the screen that repairs the first gap.
                _uiState.update {
                    it.copy(activeSettingsScreen = setupState.firstGap.repairScreenId)
                }
                return
            }
            ALL_GAMES_ITEM_ID -> {
                openAllGamesFolder()
                return
            }
            FAVORITES_ITEM_ID -> {
                openFavoritesFolder()
                return
            }
            MISSING_ITEM_ID -> {
                openMissingFolder()
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
            EMPTY_CATEGORY_ITEM_ID -> return   // not selectable
        }

        // User collection folder — open it.
        if (item?.collectionId != null && item.type == XMBItemType.COLLECTION) {
            openCollectionFolder(item.collectionId)
            return
        }

        // Real games — including package/shortcut-backed gaming apps (Android/Windows cards) —
        // Open the Game Detail page only when direct launch is disabled. Direct launch hands off
        // from the XMB itself, so returning from the emulator leaves the cursor on this entity.
        if (item?.gameId != null && item.isRealGame) {
            if (_uiState.value.directLaunch) {
                // Direct launch is intentionally a true XMB hand-off: do not compose Game Detail
                // at all. This keeps the transition seamless and leaves the cursor on the same
                // entity when PFP resumes after the emulator closes.
                launchGameDirectly(item.gameId)
            } else {
                _uiState.update { it.copy(activeGameId = item.gameId, activeGameAutoLaunch = false) }
            }
            return
        }

        // Legacy captured shortcut (BannerHub / old Winlator) — launch its stored intent.
        if (item?.launchIntentUri != null) {
            launchStoredIntent(item.launchIntentUri, item.title)
            return
        }

        // Harvested launcher shortcut — A/Cross launches the host app's specific shortcut.
        if (item?.shortcutId != null && item.packageName != null) {
            launchHarvestedShortcut(item.packageName, item.shortcutId)
            return
        }

        // Standard (non-game) app — A/Cross launches it directly, no detail page.
        if (item?.packageName != null) {
            appCategoryRepository.launch(item.packageName)
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
            // Fixed system intent constant, no user-controlled data; NEW_TASK because the
            // launcher isn't an activity task the settings app should join.
            ANDROID_SETTINGS_ITEM_ID -> {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { Timber.w(it, "Could not open device settings") }
            }
            else -> when (category?.id) {
                BuiltInCategory.SETTINGS -> {
                    // Every row here is a screen id now, including the single Settings row, which
                    // carries the id of the screen the section rail opens on.
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

    private fun openFavoritesFolder() {
        val gamesCategoryIndex = _uiState.value.categories.indexOfFirst { it.id == BuiltInCategory.GAMES }
        navigateRememberingCursor {
            it.copy(
                selectedCategoryIndex = gamesCategoryIndex.takeIf { index -> index >= 0 } ?: it.selectedCategoryIndex,
                selectedPlatformId = FAVORITES_PLATFORM_ID,
                selectedCollectionId = null,
            )
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
        // Open the collection within the category it belongs to — a collection in a custom
        // gaming category must stay in that category, not jump back to Main Game.
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

    // Closes any open Games-root drill-down (platform card, All Games, or a collection).
    private fun closePlatformFolder() = navigateRememberingCursor {
        it.copy(selectedPlatformId = null, selectedCollectionId = null)
    }

    // ── Game detail overlay ───────────────────────────────────────────────────


    private fun launchGameDirectly(gameId: Long, discId: Long? = null) {
        // Keep the XMB selection untouched. The detail overlay is only an editing surface; direct
        // launch should never navigate through it, so onResume naturally returns to this row.
        _uiState.update { it.copy(activeGameId = null, activeGameAutoLaunch = false, activeGameDiscId = null) }
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
        val profile = emulatorProfileRepository.getProfilesForPlatform(game.platformId)
            .firstOrNull { it.isAvailable }
        if (profile == null) {
            Timber.w("No emulator available for direct launch: ${game.platformId}")
            launchDispatcher.recordPreflightFailure(
                game, null,
                "No emulator is set up for ${game.platformId.uppercase()}. " +
                    "Assign one under Settings ▸ Emulators ▸ Per-System Defaults.",
            )
            return
        }
        // Preflight the same checks Game Detail's resolver applies, so a stale RetroArch core
        // mapping (or a dropped launch activity) refuses here with a repair, not at startActivity.
        val validation = runCatching { intentResolver.validateBeforeLaunch(game, profile) }
        val resolvedLaunch = ResolvedLaunch(
            profile  = profile,
            source   = LaunchSource.CATALOG_DEFAULT,
            corePath = profile.corePathFor(game.platformId),
        )
        if (validation.isFailure) {
            Timber.w(
                validation.exceptionOrNull(),
                "Direct emulator launch blocked by preflight: ${profile.name}",
            )
            launchDispatcher.recordPreflightFailure(
                game, resolvedLaunch, validation.exceptionOrNull()?.message ?: "Could not launch ${profile.name}",
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

    // B1: the single XMB direct-launch hand-off. All three direct paths (stored intent, native
    // app, emulator) end here, so every game launch records an outcome and — when the emulator
    // never comes to the foreground — raises the recovery sheet instead of failing silently.
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
                pendingGameDetailAction = null,
            )
        }
        // Rebuild the visible list: title/artwork edits made in the detail screen must show the
        // moment the overlay closes (the item build is one-shot, not reactive to those tables).
        // A rename re-sorts that list, so the cursor follows the game rather than its old slot.
        loadItemsForCategory(currentCategory(), keepCursorOnRow = true)
    }

    fun consumeGameDetailAction() {
        _uiState.update { it.copy(pendingGameDetailAction = null) }
    }

    // ── App detail overlay ────────────────────────────────────────────────────

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

    // ── Android-app launch shortcuts ───────────────────────────────────────────
    //
    // Favorites and Collections are keyed on a games-table row id. Apps placed in XMB
    // categories are package-based (no game row), so they can't join either until they have a
    // "shortcut" row. A shortcut is a GameEntity that REFERENCES the app by packageName (the only
    // duplicated field is the display label) and is typed ANDROID_APP so it never aggregates into
    // All Games. It is deduped by package — one shortcut per app, reused by Favorites, every
    // Collection, and the App Detail screen. This is what makes GameHub (and any Android app)
    // shortcutable; GameHub is otherwise treated identically to any other app.
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
                // Sentinel platform: a shortcut row backs artwork/favorites/collections without
                // placing the app in the Android library (which is observeByPlatform("android")).
                platformId    = APP_SHORTCUT_PLATFORM_ID,
                packageName   = packageName,
                isManualEntry = true,
                contentType   = GameContentType.ANDROID_APP,
            )
        )
    }

    // Silent, same as toggleGameFavorite.
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

    // Launches a captured legacy INSTALL_SHORTCUT entry by parsing its stored intent.
    private fun launchStoredIntent(intentUri: String, label: String) {
        runCatching {
            val parsed = android.content.Intent.parseUri(intentUri, android.content.Intent.URI_INTENT_SCHEME)
            // Defense in depth: re-harden at launch (also cleans entries captured before the
            // sanitizer existed) so a stored intent can never grant file access or be redirected.
            val launch = (com.psplauncher.core.common.security.ShortcutIntentSanitizer
                .sanitize(parsed, context.packageManager)
                ?: error("Captured shortcut is not safe to launch"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        }.onFailure { e ->
            Timber.e(e, "Failed to launch captured shortcut: $label")
            taskNotifier.failed("launch_intent_${label.hashCode()}", label, "Couldn't launch: ${e.message}")
        }
    }

    fun onCloseAppDetail() {
        _uiState.update { it.copy(activeAppId = null, pendingAppDetailAction = null) }
        // Rebuild the visible list so a freshly assigned background/icon (games-table row keyed by
        // package) reaches the XMB rows immediately — this is what puts artworkUri on app items.
        // App renames re-sort too, so the cursor follows the row by id.
        loadItemsForCategory(currentCategory(), keepCursorOnRow = true)
    }

    fun consumeAppDetailAction() {
        _uiState.update { it.copy(pendingAppDetailAction = null) }
    }

    // ── Settings overlay ──────────────────────────────────────────────────────

    /**
     * Moves sideways to another settings screen, from the section rail, without going back out
     * to the crossbar in between.
     *
     * Only ever a screen of the settings tree: the rail is built from the catalog, so a caller
     * cannot ask for a route the tree does not own. Guarded anyway, because this is reachable
     * from a CompositionLocal that anything in a settings screen could call.
     */
    fun onOpenSettingsScreen(screenId: String) {
        if (com.psplauncher.core.domain.model.settingsEntryFor(screenId) == null) {
            Timber.w("Settings rail asked for a screen outside the catalog: %s", screenId)
            return
        }
        Timber.d("Settings rail -> %s", screenId)
        _uiState.update { it.copy(activeSettingsScreen = screenId) }
    }

    fun onCloseSettingsScreen() {
        Timber.d("Settings closed")
        // Leaving the setup wizard by any deliberate path (Skip, Finish, back-out on a re-run)
        // stamps it as seen — see markInitialSetupSeen for why open time is the wrong moment.
        val closing = _uiState.value.activeSettingsScreen
        if (closing == INITIAL_SETUP_SCREEN_ID || closing == INITIAL_SETUP_FIRST_RUN_SCREEN_ID) {
            markInitialSetupSeen()
        }
        _uiState.update { it.copy(activeSettingsScreen = null, pendingSettingsAction = null) }
    }

    // Bridge from Library Settings → the shared installed-app picker. Closes the settings overlay
    // and opens the same picker the XMB Android card uses, so apps are added the one way.
    fun openAndroidLibraryPicker() {
        _uiState.update { it.copy(activeSettingsScreen = null, pendingSettingsAction = null) }
        openAppPicker(AppPickerTarget.AndroidGames(ANDROID_PLATFORM_ID), "Add Android Apps")
    }

    fun consumeSettingsAction() {
        _uiState.update { it.copy(pendingSettingsAction = null) }
    }

    // ── App drawer overlay ────────────────────────────────────────────────────

    fun onOpenAppDrawer() {
        _uiState.update { it.copy(activeAppDrawerFilter = "ALL") }
    }

    fun onCloseAppDrawer() {
        _uiState.update { it.copy(activeAppDrawerFilter = null, pendingDrawerAction = null) }
    }

    fun consumeDrawerAction() {
        _uiState.update { it.copy(pendingDrawerAction = null) }
    }

    // ── Color-scheme picker ─────────────────────────────────────────────────────
    //
    // The picker previews schemes live: moving the cursor writes the highlighted
    // scheme to DataStore so observeColorScheme repaints the wave/background. BACK
    // restores whatever scheme was active when the picker opened; SELECT commits.

    private var colorSchemeOriginal: XmbColorScheme? = null
    // A custom-theme accent active when the picker opened — restored on cancel (previews
    // temporarily clear it so presets are actually visible).
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
                    sublabel = if (scheme == XmbColorScheme.ORIGINAL) "Changes with the month" else "Fixed color preset",
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

    /** Touch handler — highlight (and live-preview) the tapped row. */
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
                // Suspend a custom-theme accent while previewing, or the preset preview would
                // be invisibly masked by the override. Cancel restores it (see below).
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
                    // Explicitly choosing a preset exits custom-theme mode — otherwise the
                    // imported-theme accent would keep overriding the pick invisibly. The
                    // theme's custom icons and layout leave with it (presets use the
                    // built-in glyphs and the default geometry).
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
                    // Restore the custom-theme accent that previews temporarily cleared.
                    if (accentOriginal != null) it[KEY_ACCENT_OVERRIDE] = accentOriginal
                }
            }
            colorSchemeOriginal = null
            accentOverrideOriginal = null
            _uiState.update { it.copy(colorSchemePicker = null) }
        }
    }

    // ── Live "Adjust XMB Layout" editor ────────────────────────────────────────
    // Opens over the real XMB (settings closed, so the cross is visible and moves live). The
    // current form-factor bucket is read from the window config; the draft seeds from a saved
    // tuning for that bucket, else from the legacy scale + the theme's bar line.

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
                // Close any settings screen so the live XMB shows behind the editor.
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

    // Nudge steps for D-pad / shoulder-button control.
    fun nudgeXmbLayoutHorizontal(dir: Int) = updateAdjustDraft { it.copy(barLeftFraction = it.barLeftFraction + dir * 0.01f) }
    fun nudgeXmbLayoutVertical(dir: Int) = updateAdjustDraft { it.copy(barTopFraction = it.barTopFraction + dir * 0.01f) }
    fun nudgeXmbLayoutScale(dir: Int) = updateAdjustDraft { it.copy(scale = it.scale + dir * 0.02f) }

    // Absolute setters for the on-screen sliders.
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

    // ── Live "Customize XMB Icons" editor ────────────────────────────────────
    // Opens over the real XMB (settings closed, so the cross and columns are visible). Edits
    // apply immediately through CustomIconStore — there is no draft to discard; Reset /
    // Reset All are the undo. The session carries only cursor + message state.

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
                // Close any settings screen so the live XMB shows behind the editor.
                activeSettingsScreen = null,
                pendingSettingsAction = null,
                customIconSession = CustomIconSession(groups = customIconGroups),
            )
        }
    }

    fun closeCustomIcons() {
        _uiState.update { it.copy(customIconSession = null) }
    }

    // Every cursor move drops [CustomIconSession.message]: it always describes what just
    // happened to ONE slot, so carrying it to the next one would attribute the outcome to a
    // slot it was never about.

    /** Moves the group cursor (L/R); wraps so the ends loop. */
    fun onCustomIconGroupMove(dir: Int) {
        val session = _uiState.value.customIconSession ?: return
        val next = (session.groupIndex + dir).mod(session.groups.size)
        _uiState.update {
            it.copy(customIconSession = session.copy(groupIndex = next, slotIndex = 0, message = null))
        }
    }

    /** Moves the slot cursor within the current group (UP/DOWN); clamps at the ends. */
    fun onCustomIconSlotMove(dir: Int) {
        val session = _uiState.value.customIconSession ?: return
        val count = CustomizableIcons.group(session.group).size
        val next = (session.slotIndex + dir).coerceIn(0, (count - 1).coerceAtLeast(0))
        _uiState.update { it.copy(customIconSession = session.copy(slotIndex = next, message = null)) }
    }

    /** Touch: focus a strip slot directly (same cursor as the pad moves). */
    fun onCustomIconSlotFocused(index: Int) {
        val session = _uiState.value.customIconSession ?: return
        _uiState.update { it.copy(customIconSession = session.copy(slotIndex = index, message = null)) }
    }

    /** SAF result: replace [slotKey]'s icon. The message lands in the session. */
    fun onIconPicked(slotKey: String, uri: android.net.Uri) {
        val session = _uiState.value.customIconSession ?: return
        viewModelScope.launch {
            val mime = context.contentResolver.getType(uri)
            val result = customIconStore.import(slotKey, uri, mime)
            // Committed (or refused) import: the same confirm/error pairing the sound pickers use.
            menuSound.play(if (result.ok) MenuSound.CONFIRM else MenuSound.ERROR)
            _uiState.update {
                val s = it.customIconSession ?: return@update it
                it.copy(customIconSession = s.copy(message = result.message, revision = s.revision + 1))
            }
        }
    }

    /**
     * Per-slot Reset: the user's pick goes; the built-in returns immediately UNLESS the
     * applied theme supplies this slot, in which case the theme's icon surfaces instead.
     *
     * Reset only ever clears the user tier, so on a themed slot — or one that was never
     * picked — it legitimately changes nothing on screen. That is precisely when it reads as
     * a dead button, so every outcome says what happened; only the unambiguous one (the
     * built-in visibly returns) stays silent.
     */
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

    /** Reset All: every user pick goes; the built-ins return except where the theme supplies a slot. */
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

    /** XMBShell calls once the share sheet has fired (or failed) for [pendingThemeShareFile]. */
    fun onThemeShareConsumed() {
        _uiState.update { it.copy(pendingThemeShareFile = null) }
    }

    /** "Save as Theme…" — opens the name dialog (reusing the playlist-dialog pattern). */
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

    /** The overlay calls once it has handled a forwarded [pendingCustomIconsAction]. */
    fun onCustomIconsActionConsumed() {
        _uiState.update { it.copy(pendingCustomIconsAction = null) }
    }

    /**
     * Save as Theme…: writes the whole current look (picks + theme icons + wallpaper + colors
     * + motion) into the theme library, then opens the share sheet for the saved bundle.
     * Reuses PfpThemeStore.exportForShare — no new share plumbing.
     */
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


    // The two Boot Sequence prefs, kept as fields rather than in UiState: nothing renders them,
    // and they are only read at the resume moment. Both were previously written by settings and
    // read by nothing at all — the animation always played.
    @Volatile
    private var bootEnabled: Boolean = true

    @Volatile
    private var bootOnResume: Boolean = false

    /**
     * Seeds [XMBUiState.showBootSequence] from `display_show_boot` and tracks both boot prefs.
     * The overlay holds on a black frame until startup permissions and the first-run check
     * resolve (see XMBShell), so this read lands well before anything animates — a boot the user
     * turned off never becomes visible.
     */
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
        // Resolve the user's boot media here rather than in the overlay: the store's lookup is
        // file IO, and it must not run on the composition that is trying to draw the first frame.
        // Re-resolved on every stamp bump, so an import or a restored backup is picked up.
        //
        // Audio falls back to the bundled opening chime ONLY when there is no custom boot video:
        // a custom video keeps its own audio track unless the user explicitly assigned a boot
        // sound — see resolveBootAudio, which pins that rule in one tested place.
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

    /**
     * PFP is in the foreground again after having been stopped — in practice, back from a game.
     * Replays the boot sequence when the user asked for it. Show Boot Sequence gates this too:
     * turning boot off skips BOTH of its media components, resume included (design rule 7).
     */
    fun onHostResumed() {
        if (!bootEnabled || !bootOnResume) return
        _uiState.update { it.copy(showBootSequence = true) }
    }

    // ── GameBoot ──────────────────────────────────────────────────────────────

    private fun observeGameBoot() {
        // The gate raises a request from inside LaunchDispatcher and suspends the launch until the
        // overlay reports back. A preview is never overwritten by one: previews are only reachable
        // from Settings, where no launch is in flight.
        viewModelScope.launch {
            gameBootGate.active.collect { request ->
                _uiState.update {
                    if (request == null && it.gameBootIsPreview) it
                    else it.copy(activeGameBoot = request, gameBootIsPreview = false)
                }
            }
        }
    }

    /**
     * The overlay's presentation is over — naturally, skipped, failed, or watchdogged. A preview
     * just closes; a real one releases the launch that is waiting on the gate.
     */
    fun onGameBootComplete() {
        val wasPreview = _uiState.value.gameBootIsPreview
        _uiState.update { it.copy(activeGameBoot = null, gameBootIsPreview = false) }
        if (wasPreview) {
            // Nothing is launching, so nothing will take audio focus and cut the clip: a skipped
            // preview has to silence itself, or the sound plays on over the settings screen.
            uiMediaAudioPlayer.stop()
        } else {
            gameBootGate.onPresentationFinished()
        }
    }

    // ── Settings previews ─────────────────────────────────────────────────────

    /**
     * Settings ▸ Display ▸ Boot Sequence ▸ Preview. Re-shows the real overlay over the settings
     * screen (boot already draws above that layer); its normal completion path returns here.
     */
    fun previewBootSequence() {
        _uiState.update { it.copy(showBootSequence = true) }
    }

    /**
     * Settings ▸ Display ▸ GameBoot ▸ Preview. Composes the overlay directly and NEVER touches the
     * gate — a preview must not be able to launch anything.
     *
     * It does everything else the gate does, though, and that is the point: same media resolution,
     * same audio player, started at the same moment relative to the first frame. The preview and
     * the real presentation differ only in what is waiting on the other side.
     */
    fun previewGameBoot() {
        viewModelScope.launch {
            // The preview plays regardless of the switch, so the user can audition the
            // presentation before turning it on. Audio resolves exactly as the gate resolves it
            // — the built-in sound, or nothing when a custom clip carries its own track — so the
            // preview sounds like the real thing.
            val (video, audio) = withContext(Dispatchers.IO) {
                val customVideo = uiMediaStore.pathFor(com.psplauncher.core.domain.model.UiMediaSlot.GAMEBOOT_VIDEO)
                customVideo to resolveGameBootAudio(
                    customVideoPath = customVideo,
                    defaultUri = com.psplauncher.core.ui.media.gameBootDefaultAudioUri(context.packageName),
                )
            }
            // Started here, immediately before the overlay composes, exactly as GameBootGate
            // starts it before a real presentation — the sequence is beat-matched to this sound,
            // so a silent preview would show light landing on nothing.
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
                    ),
                    gameBootIsPreview = true,
                )
            }
        }
    }

    // ── Boot sequence ─────────────────────────────────────────────────────────

    fun onBootSequenceComplete() {
        Timber.d("StartupSeq: boot sequence complete")
        _uiState.update { it.copy(showBootSequence = false) }
    }

    /** MainActivity reports the notification-permission dialog resolved (or was never needed). */
    fun onStartupPermissionsSettled() {
        Timber.d("StartupSeq: notification permission settled")
        _uiState.update { it.copy(startupPermissionsSettled = true) }
    }

    // ── First-run setup wizard ────────────────────────────────────────────────

    /**
     * One-shot first-run check. A fresh install (nothing configured, wizard never shown) gets
     * the wizard opened immediately: it composes hidden beneath the opaque boot overlay (which
     * XMBShell draws on top of the settings layer and holds until this check resolves), so the
     * boot dissolve reveals the wizard — never the XMB. Installs that already carry
     * configuration are upgraders: the seen flag is written silently so the wizard never
     * appears for them.
     */
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
            // Releases the boot overlay's hold: the wizard (if due) is now composed beneath it,
            // so the XMB-before-wizard race is excluded by construction, not by timing.
            _uiState.update { it.copy(initialSetupDecided = true) }
        }
    }

    // Memory cards cover the modern flows that write none of the checked pref keys (e.g. an
    // Android-apps-only library) — any card means an established install, not a fresh one.
    private suspend fun hasExistingLibrary(): Boolean =
        runCatching { memoryCardRepository.getAll().isNotEmpty() }.getOrDefault(false)

    // Verbose trace of the startup choreography — one line per state change, so logcat shows
    // exactly what was on screen in what order (permission gate, boot overlay, wizard, XMB).
    // Bounded: the collector completes right after the emission that ends the boot sequence,
    // so the hot XMB state stream carries no permanent logging tax.
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

    /**
     * Stamps the wizard as seen. Called on deliberate exits (Skip, Finish, closing the overlay,
     * jumping to Library Manager) — not at open, so a process death mid-wizard re-opens it on
     * the next launch instead of silently cancelling first-run setup forever.
     */
    private fun markInitialSetupSeen() {
        viewModelScope.launch {
            context.pfpDataStore.edit { it[KEY_INITIAL_SETUP_SEEN] = true }
        }
    }

    /** Wizard finish page: jump straight into the Library Manager to add consoles and scan. */
    fun openLibraryManager() {
        markInitialSetupSeen()
        _uiState.update { it.copy(activeSettingsScreen = "settings_library") }
    }

    /**
     * Wizard FINISH "Go to your library" (B3): close the wizard and land on the All Games folder
     * — cursor on the first playable game when one exists, so finishing setup ends on something
     * launchable instead of dropping the user back on a bare category bar.
     */
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

    // ── Derived setup state (B3) ──────────────────────────────────────────────
    //
    // What's still missing between a fresh install and playing a game, derived from the live
    // stores (ROM roots, console cards, emulators) instead of the write-only
    // `library_setup_complete` pref flag — so empty XMB surfaces can name the FIRST unmet step
    // and deep-link to the screen that fixes it.
    private var setupState: com.psplauncher.feature.launcher.SetupState =
        com.psplauncher.feature.launcher.SetupState()

    private fun observeSetupState() {
        viewModelScope.launch {
            setupStateProvider.observe().collect { fresh ->
                if (fresh != setupState) {
                    setupState = fresh
                    // Re-render visible empty rows so a gap closing (wizard added a root) swaps
                    // the "Add a ROM folder" prompt for the normal empty-library copy.
                    if (_uiState.value.currentItems.any { it.type == XMBItemType.EMPTY }) {
                        loadItemsForCategory(currentCategory())
                    }
                }
            }
        }
    }

    // ── User interaction ──────────────────────────────────────────────────────

    /**
     * Hook invoked on every gamepad action (and from touch gestures via the activity). The
     * background mode and wave style are now explicit, user-controlled settings, so interaction
     * no longer mutates the wave — but it DOES reset the idle timer and drop the hint pill.
     *
     * The drop is done here rather than left to the idle loop: this comment used to promise the
     * hint "hides immediately on any activity" while the actual hide waited for the next poll
     * tick, leaving the pill up for as much as IDLE_HINT_POLL_MS after a press.
     */
    fun onUserInteraction() {
        lastInteractionMs = SystemClock.elapsedRealtime()
        if (_uiState.value.showContextMenuHint ||
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

    // ── Library setup state ───────────────────────────────────────────────────

    private fun observeLibrarySetupState() {
        viewModelScope.launch {
            context.pfpDataStore.data.collect { prefs ->
                val complete = prefs[KEY_SETUP_COMPLETE] ?: false
                _uiState.update { it.copy(librarySetupComplete = complete) }
            }
        }
    }

    // ── Icon style ────────────────────────────────────────────────────────────

    // (The legacy display_icon_style pref is no longer observed — Artwork ▸ Game Icon Display
    // and its Physical Media mode replaced the old PSP Rectangle / Cartridge icon style.)

    // Global icon display mode — tiles resolve against it at render, so a change recomposes
    // every visible tile without rebuilding the item list.
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
            iconDisplayPreferences.snapPlacementFlow.collect { placement ->
                // Drop any playing snap on a placement change so it restarts where it now belongs
                // instead of finishing out in the slot the user just moved it out of.
                _uiState.update { it.copy(snapPlacement = placement, focusedGameVideo = null) }
            }
        }
        viewModelScope.launch {
            // User-adjustable rest gate (Artwork ▸ Art Preferences ▸ Video Snap Delay). Each new
            // rest starts from the current value; the in-flight delay simply runs out unchanged.
            iconDisplayPreferences.lingerDelaySecondsFlow.collect { seconds ->
                icon1LingerMs = (seconds * 1_000f).toLong()
            }
        }
        viewModelScope.launch {
            gameLaunchPreferences.directLaunchFlow.collect { direct ->
                _uiState.update { it.copy(directLaunch = direct) }
            }
        }
    }

    // ── ICON1 video snaps ─────────────────────────────────────────────────────

    @Volatile private var animatedIconsEnabled = true

    /** How long the cursor rests before the XMB takes on the focused game's colour. */
    private val ACCENT_SETTLE_MS = 220L

    // Live rest-before-play gate, fed by IconDisplayPreferences.lingerDelaySecondsFlow; starts at
    // the PSP-faithful 1.5 s and tracks the user's Video Snap Delay setting.
    @Volatile private var icon1LingerMs = ICON1_LINGER_MS

    /**
     * PSP ICON1 choreography with a battery conscience: when the cursor RESTS on a game whose
     * tile is in ICON0 mode, wait out the linger gate (scrolling never spins up a decoder),
     * re-check the environment gates, then look up the game's video snap and publish it —
     * [com.psplauncher.feature.xmb.ui.Icon1VideoOverlay] plays it in-slot. Any focus move,
     * overlay, or mode change clears it immediately (collectLatest cancels the pending linger).
     */
    /**
     * The focused game's colour, following the cursor.
     *
     * Debounced, and that is the whole difference from the detail page's version of this: there
     * a page opens on ONE game, here the cursor can cross forty of them in a second, and decoding
     * every one of those would be forty bitmaps nobody ever sees. The wait is deliberately
     * shorter than the video snap's -- a colour settling in is cheap and reads as the screen
     * catching up, where a video starting is an event.
     */
    private fun observeFocusedGameAccent() {
        viewModelScope.launch {
            _uiState
                .map { s -> s.currentItems.getOrNull(s.selectedItemIndex)?.takeIf { it.isRealGame } }
                .distinctUntilChanged { a, b -> a?.gameId == b?.gameId }
                .collectLatest { item ->
                    if (item == null) {
                        _uiState.update {
                            it.copy(focusedGameAccentArgb = null, focusedGameBackdrop = null)
                        }
                        return@collectLatest
                    }
                    kotlinx.coroutines.delay(ACCENT_SETTLE_MS)
                    val art = artworkAccent.resolve(
                        item.artworkUri, item.heroUri, item.boxArtUri, item.iconUri,
                    )
                    // collectLatest cancels this on any cursor move, so reaching here means the
                    // cursor is still on the game this was read for.
                    _uiState.update {
                        it.copy(
                            focusedGameAccentArgb = art?.accent,
                            focusedGameBackdrop = art?.uri,
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
                    // Approve only if the snap has somewhere to draw. snapSiteFor is the one
                    // definition of that, shared with the two render sites -- the in-tile
                    // placement needs an ICON0 tile to play over, the background placement needs
                    // nothing and so plays in any icon mode.
                    val eligible = item?.gameId != null && item.isRealGame &&
                        !s.hasBlockingOverlay &&
                        com.psplauncher.feature.xmb.ui.snapSiteFor(
                            s.snapPlacement,
                            resolveIconDisplay(item, s.iconDisplayMode, s.iconDisplayModeByPlatform).mode,
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
                    // ICON1 (short snap) is preferred; a full VIDEO is a valid fallback since the
                    // icon player clips to 60 s at playback anyway (covers pre-split data too).
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

    // Environment gates, checked after the linger: global toggle, Battery Saver, thermal
    // pressure, and low battery while unplugged all veto the decode before it starts.
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

    // ── Touch-navigation button preference ────────────────────────────────────────

    private fun observeTouchNavButtonMode() {
        viewModelScope.launch {
            context.pfpDataStore.data.collect { prefs ->
                val mode = com.psplauncher.core.domain.model.TouchNavButtonMode
                    .fromName(prefs[KEY_TOUCH_NAV_BUTTON])
                val sensitivity = com.psplauncher.core.domain.model.TouchSensitivity
                    .fromName(prefs[KEY_TOUCH_SENSITIVITY])
                val hintEnabled = prefs[KEY_CONTEXT_MENU_HINT] ?: true
                val hintDelaySeconds =
                    (prefs[KEY_CONTEXT_MENU_HINT_DELAY_SECONDS] ?: 2.5f).coerceIn(1f, 5f)
                val legibility = com.psplauncher.core.domain.model.IconLegibilityStyle
                    .fromName(prefs[KEY_ICON_LEGIBILITY])
                val solidUnfocused = prefs[KEY_SOLID_UNFOCUSED_ICONS] ?: false
                val textShadow = prefs[KEY_TEXT_SHADOW] ?: true
                _uiState.update {
                    it.copy(
                        touchNavButtonMode = mode,
                        touchSensitivity = sensitivity,
                        contextMenuHintEnabled = hintEnabled,
                        contextMenuHintDelaySeconds = hintDelaySeconds,
                        iconLegibility = legibility,
                        solidUnfocusedIcons = solidUnfocused,
                        textShadow = textShadow,
                    )
                }
            }
        }
    }

    // ── Wave style ──────────────────────────────────────────────────────────────

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
                        thermalThrottleAware = prefs[KEY_THERMAL_AWARE] ?: true,
                    )
                }
            }
        }
    }

    // ── Custom wallpaper ──────────────────────────────────────────────────────

    private fun observeWallpaper() {
        viewModelScope.launch {
            context.pfpDataStore.data.collect { prefs ->
                val path = prefs[KEY_CUSTOM_WALLPAPER]
                // Validate the file still exists before surfacing it to the UI.
                val validPath = if (path != null && java.io.File(path).exists()) path else null
                // Motion is valid only with its poster (the freeze/failure fallback). Reading
                // the invalid state as "no motion" — never trying to recover it.
                val motionPath = prefs[KEY_MOTION_WALLPAPER]
                    ?.takeIf { validPath != null && java.io.File(it).exists() }
                _uiState.update { it.copy(customWallpaperPath = validPath, motionWallpaperPath = motionPath) }
            }
        }
    }

    // ── Static data ───────────────────────────────────────────────────────────

    companion object {
        private val KEY_WAVE_STYLE        = stringPreferencesKey("display_wave_style")
        // Must match DisplaySettingsViewModel — both read/write these wave power-throttle prefs.
        private val KEY_RESPECT_BATTERY   = booleanPreferencesKey("display_battery_saver")
        private val KEY_THERMAL_AWARE     = booleanPreferencesKey("display_thermal_aware")
        private val KEY_COLOR_SCHEME      = stringPreferencesKey("display_color_scheme")
        // Custom-theme cascade (docs/xmb-theme-creator-plan.md): when set, this ARGB accent
        // overrides the preset scheme — wave, gradient, and cursor all derive from it.
        private val KEY_ACCENT_OVERRIDE   = longPreferencesKey("theme_accent_override")
        // Unified icon tint (ARGB); unset = white = the icon art's native color.
        private val KEY_ICON_COLOR        = longPreferencesKey("theme_icon_color")
        // display_-prefixed, matching DisplaySettingsViewModel's keys: the font colour is a
        // Display setting the user owns, not a property a theme bundle silently overwrites.
        private val KEY_TEXT_COLOR        = longPreferencesKey("display_text_color")
        // Display ▸ Scale & Layout — must match DisplaySettingsViewModel (shared prefs contract).
        private val KEY_XMB_SCALE         = androidx.datastore.preferences.core.floatPreferencesKey("display_xmb_scale")
        private val KEY_BAR_TOP_FRACTION  = androidx.datastore.preferences.core.floatPreferencesKey("display_bar_top_fraction")
        // Per-form-factor live layout tunings (scale + horizontal + vertical), one JSON prefs string.
        // Idle context-menu hint: how long to wait before showing, and how often to recheck.
        internal const val IDLE_HINT_DELAY_MS = 2_500L
        internal const val IDLE_HINT_POLL_MS  = 500L
        private val KEY_XMB_LAYOUT_ADJUST = stringPreferencesKey("display_xmb_layout_adjust")
        private val KEY_SETUP_COMPLETE    = booleanPreferencesKey("library_setup_complete")
        // First-run wizard: set the moment the wizard is shown (or silently seeded for installs
        // that already carry configuration), so it only ever auto-opens once.
        private val KEY_INITIAL_SETUP_SEEN = booleanPreferencesKey("initial_setup_seen")
        internal const val INITIAL_SETUP_SCREEN_ID = "settings_initial_setup"
        // The automatic first-run variant of the wizard: Back cannot exit from its first page.
        internal const val INITIAL_SETUP_FIRST_RUN_SCREEN_ID = "settings_initial_setup_first"

        // Pref keys that mean the install already has real configuration — roots, a finished
        // library setup, or connected service credentials. Such installs are upgraders and must
        // never see the first-run wizard. Complemented by hasExistingLibrary() for modern flows
        // (e.g. an Android-apps-only library) that write none of these keys.
        private val EXISTING_CONFIG_STRING_KEYS = listOf(
            stringPreferencesKey("library_rom_root_tree_uris"),
            stringPreferencesKey("library_rom_root_tree_uri"), // legacy single ROM root
            stringPreferencesKey("music_root_tree_uris"),
            stringPreferencesKey("video_root_tree_uris"),
            stringPreferencesKey("photo_root_tree_uris"),
            stringPreferencesKey("artwork_folder_tree_uri"),
            // Service identities/credentials (public parts only — the encrypted secrets always
            // travel with them, so presence of one implies a configured account).
            stringPreferencesKey("sgdb_api_key"),
            stringPreferencesKey("igdb_client_id"),
            stringPreferencesKey("ss_username"),
            stringPreferencesKey("ra_username"),
            stringPreferencesKey("steam_id64"),
        )

        /** Pure decision: does this preferences snapshot already carry user configuration? */
        internal fun hasExistingSetupConfig(prefs: androidx.datastore.preferences.core.Preferences): Boolean =
            prefs[KEY_SETUP_COMPLETE] == true ||
                EXISTING_CONFIG_STRING_KEYS.any { !prefs[it].isNullOrBlank() }
        private val KEY_CUSTOM_WALLPAPER  = stringPreferencesKey("display_custom_wallpaper")
        // Must match DisplaySettingsViewModel — shared wallpaper cascade prefs. Motion is never
        // set without the poster key (invariant enforced at the write sites).
        private val KEY_MOTION_WALLPAPER = stringPreferencesKey("display_motion_wallpaper")
        // Must match DisplaySettingsViewModel — the Boot Sequence toggles. Before this both keys
        // were written by settings and read by nothing: the boot animation always played.
        private val KEY_SHOW_BOOT       = booleanPreferencesKey("display_show_boot")
        private val KEY_BOOT_ON_RESUME  = booleanPreferencesKey("display_boot_on_resume")
        // Must match DisplaySettingsViewModel.KEY_TOUCH_NAV_BUTTON — both read/write this pref.
        private val KEY_TOUCH_NAV_BUTTON  = stringPreferencesKey("interface_touch_nav_button")
        // Must match DisplaySettingsViewModel.KEY_CONTEXT_MENU_HINT — both read/write this pref.
        private val KEY_CONTEXT_MENU_HINT = booleanPreferencesKey("interface_context_menu_hint")
        private val KEY_CONTEXT_MENU_HINT_DELAY_SECONDS =
            floatPreferencesKey("interface_context_menu_hint_delay_seconds")
        // Must match DisplaySettingsViewModel.KEY_TOUCH_SENSITIVITY — both read/write this pref.
        private val KEY_TOUCH_SENSITIVITY = stringPreferencesKey("interface_touch_sensitivity")
        // Must match DisplaySettingsViewModel.KEY_ICON_LEGIBILITY — both read/write this pref.
        private val KEY_ICON_LEGIBILITY = stringPreferencesKey("display_icon_legibility")
        // Must match DisplaySettingsViewModel.KEY_SOLID_UNFOCUSED_ICONS — both read/write this pref.
        private val KEY_SOLID_UNFOCUSED_ICONS = booleanPreferencesKey("display_solid_unfocused_icons")
        // Must match DisplaySettingsViewModel.KEY_TEXT_SHADOW — both read/write this pref.
        private val KEY_TEXT_SHADOW = booleanPreferencesKey("display_text_shadow")
        // ICON1 linger default (1.5 s) — the user can adjust the delay under Artwork ▸ Art
        // Preferences ▸ Video Snap Delay. Rest-then-animate matches the PSP's choreography and
        // guarantees scrolling through the row never spins up a video decoder.
        private const val ICON1_LINGER_MS = 1_500L
        private const val SETUP_ITEM_ID = "library_setup"
        private const val NO_CONSOLES_ITEM_ID = "no_consoles"
        // B3: empty All Games row while setup still has an unmet step (names + fixes the gap).
        private const val SETUP_GAP_ITEM_ID = "setup_gap"
        private const val NO_GAMES_ITEM_ID    = "no_games"
        private const val EMPTY_COLLECTION_ITEM_ID = "empty_collection"
        private const val EMPTY_FAVORITES_ITEM_ID = "empty_favorites"
        private const val EMPTY_CATEGORY_ITEM_ID = "empty_category"
        private const val ALL_GAMES_ITEM_ID = "all_games"
        private const val ALL_GAMES_PLATFORM_ID = "__all_games__"
        private const val FAVORITES_ITEM_ID = "favorites_folder"
        private const val FAVORITES_PLATFORM_ID = "__favorites__"
        private const val MISSING_ITEM_ID = "missing_folder"
        private const val MISSING_PLATFORM_ID = "__missing__"
        private const val EMPTY_MISSING_ITEM_ID = "empty_missing"
        // Shown as each missing row's subtitle. Phrased around the scan rather than the file
        // ("File not found" alone reads as permanent) because dropping the file back reactivates it.
        private const val MISSING_REASON = "File not found on last scan"
        private const val ADD_APPS_ITEM_ID = "add_apps"
        private const val ADD_GAMES_ITEM_ID = "add_games"
        private const val FIND_GAMES_ITEM_ID = "find_games"
        // Platform id whose library is built from installed apps (picker) instead of ROM scans.
        private const val ANDROID_PLATFORM_ID = "android"
        // Sentinel platform for app rows that merely BACK a category app's artwork / favorite /
        // collection membership. They reference an app by package but are NOT in the Android
        // library, so they use this id instead of "android" to stay out of observeByPlatform.
        private const val APP_SHORTCUT_PLATFORM_ID = "app_shortcut"
        // Virtual card holding PC-launcher game imports (harvest / folder scan / add-by-ID).
        private const val WINDOWS_PLATFORM_ID = "windows"

        // Music category synthetic rows / drill ids.
        private const val ADD_MUSIC_FOLDER_ITEM_ID = "add_music_folder"
        private const val ALL_MUSIC_ITEM_ID = "all_music"
        internal const val NOW_PLAYING_ITEM_ID = "now_playing"
        private const val PLAYLISTS_ITEM_ID = "playlists"
        private const val MUSIC_APPS_ITEM_ID = "music_apps_item"
        private const val ADD_MUSIC_APPS_ITEM_ID = "add_music_apps"
        private const val CREATE_PLAYLIST_ITEM_ID = "create_playlist"
        private const val ADD_TRACKS_ITEM_ID = "add_tracks"
        private const val EMPTY_PLAYLIST_ITEM_ID = "empty_playlist"
        // The "Apps" sections (Music / Video / Photo) are backed by the REAL built-in media
        // categories — not hidden pseudo-categories. Apps auto-populate from the classifier
        // (installed music / video / photo apps) and any manual picks live in the same real
        // category, so there is nothing hidden for users to tamper with in Category settings.
        private const val MUSIC_APPS_CATEGORY_ID = "music"
        // Video root item ids.
        private const val ALL_VIDEOS_ITEM_ID = "all_videos"
        private const val VIDEO_COLLECTIONS_ITEM_ID = "video_collections"
        private const val RECENTLY_WATCHED_ITEM_ID = "recently_watched"
        private const val FAVORITE_VIDEOS_ITEM_ID = "favorite_videos"
        private const val VIDEO_PLAYLISTS_ITEM_ID = "video_playlists"
        private const val CREATE_VIDEO_PLAYLIST_ITEM_ID = "create_video_playlist"
        private const val VIDEO_LIBRARIES_ITEM_ID = "video_libraries"
        private const val VIDEO_APPS_ITEM_ID = "video_apps_item"
        private const val ADD_VIDEOS_ITEM_ID = "add_videos"
        private const val ADD_VIDEO_APPS_ITEM_ID = "add_video_apps"
        private const val VIDEO_APPS_CATEGORY_ID = "videos"
        // Photo root item ids.
        private const val ALL_PHOTOS_ITEM_ID = "all_photos"
        private const val CAMERA_ITEM_ID = "photo_camera"
        private const val ADD_PHOTO_LIBRARY_ITEM_ID = "add_photo_library"
        private const val PHOTO_ALBUMS_ITEM_ID = "photo_albums"
        private const val OPEN_READER_ITEM_ID = "library_open_reader"
        private const val BOOK_SHELVES_ITEM_ID = "library_shelves"
        private const val BOOK_SERIES_ITEM_ID = "library_series"
        private const val ALL_BOOKS_ITEM_ID = "all_books"
        private const val ADD_BOOK_FOLDER_ITEM_ID = "add_book_folder"
        private const val PHOTO_APPS_ITEM_ID = "photo_apps_item"
        private const val ADD_PHOTO_APPS_ITEM_ID = "add_photo_apps"
        private const val PHOTO_APPS_CATEGORY_ID = "photos"
        // Generic memory-card art for the "Music" (All Music) item — the physical-media default
        // PNG, loaded from assets via Coil (same convention as PhysicalMediaIcon).
        private const val MEMORY_CARD_ASSET_URI =
            "file:///android_asset/systems/physical-media/_default.png"
        // Sentinel in XMBContextMenu.musicTrackId marking the in-app player's own options menu.
        private const val MUSIC_PLAYER_MENU_MARKER = "__music_player__"

        // Used only if the categories table hasn't been seeded yet (first frame on first run).
        // The main XMB always presents these seven categories in this order.
        val FALLBACK_CATEGORIES = listOf(
            Category(id = BuiltInCategory.SETTINGS, name = "Settings",  iconKey = "ic_settings", type = CategoryType.BUILT_IN, position = 0),
            Category(id = "photos",                 name = "Photo",     iconKey = "ic_photos",   type = CategoryType.BUILT_IN, position = 1),
            Category(id = "music",                  name = "Music",     iconKey = "ic_music",    type = CategoryType.BUILT_IN, position = 2),
            Category(id = "videos",                 name = "Video",     iconKey = "ic_videos",   type = CategoryType.BUILT_IN, position = 3),
            Category(id = BuiltInCategory.GAMES,    name = "Game",      iconKey = "ic_games",    type = CategoryType.BUILT_IN, position = 4, isGamingCategory = true),
            Category(id = "network",                name = "Network",   iconKey = "ic_network",  type = CategoryType.BUILT_IN, position = 5),
            Category(id = "app_store",              name = "App Store", iconKey = "ic_appstore", type = CategoryType.BUILT_IN, position = 6),
        )

        private val ANDROID_ITEMS = listOf(
            XMBItem(id = "drawer_all",       title = "All Apps",      subtitle = "Browse every installed app"),
            XMBItem(id = "drawer_games",     title = "Games",         subtitle = "Apps categorized as games"),
            XMBItem(id = "drawer_emulators", title = "Emulators",     subtitle = "RetroArch, PPSSPP, Dolphin and more"),
            XMBItem(id = "drawer_recent",    title = "Recently Used", subtitle = "Apps you've used lately"),
        )

        // First item opens the device's own Settings app (not a PFP screen).
        internal const val ANDROID_SETTINGS_ITEM_ID = "settings_android_system"

        /**
         * The id the single Settings row opens on: the first screen of the first section.
         *
         * Taken from the catalog rather than written out, so reordering the catalog moves this
         * with it. The rail on that screen is what reaches everything else.
         */
        internal val SETTINGS_ENTRY_SCREEN_ID: String =
            com.psplauncher.core.domain.model.SETTINGS_CATALOG.first().id

        // Settings root: one row into the settings screens, one into Android's own. `internal` so
        // the hierarchy unit tests can assert the exact root order.
        internal val SETTINGS_ROOT_ITEMS = listOf(
            XMBItem(
                id = SETTINGS_ENTRY_SCREEN_ID,
                title = "Settings",
                subtitle = "Library, emulators, appearance, interface, media & system",
            ),
            XMBItem(id = ANDROID_SETTINGS_ITEM_ID, title = "Android Settings", subtitle = "Opens device settings"),
        )
    }

    private fun canonicalXmbCategories(categories: List<Category>): List<Category> =
        canonicalXmbCategories(categories, FALLBACK_CATEGORIES)

    private fun defaultXmbCategoryIndex(categories: List<Category>): Int =
        categories.indexOfFirst { it.id == BuiltInCategory.GAMES }
            .takeIf { it >= 0 }
            ?: 0
}
