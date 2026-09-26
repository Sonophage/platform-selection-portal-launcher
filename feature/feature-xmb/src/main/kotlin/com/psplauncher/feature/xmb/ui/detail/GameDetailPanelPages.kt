package com.psplauncher.feature.xmb.ui.detail

import com.psplauncher.core.domain.model.Game
import com.psplauncher.feature.xmb.viewmodel.XMBItem
import com.psplauncher.feature.xmb.viewmodel.gameMetadataLine

enum class DetailPanelPage(val label: String) {
    LOGO("Logo"),
    INFO("Info"),
    VIDEO("Video"),
    BOX_ART("Box Art"),
    GALLERY("Media"),
}

fun availablePanelPages(
    hasBoxArt: Boolean,
    hasVideo: Boolean,
    hasGallery: Boolean,
    hasInfo: Boolean,
): List<DetailPanelPage> =
    DetailPanelPage.entries.filter {
        when (it) {
            DetailPanelPage.LOGO -> true
            DetailPanelPage.BOX_ART -> hasBoxArt
            DetailPanelPage.VIDEO -> hasVideo
            DetailPanelPage.GALLERY -> hasGallery
            DetailPanelPage.INFO -> hasInfo
        }
    }

fun stepPanelPage(current: DetailPanelPage, pages: List<DetailPanelPage>, delta: Int): DetailPanelPage {
    val index = pages.indexOf(current)
    if (index < 0) return current
    return pages[(index + delta).coerceIn(0, pages.lastIndex)]
}

fun resolvePanelPage(requested: DetailPanelPage, pages: List<DetailPanelPage>): DetailPanelPage =
    if (requested in pages) requested else pages.first()

data class DetailPanelContent(
    val title: String,
    val platformName: String,
    val logoUri: String? = null,
    val boxArtUri: String? = null,

    val posterFallbackUri: String? = null,
    val metaLine: String? = null,
    val description: String? = null,
    val fileName: String? = null,

    val playTime: String? = null,

    val videoUri: String? = null,
    val media: List<DetailMedia> = emptyList(),
) {
    val pages: List<DetailPanelPage>
        get() = availablePanelPages(
            hasBoxArt = boxArtUri != null,
            hasVideo = videoUri != null,
            hasGallery = media.isNotEmpty(),
            hasInfo = hasInfo,
        )

    val hasInfo: Boolean
        get() = !description.isNullOrBlank() || metaLine != null || fileName != null
}

fun panelPlayTime(millis: Long): String? {
    if (millis <= 0L) return null
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "Under a minute"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

fun panelFileName(romPath: String?): String? =
    romPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

fun detailPanelContentFor(
    game: Game,
    platformName: String,
    media: List<DetailMedia>,
    videoUri: String? = null,
): DetailPanelContent = DetailPanelContent(
    title = game.displayTitle,
    platformName = platformName,
    logoUri = game.logoUri,
    boxArtUri = game.boxArtUri,
    posterFallbackUri = game.heroUri ?: game.artworkUri,
    metaLine = gameMetadataLine(game.releaseYear, game.genre, game.developer, game.players),
    description = game.description,

    fileName = panelFileName(game.romPath),
    playTime = panelPlayTime(game.totalPlayTimeMillis),
    videoUri = videoUri,
    media = media,
)

fun detailPanelContentFor(
    item: XMBItem,
    platformName: String,
    videoUri: String? = null,
): DetailPanelContent =
    DetailPanelContent(
        title = item.title,
        platformName = platformName,

        logoUri = item.logoUri.takeIf { item.hasVisibleLogo },
        boxArtUri = item.boxArtUri,
        posterFallbackUri = item.heroUri ?: item.artworkUri,
        metaLine = item.metadataLine,
        description = item.description,
        fileName = panelFileName(item.romPath),
        playTime = panelPlayTime(item.totalPlayTimeMillis),
        videoUri = videoUri,
    )
