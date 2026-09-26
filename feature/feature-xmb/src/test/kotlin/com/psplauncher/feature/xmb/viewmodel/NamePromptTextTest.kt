package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NamePromptTextTest {
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
        assertEquals("the prompt table must cover all four name prompts", 4, prompts.size)

        for ((name, open, read) in prompts) {
            val typed = open(XMBUiState()).withNamePromptText("Backlog")
            assertEquals("$name must receive the typed text", "Backlog", read(typed))
        }
    }

    @Test
    fun `a prompt opens with its seed text already in the live field`() {
        val seeded = CollectionNameDialogState(title = "Rename Collection", initialText = "Shooters")
        assertEquals("the live field starts at the seed", "Shooters", seeded.text)
    }

    @Test
    fun `typing with no prompt open changes nothing`() {
        val idle = XMBUiState()
        val after = idle.withNamePromptText("stray")
        assertEquals("no prompt open means no rename text", "", after.renameAppText)
        assertNull("no prompt open means no collection prompt", after.collectionNameDialog)
        assertEquals("the state is untouched", idle, after)
    }

    @Test
    fun `the first prompt in gamepad order wins when two are somehow open`() {
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
