package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the status strip is drawn, and where its words still describe what is under it.
 *
 * The strip used to be part of the crossbar's foreground, so every screen that covered the
 * crossbar took the clock, the battery and the notification corner with it: walking into the App
 * Drawer lost the time and walking out found it again. It is the launcher's own chrome now and
 * stays on top of the launcher's own screens.
 *
 * TWO QUESTIONS, and they are different, which is why there are two properties:
 *
 *  - IS IT DRAWN. No on a full-screen overlay — the video player, the photo viewer, the boot and
 *    disc ceremonies, the modal pickers. Yes everywhere else.
 *  - DO ITS WORDS STILL APPLY. The clock and the battery are facts about the device and are true
 *    anywhere. The sort label, the shoulder and left/right hints and the home shelf's filter row
 *    are facts about the CROSSBAR, and on a chrome screen they describe a list the user is no
 *    longer looking at. The drawer showed "Title" over a grid it does not sort.
 *
 * The pair being guarded is the partition itself. `otherBlockingOverlay` is the OR of the two
 * halves, so a screen cannot be in neither — but it can be in the WRONG one, and that is a
 * failure nobody reports: the screen works and the clock is quietly missing, or quietly lying.
 * Every screen below is one row of that decision, written down.
 */
class StatusStripVisibilityTest {

    private fun crossbar() = XMBUiState(showBootSequence = false)

    // ── Is it drawn ───────────────────────────────────────────────────────

    @Test
    fun `on the crossbar it is drawn`() {
        assertTrue(crossbar().statusStripVisible)
        assertTrue("the crossbar's own context is the strip's", crossbar().stripShowsXmbContext)
    }

    @Test
    fun `the launcher's own screens keep it`() {
        val chrome = mapOf(
            "App Drawer" to crossbar().copy(activeAppDrawerFilter = "DEFAULT"),
            "Settings" to crossbar().copy(activeSettingsScreen = "settings_display"),
            "Search" to crossbar().copy(search = SearchState(scope = SearchScope.ALL)),
            "Game detail" to crossbar().copy(activeGameId = 1L),
            "App detail" to crossbar().copy(activeAppId = 1L),
        )
        chrome.forEach { (name, state) ->
            assertTrue("$name must still cover the crossbar", state.hasBlockingOverlay)
            assertTrue("$name lost the clock", state.statusStripVisible)
            // The other half of the decision: it is drawn, and it goes quiet about the crossbar.
            assertFalse(
                "$name is not the crossbar, so the sort label must not claim to describe it",
                state.stripShowsXmbContext,
            )
        }
    }

    @Test
    fun `a full-screen overlay covers it`() {
        val fullscreen = mapOf(
            "boot" to crossbar().copy(showBootSequence = true),
            "video player" to crossbar().copy(activeVideoId = "v1"),
            "music browser" to crossbar().copy(
                musicBrowser = MusicBrowserState(view = MusicBrowserView.AllMusic, title = "All Tracks"),
            ),
            "music player" to crossbar().copy(musicPlayerVisible = true),
        )
        fullscreen.forEach { (name, state) ->
            assertTrue("$name must cover the crossbar", state.hasBlockingOverlay)
            assertFalse("$name must cover the clock too", state.statusStripVisible)
        }
    }

    // ── Do its words still apply ──────────────────────────────────────────

    /**
     * The rail and the notification sheet are not covers in the sense that matters here.
     *
     * You are still standing on the crossbar with something open in front of it, so the sort label
     * and the hints are still about the list you can see behind the menu. Getting this wrong in
     * the other direction would blank the strip's centre every time the options rail opened.
     */
    @Test
    fun `the rail and the sheet keep both the strip and its crossbar context`() {
        val rail = crossbar().copy(
            activeContextMenu = XMBContextMenu(title = "Options", items = emptyList()),
        )
        assertTrue(rail.statusStripVisible)
        assertTrue("the rail is not a cover; the crossbar is still underneath it", rail.stripShowsXmbContext)

        val sheet = crossbar().copy(notificationsOpen = true)
        assertTrue(sheet.statusStripVisible)
        assertTrue(sheet.stripShowsXmbContext)
    }

    /**
     * A rail opened FROM a chrome screen does not hand the crossbar's context back.
     *
     * This is the case the two properties would disagree on if either were a copy of the other:
     * overlayKeepsChrome is "a rail is open AND nothing else is", so a rail over the App Drawer is
     * still the App Drawer, and the sort label must stay quiet.
     */
    @Test
    fun `a rail over a chrome screen does not restore the crossbar's context`() {
        val state = crossbar().copy(
            activeAppDrawerFilter = "DEFAULT",
            activeContextMenu = XMBContextMenu(title = "Options", items = emptyList()),
        )
        assertTrue("the drawer still keeps the clock", state.statusStripVisible)
        assertFalse("the crossbar is two screens away", state.stripShowsXmbContext)
    }
}
