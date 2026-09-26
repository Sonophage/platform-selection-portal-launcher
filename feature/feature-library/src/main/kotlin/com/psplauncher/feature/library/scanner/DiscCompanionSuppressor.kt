package com.psplauncher.feature.library.scanner

import javax.inject.Inject
import javax.inject.Singleton

data class ScannedDiscFile(
    val rawPath: String,
    val name: String,
)

@Singleton
class DiscCompanionSuppressor @Inject constructor() {
    fun interface SheetReader {
        fun read(file: ScannedDiscFile): List<String>?
    }

    fun suppressedFiles(files: List<ScannedDiscFile>, reader: SheetReader): Set<String> {
        val suppressed = mutableSetOf<String>()

        for ((_, folderFiles) in files.groupBy {
            it.rawPath.substringBeforeLast('/').substringBeforeLast('\\')
        }) {
            val byName = folderFiles.associateBy { it.name.lowercase() }
            for (sheet in folderFiles) {
                val ext = sheet.name.substringAfterLast('.', "").lowercase()
                val referenced = when (ext) {
                    "cue" -> reader.read(sheet)?.let(::cueSheetReferences)
                    "gdi" -> reader.read(sheet)?.let(::gdiSheetTrackNames)
                    else -> null
                } ?: continue
                for (name in referenced) {
                    byName[name]?.let { suppressed.add(it.rawPath) }
                }
            }
        }
        return suppressed
    }
}
