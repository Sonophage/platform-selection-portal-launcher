package com.psplauncher.feature.artwork.portable

import com.psplauncher.feature.artwork.store.ArtworkKind

object ArtworkPathResolver {
    const val DIR_PFP = "pfp"

    const val DIR_ICON0 = "$DIR_PFP/icon0"

    const val DIR_ICON1 = "$DIR_PFP/icon1"

    const val DIR_VERSIONS = "$DIR_PFP/versions"

    const val DIR_ORIGINALS = "$DIR_PFP/originals"

    fun versionsDirSegments(platformId: String, kind: ArtworkKind): List<String> =
        listOf(ArtworkLibraryManifest.DIR_ARTWORK, platformId, DIR_PFP, "versions", kind.name.lowercase())

    fun originalsDirSegments(platformId: String, kind: ArtworkKind): List<String> =
        listOf(ArtworkLibraryManifest.DIR_ARTWORK, platformId, DIR_PFP, "originals", kind.name.lowercase())

    private val KIND_TO_DIR: Map<ArtworkKind, String> = mapOf(
        ArtworkKind.ICON           to DIR_ICON0,
        ArtworkKind.ICON1          to DIR_ICON1,
        ArtworkKind.BOX_ART        to "covers",
        ArtworkKind.HERO           to "miximages",
        ArtworkKind.BACKGROUND     to "fanart",
        ArtworkKind.LOGO           to "marquees",
        ArtworkKind.SCREENSHOT     to "screenshots",
        ArtworkKind.TITLESCREEN    to "titlescreens",
        ArtworkKind.PHYSICAL_MEDIA to "physicalmedia",
        ArtworkKind.BOX_3D         to "3dboxes",
        ArtworkKind.MANUAL         to "manuals",
        ArtworkKind.VIDEO          to "videos",
    )

    private val DIR_TO_KIND: Map<String, ArtworkKind> =
        KIND_TO_DIR.entries.associate { (kind, dir) -> dir to kind }

    private val RESERVED_MEDIA_DIRS = setOf("backcovers")

    fun mediaDirFor(kind: ArtworkKind): String = KIND_TO_DIR.getValue(kind)

    fun kindForMediaDir(dirName: String): ArtworkKind? = DIR_TO_KIND[dirName.lowercase()]

    fun isMediaDirName(dirName: String): Boolean {
        val lower = dirName.lowercase()
        return lower in DIR_TO_KIND || lower in RESERVED_MEDIA_DIRS
    }

    val importedKinds: Set<ArtworkKind> = KIND_TO_DIR.keys

    fun relativePath(platformId: String, kind: ArtworkKind, fileName: String): String =
        "${ArtworkLibraryManifest.DIR_ARTWORK}/$platformId/${mediaDirFor(kind)}/$fileName"
}
