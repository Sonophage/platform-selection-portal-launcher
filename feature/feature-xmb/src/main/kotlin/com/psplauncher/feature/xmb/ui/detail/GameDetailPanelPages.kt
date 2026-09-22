package com.psplauncher.feature.xmb.ui.detail

import com.psplauncher.core.domain.model.Game
import com.psplauncher.feature.xmb.viewmodel.XMBItem
import com.psplauncher.feature.xmb.viewmodel.gameMetadataLine

/**
 * The pages of the Game Detail panel, in the order the strip draws them and the order L1/R1 walk
 * them.
 *
 * NeoStation's panel has five: logo, box art, fanart, info, trophies. This one has five too, but
 * not the same five. Trophies
 * is absent because there is nothing behind it — the codebase has no achievement or trophy store
 * of any kind (the only surviving uses of the word are a Vita scanner folder name and a comment on
 * GameEntity), so the page would be a permanently empty tab. Fanart is not a page either: it is
 * already the full-bleed backdrop behind every page, so a page showing it would be a picture of
 * the wallpaper. Its slot goes to the media strip, which is the richer thing this app has and
 * NeoStation does not.
 */
/**
 * The panel's pages, in strip order — which is declaration order, because availablePanelPages
 * filters `entries` and the shoulders walk what it returns.
 *
 * Logo, then Info, then Video, then the art. The owner's order, and it reads as one: the logo is
 * the game, what it IS comes next, the clip after that, and the box and the gallery are the
 * browsing pages you go looking for rather than the ones you want first.
 */
enum class DetailPanelPage(val label: String) {
    LOGO("Logo"),
    INFO("Info"),
    VIDEO("Video"),
    BOX_ART("Box Art"),
    GALLERY("Media"),
}

/**
 * The pages worth walking to for one game.
 *
 * A tab the user can land on and find nothing is worse than a tab that is not there, so every
 * page that is only worth opening when something filled it in drops out when nothing did. Box
 * art, video and media go when their asset is missing; Info goes when the game has no
 * description, no scraped metadata line and no filename, which is a scraped-nothing homebrew.
 *
 * [DetailPanelPage.LOGO] is the exception and is always offered, because it is not an asset page
 * — it is the resting state. The crossbar on its logo page looks exactly like the crossbar always
 * has (a logo, or nothing), so dropping it would leave no way back to that view once the user has
 * walked off it, and would pop box art onto the screen unprompted for every logo-less game. It is
 * also what keeps this list non-empty for the callers below.
 */
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
    /**
     * How long this game has been played, already formatted, or null when it has never been
     * launched through the launcher. Shown under the logo on the Last Played shelf.
     */
    val playTime: String? = null,
    /**
     * The game's video snap. On the crossbar this is the already-approved clip and nothing else:
     * the panel's video page becomes that snap's one render site while it is open, so no second
     * decoder opens on the same file. See snapSiteFor.
     */
    val videoUri: String? = null,
    val media: List<DetailMedia> = emptyList(),
) {
    /**
     * The strip's pages, derived rather than passed in. The strip's icons and the L1/R1 walk read
     * the same property, so they cannot be given different lists by a caller that updated one and
     * forgot the other.
     */
    val pages: List<DetailPanelPage>
        get() = availablePanelPages(
            hasBoxArt = boxArtUri != null,
            hasVideo = videoUri != null,
            hasGallery = media.isNotEmpty(),
            hasInfo = hasInfo,
        )

    /** The Info card would have at least one line in it. Nothing scraped and no file: no tab. */
    val hasInfo: Boolean
        get() = !description.isNullOrBlank() || metaLine != null || fileName != null
}

/**
 * Play time for the panel, or null when there is none to show.
 *
 * Null rather than "0 min" on purpose: only 2 of the owner's 148 games have any recorded, because
 * the column is written on return from a launch and most of his library predates that. A row of
 * zeroes would read as a broken counter rather than as a library that has not been played through
 * this launcher yet.
 */
fun panelPlayTime(millis: Long): String? {
    if (millis <= 0L) return null
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "Under a minute"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
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
    videoUri: String? = null,
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
    playTime = panelPlayTime(game.totalPlayTimeMillis),
    videoUri = videoUri,
    media = media,
)

/**
 * What the crossbar shows for the row under the cursor.
 *
 * Every field comes off the already-published row, so hovering costs no query and no disk read.
 * [DetailPanelContent.media] is left empty, which is what keeps the Media page out of the strip
 * here — see the note on [DetailPanelContent].
 */
fun detailPanelContentFor(
    item: XMBItem,
    platformName: String,
    videoUri: String? = null,
): DetailPanelContent =
    DetailPanelContent(
        title = item.title,
        platformName = platformName,
        // hasVisibleLogo, not logoUri: this is the same predicate XMBItemList reads to decide
        // whether the row keeps its own title, and the shell reads this field to drive the PIC0
        // fade. One answer to "will a logo actually be drawn", not three.
        logoUri = item.logoUri.takeIf { item.hasVisibleLogo },
        boxArtUri = item.boxArtUri,
        posterFallbackUri = item.heroUri ?: item.artworkUri,
        metaLine = item.metadataLine,
        description = item.description,
        fileName = panelFileName(item.romPath),
        playTime = panelPlayTime(item.totalPlayTimeMillis),
        videoUri = videoUri,
    )
