package com.psplauncher.feature.artwork.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IgdbApiTest {
    @Test
    fun `title search asks for a list with names and release dates`() {
        assertEquals(
            "search \"Final Fantasy VI Advance\"; fields name,first_release_date,cover.image_id; limit 10;",
            IgdbApi.searchBody("Final Fantasy VI Advance", 10),
        )
    }

    @Test
    fun `the batch scraper's best-match query is unchanged`() {
        assertEquals(
            "search \"Final Fantasy VI Advance\"; fields name,cover.image_id,artworks.image_id; limit 1;",
            IgdbApi.bestMatchBody("Final Fantasy VI Advance"),
        )
    }

    @Test
    fun `a double quote in a title cannot close the search string`() {
        val body = IgdbApi.searchBody("The \"Hard\" Game", 10)

        assertFalse(body.contains("\"Hard\""))
        assertEquals("search \"The 'Hard' Game\"; fields name,first_release_date,cover.image_id; limit 10;", body)
    }

    @Test
    fun `a matched game is fetched by id, not by title`() {
        assertEquals(
            "fields name,cover.image_id,artworks.image_id; where id = 1234;",
            IgdbApi.byIdBody(1234L),
        )
    }
}
