package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.PlatformIds
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
import com.psplauncher.core.domain.model.CategoryType
import com.psplauncher.core.domain.model.ControllerHintPolicy
import com.psplauncher.core.domain.model.ControllerIcon
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
import com.psplauncher.feature.launcher.corePathFor
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
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
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
import kotlinx.coroutines.withTimeout
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
    /**
     * The rows behind "More…", if this menu was long enough to need one.
     *
     * Carried on the menu rather than rebuilt when More is pressed: rebuilding would mean
     * re-running a builder whose inputs (the row, the disc count, the hide location) are gone
     * by then, and the two lists could disagree about what the menu contained.
     */
    val overflow: List<XMBContextMenuItem> = emptyList(),
    /**
     * Which rail row has the cursor, or NULL while the menu is open and nothing is picked.
     *
     * It opened on row one, which meant opening the options over a game immediately took confirm
     * away from the game: the press under your thumb went from "play this" to "run whatever the
     * first action happens to be". Nothing is selected until you move onto something.
     */
    val selectedIndex: Int? = null,
    /**
     * The id confirm runs while [selectedIndex] is null — the thing this row IS for.
     *
     * "play" on a game, so opening the options does not stop A launching it. Null on a menu with
     * no such verb, where confirm with nothing picked does nothing at all rather than guessing.
     */
    val primaryId: String? = null,
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
    // Set on the media columns' collapsed Add submenu. Its items carry the SAME ids the Add rows
    // used to, so activating one re-enters the ordinary row-select path rather than repeating it.
    val isAddMenu: Boolean = false,
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
    // Set on a book row's options menu.
    val bookFileId: String? = null,
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
    /**
     * Starts a new group, drawn as a heading ABOVE this row.
     *
     * A property of the first row rather than a row of its own, so the cursor cannot land on a
     * heading and nothing downstream has to know to skip it. A fourteen-row game menu was one
     * undifferentiated column where "Remove from Library" sat two rows under "Icon Display".
     */
    val heading: String? = null,
    /**
     * In the menu, dispatchable by id, never drawn and never walked.
     *
     * The rail is the one list the cursor reads, so a hidden row simply is not in it. What it
     * still is, is the ONE definition of what its id does: Play is not a row anyone should have
     * to find any more — confirm launches the game — but keeping the entry means the press runs
     * the same handler the row ran, instead of a second path to the same verb that can drift from
     * it. See [XMBContextMenu.primaryId].
     */
    val hidden: Boolean = false,
)

// Drives the shared text-input dialog. Creating a collection is the default; the optional
// targets repurpose it for renames and the game Edit Title / Edit Note actions.
data class CollectionNameDialogState(
    val title: String,
    val initialText: String = "",
    // What is in the field right now. Hoisted rather than kept in the composable so the gamepad
    // path can confirm with it: A has to reach the same string the user can see.
    val text: String = initialText,
    val forGameId: Long? = null,
    // When set, confirming renames this collection instead of creating a new one.
    val renameCollectionId: Long? = null,
    // When set, confirming saves a display-title override for this game (blank resets it).
    val editTitleGameId: Long? = null,
    // When set, confirming saves this game's note (blank clears it).
    val editNoteGameId: Long? = null,
    // When set, confirming hands the text to Quick Search rather than making a collection.
    val quickSearch: Boolean = false,
    // The prompt's own hint and commit label. Defaulted to the collection wording, because that
    // is what this dialog started as and still mostly is; a repurposed prompt that kept saying
    // "e.g. RPGs, Currently Playing" under a Quick Search title would be lying on screen.
    val placeholder: String = "e.g. RPGs, Currently Playing",
    val confirmLabel: String = "Save",
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
    /** Null when the row has nothing to add — most presets are just their name and their colour. */
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
}

// this stays a sealed interface so the drill/back plumbing keeps a stable type.


// ── Settings hierarchy ────────────────────────────────────────────────────────
// The Settings category root shows the Android system-settings leaf plus these six nested L1
// sections. Selecting a section drills into the shared two-pane flyout (same interaction model
// as Music/Video/Photo); selecting an L2 row inside it opens the existing settings screen
// overlay. L2 row ids ARE screen route ids — SettingsNavHost resolves them, so legacy direct
// callers keep working during migration.

// The L2 rows of a section. Ids must be unique inside the list (list keys + cursor restore) and
// distinct from every section id (the select handler routes section ids to the flyout and
// everything else to activeSettingsScreen — see SettingsHierarchyTest).
// ── Fullscreen music browser (Settings-style, searchable) ───────────────────────
// Opened from the "Music" and "Playlist" root items as a fullscreen overlay (not the inline XMB
// list). Rows reuse XMBItem so the same row visuals/actions apply: tracks play, playlists drill in,
// plus Create Playlist / Add Tracks action rows.

sealed interface MusicBrowserView {
    data object AllMusic : MusicBrowserView
    data object Playlists : MusicBrowserView
    data class Playlist(val id: Long, val name: String) : MusicBrowserView
    // Artists and Albums list groups; Artist and Album list one group's tracks. [key] is what the
    // tracks were grouped on, which is what the drill-in filters by -- never the display name.
    data object Artists : MusicBrowserView
    data object Albums : MusicBrowserView
    data class Artist(val name: String, val key: String) : MusicBrowserView
    data class Album(val name: String, val key: String) : MusicBrowserView
}

/** True for the two views that list groups rather than tracks. */
internal val MusicBrowserView.listsGroups: Boolean
    get() = this == MusicBrowserView.Artists || this == MusicBrowserView.Albums

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

/**
 * The library search overlay.
 *
 * [rows] is what is on screen: already matched, already labelled, ready to render. The raw
 * libraries are NOT held here -- they are read once when the overlay opens and kept in a field,
 * because a search that re-queried the database on every keystroke would make typing feel like
 * the device was thinking about it.
 */
data class SearchState(
    val scope: SearchScope,
    val query: String = "",
    val rows: List<XMBItem> = emptyList(),
    val selectedIndex: Int = 0,
    // Bumped to snap the list back to the top when the query changes.
    val scrollToTopToken: Int = 0,
    // True once the libraries have been read. Until then the overlay says so rather than
    // claiming an empty library.
    val loaded: Boolean = false,
    /**
     * How many columns the grid measured, so UP and DOWN step a whole real row.
     *
     * [SEARCH_GRID_COLUMNS] is the value for the window before the first measurement lands, and
     * the number the handheld measures anyway.
     */
    val columns: Int = SEARCH_GRID_COLUMNS,
)

