package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The name prompts' text routing.
 *
 * This exists because of a pair that must stay in step and is only obviously guarded on one side.
 * [withNamePromptText] decides which prompt a keystroke lands in; XMBViewModel.handleGamepadAction
 * decides which field the A button confirms from. They are two separate `when` ladders over the
 * same four prompts, in two different parts of the file. If they ever disagree, the failure is
 * silent and expensive: A creates a collection named whatever was left in another prompt's field,
 * and nothing throws.
 *
 * So each case below writes through the router and reads back the exact field the gamepad branch
 * reads, by name.
 */
class NamePromptTextTest {

    /** Each prompt: how to open it, and the field A confirms from. */
    private val prompts: List<Triple<String, (XMBUiState) -> XMBUiState, (XMBUiState) -> String?>> =
        listOf(
            Triple(
                "rename shortcut",
                { s: XMBUiState -> s.copy(renameAppTarget = "com.example", renameAppCurrent = "Example") },
                { s: XMBUiState -> s.renameAppText },
            ),
            Triple(
                "collection name",
                { s: XMBUiState -> s.copy(collectionNameDialog = CollectionNameDialogState(title = "New Collection")) },
                { s: XMBUiState -> s.collectionNameDialog?.text },
            ),
            Triple(
                "playlist name",
                { s: XMBUiState -> s.copy(playlistNameDialog = PlaylistNameDialogState(title = "New Playlist")) },
                { s: XMBUiState -> s.playlistNameDialog?.text },
            ),
            Triple(
                "save theme name",
                { s: XMBUiState -> s.copy(saveThemeNameDialog = PlaylistNameDialogState(title = "Save as Theme")) },
                { s: XMBUiState -> s.saveThemeNameDialog?.text },
            ),
        )

    @Test
    fun `every name prompt receives what was typed into it`() {
        // Guard on the guard: if the table is empty this test passes without asserting anything.
        assertEquals("the prompt table must cover all four name prompts", 4, prompts.size)

        for ((name, open, read) in prompts) {
            val typed = open(XMBUiState()).withNamePromptText("Backlog")
            assertEquals("$name must receive the typed text", "Backlog", read(typed))
        }
    }

    @Test
    fun `a prompt opens with its seed text already in the live field`() {
        // The seed matters for renames: opening "Rename Collection" and pressing A immediately
        // must keep the existing name, not clear it.
        val seeded = CollectionNameDialogState(title = "Rename Collection", initialText = "Shooters")
        assertEquals("the live field starts at the seed", "Shooters", seeded.text)
    }

    @Test
    fun `typing with no prompt open changes nothing`() {
        // The control. Without it, a router that wrote into a fixed field would still pass above.
        val idle = XMBUiState()
        val after = idle.withNamePromptText("stray")
        assertEquals("no prompt open means no rename text", "", after.renameAppText)
        assertNull("no prompt open means no collection prompt", after.collectionNameDialog)
        assertEquals("the state is untouched", idle, after)
    }

    @Test
    fun `the first prompt in gamepad order wins when two are somehow open`() {
        // The order guard. These should never both be open, but if they are, the keystroke has to
        // land in the one handleGamepadAction confirms from -- it checks renameAppTarget first.
        val both = XMBUiState(
            renameAppTarget = "com.example",
            renameAppCurrent = "Example",
            collectionNameDialog = CollectionNameDialogState(title = "New Collection"),
        ).withNamePromptText("Backlog")

        assertEquals("the rename prompt is checked first, so it takes the text", "Backlog", both.renameAppText)
        assertEquals(
            "the collection prompt must not also take it",
            "",
            both.collectionNameDialog?.text,
        )
    }
}
