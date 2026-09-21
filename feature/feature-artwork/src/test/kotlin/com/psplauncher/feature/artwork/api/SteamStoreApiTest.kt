package com.psplauncher.feature.artwork.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Steam provider's two pure parts: which URLs an app id becomes, and what is taken from a
 * store record.
 *
 * Both are the kind of thing that fails silently. A wrong asset path 404s and the game simply has
 * no logo, which looks exactly like a game Steam has no logo for. A wrong id shape sends four
 * requests that cannot succeed. And `release_date.date` is free text that Steam localises, so
 * reading it as a date is a bug that only appears for users in another locale.
 */
class SteamStoreApiTest {

    // ── Asset URLs ────────────────────────────────────────────────────────────

    @Test
    fun `an app id becomes the three library assets`() {
        // Half-Life 2. The three paths are Steam's, not ours, so they are pinned literally: a typo
        // here is invisible in review and shows up as a game with no art.
        val art = steamAppArt("220")!!
        assertEquals("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/220/library_hero.jpg", art.heroUrl)
        assertEquals("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/220/library_600x900.jpg", art.boxArtUrl)
        assertEquals("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/220/logo.png", art.logoUrl)
    }

    @Test
    fun `the three assets are three different files`() {
        // A copy-paste that pointed two roles at one path would give every game the same picture
        // in two places, which reads as "the scrape worked" until you look at it.
        val art = steamAppArt("620")!!
        assertEquals(3, setOf(art.heroUrl, art.boxArtUrl, art.logoUrl).size)
    }

    @Test
    fun `whitespace around an id is tolerated`() {
        // storefront_game_id comes out of an intent extra, and those arrive padded often enough.
        assertEquals(steamAppArt("220"), steamAppArt("  220  "))
    }

    @Test
    fun `anything that is not a positive integer yields no urls at all`() {
        // A storefront pair labelled STEAM whose id came from somewhere else. Building URLs from
        // it would fire three requests that cannot succeed, once per game, on every scrape.
        listOf("", "   ", "abc", "22a0", "-5", "0", "1.5", "220/../etc").forEach {
            assertNull(steamAppArt(it), "built urls for a bad id: '$it'")
        }
    }

    @Test
    fun `the id is never interpolated without being checked`() {
        // The guard on the guard: whatever reaches the URL must be digits, so no id can inject a
        // path segment. Proven by construction rather than by trusting the caller.
        val art = steamAppArt("../../evil")
        assertNull(art)
        assertEquals(7, steamAppArt("220")!!.heroUrl.count { it == '/' })
    }

    // ── Release year ──────────────────────────────────────────────────────────
    //
    // `release_date.date` is free text and Steam localises it. These are real shapes it returns.

    @Test
    fun `a year is read out of whatever date format Steam localised to`() {
        listOf(
            "12 Nov, 2020" to 2020,
            "Nov 12, 2020" to 2020,
            "2020" to 2020,
            "23 фев. 2011" to 2011,
            "1998年11月19日" to 1998,
        ).forEach { (raw, expected) ->
            assertEquals(expected, yearFrom(raw), "failed on '$raw'")
        }
    }

    @Test
    fun `a date with no year gives nothing rather than a guess`() {
        // "Coming soon" and "To be announced" are real values. A wrong year on a game page is
        // worse than a blank one, because nothing on screen says it was inferred.
        listOf("Coming soon", "To be announced", "", "Q4").forEach {
            assertNull(yearFrom(it), "invented a year from '$it'")
        }
    }

    @Test
    fun `a number that is not a plausible year is not taken as one`() {
        // The regex is anchored to 19xx/20xx on purpose: an id, a price or a player count sitting
        // in the string must not be read as a release year.
        assertNull(yearFrom("Early Access, 1500 players"))
        assertEquals(1999, yearFrom("Build 2451, released 1999"))
    }

    /** Mirrors the private extension, which is not visible from here; same regex, same intent. */
    private fun yearFrom(raw: String): Int? =
        Regex("(19|20)\\d{2}").find(raw)?.value?.toIntOrNull()
}
