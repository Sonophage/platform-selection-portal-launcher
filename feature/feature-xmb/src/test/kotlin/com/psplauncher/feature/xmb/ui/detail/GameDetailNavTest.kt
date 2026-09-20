package com.psplauncher.feature.xmb.ui.detail

import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.navigation.NavigationNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Game Detail navigation contract, tested away from Compose: stable keys, readiness gating,
 * geometry-driven rows, boundary stops, dynamic-node recovery, the touch cursor and modal context
 * isolation. All of it is plain JVM work because [GameDetailNav] owns no UI.
 */
class GameDetailNavTest {

    private val activated = mutableListOf<String>()

    private fun content(
        loaded: Boolean = true,
        emulatorControls: Boolean = true,
        discs: List<Long> = emptyList(),
        overview: Boolean = true,
        info: Boolean = true,
        media: List<String> = emptyList(),
    ) = GameDetailNavContent(
        gameId = 7L,
        loaded = loaded,
        showEmulatorControls = emulatorControls,
        discIds = discs,
        showOverview = overview,
        showInfo = info,
        mediaIds = media,
    )

    private fun nav(
        content: GameDetailNavContent = content(),
        geometry: Map<String, Float> = emptyMap(),
    ): GameDetailNav = GameDetailNav().apply {
        onActivate = { activated += it }
        updateContent(content)
        if (geometry.isNotEmpty()) reportGeometry(geometry)
    }

    private fun readyNav(
        content: GameDetailNavContent = content(),
        geometry: Map<String, Float> = emptyMap(),
    ): GameDetailNav = nav(content, geometry).also { it.markReady() }

    // ── Readiness ─────────────────────────────────────────────────────────

    @Test
    fun `the page opens on Play, not on the Overview registered above it`() {
        // Overview is the first node in the graph because it is the first thing on screen. The
        // cursor still starts on the page's reason for existing.
        assertEquals(GameDetailKeys.LAUNCH, readyNav().focusedKey)
    }

    @Test
    fun `a later content update never drags the cursor back to Play`() {
        // The opening placement fires once. Artwork or metadata arriving afterwards rebuilds the
        // graph, and a rebuild that re-homed the cursor would undo every move the user made.
        val nav = readyNav()
        nav.handleAction(GamepadAction.NAVIGATE_UP)
        assertEquals(GameDetailKeys.OVERVIEW, nav.focusedKey)

        nav.updateContent(content(media = listOf("i:a")))
        assertEquals(GameDetailKeys.OVERVIEW, nav.focusedKey)
    }

    @Test
    fun `direction input before readiness is ignored and never replayed`() {
        val nav = nav()
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)

