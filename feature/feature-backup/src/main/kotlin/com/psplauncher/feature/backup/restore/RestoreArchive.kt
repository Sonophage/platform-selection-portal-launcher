package com.psplauncher.feature.backup.restore

import com.psplauncher.core.archive.BoundedZipReader
import com.psplauncher.core.archive.SafeArchivePath
import com.psplauncher.core.archive.ZipLimits
import com.psplauncher.core.domain.model.EmulatorProfile
import com.psplauncher.core.domain.model.EmulatorProfileAdmission
import com.psplauncher.feature.backup.BACKUP_FILES_PREFIX
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.InputStream

object RestoreArchive {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val PROFILES_PATH = "emulator_profiles/custom_profiles.json"

    fun read(
        source: InputStream,
        staging: File,
        bundledRoots: List<String>,
        limits: ZipLimits = ZipLimits(),
        selfPackage: String? = null,
    ): RestoredBundle {
        staging.deleteRecursively()
        staging.mkdirs()

        val jsonEntries = mutableMapOf<String, String>()
        val refusals = mutableListOf<String>()
        var staged = 0

        BoundedZipReader.read(source, limits) { entry ->
            if (entry.isDirectory) return@read

            if (!entry.name.startsWith(BACKUP_FILES_PREFIX)) {
                jsonEntries[entry.name] = entry.readBytes().toString(Charsets.UTF_8)
                return@read
            }

            val relative = entry.name.removePrefix(BACKUP_FILES_PREFIX)

            val dest = SafeArchivePath.resolveWithinRoots(staging, relative, bundledRoots)
            if (dest == null) {
                refusals += "Refused '$relative': not inside a restorable folder"
                Timber.w("Restore refused out-of-root entry: %s", relative)
                return@read
            }
            dest.parentFile?.mkdirs()
            dest.outputStream().use { out -> entry.copyTo(out) }
            staged++
        }

        staged -= sanitizeProfiles(staging, refusals, selfPackage)

        return RestoredBundle(jsonEntries, staging, bundledRoots, refusals, staged)
    }

    private fun sanitizeProfiles(
        staging: File,
        refusals: MutableList<String>,
        selfPackage: String?,
    ): Int {
        val file = File(staging, PROFILES_PATH)
        if (!file.exists()) return 0

        val parsed = runCatching {
            json.decodeFromString(ListSerializer(EmulatorProfile.serializer()), file.readText())
        }.getOrElse { e ->

            refusals += "Refused emulator profiles: file could not be parsed"
            Timber.w(e, "Restore refused unparseable emulator profiles")
            file.delete()
            return 1
        }

        val result = EmulatorProfileAdmission.admit(parsed, selfPackage)
        result.refused.forEach {
            refusals += "Refused emulator profile '${it.name}': ${it.reason}"
            Timber.w("Restore refused emulator profile %s: %s", it.id, it.reason)
        }
        if (result.refused.isEmpty()) return 0

        if (result.admitted.isEmpty()) {
            file.delete()
            return 1
        }
        file.writeText(
            json.encodeToString(ListSerializer(EmulatorProfile.serializer()), result.admitted),
        )
        return 0
    }
}

class RestoredBundle internal constructor(

    val jsonEntries: Map<String, String>,

    val stagingDir: File,
    private val bundledRoots: List<String>,

    val refusals: List<String>,

    val stagedCount: Int,
) {
    fun commitFiles(filesDir: File) {
        val staged = stagingDir.takeIf { it.exists() }
            ?.walkTopDown()?.filter { it.isFile }?.toList().orEmpty()
        if (staged.isEmpty()) {
            discard()
            return
        }
        bundledRoots.forEach { root -> File(filesDir, root).deleteRecursively() }
        staged.forEach { src ->
            val relative = src.relativeTo(stagingDir).invariantSeparatorsPath

            val dest = com.psplauncher.core.archive.SafeArchivePath
                .resolveWithinRoots(filesDir, relative, bundledRoots)
            if (dest == null) {
                Timber.e("Staged file escaped confinement at commit time: %s", relative)
                return@forEach
            }
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = true)
        }
        discard()
    }

    fun discard() {
        stagingDir.deleteRecursively()
    }
}
