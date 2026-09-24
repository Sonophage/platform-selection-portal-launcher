package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.MusicTrack
import com.psplauncher.core.ui.notification.AndroidNotice
import com.psplauncher.feature.xmb.music.MusicPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the notification sheet's cursor can reach, and what the bar says about it.
 *
 * The rule this file exists to hold: **the cursor only ever stops somewhere a press does
 * something.** The sheet has three kinds of row and only two of them can be acted on — the
 * launcher's column is finished work, an ongoing system notice cannot be cleared, and a
 * notification with no content intent has nowhere to go. Every one of those is a place a cursor
 * could stop and a button could then do nothing, which is the fault the keyboard prompts shipped
 * with: a footer naming a press that was bound to nothing.
 */
class NotificationSheetTest {

    private fun notice(
        key: String,
        canOpen: Boolean = true,
        canDismiss: Boolean = true,
    ) = AndroidNotice(
        key = key,
        appLabel = "Signal",
        title = "A message",
        text = "hello",
        postedAt = 1L,
        canOpen = canOpen,
        canDismiss = canDismiss,
    )

    private fun track() = MusicTrack(
        id = "t1", folderId = "f", uri = "content://t1", displayName = "track.mp3",
        title = "Blue Monday", artist = "New Order",
    )

    private fun state(
        notices: List<AndroidNotice> = emptyList(),
        playing: Boolean = false,
        resume: Game? = null,
        cursor: Int = 0,
    ) = XMBUiState(
        // The default state is still BOOTING -- showBootSequence defaults to true -- and boot is a
        // blocking overlay, so a fixture that left it alone would be testing a sheet over a boot
        // screen, which cannot happen.
        showBootSequence = false,
        notificationsOpen = true,
        androidNotices = notices,
        noticeCursor = cursor,
        resumeGame = resume,
        musicPlayback = if (playing) {
            MusicPlaybackState(track = track(), isPlaying = true, positionMs = 1_000, durationMs = 4_000)
        } else {
            MusicPlaybackState()
        },
    )

    private val game = Game(id = 7, title = "Crisis Core", platformId = "psp", romPath = "/roms/cc.iso")

    @Test
    fun `the media row is there when something is playing`() {
        val rows = state(playing = true).noticeFocusables
        assertEquals(listOf(NoticeFocus.Media), rows)
    }

    @Test
    fun `the media row is there for a game to resume when nothing is playing`() {
        assertEquals(listOf(NoticeFocus.Media), state(resume = game).noticeFocusables)
    }

    @Test
    fun `with nothing playing and nothing played the row is not drawn at all`() {
        // Not "drawn empty". An empty transport is a control for something that is not there.
        assertTrue(state().noticeFocusables.isEmpty())
        assertNull(state().focusedNotice)
    }

    @Test
    fun `notices follow the media row, in order, named by key`() {
        val rows = state(notices = listOf(notice("a"), notice("b")), playing = true).noticeFocusables
        assertEquals(
            listOf(NoticeFocus.Media, NoticeFocus.Notice("a"), NoticeFocus.Notice("b")),
            rows,
        )
    }

    @Test
    fun `the cursor never reaches past what the sheet draws`() {
        // The column draws NOTICE_ROWS of them; a cursor that could walk to the fortieth would be
        // walking off the bottom of the screen with nothing to look at.
        val many = (1..12).map { notice("k$it") }
        val rows = state(notices = many).noticeFocusables
        assertEquals(NOTICE_ROWS, rows.size)
    }

    /**
     * The list is live — the app that posted a notification can clear it while the sheet is open —
     * so the cursor is clamped on READ. Stored as an index it would otherwise point past the end,
     * and `focusedNotice` would be null on a sheet that still has rows in it.
     */
    @Test
    fun `a cursor past the end lands on the last row rather than nowhere`() {
        val shrunk = state(notices = listOf(notice("a")), cursor = 4)
        assertEquals(NoticeFocus.Notice("a"), shrunk.focusedNotice)
    }

    @Test
    fun `the bar names Open only where the notification has somewhere to go`() {
        val canOpen = promptsFor(state(notices = listOf(notice("a", canOpen = true))))
        assertEquals("Open", canOpen.primary?.verb)

        val cannot = promptsFor(state(notices = listOf(notice("a", canOpen = false))))
        assertNull(
            "the bar offered Open on a notification with no content intent",
            cannot.primary,
        )
    }

    @Test
    fun `the bar names Clear only where clearing works`() {
        val clearable = promptsFor(state(notices = listOf(notice("a", canDismiss = true))))
        assertTrue(clearable.right.any { it.verb == "Clear" })

        // An ongoing notice — a media session, a foreground service. cancelNotification on one is
        // a silent no-op, so naming the press would be naming something that does not happen.
        val ongoing = promptsFor(state(notices = listOf(notice("a", canDismiss = false))))
        assertTrue(
            "the bar offered Clear on an ongoing notification",
            ongoing.right.none { it.verb == "Clear" },
        )
    }

    /**
     * The resume row names the game the way the rest of the launcher does.
     *
     * Game.title is the row as the SCAN wrote it — the ROM's filename with its illegal characters
     * sanitised — and this row read it directly, so it alone said "The Elder Scrolls V_ Skyrim
     * Special Edition" while every other surface said it with the colon. Seen on the device, in
     * the hint bar, beside a crossbar row spelling it correctly.
     */
    @Test
    fun `the resume row uses the display title, not the filename`() {
        val scraped = game.copy(title = "The Elder Scrolls V_ Skyrim", scrapedTitle = "The Elder Scrolls V: Skyrim")
        assertEquals(
            "The Elder Scrolls V: Skyrim",
            promptsFor(state(resume = scraped)).primary?.target,
        )
        // And the user's own name beats the scraped one, which is the rest of displayTitle's rule.
        val renamed = scraped.copy(userTitleOverride = "Skyrim")
        assertEquals("Skyrim", promptsFor(state(resume = renamed)).primary?.target)
    }

    @Test
    fun `the media row's verb follows what the row is`() {
        assertEquals("Pause", promptsFor(state(playing = true)).primary?.verb)
        assertEquals("Resume", promptsFor(state(resume = game)).primary?.verb)
    }

    @Test
    fun `back closes the sheet rather than whatever is behind it`() {
        assertEquals(GamepadAction.BACK, promptsFor(state(playing = true)).back?.action)
        assertEquals("Close", promptsFor(state(playing = true)).back?.verb)
    }

    /**
     * The sheet is an overlay for input and the idle cues, and NOT one for the chrome: the strip
     * is what you pressed to open it and the bar is what names its presses. Both readings come
     * from one list — see XMBUiState.overlayKeepsChrome.
     */
    @Test
    fun `the sheet blocks the XMB but keeps the strip and the bar`() {
        val open = state(playing = true)
        assertTrue(open.hasBlockingOverlay)
        assertTrue(open.overlayKeepsChrome)
    }
}
