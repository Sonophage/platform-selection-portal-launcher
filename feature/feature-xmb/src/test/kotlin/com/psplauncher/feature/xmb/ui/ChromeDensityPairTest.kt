package com.psplauncher.feature.xmb.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chrome bands and the screens that reserve room under them must resolve dp at ONE density.
 *
 * `StatusStripHeight` is 34dp for the strip the shell draws AND for the six screens that hold
 * room back under it; `HintBarHeight` is the same pair at the bottom. ChromeBands.kt exists to make
 * that one number — but one number resolved at two densities is two numbers. At 0.6 the strip
 * overlaps content by 14dp; at 1.8 it leaves 27dp of empty band.
 *
 * The shell used to hold both bands and every chrome screen at the device's BASE density, which
 * kept the pair in step by keeping everyone at 1.0 — and took the user's size slider out of the
 * chrome with it. They now share `uiDensity` (base × the user's scale), while the crossbar's
 * auto-fit multiplies on top for the cross alone.
 *
 * This is a source check rather than a rendered one on purpose: what can break is a future edit
 * re-pinning ONE of the three to `baseDensity`, and that is visible in the text long before it is
 * visible on a screen — and only on a device whose owner has moved the slider off 1.0, which is
 * nobody by default. A rendered test at scale 1.0 would pass either way.
 */
class ChromeDensityPairTest {

    private val shell: String by lazy {
        val f = File("src/main/kotlin/com/psplauncher/feature/xmb/ui/XMBShell.kt")
        assertTrue("XMBShell.kt not found at ${f.absolutePath} — has the file moved?", f.exists())
        f.readText()
    }

    @Test
    fun `no chrome band is pinned back to the device base density`() {
        // `LocalDensity provides baseDensity` is exactly the shape that re-splits the pair.
        val offenders = shell.lineSequence()
            .withIndex()
            .filter { (_, l) -> Regex("""LocalDensity\s+provides\s+baseDensity\b""").containsMatchIn(l) }
            .map { (i, l) -> "line ${i + 1}: ${l.trim()}" }
            .toList()
        assertTrue(
            "a chrome band is pinned to base density again, so it no longer shares a density with " +
                "the screens reserving room under it: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the three bands and the chrome screens all take uiDensity`() {
        // The strip, the hint bar and the block holding every separate screen. Three, and if one
        // is removed or renamed this count says so rather than the pair quietly re-splitting.
        val uses = Regex("""LocalDensity\s+provides\s+uiDensity\b""").findAll(shell).count()
        assertEquals(
            "expected the status strip, the hint bar and the chrome-screen block to share " +
                "uiDensity; found $uses site(s)",
            3,
            uses,
        )
    }

    @Test
    fun `the crossbar's auto-fit is applied on top of uiDensity, not instead of it`() {
        // uiScale is the cross's own fit — min(h/468, w/832). It must multiply the user's scale
        // rather than replace it, or the slider stops reaching the crossbar; and it must not
        // appear anywhere else, or a tablet's chrome grows 28% for a reason about the cross.
        assertTrue(
            "the canvas provider must build on uiDensity",
            shell.contains("Density(uiDensity.density * uiScale, uiDensity.fontScale)"),
        )
        // CODE uses only. Counting raw matches counts the prose about it too — six here, four of
        // them comments — which is the same over-counting that made an earlier sweep report the
        // arrow keys unreachable. Strip the comments, then count.
        val code = shell.lineSequence()
            .map { it.substringBefore("//") }
            .joinToString("\n")
        val uiScaleUses = Regex("""\buiScale\b""").findAll(code).count()
        assertEquals(
            "uiScale belongs to its own definition and the one canvas provider, nowhere else; " +
                "found $uiScaleUses code use(s)",
            2,
            uiScaleUses,
        )
    }
}
