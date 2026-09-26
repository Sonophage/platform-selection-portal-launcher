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
        val many = (1..12).map { notice("k$it") }
        val rows = state(notices = many).noticeFocusables
        assertEquals(NOTICE_ROWS, rows.size)
    }

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

        val ongoing = promptsFor(state(notices = listOf(notice("a", canDismiss = false))))
        assertTrue(
            "the bar offered Clear on an ongoing notification",
            ongoing.right.none { it.verb == "Clear" },
        )
    }

    @Test
    fun `the resume row uses the display title, not the filename`() {
        val scraped = game.copy(title = "The Elder Scrolls V_ Skyrim", scrapedTitle = "The Elder Scrolls V: Skyrim")
        assertEquals(
            "The Elder Scrolls V: Skyrim",
            promptsFor(state(resume = scraped)).primary?.target,
        )

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

    @Test
    fun `the sheet blocks the XMB but keeps the strip and the bar`() {
        val open = state(playing = true)
        assertTrue(open.hasBlockingOverlay)
        assertTrue(open.overlayKeepsChrome)
    }
}
