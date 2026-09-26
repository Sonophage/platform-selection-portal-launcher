package com.psplauncher.feature.library.scanner

import com.psplauncher.core.data.platform.PlatformFolderHintResolver
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscImageResolver @Inject constructor(
    private val folderHintResolver: PlatformFolderHintResolver,
) {
    data class ResolvedDisc(
        val launchFile: File,
        val platformId: String,
        val suppressedFiles: Set<File>,
    )

    data class FolderResolution(
        val resolvedDiscs: List<ResolvedDisc>,
        val suppressedPaths: Set<String>,
        val requiresUserAssignment: List<File>,
    )

    fun resolveFolder(folder: File): FolderResolution {
        val allFiles = folder.walkTopDown().filter { it.isFile }.toList()
        return resolveFiles(allFiles)
    }

    fun resolveFiles(files: List<File>): FolderResolution {
        val resolvedDiscs      = mutableListOf<ResolvedDisc>()
        val suppressedPaths    = mutableSetOf<String>()
        val requiresAssignment = mutableListOf<File>()

        val byDir = files.groupBy { it.parentFile?.absolutePath ?: "" }

        for ((_, siblings) in byDir) {
            val cueFiles = siblings.filter { it.extension.lowercase() == "cue" }
            val binFiles = siblings.filter { it.extension.lowercase() == "bin" }
            val chdFiles = siblings.filter { it.extension.lowercase() == "chd" }
            val imgFiles = siblings.filter { it.extension.lowercase() == "img" }

            for (cueFile in cueFiles) {
                val companionBins = findCompanionBins(cueFile, binFiles)
                val platformId = folderHintResolver.detectFromPath(cueFile.absolutePath) ?: "psx"

                resolvedDiscs.add(
                    ResolvedDisc(
                        launchFile = cueFile,
                        platformId = platformId,
                        suppressedFiles = companionBins.toSet(),
                    )
                )

                companionBins.forEach { suppressedPaths.add(it.absolutePath) }

                Timber.d(
                    "Disc resolved: ${cueFile.name} (PS1) — " +
                    "suppressed ${companionBins.size} companion .bin file(s)"
                )
            }

            for (gdiFile in siblings.filter { it.extension.lowercase() == "gdi" }) {
                val trackNames = gdiSheetTrackNames(readLinesOrEmpty(gdiFile))
                val trackFiles = siblings.filter { it.name.lowercase() in trackNames }
                trackFiles.forEach { suppressedPaths.add(it.absolutePath) }
                if (trackFiles.isNotEmpty()) {
                    Timber.d("GDI tracks suppressed for ${gdiFile.name}: ${trackFiles.size}")
                }
            }

            val claimedBins = resolvedDiscs
                .flatMap { it.suppressedFiles }
                .map { it.absolutePath }
                .toSet()

            for (binFile in binFiles) {
                if (binFile.absolutePath in claimedBins) continue

                val hasCueSibling = cueFiles.any { cue ->
                    cue.parentFile?.absolutePath == binFile.parentFile?.absolutePath
                }

                if (!hasCueSibling) {
                    val platformId = folderHintResolver.detectFromPath(binFile.absolutePath) ?: "megadrive"
                    resolvedDiscs.add(
                        ResolvedDisc(
                            launchFile = binFile,
                            platformId = platformId,
                            suppressedFiles = emptySet(),
                        )
                    )
                    Timber.d("Orphan .bin resolved as $platformId: ${binFile.name}")
                }
            }

            for (chdFile in chdFiles) {
                requiresAssignment.add(chdFile)
                Timber.d(".chd flagged for user platform assignment: ${chdFile.name}")
            }

            for (imgFile in imgFiles) {
                requiresAssignment.add(imgFile)
                Timber.d(".img flagged for user platform assignment: ${imgFile.name}")
            }
        }

        return FolderResolution(
            resolvedDiscs = resolvedDiscs,
            suppressedPaths = suppressedPaths,
            requiresUserAssignment = requiresAssignment,
        )
    }

    private fun findCompanionBins(cueFile: File, candidates: List<File>): List<File> {
        val referencedNames = parseCueFileReferences(cueFile)

        return if (referencedNames.isNotEmpty()) {
            candidates.filter { it.name.lowercase() in referencedNames }
        } else {
            val cueBaseName = cueFile.nameWithoutExtension.lowercase()
            candidates.filter { bin ->
                bin.parentFile?.absolutePath == cueFile.parentFile?.absolutePath &&
                bin.nameWithoutExtension.lowercase().let { binBase ->
                    binBase == cueBaseName ||
                    binBase.startsWith(cueBaseName) ||
                    binBase.replace(Regex("\\s*track\\s*\\d+", RegexOption.IGNORE_CASE), "").trim() == cueBaseName
                }
            }
        }
    }

    private fun parseCueFileReferences(cueFile: File): Set<String> {
        return try {
            cueSheetReferences(cueFile.readLines())
        } catch (e: Exception) {
            Timber.w(e, "Could not parse CUE sheet: ${cueFile.name} — falling back to name matching")
            emptySet()
        }
    }

    private fun readLinesOrEmpty(file: File): List<String> = try {
        file.readLines()
    } catch (e: Exception) {
        Timber.w(e, "Could not read sheet: ${file.name}")
        emptyList()
    }
}