        // Dropped, not buffered: the cursor is still on Launch and nothing accumulated.
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)

        nav.markReady()
        nav.handleAction(GamepadAction.NAVIGATE_UP)
        assertEquals(GameDetailKeys.OVERVIEW, nav.focusedKey)
    }

    @Test
    fun `confirm before readiness does not activate anything`() {
        val nav = nav()
        nav.handleAction(GamepadAction.SELECT)
        assertTrue(activated.isEmpty())
    }

    // ── Rows and boundaries ───────────────────────────────────────────────

    @Test
    fun `the action row is Play then Details, and neither edge wraps`() {
        val nav = readyNav()
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)

        nav.handleAction(GamepadAction.NAVIGATE_RIGHT)
        assertEquals(GameDetailKeys.DETAILS, nav.focusedKey)

        // Right at the end stops instead of wrapping back to Play.
        nav.handleAction(GamepadAction.NAVIGATE_RIGHT)
        assertEquals(GameDetailKeys.DETAILS, nav.focusedKey)

        // Left at the start stops on Play, never on the invisible band that holds the two.
        repeat(3) { nav.handleAction(GamepadAction.NAVIGATE_LEFT) }
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)
    }

    @Test
    fun `up from the action row reaches Overview and stops at the top of the page`() {
        val nav = readyNav()
        nav.handleAction(GamepadAction.NAVIGATE_UP)
        assertEquals(GameDetailKeys.OVERVIEW, nav.focusedKey)

        nav.handleAction(GamepadAction.NAVIGATE_UP)
        assertEquals(GameDetailKeys.OVERVIEW, nav.focusedKey)
    }

    @Test
    fun `Details is always reachable, with or without a manual`() {
        // The pills it replaced hid Manual by disabling a slot in a fixed row. Details itself is
        // never conditional: its dropdown is what varies.
        assertTrue(GameDetailKeys.DETAILS in readyNav().reachableKeys())
        assertTrue(GameDetailKeys.DETAILS in readyNav(content(emulatorControls = false)).reachableKeys())
    }

    @Test
    fun `down walks the page rows in the order the redesign lays them out`() {
        // Overview, action row, discs, media, information band. The band moved BELOW the media
        // strip: the artwork comes first on the page and the numbers close it.
        val nav = readyNav(content(discs = listOf(1L, 2L), media = listOf("i:shot")))
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)
        nav.handleAction(GamepadAction.NAVIGATE_UP)
        assertEquals(GameDetailKeys.OVERVIEW, nav.focusedKey)

        nav.handleAction(GamepadAction.NAVIGATE_DOWN)   // action row
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)   // discs
        assertEquals(GameDetailKeys.disc(1L), nav.focusedKey)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)   // media strip
        assertEquals(GameDetailKeys.media("i:shot"), nav.focusedKey)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)   // information band
        assertEquals(GameDetailKeys.INFO, nav.focusedKey)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)   // bottom boundary
        assertEquals(GameDetailKeys.INFO, nav.focusedKey)
    }

    @Test
    fun `the disc row hands the cursor to the member nearest where it came from`() {
        val nav = readyNav(content(discs = listOf(1L, 2L, 3L)))
        nav.handleAction(GamepadAction.NAVIGATE_RIGHT)  // Details (child 1 of the action row)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals(GameDetailKeys.disc(2L), nav.focusedKey)
    }

    @Test
    fun `media strip boundaries stop at the ends`() {
        val nav = readyNav(content(media = listOf("i:a", "i:b"), overview = false, info = false))
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals(GameDetailKeys.media("i:a"), nav.focusedKey)

        nav.handleAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(GameDetailKeys.media("i:a"), nav.focusedKey)

        nav.handleAction(GamepadAction.NAVIGATE_RIGHT)
        assertEquals(GameDetailKeys.media("i:b"), nav.focusedKey)

        nav.handleAction(GamepadAction.NAVIGATE_RIGHT)
        assertEquals(GameDetailKeys.media("i:b"), nav.focusedKey)
    }

    @Test
    fun `up from the media strip lands on the row above, not on the band`() {
        val nav = readyNav(content(media = listOf("i:a")))
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)   // media strip
        assertEquals(GameDetailKeys.media("i:a"), nav.focusedKey)

        nav.handleAction(GamepadAction.NAVIGATE_UP)
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)
    }

    // ── Geometry ──────────────────────────────────────────────────────────

    @Test
    fun `vertical order follows reported geometry, not registration order`() {
        // Registered order is overview-then-info; on screen they are the other way round here, and
        // the cursor must follow what the user sees.
        val nav = readyNav(
            content = content(),
            geometry = mapOf(
                GameDetailKeys.ACTIONS to 60f,
                GameDetailKeys.INFO to 200f,
                GameDetailKeys.OVERVIEW to 320f,
            ),
        )
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals(GameDetailKeys.INFO, nav.focusedKey)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals(GameDetailKeys.OVERVIEW, nav.focusedKey)
    }

    // ── Dynamic content ───────────────────────────────────────────────────

    @Test
    fun `Details hands back its own key, so the page opens the dropdown and nothing else`() {
        val nav = readyNav()
        nav.handleAction(GamepadAction.NAVIGATE_RIGHT)
        assertEquals(GameDetailKeys.DETAILS, nav.focusedKey)

        nav.handleAction(GamepadAction.SELECT)
        assertEquals(listOf(GameDetailKeys.DETAILS), activated)
    }

    @Test
    fun `a package-backed entry keeps Details but has no emulator information field`() {
        val nav = readyNav(content(emulatorControls = false))
        assertTrue(GameDetailKeys.OVERVIEW in nav.reachableKeys())
        // Details opens the dropdown, which every entry has; only the emulator field is ROM-only.
        assertTrue(GameDetailKeys.DETAILS in nav.reachableKeys())

        // The information band is still a reading stop, but confirming it does nothing.
        focusInfo(nav)
        nav.handleAction(GamepadAction.SELECT)
        assertTrue(activated.isEmpty())
    }

    @Test
    fun `confirming the highlighted information band opens the emulator picker straight away`() {
        val nav = readyNav()
        focusInfo(nav)
        assertEquals(GameDetailKeys.INFO, nav.focusedKey)

        // No RIGHT into an inner field first: the highlighted row IS the action.
        nav.handleAction(GamepadAction.SELECT)
        assertEquals(listOf(GameDetailKeys.INFO), activated)

        // And RIGHT has nowhere to go inside it.
        nav.handleAction(GamepadAction.NAVIGATE_RIGHT)
        assertEquals(GameDetailKeys.INFO, nav.focusedKey)
    }

    /** Walks the action row down to the information band, the page's last row. */
    private fun focusInfo(nav: GameDetailNav) {
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals(GameDetailKeys.INFO, nav.focusedKey)
    }

    @Test
    fun `losing the focused node recovers to the nearest visible one`() {
        val nav = readyNav(content(media = listOf("i:a", "i:b")))
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        nav.handleAction(GamepadAction.NAVIGATE_RIGHT)
        assertEquals(GameDetailKeys.media("i:b"), nav.focusedKey)

        // The scraped asset is removed while the cursor sits on it.
        nav.updateContent(content(media = listOf("i:a")))

        // Recovery stays in the same row (never a jump back to Launch), and never parks on the
        // invisible strip band.
        assertEquals(GameDetailKeys.media("i:a"), nav.focusedKey)
    }

    @Test
    fun `an unrelated content change keeps the cursor where it is`() {
        val nav = readyNav()
        nav.handleAction(GamepadAction.NAVIGATE_RIGHT)
        assertEquals(GameDetailKeys.DETAILS, nav.focusedKey)

        // A scrape finished loading: the graph changed, the focused node did not.
        nav.updateContent(content())
        assertEquals(GameDetailKeys.DETAILS, nav.focusedKey)
    }

    @Test
    fun `media that arrives later becomes focusable`() {
        val nav = readyNav(content(media = emptyList()))
        assertFalse(GameDetailKeys.media("i:a") in nav.reachableKeys())

        nav.updateContent(content(media = listOf("i:a")))
        assertTrue(GameDetailKeys.media("i:a") in nav.reachableKeys())
    }

    @Test
    fun `an unloaded page has nothing focusable`() {
        val nav = readyNav(content(loaded = false))
        assertNull(nav.focusedKey)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertNull(nav.focusedKey)
    }

    // ── Cursor visibility ─────────────────────────────────────────────────

    @Test
    fun `touch hides the cursor but keeps logical focus, and controller input brings it back`() {
        val nav = readyNav()
        nav.handleAction(GamepadAction.NAVIGATE_RIGHT)
        assertEquals(GameDetailKeys.DETAILS, nav.focusedKey)
        assertTrue(nav.cursorVisible)

        nav.markTouchInput()
        assertFalse(nav.cursorVisible)
        assertEquals(GameDetailKeys.DETAILS, nav.focusedKey)

        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertTrue(nav.cursorVisible)
    }

    @Test
    fun `tapping a node moves logical focus there and activates it`() {
        val nav = readyNav()
        nav.markTouchInput()

        assertTrue(nav.touch(GameDetailKeys.DETAILS))
        assertEquals(listOf(GameDetailKeys.DETAILS), activated)
        assertEquals(GameDetailKeys.DETAILS, nav.focusedKey)
        // A tap is still touch input: the cursor stays hidden.
        assertFalse(nav.cursorVisible)
    }

    // ── Recovery lock ─────────────────────────────────────────────────────

    @Test
    fun `repeated input during an alignment is dropped, not queued`() {
        val nav = readyNav()
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)

        nav.beginRecoveryLock()
        repeat(3) { nav.handleAction(GamepadAction.NAVIGATE_DOWN) }
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)

        nav.endRecoveryLock()
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals(GameDetailKeys.INFO, nav.focusedKey)
    }

    // ── Modal contexts ────────────────────────────────────────────────────

    @Test
    fun `a modal owns all input and hands back the exact page node it interrupted`() {
        val nav = readyNav()
        nav.handleAction(GamepadAction.NAVIGATE_RIGHT)
        assertEquals(GameDetailKeys.DETAILS, nav.focusedKey)

        val modalRows = listOf(
            NavigationNode("modal:row0", onSelect = { activated += "modal:row0" }),
            NavigationNode("modal:row1", onSelect = { activated += "modal:row1" }),
        )
        nav.pushModal(GameDetailKeys.MODAL_OPTIONS, modalRows, preferredFocus = "modal:row0")
        assertTrue(nav.isModalActive)
        assertEquals("modal:row0", nav.focusedKey)

        // Direction and confirm both belong to the overlay: no page node may fire through it.
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals("modal:row1", nav.focusedKey)
        nav.handleAction(GamepadAction.SELECT)
        assertEquals(listOf("modal:row1"), activated)

        // Closing restores the exact node the page was on — not Play, not the first row.
        assertEquals(GameDetailKeys.DETAILS, nav.popModal())
        assertFalse(nav.isModalActive)
        assertEquals(GameDetailKeys.DETAILS, nav.focusedKey)
    }

    @Test
    fun `a modal with no rows consumes navigation without moving the page cursor`() {
        val nav = readyNav()
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        val before = nav.focusedKey

        nav.pushModal(GameDetailKeys.MODAL_MANUAL_VIEWER)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        nav.handleAction(GamepadAction.SELECT)
        assertTrue(activated.isEmpty())

        assertEquals(before, nav.popModal())
    }

    @Test
    fun `page content updates wait for the modal to close`() {
        val nav = readyNav(content(media = emptyList()))
        nav.pushModal(GameDetailKeys.MODAL_DETAILS)
        // A scrape lands while the dropdown is up: it must not overwrite the overlay's own graph.
        nav.updateContent(content(media = listOf("i:a")))
        assertFalse(GameDetailKeys.media("i:a") in nav.reachableKeys())

        nav.popModal()
        assertTrue(GameDetailKeys.media("i:a") in nav.reachableKeys())
    }
}
