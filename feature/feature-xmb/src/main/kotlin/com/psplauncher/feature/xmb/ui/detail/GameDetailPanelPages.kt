package com.psplauncher.feature.xmb.ui.detail

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
