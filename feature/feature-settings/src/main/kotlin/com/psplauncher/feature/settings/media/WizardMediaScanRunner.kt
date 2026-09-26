package com.psplauncher.feature.settings.media

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.psplauncher.core.data.repository.MediaRootKind
import com.psplauncher.core.data.repository.MediaRootRepository
import com.psplauncher.core.domain.repository.MusicRepository
import com.psplauncher.core.domain.repository.PhotoRepository
import com.psplauncher.core.domain.repository.VideoRepository
import com.psplauncher.core.ui.notification.BackgroundTaskNotifier
import com.psplauncher.feature.library.scanner.MusicScanResult
import com.psplauncher.feature.library.scanner.MusicScanner
import com.psplauncher.feature.library.scanner.PhotoScanResult
import com.psplauncher.feature.library.scanner.PhotoScanner
import com.psplauncher.feature.library.scanner.VideoScanResult
import com.psplauncher.feature.library.scanner.VideoScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WizardMediaScanRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRootRepository: MediaRootRepository,
    private val musicRepository: MusicRepository,
    private val musicScanner: MusicScanner,
    private val photoRepository: PhotoRepository,
    private val photoScanner: PhotoScanner,
    private val videoRepository: VideoRepository,
    private val videoScanner: VideoScanner,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notifier = BackgroundTaskNotifier(context)
    private val inFlight = ConcurrentHashMap<MediaRootKind, Job>()

    fun kickoff(kind: MediaRootKind) {
        if (inFlight[kind]?.isActive == true) return
        inFlight[kind] = scope.launch {
            runCatching { scan(kind) }
                .onFailure { Timber.w(it, "Wizard %s scan failed", kind.name) }
        }
    }

    private suspend fun scan(kind: MediaRootKind) {
        val roots = mediaRootRepository.getAll(kind)
        when (kind) {
            MediaRootKind.MUSIC -> {
                dropOrphanMusicLibraries(roots); roots.forEach { scanMusic(it) }
            }
            MediaRootKind.PHOTO -> {
                dropOrphanPhotoLibraries(roots); roots.forEach { scanPhoto(it) }
            }
            MediaRootKind.VIDEO -> {
                dropOrphanVideoLibraries(roots); roots.forEach { scanVideo(it) }
            }

            MediaRootKind.BOOK -> Unit
        }
    }

    private suspend fun dropOrphanMusicLibraries(roots: List<String>) {
        musicRepository.getFolders()
            .filter { it.treeUri !in roots }
            .forEach { musicRepository.removeFolder(it.id) }
    }

    private suspend fun dropOrphanPhotoLibraries(roots: List<String>) {
        photoRepository.getLibraries()
            .filter { it.treeUri !in roots }
            .forEach { photoRepository.removeLibrary(it.id) }
    }

    private suspend fun dropOrphanVideoLibraries(roots: List<String>) {
        videoRepository.getLibraries()
            .filter { it.treeUri !in roots }
            .forEach { videoRepository.removeLibrary(it.id) }
    }

    private suspend fun scanMusic(root: String) {
        val folders = musicRepository.getFolders()
        val existingRow = folders.firstOrNull { it.treeUri == root }
        val folder = existingRow ?: musicRepository.addFolder(displayName(root, "Music"), root)
        val target = musicRepository.getFolder(folder.id) ?: folder

        val taskId = "music_scan_${target.id}"
        notifier.running(taskId, "Scanning ${target.displayName}", null)
        val existing = musicRepository.observeTracksByFolder(target.id).first()
        musicScanner.scan(target, deep = false, existing = existing).collect { result ->
            when (result) {
                is MusicScanResult.Progress -> Unit
                is MusicScanResult.Complete -> {
                    musicRepository.replaceTracksForFolder(result.folderId, result.tracks, System.currentTimeMillis())
                    notifier.complete(taskId, "Scanned ${target.displayName}", "${result.tracks.size} tracks")
                }
                is MusicScanResult.Error -> notifier.failed(taskId, "Scan failed", result.message)
            }
        }
    }

    private suspend fun scanPhoto(root: String) {
        val libs = photoRepository.getLibraries()
        val existingRow = libs.firstOrNull { it.treeUri == root }
        val library = existingRow ?: photoRepository.addLibrary(displayName(root, "Photos"), root, scanRecursively = true)
        val target = photoRepository.getLibrary(library.id) ?: library

        val taskId = "photo_scan_${target.id}"
        notifier.running(taskId, "Scanning ${target.displayName}", null)
        photoScanner.scan(target, deep = false, existing = photoRepository.getPhotosForLibrary(target.id)).collect { result ->
            when (result) {
                is PhotoScanResult.Progress -> Unit
                is PhotoScanResult.Complete -> {
                    photoRepository.replacePhotosForLibrary(result.libraryId, result.photos, System.currentTimeMillis())
                    notifier.complete(taskId, "Scanned ${target.displayName}", "${result.photos.size} photos")
                }
                is PhotoScanResult.Error -> notifier.failed(taskId, "Scan failed", result.message)
            }
        }
    }

    private suspend fun scanVideo(root: String) {
        val libs = videoRepository.getLibraries()
        val existingRow = libs.firstOrNull { it.treeUri == root }
        val library = existingRow ?: videoRepository.addLibrary(displayName(root, "Videos"), root, scanRecursively = true)
        val target = videoRepository.getLibrary(library.id) ?: library

        val taskId = "video_scan_${target.id}"
        notifier.running(taskId, "Scanning ${target.displayName}", null)
        videoScanner.scan(target, deep = false, existing = videoRepository.getVideosForLibrary(target.id)).collect { result ->
            when (result) {
                is VideoScanResult.Progress -> Unit
                is VideoScanResult.Complete -> {
                    videoRepository.replaceVideosForLibrary(result.libraryId, result.videos, System.currentTimeMillis())
                    notifier.complete(taskId, "Scanned ${target.displayName}", "${result.videos.size} videos")
                }
                is VideoScanResult.Error -> notifier.failed(taskId, "Scan failed", result.message)
            }
        }
    }

    private fun displayName(treeUri: String, fallback: String): String =
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.name }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Uri.parse(treeUri).lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?: fallback
}
