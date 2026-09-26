package com.psplauncher.feature.library.scanner

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArcadeRomsetCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Entry(val platformId: String, val title: String)

    val validatedPlatforms = setOf("cps1", "cps2", "cps3", "neogeo")

    private val arcadePlatforms = validatedPlatforms + setOf("mame", "arcade")

    private val byRomset: Map<String, Entry> by lazy { load() }

    private fun load(): Map<String, Entry> {
        val out = HashMap<String, Entry>()
        runCatching {
            context.assets.open("arcade/arcade_romsets.tsv").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val parts = line.split('\t')
                    if (parts.size < 3) continue
                    out[parts[0].trim().lowercase()] = Entry(parts[1].trim(), parts[2].trim())
                }
            }
        }.onFailure { Timber.e(it, "Failed to load arcade romset catalog") }
        Timber.i("Arcade romset catalog loaded: ${out.size} romsets (cps1/2/3, neogeo, mame)")
        return out
    }

    sealed interface Decision {
        data object UseDefault : Decision

        data class Route(val platformId: String, val title: String) : Decision

        data object Skip : Decision
    }

    fun decide(fileStem: String, extensionLower: String, candidatePlatformId: String?): Decision {
        if (extensionLower != "zip" && extensionLower != "7z") return Decision.UseDefault
        if (candidatePlatformId !in arcadePlatforms) return Decision.UseDefault
        byRomset[fileStem.lowercase()]?.let { return Decision.Route(it.platformId, it.title) }
        return if (candidatePlatformId in validatedPlatforms) Decision.Skip else Decision.UseDefault
    }
}
