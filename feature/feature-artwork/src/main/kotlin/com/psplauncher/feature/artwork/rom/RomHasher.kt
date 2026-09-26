package com.psplauncher.feature.artwork.rom

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class RomIdentity(
    val crc32: String?,
    val sizeBytes: Long?,
    val fileName: String?,
)

@Singleton
class RomHasher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun identify(
        romPath: String?,
        romUri: String?,
        maxHashBytes: Long = DEFAULT_MAX_HASH_BYTES,
    ): RomIdentity = withContext(Dispatchers.IO) {
        val file = romPath?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
        if (file != null) {
            return@withContext identifyStream(
                fileName  = file.name,
                knownSize = file.length(),
                maxHashBytes = maxHashBytes,
            ) { file.inputStream() }
        }

        val uri = romUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (uri != null) {
            val name = romPath?.substringAfterLast('/') ?: uri.lastPathSegment?.substringAfterLast('/')
            val size = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull()?.takeIf { it >= 0 }
            return@withContext identifyStream(
                fileName  = name,
                knownSize = size,
                maxHashBytes = maxHashBytes,
            ) { context.contentResolver.openInputStream(uri) }
        }

        RomIdentity(crc32 = null, sizeBytes = null, fileName = romPath?.substringAfterLast('/'))
    }

    private inline fun identifyStream(
        fileName: String?,
        knownSize: Long?,
        maxHashBytes: Long,
        open: () -> InputStream?,
    ): RomIdentity {
        val isZip = fileName.orEmpty().lowercase(Locale.US).endsWith(".zip")

        if (!isZip && knownSize != null && knownSize > maxHashBytes) {
            return RomIdentity(crc32 = null, sizeBytes = knownSize, fileName = fileName)
        }

        return runCatching {
            val stream = open() ?: return RomIdentity(null, knownSize, fileName)
            stream.use { s ->
                if (isZip) {
                    val inner = hashFirstZipEntry(s, maxHashBytes)
                        ?: return RomIdentity(null, knownSize, fileName)
                    RomIdentity(inner.crc32, inner.sizeBytes, fileName)
                } else {
                    val (crc, size) = crcOfStream(s, maxHashBytes)
                    RomIdentity(crc, size ?: knownSize, fileName)
                }
            }
        }.getOrElse { e ->
            Timber.w(e, "ROM hash failed for $fileName")
            RomIdentity(null, knownSize, fileName)
        }
    }

    companion object {
        const val DEFAULT_MAX_HASH_BYTES = 256L * 1024 * 1024

        private val NON_ROM_ENTRY_EXTENSIONS = setOf("txt", "nfo", "diz", "md", "xml", "dat")

        fun hashFirstZipEntry(stream: InputStream, maxHashBytes: Long): RomIdentity? {
            val zip = ZipInputStream(stream.buffered())
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val ext  = name.substringAfterLast('.', "").lowercase(Locale.US)
                if (!entry.isDirectory && ext !in NON_ROM_ENTRY_EXTENSIONS) {
                    if (entry.size in (maxHashBytes + 1)..Long.MAX_VALUE) return RomIdentity(null, entry.size, name)
                    val (crc, counted) = crcOfStream(zip, maxHashBytes)
                    return RomIdentity(crc, counted ?: entry.size.takeIf { it >= 0 }, name)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            return null
        }

        fun crcOfStream(stream: InputStream, maxHashBytes: Long): Pair<String?, Long?> {
            val crc = CRC32()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buf)
                if (n == -1) break
                crc.update(buf, 0, n)
                total += n
                if (total > maxHashBytes) return null to null
            }
            val hex = crc.value.toString(16).uppercase(Locale.US).padStart(8, '0')
            return hex to total
        }
    }
}
