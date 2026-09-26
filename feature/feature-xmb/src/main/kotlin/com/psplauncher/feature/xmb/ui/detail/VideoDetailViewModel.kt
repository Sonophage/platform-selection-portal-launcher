package com.psplauncher.feature.xmb.ui.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.Video
import com.psplauncher.core.domain.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import com.psplauncher.core.ui.components.moved
import com.psplauncher.core.ui.components.chose
import com.psplauncher.core.ui.components.MenuState
import com.psplauncher.core.ui.components.MenuSelect
import com.psplauncher.core.ui.components.MenuRow
import com.psplauncher.core.ui.components.MenuGroup

enum class VideoDetailAction(val label: String, val group: MenuGroup = MenuGroup.MAIN) {
    PLAY("Play"),
    RESUME("Resume"),
    RESTART("Start from Beginning"),
    INFO("Information"),
    FAVORITE("Favorite", MenuGroup.LIBRARY),
    PLAYLIST("Add to Playlist", MenuGroup.LIBRARY),
    RENAME("Rename Title", MenuGroup.SETTINGS),
    THUMBNAIL("Change Thumbnail", MenuGroup.SETTINGS),
    LOCATION("Open File Location", MenuGroup.SETTINGS),
    REMOVE("Remove From Library", MenuGroup.REMOVE),
}

data class VideoPlaylistOption(val id: Long, val name: String, val checked: Boolean)

data class VideoDetailUiState(
    val video: Video? = null,
    val siblings: List<Video> = emptyList(),
    val isLoading: Boolean = true,

    val mainFocus: Int = 0,
    val showOptions: Boolean = false,
    val optionsIndex: Int = 0,
    val confirmRemove: Boolean = false,
    val isEditingTitle: Boolean = false,
    val titleText: String = "",
    val infoVisible: Boolean = false,
    val actionMessage: String? = null,

    val pickThumbnail: Boolean = false,

    val showPlaylistPicker: Boolean = false,
    val playlistOptions: List<VideoPlaylistOption> = emptyList(),
    val playlistPickerIndex: Int = 0,
    val creatingPlaylist: Boolean = false,
    val newPlaylistName: String = "",

    val playing: Boolean = false,
    val playStartPositionMs: Long = 0,

    val handedOffToPlayer: Boolean = false,
    val launchError: String? = null,
    val closed: Boolean = false,
) {
    val hasResume: Boolean get() = (video?.resumePositionMs ?: 0) > 0

    val primaryActions: List<VideoDetailAction>
        get() = if (hasResume) listOf(VideoDetailAction.RESUME, VideoDetailAction.RESTART)
                else listOf(VideoDetailAction.PLAY)

    val optionsMenu: MenuState<VideoDetailAction>
        get() = MenuState(
            title = "Options",
            rows = optionsActions.map {
                MenuRow(
                    action = it,
                    label = if (it == VideoDetailAction.FAVORITE) {
                        if (video?.isFavorite == true) "Remove from Favorites" else "Add to Favorites"
                    } else it.label,
                    group = it.group,
                    isDestructive = it == VideoDetailAction.REMOVE,
                    confirms = false,
                )
            },
            selectedIndex = optionsIndex,
        )

    val optionsActions: List<VideoDetailAction>
        get() = VideoDetailAction.entries.filter {
            when (it) {
                VideoDetailAction.PLAY    -> !hasResume
                VideoDetailAction.RESUME  -> hasResume
                VideoDetailAction.RESTART -> hasResume
                else                      -> true
            }
        }
}

private const val RESUME_END_EPSILON_MS = 5_000L

