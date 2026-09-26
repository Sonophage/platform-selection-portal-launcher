package com.psplauncher.feature.artwork.store

import com.psplauncher.core.domain.model.GameRegion
import com.psplauncher.core.ui.detail.DetailHeroAspect

data class CropProfile(val key: String, val aspect: Float?)

class CropProfileRegistry(private val entries: Map<String, Float>) {
    fun resolve(
        kind: ArtworkKind,
        platformId: String?,
        region: GameRegion?,
        override: String? = null,
    ): CropProfile {
        val kindKey = kind.name

        override?.trim()?.takeIf { it.isNotEmpty() }?.let { key ->
            if (key == ORIGINAL_KEY) return CropProfile(ORIGINAL_KEY, null)
            if (key == kindKey || key.startsWith("$kindKey:")) {
                entries[key]?.let { return CropProfile(key, it) }
            }
        }

        val platformKey = platformId?.let { "$kindKey:$it" }
        val regionKey = if (platformId != null && region != null) "$kindKey:$platformId:${region.name}" else null

        regionKey?.let { key -> entries[key]?.let { return CropProfile(key, it) } }
        platformKey?.let { key -> entries[key]?.let { return CropProfile(key, it) } }
        entries[kindKey]?.let { return CropProfile(kindKey, it) }
        return CropProfile(ORIGINAL_KEY, null)
    }

    companion object {
        const val ORIGINAL_KEY = "original"

        val Default = CropProfileRegistry(
            mapOf(
                ArtworkKind.ICON.name to 144f / 80f,
                ArtworkKind.ICON1.name to 144f / 80f,

                ArtworkKind.HERO.name to DetailHeroAspect,
                ArtworkKind.BACKGROUND.name to 16f / 9f,
            )
        )
    }
}
