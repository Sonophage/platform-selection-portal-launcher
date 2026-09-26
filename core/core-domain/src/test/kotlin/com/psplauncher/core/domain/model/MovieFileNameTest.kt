package com.psplauncher.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

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
        assertEquals("Blade Runner 2049 (2017)", MovieFileName.titleOf("Blade Runner 2049 2017 2160p UHD BluRay x265.mkv"))
        assertEquals("2012 (2009)", MovieFileName.titleOf("2012.2009.1080p.BluRay.x264.mkv"))
        assertEquals("1917 (2019)", MovieFileName.titleOf("1917.2019.1080p.BluRay.x264-SPARKS.mkv"))
    }

    @Test
    fun `a file that is not a scene release comes through as itself`() {
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
        assertEquals("1080p x264", MovieFileName.titleOf("1080p.x264.mkv"))
        assertEquals("", MovieFileName.titleOf(""))
    }
}
