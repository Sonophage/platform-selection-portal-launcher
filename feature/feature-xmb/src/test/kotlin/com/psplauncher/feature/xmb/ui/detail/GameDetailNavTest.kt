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
        media: List<String> = emptyList(),
        onMediaPage: Boolean = media.isNotEmpty(),
    ) = GameDetailNavContent(
        gameId = 7L,
        loaded = loaded,
        showEmulatorControls = emulatorControls,
        discIds = discs,
        mediaIds = media,
        onMediaPage = onMediaPage,
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
    fun `the page opens on Play, not on the first button in the footer`() {
        // The footer reads heart, gear, Play, and the engine focuses the first node it is given.
        // The cursor still starts on the page's reason for existing.
        assertEquals(GameDetailKeys.LAUNCH, readyNav().focusedKey)
    }

    @Test
    fun `a later content update never drags the cursor back to Play`() {
        // The opening placement fires once. Artwork or metadata arriving afterwards rebuilds the
        // graph, and a rebuild that re-homed the cursor would undo every move the user made.
        val nav = readyNav()
        nav.handleAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(GameDetailKeys.OPTIONS, nav.focusedKey)

        nav.updateContent(content(media = listOf("i:a")))
        assertEquals(GameDetailKeys.OPTIONS, nav.focusedKey)
    }

    @Test
    fun `direction input before readiness is ignored and never replayed`() {
        val nav = nav()
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)

        // Dropped, not buffered: the cursor is still on Launch and nothing accumulated.
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)

        nav.markReady()
        nav.handleAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(GameDetailKeys.OPTIONS, nav.focusedKey)
    }

    @Test
    fun `confirm before readiness does not activate anything`() {
        val nav = nav()
        nav.handleAction(GamepadAction.SELECT)
        assertTrue(activated.isEmpty())
    }

    // ── Rows and boundaries ───────────────────────────────────────────────

    @Test
    fun `the footer is Favourite, Options, Play, and neither edge wraps`() {
        val nav = readyNav()
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)

        nav.handleAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(GameDetailKeys.OPTIONS, nav.focusedKey)
        nav.handleAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(GameDetailKeys.FAVORITE, nav.focusedKey)

        // Left at the start stops instead of wrapping, and never parks on the invisible band
        // that holds the three.
        repeat(3) { nav.handleAction(GamepadAction.NAVIGATE_LEFT) }
        assertEquals(GameDetailKeys.FAVORITE, nav.focusedKey)

        repeat(5) { nav.handleAction(GamepadAction.NAVIGATE_RIGHT) }
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)
    }

    @Test
    fun `up from the footer stays put, because the panel is not a node`() {
        // The overview, the information band and the media strip were rows above this one. They
        // are the panel's PAGES now, walked with the shoulders, and a page is not somewhere a
        // cursor goes — so the footer is the top of the graph.
        val nav = readyNav()
        repeat(3) { nav.handleAction(GamepadAction.NAVIGATE_UP) }
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)
    }

    @Test
    fun `Options is always reachable, whatever the entry is`() {
        // It is the way to scrape and edit, and to the emulator picker that used to sit on the
        // information band. Never conditional: the MENU varies, this button does not.
        assertTrue(GameDetailKeys.OPTIONS in readyNav().reachableKeys())
        assertTrue(GameDetailKeys.OPTIONS in readyNav(content(emulatorControls = false)).reachableKeys())
    }

    @Test
    fun `down walks the rows the page still has`() {
        // Three now, not five: the footer, the disc row, and the media tiles while their page is
        // showing. Everything else became a panel page.
        val nav = readyNav(content(discs = listOf(1L, 2L), media = listOf("i:shot")))
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)

        // Play is the third button, so the disc row hands back its own third member — here its
        // last, since there are two. That is the engine's nearest-index rule, not an accident.
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)   // discs
        assertEquals(GameDetailKeys.disc(2L), nav.focusedKey)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)   // media tiles
        assertEquals(GameDetailKeys.media("i:shot"), nav.focusedKey)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)   // bottom boundary
        assertEquals(GameDetailKeys.media("i:shot"), nav.focusedKey)
    }

    @Test
    fun `the media tiles are unreachable while their page is not showing`() {
        // A node the user cannot see is a place the cursor can vanish into. The tiles join the
        // graph only when the panel is on its media page.
        val hidden = readyNav(content(media = listOf("i:a"), onMediaPage = false))
        assertTrue(GameDetailKeys.media("i:a") !in hidden.reachableKeys())

        val shown = readyNav(content(media = listOf("i:a"), onMediaPage = true))
        assertTrue(GameDetailKeys.media("i:a") in shown.reachableKeys())
    }

    @Test
    fun `the disc row hands the cursor to the member nearest where it came from`() {
        val nav = readyNav(content(discs = listOf(1L, 2L, 3L)))
        nav.handleAction(GamepadAction.NAVIGATE_LEFT)   // Options (child 1 of the footer)
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals(GameDetailKeys.disc(2L), nav.focusedKey)
    }

    @Test
    fun `media strip boundaries stop at the ends`() {
        val nav = readyNav(content(media = listOf("i:a", "i:b")))
        // Down from Play (the third button) lands on the tile nearest that index — the last one.
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals(GameDetailKeys.media("i:b"), nav.focusedKey)

        nav.handleAction(GamepadAction.NAVIGATE_RIGHT)
        assertEquals(GameDetailKeys.media("i:b"), nav.focusedKey)

        nav.handleAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(GameDetailKeys.media("i:a"), nav.focusedKey)

        nav.handleAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(GameDetailKeys.media("i:a"), nav.focusedKey)
    }

    @Test
    fun `up from the media strip lands on the row above, not on the band`() {
        val nav = readyNav(content(media = listOf("i:a")))
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)   // media tiles
        assertEquals(GameDetailKeys.media("i:a"), nav.focusedKey)

        // Back up to the footer — a button, never the invisible band that holds the three.
        nav.handleAction(GamepadAction.NAVIGATE_UP)
        assertEquals(GameDetailKeys.FAVORITE, nav.focusedKey)
    }

    // ── Geometry ──────────────────────────────────────────────────────────

    @Test
    fun `vertical order follows reported geometry, not registration order`() {
        // Registered order is discs-then-media; on screen they are the other way round here, and
        // the cursor must follow what the user sees.
        val nav = readyNav(
            content = content(discs = listOf(1L, 2L), media = listOf("i:a")),
            geometry = mapOf(
                GameDetailKeys.ACTIONS to 60f,
                GameDetailKeys.MEDIA to 200f,
                GameDetailKeys.DISCS to 320f,
            ),
        )
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)

        // Registration puts the disc row BEFORE the media tiles. Geometry puts the tiles first,
        // and the cursor follows what the user sees — down from the footer reaches the tiles,
        // not the row that was registered above them.
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals(GameDetailKeys.media("i:a"), nav.focusedKey)
    }

    // ── Dynamic content ───────────────────────────────────────────────────

    @Test
    fun `the gear hands back its own key, so the page opens Options and nothing else`() {
        val nav = readyNav()
        nav.handleAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(GameDetailKeys.OPTIONS, nav.focusedKey)

        nav.handleAction(GamepadAction.SELECT)
        assertEquals(listOf(GameDetailKeys.OPTIONS), activated)
    }

    @Test
    fun `a package-backed entry keeps the whole footer`() {
        // The emulator picker used to hang off the information band, which is a panel page now
        // and holds no cursor. It is an Options row instead, and Options is on every entry —
        // so a package-backed game loses nothing by having no emulator of its own.
        val nav = readyNav(content(emulatorControls = false))

        assertTrue(GameDetailKeys.FAVORITE in nav.reachableKeys())
        assertTrue(GameDetailKeys.OPTIONS in nav.reachableKeys())
        assertTrue(GameDetailKeys.LAUNCH in nav.reachableKeys())
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
        nav.handleAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(GameDetailKeys.OPTIONS, nav.focusedKey)

        // A scrape finished loading: the graph changed, the focused node did not.
        nav.updateContent(content())
        assertEquals(GameDetailKeys.OPTIONS, nav.focusedKey)
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
        val nav = readyNav(content(discs = listOf(1L, 2L)))
        nav.handleAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(GameDetailKeys.OPTIONS, nav.focusedKey)
        assertTrue(nav.cursorVisible)

        nav.markTouchInput()
        assertFalse(nav.cursorVisible)
        assertEquals(GameDetailKeys.OPTIONS, nav.focusedKey)

        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertTrue(nav.cursorVisible)
    }

    @Test
    fun `tapping a node moves logical focus there and activates it`() {
        val nav = readyNav()
        nav.markTouchInput()

        assertTrue(nav.touch(GameDetailKeys.OPTIONS))
        assertEquals(listOf(GameDetailKeys.OPTIONS), activated)
        assertEquals(GameDetailKeys.OPTIONS, nav.focusedKey)
        // A tap is still touch input: the cursor stays hidden.
        assertFalse(nav.cursorVisible)
    }

    // ── Recovery lock ─────────────────────────────────────────────────────

    @Test
    fun `repeated input during an alignment is dropped, not queued`() {
        // Two discs, not one: a single-disc game registers no disc row at all, so the page would
        // have nothing below the footer and this test would pass by having nowhere to move.
        val nav = readyNav(content(discs = listOf(1L, 2L)))
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)

        nav.beginRecoveryLock()
        repeat(3) { nav.handleAction(GamepadAction.NAVIGATE_DOWN) }
        assertEquals(GameDetailKeys.LAUNCH, nav.focusedKey)

        nav.endRecoveryLock()
        nav.handleAction(GamepadAction.NAVIGATE_DOWN)
        assertEquals(GameDetailKeys.disc(2L), nav.focusedKey)
    }

    // ── Modal contexts ────────────────────────────────────────────────────

    @Test
    fun `a modal owns all input and hands back the exact page node it interrupted`() {
        val nav = readyNav()
        nav.handleAction(GamepadAction.NAVIGATE_LEFT)
        assertEquals(GameDetailKeys.OPTIONS, nav.focusedKey)

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
        assertEquals(GameDetailKeys.OPTIONS, nav.popModal())
        assertFalse(nav.isModalActive)
        assertEquals(GameDetailKeys.OPTIONS, nav.focusedKey)
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
