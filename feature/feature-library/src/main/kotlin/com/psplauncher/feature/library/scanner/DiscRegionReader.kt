package com.psplauncher.feature.library.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.psplauncher.core.data.saf.isSafeSiblingName
import com.psplauncher.core.data.saf.safSiblingDocumentId
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameRegion
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class DiscRegionReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun read(game: Game): GameRegion? = when (game.platformId) {
        "psx" -> headDetect(game, 256 * 1024, DiscRegionDetectors::detectPsx)
        "ps2" -> headDetect(game, 256 * 1024, DiscRegionDetectors::detectPs2)
        "psp" -> headDetect(game, 64 * 1024, DiscRegionDetectors::detectPsp)
        "gc", "wii" -> headDetect(game, 4 * 1024, DiscRegionDetectors::detectBootBin)
        "saturn", "dreamcast", "segacd" -> headDetect(game, 4 * 1024, DiscRegionDetectors::detectIpBin)
        "x360" -> headDetect(game, 2 * 1024 * 1024, DiscRegionDetectors::detectX360)
        "ps3" -> detectPs3(game)
        else -> null
    }

    private fun headDetect(
        game: Game,
        maxBytes: Int,
        detect: (ByteArray) -> GameRegion?,
    ): GameRegion? {
        val head = readImageHead(game, maxBytes) ?: return null
        return runCatching { detect(head) }.getOrNull()
    }

    private fun detectPs3(game: Game): GameRegion? {
        val path = game.romPath ?: return null
        if (!game.romUri.isNullOrBlank()) return null
        val dir = File(path)
        if (!dir.isDirectory) return headDetect(game, 256 * 1024) { DiscRegionDetectors.detectPs3Sfo(it) }
        val candidates = listOf(
            File(dir, "PARAM.SFO"),
            File(dir, "PS3_GAME/PARAM.SFO"),
        )
        for (sfo in candidates) {
            if (!sfo.isFile) continue
            return runCatching {
                sfo.inputStream().use { DiscRegionDetectors.detectPs3Sfo(it.readBytes()) }
            }.getOrNull()
        }
        return null
    }

    private fun readImageHead(game: Game, maxBytes: Int): ByteArray? {
        return try {
            if (game.romUri.isNullOrBlank()) {
                val file = game.romPath?.let { File(it) } ?: return null
                val image = resolveRawImageFile(file) ?: return null
                if (!image.isFile) return null
                image.inputStream().use { readAtMost(it, maxBytes) }
            } else {
                val imageUri = resolveSafImageUri(game) ?: return null
                context.contentResolver.openInputStream(imageUri)?.use { readAtMost(it, maxBytes) }
            }
        } catch (e: Exception) {
            Timber.w("Region read failed for %s: %s", game.romPath, e.message)
            null
        }
    }

    private fun readAtMost(input: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var total = 0
        while (total < maxBytes) {
            val n = input.read(buffer, total, maxBytes - total)
            if (n < 0) break
            total += n
        }
        return buffer.copyOf(total)
    }

    private fun resolveRawImageFile(file: File): File? {
        return when (file.extension.lowercase()) {
            "cue" -> {
                val names = runCatching { cueSheetReferencesRaw(file.readLines()) }.getOrNull() ?: return null
                names.firstOrNull()
                    ?.takeIf(::isSafeSiblingName)
                    ?.let { File(file.parentFile, it) }
            }
            "gdi" -> {
                val lines = runCatching { file.readLines() }.getOrNull() ?: return null
                gdiSheetTrackNamesRaw(lines).firstOrNull()
                    ?.takeIf(::isSafeSiblingName)
                    ?.let { File(file.parentFile, it) }
            }
            "m3u" -> null
            else -> file
        }
    }

    private fun resolveSafImageUri(game: Game): Uri? {
        val sheetUri = runCatching { Uri.parse(game.romUri) }.getOrNull() ?: return null
        val name = sheetUri.lastPathSegment?.substringAfterLast('/') ?: return null
        return when (name.substringAfterLast('.', "").lowercase()) {
            "cue" -> {
                val lines = context.contentResolver.openInputStream(sheetUri)
                    ?.bufferedReader()?.readLines() ?: return null
                cueSheetReferencesRaw(lines).firstOrNull()?.let { siblingDocumentUri(sheetUri, it) }
            }
            "gdi" -> {
                val lines = context.contentResolver.openInputStream(sheetUri)
                    ?.bufferedReader()?.readLines() ?: return null
                gdiSheetTrackNamesRaw(lines).firstOrNull()?.let { siblingDocumentUri(sheetUri, it) }
            }
            "m3u" -> null
            else -> sheetUri
        }
    }

    private fun siblingDocumentUri(sheetUri: Uri, siblingName: String): Uri? {
        if (!isSafeSiblingName(siblingName)) return null
        val docId = runCatching { DocumentsContract.getDocumentId(sheetUri) }.getOrNull() ?: return null
        val siblingId = safSiblingDocumentId(docId, siblingName) ?: return null
        return runCatching { DocumentsContract.buildDocumentUriUsingTree(sheetUri, siblingId) }.getOrNull()
    }
}
