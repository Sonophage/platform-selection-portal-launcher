package com.psplauncher.feature.library.scanner

import com.psplauncher.core.data.platform.PlatformFolderHintResolver
import com.psplauncher.core.data.repository.RomRootRepository
import com.psplauncher.core.domain.model.MemoryCard
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ScanSourceResolver @Inject constructor(
    private val romScanner: RomScanner,
    private val romRootRepository: RomRootRepository,
    private val folderHintResolver: PlatformFolderHintResolver,
) {
    suspend fun sourcesFor(card: MemoryCard): List<(Set<String>) -> Flow<ScanResult>> {
        val exts = card.supportedExtensions
        val rec = card.scanRecursively

        if (!card.treeUri.isNullOrBlank()) {
            return listOf({ existing -> romScanner.scanTree(card.treeUri!!, exts, card.platformId, rec, existing) })
        }

        val targets = rootScanTargets(card.platformId)
        if (targets.isNotEmpty()) {
            return targets.map { (rootUri, childDocId) ->
                { existing: Set<String> ->
                    romScanner.scanTree(rootUri, exts, card.platformId, rec, existing, startDocId = childDocId)
                }
            }
        }

        if (!card.romDirectory.isNullOrBlank()) {
            return listOf({ existing -> romScanner.scanDirectory(card.romDirectory!!, exts, card.platformId, rec, existing) })
        }

        return emptyList()
    }

    private suspend fun rootScanTargets(platformId: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (rootUri in romRootRepository.getAll()) {
            for (name in romScanner.listSubfolderNames(rootUri)) {
                if (folderHintResolver.detectFromFolderName(name) == platformId) {
                    RomRootRepository.childDocIdOf(rootUri, name)?.let { out.add(rootUri to it) }
                }
            }
        }
        return out
    }
}
