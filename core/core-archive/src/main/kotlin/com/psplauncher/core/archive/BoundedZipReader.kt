package com.psplauncher.core.archive

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

data class ZipLimits(

    val maxEntries: Int = 512,

    val maxEntryBytes: Long = 32L * 1024 * 1024,

    val maxTotalBytes: Long = 128L * 1024 * 1024,
)

sealed interface ZipRejection {
    data class TooManyEntries(val limit: Int) : ZipRejection
    data class EntryTooLarge(val name: String, val limit: Long) : ZipRejection
    data class ArchiveTooLarge(val limit: Long) : ZipRejection
}

class ZipLimitExceededException(val rejection: ZipRejection) : IOException(
    when (rejection) {
        is ZipRejection.TooManyEntries -> "archive has more than ${rejection.limit} entries"
        is ZipRejection.EntryTooLarge ->
            "entry '${rejection.name}' exceeds ${rejection.limit} bytes when inflated"
        is ZipRejection.ArchiveTooLarge -> "archive exceeds ${rejection.limit} inflated bytes"
    },
)

class BoundedZipEntry internal constructor(
    val name: String,
    val isDirectory: Boolean,
    private val source: InputStream,
    private val limits: ZipLimits,
    private val budget: ArchiveBudget,
) {
    private var consumed = false
    internal var stopped = false
        private set

    fun stop() {
        stopped = true
    }

    fun readBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        copyTo(out)
        return out.toByteArray()
    }

    fun copyTo(out: OutputStream): Long {
        check(!consumed) { "entry '$name' has already been read" }
        consumed = true
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var entryTotal = 0L
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            entryTotal += read
            if (entryTotal > limits.maxEntryBytes) {
                throw ZipLimitExceededException(ZipRejection.EntryTooLarge(name, limits.maxEntryBytes))
            }
            budget.spend(read.toLong(), limits)
            out.write(buffer, 0, read)
        }
        return entryTotal
    }

    internal fun drain() {
        if (consumed) return
        copyTo(OutputStream.nullOutputStream())
    }
}

class ArchiveBudget internal constructor() {
    private var total = 0L

    internal fun spend(bytes: Long, limits: ZipLimits) {
        total += bytes
        if (total > limits.maxTotalBytes) {
            throw ZipLimitExceededException(ZipRejection.ArchiveTooLarge(limits.maxTotalBytes))
        }
    }
}

object BoundedZipReader {
    fun read(
        source: InputStream,
        limits: ZipLimits = ZipLimits(),
        onEntry: (BoundedZipEntry) -> Unit,
    ) {
        val budget = ArchiveBudget()
        var count = 0
        ZipInputStream(source.buffered()).use { zip ->
            var raw = zip.nextEntry
            while (raw != null) {
                count++
                if (count > limits.maxEntries) {
                    throw ZipLimitExceededException(ZipRejection.TooManyEntries(limits.maxEntries))
                }
                val entry = BoundedZipEntry(
                    name = raw.name,
                    isDirectory = raw.isDirectory,

                    source = NonClosingInputStream(zip),
                    limits = limits,
                    budget = budget,
                )
                onEntry(entry)

                if (entry.stopped) break

                if (!raw.isDirectory) entry.drain()
                zip.closeEntry()
                raw = zip.nextEntry
            }
        }
    }
}

private class NonClosingInputStream(private val delegate: InputStream) : InputStream() {
    override fun read(): Int = delegate.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
    override fun available(): Int = delegate.available()
    override fun close() = Unit
}

object SafeArchivePath {
    fun resolveWithin(root: File, relative: String): File? {
        val cleaned = relative.trim()
        if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return null
        if (File(cleaned).isAbsolute || cleaned.startsWith("/") || cleaned.startsWith("\\")) return null

        if (cleaned.length >= 2 && cleaned[1] == ':') return null

        val rootCanonical = root.canonicalPath
        val candidate = File(root, cleaned)
        val candidateCanonical = candidate.canonicalPath
        if (candidateCanonical == rootCanonical) return null
        if (!candidateCanonical.startsWith(rootCanonical + File.separator)) return null
        return candidate
    }

    fun resolveWithinRoots(base: File, relative: String, roots: List<String>): File? {
        val resolved = resolveWithin(base, relative) ?: return null
        val rel = resolved.canonicalFile.relativeTo(base.canonicalFile).invariantSeparatorsPath
        val firstSegment = rel.substringBefore('/')

        if (firstSegment !in roots) return null

        if (rel == firstSegment) return null
        return resolved
    }
}