@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,
    private val intentResolver: com.psplauncher.core.data.video.VideoIntentResolver,
    private val mediaLaunchGate: com.psplauncher.core.data.launch.MediaLaunchGate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VideoDetailUiState())
    val uiState: StateFlow<VideoDetailUiState> = _uiState.asStateFlow()

    fun loadVideo(id: String) {
        viewModelScope.launch {
            _uiState.update { VideoDetailUiState(isLoading = true) }
            val video = videoRepository.getVideo(id)
            val siblings = video?.let { videoRepository.getVideosForLibrary(it.libraryId) }
                ?.sortedBy { it.displayTitle.lowercase() }
                ?: emptyList()
            _uiState.update {
                it.copy(video = video, siblings = siblings, isLoading = false, mainFocus = 0)
            }
        }
    }

    fun handleGamepadAction(action: GamepadAction) {
        val s = _uiState.value

        if (s.handedOffToPlayer) return
        if (s.launchError != null) {
            if (action == GamepadAction.SELECT || action == GamepadAction.BACK) dismissLaunchError()
            return
        }
        when {
            s.confirmRemove -> when (action) {
                GamepadAction.SELECT -> confirmRemove()
                GamepadAction.BACK   -> _uiState.update { it.copy(confirmRemove = false) }
                else -> Unit
            }
            s.creatingPlaylist -> if (action == GamepadAction.BACK) {
                _uiState.update { it.copy(creatingPlaylist = false, newPlaylistName = "") }
            }
            s.showPlaylistPicker -> {
                val count = s.playlistOptions.size + 1
                when (action) {
                    GamepadAction.NAVIGATE_UP   -> _uiState.update { it.copy(playlistPickerIndex = (it.playlistPickerIndex - 1 + count) % count) }
                    GamepadAction.NAVIGATE_DOWN -> _uiState.update { it.copy(playlistPickerIndex = (it.playlistPickerIndex + 1) % count) }
                    GamepadAction.SELECT        -> activatePlaylistPickerRow(s.playlistPickerIndex)
                    GamepadAction.BACK          -> _uiState.update { it.copy(showPlaylistPicker = false) }
                    else -> Unit
                }
            }
            s.infoVisible -> if (action == GamepadAction.SELECT || action == GamepadAction.BACK) {
                _uiState.update { it.copy(infoVisible = false) }
            }
            s.isEditingTitle -> if (action == GamepadAction.BACK) cancelTitleEdit()
            s.showOptions -> {
                when (action) {
                    GamepadAction.NAVIGATE_UP   -> _uiState.update { it.copy(optionsIndex = it.optionsMenu.moved(-1).selectedIndex ?: 0) }
                    GamepadAction.NAVIGATE_DOWN -> _uiState.update { it.copy(optionsIndex = it.optionsMenu.moved(+1).selectedIndex ?: 0) }
                    GamepadAction.SELECT        -> onOptionRowActivated(s.optionsIndex)
                    GamepadAction.BACK          -> _uiState.update { it.copy(showOptions = false) }
                    else -> Unit
                }
            }
            else -> {
                val total = s.primaryActions.size.coerceAtLeast(1)
                when (action) {
                    GamepadAction.NAVIGATE_UP   -> _uiState.update { it.copy(mainFocus = (it.mainFocus - 1 + total) % total) }
                    GamepadAction.NAVIGATE_DOWN -> _uiState.update { it.copy(mainFocus = (it.mainFocus + 1) % total) }
                    GamepadAction.SELECT -> s.primaryActions.getOrNull(s.mainFocus)?.let { activate(it) }
                    GamepadAction.BACK -> _uiState.update { it.copy(closed = true) }
                    GamepadAction.OPEN_CONTEXT_MENU -> openOptions()
                    else -> Unit
                }
            }
        }
    }

    fun openOptions() = _uiState.update { it.copy(showOptions = true, optionsIndex = 0) }
    fun closeOptions() = _uiState.update { it.copy(showOptions = false) }

    fun onOptionRowActivated(index: Int) {
        val chosen = _uiState.value.optionsMenu.chose(index)
        if (chosen is MenuSelect.Run) activate(chosen.action)
    }

    fun activate(action: VideoDetailAction) {
        _uiState.update { it.copy(showOptions = false) }
        val video = _uiState.value.video ?: return
        when (action) {
            VideoDetailAction.PLAY    -> play(0)
            VideoDetailAction.RESUME  -> play(video.resumePositionMs)
            VideoDetailAction.RESTART -> play(0)
            VideoDetailAction.FAVORITE -> toggleFavorite()
            VideoDetailAction.PLAYLIST -> openPlaylistPicker()
            VideoDetailAction.RENAME  -> startEditTitle()
            VideoDetailAction.THUMBNAIL -> _uiState.update { it.copy(pickThumbnail = true) }
            VideoDetailAction.INFO    -> _uiState.update { it.copy(infoVisible = true) }
            VideoDetailAction.LOCATION -> showMessage(video.relativePath?.let { "In: $it" } ?: video.displayName)
            VideoDetailAction.REMOVE  -> _uiState.update { it.copy(confirmRemove = true) }
        }
    }

    fun toggleFavorite() {
        val v = _uiState.value.video ?: return
        viewModelScope.launch {
            val next = !v.isFavorite
            videoRepository.setFavorite(v.id, next)
            _uiState.update { it.copy(video = it.video?.copy(isFavorite = next), actionMessage = if (next) "Added to Favorites" else "Removed from Favorites") }
        }
    }

    fun openPlaylistPicker() {
        val v = _uiState.value.video ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showPlaylistPicker = true, playlistPickerIndex = 0, playlistOptions = buildPlaylistOptions(v.id)) }
        }
    }

    private suspend fun buildPlaylistOptions(videoId: String): List<VideoPlaylistOption> {
        val memberOf = videoRepository.getPlaylistIdsForVideo(videoId).toSet()
        return videoRepository.observePlaylists().first().map {
            VideoPlaylistOption(id = it.id, name = it.name, checked = it.id in memberOf)
        }
    }

    fun onPlaylistRowClick(index: Int) {
        _uiState.update { it.copy(playlistPickerIndex = index) }
        activatePlaylistPickerRow(index)
    }

    private fun activatePlaylistPickerRow(index: Int) {
        val s = _uiState.value
        val v = s.video ?: return
        if (index >= s.playlistOptions.size) {
            _uiState.update { it.copy(creatingPlaylist = true, newPlaylistName = "") }
            return
        }
        val option = s.playlistOptions.getOrNull(index) ?: return
        viewModelScope.launch {
            videoRepository.toggleVideoInPlaylist(option.id, v.id)
            _uiState.update { it.copy(playlistOptions = buildPlaylistOptions(v.id)) }
        }
    }

    fun closePlaylistPicker() = _uiState.update { it.copy(showPlaylistPicker = false) }

    fun onNewPlaylistNameChange(text: String) = _uiState.update { it.copy(newPlaylistName = text) }

    fun confirmCreatePlaylist() {
        val v = _uiState.value.video ?: return
        val name = _uiState.value.newPlaylistName.trim()
        if (name.isBlank()) { _uiState.update { it.copy(creatingPlaylist = false) }; return }
        viewModelScope.launch {
            val id = videoRepository.createPlaylist(name)
            videoRepository.addVideoToPlaylist(id, v.id)
            _uiState.update { it.copy(creatingPlaylist = false, newPlaylistName = "", playlistOptions = buildPlaylistOptions(v.id)) }
        }
    }

    fun cancelCreatePlaylist() = _uiState.update { it.copy(creatingPlaylist = false, newPlaylistName = "") }

    fun play(positionMs: Long) {
        val video = _uiState.value.video ?: return
        viewModelScope.launch {
            val pref = videoRepository.getDefaultVideoPlayer()
            if (pref == null || pref == "builtin") { startPlayback(positionMs); return@launch }

            val ask = pref == "ask"
            val pkg = if (ask) null else pref

            intentResolver.validate(video, pkg)?.let { err ->
                _uiState.update { it.copy(showOptions = false, launchError = err) }
                return@launch
            }
            _uiState.update { it.copy(showOptions = false, handedOffToPlayer = true) }

            markWatchedExternally(video)

            mediaLaunchGate.awaitHandOff(video.effectiveThumbnailUri)
            val err = if (ask) intentResolver.launchChooser(video) else intentResolver.launch(video, pref)
            if (err != null) _uiState.update { it.copy(handedOffToPlayer = false, launchError = err) }
        }
    }

    fun onReturnedFromExternal() {
        if (!_uiState.value.handedOffToPlayer) return
        _uiState.update { it.copy(handedOffToPlayer = false) }
        val id = _uiState.value.video?.id ?: return
        viewModelScope.launch {
            val fresh = videoRepository.getVideo(id)
            _uiState.update { it.copy(video = fresh ?: it.video) }
        }
    }

    fun clearExternalOverlay() = _uiState.update { it.copy(handedOffToPlayer = false) }

    fun dismissLaunchError() = _uiState.update { it.copy(launchError = null) }

    fun onClosedHandled() = _uiState.update { it.copy(closed = false) }

    fun startPlayback(positionMs: Long) {
        _uiState.update { it.copy(playing = true, playStartPositionMs = positionMs, showOptions = false) }
    }

    private suspend fun markWatchedExternally(video: Video) {
        videoRepository.setResumePosition(video.id, video.resumePositionMs, System.currentTimeMillis())
    }

    fun onPlaybackExit() {
        _uiState.update { it.copy(playing = false) }
        _uiState.value.video?.id?.let { loadVideo(it) }
    }

    fun saveResume(videoId: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            val finished = durationMs > 0 && positionMs >= durationMs - RESUME_END_EPSILON_MS
            if (finished || positionMs <= 0) videoRepository.clearResumePosition(videoId)
            else videoRepository.setResumePosition(videoId, positionMs, System.currentTimeMillis())
        }
    }

    fun startEditTitle() {
        val v = _uiState.value.video ?: return
        _uiState.update { it.copy(isEditingTitle = true, titleText = v.displayTitle) }
    }
    fun onTitleChanged(text: String) = _uiState.update { it.copy(titleText = text) }
    fun saveTitle() {
        val v = _uiState.value.video ?: return
        val newTitle = _uiState.value.titleText.trim().ifEmpty { null }
        viewModelScope.launch {
            videoRepository.setCustomTitle(v.id, newTitle)
            _uiState.update { it.copy(video = videoRepository.getVideo(v.id) ?: it.video, isEditingTitle = false) }
        }
    }
    fun cancelTitleEdit() = _uiState.update { it.copy(isEditingTitle = false) }

    fun consumeThumbnailPick() = _uiState.update { it.copy(pickThumbnail = false) }

    fun onThumbnailPicked(uri: Uri) {
        val v = _uiState.value.video ?: return
        viewModelScope.launch {
            val path = copyThumbnail(uri, v.id)
            if (path == null) { showMessage("Could not import image"); return@launch }
            videoRepository.setCustomThumbnail(v.id, path)
            _uiState.update { it.copy(video = videoRepository.getVideo(v.id) ?: it.video, actionMessage = "Thumbnail updated") }
        }
    }

    private suspend fun copyThumbnail(uri: Uri, videoId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "video_thumbs").apply { mkdirs() }
            val dest = File(dir, "custom_${videoId}_${System.currentTimeMillis()}.img")
            context.contentResolver.openInputStream(uri)?.use { inp ->
                dest.outputStream().use { out -> inp.copyTo(out) }
            } ?: return@runCatching null
            Uri.fromFile(dest).toString().takeIf { dest.length() > 0 }
        }.getOrElse { Timber.w(it, "Thumbnail import failed"); null }
    }

    fun confirmRemove() {
        val v = _uiState.value.video ?: return
        viewModelScope.launch {
            videoRepository.removeVideo(v.id)
            _uiState.update { it.copy(confirmRemove = false, closed = true) }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(actionMessage = null) }
    private fun showMessage(msg: String) = _uiState.update { it.copy(actionMessage = msg) }
}
