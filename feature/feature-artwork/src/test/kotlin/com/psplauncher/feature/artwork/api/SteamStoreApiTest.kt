package com.psplauncher.feature.artwork.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SteamStoreApiTest {
    @Test
    fun `an app id becomes the three library assets`() {
        val art = steamAppArt("220")!!
        assertEquals("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/220/library_hero.jpg", art.heroUrl)
        assertEquals("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/220/library_600x900.jpg", art.boxArtUrl)
        assertEquals("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/220/logo.png", art.logoUrl)
    }

    @Test
    fun `the three assets are three different files`() {
        val art = steamAppArt("620")!!
        assertEquals(3, setOf(art.heroUrl, art.boxArtUrl, art.logoUrl).size)
    }

    @Test
    fun `whitespace around an id is tolerated`() {
        assertEquals(steamAppArt("220"), steamAppArt("  220  "))
    }

    @Test
    fun `anything that is not a positive integer yields no urls at all`() {
        listOf("", "   ", "abc", "22a0", "-5", "0", "1.5", "220/../etc").forEach {
            assertNull(steamAppArt(it), "built urls for a bad id: '$it'")
        }
    }

    @Test
    fun `the id is never interpolated without being checked`() {
        val art = steamAppArt("../../evil")
        assertNull(art)
        assertEquals(7, steamAppArt("220")!!.heroUrl.count { it == '/' })
    }

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
        listOf("Coming soon", "To be announced", "", "Q4").forEach {
            assertNull(yearFrom(it), "invented a year from '$it'")
        }
    }

    @Test
    fun `a number that is not a plausible year is not taken as one`() {
        assertNull(yearFrom("Early Access, 1500 players"))
        assertEquals(1999, yearFrom("Build 2451, released 1999"))
    }

    private fun yearFrom(raw: String): Int? =
        Regex("(19|20)\\d{2}").find(raw)?.value?.toIntOrNull()

    @Test
    fun `another store's id is never treated as a Steam id`() {
        assertNull(steamAppIdOf("GOG", "620"))
        assertNull(steamAppIdOf("EPIC", "620"))
        assertNull(steamAppIdOf("AMAZON", "620"))
        assertNull(steamAppIdOf(null, "620"))
    }

    @Test
    fun `the storefront is matched however it was cased`() {
        assertEquals("620", steamAppIdOf("STEAM", "620"))
        assertEquals("620", steamAppIdOf("steam", "620"))
        assertEquals("620", steamAppIdOf("Steam", " 620 "))
    }

    @Test
    fun `an id that is not a positive integer is not an app id`() {
        assertNull(steamAppIdOf("STEAM", null))
        assertNull(steamAppIdOf("STEAM", ""))
        assertNull(steamAppIdOf("STEAM", "   "))
        assertNull(steamAppIdOf("STEAM", "0"))
        assertNull(steamAppIdOf("STEAM", "-5"))
        assertNull(steamAppIdOf("STEAM", "620a"))
        assertNull(steamAppIdOf("STEAM", "com.valve.portal2"))
    }

    @Test
    fun `it agrees with steamAppArt about what an app id is`() {
        listOf("620", "1", "9999999", "0", "-5", "620a", "", "  ").forEach { id ->
            val asksSteam = steamAppIdOf("STEAM", id) != null
            val buildsArt = steamAppArt(id) != null
            assertEquals(asksSteam, buildsArt, "disagreed about '$id'")
        }
    }
}
