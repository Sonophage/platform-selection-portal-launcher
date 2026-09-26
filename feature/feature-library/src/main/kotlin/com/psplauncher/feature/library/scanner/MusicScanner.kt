package com.psplauncher.feature.library.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.psplauncher.core.data.music.AudioFileFilter
import com.psplauncher.core.data.saf.SafChild
import com.psplauncher.core.data.saf.hasNoMediaMarker
import com.psplauncher.core.data.saf.isIgnoredDir
import com.psplauncher.core.data.saf.querySafChildren
import com.psplauncher.core.data.saf.safScanStartDocId
import com.psplauncher.core.domain.model.MusicFolder
import com.psplauncher.core.domain.model.MusicTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed interface MusicScanResult {
    data class Progress(val folderName: String, val filesSeen: Int, val tracksFound: Int) : MusicScanResult
    data class Complete(val folderId: String, val tracks: List<MusicTrack>) : MusicScanResult
    data class Error(val folderId: String, val message: String) : MusicScanResult
}

internal fun canReuseMusicMetadata(
    prior: MusicTrack?,
    lastModified: Long?,
    artExists: (String) -> Boolean,
): Boolean {
    if (prior == null) return false
    if (prior.lastModified != lastModified) return false
    if (prior.albumArtist == null) return false
    return musicArtStillOnDisk(prior.artUri, artExists)
}

internal fun musicArtStillOnDisk(artUri: String?, exists: (String) -> Boolean): Boolean {
    if (artUri.isNullOrBlank()) return true

    val path = artUri.trim().removePrefix("file://").takeIf { it.startsWith("/") } ?: return false
    return exists(path)
}

@Singleton
class MusicScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scan(
        folder: MusicFolder,
        deep: Boolean = true,
        existing: List<MusicTrack> = emptyList(),
    ): Flow<MusicScanResult> = flow {
        val treeUri = runCatching { Uri.parse(folder.treeUri) }.getOrNull()
        val root = treeUri?.let { DocumentFile.fromTreeUri(context, it) }
        if (treeUri == null || root == null || !root.canRead()) {
            emit(MusicScanResult.Error(folder.id, "Permission lost, re-select folder."))
            return@flow
        }

        val startMs = System.currentTimeMillis()
        Timber.i("Music scan started: \"${folder.displayName}\" (${folder.treeUri})")
        val tracks = mutableListOf<MusicTrack>()
        val byUri = existing.associateBy { it.uri }

        val artByAlbum = HashMap<String, String?>()
        var filesSeen = 0

        val stack = ArrayDeque<Pair<String, String>>()
        stack.addLast(safScanStartDocId(context, treeUri) to "")
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (dirDocId, relPath) = stack.removeLast()
            val children = context.contentResolver.querySafChildren(treeUri, dirDocId)
            if (children.hasNoMediaMarker()) continue
            for (child in children) {
                coroutineContext.ensureActive()
                if (child.isDirectory) {
                    if (child.isIgnoredDir()) continue
                    stack.addLast(child.documentId to if (relPath.isEmpty()) child.name else "$relPath/${child.name}")
                    continue
                }
                filesSeen++
                val track = runCatching { child.toTrackOrNull(folder.id, relPath, artByAlbum, deep, byUri) }
                    .getOrElse { Timber.w(it, "Skipping unreadable file ${child.uri}"); null }
                if (track != null) tracks.add(track)

                if (filesSeen % 25 == 0) {
                    emit(MusicScanResult.Progress(folder.displayName, filesSeen, tracks.size))
                }
            }
        }

        val took = System.currentTimeMillis() - startMs
        Timber.i("Music scan complete: \"${folder.displayName}\" — ${tracks.size} tracks from $filesSeen files in ${took}ms")
        emit(MusicScanResult.Complete(folder.id, tracks))
    }.flowOn(Dispatchers.IO)

    private fun SafChild.toTrackOrNull(
        folderId: String,
        relPath: String,
        artByAlbum: MutableMap<String, String?>,
        deep: Boolean,
        existingByUri: Map<String, MusicTrack>,
    ): MusicTrack? {
        if (!AudioFileFilter.isAudio(name, mime)) return null

        val prior = existingByUri[uri.toString()]

        if (prior != null && !deep && canReuseMusicMetadata(prior, lastModified) { artStillOnDisk(it) }) {
            return prior.copy(folderId = folderId, relativePath = relPath.takeIf { it.isNotEmpty() })
        }

        val trackId = prior?.id ?: UUID.randomUUID().toString()
        val meta = readMetadata(uri)

        val albumKey = "${meta?.artist.orEmpty()}|${meta?.album.orEmpty()}"
            .takeIf { meta?.album?.isNotBlank() == true }
        val artUri = if (albumKey != null && artByAlbum.containsKey(albumKey)) {
            artByAlbum[albumKey]
        } else {
            val cached = cacheAlbumArt(meta?.artwork, albumKey ?: trackId)
            if (albumKey != null) artByAlbum[albumKey] = cached
            cached
        }

        return MusicTrack(
            id = trackId,
            folderId = folderId,
            uri = uri.toString(),
            displayName = name,
            title = meta?.title,
            artist = meta?.artist,
            album = meta?.album,
            albumArtist = meta?.albumArtist,
            durationMs = meta?.durationMs,
            mimeType = mime ?: meta?.mimeType,
            sizeBytes = sizeBytes,
            lastModified = lastModified,
            trackNumber = meta?.trackNumber,
            relativePath = relPath.takeIf { it.isNotEmpty() },
            artUri = artUri,
        )
    }

    private data class TrackMeta(
        val title: String?,
        val artist: String?,

        val albumArtist: String,
        val album: String?,
        val durationMs: Long?,
        val trackNumber: Int?,
        val mimeType: String?,
        val artwork: ByteArray?,
    )

    private fun readMetadata(uri: Uri): TrackMeta? = runCatching {
        MediaMetadataRetriever().use { mmr ->
            mmr.setDataSource(context, uri)
            TrackMeta(
                title = mmr.str(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = mmr.str(MediaMetadataRetriever.METADATA_KEY_ARTIST),

                albumArtist = mmr.str(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).orEmpty(),
                album = mmr.str(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = mmr.str(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                trackNumber = mmr.str(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.trim()?.toIntOrNull(),
                mimeType = mmr.str(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                artwork = runCatching { mmr.embeddedPicture }.getOrNull(),
            )
        }
    }.getOrNull()

    private fun MediaMetadataRetriever.str(key: Int): String? =
        runCatching { extractMetadata(key)?.takeIf { it.isNotBlank() } }.getOrNull()

    private val artCacheDir: File by lazy {
        File(context.filesDir, "music_art").apply { mkdirs() }
    }

    private fun artStillOnDisk(artUri: String?): Boolean =
        musicArtStillOnDisk(artUri) { path -> runCatching { File(path).exists() }.getOrDefault(false) }

    private fun cacheAlbumArt(bytes: ByteArray?, key: String): String? {
        if (bytes == null || bytes.isEmpty()) return null
        return runCatching {
            val file = File(artCacheDir, "${sha1(key)}.img")
            if (!file.exists()) file.writeBytes(bytes)
            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