// Drives the "New / Rename Playlist" text dialog. When [forTrackId] is set, the freshly created
// playlist immediately receives that track.
data class PlaylistNameDialogState(
    val title: String,
    val initialText: String = "",
    // Live field contents, hoisted for the same reason as CollectionNameDialogState.text.
    val text: String = initialText,
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

/**
 * [androidx.compose.runtime.Immutable] because the ViewModel only ever REPLACES this object with
 * `copy`; it never mutates a list it has already published.
 *
 * Without the annotation the Compose compiler sees `List`/`Map` fields, judges the whole class
 * unstable, and `XMBShell` becomes unskippable -- its entire body re-executing on every cursor
 * step and, while music plays, twice a second when nothing visible changed. The contract is a
 * promise the writer has to keep: nothing in here may be mutated in place after publication.
 */
@androidx.compose.runtime.Immutable
/** Newest-first artwork per media column, for the card art grids to slice. */
data class MediaCovers(
    val music: List<String> = emptyList(),
    val video: List<String> = emptyList(),
    val photo: List<String> = emptyList(),
    val books: List<String> = emptyList(),
)

data class XMBUiState(
    // ── Horizontal axis: platforms (SD cards) + utility tabs ──────────────
    val categories: List<Category> = emptyList(),
    val selectedCategoryIndex: Int = 0,
    val platformGameCounts: Map<String, Int> = emptyMap(),
    // Total real games (content_type = GAME) across all platforms — the "All Games" count.
    val allGamesCount: Int = 0,
    /** Newest-first cover art per Games-root card, keyed by that card's own item id. */
    val cardFanCovers: Map<String, List<String>> = emptyMap(),
    /**
     * The covers inside each SHELF, by card id. A map of its own, deliberately.
     *
     * These used to live in [cardFanCovers] and were written by two flows — observeCategories
     * rebuilds that map wholesale on every library change, so it wiped the shelf entries the
     * shelf flow had just put there, and the shelves drew glyphs instead of covers. Two writers
     * and one map, where only one of them replaced.
     *
     * One owner now: observeShelfContents writes all five, including Favorites.
     */
    val shelfFanCovers: Map<String, List<String>> = emptyMap(),
    // Count of favorited entries — drives the Games-root "Favorites" item visibility.
    val favoritesCount: Int = 0,
    // Games flagged missing by the reconciler. Tracked separately because every other count here
    // comes from queries that filter is_missing = 0, so missing rows are invisible to them.
    val missingCount: Int = 0,
    /**
     * How many games carry each mark, and how many arrived since the library started counting.
     *
     * The shelves hide themselves on these, card by card, and the Shelves column hides itself when
     * every one of them is empty — see [shelfCards]. Counts rather than lists, because the column
     * only needs to know whether there is anything to look at; the lists are read when you open one.
     */
    val playStateCounts: Map<PlayState, Int> = emptyMap(),
    val recentlyAddedCount: Int = 0,
    val selectedPlatformId: String? = null,
    // When non-null, the Games category is showing the contents of a user collection.
    val selectedCollectionId: Long? = null,
    // User-created collections, ordered, shown under "All Games" in the Games root.
    val collections: List<GameCollection> = emptyList(),
    // Music drill-down: which Music sub-screen is open (Root shows the static items + All Music).
    val musicNav: MusicNav = MusicNav.Root,
    val musicFolders: List<com.psplauncher.core.domain.model.MusicFolder> = emptyList(),
    /**
     * Newest-first artwork for each media column's rows to slice, four per row.
     *
     * One pool per column rather than one list per row: a column's rows are cuts of the same
     * library, so they draw from the same art and are told apart by their offset. See gridSliceAt.
     */
    val mediaCovers: MediaCovers = MediaCovers(),
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
    /**
     * The A–Z rail, up only while a shoulder is held or a finger is on it.
     *
     * Deliberately NOT part of the overlay partition below. The rail does not cover the crossbar
     * — it stands beside the list the way the action pills do, the clock stays, the hint bar stays
     * and the column keeps drawing — so it is not a screen that [otherBlockingOverlay] has to
     * account for. What it does take is the D-pad, and that is an explicit branch in
     * [dispatchGamepadAction] rather than a new entry in a list of things that block.
     */
    val letterJump: LetterJumpState? = null,
    // Non-null while drilled into a Games sub-item (a platform card, All Games, Favorites, or a
    // collection): the parent's label, which drives the two-pane "flyout" listing (parent on the
    // left, children in a centre-locked column on the right). Null = normal single-column list.
    val drillTitle: String? = null,
    // The current category's sibling items (All Games / Favorites / collections / memory cards),
    // shown as the flyout's left icon column (PSP-style); [drillSiblingIndex] is the one currently
    // drilled into, which sits centred on the arrow. Empty when not drilled in.
    val drillSiblings: List<XMBItem> = emptyList(),
    val drillSiblingIndex: Int = 0,
    // Bumped on a sort cycle, alongside selectedItemIndex = 0.
    //
    // It no longer reaches XMBItemList: that list is absolute-offset layout rather than a scroll
    // container, so it has nothing to scroll, and the parameter it used to be passed to was never
    // read. The comment here claimed otherwise for as long as that was true. What actually returns
    // the crossbar to the top is the `selectedItemIndex = 0` written with it. The token is still
    // live for the music browser, which IS a LazyColumn (see MusicBrowserScreen).
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
    /**
     * Nothing has been pressed for a while.
     *
     * Raised by the same poller that raises [showContextMenuHint] and off the same clock — one
     * idle source of truth, so the wave settling and the hint appearing cannot disagree about
     * whether anyone is there. It is NOT that flag: the hint is gated on the focused row having a
     * context menu, and the wave does not care what the cursor is on.
     */
    val idle: Boolean = false,
    // True when the user has been idle inside the App Drawer with a controller — the drawer's own
    // contextual hint bar (see shouldShowAppDrawerHint). Deliberately a separate flag from
    // showContextMenuHint: the drawer is a blocking overlay, so the XMB pill's gate is false
    // exactly while the drawer is open; AppDrawerScreen renders its own pill from this flag.
    val showAppDrawerHint: Boolean = false,
    // True when the user has been idle on the Sound settings screen with a controller. Settings
    // overlays are blocking by design, so this needs its own idle flag rather than reusing the XMB
    // context-menu hint (whose blocking-overlay gate would always suppress it).
    val showSettingsHint: Boolean = false,
    // User setting (Display ▸ Button Hints). When false the hints never show.
    val contextMenuHintEnabled: Boolean = ControllerHintPolicy.DEFAULT_ENABLED,
    // User-configured pause before the hints appear, clamped by ControllerHintPolicy. Zero, the
    // default, means they do not hide at all.
    val contextMenuHintDelaySeconds: Float = ControllerHintPolicy.DEFAULT_DELAY_SECONDS,
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
    /** Draw the wave over a custom wallpaper rather than letting the wallpaper replace it. */
    val waveOverWallpaper: Boolean = false,
    /**
     * The accent derived from the current wallpaper, or null when there is no wallpaper or it
     * could not be read. Tints the wave when it is drawn over that wallpaper — see XmbBackground.
     */
    val wallpaperAccent: Long? = null,
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
    // The launch disc, on screen between confirming a film, book or track and the thing opening.
    // Games are not here: they already have GameBoot, and two presentations for one launch is one
    // too many. See [awaitDiscHandOff].
    val discCeremony: DiscCeremonyState? = null,
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
    /**
     * Where Back should return to instead of the Settings root — set only when the setup wizard
     * sent you to a real settings screen.
     *
     * Settings has no back stack: Back means "up to the root", which is right for a rail you
     * walked in through. It is wrong for the wizard's Make It Yours page, whose four rows open
     * Theme, Sound, Boot and Layout as an excursion — landing at the Settings root from there
     * drops you out of a flow you are ten steps into.
     *
     * One level deep on purpose. A real stack is what the rail is deliberately not, and the only
     * journey that needs to come back is this one.
     */
    val settingsReturnTo: String? = null,
    // The drilled-into Settings L1 section — non-null while its two-pane flyout shows the L2 rows,
    // null at the flat section root. Deliberately NOT part of hasBlockingOverlay: the flyout is
    // XMB foreground, so input keeps driving the item list exactly like every other drill.
    // Settings ▸ Controller ▸ Left Backs Out. Mirrored from ControllerLayoutRepository so both the
    // XMB's own LEFT and the Settings overlay's read one value. Default true matches the pref's.
    val leftBacksOut: Boolean = true,
    val pendingSettingsAction: GamepadAction? = null,
    val activeAppDrawerFilter: String? = null,
    val pendingDrawerAction: GamepadAction? = null,
    /**
     * A character typed while the App Drawer is open, for its search box to take.
     *
     * The drawer's `searchActive` is local Compose state — it is drawer business and the crossbar
     * has no opinion about it — so a keystroke cannot be handed to it directly. It rides the same
     * one-shot channel pendingDrawerAction uses: set here, consumed there, cleared on consumption.
     */
    val pendingDrawerTypedChar: String? = null,
    val pendingGameDetailAction: GamepadAction? = null,
    val activeGameId: Long? = null,
    // True when the Game Detail screen should fire its Play action as soon as the game loads —
    // set by direct-launch confirms and the Options menu's "Launch Game" entry; cleared on close.
    val activeGameAutoLaunch: Boolean = false,
    /**
     * A [com.psplauncher.feature.xmb.ui.detail.DetailAction] name to fire as Game Detail opens.
     *
     * The Details submenu lists actions that LIVE on that screen — the Artwork Studio, the
     * metadata preview, the manual viewer, a re-scrape — each of which is a piece of that
     * screen's own state and cannot simply be run from the crossbar. So the row opens the screen
     * already doing the thing, the way autoLaunch opens it already launching, rather than landing
     * you on a page you then have to navigate.
     *
     * Edit Title and Edit Note are NOT here: their dialog is the crossbar's own, so those two
     * never open the screen at all.
     */
    val activeGameAction: String? = null,
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
    /**
     * The one video worth offering to resume, or null when there is nothing part-watched.
     *
     * 6b's row is "Video · resume", and resume is the whole point: a film you finished is not a
     * thing to continue, and one you never started has nothing to continue from.
     */
    val resumeVideo: com.psplauncher.core.domain.model.Video? = null,
    // Play as soon as the detail page has the film, instead of sitting on its Play button. Only
    // search sets it: everywhere else the detail page IS the destination, and only search is a
    // place you went to reach one specific thing. Mirrors [activeGameAutoLaunch].
    val activeVideoAutoPlay: Boolean = false,
    val pendingVideoDetailAction: GamepadAction? = null,

    // ── Photo ─────────────────────────────────────────────────────────────
    val photoNav: PhotoNav = PhotoNav.Root,
    val booksNav: BooksNav = BooksNav.Root,
    /**
     * The last book you opened from PFP, for the Books column's "Continue reading" row.
     *
     * Only ever set by [markBookOpened], which fires once the reader has actually accepted the
     * intent — so a book whose reader refused it never leads the column.
     */
    val continueBook: com.psplauncher.core.domain.model.Book? = null,
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
    val renameAppText: String = "",         // live field contents, hoisted so A can confirm it

    // ── Create-collection text dialog ─────────────────────────────────────
    val collectionNameDialog: CollectionNameDialogState? = null,

    // ── Create/rename-playlist text dialog ────────────────────────────────
    val playlistNameDialog: PlaylistNameDialogState? = null,

    // ── "Add Tracks" picker (inside a playlist) ───────────────────────────
    val musicTrackPicker: MusicTrackPickerState? = null,

    // ── Fullscreen searchable music browser (Music / Playlist) ─────────────
    val musicBrowser: MusicBrowserState? = null,
    val search: SearchState? = null,

    // ── Simple read-only info dialog (e.g. file location) ──────────────────
    val infoDialog: InfoDialogState? = null,

    // ── One-time "finish setting up your Windows Library" prompt ───────────
    // Raised by the pin workflow when a PC shortcut arrived before setup was complete
    // (docs/windows-library-refactor-plan.md section 3); consumed on first XMB open.
    val showWindowsSetupPrompt: Boolean = false,

    // ── Launch recovery sheet (B1) ─────────────────────────────────────────
    // Non-null while the recovery sheet should be drawn over the shell.
    val launchRecovery: com.psplauncher.feature.launcher.LaunchRecoveryRequest? = null,
    /** Which recovery button the cursor is on. Reset to 0 — the remedy — with every new sheet. */
    val launchRecoveryCursor: Int = 0,

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
    // "Fade By Distance": when true, unselected XMB icons and rows dim by how far they sit from
    // (selection still reads by icon size and label). Default false = today's dimming.
    val fadeByDistance: Boolean = true,
    /** "Card Art Grid": a Games-root card's tile is a 2x2 of what is inside it. */
    val cardArtGrid: Boolean = true,
    /** Whether the Recent shelf carries apps too. Off by default — see the settings row. */
    val recentsIncludeApps: Boolean = false,
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
     * "Backdrop & Tint" (Artwork settings). When false the shell keeps the theme's own wallpaper
     * and colour whatever the cursor is on, which is the XMB's own idea of how a system looks.
     */
    val itemBackdropEnabled: Boolean = true,
    /**
     * The focused row's own colour, read out of its artwork. Null on a row with no art of its
     * own, on art with no dominant hue, and for the moment before it has been read -- in all
     * three the XMB keeps the user's theme, which is what it looked like before this existed.
     *
     * Every category feeds this, not only Games: see [XMBItem.backdropArt].
     */
    val focusedItemAccentArgb: Long? = null,
    /**
     * The image behind the focused row: the first of its art candidates that actually decodes.
     *
     * NOT simply artworkUri, which is what this used to read. 125 of the 147 games on the real
     * library name an internal artwork path that no longer exists, so reading the named slot
     * showed the wallpaper for all of them and the per-game backdrop only ever appeared for the
     * five with a content:// one.
     */
    val focusedItemBackdrop: String? = null,
    /**
     * Which page the hover panel was walked to with L1/R1, and the game it was walked to ON.
     *
     * The pair is the whole mechanism. Moving the cursor to another game returns the panel to its
     * logo page — the crossbar goes back to looking like the crossbar — and that reset is derived
     * from [panelPageGameId] rather than written at every place the cursor can move. There are
     * many such places and a reset missed at one of them is a panel stuck open on the wrong
     * game's art, which is exactly the kind of thing that only shows up on a handheld.
     *
     * Read [effectivePanelPage], never these two directly.
     */
    /** Which media the home shelf is showing. Cycled with X — see RecentFilter. */
    val recentFilter: RecentFilter = RecentFilter.ALL,
    /**
     * Whether the home shelf's column of other recents is on screen.
     *
     * Away by default. The page's subject is the thing you last opened, and a permanent column of
     * smaller copies of the same idea beside it is the launcher talking over its own screen.
     * LEFT brings it in, RIGHT or BACK puts it away, and the cursor walks the recents either way
     * -- hidden, the cards are still there, they are just not drawn.
     */
    val recentRailVisible: Boolean = false,
    /**
     * Which 9i pill under the focused row has the cursor, or null while it is on the row itself.
     *
     * Keyed to the row's id, so moving the column cursor invalidates it on its own.
     */
    val pillCursor: PillCursor? = null,
    /**
     * The notification sheet, pulled down from the strip.
     *
     * It lived in the shell as local Compose state, which made it a thing only a finger could
     * open and only a finger could close: BACK went to the dispatcher, found no branch for it and
     * opened the App Drawer with the sheet still on screen. State here so a button can open it,
     * BACK can close it, and it counts as the overlay it is.
     */
    val notificationsOpen: Boolean = false,
    /**
     * Where the cursor is in the open sheet, over [noticeFocusables].
     *
     * Index 0 is the media row when there is one. Clamped on read rather than reset on write,
     * because the list under it changes on its own — a notification the posting app clears while
     * the sheet is open shortens it with nobody pressing anything.
     */
    val noticeCursor: Int = 0,
    /** The device's own notifications, live. Empty when access has not been granted. */
    val androidNotices: List<AndroidNotice> = emptyList(),
    /**
     * What the sheet's top row offers when nothing is playing: the last game you were in.
     *
     * The row is one slot with two tenants. Music wins it while there is music, because a
     * transport you can reach is worth more than a shortcut you already have on the shelf below.
     */
    val resumeGame: Game? = null,
    val panelPage: DetailPanelPage = DetailPanelPage.LOGO,
    val panelPageGameId: Long? = null,
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

    /**
     * The row the hover panel is drawn for, or null when there is no panel.
     *
     * Gated on a real game WITH backdrop art: the region has always needed something behind it,
     * and a panel floating on the bare wallpaper reads as a stray card.
     */
    /**
     * The row the hover panel is describing, or null when the panel should not be drawn.
     *
     * The isRealGame half is a CROSSBAR rule, not a panel rule: a panel floating over a folder
     * row or a playlist in a normal column reads as a stray card. The home page is the opposite
     * case — the hero IS the page, and a track, a book and a film each deserve one as much as a
     * game does, so there the only question is whether the row has art to draw.
     *
     * One property with one explicit condition rather than two properties that would drift.
     */
    val hoverPanelItem: XMBItem?
        get() = focusedItem?.takeIf {
            (it.isRealGame || onLastPlayedHome) && it.backdropArt.isNotEmpty()
        }

    /**
     * What the hover panel is showing, INCLUDING which pages it offers.
     *
     * Computed here rather than at each consumer because there are two and they must not
     * disagree: XMBShell draws the panel and its strip, and stepHoverPanelPage decides where
     * L1/R1 land. They did disagree — the shell passed the approved snap and the walk did not,
     * so the strip drew a Video tab that R1 stepped straight over. One property, no second
     * chance to forget an argument.
     */
    /**
     * The page the panel is actually on: the walked-to page while the cursor is still on the game
     * it was walked to on, and the logo page the moment it is not.
     */
    val effectivePanelPage: DetailPanelPage
        get() = if (panelStripOpen) panelPage else DetailPanelPage.LOGO

    /**
     * Has the user opened the strip on the game under the cursor right now?
     *
     * The same pair [effectivePanelPage] reads, named once and shared, because THREE things now
     * branch on it and they must not disagree: the shell decides whether to draw the panel at all,
     * the row decides whether to swap its subtitle for the scraped facts, and L1 decides whether
     * it is stepping a page or closing the strip. Three private copies of one condition is the
     * shape that drifts.
     *
     * Closed is the resting state, and it is a real state rather than the absence of one: the
     * crossbar shows the row's name and nothing else, and L1 from the logo page returns to it.
     */
    val panelStripOpen: Boolean
        get() = panelPageGameId != null && panelPageGameId == hoverPanelItem?.gameId

    /**
     * Standing on the home page — the state in which the crossbar is hidden and the screen is the
     * game you were last playing.
     *
     * The drill exclusion is not decoration. The shell reads this to decide whether to draw the
     * page or the bar, and a drilled sub-item under a hidden bar would be a screen LEFT can no
     * longer back out of.
     */
    /**
     * True while the Recent shelf is standing in for the crossbar.
     *
     * It replaces the bar rather than sitting beside it — but only when it has something to
     * replace it WITH. A shelf with nothing on it used to take the whole screen anyway, so a
     * fresh install landed on "Nothing played yet." with no crossbar, no categories and no
     * Settings in sight: a dead end you had to know a button to leave.
     *
     * Gated on the ALL filter being empty rather than on [currentItems], so filtering to a medium
     * you have none of keeps the shelf and says "No recent games." Only "nothing played at all"
     * hands the screen back to the bar.
     *
     * Every consequence follows from this one flag — input routing, the launch spine, the filter
     * names — so the empty case gets the ordinary crossbar whole rather than a shelf wearing
     * pieces of one.
     */
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

    /**
     * True iff L1/R1 would actually move the hover panel — the focused game has scraped enough
     * to have a second page.
     *
     * Read off hoverPanelContent rather than asked separately, so the hint and the walk cannot
     * disagree: the pages the strip draws ARE the pages the shoulders step through, and a pill
     * offering Pages on a game with nothing but its logo would be a prompt for a press that
     * does nothing.
     */
    /** True iff an X press would cycle the home shelf's media filter rather than sort a list. */
    val canFilterRecents: Boolean
        get() = onLastPlayedHome

    val hoverPanelHasPages: Boolean
        get() = (hoverPanelContent?.pages?.size ?: 0) > 1

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

    /**
     * True when the only thing over the XMB is one the status strip and hint bar draw ON TOP of.
     *
     * Two of them now: the context rail and the notification sheet. Both hug an edge, both leave
     * the crossbar visible behind them, and both need the bar to keep naming what their own
     * presses do — the sheet especially, since the strip is the thing you pressed to open it and
     * it pads itself clear of it on purpose.
     *
     * They are NOT removed from [hasBlockingOverlay], which also decides input routing and the
     * idle cues, and both of those should still treat these as overlays. Two readings of one
     * list, which is why this is a second property and not a second list.
     *
     * Was `contextRailOnly`, when the rail was the only one.
     */
    val overlayKeepsChrome: Boolean
        get() = (activeContextMenu != null || notificationsOpen) && !otherBlockingOverlay

    /**
     * The rows the sheet's cursor walks, in the order it walks them.
     *
     * The LAUNCHER column is deliberately absent. Its rows are reports of finished work — there is
     * nothing to do to one — so a cursor that stopped on them would be a cursor that sometimes
     * does nothing when you press it. The media row is here when there is something playing,
     * because that row has controls; the system's notifications are here because they open.
     */
    val noticeFocusables: List<NoticeFocus>
        get() = buildList {
            if (musicPlayback.track != null || resumeGame != null) add(NoticeFocus.Media)
            androidNotices.take(NOTICE_ROWS).forEach { add(NoticeFocus.Notice(it.key)) }
        }

    /** The focused row, or null when the sheet has nothing that can be acted on. */
    val focusedNotice: NoticeFocus?
        get() = noticeFocusables.let { rows ->
            if (rows.isEmpty()) null else rows[noticeCursor.coerceIn(0, rows.lastIndex)]
        }

    /**
     * The shelves that currently have something on them, in the order they are drawn.
     *
     * ONE definition, read by the column's contents AND by whether the column exists at all. Two
     * lists — "what to draw" and "is there anything to draw" — would be the pair that disagrees
     * the first time a shelf is added, and this file has already produced that bug twice today.
     *
     * Favorites leads because it is the shelf that predates all of this. Recently Added is last
     * because it is a different kind of answer: the other four are things you said about a game,
     * and that one is a fact about when it arrived.
     */
    val shelfCards: List<ShelfCard>
        get() = buildList {
            if (favoritesCount > 0) add(ShelfCard.Favorites(favoritesCount))
            PlayState.entries.forEach { state ->
                playStateCounts[state]?.takeIf { it > 0 }?.let { add(ShelfCard.Marked(state, it)) }
            }
            if (recentlyAddedCount > 0) add(ShelfCard.RecentlyAdded(recentlyAddedCount))
        }

    /**
     * Whether a column is on the bar and can be stepped onto.
     *
     * ONE predicate, read by the bar's drawing and by left/right stepping. Shelves is the only
     * column that answers false: with nothing marked and nothing recently added there is nothing
     * on it, so it is not drawn and not landed on, and it arrives on its own the moment a shelf
     * has something.
     *
     * The category stays in [categories] either way. Removing it would renumber every column to
     * its right the instant a game was marked, and the selection is an index into that list — you
     * would mark a game in Music and find yourself in Video.
     */
    fun categoryReachable(category: Category): Boolean =
        category.id != BuiltInCategory.SHELVES || shelfCards.isNotEmpty()

    /**
     * Whether ENTER opens the App Drawer here instead of confirming.
     *
     * ON THE CROSSBAR ONLY, and that limit is the whole design. Enter is bound to SELECT, so it is
     * the keyboard's confirm; taking it away everywhere would leave a keyboard user unable to open
     * a folder, start a game or pick a rail row. On the crossbar it is the press with the least to
     * do and the drawer is what gets reached for, and selection there is a finger's job on this
     * device.
     *
     * Excluded, each for its own reason: a drilled-in list, where confirm opens the thing under
     * the cursor; the Last Played shelf, where confirm launches what you were playing and is the
     * main verb on the screen; the pill row, where confirm runs the pill; and every overlay, where
     * something else already owns the keyboard.
     */
    val enterOpensAppDrawer: Boolean
        get() = search == null &&
            !hasBlockingOverlay &&
            !isInSubItem &&
            !onLastPlayedHome &&
            activePillIndex() == null

    // True whenever something is layered over the main XMB. The gamepad dispatcher uses this
    // as a final guard so D-Pad/A never drives the category bar or item list behind an overlay.
    val hasBlockingOverlay: Boolean
        get() = otherBlockingOverlay || activeContextMenu != null || notificationsOpen

    /**
     * Everything that covers the XMB EXCEPT the two the chrome stays on top of: the context rail
     * and the notification sheet.
     *
     * Split out rather than copied: [hasBlockingOverlay] and [overlayKeepsChrome] are two readings of
     * one list, and a second copy of twenty-five conditions is a second copy that stops agreeing
     * the first time a screen is added to one of them.
     *
     * It is now the OR of the two halves below, and that is the guard. The status strip is drawn
     * over one half and covered by the other, so the question "does the clock show here" has to be
     * answered for every screen that covers the XMB. Keeping one list and partitioning it means a
     * new screen cannot be added to neither half — which, had this been a second list, is exactly
     * what would happen: the screen would work, and the clock would quietly be wrong on it.
     */
    private val otherBlockingOverlay: Boolean
        get() = chromeOverlay || fullscreenOverlay

    /**
     * The screens that cover the XMB but KEEP the status strip on top of them.
     *
     * These are the app's own chrome — a drawer, a settings tree, a search, a page about a thing.
     * You are still in the launcher on them, and the launcher is what owns the clock, the battery
     * and the notification corner. Before this they each covered the strip, so walking into the
     * App Drawer lost the time and walking out found it again.
     *
     * Both pickers are here too, and they are the ones that read as a surprise: they fill the
     * screen, which is what put them in the other half. Filling the screen is not owning it. Each
     * is a list you browse for as long as it takes to find what you came for — the drawer's job,
     * in the drawer's clothes — not a box you answer and dismiss.
     *
     * [gamePickerCategoryId] is the narrower of the two: it is reached only from the "Add Games"
     * row at the foot of a gaming category's column, and the built-in Game category is not one of
     * those — that column has its own builder and never draws the row. So it is only ever seen on
     * a category the user made. That is a reason it was easy to overlook, not a reason for the
     * clock to behave differently on it.
     */
    private val chromeOverlay: Boolean
        get() = activeSettingsScreen != null ||
            appPicker != null ||
            gamePickerCategoryId != null ||
            activeAppDrawerFilter != null ||
            activeGameId != null ||
            activeAppId != null ||
            search != null

    /**
     * The screens that take the whole screen and cover the strip with it.
     *
     * Two kinds, and both want the room more than they want the clock: something playing or
     * presenting full-bleed (the video player, the photo viewer, the boot and disc ceremonies),
     * and the modal dialogs and the SMALL pickers — a colour wheel, a name box — where a strip
     * drawn on top would be chrome floating over a box that is deliberately the only thing you
     * can touch.
     *
     * "Picker" is not the test; being a box you answer is. Both the installed-app picker and the
     * game picker are named like these and belong with the drawer instead — see [chromeOverlay].
     * What is left under this name really is answer-and-dismiss: a colour wheel, a name box, a
     * confirmation.
     */
    private val fullscreenOverlay: Boolean
        get() = showBootSequence ||
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

    /**
     * Whether the strip's contents still describe what is under it.
     *
     * The strip carries two kinds of thing. The clock, the battery and the notification corner are
     * facts about the DEVICE and are true on any screen. The sort label, the shoulder and
     * left/right hints and the home shelf's filter row are facts about the CROSSBAR — and once a
     * chrome screen covers it, they describe a list the user is no longer looking at. The drawer
     * showed "Title" over a grid it does not sort.
     *
     * The rail and the notification sheet are not covers in that sense: you are still standing on
     * the crossbar with something open in front of it, so its context is still yours. That is what
     * [overlayKeepsChrome] already means, and this is its third reader.
     */
    val stripShowsXmbContext: Boolean
        get() = !hasBlockingOverlay || overlayKeepsChrome

    /**
     * Whether the status strip is drawn right now.
     *
     * Everywhere except a full-screen overlay — so the crossbar, the context rail, the
     * notification sheet, and every chrome screen above. The strip is a fixture of the launcher
     * rather than a part of the crossbar, which is what "those should be global" asked for.
     */
    val statusStripVisible: Boolean
        get() = !fullscreenOverlay
}

/**
 * The launch disc on screen, and the cover art printed on its face.
 *
 * Art only — no title, no id, nothing the overlay could act on. The disc reports two moments and
 * the ViewModel decides what they mean, which is what keeps one overlay usable by three unrelated
 * launch paths.
 */
data class DiscCeremonyState(val art: Any?)

/**
 * How many result cards a search row holds.
 *
 * Lives here, not in SearchScreen, because the ViewModel's cursor has to step by exactly the
 * number the grid draws — up and down move a row. Two copies of this number is a cursor that
 * jumps two rows or half of one, and it would only be wrong on one axis.
 *
 * Seven, not five, because the card is a fixed 2:3 and so the column count IS the row height: at
 * five the covers were 475px tall on this screen, which pushed the second line of a long title
 * and the whole subtitle off the bottom of the panel. Widen the grid and the cards get shorter.
 *
 * **Seven is now the answer on the reference panel rather than the rule.** Because the count sets
 * the card's size, a fixed seven meant a wider screen drew BIGGER cards rather than more of them
 * — the one reading of "responsive" this app does not want, since the card's size is the user's
 * through the scale slider. [SearchScreen] measures its own width and divides by
 * SearchScreen's own SEARCH_TILE_TARGET_WIDTH; on the 821dp handheld that still comes to
 * seven. This constant
 * stays as the fallback for a window that has not been measured yet.
 */
const val SEARCH_GRID_COLUMNS = 7


/** Bounds on the derived count. One column is not a grid; past a dozen a 2:3 card is a stamp. */
const val SEARCH_GRID_MIN_COLUMNS = 3
const val SEARCH_GRID_MAX_COLUMNS = 12

enum class XMBItemType {
    STANDARD,
    ALL_GAMES,
    FAVORITES,
    /**
     * A card in the Shelves column: Favorites, Playing, Completed, Backlog, Recently Added.
     *
     * ONE type for all five rather than five. Which shelf it is comes from the row's id through
     * [shelfCardFor], so the thing that DRAWS a shelf and the thing that OPENS it read the same
     * answer from the same place — five enum values would be a second list to keep in step with
     * the first, which is the pair that has bitten this file twice.
     */
    SHELF,
    // The Missing bucket: games whose ROM file was gone on the last trustworthy scan. Sits beside
    // All Games / Favorites and only appears when something is actually missing.
    MISSING,
    MEMORY_CARD,
    COLLECTION,
    // An artist or an album in the music browser: a row standing for a set of tracks.
    MUSIC_GROUP,
    // The "Artists" and "Albums" section rows at the Music root.
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
    // Library (books) rows.
    LIBRARY_SHELVES,
    LIBRARY_READER,
    LIBRARY_FOLDER,
    LIBRARY_BOOK,
    // The "Series" root row, and each series folder inside it.
    LIBRARY_SERIES,
    PHOTO_FILE,
    CAMERA,
    // The Search row a library column leads with, and Network's Quick Search.
    SEARCH,
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
/**
 * The library this row BELONGS to, from its own type — not from where the cursor happens to be.
 *
 * Search needed this first: its results span every library, so "which column owns this?" could
 * never be answered by the current category. The Last Played shelf now has the same shape — a
 * music track, a book and a film sitting in a column that is none of those — and asking the
 * cursor there gave the wrong answer, which is why Y did nothing on those rows.
 *
 * Null for rows that belong to no library in particular (apps, folders, action rows); callers
 * fall back to the current category for those, which is the old behaviour exactly.
 */
internal fun XMBItem.owningCategory(): String? = when (type) {
    XMBItemType.VIDEO_FILE -> BuiltInCategory.VIDEO
    XMBItemType.PHOTO_FILE -> BuiltInCategory.PHOTO
    XMBItemType.LIBRARY_BOOK -> BuiltInCategory.LIBRARY
    XMBItemType.MUSIC_TRACK -> BuiltInCategory.MUSIC
    else -> if (gameId != null) BuiltInCategory.GAMES else null
}

/**
 * The category a context menu for this row should be built from: the row's own library when it
 * has one, otherwise wherever the cursor is.
 *
 * One function so hasContextMenu below and the three open*ContextMenu openers cannot disagree —
 * the pill promising a menu the press then refuses is the exact failure this pair produces.
 */
internal fun XMBItem.menuHostCategory(currentCategoryId: String?): String? =
    owningCategory() ?: currentCategoryId

fun XMBItem.hasContextMenu(state: XMBUiState): Boolean {
    val categoryId = menuHostCategory(state.categories.getOrNull(state.selectedCategoryIndex)?.id)
    return when {
        // Music tracks / Now Playing / playlists / music-apps.
        categoryId == BuiltInCategory.MUSIC && (
            id == XMBViewModel.NOW_PLAYING_ITEM_ID ||
                type == XMBItemType.MUSIC_TRACK ||
                (type == XMBItemType.PLAYLIST && playlistId != null)
        ) -> true
        // Video files / libraries / playlists / video-apps.
        categoryId == BuiltInCategory.VIDEO && (
            (type == XMBItemType.VIDEO_FILE && id.startsWith("vid_")) ||
                (type == XMBItemType.VIDEO_FOLDER && id.startsWith("vlib_")) ||
                (type == XMBItemType.PLAYLIST && playlistId != null)
        ) -> true
        // Books. The Library column and, through owningCategory, wherever else a book is listed.
        categoryId == BuiltInCategory.LIBRARY &&
            type == XMBItemType.LIBRARY_BOOK && id.startsWith("book_") -> true
        // Photo files / libraries / photo-apps.
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
 * Pure decision: should the context-menu hint be visible right now? Top-level so unit tests can
 * exercise it without a ViewModel instance. Gates:
 *  - Display ▸ Button Hints is on ([XMBUiState.contextMenuHintEnabled]);
 *  - no blocking overlay, no open context menu ([XMBUiState.hasBlockingOverlay],
 *    [XMBUiState.activeContextMenu]);
 *  - the pill has something true to say — the focused item has a context menu
 *    ([XMBUiState.focusedItemHasContextMenu]) or the current list can sort
 *    ([XMBUiState.canSortCurrentList]);
 *  - the configured delay has elapsed, which by default is zero
 *    ([ControllerHintPolicy.DEFAULT_DELAY_SECONDS]) so the pill simply stays up.
 *
 * It is no longer gated on the last input having come from a controller. That gate made sense
 * while the pill was a legend: a touch user does not need to be told which face button sorts.
 * The prompts are tappable now, so the pill is a control, and the gate hid it precisely from the
 * people who would press it — worse, touching it set the flag that hid it, so it could not be
 * used twice. The glyph still earns its place on a touch screen, because it says which button
 * does the same thing next time.
 *
 * Deliberately NOT gated on [XMBUiState.isInSubItem]: drilled-in items (the game flyout, a
 * library's files) have context menus and can sort, so that gate hid the hint exactly where a
 * new user is most likely to need it.
 */
/**
 * Whether the hints hide themselves on input and wait for a pause, or simply stay up.
 *
 * This exists because "always shown" is not just a delay of zero. Both [XMBViewModel.markTouchInput]
 * and [XMBViewModel.onUserInteraction] eagerly clear the hint flags on every input, deliberately:
 * while the hints were idle chrome, leaving them up for as much as one poll tick after a press was
 * a visible lag. With a zero delay that same eagerness turns into a flicker on every single
 * button press, because the poller puts them straight back 500ms later. So the eager hide is
 * conditional on there being something to hide for.
 */
val XMBUiState.hintsAutoHide: Boolean
    get() = contextMenuHintDelaySeconds > 0f

/**
 * The three hint flags recomputed from their own gates, with no pause required.
 *
 * With auto-hide off the flags are no longer a timer's output; they are a function of the state
 * you are in. The idle poller can only raise them on its next tick, which is up to
 * [XMBViewModel.IDLE_HINT_POLL_MS] away, and that showed up on the device as the bar missing for
 * half a second after every press. The input markers call this instead, so the flags are right on
 * the same frame as the input and the poller is left as a backstop.
 *
 * Safe to call on a state whose flags are stale: none of the three gates reads a hint flag.
 */
internal fun XMBUiState.withHintsShownNow(): XMBUiState = copy(
    showContextMenuHint = shouldShowContextMenuHint(this, 0L),
    showAppDrawerHint = shouldShowAppDrawerHint(this, 0L),
    showSettingsHint = shouldShowSettingsHint(this, 0L),
)

fun shouldShowContextMenuHint(state: XMBUiState, idleMs: Long): Boolean =
    state.contextMenuHintEnabled &&
        // The context rail is the one blocking overlay the pill survives — "the header and hints
        // still show on top of the context screen". It used to be excluded twice over, once here
        // and once inside hasBlockingOverlay, which is why overlayKeepsChrome exists — and why
        // the reading of it is a named property now rather than four copies of one expression.
        state.stripShowsXmbContext &&
        // Every capability the pill can advertise has to be listed here, or the press works and
        // nothing on screen says so. canFilterRecents is the reason this is a list and not a
        // pair: filtering the home shelf down to a medium you have none of empties it, which
        // removes the focused item AND the context menu, so without this the one control that
        // gets you back out would disappear at exactly the moment you need it.
        (state.focusedItemHasContextMenu || state.canSortCurrentList || state.canFilterRecents) &&
        idleMs >= (state.contextMenuHintDelaySeconds * 1_000f).toLong()

/**
 * Pure decision: should the App Drawer's contextual controller hint bar be visible right now?
 * Top-level so unit tests can exercise it without a ViewModel instance. Gates:
 *  - Display ▸ Button Hints is on;
 *  - the App Drawer is actually open ([XMBUiState.activeAppDrawerFilter] non-null);
 *  - no context menu is up (one can never sit over the drawer, but the gate stays symmetric with
 *    [shouldShowContextMenuHint]);
 *  - the idle delay [idleMs] has elapsed.
 *
 * Deliberately separate from [shouldShowContextMenuHint]: the drawer is a blocking overlay
 * ([XMBUiState.hasBlockingOverlay]), so the XMB pill's gate is false whenever the drawer is open —
 * and this gate is true only then. Both share the same idle clock and the
 * contextMenuHintEnabled / contextMenuHintDelaySeconds settings, so Display ▸ Button Hints
 * toggles the drawer hint too.
 */
fun shouldShowAppDrawerHint(state: XMBUiState, idleMs: Long): Boolean =
    state.contextMenuHintEnabled &&
        state.activeAppDrawerFilter != null &&
        state.activeContextMenu == null &&
        // The sheet can be pulled down over the drawer now, and it takes every key when it is
        // open. A bar naming Launch and Options under a sheet that will not deliver either is a
        // bar telling the user something untrue -- the shell's own bar, which follows whatever
        // owns the screen, is drawn on top instead.
        !state.notificationsOpen &&
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
    // The description and the ROM path, carried on the row so the hover panel can draw its Info
    // page without a query. Nothing else reads them; they are here because the alternative is a
    // database round trip on every D-pad press.
    val description: String? = null,
    val romPath: String? = null,
    /** Milliseconds this game has been played; 0 when it has never been launched from here. */
    val totalPlayTimeMillis: Long = 0L,
    /**
     * Cover art of the newest games INSIDE this card, newest first.
     *
     * Read by two things: the fan on the right of the crossbar, and the card's own 2x2 art grid.
     * Empty for anything that is not a Games-root card, and for a card whose games have no
     * artwork — both consumers are previews of what is in there, and there is nothing to preview.
     *
     * BOTH are gated on the Card Art Grid setting, and they have to be: the setting means "show
     * what is inside this card rather than its console icon", so a fan of those same covers
     * drawn beside an icon is the setting applied to one of its two halves. The grid asked and
     * the fan did not, until it was noticed on the device.
     */
    val insideCovers: List<String> = emptyList(),
    val gameId: Long? = null,
    val platformId: String? = null,
    val collectionId: Long? = null,     // set on COLLECTION rows in the Games root
    val iconKey: String? = null,        // catalog icon key for COLLECTION rows (null = default memory-card art)
    val accentColor: Long? = null,
    val isFavorite: Boolean = false,
    /** Playing / Completed / Backlog as its enum name, or null for unmarked. See [PlayState]. */
    val playState: String? = null,
    val isAndroidApp: Boolean = false,
    // True for contentType GAME rows — real games open the Game Detail page on select, even when
    // package/shortcut-backed (Android/Windows gaming apps). Standard apps launch directly.
    val isRealGame: Boolean = false,
    val packageName: String? = null,
    // Host app's launcher-shortcut id for harvested per-game entries; launched via LauncherApps.
    val shortcutId: String? = null,
    // Captured legacy INSTALL_SHORTCUT launch intent (Intent.toUri); launched by parsing it.
    val launchIntentUri: String? = null,
    // Music: the owning folder's id, and the track's SAF uri + mime (all on MUSIC_TRACK rows).
    val musicFolderId: String? = null,
    // What a MUSIC_GROUP row was grouped on -- see MusicGroup.key. Carried rather than parsed
    // back out of the row id, because an artist called "Unknown" and the unknown-artist bucket
    // would then be the same string.
    val musicGroupKey: String? = null,
    val mediaUri: String? = null,
    val mimeType: String? = null,
    // Square album-cover art (on MUSIC_TRACK rows); a file:// uri cached during scan, may be null.
    val coverUri: String? = null,
    /**
     * How far through this row you are, 0..1, or null when the medium has no notion of it.
     *
     * Video and the playing MUSIC track have one. A book does not: it opens in somebody else's
     * reader, which never tells us where it got to — so 6c's "page 62 of 190" has no source here
     * and the Books column gets no scrubber. A game has no length to be a fraction of.
     *
     * Kept as a fraction rather than a position and a duration because the two are only ever used
     * together and a row that carried one without the other could not say anything at all.
     */
    val progressFraction: Float? = null,
    /** "23 min left" — the words beside [progressFraction]. Null whenever that is. */
    val progressLabel: String? = null,
    // Playlist id on PLAYLIST rows.
    val playlistId: Long? = null,
    // Text-only row: never draws a leading icon/tile and always shows its label, regardless of
    // selection: the row reads as plain text plus a reason rather than an icon and a title.
    val textOnly: Boolean = false,
    // The row's kind: what it draws as and how the column treats it (STANDARD tile, navigation
    // row, playlist, and so on).
    val type: XMBItemType = XMBItemType.STANDARD,
) {
    /**
     * The art this row's shell colour and backdrop are read from, best first, or empty when the
     * row has no art of its own.
     *
     * One list for every category on purpose. Games had this treatment and the other libraries
     * did not, which meant the shell took a photograph's colour on the Games row and dropped
     * back to the flat theme the moment you moved to Music or Photo. Whether a row can colour
     * the shell is now a question about the row, not about which category it came from.
     *
     * [MEMORY_CARD_ASSET_URI] is excluded deliberately: it is the bundled folder icon that every
     * category's navigation rows share, so reading a colour from it would tint the whole shell
     * the same grey on every folder, everywhere, which reads as the theme having broken.
     */
    val backdropArt: List<String>
        get() = listOfNotNull(artworkUri, heroUri, coverUri, boxArtUri, iconUri)
            .filter { it.isNotBlank() && it != XMBViewModel.MEMORY_CARD_ASSET_URI }

    /**
     * The art the home shelf's card draws, best first, or null when the row has none.
     *
     * Same SET of slots as [backdropArt], different ORDER, and that is the whole point of them
     * sitting together. backdropArt is choosing a landscape background so it leads with
     * artworkUri; this is choosing a portrait cover so it leads with the two cover slots. When a
     * new art slot is added to this row, both want it — and only one of them having it is how a
     * music track and a video ended up drawing their titles as text while their covers sat
     * unread on the row.
     */
    val shelfCoverArt: String?
        get() = listOfNotNull(boxArtUri, coverUri, artworkUri, heroUri, iconUri)
            .firstOrNull { it.isNotBlank() && it != XMBViewModel.MEMORY_CARD_ASSET_URI }

    /**
     * A clear logo will actually be drawn for this row.
     *
     * ONE predicate, read by both halves of a pair that used to disagree. The row hid its title
     * whenever a logo existed, on the premise that the logo IS the identity; the shell only drew
     * the logo when there was also background art. A game with a logo and no art therefore got
     * neither -- a tile with nothing naming it, and nothing logged.
     *
     * Gated on [backdropArt] rather than on `artworkUri` alone because that column is not what
     * decides the backdrop any more: the shell shows the first art candidate that actually
     * decodes, so the logo has to ask the same question.
     */
    val hasVisibleLogo: Boolean
        get() = !logoUri.isNullOrBlank() && backdropArt.isNotEmpty()
}

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
/**
 * Puts [text] into whichever name prompt is open (pure — unit-tested).
 *
 * One function rather than four intents because exactly one of these can be open at a time and the
 * text is the same kind of thing in each.
 *
 * The branch ORDER is the load-bearing part, and it mirrors the order of the prompt branches in
 * XMBViewModel.handleGamepadAction. Those branches confirm by reading the field this function
 * writes, so if the two orders ever disagree, A confirms a string the user never typed. A prompt
 * added to one must be added to the other.
 */
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
    private val gameLaunchPreferences: com.psplauncher.core.data.repository.GameLaunchPreferences,
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
    // context-menu hint: on unless Display ▸ Button Hints is off or a delay is configured, and
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
    private var enabledCards: List<MemoryCard> = emptyList()
    private var baseThemeColors: PFPColors = DefaultPFPColors

    // Background work is surfaced to the Android notification bar, not an in-app tray.
    private val taskNotifier = BackgroundTaskNotifier(context)

    init {
        gamepadInputHandler.scope = viewModelScope
        // The player tells us when a track actually starts; the stamp is written here, where the
        // repositories live. See MusicPlayerController.onTrackStarted for why it is not setQueue.
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
            // The remedy for a revoked storage grant: Library Manager is where a ROM root is
            // re-granted, which is the thing the failure message asks the user to do.
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


    /**
     * A game row's meta line: the system, then when you last played it.
     *
     * "Game Boy Advance · Today, 1:49 PM" — and the redesign's note on it was "This is what is
     * missing from what we have now. if it hasnt been played it defaults to just the system and
     * publisher". So a game you have never started falls back to its publisher, and one with
     * neither a play record nor a publisher is left as the bare system name rather than trailing a
     * separator with nothing after it.
     *
     * THIS REPLACES "Platform (Emulator)". The emulator's friendly name used to sit in parentheses
     * here; it is the same string on every row of a console's column, which is a poor use of the
     * one line a row gets, and it is still on the game's detail screen where it is actually a
     * question you might be asking.
     */
    private fun gameMetaLabel(g: Game): String = gameMetaLine(
        platform = platformCache[g.platformId]?.name ?: g.platformId,
        lastPlayedAt = g.lastPlayedAt,
        publisher = g.publisher,
    )

    // Music folders drive the Music category's root list; the default player is cached for launch.
    /**
     * Keeps each media column's art pool current, for the rows' 2x2 grids.
     *
     * One combine for all four, and each flow is a LIMIT query returning URIs — twenty-four
     * strings per column, not every track and photo in the library. The Games grid gets its covers
     * off the snapshot observeCategories already holds; media has no such snapshot, so this is the
     * one place that reads art for them.
     *
     * The rows are rebuilt on arrival, but only for the column that is actually on screen: a media
     * column's sections are pure functions of this state, so nothing redraws for a library the
     * user is not looking at.
     */
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

                    // The fan's covers, from the SAME snapshot the counts come from — the games
                    // are already in hand here, so this costs a sort and no query.
                    //
                    // "Newest" is date_added, then id — see fanCoversOf, which owns the rule.
                    // Rows predating that column read 0 and tie, so an untouched library falls
                    // through to exactly the id order this used to use on its own.
                    // for insertion dates.
                    //
                    // See fanCoversOf for why sorted-then-mapped-then-taken is the whole rule.
                    fun fanOf(games: List<Game>): List<String> = fanCoversOf(games)
                    val realGames = displayGames.filter { it.contentType == GameContentType.GAME }
                    val fanCovers = buildMap<String, List<String>> {
                        put(ALL_GAMES_ITEM_ID, fanOf(realGames))
                        // NO SHELF ENTRIES HERE. This map is rebuilt whole on every library
                        // change, so anything another flow put in it would be wiped — which is
                        // exactly what happened to the shelves. They own shelfFanCovers instead.
                        realGames.groupBy { it.platformId }
                            .forEach { (pid, list) -> put(cardItemId(pid), fanOf(list)) }
                    }

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
                        cardFanCovers = fanCovers,
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

    private fun currentCategory(): Category? = _uiState.value.currentCategoryOrNull()

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
                BuiltInCategory.SHELVES -> {
                    // Two states, one branch: the column's own list of shelves, or the games on
                    // whichever shelf is open. selectedPlatformId carries which — the same field a
                    // console card uses, because a shelf IS a folder of games as far as everything
                    // downstream is concerned, and giving it a second mechanism would give the
                    // crossbar a second way to be "inside something".
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
                        // Hidden games are honoured on every shelf, for the reason the Last Played
                        // shelf gives: hiding a game is how someone says they do not want to see
                        // it, and a shelf that resurfaced it anyway would be a poor joke.
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
                        // NOT gameSorted, for the reason Last Played is not: when it arrived is
                        // the whole of what this shelf says, and a user sort would delete it.
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
                    // Deliberately NOT gameSorted. Recency is the whole point of this list, and a
                    // user sort would destroy it; activeSortModes declines to offer one because
                    // the category is not a gaming category (see BUILT_IN_CATEGORIES).
                    //
                    // Hidden games follow ALL_GAMES: hiding a game from All Games is how a user
                    // says "I do not want to see this", and honouring it everywhere except the one
                    // shelf that resurfaces whatever they last opened would be a poor joke.
                    //
                    // Four libraries, not one. Each keeps its own recency column and each is read
                    // through its own flow; mergeRecents does the interleaving and the filtering,
                    // and is pure so the ordering can be proven without a device.
                    //
                    // The rows are built by the SAME mappers the Music, Library and Video columns
                    // use, so the context menu works and each row looks like itself. Selecting one
                    // did NOT come for free: the columns open their own rows in their own
                    // selection handlers, which never run here. See the media fallthrough in the
                    // confirm dispatch.
                    var keepCursor = keepCursorOnRow
                    combine(
                        gameRepository.observeRecentlyPlayed(RECENTLY_PLAYED_LIMIT),
                        musicRepository.observeRecentlyPlayedTracks(RECENTLY_PLAYED_LIMIT),
                        bookRepository.observeRecentlyOpenedBooks(RECENTLY_PLAYED_LIMIT),
                        videoRepository.observeRecentlyWatched(),
                        // The filter and the app rows arrive together, as one flow, so this
                        // stays a TYPED five-argument combine. The six-flow overload hands back an
                        // Array<Any?> to index and cast, which is the shape that has already cost
                        // this file two bugs today — a list and an index that agree until they do
                        // not.
                        recentFilterAndApps(),
                    ) { games, tracks, books, videos, filterAndApps ->
                        val (filter, appRows) = filterAndApps
                        // The play queue for a track opened from this shelf is the shelf's own
                        // music. openMusicPlayerForItem reads it and RETURNS SILENTLY when it is
                        // empty, which is what made A do nothing on a Recent music row: the rows
                        // were built by the Music column's mapper, but its queue was never filled.
                        currentMusicTracks = tracks
                        val visibleGames = games.notHiddenAt(HideLocationType.ALL_GAMES)
                        mergeRecents(
                            games  = visibleGames.map { it.lastPlayedAt ?: 0L }.zip(visibleGames.toXmbItems()),
                            // Collapsed: a run of tracks from one album is one album row. See
                            // recentMusicRows -- an evening with a record should not be the
                            // whole shelf.
                            music  = tracks.recentMusicRows(),
                            books  = books.map { it.lastOpenedAt ?: 0L }.zip(bookItems(books)),
                            videos = videos.map { it.lastWatchedAt ?: 0L }.zip(videos.toVideoItems()),
                            apps   = appRows,
                            filter = filter,
                            limit  = RECENTLY_PLAYED_LIMIT,
                        )
                    }.collect { items ->
                        // No placeholder row. Every other column needs one because the crossbar
                        // has to draw something; this column IS the home page, which owns its own
                        // empty state. A fake row here became the "focused item", so the footer
                        // read "Music: Nothing played yet" — a placeholder being reported as
                        // though the user had played it. Seen on the device.
                        publishGameItems(items, keepCursor)
                        keepCursor = true
                    }
                }
                BuiltInCategory.ANDROID -> {
                    _uiState.update { it.copy(currentItems = ANDROID_ITEMS) }
                }
                BuiltInCategory.SETTINGS -> {
                    // Root rows while no section is open; the drilled-into section's L2 rows otherwise.
                    // Always reset the cursor against the newly selected list so nested Settings
                    // cannot retain an out-of-range index from the parent list.
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
                        // Quick Search leads the Network column: a launcher with no browser of
                        // its own still has to be able to look something up, and a search box is
                        // the shortest route to a browser on a device where opening one and
                        // reaching its address bar is four presses.
                        val lead = if (category.id == NETWORK_CATEGORY_ID) listOf(quickSearchItem()) else emptyList()
                        // "Add Apps" is offered on every app section so the same picker serves
                        // Video, Music, Network, App Store and custom categories alike.
                        _uiState.update { it.copy(currentItems = lead + items + addAppsItem()) }
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

    /**
     * A library column: its own rows, then its scoped Search row.
     *
     * Five columns have to agree about where that row goes, and until now each one placed it
     * itself -- which is how Music ended up without one at all. One function decides the position,
     * so moving it again is one edit and cannot half-land.
     */
    private fun libraryColumn(body: List<XMBItem>, scope: SearchScope): List<XMBItem> =
        body + librarySearchItem(scope)

    /**
     * The Search row every library column ends with.
     *
     * Scoped, so searching from inside Video looks only at video. It TRAILS the column: Select
     * already opens an all-library search from anywhere, so this row is the narrow second path,
     * not the thing you came to the column for.
     *
     * Music has one too now. It was the lone exception, on the grounds that its "Music" and
     * "Playlist" rows open a browser that is itself searchable -- which is true, and still left
     * Music as the only column with no visible way to search it.
     */
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

    // ── Music ───────────────────────────────────────────────────────────────────

    /** Rebuilds the Music root list in place, relocating the cursor to the same row id so a shape
     *  change (the "Now Playing" row appearing/disappearing) never moves the visible selection. */
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


    /**
     * Every "Add ..." this category offers right now, in the order they belong in.
     *
     * One function rather than rows scattered through the builders, because the same list has to
     * answer two questions: what the column ends with, and what the Add submenu contains. Two
     * copies would drift the moment one gained an entry.
     */
    private fun musicAddActions(): List<XMBItem> = buildList {
        // Getting-started prompt: opens Settings → Music. Drops away once a root has been scanned
        // (even if it found no tracks), since the root is then managed in Settings.
        if (_uiState.value.musicFolders.none { it.lastScannedAt != null }) add(addMusicFolderItem())
        add(addMusicAppsItem())
    }

    /**
     * The whole Music root: its sections, then the installed music apps, then Add Music Apps.
     *
     * The apps used to live one level down behind a "Music Apps" row. Media first, then the tools
     * for it, is the order a user scans in, and it costs one press fewer to reach Spotify.
     */
    private suspend fun musicRootItems(): List<XMBItem> =
        libraryColumn(
            _uiState.value.musicRootSections() + musicAppItems() + collapseAddRows(musicAddActions()),
            SearchScope.MUSIC,
        )


    /** The Add submenu's entries for whichever column the cursor is in, or empty elsewhere. */
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
                activeContextMenu = XMBContextMenu(
                    title = "Add",
                    items = actions.map { XMBContextMenuItem(it.id, it.title) },
                    isAddMenu = true,
                )
            )
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

    // Music Apps: the apps the user added (stored under a dedicated pseudo-category so they don't
    // mix with the built-in Music category), plus an "Add Music Apps" row.
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

    private fun categoryDisplayName(id: String): String = _uiState.value.categoryDisplayNameOf(id)

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
            // The resume candidate, for the row 6b puts at the top of the column.
            //
            // Recently watched, narrowed to the ones actually part-way through: a position past
            // zero, a known duration, and not so near the end that "resume" would drop you into
            // the credits. RESUME_DONE_FRACTION is that last guard — a player writes the position
            // as you watch, so a film watched to the end sits at ~100% and would otherwise lead
            // this column forever, offering to resume something already finished.
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


    /** Every "Add ..." the Video column offers (see [musicAddActions]). */
    private fun videoAddActions(): List<XMBItem> = buildList {
        if (_uiState.value.videoLibraries.none { it.lastScannedAt != null }) add(addVideosItem())
        add(addVideoAppsItem())
    }

    /** The whole Video root: its sections, then the installed video apps, then Add Video Apps. */
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

    // Through the shared mapper, so a library listing and the Video root's resume row cannot
    // drift into two ideas of what a video row is.
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

    // Playlist list: one row per playlist + a "Create Playlist" row.
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

    // Handles A/Cross on any Video row. Returns true when [item] is a Video row it owns.
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
        // Video-app rows launch the app.
        item.packageName != null -> {
            menuSound.play(MenuSound.LAUNCH)
            launchAppWithDisc(item.packageName, item.shelfCoverArt)
            true
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

    // Stable key for whatever list [s] currently shows.
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

    // Long-press options for a video playlist row: open / rename / delete.
    private fun openVideoPlaylistContextMenu(playlistId: Long, name: String) {
        val items = videoPlaylistContextMenuItems()
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(name, items, videoPlaylistId = playlistId)) }
    }

    // Opens the △ options menu for a Video row. Returns true when [item] is a video row it owns
    // (a video file, a library card, a playlist row, or a video-app row), so the generic
    // Y/long-press handler can stop.
    private fun openVideoContextMenu(item: XMBItem): Boolean {
        // The ROW's library, not the cursor's — see XMBItem.menuHostCategory.
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

    // Options for a single video file. Favorite label + "Remove from this Playlist" reflect the
    // current state/context. Fetches the video first so the favorite label is correct.
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
            // Clears the watch stamp only. resume_position_ms stays, so a part-watched film taken
            // off the shelf still resumes where it was if you open it again — the shelf asks
            // "when", the position asks "where", and they are not the same question.
            "video_remove_recent" -> appAction { videoRepository.clearLastWatched(videoId) }
            "video_remove" -> appAction { videoRepository.removeVideo(videoId) }
        }
    }

    // Second-level menu: the playlists a video can be added to (checkmarks show membership), plus
    // "Create New Playlist". Stays open while toggling so several can be picked at once.
    private fun openVideoPlaylistPicker(videoId: String, selectIndex: Int? = 0) {
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
                    selectedIndex = selectIndex?.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
                    videoPlaylistPickerVideoId = videoId,
                )
            )}
        }
    }

    // Options for a video library card: open, scan, or manage in Settings.
    private fun openVideoLibraryContextMenu(libraryId: String, name: String) {
        val items = videoLibraryContextMenuItems()
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
    // The last book opened from PFP, for the Continue reading row.
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


    /** Every "Add ..." the Library column offers (see [musicAddActions]). */
    private fun booksAddActions(): List<XMBItem> = buildList {
        // Getting-started prompt, gone once a shelf has been scanned even if it found nothing.
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

    /**
     * The whole Library root: its sections, then the installed reader apps, then Add Book Apps.
     *
     * The pinned default reader above stays what it is -- the app a book opens IN. These rows are
     * for launching a reader on its own, and match what Music, Video and Photo already do.
     */
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
                // Same image in both slots on purpose: coverUri draws the list tile, artworkUri is
                // the shell's hover-background slot. XMBShell already crossfades artworkUri behind
                // whatever row is selected, for any item type, so a book gets the treatment games
                // get without a second rendering path.
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

    /** Returns true when [item] was a Library row and has been handled. */
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
        // A reader row launches its app, the same way a music or video app row does.
        item.packageName != null -> {
            menuSound.play(MenuSound.LAUNCH)
            launchAppWithDisc(item.packageName, item.shelfCoverArt)
            true
        }
        else -> false
    }

    /** Hands the book to the chosen reader, or to the chooser when none is set. */
    private fun openBook(bookId: String) {
        menuSound.play(MenuSound.LAUNCH)
        viewModelScope.launch {
            val book = bookRepository.getBook(bookId) ?: return@launch
            // After the lookup, before the hand-off: a book that is not in the library never gets
            // a disc, for the same reason a game that cannot launch never gets a GameBoot.
            awaitDiscHandOff(book.coverUri)
            val error = bookIntentResolver.launch(book, _uiState.value.defaultReader)
            if (error != null) {
                _uiState.update { it.copy(infoDialog = InfoDialogState(title = book.displayTitle, message = error)) }
                return@launch
            }
            // Only once the reader actually took it. A book whose reader is missing or refused the
            // intent was not opened, and a shelf that listed it would be pointing at a dead end.
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
    // Covered by QUERY_ALL_PACKAGES, declared and reasoned in app/src/main/AndroidManifest.xml.
    // Lint warns per call site because a library module cannot see the app module's manifest.
    @Suppress("QueryPermissionsNeeded")
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


    /** Every "Add ..." the Photo column offers (see [musicAddActions]). */
    private fun photoAddActions(): List<XMBItem> = buildList {
        if (_uiState.value.photoLibraries.none { it.lastScannedAt != null }) add(addPhotoLibraryItem())
        add(addPhotoAppsItem())
    }

    /** The whole Photo root: its sections, then the installed photo apps, then Add Photo Apps. */
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

    // Photo Apps: the apps the user added (stored under a dedicated pseudo-category so they don't
    // mix with the built-in Photo category), plus an "Add Photo Apps" row. Mirrors Music/Video Apps.
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

    // One folder card per Album, drillable into its photos. The root folder is managed in
    // Settings → Photo, so there is no add row here.
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

    // Handles A/Cross on any Photo row. Returns true when [item] is a Photo row it owns.
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
        // Photo-app rows launch the app.
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
    /**
     * A book's options menu. Books had none at all — not on the Last Played shelf and not in the
     * Library column either — so Y simply did nothing on them anywhere in the app.
     *
     * Deliberately short. Everything here is an operation BookRepository already exposes; a menu
     * that offers what a library cannot do is worse than a menu with four rows in it.
     */
    private fun openBookContextMenu(item: XMBItem): Boolean {
        if (item.menuHostCategory(currentCategory()?.id) != BuiltInCategory.LIBRARY) return false
        if (item.type != XMBItemType.LIBRARY_BOOK || !item.id.startsWith("book_")) return false
        val bookId = item.id.removePrefix("book_")
        viewModelScope.launch {
            // Read for the same reason the music and video menus do: whether the row carries a
            // stamp decides whether Remove from Recent is offered at all.
            val onShelf = runCatching { bookRepository.getBook(bookId) }
                .getOrNull()?.lastOpenedAt != null
            val items = bookContextMenuItems(hasOpenStamp = onShelf)
            _uiState.update {
                it.copy(activeContextMenu = XMBContextMenu(item.title, items, bookFileId = bookId))
            }
        }
        return true
    }

    private fun handleBookAction(bookId: String, itemId: String) {
        when (itemId) {
            "book_open" -> openBook(bookId)
            // No explicit reload: observeRecentlyOpenedBooks is a Room Flow and invalidates itself
            // on the write, the same as the game and track paths.
            "book_remove_recent" -> appAction { bookRepository.clearBookLastOpened(bookId) }
            "book_remove" -> appAction { bookRepository.removeBook(bookId) }
        }
    }

    private fun openPhotoContextMenu(item: XMBItem): Boolean {
        // The ROW's library, not the cursor's — see XMBItem.menuHostCategory.
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

    // Options for a single photo row. Viewing-related options (zoom, rotate, wallpaper) live in
    // the fullscreen viewer's own Options menu; the list row only opens/removes.
    private fun openPhotoFileContextMenu(photoId: String, title: String) {
        val items = photoFileContextMenuItems()
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
        val items = photoLibraryContextMenuItems()
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
                // Artists and Albums are the same query as All Music, grouped. No second DAO
                // method: the browser already holds every track, and a phone-sized library
                // groups in memory faster than it would round-trip the database.
                MusicBrowserView.Artists, MusicBrowserView.Albums ->
                    musicRepository.observeAllTracks().collect { tracks ->
                        browserRawTracks = tracks; rebuildBrowserGroupRows()
                    }
                is MusicBrowserView.Artist -> musicRepository.observeAllTracks().collect { tracks ->
                    // Not a filter written here: an artist row can stand for a split credit line,
                    // and MediaColumns owns the one answer to which tracks a row means.
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
        currentMusicTracks = filtered   // the play queue is exactly what's on screen
        val baseRows = when {
            filtered.isNotEmpty() -> filtered.toMusicItems()
            q.isNotBlank()        -> listOf(browserNoResultsItem())
            isPlaylist            -> listOf(emptyPlaylistItem())
            else                  -> listOf(emptyAllMusicItem())
        }
        val rows = if (isPlaylist) baseRows + addTracksItem() else baseRows
        // The mode's name alone. "Sort: Title" spent two thirds of the centre of the strip
        // naming the control rather than its value, next to a clock that does not say "Time:".
        val label = _uiState.value.musicSortMode.label
        _uiState.update { it.copy(musicBrowser = it.musicBrowser?.copy(
            rows = rows,
            selectedIndex = state.selectedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0)),
            sortLabel = label,
        )) }
    }

    /**
     * The Artists / Albums list: one row per group, filtered by the search box on the group name.
     *
     * No sort pill. The sort modes are Title / Artist / Album / Date Added, none of which means
     * anything to a list that is already one row per artist, so [sortLabel] stays null and the
     * pill and the X prompt disappear with it.
     */
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
        when {
            state.view is MusicBrowserView.Playlists -> rebuildBrowserPlaylistRows()
            state.view.listsGroups -> rebuildBrowserGroupRows()
            else -> rebuildBrowserTrackRows()
        }
    }

    // ── Library search ──────────────────────────────────────────────────────────
    //
    // One overlay, two ways in: a Search row at the top of a library scopes it to that library,
    // and the Select button searches everything. Scope is the only difference.
    //
    // The libraries are read ONCE, when the overlay opens, and kept here. Re-querying on every
    // keystroke would put a database round trip between a key press and the letter appearing,
    // which on a handheld reads as the device struggling.
    private var searchGames: List<com.psplauncher.core.domain.model.Game> = emptyList()
    /**
     * Installed apps, for the global search.
     *
     * Read once when the search opens, like every other library here. Apps are the one thing on
     * this device you cannot already find another way — a game has its column, a track has the
     * music browser, and an app has a drawer you have to know is behind Back.
     */
    private var searchApps: List<com.psplauncher.feature.appbar.InstalledApp> = emptyList()
    private var searchVideos: List<com.psplauncher.core.domain.model.Video> = emptyList()
    private var searchPhotos: List<com.psplauncher.core.domain.model.Photo> = emptyList()
    private var searchBooks: List<com.psplauncher.core.domain.model.Book> = emptyList()
    private var searchTracks: List<com.psplauncher.core.domain.model.MusicTrack> = emptyList()

    /**
     * Whether a typed character should open search right now.
     *
     * The rule a desktop launcher has always had: start typing and you are searching. It only
     * holds while NOTHING else owns the keyboard — a search already open, a rename dialog, the app
     * drawer's own box, a settings text field — because in those a letter is a letter and stealing
     * it would make the field unusable.
     *
     * hasBlockingOverlay covers all of them in one read, which is the point of that predicate; the
     * context rail is excluded from it separately, so it is named here. A letter over an open rail
     * should search, not be swallowed by a menu that has no text in it.
     */
    /**
     * Whether ENTER should open the App Drawer instead of confirming.
     *
     * ON THE CROSSBAR ONLY, and that limit is the whole design. Enter is bound to SELECT, so it
     * is the keyboard's confirm — taking it away everywhere would leave a keyboard user unable to
     * open a folder, start a game or pick a rail row. On the crossbar it is the press with the
     * least to do and the drawer is what the owner reaches for; selection there is a finger's job
     * on this device.
     *
     * Excluded, each for its own reason: a drilled-in list, where confirm opens the thing under
     * the cursor; the Last Played shelf, where confirm launches what you were playing and is the
     * main verb on the screen; and every overlay, where something else already owns the keyboard.
     */
    fun enterOpensAppDrawer(): Boolean = _uiState.value.enterOpensAppDrawer

    /**
     * A printable character arrived. Route it to whichever search is the right one.
     *
     * Two searches, one gesture: the drawer has its own box and its own results, and typing while
     * it is open should land there rather than closing it and opening the global one behind it.
     * Anywhere else on the XMB the character opens the global search carrying itself.
     *
     * Returns false when nothing wanted it, so the Activity lets it fall through to whatever
     * text field is really focused.
     */
    fun onTypedCharacter(ch: String): Boolean {
        if (_uiState.value.activeAppDrawerFilter != null) {
            _uiState.update { it.copy(pendingDrawerTypedChar = ch) }
            return true
        }
        if (!typeToSearchAllowed()) return false
        openSearchTyping(ch)
        return true
    }

    /** The drawer took the character. One-shot, like every pending action beside it. */
    fun onDrawerTypedCharConsumed() {
        _uiState.update { it.copy(pendingDrawerTypedChar = null) }
    }

    fun typeToSearchAllowed(): Boolean {
        val state = _uiState.value
        return state.search == null && state.stripShowsXmbContext
    }

    /** Opens search already carrying [query] — the character that opened it. */
    fun openSearchTyping(query: String) {
        openSearch(SearchScope.ALL)
        onSearchQueryChange(query)
    }

    fun openSearch(scope: SearchScope) {
        menuSound.play(MenuSound.SELECT)
        _uiState.update { it.copy(search = SearchState(scope = scope)) }
        viewModelScope.launch {
            // Only what the scope can actually match. A scoped search never pays for libraries it
            // will not look in, which on a large game library is the difference that matters.
            val wantsGames = scope == SearchScope.ALL || scope == SearchScope.GAMES
            val wantsVideos = scope == SearchScope.ALL || scope == SearchScope.VIDEOS
            val wantsPhotos = scope == SearchScope.ALL || scope == SearchScope.PHOTOS
            val wantsBooks = scope == SearchScope.ALL || scope == SearchScope.BOOKS
            searchGames = if (wantsGames) gameRepository.observeAllGames().first() else emptyList()
            searchVideos = if (wantsVideos) videoRepository.observeAllVideos().first() else emptyList()
            searchPhotos = if (wantsPhotos) photoRepository.observeAllPhotos().first() else emptyList()
            searchBooks = if (wantsBooks) bookRepository.observeAllBooks().first() else emptyList()
            // Music has its own scope now. It used to ride along on the global search only, on the
            // reasoning that the Music column's browser is already searchable -- which is true and
            // was still the wrong call: that search is two presses inside a browser you have to
            // know to open, so Music was the one library column with no visible way to search it.
            val wantsTracks = scope == SearchScope.ALL || scope == SearchScope.MUSIC
            searchTracks = if (wantsTracks) musicRepository.observeAllTracks().first() else emptyList()
            // Apps ride the global search only. There is no Apps scope: the drawer's own box is
            // the scoped search for them, and it is on screen the whole time the drawer is open.
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
        // Drop the snapshots with the overlay. Holding a whole library alive behind a closed
        // screen is the kind of thing that only shows up as a memory graph six months later.
        searchGames = emptyList(); searchVideos = emptyList(); searchPhotos = emptyList()
        searchApps = emptyList()
        searchBooks = emptyList(); searchTracks = emptyList()
        _uiState.update { it.copy(search = null) }
    }

    private fun rebuildSearchRows() {
        val state = _uiState.value.search ?: return
        val q = state.query
        val rows = buildList {
            // APPS LEAD. They used to come last, after five libraries, which put the thing most
            // often searched for at the bottom of a list that is capped per library — so a query
            // matching a dozen games could push an exact app match off the visible rows entirely.
            // Games follow, then the media libraries.
            //
            // The package name is in the haystack as well as the label, because half of what
            // someone remembers about an app is what the store called it.
            searchApps.filter { matchesSearch(q, it.label, it.packageName) }
                .take(SEARCH_RESULTS_PER_LIBRARY)
                .forEach { app ->
                    add(
                        XMBItem(
                            id = "searchapp_${app.packageName}",
                            title = app.label,
                            subtitle = "App",
                            packageName = app.packageName,
                            // Says what it is, so XMBItemList draws its icon. Without this the
                            // row matched no art branch and came out as a bare label — the same
                            // omission the home shelf had, in the second of the two places that
                            // build an app row from scratch.
                            isAndroidApp = true,
                        ),
                    )
                }
            // The platform is in the haystack because the row already PRINTS it: a list that shows
            // you "Castlevania · PlayStation" and then finds nothing for "psx castlevania" is
            // showing you a field it refuses to search. platformCache holds the display name the
            // row uses, so the two cannot disagree about what the console is called.
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
        // Asks the snapshots this search is actually working from, so a scoped search reports the
        // state of ITS library rather than the app's — openSearch only fills the lists its scope
        // reads.
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

    /**
     * Moves the search cursor by [delta] cells, clamped.
     *
     * Clamped rather than wrapped, and deliberately NOT row-aware: stepping right off the end of
     * a row lands on the first cell of the next one, which is how reading order works and what a
     * flat index gives for free. The clamp at both ends is what stops it running off the grid.
     */
    /** The column count the search grid last measured, or the fallback before it has. */
    private fun searchColumns(): Int =
        (_uiState.value.search?.columns ?: SEARCH_GRID_COLUMNS).coerceAtLeast(1)

    /** [SearchScreen] measured its width; keep the cursor's row width in step with the grid's. */
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

    /**
     * Opens whatever the highlighted result is, in the place it lives.
     *
     * The crossbar is moved to the owning column first, so backing out of the thing you opened
     * leaves you where it came from rather than wherever you happened to be standing when you
     * started searching.
     *
     * Playable results PLAY. A search is not browsing -- you typed the name of one thing, so
     * landing on its detail page and having to press A again is a second confirmation of a choice
     * already made. Tracks always did this; games and films now do too. Books and photos do not,
     * because their "detail page" IS the reader and the viewer.
     */
    fun onSearchActivatedAt(index: Int) {
        val state = _uiState.value.search ?: return
        val row = state.rows.getOrNull(index) ?: return
        if (row.type == XMBItemType.EMPTY) return
        _uiState.update { it.copy(search = it.search?.copy(selectedIndex = index)) }

        // AN APP RESULT LAUNCHES, and it is handled before everything below because none of that
        // applies to it: it belongs to no column, so there is no cursor to move first, and the
        // rules about landing you where the thing lives would send you nowhere.
        //
        // Without this the row was findable and dead. searchRowCategory returns null for an app
        // and the function returned right there, so an app could be searched for, could be seen,
        // and could not be opened -- the shape of every "why does nothing happen" report.
        // Named rather than smart-cast: isInstalledApp is an extension property, so the compiler
        // cannot know it implies a non-null packageName. Pulling the value out says the same thing
        // and keeps the predicate in one place.
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
            // launchGameDirectly, not the detail screen's invisible auto-launch. Three of the
            // four callers of that path were moved off it when direct launch was built — "the
            // detail overlay is only an editing surface" — and search was the one left behind,
            // opening a whole screen underneath a launch that never shows it.
            else -> row.gameId?.let { id -> launchGameDirectly(id) }
        }
    }

    /** The column a result belongs to, which is where the cursor is put before opening it. */
    private fun searchRowCategory(row: XMBItem): String? = row.owningCategory()

    private fun selectCategoryById(categoryId: String) {
        val index = _uiState.value.categories.indexOfFirst { it.id == categoryId }
        if (index >= 0) onCategorySelected(index)
    }

    /**
     * A photo is opened inside its album, not on its own.
     *
     * openPhotoViewer reads the album from photoNav, so the cursor is put in that album first --
     * which also means backing out of the viewer lands in the album the photo lives in rather
     * than at the Photo root.
     */
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

    // ── Result rows ─────────────────────────────────────────────────────────────
    // Each carries the id shape its own opener already parses ("vid_", "pho_", "book_", "mt_"),
    // so nothing here invents a second way to identify an entry. The subtitle names the library,
    // because in a global search "Spirited Away" could be a film or a book and the row is the
    // only thing that can say which.

    private fun com.psplauncher.core.domain.model.Game.toSearchRow(): XMBItem = XMBItem(
        id = "search_game_$id",
        title = title,
        subtitle = listOfNotNull("Game", platformCache[platformId]?.name).joinToString("  ·  "),
        // Both art slots, because the card and the shell want different ones: shelfCoverArt reads
        // boxArtUri first for a PORTRAIT cover, backdropArt reads artworkUri first for a LANDSCAPE
        // background. Carrying only artworkUri was why every search result drew the wide grid art
        // squeezed into a 2:3 tile -- the row simply never carried the cover for the card to find.
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
            // A drilled-in view backs out to the list it came from; everything else closes.
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
        // Re-anchor the XMB cursor on the row the browser was opened from, so the reveal is
        // seamless even if the root list changed shape while the browser was open.
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
        item.id == SEARCH_ITEM_ID -> { openSearch(SearchScope.MUSIC); true }
        item.id == ADD_MENU_ITEM_ID -> { menuSound.play(MenuSound.SELECT); openAddMenu(); true }
        item.type == XMBItemType.EMPTY -> true   // not selectable
        item.id == NOW_PLAYING_ITEM_ID -> {
            menuSound.play(MenuSound.SELECT)
            if (_uiState.value.musicPlayback.track != null) _uiState.update { it.copy(musicPlayerVisible = true) }
            true
        }
        // "Music" and "Playlist" open the fullscreen, searchable browser instead of the inline list.
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
        // Music-app rows launch the app. They sit at the root now, so the row's own package is
        // the whole test -- there is no sub-view left to be in.
        item.packageName != null -> {
            menuSound.play(MenuSound.LAUNCH)
            launchAppWithDisc(item.packageName, item.shelfCoverArt)
            true
        }
        else -> false
    }

    // Selecting a song opens the in-app full player, with the on-screen track list as the queue.
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
                    items = nowPlayingContextMenuItems(playback.isPlaying),
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
        // The track is read for one reason: whether it carries a play stamp, which decides
        // whether "Remove from Recent" is offered. The video menu already opens this way.
        val trackId = item.id.removePrefix("mt_")
        viewModelScope.launch {
            val onShelf = runCatching { musicRepository.getTrack(trackId) }
                .getOrNull()?.lastPlayedAt != null
            val items = musicTrackContextMenuItems(playlistId = playlistId, hasPlayStamp = onShelf)
            _uiState.update { it.copy(
                activeContextMenu = XMBContextMenu(
                    title        = item.title,
                    items        = items,
                    musicTrackId = trackId,
                    playlistId   = playlistId,
                )
            )}
        }
    }

    // Options menu for a playlist row: open / rename / add tracks / delete.
    private fun openPlaylistRowContextMenu(playlistId: Long, name: String) {
        val items = playlistRowContextMenuItems()
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(name, items, playlistId = playlistId)) }
    }

    // Second-level menu: the playlists a track can be added to (checkmarks show membership), plus
    // "Create New Playlist". Stays open while toggling so several can be picked at once.
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
                activeContextMenu = XMBContextMenu(
                    title                 = "Add to Playlist",
                    items                 = items,
                    selectedIndex         = selectIndex?.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
                    playlistPickerTrackId = trackId,
                )
            )}
        }
    }

    // Opens the right options (△) menu for a Music item. Returns true when [item] is a music row
    // it owns (track / playlist / music-app), so the generic Y handler can stop. The Now Playing
    // row is consumed without a menu (its options live in the full player).
    private fun openMusicContextMenu(item: XMBItem): Boolean {
        // The ROW's library, not the cursor's — see XMBItem.menuHostCategory.
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
            // No explicit reload: observeRecentlyPlayedTracks is a Room Flow and invalidates
            // itself on the write, the same as the game path.
            "remove_from_recent" -> appAction { musicRepository.clearTrackLastPlayed(trackId) }
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

    /**
     * Steps the home shelf to the next medium, wrapping past Video back to All.
     *
     * Only the state changes: the column's flow already has the filter as one of its combined
     * sources, so the list rebuilds itself through the same merge rather than a second path. The
     * cursor goes back to the top because the row it was on usually is not in the new list.
     */
    /**
     * The shelf's filter, and the apps it may show, as one emission.
     *
     * Apps come from UsageStatsManager through InstalledAppRepository, which is a suspend read
     * rather than a flow, so it is re-read when the app set changes and when the setting is
     * toggled — not on every tick of the four media flows beside it.
     *
     * EMPTY WHEN THE SETTING IS OFF, rather than filtered at the merge. One empty list means ALL
     * and APPS cannot disagree about whether apps are on; two places deciding it is how a filter
     * ends up showing rows the "All" beside it does not.
     *
     * Usage access is a system screen the user can revoke at any time. Without it the timestamps
     * come back empty and every app reads as never used, so they simply do not appear — the
     * setting's own row is where that is explained, not here.
     */
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
                    // Removed from the shelf by hand. Filtered here rather than at the merge for
                    // the reason in this function's doc: one list, so ALL and APPS cannot
                    // disagree about what is on the shelf.
                    .filterNot { isHiddenAt(HiddenPlacement.appKey(it.packageName), HideLocationType.RECENTS) }
                    .sortedByDescending { it.lastUsedAt }
                    .take(RECENTLY_PLAYED_LIMIT)
                    .map { app ->
                        app.lastUsedAt to XMBItem(
                            id = "$RECENT_APP_ID_PREFIX${app.packageName}",
                            title = app.label,
                            subtitle = "App",
                            packageName = app.packageName,
                            // Says what it is. XMBItemList picks its leading art with
                            // `isAndroidApp && packageName != null`, so without this the shelf's
                            // app rows matched no branch and drew with no icon — and the backdrop
                            // behind them had nothing to read either.
                            isAndroidApp = true,
                        )
                    }
                filter to rows
            }

    private fun cycleRecentFilter() =
        setRecentFilter(_uiState.value.let { it.recentFilter.next(it.recentsIncludeApps) })

    /**
     * Pick a filter outright, which is what a tap on its name means.
     *
     * X cycles, and cycling is the only thing a button can do — but the names are all on screen
     * at once, so a finger can say "that one" and should not have to press X three times to get
     * there. Same state change either way; [cycleRecentFilter] is now this with the next one
     * worked out for it.
     */
    fun setRecentFilter(filter: RecentFilter) {
        menuSound.play(MenuSound.SCROLL)
        _uiState.update { it.copy(recentFilter = filter, selectedItemIndex = 0) }
    }

    /**
     * Show or hide the recents rail — the touch equivalent of LEFT and RIGHT on this page.
     *
     * The rail was reachable by the D-pad alone, so on the one screen a new install lands on,
     * touch could see a single item and had no way to reach the rest. Tapping the artwork opens
     * it and tapping the artwork again puts it away, which is the same in-and-out the shoulder
     * presses give.
     */
    fun toggleRecentRail() {
        if (!_uiState.value.onLastPlayedHome) return
        menuSound.play(MenuSound.SYSTEM_BROWSE)
        _uiState.update { it.copy(recentRailVisible = !it.recentRailVisible) }
    }

    private fun cycleSort() {
        // The fullscreen music browser sorts its own track views (not the playlists list).
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
            VideoNav.Root            -> null
        }
        if (videoTitle != null) return videoTitle
        // Photo sub-navigation is a drill-in too.
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
            val sibs = _uiState.value.musicRootSections().filter {
                it.type == XMBItemType.PLAYLIST || it.type == XMBItemType.MEMORY_CARD
            }
            val idx = sibs.indexOfFirst { sib ->
                when (s.musicNav) {
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
            // Root sections: All Videos / Collections / Video Libraries.
            val sibs = _uiState.value.videoRootSections().filter {
                it.type == XMBItemType.MEMORY_CARD || it.type == XMBItemType.VIDEO_COLLECTIONS ||
                    it.type == XMBItemType.VIDEO_LIBRARY
            }
            val idx = sibs.indexOfFirst { sib ->
                when (s.videoNav) {
                    VideoNav.AllVideos   -> sib.type == XMBItemType.MEMORY_CARD
                    VideoNav.Collections -> sib.type == XMBItemType.VIDEO_COLLECTIONS
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
            val sibs = _uiState.value.photoRootSections(cameraAvailable).filter {
                it.type == XMBItemType.MEMORY_CARD || it.type == XMBItemType.PHOTO_ALBUMS
            }
            val idx = sibs.indexOfFirst { sib ->
                when (s.photoNav) {
                    PhotoNav.AllPhotos -> sib.type == XMBItemType.MEMORY_CARD
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

    // Status-bar hint for the current list ("Title"), or null when the list isn't sortable.
    private fun currentSortLabel(): String? {
        val cycle = activeSortContext() ?: return null
        return _uiState.value.sortModeFor(cycle).label
    }

    /**
     * Last Played with nothing in it.
     *
     * Its own message rather than "No games assigned", because nothing can be assigned here and a
     * user told to assign something would go looking for a control that does not exist. An
     * established install starts empty too: last_played_at was never written before 90fe587f, so
     * the shelf fills up as games are played rather than arriving full.
     */
    private fun emptyCategoryItem(category: Category): XMBItem {
        val (message, subtitle) = if (category.isGamingCategory) {
            "No games assigned." to "Add games to this category."
        } else {
            // Only the categories that actually reach this branch. Music, Video, Photo and
            // Library each build their own root above and never fall through to here, so the
            // arms they used to have could not fire.
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

    /**
     * A console card's item id, and the key its fan covers are filed under.
     *
     * A function rather than the same interpolation typed in three places. The map is FILLED in
     * observeCategories and READ in memoryCardItems, about 2900 lines apart, and the first attempt
     * had them disagreeing: one said `card_$pid` and the other `card_${card.platformId}` — both
     * wrong in the same way, a script having written Kotlin's dollar escape in literally, and each
     * wrong differently enough that the lookup silently missed. No error, no crash, just a card
     * with no fan. Two spellings of one key cannot drift if there is only one spelling.
     */
    private fun cardItemId(platformId: String): String = "card_" + platformId

    // Games root: one item per enabled Memory Card (already ordered pinned-first by the DAO).
    private fun memoryCardItems(): List<XMBItem> {
        // Real games only (excludes app-style entries), matching what All Games actually shows.
        val totalGames = _uiState.value.allGamesCount
        val allGamesItem = XMBItem(
            id       = ALL_GAMES_ITEM_ID,
            title    = "All Games",
            subtitle = countLabel(totalGames, "game", "games"),
            insideCovers = _uiState.value.cardFanCovers[ALL_GAMES_ITEM_ID].orEmpty(),
            type     = XMBItemType.ALL_GAMES,
        )

        // FAVORITES MOVED OUT OF HERE, to the Shelves column, on 2026-09-24. One destination,
        // not two: it is a shelf like Playing and Completed, and a card in both places would be
        // two doors to one list that could disagree about what is behind them. The Games root is
        // All Games, Missing, then the consoles.

        // Missing sits under Favorites and only exists while something is actually missing, so a
        // healthy library never sees it. It disappears on its own once the files come back.
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

        // Search TRAILS this column rather than leading it, via libraryColumn like the other
        // four. It used to lead, on the reasoning that a 147-game library wants search first. What
        // that missed is that Select already opens search from anywhere
        // (GamepadAction.OPEN_SEARCH), so the row is a narrow second path to something that
        // already has a one-button path -- and it was taking the slot above All Games, which is
        // where the cursor lands and what you actually came for.

        // User collections sit just under All Games / Favorites — like Favorites but user-defined.
        // Only collections assigned to this (the Main Game) category appear here; categoryId
        // is the single source of truth for a collection's placement. Pinned collections first.
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

        // Windows is import-driven and belongs under Settings ▸ Library rather than the normal
        // console cards. Only surface it here when the Windows card actually contains game rows.
        val visibleCards = enabledCards.filter { card ->
            card.platformId != WINDOWS_PLATFORM_ID ||
                (_uiState.value.platformGameCounts[WINDOWS_PLATFORM_ID] ?: card.gameCount) > 0
        }

        // Genuinely no cards at all — the user removed even the seeded Android one.
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

        // A column full of cards holding nothing still has to say where to go.
        //
        // The pointer above is guarded on there being no cards, and an Android Memory Card is
        // seeded on a fresh install — so it was false on exactly the device that needed it. A new
        // user's Games column read "Search Games / All Games (0 games) / Android Memory Card
        // (0 games)" and pointed nowhere at all. Emptiness here is about GAMES, not about whether
        // a card exists, and the answer is the same setup-gap row All Games already shows.
        val gapRow = if (totalGames == 0) setupGapItem() else null
        return libraryColumn(
            header + collectionItems + cardRows + listOfNotNull(gapRow),
            SearchScope.GAMES,
        )
    }

    /**
     * The row that names the FIRST unmet setup step and confirms through to the screen that fixes
     * it, or null once nothing is missing.
     *
     * One definition, because two screens need the same answer: All Games when it is empty, and
     * the Games root when the library has no games in it at all. A second hand-written "go to
     * Library Manager" message in the other place is a message that can disagree with this one
     * about which step is actually first.
     */
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

    // B3: the empty All Games row names the FIRST unmet setup step and confirms through to the
    // screen that fixes it. "No games imported yet" told a fresh install nothing actionable.
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
                gamepadInputHandler.stickSensitivity = prefs.stickSensitivity
                _uiState.update { it.copy(leftBacksOut = prefs.leftBacksOut) }
            }
        }
    }

    /**
     * A tap on an on-screen button prompt, routed exactly as the physical press would be.
     *
     * One entry serves every footer in the app, because [dispatchGamepadAction] already hands the
     * action to whichever surface owns input — the crossbar, a settings screen through
     * [XMBUiState.pendingSettingsAction], the app drawer. A tapped prompt that took a shortcut
     * past that would be a second definition of what each button does.
     *
     * It reports touch honestly. That used to hide the prompt it was reporting, which is why the
     * touch gate had to go before this could be wired at all.
     */
    fun onPromptTapped(action: GamepadAction) {
        markTouchInput()
        dispatchGamepadAction(action)
    }

    /**
     * An action from a key the launcher claimed itself, outside the binding table.
     *
     * Space, today. It cannot be bound — DEFAULT_BINDINGS is guarded against claiming a key that
     * types a character, because a bound keycode never reaches a text field — so the Activity
     * takes it at dispatch and hands the action here. Controller input, not touch: it came from a
     * key, and reporting it as a finger would flip the contextual touch controls.
     */
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
                    // The fallback the handler cannot make: a hold over a list with no rail is
                    // still a press, and the step it owed is paid here. Without this, holding a
                    // shoulder on a short column would swallow the input entirely.
                    is ShoulderHold.End ->
                        if (_uiState.value.letterJump != null) closeLetterJump()
                        else dispatchGamepadAction(hold.action)
                }
            }
        }
    }

    // ── Letter jump ────────────────────────────────────────────────────────────

    /**
     * Raise the rail, if this column has one.
     *
     * Nothing happens on a list that is short, unsorted or nearly all one letter — see
     * [letterAnchors], which decides that by reading the list rather than by being told.
     */
    private fun openLetterJump() {
        val s = _uiState.value
        if (s.hasBlockingOverlay || s.letterJump != null) return
        val rail = letterJumpFor(s.currentItems, s.selectedItemIndex) ?: return
        _uiState.update { it.copy(letterJump = rail, selectedItemIndex = rail.targetIndex) }
    }

    /** Let go: the rail goes, the cursor stays where the rail put it. */
    private fun closeLetterJump() = _uiState.update { it.copy(letterJump = null) }

    /**
     * Step the rail and take the list with it, so the column reads as it moves rather than
     * jumping once on release. This is why it is a scrubber and not a menu.
     */
    private fun moveLetterJump(delta: Int) {
        val rail = _uiState.value.letterJump ?: return
        val next = rail.move(delta)
        if (next === rail) return
        menuSound.play(MenuSound.SCROLL)
        _uiState.update { it.copy(letterJump = next, selectedItemIndex = next.targetIndex) }
    }

    /** A finger at [fraction] down the rail. Touch's way in — no hold, no shoulder. */
    fun onLetterRailTouch(fraction: Float) {
        onUserInteraction()
        val s = _uiState.value
        // The same guard [openLetterJump] has, and it was missing here. The rail keeps drawing
        // behind the context rail and the notification sheet — both leave the crossbar visible —
        // so a thumb could scrub the list underneath a menu that is supposed to own the input.
        // Two ways into one state and only one of them was checked.
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

    /** The finger left the rail. Same ending as letting go of the shoulder. */
    fun onLetterRailReleased() = closeLetterJump()

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
                // The wave's own reading of idle: a plain pause, with none of the hint's
                // conditions about what is focused or whether an overlay is up.
                val waveIdle = idleMs >= WAVE_IDLE_MS
                if (waveIdle != s.idle) _uiState.update { it.copy(idle = waveIdle) }
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

        // ── The letter rail takes the D-pad while it is up ─────────────────────
        //
        // First, and above the pickers, because the rail can only exist on the crossbar: none of
        // the branches below can be open at the same time (openLetterJump refuses while
        // hasBlockingOverlay), so the order costs nothing and reads in the order the user sees.
        //
        // UP/DOWN walk the rungs. LEFT/RIGHT are swallowed rather than passed through: they would
        // step the category out from under the list the rail is pointing at. BACK puts the cursor
        // back where the rail found it, which is the only way out that undoes the scrub.
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

        // ── The notification sheet captures ALL input when open ───────────────
        //
        // Above the context menu because it is drawn above it, and because the two cannot be open
        // together: opening the sheet is a press the crossbar takes, and the menu takes every
        // press while it is up.
        if (state.notificationsOpen) {
            when (action) {
                GamepadAction.NAVIGATE_UP   -> moveNoticeCursor(-1)
                GamepadAction.NAVIGATE_DOWN -> moveNoticeCursor(+1)
                // Left and right are the media row's, and only the media row's: they are the
                // transport. On a notification they do nothing rather than stepping the cursor
                // sideways through a single column.
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

        // ── Context menu captures ALL input when open ──────────────────────────
        if (state.activeContextMenu != null) {
            when (action) {
                GamepadAction.NAVIGATE_UP   -> shiftContextMenu(-1)
                GamepadAction.NAVIGATE_DOWN -> shiftContextMenu(+1)
                // Picked a row: run it. Picked nothing: run what the menu says it is FOR, which
                // for a game is Play — so opening the options over a game does not take confirm
                // away from the game. The cursor walks railRows, so that is the list its position
                // means something in; the primary is an id and needs no list at all.
                GamepadAction.SELECT        -> {
                    val menu = state.activeContextMenu
                    val picked = menu?.selectedIndex?.let { state.railRows().getOrNull(it) }
                    when {
                        picked != null -> activateContextMenuItem(picked.id)
                        menu?.primaryId != null -> activateContextMenuItem(menu.primaryId)
                        else -> Unit
                    }
                }
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

        // ── Modal text dialogs capture ALL input — the keyboard owns everything except the
        //    two buttons the XMB always means: A confirms what is in the field, B cancels. The
        //    XMB behind must never move. Confirming reads the hoisted text (see
        //    [onNamePromptTextChanged]) so A and the on-screen Save agree. ────────────────
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
        // Read-only info dialog (e.g. file location) — A or B closes it.
        if (state.infoDialog != null) {
            if (action == GamepadAction.BACK || action == GamepadAction.SELECT) dismissInfoDialog()
            return
        }
        // Launch recovery sheet — the cursor walks the buttons, A activates the one it is on.
        //
        // A used to mean RETRY outright, which made three of the five buttons unreachable by a pad
        // on a pad-first device, at the one moment the pad has to work. The order now comes from
        // launchRecoveryActions, so the button at index 0 is the remedy for THIS failure.
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
        // Windows Library setup prompt — A sets up (Library Manager), B defers.
        if (state.showWindowsSetupPrompt) {
            when (action) {
                GamepadAction.SELECT -> confirmWindowsSetupPrompt()
                GamepadAction.BACK   -> dismissWindowsSetupPrompt()
                else                 -> Unit
            }
            return
        }

        // ── Library search captures input. Beside the music browser and for the same reason:
        //    a fullscreen list with its own cursor, so nothing underneath may move. OPEN_SEARCH
        //    is absent on purpose -- pressing the search button again while searching should do
        //    nothing rather than restart the search you are halfway through typing. ────────────
        if (state.search != null) {
            when (action) {
                // The results are a GRID now, so up and down move by a row and left and right by
                // one. A list only ever needed two of these; carrying that over would have left
                // the grid walkable one cell per press in one dimension only.
                // A row is whatever the grid drew, not a constant — see SearchState.columns.
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

        // ── Start opens the notification sheet, from anywhere the strip is drawn ──────
        //
        // Above the per-screen routing below, because those branches forward EVERYTHING to the
        // screen they name and would swallow it -- which is why Start used to work only on the
        // crossbar, while the strip that it opens was on every screen.
        //
        // Gated on the strip being drawn, which is what excludes the screens that have their own
        // use for Start: the pickers confirm with it and the Artwork Studio applies with it, and
        // every one of those is a full-screen overlay. The sheet's own branch, further up, has
        // already returned if it is open, so this only ever opens.
        //
        // The Studio is the exception the gate cannot see, because it lives INSIDE the game page
        // and the page is chrome. GameDetailScreen keeps Start for that reason and hands it back
        // when the Studio is closed -- its "HOME belongs to the shell, never to this page" was
        // already written there, with nothing to hand it to.
        if (action == GamepadAction.HOME && state.statusStripVisible && state.activeGameId == null) {
            toggleNotifications()
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
                    // The shoulders step between settings SECTIONS. They used to fall through to
                    // `else -> Unit` here, which is why the rail could afford to list every
                    // section: there was no other way to reach one. Now the rail lists the
                    // current section's screens and these are how you leave it.
                    GamepadAction.PREV_CATEGORY,
                    GamepadAction.NEXT_CATEGORY,
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
            state.saveThemeNameDialog != null -> {
                // "Save as Theme..." is a touch-only button inside the icon editor, and the
                // dialog it opens had NO branch here at all -- five references in the whole file,
                // none of them a gamepad path. It is in hasBlockingOverlay, so every press fell
                // through to the silent return below and the controller went dead until the user
                // touched Cancel. A saves the typed name, B closes it.
                when (action) {
                    GamepadAction.SELECT -> confirmSaveCurrentLookAsTheme(state.saveThemeNameDialog.text)
                    GamepadAction.BACK   -> dismissSaveThemeNameDialog()
                    else                 -> Unit
                }
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
        //
        // It LOGS now. It was written to catch exactly that mistake and then caught one in
        // production silently for as long as the Save-as-Theme dialog existed -- the controller
        // simply stopped responding and nothing said why. A dropped press here is always a bug
        // in this function, so it should arrive in a log rather than in the user's hands.
        if (state.hasBlockingOverlay) {
            Timber.w("Gamepad action $action dropped: a blocking overlay has no branch in this dispatcher")
            return
        }

        when (action) {
            // Item cursor moves through the shared moveItemCursor() so touch swipes and the D-pad
            // drive identical logic; cancel auto-repeat when we hit a list boundary.
            GamepadAction.NAVIGATE_UP   -> {
                // Out of the pill row first, and the press is spent doing it — you came DOWN into
                // it from this row, so UP gives that row back rather than skipping past it. Same
                // order BACK follows a few branches below, for the same reason.
                if (state.activePillIndex() != null) {
                    menuSound.play(MenuSound.SCROLL)
                    _uiState.update { it.copy(pillCursor = null) }
                    return
                }
                if (!moveItemCursor(-1)) gamepadInputHandler.cancelRepeat()
            }
            GamepadAction.NAVIGATE_DOWN -> {
                // Already in the row: there is nothing under it.
                if (state.activePillIndex() != null) {
                    gamepadInputHandler.cancelRepeat()
                    return
                }
                if (moveItemCursor(+1)) return
                // The bottom of the column, where DOWN has always done nothing at all. That dead
                // press is the row's way in, and it is the only one that does not compete with a
                // press that already means something.
                //
                // Guarded on the row being DRAWN, not merely on the focused item having pills.
                // Without that this opened a door on the Last Played shelf, which has no pill row
                // at all: the cursor went into a row nobody can see and the next confirm would
                // have run an action nobody picked.
                if (state.pillRowVisible && pillPressHandled(action, state)) return
                gamepadInputHandler.cancelRepeat()
            }
            GamepadAction.NAVIGATE_LEFT -> {
                // ALREADY IN THE PILL ROW: left and right are the row's and nothing else's.
                // Without this, LEFT from inside the row hit the drill branch below and left the
                // folder entirely — a press that walked out of two things at once, from a cursor
                // sitting on "Favorite". The row is the innermost open thing; it goes first.
                if (state.activePillIndex() != null && pillPressHandled(action, state)) return
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
                // The home shelf is the leftmost column, so LEFT has nothing to step to and has
                // always been a dead press there. It brings the recents rail in instead.
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
                // The same pre-check as LEFT, and it matters on the one screen the rule below
                // excludes: the shelf's pills are reachable by DOWN now, and once the cursor is
                // in them RIGHT has to walk the row rather than step off the shelf.
                if (state.activePillIndex() != null && pillPressHandled(action, state)) return
                // ...and RIGHT puts the rail away ON THE WAY PAST. It used to spend the press
                // doing only that, which made leaving the shelf cost two presses before the pill
                // row started charging for more — "moving from the recent screen to xmb is taking
                // too many presses". No return: the rail closes and the step happens together.
                if (state.onLastPlayedHome && state.recentRailVisible) {
                    _uiState.update { it.copy(recentRailVisible = false) }
                }
                // The shelf has no pill row — LastPlayedPage draws none, which is what
                // pillRowVisible is for. This used to read "the pills are still there to touch"
                // on that screen; they are not there at all. See pillNav for what they cost where
                // they do apply.
                if (state.pillRowVisible && pillPressHandled(action, state)) return
                if (state.isInSubItem) { gamepadInputHandler.cancelRepeat(); return }
                val next = state.stepToReachableCategory(+1)
                if (next != state.selectedCategoryIndex) onCategorySelected(next)
                else gamepadInputHandler.cancelRepeat()
            }
            GamepadAction.SELECT     -> {
                // A pill under the cursor takes confirm; otherwise the row does, as always.
                val pill = state.activePillIndex()?.let { state.focusedPills().getOrNull(it) }
                if (pill != null) onPillActivated(pill.id) else onItemSelected(state.selectedItemIndex)
            }
            GamepadAction.BACK       -> {
                menuSound.play(MenuSound.BACK)
                // The pill row is the innermost open thing, so it closes first — the same order
                // the recents rail follows, and for the same reason: BACK leaves what you are in
                // before it leaves what that is inside of.
                if (state.activePillIndex() != null) {
                    _uiState.update { it.copy(pillCursor = null) }
                    return
                }
                // The rail collapses first. It is a thing that is open, and BACK closes the
                // innermost open thing before it does anything larger -- reaching the App Drawer
                // past an open rail would be a surprise.
                if (state.onLastPlayedHome && state.recentRailVisible) {
                    _uiState.update { it.copy(recentRailVisible = false) }
                    return
                }
                // One level up, or the App Drawer when there is no level left to leave.
                if (!backOutOfDrill(state)) onOpenAppDrawer()
            }
            // Y / Triangle — open context menu for whichever item type has focus
            GamepadAction.OPEN_CONTEXT_MENU -> openContextMenuForFocusedItem()
            // Start button no longer restarts / shows the boot screen.
            // Start. It did nothing here at all — the binding exists for the pickers, where it
            // confirms, and the crossbar simply had no use for it.
            GamepadAction.HOME          -> toggleNotifications()
            // Cycle the sort order of the current list (PSP-style). Whichever face button
            // the user's X/Y layout assigns to sort dispatches this.
            // X is free on the home page — activeSortModes declines Last Played because its
            // order IS its meaning — so it cycles which media the shelf shows instead. One
            // button, and which job it does is decided by which list is on screen.
            GamepadAction.CHANGE_SORT ->
                if (state.onLastPlayedHome) cycleRecentFilter() else cycleSort()
            // Searches everything, from anywhere on the home screen. The per-library Search rows
            // are the same overlay with a narrower scope.
            GamepadAction.OPEN_SEARCH -> openSearch(SearchScope.ALL)
            // CHANGE_SORT and OPEN_CONTEXT_MENU are deliberately NOT repeated here. Both are
            // handled above, and Kotlin takes the first matching branch, so a second mention is
            // dead — and the kind of dead that bites, because the next person to change one of
            // them has two places to find and only one that runs.
            //
            // OPEN_CONTEXT_MENU was in fact still repeated below this very comment, as `-> Unit`,
            // for as long as the comment has existed: an unreachable branch that read as "the
            // context menu does nothing at the crossbar root" while the real handler thirty lines
            // up opened it. The compiler had been saying so the whole time — `Duplicate branch
            // condition in 'when'` — which is the argument for reading the warnings.
            // The shoulders were dead on the crossbar root. They now walk the hover panel in the
            // logo region. No conflict to resolve: nothing else on this screen claimed them.
            GamepadAction.PREV_CATEGORY -> stepHoverPanelPage(-1)
            GamepadAction.NEXT_CATEGORY -> stepHoverPanelPage(+1)
        }
    }

    /**
     * Walk the hover panel's page for the row under the cursor.
     *
     * The available pages are recomputed from the focused row rather than stored, so a row with
     * no box art cannot be walked onto a box art page. Non-game rows ignore the shoulders
     * entirely: there is no panel over a settings row or a music folder to walk.
     */
    /** A tap on the strip goes straight to that page; the strip only draws pages that exist. */
    fun onPanelPageTapped(page: DetailPanelPage) = _uiState.update {
        it.copy(panelPage = page, panelPageGameId = it.hoverPanelItem?.gameId)
    }

    private fun stepHoverPanelPage(delta: Int) = _uiState.update { s ->
        val content = s.hoverPanelContent ?: return@update s
        // THE FIRST PRESS OPENS, IT DOES NOT STEP.
        //
        // Closed, effectivePanelPage reports LOGO, so R1 used to step off it and land on Info —
        // the logo was skipped entirely going right and only reachable going left. Whichever
        // shoulder you press first now opens the strip ON the logo page, and stepping starts from
        // the press after that. The pages read the same order in both directions.
        if (!s.panelStripOpen) {
            return@update s.copy(
                panelPage = DetailPanelPage.LOGO,
                panelPageGameId = s.hoverPanelItem?.gameId,
            )
        }
        // LEFT off the first page CLOSES the strip instead of clamping against it.
        //
        // The pages run LOGO, Info, Video, Box Art, and stepping left at LOGO used to land back on
        // LOGO — a press that did nothing, on the one page whose whole job is to be the way back.
        // It now returns to the resting state, so the strip has an exit at the end you arrived
        // through rather than only the far one.
        //
        // Only reachable with the strip already open, because the branch above returned for the
        // closed case — so this is "you were on the logo page and pressed left again".
        if (delta < 0 && s.effectivePanelPage == DetailPanelPage.LOGO) {
            return@update s.copy(panelPageGameId = null)
        }
        s.copy(
            // Stepping from the EFFECTIVE page, so the first shoulder press after moving to a
            // new game steps off that game's logo rather than off whatever the last game was on.
            panelPage = stepPanelPage(s.effectivePanelPage, content.pages, delta),
            panelPageGameId = s.hoverPanelItem?.gameId,
        )
    }

    // ── Context menu ──────────────────────────────────────────────────────────

    private fun openPlatformContextMenu(platformId: String) {
        val card = enabledCards.firstOrNull { it.platformId == platformId } ?: return
        val items = platformContextMenuItems(
            platformId = platformId,
            pinned = card.pinned,
            iconDisplayLabel = platformIconDisplayLabel(platformId),
        )

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
                items      = allGamesContextMenuItems(it.iconDisplayMode.label),
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
            val game = runCatching { gameRepository.getById(gameId) }.getOrNull()
            val discCount = runCatching {
                game?.discSetKey?.let { gameRepository.getDiscSetMembers(it).size } ?: 0
            }.getOrDefault(0)
            // Read off the same fetch as the disc set rather than a second query, and off the
            // stamp itself rather than off "are we on the Last Played shelf" — the entry means
            // the same thing wherever the game is being looked at, and offering it on a game
            // that was never played would be a menu row that does nothing.
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
        val (visible, overflow) = items.splitForOverflow()

        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(
                title       = item.title,
                items       = items.withOverflowRow(),
                overflow    = overflow,
                gameId      = item.gameId,
                packageName = item.packageName,
                shortcutId  = item.shortcutId,
                launchIntentUri = item.launchIntentUri,
                categoryContext = if (inGamingCategory) currentCat.id else null,
                // Confirm still plays the game while nothing in the rail is picked. The id runs
                // the hidden row above, so this is not a second way to launch — it is the row.
                primaryId   = "play",
            )
        )}
    }

    // Second-level menu: the collections a game can be added to (checkmarks show current
    // membership), plus "Create New Collection". Opened from the game options menu. The menu
    // stays open while toggling so the user can add to several collections at once.
    private fun openCollectionPicker(gameId: Long, selectIndex: Int? = 0) {
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
                    selectedIndex    = selectIndex?.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
                    gameId           = gameId,
                    collectionGameId = gameId,
                )
            )}
        }
    }

    private fun openAppContextMenu(item: XMBItem, categoryIdOverride: String? = null) {
        val pkg = item.packageName ?: return
        val categoryId = categoryIdOverride ?: currentCategory()?.id
        // The shelf builds its rows with this id prefix and nothing else does, so it is the
        // caller's honest answer to "did this row come from the home shelf".
        val items = appContextMenuItems(_uiState.value, categoryId, onRecentShelf = item.id.startsWith(RECENT_APP_ID_PREFIX))
        val (_, overflow) = items.splitForOverflow()

        _uiState.update { it.copy(
            activeContextMenu = XMBContextMenu(
                title           = item.title,
                items           = items.withOverflowRow(),
                overflow        = overflow,
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
        val items = collectionRowContextMenuItems(
            isPinned = collection.isPinned,
            hasOtherCategory = hasOtherCategory,
        )
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
        val state = _uiState.value
        val menu = state.activeContextMenu ?: return
        // railRows, not menu.items: that is the list the rail DRAWS, so it is the list the cursor
        // has to walk. Indexing the untrimmed one puts the capsule on a different action than the
        // one that runs — silently, because both lists are the same menu in the same order.
        //
        // An empty menu can flash in during a rebuild — no-op instead of coercing into the empty
        // range 0..-1 (IllegalArgumentException).
        val rows = state.railRows()
        if (rows.isEmpty()) return
        // Nothing picked yet: the first press enters the list from the end it came from, so DOWN
        // lands on the top row and UP on the bottom one.
        val current = menu.selectedIndex ?: return run {
            val entry = if (delta > 0) 0 else rows.lastIndex
            _uiState.update { it.copy(activeContextMenu = menu.copy(selectedIndex = entry)) }
        }
        val next = (current + delta).coerceIn(0, rows.size - 1)
        _uiState.update { it.copy(activeContextMenu = menu.copy(selectedIndex = next)) }
    }

    /**
     * Runs one menu entry, named by [itemId].
     *
     * BY ID, NEVER BY POSITION. There are two lists here — the builder's `items + overflow` and
     * [railRows], which is that minus the More row, minus every id the pill row already carries,
     * capped at nine with the destructive rows appended. An index into one is a different action
     * in the other, and the pill row computed its index in the first and spent it in the second,
     * where its own entry had been removed: Details ran Manage Collections, Favorite ran whatever
     * had slid into its place. Nothing threw, because both lists are the same menu.
     */
    private fun activateContextMenuItem(itemId: String) {
        val state  = _uiState.value
        val menu   = state.activeContextMenu ?: return

        // No More… branch any more: railRows never yields MENU_MORE_ITEM_ID, because the rail
        // takes `items + overflow` and cuts once itself. The builders still split around a More
        // row — the split is what keeps the panel-shaped menus in the settings screens honest —
        // and the rail puts the two halves back together before it does its own cutting.

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

        // ── The media columns' collapsed Add submenu ──────────────────────────────
        // The entries are the Add rows themselves, so the handling is the row's own: find it by
        // id and send it through the same per-category selection the column would have used. No
        // second copy of "what Add Video Apps does" exists to fall out of step.
        if (menu.isAddMenu) {
            val row = currentAddActions().firstOrNull { it.id == itemId }
            closeContextMenu()
            if (row != null) dispatchCategorySelection(row)
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
            } else if (itemId.startsWith("detail_")) {
                val gid = menu.gameId
                when (val what = itemId.removePrefix("detail_")) {
                    // The crossbar's own dialog. Blank clears the override / the note, which is
                    // what its confirm handlers already did.
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
                    // Everything else is a DetailAction name, fired as the screen opens.
                    else -> _uiState.update {
                        it.copy(activeGameId = gid, activeGameAutoLaunch = false, activeGameAction = what)
                    }
                }
            } else if (itemId.startsWith("pstate_")) {
                // "pstate_none" clears the mark; every other value is a PlayState name. Unknown
                // names resolve to null, which is the same as clearing — a stale id cannot write
                // a state the enum does not have.
                val gid = menu.gameId
                val choice = itemId.removePrefix("pstate_")
                appAction {
                    gameRepository.setPlayState(gid, PlayState.fromName(choice))
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
                "game_details"           -> openGameDetailsMenu(menu.gameId)
                // Offered only when direct launch is off (see gameContextMenuItems), and it
                // takes the SAME path confirm would take with direct launch on -- a true XMB
                // hand-off, no Game Detail composed, cursor left on this entity when the
                // emulator closes. Two ways to start a game that started it differently would
                // be worse than not offering the second one.
                "play"                   -> launchGameDirectly(menu.gameId)
                "choose_disc"             -> openDiscPickerMenu(menu.gameId)
                "export_game"            -> exportGameFromMenu(menu.gameId)
                "edit_app"               -> openAppDetail(menu.gameId, menu.packageName ?: return)
                "favorite"               -> toggleGameFavorite(menu.gameId, true)
                "unfavorite"             -> toggleGameFavorite(menu.gameId, false)
                // No explicit reload: the shelf is a Room Flow (observeRecentlyPlayed) and
                // invalidates itself on the write. Reloading by hand raced the collector and left
                // the page reading "Nothing played yet." with a game still on the shelf, until a
                // relaunch showed the truth — seen on the device. Same shape as toggleGameFavorite.
                "remove_from_recent"     -> {
                    val gid = menu.gameId
                    appAction { gameRepository.clearLastPlayed(gid) }
                }
                "add_to_collection"      -> openCollectionPicker(menu.gameId)
                "remove_from_collection" -> {
                    val gid = menu.gameId   // local val so it smart-casts inside the lambda
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
                // Two-step delete: a confirm menu first, matching the Game Detail page's guard.
                // Two-step delete, and CANCEL IS FIRST so the cursor opens on it.
                //
                // The confirm existed and opened with the cursor on "Remove": the row is reached
                // by holding DOWN to the bottom of the rail and pressing A, and the prompt that
                // came up answered a second A with yes. The App Drawer's uninstall prompt was
                // fixed for exactly this and the reasoning is its: a destructive prompt never
                // opens with the cursor on the destructive answer, because the press that got you
                // here is the press most likely to arrive again.
                "remove_game"            -> _uiState.update { it.copy(activeContextMenu = XMBContextMenu(
                    title  = "Remove \"${menu.title}\" from Library?",
                    items  = removeGameConfirmItems(),
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
                    items  = removeMissingConfirmItems(),
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
                    "launch"    -> launchAppWithDisc(pkg, selectedItemArt())
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
                    // A hide, not a clear: UsageStatsManager owns the timestamp and offers no
                    // way to forget one. Recoverable in Settings ▸ Hidden Items like every other
                    // placement, which is the whole reason it is a placement.
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

    // Submenu listing every installed emulator that supports the game's platform. Selecting a row
    // dispatches "emu_pick_<profileId>" (or "emu_pick_default" to clear the per-game override).
    // Second-level menu: how this game's XMB tile is drawn. Checkmark shows the current choice;
    // "Use Global Setting" clears the per-game override.
    /**
     * Playing / Completed / Backlog, or none of them.
     *
     * "Unmarked" is a row rather than an absence, because the only other way back out of a state
     * is to pick a different one — and a menu you can enter and not leave is the shape of every
     * flag that ends up stuck on.
     */
    /**
     * The things a game's own screen used to keep to itself.
     *
     * Two of them run here and never open it: Edit Title and Edit Note put up the crossbar's own
     * text dialog, which has had handlers for both since before anything called them — the
     * confirm path was built and unreachable.
     *
     * The other four are pieces of that screen's state — the Artwork Studio, the metadata
     * preview, the manual viewer, a re-scrape — so they open it with the action already firing
     * rather than landing you on a page to go looking. "Open Game Details" is last, for the
     * everything-else.
     */
    private fun openGameDetailsMenu(gameId: Long) {
        _uiState.update { it.copy(activeContextMenu = XMBContextMenu(
            title = "Details",
            items = listOf(
                XMBContextMenuItem("detail_title", "Edit Title"),
                XMBContextMenuItem("detail_note", "Edit Note"),
                XMBContextMenuItem("detail_ARTWORK", "Artwork"),
                XMBContextMenuItem("detail_METADATA", "Update Metadata"),
                XMBContextMenuItem("detail_MANUAL", "Manual"),
                XMBContextMenuItem("detail_REFRESH", "Refresh Artwork"),
                XMBContextMenuItem("detail_open", "Open Game Details"),
            ),
            gameId = gameId,
        ))}
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
            _uiState.update { it.copy(activeContextMenu = XMBContextMenu(
                title  = "Mark As",
                items  = items,
                gameId = gameId,
            ))}
        }
    }

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

    /** Live text for whichever name prompt is open (see [withNamePromptText]). */
    fun onNamePromptTextChanged(text: String) {
        _uiState.update { it.withNamePromptText(text) }
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
        if (dialog.quickSearch) { runQuickSearch(name); return }
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

    /**
     * Hands what was typed to whichever app the user has chosen for the job.
     *
     * PSPLauncher never picks the browser. ACTION_WEB_SEARCH and ACTION_VIEW both go to Android's
     * own default, which is why there is no browser preference anywhere in this app.
     */
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
            // A device with no browser at all. Say so rather than throwing on the launcher's own
            // scope, which would take the whole XMB down for a failed search.
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
    /**
     * Raise the focused row's own context menu — the Y press, and the one place that decides
     * which menu a row gets.
     *
     * Extracted so the pill row can reuse it. The order of these branches IS the rule (a media row
     * is asked first, a game beats a package name), and [pillsFor] mirrors it; a second copy here
     * would be the list-and-its-mirror problem with nothing checking it.
     */
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

    /**
     * A pill under the focused row: open that row's menu and activate the matching entry.
     *
     * Through the menu rather than around it, so a pill cannot do a subtly different thing from
     * the entry with the same name — every handler and every piece of context the action needs is
     * already assembled by the code that raises the menu.
     *
     * A pill whose id the menu does not offer closes the menu again rather than leaving it open on
     * screen, which is what a user would see as "the button opened a menu I did not ask for".
     * PillActionsTest is what stops that from happening; this is what it looks like if it does.
     */
    /**
     * How long a pill waits for its row's menu to appear before giving up.
     *
     * Long enough for a single indexed DB read, short enough that a row which raises no menu at
     * all does not leave a coroutine parked forever on a flow that will never emit.
     */
    /**
     * Give a left/right press to the pill row if it wants it.
     *
     * Returns true when the press was consumed. [PillNav.ExitAndPass] clears the cursor and
     * returns FALSE on purpose: that press has to go on to move the category, or a row with pills
     * would be a place the crossbar can never be reached from — see pillNav's own note.
     */
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

    // ── The notification sheet ────────────────────────────────────────────────

    /**
     * The device's notifications, mirrored into the state.
     *
     * The shell used to collect this itself, which was fine while the sheet was something only a
     * finger opened and closed. A cursor has to walk the same list the input dispatcher acts on,
     * and the dispatcher reads state — so the list lives here and the sheet draws what it is told.
     */
    private fun observeAndroidNotices() {
        viewModelScope.launch {
            AndroidNotifications.active.collect { notices ->
                _uiState.update { it.copy(androidNotices = notices) }
            }
        }
    }

    /**
     * What each shelf holds, as counts.
     *
     * Combined into one emission rather than collected separately: the Shelves column appears and
     * disappears on whether ANY of them is non-zero, and four independent updates would let the
     * column flicker in and out while a scan is writing.
     */
    private fun observeShelfCounts() {
        viewModelScope.launch {
            val marks = PlayState.entries
            // The LISTS, not the counts. A shelf needs both — how many, to decide whether it
            // exists at all, and the newest covers inside it, to look like what it holds the way
            // every other card on the crossbar does. Two queries for one answer would be two
            // answers: a card drawn from one snapshot and counted from another.
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
                        // ALL FIVE, from one flow, into a map nothing else writes. Favorites is
                        // here rather than in observeCategories' map for that reason alone: that
                        // one is rebuilt whole on every library change and took the shelves' art
                        // with it every time.
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

    /**
     * The one game the sheet can offer to resume.
     *
     * Limit 1 rather than the shelf's list: this row is a shortcut back into what you were doing,
     * and a second-most-recent game is not that. The shelf is one press away and already sorted.
     */
    private fun observeResumeGame() {
        viewModelScope.launch {
            gameRepository.observeRecentlyPlayed(1).collect { games ->
                _uiState.update { it.copy(resumeGame = games.firstOrNull()) }
            }
        }
    }

    /** Start, or the strip's left corner. */
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

    /**
     * Confirm on the focused row.
     *
     * Opening a notification closes the sheet, because the thing it opens is another app: leaving
     * it standing would put the launcher's own overlay over whatever just came to the front. The
     * media row does NOT close it — play/pause is a thing you do while looking at the sheet.
     */
    fun activateFocusedNotice() {
        when (val focus = _uiState.value.focusedNotice) {
            null -> Unit
            // Whichever tenant has the row. Music while there is music; otherwise the game,
            // and launching one closes the sheet for the same reason opening a notification does.
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
                // The close happens whether or not the intent fired. A notification that has gone
                // stale since the list was drawn leaves nothing to look at either.
                if (!AndroidNotifications.open(focus.key)) {
                    Timber.i("Notification ${focus.key} had nothing to open")
                }
                closeNotifications()
            }
        }
    }

    /** Y on the focused row: clear one notification. Nothing to do on the media row. */
    fun dismissFocusedNotice() {
        val focus = _uiState.value.focusedNotice as? NoticeFocus.Notice ?: return
        val notice = _uiState.value.androidNotices.firstOrNull { it.key == focus.key } ?: return
        // Offered only where it works. isClearable is false for an ongoing notice and
        // cancelNotification on one is a silent no-op, which reads as a dead button.
        if (!notice.canDismiss) return
        menuSound.play(MenuSound.BACK)
        AndroidNotifications.dismiss(focus.key)
    }

    /** Touch: a row was tapped. The key, not the index — see [NoticeFocus]. */
    fun onNoticeTapped(key: String) {
        val rows = _uiState.value.noticeFocusables
        val index = rows.indexOfFirst { it is NoticeFocus.Notice && it.key == key }
        if (index < 0) return
        _uiState.update { it.copy(noticeCursor = index) }
        activateFocusedNotice()
    }

    /** Touch: the ✕ on a row. */
    fun onNoticeDismissTapped(key: String) {
        val rows = _uiState.value.noticeFocusables
        val index = rows.indexOfFirst { it is NoticeFocus.Notice && it.key == key }
        if (index < 0) return
        _uiState.update { it.copy(noticeCursor = index) }
        dismissFocusedNotice()
    }

    /** Touch: the media row's primary — play/pause, or Resume when it is the game's row. */
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
            // WAIT for it. openGameContextMenu decides its Choose Disc entry from a DB read and so
            // writes the menu from a coroutine — reading activeContextMenu on the next line finds
            // null and leaves the menu standing open on screen, which is what a tapped pill did
            // before this: it raised the full menu instead of running the action. The app menu
            // happens to be synchronous, so half the pills worked and half did not.
            val menu = withTimeoutOrNull(MENU_RAISE_TIMEOUT_MS) {
                uiState.first { it.activeContextMenu != null }.activeContextMenu
            }
            if (menu == null) {
                Timber.w("Pill '$pillId' pressed on a row that raised no menu")
                return@launch
            }
            // `items + overflow`, because the builders split long menus around a More row and a
            // pill's entry can land on either side of it. Membership only — the id is what runs.
            if ((menu.items + menu.overflow).none { it.id == pillId }) {
                // PillActionsTest is what stops this; this is what it looks like if it slips
                // through. Closing again beats leaving a menu the user did not ask for.
                Timber.w("Pill '$pillId' is not offered by the focused row's menu")
                closeContextMenu()
                return@launch
            }
            activateContextMenuItem(pillId)
        }
    }

    /** A rail row was tapped. The index is a position in [railRows] — the list that was drawn. */
    fun onContextMenuItemActivatedAt(index: Int) {
        val id = _uiState.value.railRows().getOrNull(index)?.id ?: return
        _uiState.update { it.copy(activeContextMenu = it.activeContextMenu?.copy(selectedIndex = index)) }
        activateContextMenuItem(id)
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
        val category = _uiState.value.categories.getOrNull(index)
        // Landing on a category always lands on its FIRST row. This used to remember where you
        // were in each column and put you back, which reads as the bar being out of step with
        // itself: the crossbar is a horizontal list of columns, and sweeping across it left a
        // trail of columns each scrolled to a different depth with no way to see it coming. The
        // drill cursor ([viewCursor]) is a different thing and stays -- going INTO a folder and
        // back out should land where you left off, because that is one column, not seven.
        // activeAppDrawerFilter is cleared as an invariant: landing on a category always shows the
        // plain XMB (the drawer can't normally be open here, but this keeps the contextual button
        // state correct no matter which path selected the category).
        // The rail is per-visit, not remembered: leaving the shelf and coming back should show
        // the thing you last opened, which is the page's whole subject.
        _uiState.update { it.copy(selectedCategoryIndex = index, selectedItemIndex = 0, recentRailVisible = false, selectedPlatformId = null, selectedCollectionId = null, musicNav = MusicNav.Root, videoNav = VideoNav.Root, photoNav = PhotoNav.Root, activeAppDrawerFilter = null) }
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
    /**
     * The next column [delta] takes you to, stepping OVER any that is not reachable.
     *
     * A walk rather than an index bump, because an unreachable column must cost no press at all —
     * stopping on it and then needing a second press is exactly what hiding Last Played from the
     * bar was meant to stop, one screen over. Returns the current index when there is nowhere to
     * go, which is what the caller reads as "cancel the repeat".
     */
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
            // The hints only come down if they are going to come back on their own.
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

    /**
     * Hands [item] to the media column that owns it, and reports whether anyone took it.
     *
     * Extracted because the Add submenu activates the very same rows: a row selected from the
     * submenu must do exactly what it does when pressed in the column, and the only way to
     * guarantee that is for there to be one dispatcher.
     */
    /**
     * Hands [item] to the library that owns it, and returns true when that library handled it.
     *
     * Asks the ROW what it is before asking where the cursor is — [menuHostCategory], the same
     * predicate the context menus use. Keyed on the cursor alone this returned false for every
     * track, book and video on the Last Played shelf, because that column is none of the media
     * libraries by definition: A did nothing on exactly the rows Y had already been fixed for.
     * One side of the pair was guarded and the other was not.
     */
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
            // Third of the three input markers, and the one that made the fix look wrong on the
            // device: the hints only come down if they are going to come back on their own. With
            // no auto-hide the poller is the only thing that raises the flag again, so clearing
            // it here left a gap of up to one poll tick after every button press.
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

        // The media columns handle their own rows (static items, the memory card, playlists,
        // tracks, app rows, add/setup rows), each owning its sound and returning early.
        if (item != null && dispatchCategorySelection(item)) return

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
            // A shelf opens as a folder inside its own column, the same way a console card opens
            // inside Game — same field, same back-out, nothing new to remember.
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
                // The Games root's own Search row. The media columns answer this in their own
                // selection handlers, which run before this one.
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
            EMPTY_CATEGORY_ITEM_ID -> return   // not selectable
        }

        // User collection folder — open it.
        if (item?.collectionId != null && item.type == XMBItemType.COLLECTION) {
            openCollectionFolder(item.collectionId)
            return
        }

        // Media rows, wherever they are shown outside their own column -- which in practice
        // means the Recent shelf. The Music / Video / Library columns answer these in their own
        // selection handlers, which run first and return; this is the fallthrough for a row that
        // reached the generic path, and without it A on a Recent book, track or video did
        // nothing at all. Not a second code path for opening media: each branch calls the same
        // opener its column calls.
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
            // A collapsed album run on the Recent shelf. Opens that album in the browser rather
            // than starting it: the shelf said "this record", and which track to resume is a
            // question the album view can answer and a blind play cannot.
            XMBItemType.MUSIC_GROUP -> {
                item.musicGroupKey?.let {
                    menuSound.play(MenuSound.SELECT)
                    openMusicBrowser(MusicBrowserView.Album(item.title, it))
                }
                return
            }
            else -> Unit
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
            OPEN_SETTINGS_ITEM_ID -> {
                // The root list of sections. It used to open the catalog's first screen
                // directly, which worked while the rail carried the whole tree; the rail is one
                // section now, so landing inside a section would hide the other six.
                val root = com.psplauncher.core.domain.model.SETTINGS_ROOT_SCREEN_ID
                Timber.d("Opening settings: $root")
                _uiState.update { it.copy(activeSettingsScreen = root) }
            }
            else -> when (category?.id) {
                BuiltInCategory.SETTINGS -> {
                    // Any other id on this column is a direct screen route (deep links from
                    // elsewhere reuse these rows).
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

    /**
     * Opens one shelf inside the Shelves column.
     *
     * It does NOT jump to another category the way openFavoritesFolder does — the shelf lives in
     * the column you are already standing in, so moving the cursor anywhere would be moving it
     * away from the thing that was pressed.
     */
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
        // The SAME ladder Game Detail launches by — per-game override, memory card, platform
        // default, then the automatic pick. This used to take the first available profile for the
        // platform, which is the ladder's BOTTOM rung on its own: with direct launch on, a game
        // pinned through "Change Emulator" launched on something else and said nothing.
        val resolvedLaunch = launchResolver.resolve(game).getOrElse { reason ->
            Timber.w(reason, "Direct launch unresolved: gameId=${game.id}, platform=${game.platformId}")
            // The resolver names WHICH rung failed and why — a shelved platform default reads
            // differently from nothing being configured at all — so its message is the one worth
            // surfacing rather than a fixed sentence about per-system defaults.
            launchDispatcher.recordPreflightFailure(
                game, null,
                reason.message ?: "No emulator is set up for ${game.platformId.uppercase()}.",
            )
            return
        }
        val profile = resolvedLaunch.profile
        // Preflight the same checks Game Detail's resolver applies, so a stale RetroArch core
        // mapping (or a dropped launch activity) refuses here with a repair, not at startActivity.
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
                // The resolver already decided what kind of failure this is; the sheet leads with
                // the matching remedy instead of always leading with Retry.
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
                // Cleared with the screen, or reopening it by any other route would re-fire the
                // last deep-linked action — the Studio opening again on a press that asked for
                // nothing of the kind.
                activeGameAction = null,
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
        // Rebuild the visible list so a freshly assigned background/icon (games-table row keyed by
        // package) reaches the XMB rows immediately — this is what puts artworkUri on app items.
        // App renames re-sort too, so the cursor follows the row by id.
        loadItemsForCategory(currentCategory(), keepCursorOnRow = true)
    }

    fun consumeAppDetailAction() {
        _uiState.update { it.copy(pendingAppDetailAction = null) }
    }

    // ── Settings hierarchy (L1 section flyouts) ───────────────────────────────
    // Drilling into a section reuses the shared drill path (computeDrillTitle / computeDrillSiblings
    // → XmbDrillFlyout) with cursor memory, exactly like Music/Video/Photo. Back from an L2
    // flyout is revealed — there is no extra stack to unwind.


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
        // The root is not in the catalog by design (see SETTINGS_ROOT_SCREEN_ID), so it has to be
        // allowed past a guard whose whole job is to reject ids that are not.
        if (screenId != com.psplauncher.core.domain.model.SETTINGS_ROOT_SCREEN_ID &&
            com.psplauncher.core.domain.model.settingsEntryFor(screenId) == null
        ) {
            Timber.w("Settings rail asked for a screen outside the catalog: %s", screenId)
            return
        }
        Timber.d("Settings rail -> %s", screenId)
        _uiState.update {
            // Only the wizard earns a return address; every other screen keeps Back meaning
            // "up to the root", and opening from anywhere else CLEARS a stale one.
            it.copy(
                activeSettingsScreen = screenId,
                settingsReturnTo = returnAddressFor(it.activeSettingsScreen),
            )
        }
    }

    /**
     * Back from a settings screen: to the root list, or out of Settings when already there.
     *
     * Settings is two levels now — the sections, then a section's screens — and Back has to mean
     * "up one" at the first level rather than "leave". Without this the root would be a page you
     * could only ever pass through on the way in.
     */
    fun onSettingsBack() {
        // The wizard's excursion, returning. Consumed here so a second Back behaves normally.
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
        // Leaving the setup wizard by any deliberate path (Skip, Finish, back-out on a re-run)
        // stamps it as seen — see markInitialSetupSeen for why open time is the wrong moment.
        val closing = _uiState.value.activeSettingsScreen
        if (closing in WIZARD_SCREEN_IDS) {
            markInitialSetupSeen()
        }
        _uiState.update {
            it.copy(activeSettingsScreen = null, settingsReturnTo = null, pendingSettingsAction = null)
        }
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
        // Not a literal: AppFilter.DEFAULT is the one place that decides where the menu opens.
        _uiState.update { it.copy(activeAppDrawerFilter = com.psplauncher.feature.appbar.AppFilter.DEFAULT.name) }
    }

    /**
     * Put an app on the cross bar, in the column the drawer was opened over.
     *
     * "Add to Cross Bar" does not say WHERE, and the column you came from is the only destination
     * a user can predict without being asked: the drawer opens over a category and that category
     * is still the selected one while it is up.
     *
     * Writes through [AppCategoryRepository.addToCategory], the same call the category's own "Add
     * Apps" picker makes, so an app pinned from here and one picked there are the same row.
     *
     * Refuses out loud rather than writing a row nothing will draw. Three columns do not read
     * [AppCategoryRepository.appsForCategory] at all, so an app assigned to one of them is in the
     * database and on no screen:
     *  - a gaming category builds its column from the games table,
     *  - Last Played is derived from last_played_at and nothing can be assigned to it (it is
     *    `isGamingCategory = false`, so that flag alone does not catch it),
     *  - Settings builds its own hierarchy.
     */
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
                    // Only the rows that have something to say get a second line. Eleven identical
                    // "Fixed color preset" sublabels told the user nothing eleven times, pushed
                    // the last row off the bottom of the panel, and buried the one line that IS
                    // information — the month-changing scheme — in the middle of them.
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
        // The Save-as-Theme dialog is opened from INSIDE this editor, so it cannot outlive it.
        // It used to: closeCustomIcons cleared only the session, leaving a blocking dialog up
        // with nothing above it in the dispatcher.
        _uiState.update { it.copy(customIconSession = null, saveThemeNameDialog = null) }
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

    // ── The launch disc ───────────────────────────────────────────────────────
    //
    // Modelled on GameBootGate, and deliberately NOT a copy of it: that gate is a singleton
    // because LaunchDispatcher lives outside this ViewModel, and every caller of this one is a
    // method a few hundred lines up. A second singleton for three local call sites would be
    // machinery, not structure.
    //
    // Games are absent on purpose. They already have GameBoot, whose switch means "a presentation
    // plays" or "the launch is silent by decision" — a disc on top of either answer contradicts
    // the one the user gave.

    // The state is the GATE's now, mirrored here so the shell reads one ui state as it does for
    // everything else. Video Detail raises discs too, and the shell draws above it, so the request
    // cannot live in this ViewModel's private field any more.
    private fun observeMediaLaunch() {
        viewModelScope.launch {
            mediaLaunchGate.active.collect { request ->
                _uiState.update { it.copy(discCeremony = request?.let { r -> DiscCeremonyState(r.art) }) }
            }
        }
    }

    private suspend fun awaitDiscHandOff(art: Any?) = mediaLaunchGate.awaitHandOff(art)

    /**
     * Opens an installed app behind the disc.
     *
     * Every app row goes through here rather than calling the repository directly — there were
     * five such calls, one per column that can host an app shortcut, and a ceremony wired into
     * four of them is the bug this shape exists to prevent.
     */
    /**
     * The art of whatever the cursor is on — for the paths that act on the selected row without
     * being handed it, like a context menu's Launch. The menu was raised FROM this row, so its
     * face is the row's own.
     */
    private fun selectedItemArt(): Any? =
        _uiState.value.currentItems.getOrNull(_uiState.value.selectedItemIndex)?.shelfCoverArt

    private fun launchAppWithDisc(packageName: String, art: Any?) {
        viewModelScope.launch {
            awaitDiscHandOff(art ?: appLauncherIcon(packageName))
            appCategoryRepository.launch(packageName)
        }
    }

    /**
     * The disc's face for an app that has no artwork of its own.
     *
     * [XMBItem.shelfCoverArt] is a list of URI columns, and an app row only has any of them when
     * the user has assigned artwork to that package. Most have not — so the art handed to the
     * ceremony was null and the disc spun up blank, while the row it was launched from showed the
     * app's icon perfectly well. The row draws that icon from the package manager, not from a
     * URI, which is why the two disagreed.
     *
     * The gate takes `Any?` and the ceremony passes anything that is not a String straight to the
     * image, so a Drawable is already a face it can draw. Nothing new is needed but the lookup.
     *
     * Null on failure rather than throwing: an app that cannot even be asked for its icon is one
     * that is probably about to fail to launch, and the launch should be what reports that.
     */
    private fun appLauncherIcon(packageName: String): Any? =
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

    /** The disc has started fading out: whatever was waiting on it may now open. */
    fun onDiscCeremonyHandOff() = mediaLaunchGate.onHandOff()

    /** The disc has finished fading and should come off the screen. */
    fun onDiscCeremonyFinished() = mediaLaunchGate.onDismissed()

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
     * The GameBoot disc has spun up and the game may start NOW, while it is still on screen.
     *
     * Only the disc calls this. The built-in title card and a user clip both release the launch at
     * their end, because nothing is drawn behind them worth revealing; the disc's whole point is
     * that the emulator's cold start happens under the spin, so it releases early and stays up.
     * Releasing twice is harmless — the gate completes its deferred once.
     */
    fun onGameBootHandOff() {
        if (!_uiState.value.gameBootIsPreview) gameBootGate.onPresentationFinished()
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
            // Both, and in this order: a title card or a clip is releasing the launch for the
            // first time here, while the disc already released it at its hand-off and is only
            // reporting that it has finished fading. complete() on a spent deferred is a no-op.
            gameBootGate.onPresentationFinished()
            gameBootGate.onPresentationDismissed()
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
                    customAudioPath = uiMediaStore.pathFor(
                        com.psplauncher.core.domain.model.UiMediaSlot.GAMEBOOT_AUDIO,
                    ),
                    defaultUri = com.psplauncher.core.ui.media.gameBootDefaultAudioUri(context.packageName),
                )
            }
            // A real cover, because the built-in presentation is a disc now and a preview of a
            // blank one tells the user nothing about what they are switching on. Any game in the
            // library with art will do — this is a sample, not a launch.
            val previewArt = if (video != null) null else runCatching {
                gameRepository.observeAllGames().first().firstNotNullOfOrNull { it.discFaceUri }
            }.getOrNull()
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
                        coverArt = previewArt,
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
            iconDisplayPreferences.itemBackdropFlow.collect { on ->
                _uiState.update { it.copy(itemBackdropEnabled = on) }
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
     * The focused row's colour, following the cursor.
     *
     * Every category, not just Games: an album cover, a video thumbnail, a photo and a book
     * jacket all colour the shell and back it exactly the way a game's key art does. Which rows
     * qualify is [XMBItem.backdropArt]'s answer, so there is one rule rather than one per
     * library.
     *
     * Debounced, and that is the whole difference from the detail page's version of this: there
     * a page opens on ONE entry, here the cursor can cross forty of them in a second, and
     * decoding every one of those would be forty bitmaps nobody ever sees. The wait is
     * deliberately shorter than the video snap's -- a colour settling in is cheap and reads as
     * the screen catching up, where a video starting is an event.
     */
    private fun observeFocusedItemAccent() {
        viewModelScope.launch {
            _uiState
                .map { s -> s.currentItems.getOrNull(s.selectedItemIndex)?.takeIf { it.backdropArt.isNotEmpty() } }
                // By row id, not game id: every non-game row has a null game id, so comparing
                // those would report a whole music library as one unchanging item.
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
                    // collectLatest cancels this on any cursor move, so reaching here means the
                    // cursor is still on the row this was read for.
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
                    // Approve only if the snap has somewhere to draw. snapSiteFor is the one
                    // definition of that, shared with the three render sites -- the in-tile
                    // placement needs an ICON0 tile to play over, the background placement needs
                    // nothing and so plays in any icon mode, and the hover panel's video page
                    // needs neither.
                    //
                    // effectivePanelPage, not the page resolved against the available list: a
                    // game's Video tab only exists once a snap has been approved, so resolving
                    // against that list first would be a loop that never starts. Asking the
                    // walked-to page instead means "the user is sitting on Video" is itself the
                    // reason to decode -- which is how a snap reaches the panel on an icon mode
                    // that has no tile to play it over.
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
                        waveOverWallpaper    = prefs[KEY_WAVE_OVER_WALLPAPER] ?: false,
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
                // Only meaningful alongside a wallpaper that is actually there. Reading it
                // independently would leave the previous picture's colour tinting the wave after
                // its file had gone.
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

    // ── Static data ───────────────────────────────────────────────────────────

    companion object {
        private val KEY_WAVE_STYLE        = stringPreferencesKey("display_wave_style")
        // Must match DisplaySettingsViewModel — both read/write these wave power-throttle prefs.
        private val KEY_RESPECT_BATTERY   = booleanPreferencesKey("display_battery_saver")
        // The wave normally gives way to a wallpaper entirely — see XmbBackground. This keeps it, drawn
        // over the picture. Off by default: the existing behaviour is what every current install has, and
        // a setting that changes how someone's home screen looks on upgrade is a setting that arrives
        // broken.
        private val KEY_WAVE_OVER_WALLPAPER = booleanPreferencesKey("display_wave_over_wallpaper")
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
        /**
         * How long before the wave settles into its slower drift.
         *
         * Long enough not to react to a pause between two presses, short enough to have happened
         * by the time someone has stopped looking at the screen.
         */
        internal const val WAVE_IDLE_MS = 12_000L

        internal const val IDLE_HINT_POLL_MS  = 500L
        private val KEY_XMB_LAYOUT_ADJUST = stringPreferencesKey("display_xmb_layout_adjust")
        private val KEY_SETUP_COMPLETE    = booleanPreferencesKey("library_setup_complete")
        // First-run wizard: set the moment the wizard is shown (or silently seeded for installs
        // that already carry configuration), so it only ever auto-opens once.
        private val KEY_INITIAL_SETUP_SEEN = booleanPreferencesKey("initial_setup_seen")
        /**
         * The screen Back should come back to after an excursion out of [screenId], or null when
         * Back keeps its ordinary "up to the Settings root" meaning.
         *
         * A pure function because it is the one decision in the settings navigation that a later
         * change is most likely to get silently wrong: add a third wizard route and a return
         * address computed inline here would quietly not apply to it, which shows up as the
         * wizard dumping you at the root rather than as anything that looks like a bug.
         */
        internal fun returnAddressFor(screenId: String?): String? =
            screenId.takeIf { it in WIZARD_SCREEN_IDS }

        /** Every route that IS the setup wizard. The one list both the stamp and Back read. */
        internal val WIZARD_SCREEN_IDS: Set<String>
            get() = setOf(INITIAL_SETUP_SCREEN_ID, INITIAL_SETUP_FIRST_RUN_SCREEN_ID)

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
        // Must match DisplaySettingsViewModel.KEY_FADE_BY_DISTANCE — both read/write this pref.
        //
        // A NEW key, not a rename of display_solid_unfocused_icons. That one answered "dim at all?"
        // and this one answers "dim flat or by distance?", so a stored true carries no opinion about
        // the new question and reusing the key would silently reinterpret it. The old key is left
        // where it is, inert, rather than migrated to a value it never meant.
        private val KEY_FADE_BY_DISTANCE = booleanPreferencesKey("display_fade_by_distance")

        // Must match DisplaySettingsViewModel.KEY_CARD_ART_GRID — both read/write this pref.
        private val KEY_CARD_ART_GRID = booleanPreferencesKey("display_card_art_grid")
        // Must match DisplaySettingsViewModel.KEY_RECENTS_INCLUDE_APPS.
        private val KEY_RECENTS_INCLUDE_APPS = booleanPreferencesKey("display_recents_include_apps")
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
        /**
         * Past this much of a video, "resume" would be offering to rewatch the credits.
         *
         * NOT the same rule as videoProgressFraction's, and deliberately not. That one refuses a
         * fraction of 1.0 or more, because a resume point past the end is a stale stamp and there
         * is no bar to draw for it. This one decides whether a video is worth LEADING the column
         * with, which is a different question: a film at 98% still draws a perfectly good bar in a
         * listing, and still should not be the first thing the Video column offers.
         *
         * If these two are ever collapsed into one number, the column starts offering to resume
         * things that are finished.
         */
        private const val RESUME_DONE_FRACTION = 0.97f
        private const val ALL_GAMES_PLATFORM_ID = "__all_games__"
        private const val FAVORITES_ITEM_ID = "favorites_folder"
        /**
         * UNREACHABLE as of 2026-09-24 and left in deliberately, for one release.
         *
         * Favorites moved to the Shelves column, so nothing sets this any more — the branches
         * that read it (the drill-in, the title, the hide location, the sibling walk) are dead
         * paths that still look live. They are not deleted yet because `BuiltInCategory.FAVORITES`
         * is a legacy CATEGORY id an older database can still carry, and untangling which of
         * those five readers serves that row and which served the card is a sweep of its own
         * rather than a line in this change.
         */
        internal const val FAVORITES_PLATFORM_ID = "__favorites__"
        private const val MISSING_ITEM_ID = "missing_folder"
        internal const val MISSING_PLATFORM_ID = "__missing__"
        private const val EMPTY_MISSING_ITEM_ID = "empty_missing"
        // Shown as each missing row's subtitle. Phrased around the scan rather than the file
        // ("File not found" alone reads as permanent) because dropping the file back reactivates it.
        private const val MISSING_REASON = "File not found on last scan"
        private const val ADD_APPS_ITEM_ID = "add_apps"
        /**
         * The home shelf's app rows, and the only rows with this prefix.
         *
         * Named once because two things read it now: the builder that makes the id and the
         * context menu asking whether a row came off the shelf. A literal in both places is
         * the pair that stops agreeing the day the prefix changes, and the symptom would be
         * a menu quietly missing one item.
         */
        internal const val RECENT_APP_ID_PREFIX = "recentapp_"
        private const val ADD_GAMES_ITEM_ID = "add_games"
        private const val FIND_GAMES_ITEM_ID = "find_games"
        // Platform id whose library is built from installed apps (picker) instead of ROM scans.
        // Sentinel platform for app rows that merely BACK a category app's artwork / favorite /
        // collection membership. They reference an app by package but are NOT in the Android
        // library, so they use this id instead of "android" to stay out of observeByPlatform.
        private const val APP_SHORTCUT_PLATFORM_ID = "app_shortcut"
        // Virtual card holding PC-launcher game imports (harvest / folder scan / add-by-ID).

        // Music category synthetic rows / drill ids.
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
        // The "Apps" sections (Music / Video / Photo) are backed by the REAL built-in media
        // categories — not hidden pseudo-categories. Apps auto-populate from the classifier
        // (installed music / video / photo apps) and any manual picks live in the same real
        // category, so there is nothing hidden for users to tamper with in Category settings.
        internal const val MUSIC_APPS_CATEGORY_ID = "music"
        // Video root item ids.
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
        // Photo root item ids.
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
        // How far back Last Played reaches. A "what was I doing" shelf, not an archive: past
        // twenty rows nobody is recognising a game by having played it recently.
        private const val RECENTLY_PLAYED_LIMIT = 20
        internal const val ADD_MENU_ITEM_ID = "add_menu"
        internal const val QUICK_SEARCH_ITEM_ID = "quick_search"
        internal const val SEARCH_ITEM_ID = "library_search"
        // Per library, not overall. A global search that returned two hundred games and nothing
        // else would bury the one book you were looking for.
        private const val SEARCH_RESULTS_PER_LIBRARY = 40
        private const val NETWORK_CATEGORY_ID = "network"
        // Reader apps are stored under the Library category's own id, the same convention the
        // other media categories use for their app rows.
        private const val LIBRARY_APPS_CATEGORY_ID = BuiltInCategory.LIBRARY
        private const val ADD_PHOTO_APPS_ITEM_ID = "add_photo_apps"
        private const val PHOTO_APPS_CATEGORY_ID = "photos"
        // Generic memory-card art for the "Music" (All Music) item — the physical-media default
        // PNG, loaded from assets via Coil (same convention as PhysicalMediaIcon).
        // `internal` because XMBItem.backdropArt has to exclude it: a row wearing the shared
        // folder icon must not colour the shell.
        internal const val MEMORY_CARD_ASSET_URI =
            "file:///android_asset/systems/physical-media/_default.png"
        // Sentinel in XMBContextMenu.musicTrackId marking the in-app player's own options menu.
        private const val MUSIC_PLAYER_MENU_MARKER = "__music_player__"

        /**
         * The crossbar's cold-start list. NOT a second copy any more.
         *
         * It used to be a byte-for-byte duplicate of the repository's built-in table, and the
         * two drifted: Library was added there and not here. That was not just a cold-start
         * difference -- canonicalXmbCategories derives its built-in ids from THIS list, so a
         * category missing from it fell through to the custom path and lost the canonical icon,
         * and observeCategoryBar falls back to it on an empty read, where the section vanished
         * from the bar outright. There is one list now, in core-domain.
         */
        val FALLBACK_CATEGORIES: List<Category> =
            com.psplauncher.core.domain.model.BUILT_IN_CATEGORIES

        /**
         * The Android column's rows, built FROM AppFilter rather than beside it.
         *
         * They were a hand-kept copy: the same four sections, in a different order, with their
         * own labels and subtitles. The row id is "drawer_" + the filter's name, which is how a
         * row round-trips back to the filter it opens (see the ANDROID branch of onItemSelected),
         * so the two were already joined by a string convention with nothing checking it. Now the
         * list, the order and the wording all come from one place.
         */
        private val ANDROID_ITEMS = com.psplauncher.feature.appbar.AppFilter.entries.map { filter ->
            XMBItem(
                id = "drawer_${filter.name.lowercase()}",
                title = filter.label,
                subtitle = filter.subtitle,
            )
        }

        // First item opens the device's own Settings app (not a PFP screen).
        internal const val ANDROID_SETTINGS_ITEM_ID = "settings_android_system"

        // Opens the settings screens. One row, not a tree.
        internal const val OPEN_SETTINGS_ITEM_ID = "settings_open"

        /**
         * The Settings column: open the settings, or open Android's.
         *
         * It used to be the six sections, each drilling into its own screens: three presses to
         * reach Library Manager. The screens have carried the whole tree in their own rail since
         * the rail was added, so the column was a second way to walk a tree that is already on
         * screen once you arrive. One press now, and the rail does the walking.
         *
         * Android Settings stays a row of its own. It is not one of PSPLauncher's screens, it is
         * not in SETTINGS_CATALOG, and the rail is built from that catalog -- so folding it in
         * would mean inventing a rail entry that routes nowhere. It is a different destination
         * and it reads as one.
         *
         * `internal` so the hierarchy unit tests can assert the exact root order.
         */
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

    /**
     * Where the launcher opens: Last Played, which is the home.
     *
     * It used to be Games. Last Played is the leftmost column and is now a full page that hides
     * the crossbar, so opening there means the launcher greets you with what you were playing
     * rather than with a list to walk. Games remains the fallback for a bar that has had Last
     * Played hidden, and index 0 for one that has neither.
     */
    private fun defaultXmbCategoryIndex(categories: List<Category>): Int =
        categories.indexOfFirst { it.id == BuiltInCategory.RECENTLY_PLAYED }
            .takeIf { it >= 0 }
            ?: categories.indexOfFirst { it.id == BuiltInCategory.GAMES }
                .takeIf { it >= 0 }
            ?: 0
}
