package com.psplauncher.feature.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PcLauncherCatalogTest {
    private fun classesOf(vararg present: String): (String) -> Boolean = { it in present.toSet() }

    @Test
    fun `v6 lineage component wins`() {
        assertEquals(
            GameHubGeneration.V6,
            PcLauncherCatalog.resolveGeneration(
                versionNameMajor = 6,
                label = "GameHub",
                hasClass = classesOf(PcLauncherCatalog.V6_DEEP_LINK_ACTIVITY),
            ),
        )
    }

    @Test
    fun `v5 lineage components win, even against a v6-looking version`() {
        assertEquals(
            GameHubGeneration.V5,
            PcLauncherCatalog.resolveGeneration(
                versionNameMajor = 6,
                label = null,
                hasClass = classesOf(PcLauncherCatalog.V5_GAME_DETAIL_ACTIVITY),
            ),
        )
        assertEquals(
            GameHubGeneration.V5,
            PcLauncherCatalog.resolveGeneration(
                versionNameMajor = null,
                label = null,
                hasClass = classesOf("com.xj.app.DeepLinkRouterActivity"),
            ),
        )
    }

    @Test
    fun `component beats version when both lineages would disagree`() {
        assertEquals(
            GameHubGeneration.V6,
            PcLauncherCatalog.resolveGeneration(
                versionNameMajor = 5,
                label = "BannerHub",
                hasClass = classesOf(PcLauncherCatalog.V6_DEEP_LINK_ACTIVITY),
            ),
        )
    }

    @Test
    fun `family-labeled install without known components falls back to version major`() {
        assertEquals(
            GameHubGeneration.V6,
            PcLauncherCatalog.resolveGeneration(versionNameMajor = 6, label = "GameHub Pro") { false },
        )
        assertEquals(
            GameHubGeneration.V5,
            PcLauncherCatalog.resolveGeneration(versionNameMajor = 5, label = "BannerHub") { false },
        )
    }

    @Test
    fun `spoofed-name genuine app resolves to null`() {
        assertNull(
            PcLauncherCatalog.resolveGeneration(versionNameMajor = 10, label = "AnTuTu Benchmark") { false },
        )
        assertNull(PcLauncherCatalog.resolveGeneration(versionNameMajor = null, label = null) { false })
    }

    @Test
    fun `banner labels claim the BannerHub def`() {
        assertTrue(PcLauncherCatalog.brandMatches(PcLauncherType.BANNERHUB_V6, "BannerHub v6"))
        assertFalse(PcLauncherCatalog.brandMatches(PcLauncherType.GAMEHUB_LITE, "BannerHub v6"))
    }

    @Test
    fun `any other verified variant claims GameHub Lite, arbitrary labels included`() {
        assertTrue(PcLauncherCatalog.brandMatches(PcLauncherType.GAMEHUB_LITE, "AI Bench"))
        assertTrue(PcLauncherCatalog.brandMatches(PcLauncherType.GAMEHUB_LITE, "GameHub"))
        assertTrue(PcLauncherCatalog.brandMatches(PcLauncherType.GAMEHUB_LITE, null))
        assertFalse(PcLauncherCatalog.brandMatches(PcLauncherType.BANNERHUB_V6, "AI Bench"))
        assertFalse(PcLauncherCatalog.brandMatches(PcLauncherType.BANNERHUB_V6, null))
    }

    @Test
    fun `family pool membership is unchanged by the fingerprint rework`() {
        assertTrue(PcLauncherCatalog.isGameHubFamilyPackage("com.ludashi.aibench"))
        assertTrue(PcLauncherCatalog.isGameHubFamilyPackage("com.xiaoji.egggame"))
        assertFalse(PcLauncherCatalog.isGameHubFamilyPackage("app.gamenative"))
        assertFalse(PcLauncherCatalog.isGameHubFamilyPackage(null))
    }

    @Test
    fun `launcher-exclusive packages resolve without a fingerprint`() {
        assertEquals(PcLauncherType.GAMENATIVE, PcLauncherCatalog.forPackage("app.gamenative")?.type)
        assertEquals(PcLauncherType.WINLATOR, PcLauncherCatalog.forPackage("com.winlator")?.type)
        assertNull(PcLauncherCatalog.forPackage("com.example.random"))
    }
}
