package com.psplauncher.feature.xmb.ui.detail

import com.psplauncher.core.domain.model.Game
import com.psplauncher.feature.xmb.viewmodel.XMBItem
import com.psplauncher.feature.xmb.viewmodel.gameMetadataLine

/**
 * The pages of the Game Detail panel, in the order the strip draws them and the order L1/R1 walk
 * them.
 *
 * NeoStation's panel has five: logo, box art, fanart, info, trophies. This one has four. Trophies
 * is absent because there is nothing behind it — the codebase has no achievement or trophy store
 * of any kind (the only surviving uses of the word are a Vita scanner folder name and a comment on
 * GameEntity), so the page would be a permanently empty tab. Fanart is not a page either: it is
 * already the full-bleed backdrop behind every page, so a page showing it would be a picture of
 * the wallpaper. Its slot goes to the media strip, which is the richer thing this app has and
 * NeoStation does not.
 */
enum class DetailPanelPage(val label: String) {
    LOGO("Logo"),
    BOX_ART("Box Art"),
    GALLERY("Media"),
    INFO("Info"),
}

/**
 * The pages worth walking to for one game.
 *
 * A tab the user can land on and find nothing is worse than a tab that is not there, so the two
 * pages that are purely an asset drop out when the asset is missing. [DetailPanelPage.LOGO] stays
 * either way — it falls back to the title, which every game has — and [DetailPanelPage.INFO] stays
 * even with no description, because the filename and the platform are always something to say.
 * Both therefore make the list non-empty, which the callers below rely on.
 */
fun availablePanelPages(hasBoxArt: Boolean, hasGallery: Boolean): List<DetailPanelPage> =
    DetailPanelPage.entries.filter {
        when (it) {
            DetailPanelPage.LOGO -> true
            DetailPanelPage.BOX_ART -> hasBoxArt
            DetailPanelPage.GALLERY -> hasGallery
            DetailPanelPage.INFO -> true
        }
    }

/**
 * Walk the strip by [delta] steps and clamp at both ends.
 *
 * It clamps rather than wraps, against `cycleMetadataSource`'s wrapping precedent two screens
 * over, because that one drives an invisible value and this one drives a strip the user is looking
 * at: wrapping would throw the highlight the full width of the strip, which reads as a misfire
 * rather than as an end. It is also the rule the page's own D-pad navigation already follows.
 *
 * Returns [current] unchanged when it is not in [pages], so a page that disappears under the
 * cursor cannot be walked away from into a garbage index.
 */
fun stepPanelPage(current: DetailPanelPage, pages: List<DetailPanelPage>, delta: Int): DetailPanelPage {
    val index = pages.indexOf(current)
    if (index < 0) return current
    return pages[(index + delta).coerceIn(0, pages.lastIndex)]
}

/**
 * The page to show when the panel is asked for [requested] but [pages] does not have it — which
 * happens for real when a scrape fills in box art while the panel is open, and when the panel
 * reopens on a game with fewer assets than the last one.
 */
fun resolvePanelPage(requested: DetailPanelPage, pages: List<DetailPanelPage>): DetailPanelPage =
    if (requested in pages) requested else pages.first()

/**
 * Everything the panel draws, in one shape.
 *
 * The panel has two hosts holding two different row types — the crossbar has an `XMBItem`, the
 * drill-down has a `Game` — and the one thing that must not happen is the panel growing two
 * renderers to suit them. They each build one of these instead.
 *
 * [media] is empty on the crossbar by design, not by omission: resolving a game's media means
 * touching the disk, and the crossbar would be doing it on every D-pad press. Everything else the
 * panel needs is already in memory on both sides, so Logo, Box Art and Info cost nothing to hover
 * and the Media page simply is not offered there. It is offered in the drill-down, which resolves
 * once for one game.
 */
data class DetailPanelContent(
    val title: String,
    val platformName: String,
    val logoUri: String? = null,
    val boxArtUri: String? = null,
    /** Hero or grid art, used as the poster behind a video tile that has no thumbnail of its own. */
    val posterFallbackUri: String? = null,
    val metaLine: String? = null,
    val description: String? = null,
    val fileName: String? = null,
    val media: List<DetailMedia> = emptyList(),
) {
    /**
     * The strip's pages, derived rather than passed in. The strip's icons and the L1/R1 walk read
     * the same property, so they cannot be given different lists by a caller that updated one and
     * forgot the other.
     */
    val pages: List<DetailPanelPage>
        get() = availablePanelPages(hasBoxArt = boxArtUri != null, hasGallery = media.isNotEmpty())
}

/** The filename a panel shows for a ROM, or null for a package-backed entry that has no file. */
fun panelFileName(romPath: String?): String? =
    romPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

/**
 * What the drill-down shows: one loaded [Game], with its media already resolved.
 */
fun detailPanelContentFor(
    game: Game,
    platformName: String,
    media: List<DetailMedia>,
): DetailPanelContent = DetailPanelContent(
    title = game.displayTitle,
    platformName = platformName,
    logoUri = game.logoUri,
    boxArtUri = game.boxArtUri,
    posterFallbackUri = game.heroUri ?: game.artworkUri,
    metaLine = gameMetadataLine(game.releaseYear, game.genre, game.developer, game.players),
    description = game.description,
    // romPath is derived for SAF-backed games too, so it is the one field that names the file for
    // every ROM. A package-backed Android or Windows entry has none and correctly shows nothing.
    fileName = panelFileName(game.romPath),
    media = media,
)

/**
 * What the crossbar shows for the row under the cursor.
 *
 * Every field comes off the already-published row, so hovering costs no query and no disk read.
 * [DetailPanelContent.media] is left empty, which is what keeps the Media page out of the strip
 * here — see the note on [DetailPanelContent].
 */
fun detailPanelContentFor(item: XMBItem, platformName: String): DetailPanelContent =
    DetailPanelContent(
        title = item.title,
        platformName = platformName,
        logoUri = item.logoUri,
        boxArtUri = item.boxArtUri,
        posterFallbackUri = item.heroUri ?: item.artworkUri,
        metaLine = item.metadataLine,
        description = item.description,
        fileName = panelFileName(item.romPath),
    )
